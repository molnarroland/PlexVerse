@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.embergabor.simplex.core.ui.PillButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import hu.molnarroland.plexverse.profile.Profile
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Set once the splash has given way; the backdrop pictures only fade in after it. */
var splashDone by mutableStateOf(false)

/**
 * The app's own splash, taking over from the system one with the mark in the same place: the planet
 * pulses and its satellites orbit while servers answer, with a dot filling for each ("2 of 3 servers").
 */
@Composable
fun Splash(answered: Int, total: Int) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Logo(Modifier.size(220.dp))
        Wordmark(40.sp, Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 18.dp).size(260.dp, 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (total > 0) {
                repeat(minOf(total, 8)) { i -> Box(Modifier.size(8.dp).background(if (i < answered) Look.AccentSoft else Palette.Control, CircleShape)) }
                Text("$answered of $total servers", fontSize = 12.sp, color = Palette.Secondary, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * plex.tv/link sign-in: the code, and a QR code a phone can scan instead of typing on the remote. Once
 * it's entered, each server is listed as it answers; the app goes on when the first one has.
 */
@Composable
fun SignInScreen(plex: PlexClient, onDone: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val found = remember { mutableStateMapOf<String, ServerState>() }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(attempt) {
        status = null
        try {
            val pin = plex.createPin()
            code = pin.code
            while (true) {
                delay(2000)
                val accountToken = plex.pinToken(pin.id) ?: continue
                code = null
                status = "Finding your servers…"
                // List the servers first, so each can be shown as it answers.
                plex.linkAccount(accountToken)
                val servers = plex.cachedServers
                names = servers.associate { it.machineId to it.name + if (it.owned) "" else " · shared" }
                for (s in servers) {
                    found[s.machineId] = ServerState.Waiting
                    launch {
                        val reached = withTimeoutOrNull(12_000) { runCatching { plex.server(s.machineId) }.getOrNull() }
                        found[s.machineId] = if (reached != null) ServerState.Done(0) else ServerState.Failed("not answering")
                    }
                }
                plex.connect(accountToken)
                Session.signedIn(context, plex, accountToken)
                onDone()
                break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            code = null
            status = (e.message ?: "Something went wrong") + "\nPress OK to try again."
        }
    }

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(Modifier.size(56.dp), animated = false)
            Text("Sign in to Plex", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
        val c = code
        if (c != null) {
            Text("On your phone, scan the code, or go to plex.tv/link and enter:", fontSize = 13.sp, color = Palette.Secondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                QrCode("https://plex.tv/link?code=$c", Modifier.size(150.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    c.forEach { ch ->
                        Box(Modifier.size(60.dp, 75.dp).background(Palette.Tile, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text(ch.toString(), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        }
                    }
                }
            }
            Text("This screen moves on by itself once the code is entered.", fontSize = 11.sp, color = Palette.Secondary)
        }
        status?.let { s ->
            Text(s, fontSize = 13.sp, color = Palette.Secondary)
            if (s.endsWith("try again.")) PillButton("Try again", { attempt++ })
        }
        if (found.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, state) in found) ServerLine(names[id] ?: id, state, done = "answering")
            }
        }
    }
}

/** A QR code for [text], dark modules on a light tile so phones read it in either theme. */
@Composable
fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1)) }
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color(0xFF140A26), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

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

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Text("Who's watching?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            profiles.forEach { p ->
                Avatar(p, store.isKids(p), Modifier.remembersFocus(memory, p.id, default = p.id == (store.current ?: profiles.first().id))) {
                    if (busy == null) {
                        if (Session.needsPin(context, p)) asking = p else pick(p, null)
                    }
                }
            }
        }
        Text(busy ?: error ?: "Hold Back on Home to switch", fontSize = 13.sp, color = if (error != null) Look.Bad else Palette.Secondary)
    }
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
