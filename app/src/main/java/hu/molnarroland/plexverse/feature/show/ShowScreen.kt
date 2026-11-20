@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.show

import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.embergabor.simplex.core.plex.Episodes
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.WideCard
import hu.molnarroland.plexverse.data.Copy
import hu.molnarroland.plexverse.data.Source
import hu.molnarroland.plexverse.data.Verse
import hu.molnarroland.plexverse.data.ViewPrefs
import hu.molnarroland.plexverse.data.queueEntry
import hu.molnarroland.plexverse.ui.ErrorMessage
import hu.molnarroland.plexverse.ui.ItemInfo
import hu.molnarroland.plexverse.ui.ItemMenu
import hu.molnarroland.plexverse.ui.LocalRefresh
import hu.molnarroland.plexverse.ui.Message
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.PinnedRow
import hu.molnarroland.plexverse.ui.SectionTitle
import hu.molnarroland.plexverse.ui.entersOnFirst
import hu.molnarroland.plexverse.ui.load
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** An episode merged across the servers that have the show, with its copy on each. */
/** An episode merged across the servers that have the show, with its copy on each (preferred first). */
class ShowEpisode(val key: String, val item: Item, val copies: List<Copy>) {
    val entry get() = queueEntry(copies)
    val server get() = copies.firstOrNull()?.server
}

/**
 * A show from every server in [copies] (in preference order): their episodes merged, each playing from
 * the first server that has it. Usually just one copy, on one server.
 */
