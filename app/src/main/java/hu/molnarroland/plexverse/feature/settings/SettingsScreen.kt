@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import androidx.core.content.pm.PackageInfoCompat
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.plex.WakeOnLan
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.PlayerPrefs
import com.embergabor.simplex.core.ui.ThemeMode
import com.embergabor.simplex.core.ui.okKeys
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.data.ViewPrefs
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Session
import hu.molnarroland.plexverse.profile.Startup
import hu.molnarroland.plexverse.ui.ChoiceDialog
import hu.molnarroland.plexverse.ui.ItemInfo
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.SectionTitle
import hu.molnarroland.plexverse.ui.TextEntryDialog
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.Look
import hu.molnarroland.plexverse.ui.theme.Place
import hu.molnarroland.plexverse.ui.theme.Places
import hu.molnarroland.plexverse.ui.theme.ThemePrefs
import hu.molnarroland.plexverse.ui.theme.lookUpIp
import hu.molnarroland.plexverse.ui.theme.sunTimes
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class Section(val title: String, val icon: ImageVector, val about: String) {
    Profiles("Profiles", Icons.Account, "Who's watching comes from Plex Home, so each person has their own progress, restrictions and PIN. Holding Back on Home also switches."),
    Servers("Servers & categories", Icons.Tv, "Which server titles play from, and which category each library goes into."),
    Appearance("Appearance", Icons.Sun, "Light, dark, or Auto: dark after sunset and light after sunrise where the TV is."),
    Details("Playback details", Icons.Info, "The format readout in the player's corner, with the controls: video, HDR, and how the audio reaches the receiver. The remote's Info button shows or hides it during a video. OK switches."),
    Tunneling("Tunneled playback", Icons.Play, "Video and sound kept in step by the TV's own hardware instead of by the app. Less work for the app and can tighten lip sync with the receiver, but some TVs handle it badly. From the next video on. OK switches."),
    Spoilers("Unwatched episodes", Icons.Eye, "Blurs the pictures of episodes you haven't started, on the show page and in search, so they don't give anything away. OK switches."),
    TvHome("TV home screen", Icons.Tv, "What you're watching in the TV's own Continue watching row, and a PlexVerse channel with the newest titles (on Android TV's home; Google TV shows mostly the row). Picking one plays it as the current profile. OK switches."),
    Wake("Wake server", Icons.Disk, "Waking the server this TV connects to first when it's asleep, with Wake-on-LAN."),
    Storage("Storage", Icons.Folder, "Preview thumbnails, the search copy of your libraries and artwork, kept on the TV to load faster. OK clears them."),
    Account("Account", Icons.Account, "Signing out forgets the account and every profile on this TV."),
}

/**
 * The sections as a list on the right, tvOS style, with a big picture of the focused one on the left.
 * OK switches or clears in place; sections with more than one control open in the list's place.
 */
