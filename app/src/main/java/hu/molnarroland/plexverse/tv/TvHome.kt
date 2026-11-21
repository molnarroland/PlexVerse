package hu.molnarroland.plexverse.tv

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.embergabor.simplex.core.plex.PlexServer
import hu.molnarroland.plexverse.R
import hu.molnarroland.plexverse.data.Continuing
import hu.molnarroland.plexverse.data.Source
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.data.VerseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** One card for the TV's home screen. [id] stays the same for the same title, so a card is updated in place. */
data class HomeCard(
    val id: String,
    val kind: Kind,
    val title: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val image: String?,
    val position: Long = 0,
    val duration: Long = 0,
    val lastEngaged: Long = 0,
    val link: HomeLink,
) {
    enum class Kind { MOVIE, EPISODE, SHOW }
}

/**
 * The TV's home screen, kept in step with the app: the profile's Continue watching in the system's
 * Watch Next row ("Continue watching" on Google TV), and a PlexVerse channel with what's newest
 * (Android TV's home shows app channels; Google TV mostly doesn't). Cards open the app straight into
 * playing (or a show's page) as the current profile. Pictures are the servers' own URLs, as the app uses them.
 *
 * Every card is 16:9, so it gets a 16:9 picture, never a poster: a film's or show's background (Plex's
 * "art", the "background" of its images), an episode's own still. A title without one gets none, and the
 * home screen shows its title instead of a cropped poster.
 */
object TvHome {
    private const val TAG = "TvHome"
    private const val CHANNEL_ID = "newest"

    /** Cards for Watch Next from the Continue watching row, newest first. */
    fun watchNextCards(row: List<Continuing>, connected: Map<String, PlexServer>): List<HomeCard> = row.mapNotNull { c ->
        val first = c.sources.firstOrNull { it.machineId in connected } ?: return@mapNotNull null
        val item = c.item
        HomeCard(
            id = first.ref,
            kind = if (item.isEpisode) HomeCard.Kind.EPISODE else HomeCard.Kind.MOVIE,
            title = if (item.isEpisode) item.grandparentTitle ?: item.title else item.title,
            episodeTitle = item.title.takeIf { item.isEpisode },
            season = item.parentIndex.takeIf { item.isEpisode },
            episode = item.index.takeIf { item.isEpisode },
            year = item.year,
            image = if (item.isEpisode) still(c.sources, connected) else background(c.sources, connected),
            position = item.viewOffset,
            duration = item.duration,
            lastEngaged = item.lastViewedAt * 1000,
            link = HomeLink.Play(c.sources.joinToString(",") { it.ref }, resume = true),
        )
    }

    /** Cards for the channel: the newest titles, a film plays and a show opens its page. */
    fun channelCards(newest: List<VerseEntry>, connected: Map<String, PlexServer>): List<HomeCard> = newest.mapNotNull { e ->
        val first = e.sources.firstOrNull { it.machineId in connected } ?: return@mapNotNull null
        val show = e.item.type == "show"
        HomeCard(
            id = first.ref,
            kind = if (show) HomeCard.Kind.SHOW else HomeCard.Kind.MOVIE,
            title = e.item.title,
            year = e.item.year,
            image = background(e.sources, connected),
            duration = e.item.duration,
            link = if (show) HomeLink.Show(first.ref) else HomeLink.Play(e.sources.joinToString(",") { it.ref }, resume = false),
        )
    }

    /** The first background among the reached copies (one server may have it when another doesn't). */
    private fun background(sources: List<Source>, connected: Map<String, PlexServer>): String? =
        sources.firstNotNullOfOrNull { s -> s.item.art?.let { connected[s.machineId]?.image(it, 960, 540) } }

    /** An episode's own still (its thumb is a 16:9 frame, not a poster). */
    private fun still(sources: List<Source>, connected: Map<String, PlexServer>): String? =
        sources.firstNotNullOfOrNull { s -> s.item.thumb?.let { connected[s.machineId]?.image(it, 960, 540) } }

    /**
     * Follows [store] for as long as it's collected: once its lists settle (a few seconds without a
     * change), the home screen gets the current cards. Only what changed is written.
     */
    @OptIn(FlowPreview::class)
    suspend fun follow(context: Context, store: VerseStore) {
        val app = context.applicationContext
        combine(store.lists, store.continuing, store.connected, store.ratings) { lists, continuing, connected, ratings ->
            if (!enabled(app)) return@combine null
            val row = store.continuingRow(lists, continuing, ratings)
            val newest = store.newestForHome(lists, ratings)
            watchNextCards(row, connected).take(12) to channelCards(newest, connected)
        }
            .debounce(3000)
            .distinctUntilChanged()
            .map { cards -> withContext(Dispatchers.IO) { if (cards == null) clear(app) else publish(app, cards.first, cards.second) } }
            .collect { }
    }

