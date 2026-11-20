@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.kids

import hu.molnarroland.plexverse.data.VerseEntry
import com.embergabor.simplex.core.plex.PlexServer
import hu.molnarroland.plexverse.ui.ContinueRowContent
import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.SearchMatch
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.ui.ContinueRow
import hu.molnarroland.plexverse.ui.EntryPoster
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.PreloadImages
import hu.molnarroland.plexverse.ui.UpdatingBar
import hu.molnarroland.plexverse.ui.WithBackdrop
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.Logo
import hu.molnarroland.plexverse.ui.theme.Sky

/**
 * Kids mode, the whole app for a Kids profile: no menu with ways out, no Settings, and Back does
 * nothing (a grown-up holds it for 3 seconds, see [KidsGate]). Big posters of the profile's own
 * libraries within its age ceiling, Surprise me!, and the child's own Continue watching. Words from the
 * remote's mic narrow the posters down ([query]).
 */
@Composable
fun KidsHome(plex: PlexClient, nav: Nav, name: String, query: String?, onClearQuery: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VerseStore.get(context, plex) }
    LaunchedEffect(Unit) { store.refresh() }
    DisposableEffect(Unit) {
        Sky.kids = true
        onDispose { Sky.kids = false }
    }
    val states by store.servers.collectAsState()
    val lists by store.lists.collectAsState()
    val ratings by store.ratings.collectAsState()
    val connected by store.connected.collectAsState()
    val all = remember(lists, ratings) { store.kidsEntries(lists, ratings) }
    val entries = remember(all, query) {
        if (query.isNullOrBlank()) all
        else SearchMatch.terms(query).let { terms -> all.filter { SearchMatch.matches(terms, SearchMatch.Text(it.item.title)) } }
    }
    val memory = rememberFocusMemory()
    var surprise by remember { mutableStateOf(false) }
    KidsHomeContent(name, entries, all, query, onClearQuery, states, connected, nav, memory, onSurprise = { surprise = true }) { leading ->
        ContinueRow(store, kids = true, nav, memory, leading)
    }
    if (surprise) Surprise(all, connected, nav) { surprise = false }
}

/**
 * Kids mode as shown: a hello for [name], [continueRow] (handed Surprise me! to put first), the
 * posters of [entries] (all of [all], or those matching [query]) and the grown-ups' hint.
 */
@Composable
fun KidsHomeContent(
    name: String,
    entries: List<VerseEntry>,
    all: List<VerseEntry>,
    query: String?,
    onClearQuery: () -> Unit,
    states: Map<String, ServerState>,
    connected: Map<String, PlexServer>,
    nav: Nav,
    memory: FocusMemory,
    onSurprise: () -> Unit,
    continueRow: @Composable (leading: (@Composable () -> Unit)?) -> Unit,
) {
    val grid = rememberLazyGridState()
    PreloadImages(grid, entries, connected) { i ->
        entries.getOrNull(i - 2)?.let { e -> e.sources.firstOrNull { it.machineId in connected }?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) } }
    }

    WithBackdrop {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hi $name!", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                        Logo(Modifier.padding(end = 8.dp).size(48.dp))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    // The grid has its own side margins, so the row goes without the extra ones.
                    Box(Modifier.layout { m, c ->
                        val bleed = 48.dp.roundToPx()
                        val p = m.measure(c.copy(maxWidth = c.maxWidth + 2 * bleed))
                        layout(c.maxWidth, p.height) { p.place(-bleed, 0) }
                    }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            continueRow(if (all.isEmpty()) null else {
                                { SurpriseButton(onSurprise, Modifier.remembersFocus(memory, "surprise", default = true)) }
                            })
                            UpdatingBar(states)
                            if (!query.isNullOrBlank()) {
                                Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("“$query”", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                                    PillButton("Show everything", onClearQuery)
                                }
                            }
                        }
                    }
                }
                if (entries.isEmpty() && states.values.none { it == ServerState.Waiting }) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (all.isEmpty()) "Nothing here yet. A grown-up picks the libraries in Settings › Kids profiles." else "Nothing found.",
                            fontSize = 15.sp, color = Palette.Secondary,
                        )
                    }
                }
                items(entries, key = { it.key }) { entry ->
                    EntryPoster(entry, connected, nav, kids = true, onMenu = {}, modifier = Modifier.remembersFocus(memory, entry.key))
                }
            }
            Text(
                "Grown-ups: hold Back for 3 seconds",
                Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 14.dp),
                fontSize = 10.sp, color = Palette.Secondary,
            )
        }
    }
}

@TvPreviews
@Composable
internal fun KidsHomePreview() = PreviewTheme(kids = true) {
    val memory = rememberFocusMemory()
    KidsHomeContent("Bori", Sample.kids, Sample.kids, null, {}, emptyMap(), emptyMap(), PreviewNav, memory, onSurprise = {}) { leading ->
        ContinueRowContent(Sample.continuing.drop(2), emptyMap(), kids = true, PreviewNav, memory, leading)
    }
}

@TvPreviews
@Composable
internal fun KidsSearchPreview() = PreviewTheme(kids = true) {
    val memory = rememberFocusMemory()
    KidsHomeContent("Bori", Sample.kids.take(2), Sample.kids, "paddington", {}, emptyMap(), emptyMap(), PreviewNav, memory, onSurprise = {}) { leading ->
        ContinueRowContent(emptyList(), emptyMap(), kids = true, PreviewNav, memory, leading)
    }
}

/**
 * Kids' playing: every queue is cut to the profile's episode limit, and kept as given, so the player
 * doesn't fill it with the rest of the show.
 */
fun Nav.forKids(limit: Int): Nav {
    val nav = this
    return object : Nav by nav {
        override fun play(ratingKeys: List<String>, resume: Boolean, shuffle: Boolean, startAt: Int, keepQueue: Boolean) {
            val from = ratingKeys.drop(startAt)
            nav.play(if (limit > 0) from.take(limit) else from, resume, shuffle, 0, keepQueue = true)
        }
    }
}
