@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.start

import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import hu.molnarroland.plexverse.ui.theme.AppIcons
import androidx.tv.material3.Icon
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import hu.molnarroland.plexverse.profile.splitForPicker
import hu.molnarroland.plexverse.ui.ChoiceDialog
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
    var wrongPin by remember { mutableStateOf(false) }
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
                if (pin != null && e is PlexStatus && (e.code == 401 || e.code == 403)) {
                    // Straight back to the PIN, rather than making them find the face again.
                    wrongPin = true
                    asking = profile
                } else {
                    error = "Couldn't switch: ${e.message}"
                }
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
            note = "That PIN didn't work. Try again.".takeIf { wrongPin },
            onDone = { pin -> asking = null; wrongPin = false; pick(p, pin) },
            onDismiss = { asking = null; wrongPin = false },
        )
    }
}

private val Slot = 140.dp
private val Gap = 20.dp
private const val ROWS = 2

/**
 * "Who's watching?": a face per profile (mint for Kids, a dot for a PIN), and a [message] under them.
 * Up to two rows of faces; when there are more, the last place is "Others", listing the rest.
 */
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
    var showOthers by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text("Who's watching?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val perRow = ((maxWidth + Gap) / (Slot + Gap)).toInt().coerceAtLeast(1)
            val (shown, others) = splitForPicker(profiles, current, perRow * ROWS)
            val focusFirst = current?.takeIf { id -> shown.any { it.id == id } } ?: shown.firstOrNull()?.id
            // null is the "Others" face.
            val faces: List<Profile?> = if (others.isEmpty()) shown else shown + null
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                faces.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        row.forEach { p ->
                            if (p != null) {
                                Avatar(p, isKids(p), Modifier.remembersFocus(memory, p.id, default = p.id == focusFirst)) { onPick(p) }
                            } else {
                                OthersFace(others.size, Modifier.remembersFocus(memory, OTHERS)) { showOthers = true }
                            }
                        }
                    }
                }
            }
            if (showOthers && others.isNotEmpty()) {
                ChoiceDialog(
                    title = "Who's watching?",
                    options = others.map { p -> p.id to listOfNotNull(p.name, "Kids".takeIf { isKids(p) }, "PIN".takeIf { p.protected }).joinToString(" · ") },
                    selected = null,
                    onPick = { id -> showOthers = false; others.find { it.id == id }?.let(onPick) },
                    onDismiss = { showOthers = false },
                )
            }
        }
        Text(message, fontSize = 13.sp, color = if (isError) Look.Bad else Palette.Secondary)
    }
}

private const val OTHERS = "others"

private val FaceSize = 120.dp
private val BadgeSize = 28.dp
/** The clear ring between the badge and the face. */
private val BadgeGap = 4.dp
/** Room around the face in the layer the hole is cut in, so the focus ring isn't clipped. */
private val Margin = 8.dp

/**
 * A round face with its [label] under it. With [locked], a lock badge sits on the bottom-right edge in a
 * hole cut out of the face, so the sky shows around it whatever is behind.
 */
@Composable
private fun Face(label: String, labelColor: Color, modifier: Modifier, onClick: () -> Unit, locked: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.width(Slot), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var focused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "face")
        // The face, its hole and its badge zoom together; the Surface's own zoom would grow the face out of
        // the layer the hole is cut in (clipping it) and leave the hole and badge behind.
        Box(Modifier.size(FaceSize).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Box(
                Modifier.requiredSize(FaceSize + Margin * 2).then(if (locked) Modifier.badgeHole() else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onClick,
                    modifier = modifier.size(FaceSize).onFocusChanged { focused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(4.dp, Palette.Ink))),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                ) { content() }
            }
            if (locked) {
                Box(
                    Modifier.size(BadgeSize).offset { (badgeCenter(FaceSize.toPx()) - BadgeSize.toPx() / 2).roundToInt().let { IntOffset(it, it) } }
                        .background(Palette.Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Lock, "PIN", Modifier.size(15.dp), tint = Palette.Ground)
                }
            }
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Avatar(profile: Profile, kids: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Face(profile.name, if (kids) KidsLook.Fill else Palette.Ink, modifier, onClick, locked = profile.protected) {
        val face = if (kids) listOf(Color(0xFFD9FFF5), KidsLook.Fill, Color(0xFF0A6B57)) else listOf(Look.AccentSoft, Look.Accent, Color(0xFF2A0E5C))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(face, center = Offset(40f, 36f), radius = 220f), CircleShape), contentAlignment = Alignment.Center) {
            // The initial stays underneath: it shows while the picture loads, or if it doesn't.
            Text(profile.name.take(1).uppercase(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (profile.thumb != null) {
                AsyncImage(profile.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
    }
}

/** Where the badge's centre sits in a face [size] px wide: on its edge, at the bottom right (45°). */
private fun badgeCenter(size: Float) = size / 2 + size / 2 * 0.7071f

/** Cuts the badge's hole (badge plus [BadgeGap]) out of everything drawn here, the focus ring too. */
private fun Modifier.badgeHole() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val c = Margin.toPx() + badgeCenter(FaceSize.toPx())
        drawCircle(Color.Black, radius = (BadgeSize / 2 + BadgeGap).toPx(), center = Offset(c, c), blendMode = BlendMode.Clear)
    }

/** The last place when not everyone fits: "+3", opening the list of the rest. */
@Composable
private fun OthersFace(count: Int, modifier: Modifier, onClick: () -> Unit) {
    Face("Others", Palette.Ink, modifier, onClick) {
        Box(Modifier.fillMaxSize().background(Palette.Tile, CircleShape), contentAlignment = Alignment.Center) {
            Text("+$count", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
    }
}

@TvPreviews
@Composable
internal fun ProfilePickerPreview() = PreviewTheme {
    ProfilePickerContent(Sample.profiles, { it.restricted || it.local }, "aaa", "Hold Back on Home to switch", false, rememberFocusMemory()) {}
}


@TvPreviews
@Composable
internal fun ProfilePickerCrowdedPreview() = PreviewTheme {
    val many = Sample.profiles + listOf("Zsófi", "Bence", "Nagymama", "Nagypapa", "Kristóf-Benedek", "Lili", "Máté", "Dóra")
        .mapIndexed { i, name -> Profile("x$i", name, restricted = i % 3 == 0, protected = i % 2 == 0) }
    ProfilePickerContent(many, { it.restricted || it.local }, "aaa", "Hold Back on Home to switch", false, rememberFocusMemory()) {}
}
