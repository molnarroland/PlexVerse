@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.okKeys
import hu.molnarroland.plexverse.ui.theme.Look

/** A small popup to pick one of [options] (id to label). */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        ChoicePanel(title, options, selected, onPick, first)
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

/** What [ChoiceDialog] shows: the title and a pill per option, [first] on the selected one. */
@Composable
fun ChoicePanel(title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, first: FocusRequester = remember { FocusRequester() }) {
    Column(
        Modifier.width(320.dp).background(Palette.Tile, RoundedCornerShape(14.dp)).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
        options.forEachIndexed { i, (id, label) ->
            PillButton(label, { onPick(id) }, Modifier.fillMaxWidth().then(if (id == selected || (selected == null && i == 0)) Modifier.focusRequester(first) else Modifier), selected = id == selected)
        }
    }
}

/**
 * A line of text typed with the TV keyboard (which also takes voice). OK opens the keyboard; its Done saves.
 * [secret] hides what's typed (a PIN) and asks for digits.
 */
@Composable
fun TextEntryDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit, secret: Boolean = false, note: String? = null) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = onDismiss) {
        TextEntryPanel(title, note, text, { text = it.take(40) }, secret, onDone = { keyboard?.hide(); onDone(text) }, onDismiss, field)
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
}

/** What [TextEntryDialog] shows: the title, a note, the field ([field] focuses it) and Save / Cancel. */
@Composable
fun TextEntryPanel(
    title: String,
    note: String?,
    text: String,
    onText: (String) -> Unit,
    secret: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    field: FocusRequester = remember { FocusRequester() },
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(380.dp).background(Palette.Tile, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        note?.let { Text(it, fontSize = 12.sp, color = Palette.Secondary) }
        Box(
            Modifier.fillMaxWidth().height(46.dp).background(Palette.Control, RoundedCornerShape(10.dp))
                .then(if (focused) Modifier.border(3.dp, Look.AccentSoft, RoundedCornerShape(10.dp)) else Modifier)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = if (secret) KeyboardType.NumberPassword else KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.key in okKeys && e.type == KeyEventType.KeyDown) { keyboard?.show(); true } else false
                    },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Save", onDone)
            Spacer(Modifier.width(2.dp))
            PillButton("Cancel", onDismiss)
        }
    }
}

@Preview(name = "Dialogs", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dialogs day", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun DialogsPreview() = PreviewTheme {
    Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        ChoicePanel("Move Docs · NAS to…", listOf("movies" to "Movies", "series" to "Series", "c1" to "Documentaries", "hidden" to "Hidden"), "c1", {})
        TextEntryPanel("New category", "Named with the keyboard or its mic.", "Anime", {}, secret = false, onDone = {}, onDismiss = {})
    }
}
