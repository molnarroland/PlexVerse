@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.plex.PlexServer
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.WideCard
import com.embergabor.simplex.core.ui.formatDuration
import com.embergabor.simplex.core.ui.okKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import hu.molnarroland.plexverse.ui.SearchMatch.Text as Searchable

private const val SEARCH_HINT = "Search titles, or words from an episode's description"
private val fieldShape = RoundedCornerShape(12.dp)

/** Episodes listed per show at most; a broader word than that needs narrowing down anyway. */
private const val MAX_EPISODES = 100

/** What the remote's mic heard, for Search (or Kids mode) to take as its query (set by MainActivity). */
object VoiceSearch {
    var heard by mutableStateOf<String?>(null)
}

/** One server library's episodes (TV) or videos (other videos), prepared for matching. */
private class LibraryIndex(
    val key: String,
    val kind: Library.Kind,
    val items: List<Triple<Item, Searchable, Searchable?>>,
)

/** The matches: merged titles per category, other videos per library, and episodes per show. */
private class Found(
    val titles: List<Pair<String, List<VerseEntry>>>,
    val videos: List<Pair<String, List<Copy>>>,
    val shows: List<Pair<String, List<Copy>>>,
) {
    val isEmpty get() = titles.isEmpty() && videos.isEmpty() && shows.isEmpty()
}

/**
 * The copy of every server's episode and video lists kept on the TV, so Search can match straight away
 * and refresh quietly. One file per library ("machineId/sectionKey").
 */
private class SearchCache(context: Context) {
    private val dir = File(context.cacheDir, "search")
    private fun file(key: String) = File(dir, key.replace('/', '-') + ".json")

    fun read(key: String): String? = file(key).takeIf { it.exists() }?.readText()

    fun write(key: String, json: String) {
        dir.mkdirs()
        val tmp = File(file(key).path + ".part")
        tmp.writeText(json)
        tmp.renameTo(file(key))
    }
}

private fun index(key: String, kind: Library.Kind, json: String): LibraryIndex {
    val items = PlexClient.parseItems(json)
    val prepared = if (kind == Library.Kind.SHOWS) {
        items.sortedWith(compareBy({ it.grandparentTitle }, { it.parentIndex ?: 0 }, { it.index ?: 0 }))
            .map { Triple(it, Searchable(it.title + "\n" + (it.summary ?: "")), Searchable(it.grandparentTitle ?: "")) }
    } else {
        items.map { Triple(it, Searchable(it.title), null) }
    }
    return LibraryIndex(key, kind, prepared)
}

/**
 * Search across every server and category in use (Hidden is left out). Movies and shows match by
 * title, merged like their grids; episodes also by their description, so "bolivia" finds the Top Gear
 * special. Matching runs on the TV against a saved copy, refreshed in the background on opening.
 */
