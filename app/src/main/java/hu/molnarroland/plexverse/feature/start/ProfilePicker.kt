@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.start

import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.plex.PlexStatus
import com.embergabor.simplex.core.ui.Palette
import hu.molnarroland.plexverse.profile.Profile
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Session
import hu.molnarroland.plexverse.ui.TextEntryDialog
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.KidsLook
import hu.molnarroland.plexverse.ui.theme.Look
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * "Who's watching?": the Plex Home users (mint faces are Kids profiles, a dot means a PIN), and the
 * local Kids profile when there is one. Picking one with a PIN asks for it, then the app becomes that
 * person: their servers, progress and restrictions.
 */
@Composable
fun ProfilePicker(plex: PlexClient, onPicked: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    var profiles by remember { mutableStateOf(store.all) }
    LaunchedEffect(Unit) {
        Session.refreshUsers(context, plex)
        profiles = store.all
    }
    var asking by remember { mutableStateOf<Profile?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()

    fun pick(profile: Profile, pin: String?) {
        busy = "Switching to ${profile.name}…"
        error = null
        scope.launch {
            try {
                Session.switchTo(context, plex, profile, pin)
                onPicked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is PlexStatus && (e.code == 401 || e.code == 403)) "That PIN didn't work." else "Couldn't switch: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    ProfilePickerContent(
        profiles, store::isKids, store.current, busy ?: error ?: "Hold Back on Home to switch", error != null, memory,
    ) { p -> if (busy == null) { if (Session.needsPin(context, p)) asking = p else pick(p, null) } }
    asking?.let { p ->
        TextEntryDialog(
            title = "${p.name}'s PIN",
            initial = "",
            secret = true,
            onDone = { pin -> asking = null; pick(p, pin) },
            onDismiss = { asking = null },
        )
    }
}

/** "Who's watching?": a face per profile (mint for Kids, a dot for a PIN), and a [message] under them. */
@Composable
fun ProfilePickerContent(
    profiles: List<Profile>,
    isKids: (Profile) -> Boolean,
    current: String?,
    message: String,
    isError: Boolean,
    memory: FocusMemory,
    onPick: (Profile) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Text("Who's watching?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            profiles.forEach { p ->
                Avatar(p, isKids(p), Modifier.remembersFocus(memory, p.id, default = p.id == (current ?: profiles.first().id))) { onPick(p) }
            }
        }
        Text(message, fontSize = 13.sp, color = if (isError) Look.Bad else Palette.Secondary)
    }
}

@Composable
private fun Avatar(profile: Profile, kids: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(120.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(4.dp, Palette.Ink))),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        ) {
            val face = if (kids) listOf(Color(0xFFD9FFF5), KidsLook.Fill, Color(0xFF0A6B57)) else listOf(Look.AccentSoft, Look.Accent, Color(0xFF2A0E5C))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(face, center = Offset(40f, 36f), radius = 220f), CircleShape), contentAlignment = Alignment.Center) {
                if (profile.thumb != null) {
                    AsyncImage(profile.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(profile.name.take(1).uppercase(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                if (profile.protected) {
                    Box(Modifier.align(Alignment.BottomEnd).size(30.dp).background(Palette.Ink, CircleShape).border(4.dp, Palette.Ground, CircleShape))
                }
            }
        }
        Text(profile.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (kids) KidsLook.Fill else Palette.Ink)
    }
}

@TvPreviews
@Composable
internal fun ProfilePickerPreview() = PreviewTheme {
    ProfilePickerContent(Sample.profiles, { it.restricted || it.local }, "aaa", "Hold Back on Home to switch", false, rememberFocusMemory()) {}
}