    fun enabled(context: Context) = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("tvhome", Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
    }

    private fun publish(context: Context, watchNext: List<HomeCard>, channel: List<HomeCard>) {
        runCatching { syncWatchNext(context, watchNext) }.onFailure { Log.w(TAG, "Watch Next not updated", it) }
        runCatching { syncChannel(context, channel) }.onFailure { Log.w(TAG, "Channel not updated", it) }
    }

    /** Takes everything off the home screen: Settings turned it off, or the profile signed out. */
    fun clear(context: Context) {
        runCatching { context.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null) }
        runCatching {
            val prefs = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE)
            val id = prefs.getLong("channel", -1)
            if (id >= 0) context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null)
            prefs.edit().remove("channel").apply()
        }
    }

    // --- Watch Next ---

    @SuppressLint("RestrictedApi") // WatchNextProgram.fromCursor: the documented way to read rows back
    private fun syncWatchNext(context: Context, cards: List<HomeCard>) {
        val resolver = context.contentResolver
        // Only this app's rows come back. A row the viewer removed from the home screen isn't browsable any
        // more: it's left alone (not added again) until the title leaves Continue watching.
        val existing = HashMap<String, WatchNextProgram>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p }
            }
        }
        for (card in cards) {
            val old = existing.remove(card.id)
            if (old != null && !old.isBrowsable) continue
            val values = watchNextProgram(card).toContentValues()
            if (old == null) resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
            else resolver.update(TvContractCompat.buildWatchNextProgramUri(old.id), values, null, null)
        }
        for (gone in existing.values) resolver.delete(TvContractCompat.buildWatchNextProgramUri(gone.id), null, null)
    }

    private fun watchNextProgram(card: HomeCard): WatchNextProgram {
        val b = WatchNextProgram.Builder()
        b.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(card.lastEngaged.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setLastPlaybackPositionMillis(card.position.toInt())
            .setDurationMillis(card.duration.toInt())
            .setType(if (card.kind == HomeCard.Kind.EPISODE) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setTitle(card.title)
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setIntentUri(card.link.uri())
            .setInternalProviderId(card.id)
        card.image?.let { b.setPosterArtUri(Uri.parse(it)) }
        card.episodeTitle?.let { b.setEpisodeTitle(it) }
        card.season?.let { b.setSeasonNumber(it) }
        card.episode?.let { b.setEpisodeNumber(it) }
        card.year?.let { b.setReleaseDate(it.toString()) }
        return b.build()
    }

    // --- The channel ---

    private fun syncChannel(context: Context, cards: List<HomeCard>) {
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE)
        var id = prefs.getLong("channel", -1)
        // The viewer (or a reset launcher) may have removed it.
        if (id >= 0 && resolver.query(TvContractCompat.buildChannelUri(id), null, null, null, null)?.use { it.count } != 1) id = -1
        if (id < 0) {
            val channel = Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName("PlexVerse · New")
                .setAppLinkIntentUri(Uri.parse("${HomeLink.SCHEME}://open"))
                .setInternalProviderId(CHANNEL_ID)
                .build()
            id = ContentUris.parseId(resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()) ?: return)
            ContextCompat.getDrawable(context, R.drawable.icon)?.let { ChannelLogoUtils.storeChannelLogo(context, id, it.toBitmap(160, 160)) }
            // The app's first channel: the launcher shows it without asking.
            TvContractCompat.requestChannelBrowsable(context, id)
            prefs.edit().putLong("channel", id).apply()
        }
        // The channel's programs are replaced only when they changed (debounced above), so no flicker.
        resolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null)
        cards.forEachIndexed { i, card ->
            val b = PreviewProgram.Builder()
            b.setChannelId(id)
                .setType(if (card.kind == HomeCard.Kind.SHOW) TvContractCompat.PreviewPrograms.TYPE_TV_SERIES else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setTitle(card.title)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(card.link.uri())
                .setInternalProviderId(card.id)
                .setWeight(cards.size - i)
            card.image?.let { b.setPosterArtUri(Uri.parse(it)) }
            card.year?.let { b.setReleaseDate(it.toString()) }
            if (card.duration > 0 && card.kind == HomeCard.Kind.MOVIE) b.setDurationMillis(card.duration.toInt())
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, b.build().toContentValues())
        }
    }
}
