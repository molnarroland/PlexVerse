package hu.molnarroland.plexverse.ui.theme

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Colors
import com.embergabor.simplex.core.ui.Palette
import hu.molnarroland.plexverse.data.Verse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * PlexVerse's look: the night sky (black-violet space, purple, lilac) and the day sky (warm light,
 * the same purple deeper). simplex-core's cards and buttons read [Palette.current], so setting one of
 * these there restyles them too. Never pure black or white: a full screen of either makes the TV keep
 * adjusting its brightness.
 */
object Look {
    val Night = Colors(
        ground = Color(0xFF0B0814), ink = Color(0xFFEEE8FF), secondary = Color(0xFFA59BC0), tile = Color(0xFF211936),
        control = Color(0xFF33284F), divider = Color(0xFF3D3260), placeholder = Color(0xFF2A2142), onInk = Color(0xFF140A26),
        shadow = Color(0xFFC9A6FF),
    )
    val Day = Colors(
        ground = Color(0xFFEFE7F7), ink = Color(0xFF1A1029), secondary = Color(0xFF5E5277), tile = Color(0xFFFBF7FF),
        control = Color(0xFFDCCFF2), divider = Color(0xFFC6B7E0), placeholder = Color(0xFFDDD1EF), onInk = Color.White,
        shadow = Color(0xFF3A1F70),
    )

    /** The night palette (or most of the way to it, mid-fade) is on. */
    val night get() = Palette.Ground.luminance() < 0.5f

    /** The current selection: the main menu's chosen item, the pinned star. */
    val Accent: Color get() = if (night) Color(0xFF9B5CFF) else Color(0xFF6B2FD9)
    val AccentSoft: Color get() = if (night) Color(0xFFC9A6FF) else Color(0xFF8E5BEA)
    val Good: Color get() = if (night) Color(0xFF6EE7B7) else Color(0xFF1E7A55)
    val Bad: Color get() = if (night) Color(0xFFFF8A80) else Color(0xFFB3261E)
}

/** Whether Android TV's "Remove animations" is on: then the mark and the sky hold still. */
@Composable
fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * The mark: a planet with a play symbol cut into it, on a tilted orbit carrying three satellites (the
 * servers feeding the one library you watch from). The planet pulses and the satellites orbit, passing
 * behind it. By day the planet is a sun with slowly turning rays.
 */
@Composable
fun Logo(modifier: Modifier, animated: Boolean = true) {
    val still = !animated || animationsOff()
    val motion = rememberInfiniteTransition(label = "logo")
    val orbit by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "orbit")
    val pulse by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    val rays by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "rays")
    val night = Look.night
    Canvas(modifier) {
        drawMark(night, if (still) 0.08f else orbit, if (still) 0f else pulse, if (still) 0f else rays)
    }
}

