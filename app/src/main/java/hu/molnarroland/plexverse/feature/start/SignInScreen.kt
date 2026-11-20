@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.start

import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.profile.Session
import hu.molnarroland.plexverse.ui.ServerLine
import hu.molnarroland.plexverse.ui.theme.Logo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    SignInContent(code, status, found.map { (id, state) -> (names[id] ?: id) to state }, onRetry = { attempt++ })
}

/** The sign-in screen as shown: the [code] with its QR code, or a [status], and each server as it answers. */
@Composable
fun SignInContent(code: String?, status: String?, servers: List<Pair<String, ServerState>>, onRetry: () -> Unit) {
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
            if (s.endsWith("try again.")) PillButton("Try again", onRetry)
        }
        if (servers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((name, state) in servers) ServerLine(name, state, done = "answering")
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

@TvPreviews
@Composable
internal fun SignInPreview() = PreviewTheme { SignInContent("K7QR", null, emptyList(), onRetry = {}) }

@TvPreviews
@Composable
internal fun SignInFindingServersPreview() = PreviewTheme {
    SignInContent(null, "Finding your servers…", Sample.servers.map { (id, state) -> Sample.serverNames.getValue(id) to state }, onRetry = {})
}

