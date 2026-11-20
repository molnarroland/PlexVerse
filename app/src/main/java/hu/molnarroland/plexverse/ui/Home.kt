@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.IconTile
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.PosterCard
import com.embergabor.simplex.core.ui.WideCard
import com.embergabor.simplex.core.ui.timeLeft
import hu.molnarroland.plexverse.update.Updates
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The main menu, a capsule in the middle: the mark, Search, Home, Movies, Series, the pinned category,
 * Others (a popup of the rest) and Settings. Its width never grows past one pinned category.
 */
@Composable
fun TopMenu(current: Screen, nav: Nav, modifier: Modifier = Modifier) {
    val setup = SetupPrefs(LocalContext.current).setup
    var others by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.background(Palette.Control.copy(alpha = 0.72f), CircleShape).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(Modifier.padding(horizontal = 6.dp).size(26.dp), animated = false)
            MenuItem(null, Icons.Search, "Search", current == Screen.Search) { nav.push(Screen.Search) }
            MenuItem("Home", null, null, current == Screen.Home) { nav.push(Screen.Home) }
            MenuItem(setup.moviesName, null, null, current == Screen.Category(VerseSetup.MOVIES)) { nav.push(Screen.Category(VerseSetup.MOVIES)) }
            MenuItem(setup.seriesName, null, null, current == Screen.Category(VerseSetup.SERIES)) { nav.push(Screen.Category(VerseSetup.SERIES)) }
            setup.pinnedOther?.let { p -> MenuItem(p.name, null, null, current == Screen.Category(p.id)) { nav.push(Screen.Category(p.id)) } }
            val inOthers = current is Screen.Category && setup.others.any { it.id == current.id && it.id != setup.pinned }
            MenuItem("Others", KidsLook.Others, null, inOthers) { others = true }
            MenuItem(null, Icons.Settings, "Settings", current == Screen.Settings) { nav.push(Screen.Settings) }
        }
    }
    if (others) OthersPopup(setup, nav) { others = false }
}

