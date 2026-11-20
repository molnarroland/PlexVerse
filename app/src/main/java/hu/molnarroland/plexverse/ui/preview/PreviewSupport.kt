package hu.molnarroland.plexverse.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.ui.AppTheme
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.ThemeMode
import hu.molnarroland.plexverse.data.Continuing
import hu.molnarroland.plexverse.data.LibraryChoice
import hu.molnarroland.plexverse.data.OtherCategory
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.data.Source
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.profile.Profile
import hu.molnarroland.plexverse.ui.Backdrop
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.theme.Look
import hu.molnarroland.plexverse.ui.theme.Sky
import com.embergabor.simplex.core.plex.Library

/** A TV screen (960×540 dp, as a 1080p TV reports itself), at night and by day. */
@Preview(name = "Night", device = TV, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_TELEVISION)
@Preview(name = "Day", device = TV, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_TELEVISION)
annotation class TvPreviews

/** A piece of a screen (a row, a menu, a mark) on the sky, at night and by day. */
@Preview(name = "Night", widthDp = 960, heightDp = 200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Day", widthDp = 960, heightDp = 200, uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class PartPreviews

const val TV = "spec:width=960dp,height=540dp,dpi=320"

/**
 * PlexVerse's look for a preview: the night palette when the preview's uiMode is night, the day one
 * otherwise, with the sky behind (mint for [kids]). It sets simplex-core's global palette, as the app
 * does, so the core's cards follow too.
 */
@Composable
fun PreviewTheme(kids: Boolean = false, night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    Palette.mode = if (night) ThemeMode.Dark else ThemeMode.Light
    Palette.current = if (night) Look.Night else Look.Day
    Sky.kids = kids
    AppTheme {
        Box(Modifier.fillMaxSize()) {
            Backdrop()
            content()
        }
    }
}

/** Navigation that goes nowhere. */
object PreviewNav : Nav {
    override fun push(screen: Screen) {}
    override fun play(ratingKeys: List<String>, resume: Boolean, shuffle: Boolean, startAt: Int, keepQueue: Boolean) {}
    override fun signOut() {}
}

/** Made-up titles, servers and profiles for previews. Nothing here talks to a server; posters stay empty. */
object Sample {
    fun item(
        key: String, title: String, type: String = "movie", year: Int? = null, addedAt: Long = 0,
        viewOffset: Long = 0, viewCount: Int = 0, duration: Long = 6_600_000, show: String? = null,
        season: Int? = null, episode: Int? = null, leafCount: Int = 0, viewedLeafCount: Int = 0,
    ) = Item(
        ratingKey = key, type = type, title = title, thumb = null, art = null, grandparentRatingKey = show?.let { "s-$it" },
        grandparentTitle = show, parentIndex = season, index = episode, viewOffset = viewOffset, viewCount = viewCount,
        duration = duration, leafCount = leafCount, viewedLeafCount = viewedLeafCount, librarySectionId = "1", media = emptyList(),
        year = year, addedAt = addedAt, airDate = "2024-03-0${(episode ?: 1).coerceIn(1, 9)}",
    )

    val serverNames = mapOf("nas" to "NAS", "office" to "Office", "dad" to "Dad's server")

    val servers: Map<String, ServerState> = mapOf(
        "nas" to ServerState.Done(812), "office" to ServerState.Waiting, "dad" to ServerState.Failed("not answering"),
    )

    private val movieTitles = listOf(
        "Alien" to 1979, "Amélie" to 2001, "Arrival" to 2016, "Blade Runner" to 1982, "Brazil" to 1985, "Cube" to 1997,
        "Dune" to 2021, "Heat" to 1995, "Her" to 2013, "Inception" to 2010, "Jaws" to 1975, "Moon" to 2009,
        "Paddington" to 2014, "Spirited Away" to 2001, "The Iron Giant" to 1999, "Up" to 2009, "Wall-E" to 2008, "Zodiac" to 2007,
    )

    val movies: List<VerseEntry> = movieTitles.mapIndexed { i, (title, year) ->
        val item = item("m$i", title, year = year, addedAt = 1_700_000_000L + i * 7919 % 97 * 3600, viewCount = if (i % 5 == 0) 1 else 0, viewOffset = if (i == 3) 2_400_000 else 0)
        val sources = listOf(Source("nas", item)) + if (i % 3 == 0) listOf(Source("office", item.copy(ratingKey = "o$i"))) else emptyList()
        VerseEntry("m$i", item, sources)
    }

    val shows: List<VerseEntry> = listOf("Bluey", "Severance", "Slow Horses", "The Bear", "Top Gear", "Taskmaster").mapIndexed { i, title ->
        val item = item("s$i", title, type = "show", year = 2015 + i, leafCount = 20 + i * 4, viewedLeafCount = i * 3)
        VerseEntry("s$i", item, listOf(Source(if (i % 2 == 0) "nas" else "dad", item)))
    }

    val kids: List<VerseEntry> = (movies.filter { it.item.title in setOf("Paddington", "Spirited Away", "The Iron Giant", "Up", "Wall-E") } + shows.take(1))
        .sortedBy { it.item.title }

    val continuing: List<Continuing> = listOf(
        Continuing(item("e1", "The You You Are", type = "episode", show = "Severance", season = 2, episode = 1, viewOffset = 1_200_000, duration = 3_000_000), listOf(Source("nas", item("e1", "x")))),
        Continuing(movies[3].item, movies[3].sources),
        Continuing(item("e2", "Keepy Uppy", type = "episode", show = "Bluey", season = 1, episode = 4, viewOffset = 200_000, duration = 420_000), listOf(Source("office", item("e2", "x")))),
    )

    val setup = VerseSetup(
        libraries = mapOf(
            "nas/1" to LibraryChoice(VerseSetup.MOVIES, "Movies", Library.Kind.MOVIES),
            "nas/2" to LibraryChoice(VerseSetup.SERIES, "TV Shows", Library.Kind.SHOWS),
            "nas/3" to LibraryChoice("c1", "Docs", Library.Kind.MOVIES),
            "office/1" to LibraryChoice(VerseSetup.MOVIES, "4K", Library.Kind.MOVIES),
            "dad/1" to LibraryChoice(VerseSetup.MOVIES, "Films", Library.Kind.MOVIES),
            "dad/2" to LibraryChoice(VerseSetup.SERIES, "Series", Library.Kind.SHOWS),
            "dad/5" to LibraryChoice("c2", "Anime", Library.Kind.SHOWS),
            "office/9" to LibraryChoice(VerseSetup.HIDDEN, "Training", Library.Kind.OTHER),
        ),
        others = listOf(OtherCategory("c1", "Documentaries"), OtherCategory("c2", "Anime")),
        pinned = "c1",
        confirmed = true,
    )

    val profiles = listOf(
        Profile("aaa", "Roland", admin = true),
        Profile("bbb", "Anna", protected = true),
        Profile("ccc", "Bori", restricted = true),
        Profile(Profile.LOCAL_KIDS, "Peti"),
    )
}