@Composable
fun ShowScreen(copies: List<Copy>, nav: Nav) {
    if (copies.isEmpty()) return Message("None of the servers with this show can be reached right now.")
    val show = copies.first().item
    val refresh = LocalRefresh.current
    val result by load(copies, refresh) {
        val loaded = coroutineScope {
            copies.map { c ->
                async {
                    try {
                        val episodes = c.server.episodes(c.item.ratingKey)
                        val section = episodes.firstNotNullOfOrNull { it.librarySectionId } ?: c.item.librarySectionId
                        // Favourites are extra: if Plex can't list them, the show still opens.
                        val labelled = section?.let { runCatching { c.server.labelled(it, Episodes.FAVORITE, type = 4) }.getOrNull() }.orEmpty().map { it.ratingKey }.toSet()
                        Result.success(episodes to labelled)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.awaitAll()
        }
        // The servers that answered are enough; only when none did is it an error.
        if (loaded.none { it.isSuccess }) throw loaded.first().exceptionOrNull()!!
        // Sources are tagged with their copy's index, which is also the preference order.
        val sources = loaded.flatMapIndexed { i, r -> r.getOrNull()?.first.orEmpty().map { Source(i.toString(), it) } }
        val episodes = Verse.merge(sources, copies.indices.map { it.toString() }, Verse::episodeKeys)
            .sortedWith(compareBy(Episodes.order) { it.item })
            .map { e -> ShowEpisode(e.key, e.item, e.sources.map { Copy(copies[it.machineId.toInt()].server, it.item) }) }
        val favorites = episodes.filter { ep ->
            ep.copies.any { c -> Episodes.isFavorite(c.item) || c.item.ratingKey in loaded[copies.indexOfFirst { it.server == c.server }].getOrNull()?.second.orEmpty() }
        }
        episodes to favorites
    }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<ShowEpisode?>(null) }
    val context = LocalContext.current
    val blur = remember(refresh) { ViewPrefs(context).blurUnwatched }
    val (episodes, favorites) = result?.getOrElse { return ErrorMessage("Couldn't load ${show.title}.\n${it.message}") } ?: return
    if (episodes.isEmpty()) return Message("${show.title} has no episodes.")

    ShowContent(show, copies.first().server.image(show.thumb, 160, 240), episodes, favorites, blur, nav, memory, onMenu = { menuFor = it })
    menuFor?.let { m -> m.server?.let { server -> ItemMenu(server, m.item, nav, favorite = m in favorites, copies = m.copies) { menuFor = null } } }
}

// --- Other videos, by folder ---

/**
 * A show's page as shown: its [poster] and title with Play (the next episode), Shuffle and Shuffle
 * favorites, the [favorites] row, then a row per season. [blur] hides unstarted episodes' pictures.
 */
@Composable
fun ShowContent(
    show: Item,
    poster: String?,
    episodes: List<ShowEpisode>,
    favorites: List<ShowEpisode>,
    blur: Boolean,
    nav: Nav,
    memory: FocusMemory,
    onMenu: (ShowEpisode) -> Unit,
) {
    val next = Episodes.nextUp(episodes.map { it.item })
    val seasons = episodes.groupBy { it.item.parentIndex ?: 0 }.entries.toList()
    // The whole show is the queue, so previous and next can go anywhere in it.
    val playFrom = { i: Int, resume: Boolean -> nav.play(episodes.map { it.entry }, resume = resume, startAt = i, keepQueue = true) }

    LazyColumn(contentPadding = PaddingValues(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "header") {
            Row(
                Modifier.padding(horizontal = 48.dp).padding(bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Placeholder),
                )
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(show.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            label = "Play ${episodes[next].item.episodeLabel}",
                            icon = Icons.Play,
                            onClick = { playFrom(next, true) },
                            modifier = Modifier.remembersFocus(memory, "play", default = true),
                        )
                        PillButton(
                            label = "Shuffle",
                            icon = Icons.Shuffle,
                            onClick = { nav.play(episodes.shuffled().map { it.entry }, shuffle = true) },
                            modifier = Modifier.remembersFocus(memory, "shuffle"),
                        )
                        if (favorites.isNotEmpty()) {
                            PillButton(
                                label = "Shuffle favorites",
                                icon = Icons.Shuffle,
                                onClick = { nav.play(favorites.shuffled().map { it.entry }, shuffle = true) },
                                modifier = Modifier.remembersFocus(memory, "shuffleFavorites"),
                            )
                        }
                    }
                }
            }
        }
        // Played from here, favourites play on to the next favourite.
        if (favorites.isNotEmpty()) {
            item(key = "favorites") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("Favorites", Modifier.padding(horizontal = 48.dp))
                    PinnedRow {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            itemsIndexed(favorites, key = { _, ep -> ep.key }) { i, ep ->
                                WideCard(
                                    imageUrl = ep.server?.image(ep.item.thumb, 352, 198, blur = blur && ep.item.unseen),
                                    title = ep.item.title,
                                    subtitle = ep.item.episodeLabel,
                                    subtitleFirst = true,
                                    progress = ep.item.progress,
                                    watched = ep.item.watched,
                                    onClick = { nav.play(favorites.map { it.entry }, startAt = i, keepQueue = true) },
                                    prefetch = { ep.server?.prefetch(ep.item.ratingKey) },
                                    onLongClick = { onMenu(ep) },
                                    width = 176.dp,
                                    modifier = Modifier.remembersFocus(memory, "fav" + ep.key),
                                )
                            }
                        }
                    }
                }
            }
        }
        seasons.forEachIndexed { si, (number, eps) ->
            item(key = "season$number") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (si > 0 || favorites.isNotEmpty()) {
                        Box(Modifier.padding(horizontal = 48.dp).padding(bottom = 16.dp).fillMaxWidth().height(1.dp).background(Palette.Divider))
                    }
                    SectionTitle(if (number == 0) "Specials" else "Season $number", Modifier.padding(horizontal = 48.dp))
                    // Moving up or down into a season lands on its first episode, not the one below the last.
                    val firstEpisode = remember { FocusRequester() }
                    val rowState = rememberLazyListState()
                    PinnedRow {
                        LazyRow(
                            state = rowState,
                            modifier = Modifier.entersOnFirst(rowState, firstEpisode),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(eps, key = { it.key }) { ep ->
                                WideCard(
                                    imageUrl = ep.server?.image(ep.item.thumb, 352, 198, blur = blur && ep.item.unseen),
                                    title = ep.item.title,
                                    subtitle = listOfNotNull("Episode ${ep.item.index ?: ""}", ep.item.airDate?.let(ItemInfo::date)).joinToString(" · "),
                                    subtitleFirst = true,
                                    progress = ep.item.progress,
                                    watched = ep.item.watched,
                                    onClick = { playFrom(episodes.indexOf(ep), false) },
                                    prefetch = { ep.server?.prefetch(ep.item.ratingKey) },
                                    onLongClick = { onMenu(ep) },
                                    width = 176.dp,
                                    modifier = Modifier
                                        .then(if (ep == eps.first()) Modifier.focusRequester(firstEpisode) else Modifier)
                                        .remembersFocus(memory, ep.key),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@TvPreviews
@Composable
internal fun ShowPreview() = PreviewTheme {
    val episodes = (1..2).flatMap { season ->
        (1..6).map { e ->
            val watched = season == 1 && e < 5
            ShowEpisode("e$season-$e", Sample.item("e$season-$e", listOf("Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are", "The Grim Barbarity", "Hide and Seek")[e - 1],
                type = "episode", show = "Severance", season = season, episode = e, viewCount = if (watched) 1 else 0, viewOffset = if (season == 1 && e == 5) 900_000 else 0, duration = 3_200_000), emptyList())
        }
    }
    ShowContent(Sample.shows[1].item, null, episodes, episodes.filter { it.item.index == 3 || it.item.index == 6 }, blur = true, PreviewNav, rememberFocusMemory()) {}
}