/** One capsule in [TopMenu]: underlined when it's the current screen, filled with the accent when focused. */
@Composable
private fun MenuItem(label: String?, icon: ImageVector?, description: String?, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = if (label == null) 12.dp else 15.dp),
        shape = ButtonDefaults.shape(CircleShape),
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.Ink,
            focusedContainerColor = Look.Accent,
            focusedContentColor = Color.White,
        ),
    ) {
        val line = if (selected) Look.AccentSoft else Color.Transparent
        Row(
            Modifier.drawBehind {
                if (selected) drawRoundRect(line, Offset(0f, size.height + 2.dp.toPx()), Size(size.width, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = description, modifier = Modifier.size(if (label == null) 15.dp else 13.dp))
            if (icon != null && label != null) Spacer(Modifier.width(6.dp))
            if (label != null) Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Others: every category you made, ★ for the pinned one, then Manage categories. */
@Composable
private fun OthersPopup(setup: VerseSetup, nav: Nav, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(300.dp).background(Palette.Tile, RoundedCornerShape(14.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Others", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            if (setup.others.isEmpty()) Text("No categories of your own yet.", fontSize = 12.sp, color = Palette.Secondary, modifier = Modifier.padding(start = 4.dp))
            setup.others.forEachIndexed { i, c ->
                PillButton(
                    c.name + if (c.id == setup.pinned) "  ★" else "",
                    { onDismiss(); nav.push(Screen.Category(c.id)) },
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                )
            }
            PillButton(
                "Manage categories…", { onDismiss(); nav.push(Screen.ManageCategories) },
                Modifier.fillMaxWidth().then(if (setup.others.isEmpty()) Modifier.focusRequester(first) else Modifier),
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

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
    val update by produceState<Updates.Release?>(null) { value = Updates.newerCached() }
    // The very first open waits for the servers (or for "Continue"); later ones start from the saved copy.
    var waiting by remember { mutableStateOf(!store.hasCache()) }
    val pending = states.values.count { it == ServerState.Waiting }
    if (waiting && (pending > 0 || states.isEmpty()) && setup.libraries.isNotEmpty()) return PrefetchPanel(plex, states) { waiting = false }

    val rows = remember(lists, setup) {
        listOfNotNull(VerseSetup.MOVIES, VerseSetup.SERIES, setup.pinned).map { id -> id to store.recentlyAdded(id, lists) }.filter { it.second.isNotEmpty() }
    }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<VerseEntry?>(null) }
    WithBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TopMenu(Screen.Home, nav)
            UpdatingBar(states)
            update?.let { release ->
                Row(Modifier.padding(horizontal = 48.dp)) { PillButton("Update to ${release.version}", { nav.push(Screen.Settings) }, icon = Icons.Next) }
            }
            ContinueRow(store, kids = false, nav, memory)
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
                                    EntryPoster(entry, connected, nav, kids = false, onMenu = { menuFor = it },
                                        modifier = Modifier
                                            .then(if (entry == entries.first()) Modifier.focusRequester(firstCard) else Modifier)
                                            .remembersFocus(memory, "$id/${entry.key}", default = rows.first().first == id && entry == entries.first() && !hasContinuing(store)))
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
    menuFor?.let { entry -> EntryMenu(entry, connected, nav) { menuFor = null } }
}

private fun hasContinuing(store: VerseStore) = store.continuing.value.values.any { it.isNotEmpty() }

/** A title's poster, from the first source whose server has answered. Shows open their page; the rest play. */
@Composable
fun EntryPoster(
    entry: VerseEntry,
    connected: Map<String, com.embergabor.simplex.core.plex.PlexServer>,
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
fun EntryMenu(entry: VerseEntry, connected: Map<String, com.embergabor.simplex.core.plex.PlexServer>, nav: Nav, onDismiss: () -> Unit) {
    val copies = entry.sources.mapNotNull { s -> connected[s.machineId]?.let { Copy(it, s.item) } }
    if (copies.isEmpty()) return onDismiss()
    ItemMenu(copies.first().server, entry.item, nav, copies = copies, playFrom = true, onDismiss = onDismiss)
}

/** What's in progress on any server, newest first. Kids get no hold-OK menu. */
@Composable
fun ContinueRow(store: VerseStore, kids: Boolean, nav: Nav, memory: FocusMemory, leading: (@Composable () -> Unit)? = null) {
    LaunchedEffect(LocalRefresh.current) { store.refreshContinuing() }
    val lists by store.lists.collectAsState()
    val continuing by store.continuing.collectAsState()
    val connected by store.connected.collectAsState()
    val ratings by store.ratings.collectAsState()
    val row = remember(lists, continuing, ratings) { store.continuingRow(lists, continuing, ratings) }
    var menuFor by remember { mutableStateOf<Continuing?>(null) }
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
                // Kids' Surprise me! comes first, as one of the cards.
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
                        onLongClick = if (kids) null else ({ menuFor = c }),
                        width = 200.dp,
                        modifier = Modifier
                            .focusRequester(playedFrom.requester(c.sources.first().ref))
                            .remembersFocus(memory, "c" + c.sources.first().ref, default = leading == null && c == row.first()),
                    )
                }
            }
        }
    }
    menuFor?.let { c ->
        val copies = c.sources.mapNotNull { s -> connected[s.machineId]?.let { Copy(it, s.item) } }
        if (copies.isNotEmpty()) ItemMenu(copies.first().server, c.item, nav, continueWatching = true, copies = copies, playFrom = true) { menuFor = null }
        else menuFor = null
    }
}

/** The first fetch from every server: the orbits, a bar, then a line per server. */
@Composable
private fun PrefetchPanel(plex: PlexClient, states: Map<String, ServerState>, onContinue: () -> Unit) {
    val names = remember { plex.cachedServers.associate { it.machineId to it.name } }
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

@Composable
fun ServerLine(name: String, state: ServerState, done: String? = null) {
    val (dot, text) = when (state) {
        ServerState.Waiting -> Palette.Secondary to "…"
        is ServerState.Done -> Look.Good to (done ?: "${state.items} titles")
        is ServerState.Failed -> Look.Bad to state.reason
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text("$name   $text", fontSize = 13.sp, color = Palette.Ink)
    }
}

/**
 * The PlexVerse mark with each server as a satellite on one of three tilted orbits. Waiting servers
 * pulse, answered ones shine, failed ones turn grey. Satellites pass behind the planet.
 */
@Composable
fun Orbits(states: List<ServerState>, modifier: Modifier, planet: Dp = 72.dp, satellite: Dp = 7.dp) {
    val spin = rememberInfiniteTransition(label = "orbits")
    val turn by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "turn")
    val pulse by spin.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val ring = Palette.Divider
    val shine = Look.AccentSoft
    val failed = Palette.Secondary
    val night = Look.night
    Canvas(modifier) {
        val rings = (0 until minOf(3, states.size.coerceAtLeast(1)))
        fun radii(r: Int) = Size(size.width * (0.26f + 0.11f * r), size.height * (0.17f + 0.08f * r))
        for (r in rings) {
            val (rx, ry) = radii(r)
            drawOval(ring, topLeft = center - Offset(rx, ry), size = Size(2 * rx, 2 * ry), style = Stroke(1.dp.toPx()))
        }
        val sats = states.mapIndexed { i, state ->
            val r = i % 3
            val (rx, ry) = radii(r)
            val a = 2 * PI.toFloat() * (turn * (3 - r) + i / states.size.toFloat())
            Triple(center + Offset(cos(a) * rx, sin(a) * ry), sin(a), state)
        }
        fun sat(at: Offset, depth: Float, state: ServerState) {
            val s = satellite.toPx() * (1 + depth / 3)
            val (color, alpha) = when (state) {
                ServerState.Waiting -> shine to pulse
                is ServerState.Done -> shine to 1f
                is ServerState.Failed -> failed to 0.6f
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f * alpha), Color.Transparent), at, s * 2.4f), s * 2.4f, at)
            drawCircle(color.copy(alpha = alpha), s / 2, at)
        }
        sats.filter { it.second < 0 }.forEach { sat(it.first, it.second, it.third) }
        val p = planet.toPx()
        inset(center.x - p / 2, center.y - p / 2) { drawMark(night, turn, pulse - 0.35f, 0f) }
        sats.filter { it.second >= 0 }.forEach { sat(it.first, it.second, it.third) }
    }
}

