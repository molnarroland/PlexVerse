@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.setup

import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.OtherCategory
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.ui.ChoiceDialog
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.ScreenTitle
import hu.molnarroland.plexverse.ui.TextEntryDialog
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.Look

/**
 * Others › Manage categories: add (named with the TV keyboard, or its mic), rename, order, pin one to
 * the main menu, and delete (its libraries go back to Movies or Series). Movies and Series can be renamed.
 */
@Composable
fun ManageCategoriesScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { SetupPrefs(context) }
    var setup by remember { mutableStateOf(prefs.setup) }
    val save: (VerseSetup) -> Unit = { setup = it; prefs.setup = it; VerseStore.reset() }
    var naming by remember { mutableStateOf<String?>(null) } // a category id, or "" for a new one
    var deleting by remember { mutableStateOf<OtherCategory?>(null) }
    val memory = rememberFocusMemory()

    ManageCategoriesContent(setup, save, onRename = { naming = it }, onDelete = { deleting = it }, memory)
    naming?.let { id ->
        TextEntryDialog(
            title = if (id.isEmpty()) "New category" else "Rename ${setup.name(id)}",
            initial = if (id.isEmpty()) "" else setup.name(id),
            onDone = { name ->
                save(if (id.isEmpty()) setup.addOther(name) else setup.rename(id, name))
                naming = null
            },
            onDismiss = { naming = null },
        )
    }
    deleting?.let { o ->
        ChoiceDialog(
            title = "Delete ${o.name}? Its libraries go back to ${setup.moviesName} or ${setup.seriesName}.",
            options = listOf("keep" to "Keep it", "delete" to "Delete"),
            selected = "keep",
            onPick = { if (it == "delete") save(setup.deleteOther(o.id)); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * Manage categories as shown: Movies and Series (rename only), then each Other with Pin, Rename, ▲ ▼ and
 * Delete, and New category. [onRename] gets a category id, or "" for a new one.
 */
@Composable
fun ManageCategoriesContent(
    setup: VerseSetup,
    save: (VerseSetup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (OtherCategory) -> Unit,
    memory: FocusMemory,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Manage categories")
        Text("Others are shown in the Others popup. ★ is pinned to the main menu, one at a time.", fontSize = 12.sp, color = Palette.Secondary)
        for (id in listOf(VerseSetup.MOVIES, VerseSetup.SERIES)) {
            CategoryRow(setup.name(id), "built in · ${setup.keysIn(id).size} libraries", pinned = null) {
                PillButton("Rename", { onRename(id) }, Modifier.remembersFocus(memory, "rename-$id", default = id == VerseSetup.MOVIES && setup.others.isEmpty()))
            }
        }
        setup.others.forEachIndexed { i, o ->
            CategoryRow(o.name, "${setup.keysIn(o.id).size} libraries${if (o.id == VerseSetup.VIDEOS) " · from other-video libraries" else ""}", pinned = o.id == setup.pinned) {
                PillButton(if (o.id == setup.pinned) "Pinned" else "Pin", { save(setup.togglePin(o.id)) }, Modifier.remembersFocus(memory, "pin-${o.id}", default = i == 0), selected = o.id == setup.pinned)
                PillButton("Rename", { onRename(o.id) })
                if (i > 0) PillButton("▲", { save(setup.moveOther(o.id, -1)) })
                if (i < setup.others.lastIndex) PillButton("▼", { save(setup.moveOther(o.id, 1)) })
                PillButton("Delete", { onDelete(o) })
            }
        }
        Row(Modifier.padding(top = 10.dp)) { PillButton("+ New category", { onRename("") }) }
    }
}

@TvPreviews
@Composable
internal fun ManageCategoriesPreview() = PreviewTheme {
    ManageCategoriesContent(Sample.setup, {}, {}, {}, rememberFocusMemory())
}

@Composable
private fun CategoryRow(name: String, about: String, pinned: Boolean?, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Tile.copy(alpha = 0.72f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(when (pinned) { true -> "★"; false -> "☆"; null -> " " }, fontSize = 16.sp, color = Look.Accent, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(about, fontSize = 11.sp, color = Palette.Secondary)
        }
        actions()
    }
}
