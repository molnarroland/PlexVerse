@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.tooling.preview.Preview
import com.embergabor.simplex.core.plex.PlexServer
import hu.molnarroland.plexverse.ui.preview.PartPreviews
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.Sample
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.PosterCard
import com.embergabor.simplex.core.ui.WideCard
import com.embergabor.simplex.core.ui.timeLeft
import hu.molnarroland.plexverse.data.Continuing
import hu.molnarroland.plexverse.data.Copy
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.data.VerseStore

/** A title's poster, from the first source whose server has answered. Shows open their page; the rest play. */
@Composable
fun EntryPoster(
    entry: VerseEntry,
    connected: Map<String, PlexServer>,
    nav: Nav,
    kids: Boolean,
    onMenu: (VerseEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reached = entry.sources.firstOrNull { it.machineId in connected }
    PosterCard(
        title = entry.item.title,
        imageUrl = reached?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) },
        backdrop = reached?.let { connected[it.machineId]?.image(it.item.art ?: it.item.thumb, 640, 360) },
        progress = entry.item.progress,
        watched = entry.item.watched,
        unwatched = entry.item.unwatchedEpisodes,
        badge = if (!kids && entry.sources.size > 1) "×${entry.sources.size}" else null,
        cornerIcon = if (kids && entry.item.type == "show") Icons.Tv else null,
        onClick = {
            if (entry.item.type == "show") nav.push(Screen.Show(entry.item, entry.sources))
            else nav.play(listOf(entry.sources.joinToString(",") { it.ref }))
        },
        // The menu acts on the copies on servers that answered, so it needs one. Kids get none.
        onLongClick = if (kids) null else ({ if (reached != null) onMenu(entry) }),
        prefetch = if (entry.item.type == "show") null else reached?.let { s -> { connected.getValue(s.machineId).prefetch(s.item.ratingKey) } },
        modifier = modifier,
    )
}

@Composable
fun EntryMenu(entry: VerseEntry, connected: Map<String, PlexServer>, nav: Nav, onDismiss: () -> Unit) {
    val copies = entry.sources.mapNotNull { s -> connected[s.machineId]?.let { Copy(it, s.item) } }
    if (copies.isEmpty()) return onDismiss()
    ItemMenu(copies.first().server, entry.item, nav, copies = copies, playFrom = true, onDismiss = onDismiss)
}

/** What's in progress on any server, newest first, from [store]. Kids get no hold-OK menu. */
@Composable
fun ContinueRow(store: VerseStore, kids: Boolean, nav: Nav, memory: FocusMemory, leading: (@Composable () -> Unit)? = null) {
    LaunchedEffect(LocalRefresh.current) { store.refreshContinuing() }
    val lists by store.lists.collectAsState()
    val continuing by store.continuing.collectAsState()
    val connected by store.connected.collectAsState()
    val ratings by store.ratings.collectAsState()
    val row = remember(lists, continuing, ratings) { store.continuingRow(lists, continuing, ratings) }
    var menuFor by remember { mutableStateOf<Continuing?>(null) }
    ContinueRowContent(row, connected, kids, nav, memory, leading, onMenu = { menuFor = it })
    menuFor?.let { c ->
        val copies = c.sources.mapNotNull { s -> connected[s.machineId]?.let { Copy(it, s.item) } }
        if (copies.isNotEmpty()) ItemMenu(copies.first().server, c.item, nav, continueWatching = true, copies = copies, playFrom = true) { menuFor = null }
        else menuFor = null
    }
}

/**
 * The Continue watching row: [row]'s cards, pictures from the [connected] servers. OK resumes; [leading]
 * (Kids' Surprise me!) comes first as one of the cards.
 */
@Composable
fun ContinueRowContent(
    row: List<Continuing>,
    connected: Map<String, PlexServer>,
    kids: Boolean,
    nav: Nav,
    memory: FocusMemory,
    leading: (@Composable () -> Unit)? = null,
    onMenu: (Continuing) -> Unit = {},
) {
    // A title just watched comes in first; the row would otherwise stay scrolled on the old first card.
    val rowState = rememberLazyListState()
    LaunchedEffect(row.firstOrNull()?.sources?.first()?.ref) { rowState.scrollToItem(0) }
    // Back from the player, focus returns to the card the video started from (see PlayedFrom).
    val playedFrom = rememberPlayedFrom()
    val keys = listOfNotNull("leading".takeIf { leading != null }) + row.map { it.sources.first().ref }
    LaunchedEffect(keys) { playedFrom.follow(rowState, keys) }
    if (row.isEmpty() && leading == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (row.isNotEmpty()) SectionTitle("Continue watching", Modifier.padding(horizontal = 48.dp))
        PinnedRow {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (leading != null) item(key = "leading") { leading() }
                items(row, key = { it.sources.first().ref }) { c ->
                    val item = c.item
                    val server = connected[c.sources.first().machineId]
                    WideCard(
                        imageUrl = server?.image(if (item.isEpisode) item.thumb else item.art ?: item.thumb, 400, 225),
                        backdrop = server?.image(item.art ?: item.thumb, 640, 360),
                        title = if (item.isEpisode) item.grandparentTitle ?: item.title else item.title,
                        subtitle = if (item.isEpisode) "${item.episodeLabel} · ${item.title}" else if (item.viewOffset > 0) timeLeft(item) else null,
                        progress = item.progress,
                        watched = false,
                        onClick = { playedFrom.played(c.sources.first().ref); nav.play(listOf(c.sources.joinToString(",") { it.ref }), resume = true) },
                        prefetch = server?.let { s -> { s.prefetch(c.sources.first().item.ratingKey) } },
                        onLongClick = if (kids) null else ({ onMenu(c) }),
                        width = 200.dp,
                        modifier = Modifier
                            .focusRequester(playedFrom.requester(c.sources.first().ref))
                            .remembersFocus(memory, "c" + c.sources.first().ref, default = leading == null && c == row.first()),
                    )
                }
            }
        }
    }
}

@PartPreviews
@Composable
internal fun ContinueRowPreview() = PreviewTheme {
    ContinueRowContent(Sample.continuing, emptyMap(), kids = false, PreviewNav, rememberFocusMemory())
}

@Preview(name = "Posters", widthDp = 960, heightDp = 260, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun EntryPostersPreview() = PreviewTheme {
    Row(Modifier.padding(horizontal = 48.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        (Sample.movies.take(3) + Sample.shows.take(2)).forEach { e ->
            Box(Modifier.width(120.dp)) { EntryPoster(e, emptyMap(), PreviewNav, kids = false, onMenu = {}) }
        }
        Box(Modifier.width(120.dp)) { EntryPoster(Sample.shows[0], emptyMap(), PreviewNav, kids = true, onMenu = {}) }
    }
}
