@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.category

import com.embergabor.simplex.core.plex.PlexServer
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.IconTile
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.CollectionView
import hu.molnarroland.plexverse.data.Collections
import hu.molnarroland.plexverse.data.GridCell
import hu.molnarroland.plexverse.data.GridSort
import hu.molnarroland.plexverse.data.ViewPrefs
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.data.SortPrefs
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.ui.EntryMenu
import hu.molnarroland.plexverse.ui.EntryPoster
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.PreloadImages
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.ScreenTitle
import hu.molnarroland.plexverse.ui.UpdatingBar
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import kotlinx.coroutines.launch

/**
 * One category's titles from every server, merged, with its sort (A–Z with a letter rail, Recently
 * added, Year, Unwatched first). An Other category's other-video libraries come first as folders.
 * ×N marks a title on N servers; the line at the bottom says which server the focused one plays from.
 * Titles of one Plex collection are framed together or folded into one tile, as set in Settings.
 */
@Composable
fun CategoryScreen(plex: PlexClient, category: String, profile: String, nav: Nav) {
    val context = LocalContext.current
    val store = remember { VerseStore.get(context, plex) }
    LaunchedEffect(Unit) { store.refresh() }
    val states by store.servers.collectAsState()
    val lists by store.lists.collectAsState()
    val connected by store.connected.collectAsState()
    val setup = remember { store.setup }
    val sortPrefs = remember { SortPrefs(context, profile) }
    var sort by remember { mutableStateOf(sortPrefs.sort(category)) }
    val merged = remember(lists) { store.entries(category, lists) }
    val entries = remember(merged, sort) { sort.apply(merged) }
    val collections by store.collections.collectAsState()
    val view = remember { ViewPrefs(context).collections }
    val cells = remember(entries, collections) { Collections.cells(entries, view) { store.collectionsOf(it, collections) } }
    val folders = remember { store.folders(category) }
    val names = remember { plex.cachedServers.associate { it.machineId to it.name } }
    val order = remember(connected) { store.order(names.keys + connected.keys) }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<VerseEntry?>(null) }
    CategoryContent(
        setup.name(category), cells, view, folders, sort, { sort = it; sortPrefs.setSort(category, it) },
        states, names, order, connected, nav, memory, onMenu = { menuFor = it },
    )
    menuFor?.let { entry -> EntryMenu(entry, connected, nav) { menuFor = null } }
}

/**
 * A category's grid as shown: [title] with the sort pills, [folders] (machineId, sectionKey, title) as
 * tiles, then [cells] as posters (collections as [view] says), an A–Z rail when sorted by title, and
 * which server the focused title plays from ([order] ranks the servers, [names] by machine id).
 */
@Composable
fun CategoryContent(
    title: String,
    cells: List<GridCell>,
    view: CollectionView,
    folders: List<Triple<String, String, String>>,
    sort: GridSort,
    onSort: (GridSort) -> Unit,
    states: Map<String, ServerState>,
    names: Map<String, String>,
    order: List<String>,
    connected: Map<String, PlexServer>,
    nav: Nav,
    memory: FocusMemory,
    onMenu: (VerseEntry) -> Unit,
) {
    val letters = remember(cells, sort) { if (sort == GridSort.TITLE) GridSort.letters(cells.map { it.lead.item }) else emptyList() }
    var drawer by remember { mutableStateOf<GridCell.Collection?>(null) }
    var focused by remember { mutableStateOf<VerseEntry?>(null) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var jumpTo by remember { mutableStateOf<String?>(null) }
    val headers = 1 + folders.size
    PreloadImages(grid, cells, connected) { i ->
        cells.getOrNull(i - headers)?.lead?.let { e -> e.sources.firstOrNull { it.machineId in connected }?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 48.dp, end = if (letters.isEmpty()) 48.dp else 64.dp, top = 40.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { ScreenTitle(title) }
                        GridSort.entries.forEach { option ->
                            PillButton(option.label, {
                                onSort(option)
                                scope.launch { grid.scrollToItem(0) }
                            }, selected = option == sort)
                        }
                    }
                    UpdatingBar(states, Modifier)
                    if (cells.isEmpty() && folders.isEmpty() && states.values.none { it == ServerState.Waiting }) {
                        Text("Nothing here yet. Libraries are moved here in Settings › Servers & categories.", fontSize = 13.sp, color = Palette.Secondary)
                    }
                }
            }
            items(folders, key = { (id, section, _) -> "f$id/$section" }, span = { GridItemSpan(2) }) { (id, section, title) ->
                val server = connected[id]
                val failed = states[id] is ServerState.Failed
                IconTile(
                    icon = Icons.Folder,
                    label = listOfNotNull(title, names[id], "not reachable".takeIf { failed }).joinToString("\n"),
                    onClick = { if (server != null) nav.push(Screen.Folder(Library(section, title, Library.Kind.OTHER), null, emptyList(), id)) },
                    horizontal = true,
                    modifier = Modifier.fillMaxWidth().height(72.dp).alpha(if (failed) 0.5f else 1f)
                        .remembersFocus(memory, "f$id/$section", default = cells.isEmpty() && folders.first().first == id && folders.first().second == section),
                )
            }
            items(
                cells,
                key = { it.key },
                span = { c -> GridItemSpan(if (c is GridCell.Collection && view == CollectionView.GROUPED) minOf(c.members.size, maxLineSpan) else 1) },
            ) { cell ->
                val jump = remember { FocusRequester() }
                if (jumpTo == cell.key) {
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { jump.requestFocus() }
                        jumpTo = null
                    }
                }
                val first = cell == cells.first()
                when {
                    cell is GridCell.Collection && view == CollectionView.GROUPED -> CollectionGroup(cell, connected, nav, onMenu, memory, first, jump) { focused = it }
                    cell is GridCell.Collection -> CollectionTile(
                        cell, connected, onOpen = { drawer = cell },
                        modifier = Modifier.focusRequester(jump)
                            .onFocusChanged { if (it.isFocused) focused = null }
                            .remembersFocus(memory, cell.key, default = first),
                    )
                    else -> EntryPoster(cell.lead, connected, nav, kids = false, onMenu = onMenu,
                        modifier = Modifier.focusRequester(jump)
                            .onFocusChanged { if (it.isFocused) focused = cell.lead }
                            .remembersFocus(memory, cell.key, default = first))
                }
            }
        }
        if (letters.isNotEmpty()) {
            LetterRail(letters, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) { first ->
                jumpTo = cells[first].key
                scope.launch { grid.scrollToItem(first + headers) }
            }
        }
        focused?.let { entry ->
            val from = entry.sources.firstOrNull { it.machineId in connected } ?: entry.sources.first()
            val rank = order.indexOf(from.machineId) + 1
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Ground.copy(alpha = 0.9f))))
                    .padding(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Plays from ${names[from.machineId] ?: "server"}${if (rank > 0) ", server $rank" else ""}", fontSize = 11.sp, color = Palette.Secondary)
                if (entry.sources.size > 1) Text("Hold OK › Play from…", fontSize = 11.sp, color = Palette.Secondary)
            }
        }
    }
    drawer?.let { c -> CollectionDrawer(c, connected, nav, onMenu) { drawer = null } }
}

