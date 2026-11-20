@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.kids

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import hu.molnarroland.plexverse.ui.TextEntryPanel
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.embergabor.simplex.core.plex.PlexClient
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Session
import hu.molnarroland.plexverse.ui.TextEntryDialog
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Between Kids mode and the rest: the account owner's Plex PIN when they have one (checked with
 * plex.tv), otherwise a sum a child is unlikely to answer.
 */
@Composable
fun KidsGate(plex: PlexClient, onPassed: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val owner = remember { store.owner }
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    if (owner?.protected == true) {
        TextEntryDialog(
            title = "Grown-ups only",
            note = note ?: "${owner.name}'s Plex PIN",
            initial = "",
            secret = true,
            onDone = { pin ->
                scope.launch { if (Session.checkOwnerPin(context, plex, pin)) onPassed() else note = "That PIN didn't work." }
            },
            onDismiss = onDismiss,
        )
    } else {
        val (a, b) = remember { Random.nextInt(12, 40) to Random.nextInt(12, 40) }
        TextEntryDialog(
            title = "Grown-ups only",
            note = note ?: "What is $a + $b?",
            initial = "",
            secret = false,
            onDone = { answer -> if (answer.trim().toIntOrNull() == a + b) onPassed() else note = "Not quite. What is $a + $b?" },
            onDismiss = onDismiss,
        )
    }
}

@Preview(name = "Kids gate", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun KidsGatePreview() = PreviewTheme(kids = true) {
    Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        TextEntryPanel("Grown-ups only", "Roland's Plex PIN", "1234", {}, secret = true, onDone = {}, onDismiss = {})
        TextEntryPanel("Grown-ups only", "What is 27 + 15?", "", {}, secret = false, onDone = {}, onDismiss = {})
    }
}