@Composable
fun SearchScreen(plex: PlexClient, nav: Nav) {
    val context = LocalContext.current
    val store = remember { VerseStore.get(context, plex) }
    LaunchedEffect(Unit) { store.refresh() }
    val lists by store.lists.collectAsState()
    val connected by store.connected.collectAsState()
    val setup = remember { store.setup }
    val blur = remember { ViewPrefs(context).blurUnwatched }
    val cache = remember { SearchCache(context) }
    var indexes by remember { mutableStateOf<Map<String, LibraryIndex>>(emptyMap()) }
    val refresh = LocalRefresh.current

    // Episode and video lists: the saved copy first, then the current ones from each server reached.
    val wanted = remember(setup) {
        setup.libraries.filter { (_, c) -> c.category != VerseSetup.HIDDEN && c.kind != Library.Kind.MOVIES }
    }
    // Each library is fetched once per visit, as its server is reached.
    val fetched = remember(refresh) { HashSet<String>() }
    LaunchedEffect(refresh, connected.keys) {
        val saved = withContext(Dispatchers.Default) {
            wanted.mapNotNull { (k, c) -> if (k in indexes) null else cache.read(k)?.let { k to index(k, c.kind, it) } }.toMap()
        }
        if (saved.isNotEmpty()) indexes = indexes + saved
        val fresh = coroutineScope {
            wanted.mapNotNull { (k, c) ->
                val server = connected[k.substringBefore('/')] ?: return@mapNotNull null
                if (!fetched.add(k)) return@mapNotNull null
                async {
                    try {
                        val json = server.libraryJson(k.substringAfter('/'), episodes = c.kind == Library.Kind.SHOWS)
                        withContext(Dispatchers.IO) { runCatching { cache.write(k, json) } }
                        k to withContext(Dispatchers.Default) { index(k, c.kind, json) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null // offline: the saved copy stays
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (fresh.isNotEmpty()) indexes = indexes + fresh
    }

    val history = remember { SearchHistory(context) }
    var query by rememberSaveable { mutableStateOf(history.last) }
    var recent by remember { mutableStateOf(history.recent) }
    LaunchedEffect(query) { history.last = query }
    val saveSearch = { recent = history.add(query) }
    var menuFor by remember { mutableStateOf<VerseEntry?>(null) }
    var copyMenu by remember { mutableStateOf<Copy?>(null) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    // The keyboard only opens on OK (or on arriving here), never just because focus passes over the field.
    var typing by remember { mutableStateOf(false) }

    val terms = remember(query) { SearchMatch.terms(query) }
    val searching = query.trim().length >= 2
    // Every category's merged titles, prepared for matching once per change of the lists.
    val catalog by produceState<List<Pair<String, List<Pair<VerseEntry, Searchable>>>>>(emptyList(), lists) {
        value = withContext(Dispatchers.Default) {
            (listOf(VerseSetup.MOVIES, VerseSetup.SERIES) + setup.others.map { it.id }).map { cat ->
                setup.name(cat) to store.entries(cat, lists).map { it to Searchable(it.item.title) }
            }
        }
    }
    // Matched away from the main thread; the last results stay until the new ones are ready.
    val found by produceState<Found?>(null, terms, catalog, indexes, connected) {
        if (!searching) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val order = store.order(connected.keys)
            val rank = order.withIndex().associate { (i, id) -> id to i }
            val titles = catalog.mapNotNull { (name, entries) ->
                val matched = entries.filter { (_, text) -> SearchMatch.matches(terms, text) }.map { it.first }
                if (matched.isEmpty()) null else name to matched
            }
            val videos = indexes.values.filter { it.kind == Library.Kind.OTHER }.mapNotNull { idx ->
                val server = connected[idx.key.substringBefore('/')] ?: return@mapNotNull null
                val matched = idx.items.filter { (_, own, _) -> SearchMatch.matches(terms, own) }.map { Copy(server, it.first) }
                if (matched.isEmpty()) null else (setup.libraries[idx.key]?.title ?: "Videos") to matched
            }
            // One row per show, its episodes from the preferred server that has each.
            val episodes = indexes.values.filter { it.kind == Library.Kind.SHOWS }
                .sortedBy { rank[it.key.substringBefore('/')] ?: Int.MAX_VALUE }
                .flatMap { idx ->
                    val server = connected[idx.key.substringBefore('/')] ?: return@flatMap emptyList()
                    idx.items.filter { (_, own, show) -> SearchMatch.matches(terms, own, show) }.map { Copy(server, it.first) }
                }
            val shows = episodes.groupBy { SearchMatch.terms(it.item.grandparentTitle ?: "").joinToString(" ") }.values.map { copies ->
                val seen = HashSet<String>()
                val unique = copies.filter { c -> Verse.episodeKeys(c.item).none { it in seen }.also { if (it) seen += Verse.episodeKeys(c.item) } }
                (copies.first().item.grandparentTitle ?: "") to unique.sortedWith(compareBy(com.embergabor.simplex.core.plex.Episodes.order) { it.item }).take(MAX_EPISODES)
            }
            Found(titles, videos, shows)
        }
    }

    // Spoken, it's a finished search: no keyboard.
    LaunchedEffect(VoiceSearch.heard) {
        val heard = VoiceSearch.heard ?: return@LaunchedEffect
        VoiceSearch.heard = null
        query = heard
        recent = history.add(heard)
        typing = false
        keyboard?.hide()
    }

    LaunchedEffect(Unit) {
        if (query.isEmpty() && VoiceSearch.heard == null) {
            withFrameNanos { }
            runCatching { field.requestFocus() }
            typing = true
            keyboard?.show()
        }
    }

    // 12 columns: posters take 2 (six to a row), video thumbnails 3 (four to a row).
    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "menu") { TopMenu(Screen.Search, nav) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "field") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Palette.Tile, fieldShape)
                    .then(if (fieldFocused) Modifier.border(3.dp, Look.AccentSoft, fieldShape) else Modifier)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Search, contentDescription = null, tint = Palette.Secondary, modifier = Modifier.size(20.dp))
                Box(Modifier.padding(start = 12.dp).weight(1f)) {
                    if (query.isEmpty()) Text(SEARCH_HINT, fontSize = 16.sp, color = Palette.Secondary, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Ink),
                        cursorBrush = SolidColor(Palette.Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                        // Search on the keyboard: close it and go down to the results.
                        keyboardActions = KeyboardActions(onSearch = {
                            saveSearch()
                            typing = false
                            keyboard?.hide()
                            focus.moveFocus(FocusDirection.Down)
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .onFocusChanged {
                                fieldFocused = it.isFocused
                                if (!it.isFocused) typing = false
                            }
                            .onPreviewKeyEvent { e ->
                                if (e.key !in okKeys || typing) return@onPreviewKeyEvent false
                                if (e.type == KeyEventType.KeyDown) {
                                    typing = true
                                    keyboard?.show()
                                }
                                true
                            },
                    )
                }
                if (query.isNotEmpty()) {
                    PillButton("Clear", {
                        query = ""
                        runCatching { field.requestFocus() }
                        typing = true
                        keyboard?.show()
                    })
                }
            }
        }
        // Recent searches as quick picks while nothing is being searched.
        if (!searching && recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary, modifier = Modifier.padding(end = 4.dp))
                    recent.forEach { q -> PillButton(q, { query = q; recent = history.add(q) }) }
                }
            }
        }

        val f = found
        when {
            !searching -> {}
            f == null -> hint("Searching…")
            f.isEmpty -> hint(if (lists.isEmpty()) "Your libraries are still loading…" else "Nothing found for “${query.trim()}”.")
            // Opening a result counts the search as used.
            else -> results(nav.alsoOnOpen(saveSearch), f, connected, blur, { menuFor = it }, { copyMenu = it })
        }
    }
    menuFor?.let { entry -> EntryMenu(entry, connected, nav) { menuFor = null } }
    copyMenu?.let { c -> ItemMenu(c.server, c.item, nav) { copyMenu = null } }
}