@Composable
fun SettingsScreen(plex: PlexClient, nav: Nav, onSwitchProfile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memory = rememberFocusMemory()
    val view = remember { ViewPrefs(context) }
    val playerPrefs = remember { PlayerPrefs(context) }
    val themePrefs = remember { ThemePrefs(context) }
    val profiles = remember { ProfileStore(context) }
    var details by remember { mutableStateOf(playerPrefs.playbackDetails) }
    var blur by remember { mutableStateOf(view.blurUnwatched) }
    var tvHome by remember { mutableStateOf(hu.molnarroland.plexverse.tv.TvHome.enabled(context)) }
    var tunneled by remember { mutableStateOf(playerPrefs.tunneled) }
    var startup by remember { mutableStateOf(profiles.startup) }
    var place by remember { mutableStateOf(themePrefs.place) }
    var manual by remember { mutableStateOf(themePrefs.manual != null) }
    var picking by remember { mutableStateOf(false) }
    var typingPlace by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(0) }
    val cacheSize by produceState<Long?>(null, cleared) { value = withContext(Dispatchers.IO) { Caches.size(context) } }
    val setup = remember { SetupPrefs(context).setup }

    var focused by remember { mutableStateOf(Section.Profiles) }
    var open by remember { mutableStateOf<Section?>(null) }
    val back = remember { FocusRequester() }
    val inside = remember { FocusRequester() }
    BackHandler(enabled = open != null) { open = null }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!opened) return@LaunchedEffect // first visit: remembersFocus picks the row
        withFrameNanos { }
        runCatching { if (open != null) inside.requestFocus() else back.requestFocus() }
    }

    fun value(section: Section): String = when (section) {
        Section.Profiles -> profiles.currentProfile?.name ?: "—"
        Section.Servers -> "${setup.libraries.count { it.value.category != VerseSetup.HIDDEN }} libraries in use"
        Section.Appearance -> when (Palette.mode) {
            ThemeMode.Light -> "Light"
            ThemeMode.Dark -> "Dark"
            ThemeMode.Auto -> "Auto · ${place?.name?.ifEmpty { null } ?: "dark 19:00–07:00"}"
        }
        Section.Details -> if (details) "Shown" else "Hidden"
        Section.Tunneling -> if (tunneled) "On" else "Off"
        Section.Spoilers -> if (blur) "Pictures blurred" else "Pictures shown"
        Section.TvHome -> if (tvHome) "On" else "Off"
        Section.Wake -> plex.home?.name?.ifEmpty { null } ?: "—"
        Section.Storage -> cacheSize?.let(ItemInfo::size) ?: "…"
        Section.Account -> "Signed in"
    }
    fun click(section: Section) {
        when (section) {
            Section.Servers -> nav.push(Screen.Setup)
            Section.Details -> { details = !details; playerPrefs.playbackDetails = details }
            Section.Tunneling -> { tunneled = !tunneled; playerPrefs.tunneled = tunneled }
            Section.Spoilers -> { blur = !blur; view.blurUnwatched = blur }
            Section.TvHome -> {
                tvHome = !tvHome
                hu.molnarroland.plexverse.tv.TvHome.setEnabled(context, tvHome)
                // Off: cleared now. On: filled as soon as the lists next settle.
                if (!tvHome) scope.launch { withContext(Dispatchers.IO) { hu.molnarroland.plexverse.tv.TvHome.clear(context) } }
                else VerseStore.get(context, plex).refresh(force = true)
            }
            Section.Storage -> scope.launch {
                withContext(Dispatchers.IO) { Caches.clear(context) }
                cleared++
            }
            else -> { opened = true; open = section }
        }
    }

    SettingsLayout(open ?: focused) {
        when (open) {
            null -> {
                Section.entries.forEach { section ->
                    SettingsRow(
                        section.title, value(section), section.icon, { click(section) },
                        Modifier
                            .then(if (section == focused) Modifier.focusRequester(back) else Modifier)
                            .onFocusChanged { if (it.isFocused) focused = section }
                            .remembersFocus(memory, section.name, default = section == Section.Profiles),
                    )
                }
                Text(
                    Build.describe(context) + " · the TV's own screensaver is used",
                    fontSize = 10.sp, color = Palette.Secondary, modifier = Modifier.padding(start = 15.dp, top = 8.dp),
                )
            }
            Section.Profiles -> {
                SectionTitle(Section.Profiles.title)
                Note("Watching as ${profiles.currentProfile?.name ?: "—"}.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Switch profile", onSwitchProfile, Modifier.focusRequester(inside))
                    PillButton("Kids profiles…", { nav.push(Screen.KidsSettings) })
                }
                SectionTitle("When the app starts")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Ask who's watching", { startup = Startup.ASK; profiles.startup = startup }, selected = startup == Startup.ASK)
                    PillButton("Start as the last person", { startup = Startup.LAST; profiles.startup = startup }, selected = startup == Startup.LAST)
                }
                if (profiles.users.size <= 1) {
                    Note("Plex Home isn't set up on this account, so everyone shares one progress. Turn it on at plex.tv (Settings › Plex Home) and add people there; they show up here. A local Kids profile works without it.")
                }
            }
            Section.Appearance -> {
                SectionTitle("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.sortedBy { listOf(ThemeMode.Auto, ThemeMode.Light, ThemeMode.Dark).indexOf(it) }.forEachIndexed { i, m ->
                        PillButton(m.name, { Palette.mode = m }, if (i == 0) Modifier.focusRequester(inside) else Modifier, selected = Palette.mode == m)
                    }
                }
                SectionTitle("Location for Auto")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("From IP" + (themePrefs.fromIp?.name?.let { " · $it" } ?: ""), {
                        themePrefs.manual = null; manual = false
                        scope.launch {
                            runCatching { lookUpIp(plex) }.getOrNull()?.let { themePrefs.fromIp = it }
                            place = themePrefs.place
                            Palette.mode = ThemeMode.Auto
                        }
                    }, selected = !manual)
                    PillButton("Set a place" + (themePrefs.manual?.name?.let { " · $it" } ?: ""), { picking = true }, selected = manual)
                }
                Note(sunLine(Palette.mode, place))
            }
            Section.Wake -> {
                SectionTitle(Section.Wake.title)
                Note("Connected first to ${plex.home?.name ?: "—"} at ${plex.home?.uri ?: "—"}.")
                Note("The server's network (MAC) address, for the Wake server button when it's asleep. Plex doesn't report it and Android doesn't let apps look it up, so it's typed here, e.g. 20:07:11:1a:19:86 (the separators can be left out).")
                MacField(plex, Modifier.focusRequester(inside))
            }
            Section.Account -> {
                SectionTitle(Section.Account.title)
                Note("Signed in to plex.tv.")
                Row { PillButton("Sign out", { Session.signOut(context, plex); nav.signOut() }, Modifier.focusRequester(inside)) }
            }
            else -> {}
        }
    }
    if (picking) {
        ChoiceDialog(
            title = "Where is the TV?",
            options = listOf("type" to "Type latitude, longitude…") + Places.all.map { it.name to it.name },
            selected = themePrefs.manual?.name?.takeIf { n -> Places.all.any { it.name == n } },
            onPick = { name ->
                picking = false
                if (name == "type") typingPlace = true
                else Places.all.find { it.name == name }?.let { themePrefs.manual = it; manual = true; place = it; Palette.mode = ThemeMode.Auto }
            },
            onDismiss = { picking = false },
        )
    }
    if (typingPlace) {
        TextEntryDialog(
            title = "Latitude, longitude",
            note = "For example 47.50, 19.04",
            initial = themePrefs.manual?.let { "%.2f, %.2f".format(Locale.ROOT, it.lat, it.lng) } ?: "",
            onDone = { text ->
                val parts = text.split(',', ' ', ';').mapNotNull { it.trim().toDoubleOrNull() }
                if (parts.size == 2 && parts[0] in -90.0..90.0 && parts[1] in -180.0..180.0) {
                    val p = Place("%.2f, %.2f".format(Locale.ROOT, parts[0], parts[1]), parts[0], parts[1])
                    themePrefs.manual = p; manual = true; place = p; Palette.mode = ThemeMode.Auto
                }
                typingPlace = false
            },
            onDismiss = { typingPlace = false },
        )
    }
}

