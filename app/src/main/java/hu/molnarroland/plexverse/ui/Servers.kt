@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.Sample
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Palette
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.ui.theme.Look
import hu.molnarroland.plexverse.ui.theme.drawMark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ServerLine(name: String, state: ServerState, done: String? = null) {
    val (dot, text) = when (state) {
        ServerState.Waiting -> Palette.Secondary to "…"
        is ServerState.Done -> Look.Good to (done ?: "${state.items} titles")
        is ServerState.Failed -> Look.Bad to state.reason
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text("$name   $text", fontSize = 13.sp, color = Palette.Ink)
    }
}

/**
 * The PlexVerse mark with each server as a satellite on one of three tilted orbits. Waiting servers
 * pulse, answered ones shine, failed ones turn grey. Satellites pass behind the planet.
 */
@Composable
fun Orbits(states: List<ServerState>, modifier: Modifier, planet: Dp = 72.dp, satellite: Dp = 7.dp) {
    val spin = rememberInfiniteTransition(label = "orbits")
    val turn by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "turn")
    val pulse by spin.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val ring = Palette.Divider
    val shine = Look.AccentSoft
    val failed = Palette.Secondary
    val night = Look.night
    Canvas(modifier) {
        val rings = (0 until minOf(3, states.size.coerceAtLeast(1)))
        fun radii(r: Int) = Size(size.width * (0.26f + 0.11f * r), size.height * (0.17f + 0.08f * r))
        for (r in rings) {
            val (rx, ry) = radii(r)
            drawOval(ring, topLeft = center - Offset(rx, ry), size = Size(2 * rx, 2 * ry), style = Stroke(1.dp.toPx()))
        }
        val sats = states.mapIndexed { i, state ->
            val r = i % 3
            val (rx, ry) = radii(r)
            val a = 2 * PI.toFloat() * (turn * (3 - r) + i / states.size.toFloat())
            Triple(center + Offset(cos(a) * rx, sin(a) * ry), sin(a), state)
        }
        fun sat(at: Offset, depth: Float, state: ServerState) {
            val s = satellite.toPx() * (1 + depth / 3)
            val (color, alpha) = when (state) {
                ServerState.Waiting -> shine to pulse
                is ServerState.Done -> shine to 1f
                is ServerState.Failed -> failed to 0.6f
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f * alpha), Color.Transparent), at, s * 2.4f), s * 2.4f, at)
            drawCircle(color.copy(alpha = alpha), s / 2, at)
        }
        sats.filter { it.second < 0 }.forEach { sat(it.first, it.second, it.third) }
        val p = planet.toPx()
        inset(center.x - p / 2, center.y - p / 2) { drawMark(night, turn, pulse - 0.35f, 0f) }
        sats.filter { it.second >= 0 }.forEach { sat(it.first, it.second, it.third) }
    }
}

/** "Updating · 3/5 servers" with small orbits while a refresh runs; nothing otherwise. */
@Composable
fun UpdatingBar(states: Map<String, ServerState>, modifier: Modifier = Modifier.padding(horizontal = 48.dp)) {
    val done = states.values.count { it != ServerState.Waiting }
    if (done == states.size) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Orbits(states.values.toList(), Modifier.size(52.dp, 26.dp), planet = 14.dp, satellite = 2.5.dp)
        Text("Updating · $done/${states.size} servers", fontSize = 11.sp, color = Palette.Secondary)
    }
}

/** "3 of 5 servers responded", with a bar. */
@Composable
fun ServersBar(answered: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$answered of $total servers responded", fontSize = 12.sp, color = Palette.Secondary)
        Box(Modifier.width(260.dp).height(4.dp).background(Palette.Control, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(answered.toFloat() / total.coerceAtLeast(1)).background(Look.Accent, RoundedCornerShape(2.dp)))
        }
    }
}

@Preview(name = "Servers", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Servers day", widthDp = 960, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun ServersPreview() = PreviewTheme {
    Column(Modifier.padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Orbits(Sample.servers.values.toList(), Modifier.size(360.dp, 200.dp))
        UpdatingBar(Sample.servers, Modifier)
        ServersBar(2, 3)
        for ((id, state) in Sample.servers) ServerLine(Sample.serverNames.getValue(id), state)
    }
}

