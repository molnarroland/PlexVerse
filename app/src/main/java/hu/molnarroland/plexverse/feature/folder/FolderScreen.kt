@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.folder

import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Episodes
import com.embergabor.simplex.core.plex.FolderEntry
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexServer
import com.embergabor.simplex.core.ui.IconTile
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.WideCard
import com.embergabor.simplex.core.ui.formatDuration
import hu.molnarroland.plexverse.ui.ErrorMessage
import hu.molnarroland.plexverse.ui.ItemMenu
import hu.molnarroland.plexverse.ui.LocalRefresh
import hu.molnarroland.plexverse.ui.Message
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.PinnedRow
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.SectionTitle
import hu.molnarroland.plexverse.ui.load
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus

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

    FolderContent(plex, library, key, path, entries, favorites, nav, memory, onMenu = { menuFor = it })
    menuFor?.let { m -> ItemMenu(plex, m, nav, favorite = favorites.any { it.ratingKey == m.ratingKey }) { menuFor = null } }
}

/**
 * One level of an other-videos library as shown: the path, its favourites on the top level, then its
 * folders and videos. [plex] gives the pictures (none without it, as in previews).
 */
@Composable
fun FolderContent(
    plex: PlexServer?,
    library: Library,
    key: String?,
    path: List<String>,
    entries: List<FolderEntry>,
    favorites: List<Item>,
    nav: Nav,
    memory: FocusMemory,
    onMenu: (Item) -> Unit,
) {
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
                                    imageUrl = plex?.image(v.thumb, 396, 223),
                                    title = v.title,
                                    subtitle = null,
                                    badge = if (v.duration > 0) formatDuration(v.duration) else null,
                                    progress = v.progress,
                                    watched = v.watched,
                                    onClick = { nav.play(favoritesRow.map { plex?.ref(it.ratingKey) ?: it.ratingKey }, startAt = i, keepQueue = true) },
                                    prefetch = { plex?.prefetch(v.ratingKey) },
                                    onLongClick = { onMenu(v) },
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
                onClick = { nav.push(Screen.Folder(library, f.key, path + f.title, plex?.machineId)) },
                modifier = Modifier.fillMaxWidth().height(60.dp).remembersFocus(memory, f.key, default = f.key == firstKey),
            )
        }
        items(videos, key = { "v" + it.ratingKey }) { v ->
            WideCard(
                imageUrl = plex?.image(v.thumb, 396, 223),
                title = v.title,
                subtitle = null,
                badge = if (v.duration > 0) formatDuration(v.duration) else null,
                progress = v.progress,
                watched = v.watched,
                onClick = { nav.play(videos.drop(videos.indexOf(v)).map { plex?.ref(it.ratingKey) ?: it.ratingKey }) },
                prefetch = { plex?.prefetch(v.ratingKey) },
                onLongClick = { onMenu(v) },
                modifier = Modifier.remembersFocus(memory, v.ratingKey, default = v.ratingKey == firstKey),
            )
        }
    }
}

@TvPreviews
@Composable
internal fun FolderPreview() = PreviewTheme {
    val videos = listOf("Birthday 2019", "Lake Balaton", "First steps", "Christmas", "School play", "Snow day").mapIndexed { i, t ->
        FolderEntry.Video(Sample.item("v$i", t, type = "clip", duration = 120_000L * (i + 1), viewCount = if (i < 2) 1 else 0))
    }
    val folders = listOf("2019", "2020", "2021", "Holidays").map { FolderEntry.Folder(it, "/f/$it") }
    FolderContent(
        null, Library("4", "Home videos", Library.Kind.OTHER), null, emptyList(), folders + videos,
        videos.take(2).map { it.item }, PreviewNav, rememberFocusMemory(), onMenu = {},
    )
}