/** The mark into a square of side [size].minDimension, centred. */
fun DrawScope.drawMark(night: Boolean, orbit: Float, pulse: Float, rays: Float) {
    val s = size.minDimension
    val c = center
    val tilt = -20f
    val rx = s * 0.47f
    val ry = rx * 0.276f // cos 74°: the orbit seen almost edge-on
    val ringColor = if (night) Color(0xFFC9A6FF) else Color(0xFF7B3FE4)
    val stroke = s * 0.022f
    val oval = Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)
    val satAngles = listOf(0f, 125f, 235f).map { (it / 360f + orbit) * 2 * PI.toFloat() }
    fun satellite(a: Float, i: Int) {
        val x = cos(a) * rx
        val y = sin(a) * ry
        val r = s * (if (i == 1) 0.033f else 0.043f)
        rotate(tilt, c) {
            val at = Offset(c.x + x, c.y + y)
            drawCircle(Brush.radialGradient(listOf((if (night) Color(0xFFC9A6FF) else Color(0xFFFFB21E)).copy(alpha = 0.7f), Color.Transparent), at, r * 3), r * 3, at)
            drawCircle(if (i == 1 && night) Color(0xFFE3D2FF) else Color.White, r, at)
        }
    }
    // Back of the orbit (the half above the planet), and the satellites on it.
    rotate(tilt, c) { drawArc(ringColor, 180f, 180f, false, oval.topLeft, oval.size, style = Stroke(stroke)) }
    satAngles.forEachIndexed { i, a -> if (sin(a) < 0) satellite(a, i) }

    // The planet (by day, the sun), swelling with the pulse.
    val r = s * 0.23f * (1 + 0.06f * pulse)
    if (night) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9B5CFF).copy(alpha = 0.45f + 0.2f * pulse), Color.Transparent), c, r * 2.1f), r * 2.1f, c)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFF1E6FF), 0.22f to Color(0xFFB48CFF), 0.52f to Color(0xFF7B3FE4), 1f to Color(0xFF2A0E5C),
                center = Offset(c.x - r * 0.32f, c.y - r * 0.44f), radius = r * 1.5f,
            ), r, c,
        )
    } else {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD36E).copy(alpha = 0.7f), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
        rotate(rays, c) {
            for (i in 0 until 16) rotate(i * 22.5f, c) {
                val ray = Path().apply {
                    moveTo(c.x - r * 0.07f, c.y - r * 1.12f); lineTo(c.x, c.y - r * 1.62f); lineTo(c.x + r * 0.07f, c.y - r * 1.12f); close()
                }
                drawPath(ray, Color(0xFFFFBA46).copy(alpha = 0.95f))
            }
        }
        drawCircle(
            Brush.radialGradient(
                0f to Color.White, 0.22f to Color(0xFFFFF3C4), 0.48f to Color(0xFFFFD36E), 0.82f to Color(0xFFFF9F43), 1f to Color(0xFFF07A2A),
                center = Offset(c.x - r * 0.24f, c.y - r * 0.36f), radius = r * 1.4f,
            ), r, c,
        )
    }
    // The play symbol cut into it.
    val play = Path().apply {
        val w = r * 0.62f
        val h = r * 0.8f
        val left = c.x - w * 0.38f
        moveTo(left, c.y - h / 2); lineTo(left + w, c.y); lineTo(left, c.y + h / 2); close()
    }
    drawPath(play, if (night) Color(0xFF0B0714).copy(alpha = 0.92f) else Color(0xFF5B23C8).copy(alpha = 0.9f))

    // Front of the orbit, over the planet, and its satellites.
    rotate(tilt, c) { drawArc(ringColor, 0f, 180f, false, oval.topLeft, oval.size, style = Stroke(stroke)) }
    satAngles.forEachIndexed { i, a -> if (sin(a) >= 0) satellite(a, i) }
}

/** "Plex" light and "Verse" bold, with a lilac-to-purple fill (by day, gold to purple). */
@Composable
fun Wordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val brush = if (Look.night) Brush.horizontalGradient(listOf(Color(0xFFC9A6FF), Color(0xFF9B5CFF)))
    else Brush.horizontalGradient(listOf(Color(0xFFF29A2E), Color(0xFF8E5BEA), Color(0xFF4A1BA8)))
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Palette.Ink)) { append("Plex") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, brush = brush)) { append("Verse") }
        },
        modifier = modifier,
        fontSize = fontSize,
    )
}

/**
 * The sky behind every screen: deep space with nebulae and stars at night, a sunlit sky with rays and
 * sparkles by day, mint-tinted for Kids. Drawn once into a picture per size and theme, so each frame
 * costs one full-screen pass on the TV's GPU, not a dozen.
 */
object Sky {
    /** Kids mode is showing: the sky takes Kids' mint. */
    var kids by mutableStateOf(false)

    private var cached: Triple<Size, Pair<Boolean, Boolean>, ImageBitmap>? = null

