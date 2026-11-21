@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.PlexServer
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PosterCard
import hu.molnarroland.plexverse.data.GridCell
import hu.molnarroland.plexverse.data.VerseEntry
import hu.molnarroland.plexverse.ui.EntryPoster
import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.Look

private val Frame = RoundedCornerShape(16.dp)

private fun countLabel(c: GridCell.Collection): String {
    val shows = c.members.count { it.item.type == "show" }
    val films = c.members.size - shows
    return listOfNotNull(
        films.takeIf { it > 0 }?.let { "$it ${if (it == 1) "film" else "films"}" },
        shows.takeIf { it > 0 }?.let { "$it series" },
    ).joinToString(" · ")
}

/**
 * Grouped: the collection's titles side by side in one framed box, as wide as they are (up to a grid
 * row; more wrap inside). [jump] and the remembered focus go to its first title.
 */
@Composable
internal fun CollectionGroup(
    cell: GridCell.Collection,
    connected: Map<String, PlexServer>,
    nav: Nav,
    onMenu: (VerseEntry) -> Unit,
    memory: FocusMemory,
    firstInGrid: Boolean,
    jump: FocusRequester,
    onFocus: (VerseEntry) -> Unit,
) {
    val perRow = minOf(cell.members.size, 6)
    Column(
        Modifier.fillMaxWidth()
            .background(Palette.Tile.copy(alpha = 0.55f), Frame)
            .border(1.5.dp, Look.AccentSoft.copy(alpha = 0.45f), Frame)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${cell.name} · ${countLabel(cell)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary, maxLines = 1)
        cell.members.chunked(perRow).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { i, e ->
                    val lead = r == 0 && i == 0
                    Box(Modifier.weight(1f)) {
                        EntryPoster(
                            e, connected, nav, kids = false, onMenu = onMenu,
                            modifier = (if (lead) Modifier.focusRequester(jump) else Modifier)
                                .onFocusChanged { if (it.isFocused) onFocus(e) }
                                .remembersFocus(memory, e.key, default = firstInGrid && lead),
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Collapsed: one poster for the whole collection, with two cards peeking out behind it. OK opens [onOpen]. */
@Composable
internal fun CollectionTile(cell: GridCell.Collection, connected: Map<String, PlexServer>, onOpen: () -> Unit, modifier: Modifier) {
    val lead = cell.members.first()
    val reached = lead.sources.firstOrNull { it.machineId in connected }
    Box {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(2f / 3f).offset(y = (-12).dp).background(Palette.Control.copy(alpha = 0.55f), RoundedCornerShape(10.dp)))
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).aspectRatio(2f / 3f).offset(y = (-6).dp).background(Palette.Control, RoundedCornerShape(10.dp)))
        PosterCard(
            title = cell.name,
            imageUrl = reached?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) },
            backdrop = reached?.let { connected[it.machineId]?.image(it.item.art ?: it.item.thumb, 640, 360) },
            progress = 0f,
            watched = cell.members.all { it.item.watched },
            badge = "${cell.members.size}",
            onClick = onOpen,
            modifier = modifier,
        )
    }
}

/** Collapsed, opened: the collection's titles in a drawer along the bottom. Back closes it. */
@Composable
internal fun CollectionDrawer(
    cell: GridCell.Collection,
    connected: Map<String, PlexServer>,
    nav: Nav,
    onMenu: (VerseEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // Opening a show (or anything pushed) leaves the drawer behind; the player is its own screen, so
    // the drawer is still open when a film ends.
    val closingNav = remember(nav) {
        object : Nav by nav {
            override fun push(screen: Screen) { onDismiss(); nav.push(screen) }
        }
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CollectionDrawerPanel(cell, connected, closingNav, { onDismiss(); onMenu(it) }, first)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

/** What [CollectionDrawer] shows: the name, the count and a row of posters, [first] on the first. */
@Composable
internal fun CollectionDrawerPanel(
    cell: GridCell.Collection,
    connected: Map<String, PlexServer>,
    nav: Nav,
    onMenu: (VerseEntry) -> Unit,
    first: FocusRequester = remember { FocusRequester() },
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Palette.Ground.copy(alpha = 0.97f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.5.dp, Look.AccentSoft.copy(alpha = 0.35f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 22.dp, bottom = 18.dp),
    ) {
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(cell.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text(countLabel(cell) + " · Back closes", fontSize = 12.sp, color = Palette.Secondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(cell.members, key = { _, e -> e.key }) { i, e ->
                Box(Modifier.width(120.dp)) {
                    EntryPoster(e, connected, nav, kids = false, onMenu = onMenu, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}