/**
 * Settings' frame: the title, a big picture of the [shown] section with what it does on the left, and
 * [content] (the list, or an opened section) on the right.
 */
@Composable
internal fun SettingsLayout(shown: Section, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Text(
            "Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
        )
        Column(
            Modifier.width(435.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(165.dp).background(Palette.Tile, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
                Icon(shown.icon, contentDescription = null, tint = Look.Accent, modifier = Modifier.size(90.dp))
            }
            Text(
                shown.about, fontSize = 12.sp, color = Palette.Secondary, textAlign = TextAlign.Center,
                lineHeight = 17.sp, modifier = Modifier.width(255.dp),
            )
        }
        Column(
            Modifier.padding(start = 450.dp, top = 78.dp, end = 48.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@TvPreviews
@Composable
internal fun SettingsPreview() = PreviewTheme {
    val values = mapOf(
        Section.Profiles to "Roland", Section.Servers to "7 libraries in use", Section.Appearance to "Auto · Budapest, HU",
        Section.Details to "Hidden", Section.Tunneling to "Off", Section.Spoilers to "Pictures blurred", Section.TvHome to "On", Section.Wake to "NAS",
        Section.Storage to "412 MB", Section.Account to "Signed in",
    )
    SettingsLayout(Section.Profiles) {
        Section.entries.forEach { SettingsRow(it.title, values[it] ?: "", it.icon, {}, Modifier) }
        Text("PlexVerse 0.2.3 (3) · debug", fontSize = 10.sp, color = Palette.Secondary, modifier = Modifier.padding(start = 15.dp, top = 8.dp))
    }
}

@TvPreviews
@Composable
internal fun SettingsAppearancePreview() = PreviewTheme {
    SettingsLayout(Section.Appearance) {
        SectionTitle("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Auto", "Light", "Dark").forEach { PillButton(it, {}, selected = it == "Auto") }
        }
        SectionTitle("Location for Auto")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("From IP · Budapest, HU", {}, selected = true)
            PillButton("Set a place", {})
        }
        Note("Light now · dark at 16:31 (sunset).")
    }
}

/** "Light now · dark at 16:31 (sunset)", or what a fixed mode does. */
private fun sunLine(mode: ThemeMode, place: Place?): String {
    if (mode != ThemeMode.Auto) return "Always ${mode.name.lowercase()}."
    val p = place ?: return "No place yet: dark from 19:00 to 07:00."
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val (rise, set) = sunTimes(now.atZone(zone).toLocalDate(), p.lat, p.lng)
    val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    return when {
        rise == set -> "Polar night: dark all day."
        Duration.between(rise, set).toHours() >= 24 -> "Midnight sun: light all day."
        now < rise -> "Dark now · light at ${fmt.format(rise)} (sunrise)."
        now < set -> "Light now · dark at ${fmt.format(set)} (sunset)."
        else -> "Dark now · light tomorrow around ${fmt.format(rise)}."
    }
}

/** One line of the Settings list: icon, name, the current value, and a chevron. Light, dark when focused. */
@Composable
private fun SettingsRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.width(450.dp).height(40.dp),
        contentPadding = PaddingValues(horizontal = 15.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        colors = ButtonDefaults.colors(
            containerColor = Palette.Tile,
            contentColor = Palette.Ink,
            focusedContainerColor = Palette.Ink,
            focusedContentColor = Palette.OnInk,
        ),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            val dim = LocalContentColor.current.copy(alpha = 0.65f)
            Text(value, fontSize = 11.sp, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("›", fontSize = 15.sp, color = dim)
        }
    }
}

/** The saved MAC address, edited with the TV keyboard (OK opens it), and a button to try it. */
@Composable
private fun MacField(plex: PlexClient, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { WakeOnLan.Prefs(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    var saved by remember { mutableStateOf(prefs.mac) }
    var text by remember { mutableStateOf(saved?.let(WakeOnLan::format).orEmpty()) }
    var typing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun save() {
        val mac = WakeOnLan.parseMac(text)
        status = when {
            text.isBlank() -> { prefs.mac = null; saved = null; "Cleared." }
            mac == null -> "That isn't a MAC address: 12 hex digits, like 20:07:11:1a:19:86."
            else -> { prefs.mac = mac; saved = mac; text = WakeOnLan.format(mac); "Saved." }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier
                .width(260.dp)
                .height(44.dp)
                .background(Palette.Tile, RoundedCornerShape(10.dp))
                .then(if (focused) Modifier.border(3.dp, Palette.Ink, RoundedCornerShape(10.dp)) else Modifier)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text("Not set", fontSize = 14.sp, color = Palette.Secondary)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(17); status = null },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, showKeyboardOnFocus = false),
                keyboardActions = KeyboardActions(onDone = { save(); typing = false; keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused && typing) { typing = false; save() }
                    }
                    // OK opens the keyboard, so moving through Settings doesn't.
                    .onPreviewKeyEvent { e ->
                        if (e.key !in okKeys || typing) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyDown) { typing = true; keyboard?.show() }
                        true
                    },
            )
        }
        saved?.let { mac ->
            PillButton("Wake now", {
                scope.launch {
                    status = runCatching { WakeOnLan.send(mac, plex.home?.uri) }
                        .fold({ "Sent. A sleeping server takes a little while to come up." }, { "Couldn't send: ${it.message}" })
                }
            })
        }
    }
    status?.let { Note(it) }
}

@Composable
private fun Note(text: String) = Text(text, fontSize = 12.sp, color = Palette.Secondary)

/** What's kept in the app's cache folder: preview thumbnails, the search copy, artwork. */
object Caches {
    fun size(context: Context): Long = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clear(context: Context) {
        // Artwork through Coil, which keeps an index of its files; everything else directly.
        runCatching { SingletonImageLoader.get(context).diskCache?.clear() }
        runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }
        context.cacheDir.listFiles()?.filter { !it.name.startsWith("coil") }?.forEach(File::deleteRecursively)
    }
}

/** "PlexVerse 0.2 (3) · debug · installed 31 Oct 2026 19:40". */
object Build {
    fun describe(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val installed = SimpleDateFormat("d MMM yyyy HH:mm", Locale.ENGLISH).format(Date(info.lastUpdateTime))
        return "PlexVerse ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}) · ${if (debug) "debug" else "release"} · installed $installed"
    }
}