    fun picture(size: Size, night: Boolean, kids: Boolean): ImageBitmap {
        cached?.let { (s, k, image) -> if (s == size && k == (night to kids)) return image }
        val w = (size.width / 2).toInt().coerceAtLeast(1)
        val h = (size.height / 2).toInt().coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            if (night) drawNight(kids) else drawDay(kids)
        }
        cached = Triple(size, night to kids, image)
        return image
    }

    private fun DrawScope.nebula(at: Offset, rx: Float, ry: Float, color: Color) {
        scale(1f, ry / rx, at) { drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), at, rx), rx, at) }
    }

    private fun DrawScope.drawNight(kids: Boolean) {
        val w = size.width
        val h = size.height
        drawRect(Look.Night.ground)
        nebula(Offset(w * 0.8f, h * 0.1f), w * 0.6f, h * 0.75f, if (kids) Color(0xFF4FE3C1).copy(alpha = 0.22f) else Color(0xFF7B3FE4).copy(alpha = 0.32f))
        nebula(Offset(w * 0.08f, h), w * 0.55f, h * 0.65f, Color(0xFF461896).copy(alpha = 0.32f))
        val rnd = Random(7)
        repeat(150) {
            val r = (rnd.nextFloat().let { it * it * it } * 1.1f + 0.25f) * w / 700f
            drawCircle(Color.White.copy(alpha = 0.2f + rnd.nextFloat() * 0.6f), r, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
        }
    }

    private fun DrawScope.drawDay(kids: Boolean) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf(Color(0xFFFFF4E0), if (kids) Color(0xFFF2FFFA) else Color(0xFFFCF3FF), if (kids) Color(0xFFE3FBF4) else Color(0xFFEFE6FF)), Offset(w, 0f), Offset(0f, h)))
        val sun = Offset(w * 0.84f, h * 0.06f)
        // Rays from the sun, fading out.
        for (i in 0 until 33) rotate(i * 11f + 3f, sun) {
            val ray = Path().apply { moveTo(sun.x, sun.y); lineTo(sun.x + w * 0.7f, sun.y - w * 0.015f); lineTo(sun.x + w * 0.7f, sun.y + w * 0.015f); close() }
            drawPath(ray, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), sun, w * 0.55f))
        }
        drawCircle(Brush.radialGradient(0f to Color.White, 0.15f to Color(0xFFFFF7D6), 0.35f to Color(0xFFFFCE78).copy(alpha = 0.6f), 1f to Color.Transparent, center = sun, radius = w * 0.5f), w * 0.5f, sun)
        nebula(Offset(w * 0.06f, h), w * 0.55f, h * 0.6f, (if (kids) Color(0xFF4FE3C1) else Color(0xFFBE96FF)).copy(alpha = 0.35f))
        nebula(Offset(w * 0.4f, h * 1.1f), w * 0.4f, h * 0.45f, Color(0xFFFFAAD7).copy(alpha = 0.3f))
        val rnd = Random(11)
        repeat(90) {
            val r = (rnd.nextFloat().let { it * it } * 0.9f + 0.3f) * w / 400f
            drawCircle((if (rnd.nextBoolean()) Color(0xFF8E5BEA) else Color(0xFFF2A93B)).copy(alpha = 0.25f + rnd.nextFloat() * 0.5f), r, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
        }
        // Sparkles: four-pointed stars.
        repeat(18) {
            val at = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
            val k = (0.25f + rnd.nextFloat() * 0.35f) * w / 400f * 10f
            val sparkle = Path().apply {
                moveTo(at.x, at.y - k); quadraticTo(at.x, at.y, at.x + k, at.y); quadraticTo(at.x, at.y, at.x, at.y + k)
                quadraticTo(at.x, at.y, at.x - k, at.y); quadraticTo(at.x, at.y, at.x, at.y - k); close()
            }
            drawPath(sparkle, if (rnd.nextFloat() < 0.6f) Color(0xFFF2A93B) else Color(0xFF9B5CFF))
        }
    }
}

@Preview(name = "Mark", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Mark day", widthDp = 960, heightDp = 300, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun MarkPreview() = PreviewTheme {
    Row(Modifier.fillMaxSize().padding(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Logo(Modifier.size(200.dp), animated = false)
        Logo(Modifier.size(48.dp), animated = false)
        Wordmark(48.sp)
        Box(Modifier.size(60.dp).background(Look.Accent, CircleShape))
        Box(Modifier.size(60.dp).background(KidsLook.Fill, CircleShape))
    }
}

