@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.embergabor.simplex.core.plex.Episodes
import com.embergabor.simplex.core.plex.FolderEntry
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.plex.PlexServer
import hu.molnarroland.plexverse.update.Updates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.PosterCard
import com.embergabor.simplex.core.ui.WideCard
import com.embergabor.simplex.core.ui.IconTile
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.formatDuration
import com.embergabor.simplex.core.ui.timeLeft

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    /** A category's merged grid: Movies, Series, or one of the Others. */
    data class Category(val id: String) : Screen
    /** [sources]: the show's copies across servers. */
    data class Show(val show: Item, val sources: List<Source>? = null) : Screen
    /** An other-videos library on the server with [machineId], by folder. */
    data class Folder(val library: Library, val key: String?, val path: List<String>, val machineId: String? = null) : Screen
    /** Servers & categories, in two steps. */
    data object Setup : Screen
    data object ManageCategories : Screen
    data object KidsSettings : Screen
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

/** An episode merged across the servers that have the show, with its copy on each. */
private class ShowEpisode(val key: String, val item: Item, val copies: List<Copy>) {
    val entry get() = queueEntry(copies)
    val server get() = copies.first().server
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
                    model = copies.first().server.image(show.thumb, 160, 240),
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
                                    imageUrl = ep.server.image(ep.item.thumb, 352, 198, blur = blur && ep.item.unseen),
                                    title = ep.item.title,
                                    subtitle = ep.item.episodeLabel,
                                    subtitleFirst = true,
                                    progress = ep.item.progress,
                                    watched = ep.item.watched,
                                    onClick = { nav.play(favorites.map { it.entry }, startAt = i, keepQueue = true) },
                                    prefetch = { ep.server.prefetch(ep.item.ratingKey) },
                                    onLongClick = { menuFor = ep },
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
                                    imageUrl = ep.server.image(ep.item.thumb, 352, 198, blur = blur && ep.item.unseen),
                                    title = ep.item.title,
                                    subtitle = listOfNotNull("Episode ${ep.item.index ?: ""}", ep.item.airDate?.let(ItemInfo::date)).joinToString(" · "),
                                    subtitleFirst = true,
                                    progress = ep.item.progress,
                                    watched = ep.item.watched,
                                    onClick = { playFrom(episodes.indexOf(ep), false) },
                                    prefetch = { ep.server.prefetch(ep.item.ratingKey) },
                                    onLongClick = { menuFor = ep },
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
    menuFor?.let { m -> ItemMenu(m.server, m.item, nav, favorite = m in favorites, copies = m.copies) { menuFor = null } }
}

// --- Other videos, by folder ---

@Composable
fun FolderScreen(plex: PlexServer, library: Library, key: String?, path: List<String>, nav: Nav) {
    val refresh = LocalRefresh.current
    val result by load(key, refresh) {
        // The library's favourites, A–Z: a row on the library's top level, and known in every folder for
        // the hold-OK menu. They're extra: if Plex can't list them, the folder still opens.
        val favorites = runCatching { plex.labelled(library.key, Episodes.FAVORITE, type = 1) }.getOrNull().orEmpty()
        plex.folder(library.key, key) to favorites
    }
    val memory = rememberFocusMemory()
    var menuFor by remember { mutableStateOf<Item?>(null) }
    val (entries, favorites) = result?.getOrElse { return ErrorMessage("Couldn't load this folder.\n${it.message}") } ?: return
    if (entries.isEmpty()) return Message("This folder is empty.")

    val folders = entries.filterIsInstance<FolderEntry.Folder>()
    val videos = entries.filterIsInstance<FolderEntry.Video>().map { it.item }
    val favoritesRow = if (key == null) favorites else emptyList()
    val firstKey = favoritesRow.firstOrNull()?.let { "fav" + it.ratingKey } ?: folders.firstOrNull()?.key ?: videos.first().ratingKey

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(library.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                path.forEach {
                    Text("›", fontSize = 15.sp, color = Palette.Secondary)
                    Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary)
                }
            }
        }
        // Played from here, favourites play on to the next favourite.
        if (favoritesRow.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "favorites") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("Favorites")
                    PinnedRow {
                        LazyRow(
                            // Bleeds to the screen edges, so cards scroll past the grid's margins.
                            modifier = Modifier.layout { measurable, constraints ->
                                val bleed = 48.dp.roundToPx()
                                val p = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + 2 * bleed))
                                layout(constraints.maxWidth, p.height) { p.place(-bleed, 0) }
                            },
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            itemsIndexed(favoritesRow, key = { _, v -> v.ratingKey }) { i, v ->
                                WideCard(
                                    imageUrl = plex.image(v.thumb, 396, 223),
                                    title = v.title,
                                    subtitle = null,
                                    badge = if (v.duration > 0) formatDuration(v.duration) else null,
                                    progress = v.progress,
                                    watched = v.watched,
                                    onClick = { nav.play(favoritesRow.map { plex.ref(it.ratingKey) }, startAt = i, keepQueue = true) },
                                    prefetch = { plex.prefetch(v.ratingKey) },
                                    onLongClick = { menuFor = v },
                                    width = 198.dp,
                                    modifier = Modifier.remembersFocus(memory, "fav" + v.ratingKey, default = "fav" + v.ratingKey == firstKey),
                                )
                            }
                        }
                    }
                }
            }
        }
        items(folders, key = { "f" + it.key }) { f ->
            IconTile(
                icon = Icons.Folder,
                label = f.title,
                horizontal = true,
                onClick = { nav.push(Screen.Folder(library, f.key, path + f.title, plex.machineId)) },
                modifier = Modifier.fillMaxWidth().height(60.dp).remembersFocus(memory, f.key, default = f.key == firstKey),
            )
        }
        items(videos, key = { "v" + it.ratingKey }) { v ->
            WideCard(
                imageUrl = plex.image(v.thumb, 396, 223),
                title = v.title,
                subtitle = null,
                badge = if (v.duration > 0) formatDuration(v.duration) else null,
                progress = v.progress,
                watched = v.watched,
                onClick = { nav.play(videos.drop(videos.indexOf(v)).map { plex.ref(it.ratingKey) }) },
                prefetch = { plex.prefetch(v.ratingKey) },
                onLongClick = { menuFor = v },
                modifier = Modifier.remembersFocus(memory, v.ratingKey, default = v.ratingKey == firstKey),
            )
        }
    }
    menuFor?.let { m -> ItemMenu(plex, m, nav, favorite = favorites.any { it.ratingKey == m.ratingKey }) { menuFor = null } }
}