/** The same navigation, with [action] run first whenever something is opened or played. */
private fun Nav.alsoOnOpen(action: () -> Unit): Nav {
    val nav = this
    return object : Nav by nav {
        override fun push(screen: Screen) { action(); nav.push(screen) }
        override fun play(ratingKeys: List<String>, resume: Boolean, shuffle: Boolean, startAt: Int, keepQueue: Boolean) {
            action()
            nav.play(ratingKeys, resume, shuffle, startAt, keepQueue)
        }
    }
}

/** The last search and the few before it, kept on the TV. */
private class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    var last: String
        get() = prefs.getString("last", "") ?: ""
        set(value) { prefs.edit().putString("last", value).apply() }

    val recent: List<String> get() = prefs.getString("recent", "")!!.split('\n').filter { it.isNotBlank() }

    /** Puts [query] first in the recent list (at most 6, no repeats); returns the list. */
    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return recent
        val list = (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(6)
        prefs.edit().putString("recent", list.joinToString("\n")).apply()
        return list
    }
}

private fun LazyGridScope.hint(text: String) = item(span = { GridItemSpan(maxLineSpan) }) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 15.sp, color = Palette.Secondary)
    }
}

private fun LazyGridScope.results(
    nav: Nav,
    f: Found,
    connected: Map<String, PlexServer>,
    blur: Boolean,
    onMenu: (VerseEntry) -> Unit,
    onCopyMenu: (Copy) -> Unit,
) {
    for ((category, entries) in f.titles) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "t$category") { SectionTitle(category, Modifier.padding(top = 8.dp)) }
        items(entries, key = { "t$category/" + it.key }, span = { GridItemSpan(2) }) { entry ->
            EntryPoster(entry, connected, nav, kids = false, onMenu = onMenu)
        }
    }
    for ((library, videos) in f.videos) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "v$library") { SectionTitle(library, Modifier.padding(top = 8.dp)) }
        items(videos, key = { "v$library/" + it.ref }, span = { GridItemSpan(3) }) { c ->
            WideCard(
                imageUrl = c.server.image(c.item.thumb, 396, 223),
                title = c.item.title,
                subtitle = null,
                badge = if (c.item.duration > 0) formatDuration(c.item.duration) else null,
                progress = c.item.progress,
                watched = c.item.watched,
                onClick = { nav.play(listOf(c.ref)) },
                onLongClick = { onCopyMenu(c) },
                prefetch = { c.server.prefetch(c.item.ratingKey) },
            )
        }
    }
    // One row per show, its matching episodes in season order, with the same cards as the show's page.
    for ((show, episodes) in f.shows) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "s$show") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("$show · ${episodes.size} episode${if (episodes.size == 1) "" else "s"}")
                // The row has no side padding: a little room so the focused card's grown edge isn't cut off.
                PinnedRow(start = 8.dp) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(episodes.size) { i ->
                            val ep = episodes[i]
                            WideCard(
                                imageUrl = ep.server.image(ep.item.thumb, 352, 198, blur = blur && ep.item.unseen),
                                title = ep.item.title,
                                subtitle = ep.item.episodeLabel,
                                subtitleFirst = true,
                                progress = ep.item.progress,
                                watched = ep.item.watched,
                                onClick = { nav.play(listOf(ep.ref)) },
                                prefetch = { ep.server.prefetch(ep.item.ratingKey) },
                                onLongClick = { onCopyMenu(ep) },
                                width = 176.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
