@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.ui.ContinueRow
import hu.molnarroland.plexverse.ui.EntryMenu
import hu.molnarroland.plexverse.ui.EntryPoster
import hu.molnarroland.plexverse.ui.LocalRefresh
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.Orbits
import hu.molnarroland.plexverse.ui.PinnedRow
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.SectionTitle
import hu.molnarroland.plexverse.ui.ServerLine
import hu.molnarroland.plexverse.ui.ServersBar
import hu.molnarroland.plexverse.ui.TopMenu
import hu.molnarroland.plexverse.ui.UpdatingBar
import hu.molnarroland.plexverse.ui.WithBackdrop
import hu.molnarroland.plexverse.ui.entersOnFirst
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus

/** Home: Continue watching across servers, then what's new in Movies, Series and the pinned category. */
@Composable
fun HomeScreen(plex: PlexClient, nav: Nav) {
    val context = LocalContext.current
    val store = remember { VerseStore.get(context, plex) }
    LaunchedEffect(Unit) { store.refresh() }
    val states by store.servers.collectAsState()
    val lists by store.lists.collectAsState()
    val connected by store.connected.collectAsState()
    val setup = remember(LocalRefresh.current) { store.setup }
    // The very first open waits for the servers (or for "Continue"); later ones start from the saved copy.
    var waiting by remember { mutableStateOf(!store.hasCache()) }
    val pending = states.values.count { it == ServerState.Waiting }
    if (waiting && (pending > 0 || states.isEmpty()) && setup.libraries.isNotEmpty()) {
        val names = remember { plex.cachedServers.associate { it.machineId to it.name } }
        return PrefetchPanel(states, names) { waiting = false }
    }

    val rows = remember(lists, setup) {
        listOfNotNull(VerseSetup.MOVIES, VerseSetup.SERIES, setup.pinned).map { id -> id to store.recentlyAdded(id, lists) }.filter { it.second.isNotEmpty() }
    }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<VerseEntry?>(null) }
    WithBackdrop {
        HomeContent(setup, states, rows, connected, nav, memory, focusRows = !hasContinuing(store), onMenu = { menuFor = it }) {
            ContinueRow(store, kids = false, nav, memory)
        }
    }
    menuFor?.let { entry -> EntryMenu(entry, connected, nav) { menuFor = null } }
}

private fun hasContinuing(store: VerseStore) = store.continuing.value.values.any { it.isNotEmpty() }

/**
 * Home as shown: the main menu, the servers still updating, [continueRow], then a row of posters per
 * category in [rows] (its newest titles). [focusRows]: the first poster takes focus (nothing in progress).
 */
@Composable
fun HomeContent(
    setup: VerseSetup,
    states: Map<String, ServerState>,
    rows: List<Pair<String, List<VerseEntry>>>,
    connected: Map<String, PlexServer>,
    nav: Nav,
    memory: FocusMemory,
    focusRows: Boolean,
    onMenu: (VerseEntry) -> Unit,
    continueRow: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TopMenu(Screen.Home, nav, setup = setup)
        UpdatingBar(states)
        continueRow()
        for ((id, entries) in rows) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("${setup.name(id)} · recently added", Modifier.padding(horizontal = 48.dp))
                val rowState = rememberLazyListState()
                val firstCard = remember { FocusRequester() }
                PinnedRow {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.entersOnFirst(rowState, firstCard),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            Box(Modifier.width(120.dp)) {
                                EntryPoster(entry, connected, nav, kids = false, onMenu = onMenu,
                                    modifier = Modifier
                                        .then(if (entry == entries.first()) Modifier.focusRequester(firstCard) else Modifier)
                                        .remembersFocus(memory, "$id/${entry.key}", default = focusRows && rows.first().first == id && entry == entries.first()))
                            }
                        }
                    }
                }
            }
        }
        if (rows.isEmpty() && states.values.none { it == ServerState.Waiting }) {
            Text(
                if (setup.libraries.isEmpty()) "No libraries yet. Check Settings › Servers & categories." else "Nothing in Movies or Series yet.",
                Modifier.padding(horizontal = 48.dp), fontSize = 13.sp, color = Palette.Secondary,
            )
        }
    }
}

/** The first fetch from every server: the orbits, a bar, then a line per server ([names] by machine id). */
@Composable
fun PrefetchPanel(states: Map<String, ServerState>, names: Map<String, String>, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Getting the lists from your servers")
        Orbits(states.values.toList(), Modifier.align(Alignment.CenterHorizontally).size(360.dp, 200.dp))
        ServersBar(states.values.count { it != ServerState.Waiting }, states.size.coerceAtLeast(1))
        for ((id, state) in states) ServerLine(names[id] ?: id, state)
        PillButton("Continue with what's here", onContinue, Modifier.padding(top = 10.dp).remembersFocus(rememberFocusMemory(), "continue", default = true))
    }
}

@TvPreviews
@Composable
internal fun HomePreview() = PreviewTheme {
    val memory = rememberFocusMemory()
    val rows = listOf(VerseSetup.MOVIES to Sample.movies.take(9), VerseSetup.SERIES to Sample.shows)
    HomeContent(Sample.setup, Sample.servers, rows, emptyMap(), PreviewNav, memory, focusRows = false, onMenu = {}) {
        ContinueRowContent(Sample.continuing, emptyMap(), kids = false, PreviewNav, memory)
    }
}

@TvPreviews
@Composable
internal fun PrefetchPreview() = PreviewTheme { PrefetchPanel(Sample.servers, Sample.serverNames) {} }
