@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.ui.preview.PartPreviews
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.theme.KidsLook
import hu.molnarroland.plexverse.ui.theme.Logo
import hu.molnarroland.plexverse.ui.theme.Look

/**
 * The main menu, a capsule in the middle: the mark, Search, Home, Movies, Series, the pinned category,
 * Others (a popup of the rest) and Settings. Its width never grows past one pinned category.
 */
@Composable
fun TopMenu(current: Screen, nav: Nav, modifier: Modifier = Modifier, setup: VerseSetup = SetupPrefs(LocalContext.current).setup) {
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

@PartPreviews
@Composable
internal fun TopMenuPreview() = PreviewTheme {
    TopMenu(Screen.Category(VerseSetup.MOVIES), PreviewNav, Modifier.padding(top = 36.dp), setup = Sample.setup)
}