/** "Updating · 3/5 servers" with small orbits while a refresh runs; nothing otherwise. */
@Composable
fun UpdatingBar(states: Map<String, ServerState>, modifier: Modifier = Modifier.padding(horizontal = 48.dp)) {
    val done = states.values.count { it != ServerState.Waiting }
    if (done == states.size) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Orbits(states.values.toList(), Modifier.size(52.dp, 26.dp), planet = 14.dp, satellite = 2.5.dp)
        Text("Updating · $done/${states.size} servers", fontSize = 11.sp, color = Palette.Secondary)
    }
}

/** "3 of 5 servers responded", with a bar. */
@Composable
fun ServersBar(answered: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$answered of $total servers responded", fontSize = 12.sp, color = Palette.Secondary)
        Box(Modifier.width(260.dp).height(4.dp).background(Palette.Control, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(answered.toFloat() / total.coerceAtLeast(1)).background(Look.Accent, RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * One category's titles from every server, merged, with its sort (A–Z with a letter rail, Recently
 * added, Year, Unwatched first). An Other category's other-video libraries come first as folders.
 * ×N marks a title on N servers; the line at the bottom says which server the focused one plays from.
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
    val folders = remember { store.folders(category) }
    val letters = remember(entries, sort) { if (sort == GridSort.TITLE) GridSort.letters(entries.map { it.item }) else emptyList() }
    val names = remember { plex.cachedServers.associate { it.machineId to it.name } }
    val order = remember(connected) { store.order(names.keys + connected.keys) }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<VerseEntry?>(null) }
    var focused by remember { mutableStateOf<VerseEntry?>(null) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var jumpTo by remember { mutableStateOf<String?>(null) }
    val headers = 1 + folders.size
    PreloadImages(grid, entries, connected) { i ->
        entries.getOrNull(i - headers)?.let { e -> e.sources.firstOrNull { it.machineId in connected }?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) } }
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
                        Box(Modifier.weight(1f)) { ScreenTitle(setup.name(category)) }
                        GridSort.entries.forEach { option ->
                            PillButton(option.label, {
                                sort = option
                                sortPrefs.setSort(category, option)
                                scope.launch { grid.scrollToItem(0) }
                            }, selected = option == sort)
                        }
                    }
                    UpdatingBar(states, Modifier)
                    if (entries.isEmpty() && folders.isEmpty() && states.values.none { it == ServerState.Waiting }) {
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
                        .remembersFocus(memory, "f$id/$section", default = entries.isEmpty() && folders.first().first == id && folders.first().second == section),
                )
            }
            items(entries, key = { it.key }) { entry ->
                val jump = remember { FocusRequester() }
                if (jumpTo == entry.key) {
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { jump.requestFocus() }
                        jumpTo = null
                    }
                }
                EntryPoster(entry, connected, nav, kids = false, onMenu = { menuFor = it },
                    modifier = Modifier.focusRequester(jump)
                        .onFocusChanged { if (it.isFocused) focused = entry }
                        .remembersFocus(memory, entry.key, default = entry == entries.first()))
            }
        }
        if (letters.isNotEmpty()) {
            LetterRail(letters, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) { first ->
                jumpTo = entries[first].key
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
    menuFor?.let { entry -> EntryMenu(entry, connected, nav) { menuFor = null } }
}