@TvPreviews
@Composable
internal fun CategoryPreview() = PreviewTheme {
    CategoryContent(
        "Movies", Sample.movies.map { GridCell.Title(it) }, CollectionView.OFF, emptyList(), GridSort.TITLE, {}, Sample.servers, Sample.serverNames, listOf("nas", "office", "dad"),
        emptyMap(), PreviewNav, rememberFocusMemory(), onMenu = {},
    )
}

@TvPreviews
@Composable
internal fun OtherCategoryPreview() = PreviewTheme {
    CategoryContent(
        "Documentaries", GridSort.ADDED.apply(Sample.movies.drop(6).take(8)).map { GridCell.Title(it) }, CollectionView.OFF, listOf(Triple("office", "9", "Training"), Triple("nas", "4", "Home videos")),
        GridSort.ADDED, {}, emptyMap(), Sample.serverNames, listOf("nas", "office", "dad"), emptyMap(), PreviewNav, rememberFocusMemory(), onMenu = {},
    )
}

@TvPreviews
@Composable
internal fun CollectionsGroupedPreview() = PreviewTheme {
    CategoryContent(
        "Movies", Sample.collectionCells(CollectionView.GROUPED), CollectionView.GROUPED, emptyList(), GridSort.TITLE, {}, Sample.servers, Sample.serverNames,
        listOf("nas", "office", "dad"), emptyMap(), PreviewNav, rememberFocusMemory(), onMenu = {},
    )
}

@TvPreviews
@Composable
internal fun CollectionsCollapsedPreview() = PreviewTheme {
    CategoryContent(
        "Movies", Sample.collectionCells(CollectionView.COLLAPSED), CollectionView.COLLAPSED, emptyList(), GridSort.TITLE, {}, Sample.servers, Sample.serverNames,
        listOf("nas", "office", "dad"), emptyMap(), PreviewNav, rememberFocusMemory(), onMenu = {},
    )
}

@TvPreviews
@Composable
internal fun CollectionDrawerPreview() = PreviewTheme {
    val c = Sample.collectionCells(CollectionView.COLLAPSED).filterIsInstance<GridCell.Collection>().first()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { CollectionDrawerPanel(c, emptyMap(), PreviewNav, {}) }
}

private const val ALPHABET = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

private val LETTER_HEIGHT = 16.dp

/**
 * A–Z down the right edge, over the grid: every letter, the ones without titles dimmed. The focused
 * letter also shows big beside the rail. OK or ◀ jumps to the first title under it.
 */
@Composable
internal fun LetterRail(letters: List<Pair<Char, Int>>, modifier: Modifier, onJump: (Int) -> Unit) {
    val firsts = remember(letters) { letters.toMap() }
    var focused by remember { mutableStateOf<Int?>(null) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp)) {
            focused?.let { i ->
                Box(
                    Modifier
                        .offset(y = LETTER_HEIGHT * i + 6.dp + LETTER_HEIGHT / 2 - 32.dp)
                        .size(64.dp)
                        .background(Palette.Control.copy(alpha = 0.92f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(ALPHABET[i].toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Ink) }
            }
        }
        Column(
            Modifier.width(32.dp).background(Palette.Control.copy(alpha = 0.75f), CircleShape).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ALPHABET.forEachIndexed { i, letter ->
                val first = firsts[letter]
                if (first == null) {
                    Box(Modifier.size(24.dp, LETTER_HEIGHT), contentAlignment = Alignment.Center) {
                        Text(letter.toString(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary.copy(alpha = 0.4f))
                    }
                    return@forEachIndexed
                }
                Surface(
                    onClick = { onJump(first) },
                    modifier = Modifier
                        .size(24.dp, LETTER_HEIGHT)
                        .onFocusChanged { if (it.isFocused) focused = i else if (focused == i) focused = null }
                        .onPreviewKeyEvent { e ->
                            if (e.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                            if (e.type == KeyEventType.KeyDown) onJump(first)
                            true
                        },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Palette.Ink,
                        focusedContainerColor = Palette.Ink,
                        focusedContentColor = Palette.OnInk,
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(letter.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- One show: Play / Shuffle, its favourite episodes, then one row per season ---
