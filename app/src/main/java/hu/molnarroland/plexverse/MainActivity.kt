package hu.molnarroland.plexverse

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.embergabor.simplex.core.player.PlayerActivity
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.AppTheme
import com.embergabor.simplex.core.ui.Palette
import hu.molnarroland.plexverse.data.Copy
import hu.molnarroland.plexverse.data.ServerState
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.feature.category.CategoryScreen
import hu.molnarroland.plexverse.feature.folder.FolderScreen
import hu.molnarroland.plexverse.feature.home.HomeScreen
import hu.molnarroland.plexverse.feature.kids.KidsGate
import hu.molnarroland.plexverse.feature.kids.KidsHome
import hu.molnarroland.plexverse.feature.kids.KidsSettingsScreen
import hu.molnarroland.plexverse.feature.kids.forKids
import hu.molnarroland.plexverse.feature.search.SearchScreen
import hu.molnarroland.plexverse.feature.settings.SettingsScreen
import hu.molnarroland.plexverse.feature.setup.ManageCategoriesScreen
import hu.molnarroland.plexverse.feature.setup.SetupScreen
import hu.molnarroland.plexverse.feature.show.ShowScreen
import hu.molnarroland.plexverse.feature.start.ProfilePicker
import hu.molnarroland.plexverse.feature.start.SignInScreen
import hu.molnarroland.plexverse.feature.start.Splash
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Startup
import hu.molnarroland.plexverse.ui.Backdrop
import hu.molnarroland.plexverse.ui.Backdrops
import hu.molnarroland.plexverse.ui.LocalRefresh
import hu.molnarroland.plexverse.ui.LocalRefreshBump
import hu.molnarroland.plexverse.ui.LocalScreenBackdrop
import hu.molnarroland.plexverse.ui.Message
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.PivotScroll
import hu.molnarroland.plexverse.ui.RemoteKeys
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.VoiceSearch
import hu.molnarroland.plexverse.ui.splashDone
import hu.molnarroland.plexverse.ui.theme.FollowTheme
import hu.molnarroland.plexverse.ui.theme.applyTheme
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Where the app is: signing in, picking who's watching, the grown-ups' app, or Kids mode. */
private enum class Phase { SignIn, Picker, Main, Kids }

class MainActivity : ComponentActivity() {

    private val refresh = mutableIntStateOf(0)

    /** The screen as it was, over the next one until that has its content; [coverFade] fades it away. */
    private var cover by mutableStateOf<ImageBitmap?>(null)
    private val coverFade = Animatable(1f)

    /**
     * A screen reader is on. Only then is the screen described to accessibility services: Compose does it
     * whenever any service is on (on the Sony, the AT4K launcher, which only watches which app is open),
     * and it took about a quarter of the main thread's time after each screen change.
     */
    private val screenReader = mutableStateOf(false)
    private val resumed = mutableStateOf(false)
    private var started = false

    /** What holding Back does where the app is now ([longBackMs] long), or null for nothing. */
    private var longBack: (() -> Unit)? = null
    private var longBackMs = 1000L
    private var backDownAt = 0L
    private var longBackFired = false

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (Android 12+) shows the still mark; the app's own animated one takes over.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val plex = PlexClient.get(this)
        applyTheme(this)
        // The window shows before Compose draws: same ground, so the start doesn't flash.
        window.setBackgroundDrawable(ColorDrawable(Palette.Ground.toArgb()))
        setContent {
            FollowTheme(plex, resumed)
            AppTheme {
                CompositionLocalProvider(
                    LocalRefresh provides refresh.intValue,
                    LocalRefreshBump provides { refresh.intValue++ },
                    LocalBringIntoViewSpec provides PivotScroll.Default,
                ) {
                    Box(Modifier.fillMaxSize().then(if (screenReader.value) Modifier else Modifier.clearAndSetSemantics { })) {
                        Backdrop()
                        // The app starts unseen under the splash rather than covered, so the TV doesn't draw it for nothing.
                        Box(Modifier.graphicsLayer { alpha = if (splashDone) 1f else 0f; compositingStrategy = CompositingStrategy.ModulateAlpha }) { App(plex) }
                        // The last screen, held over the next until that has its content (see App).
                        cover?.let { picture -> Canvas(Modifier.fillMaxSize()) { drawImage(picture, alpha = coverFade.value) } }
                        if (!splashDone) SplashOverlay(plex)
                    }
                    // Compose covers the window from here on: drawing its ground too would be one more full-screen pass a frame.
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        window.setBackgroundDrawable(null)
                    }
                }
            }
        }
    }

    /** The splash, with a dot per server of the current profile as it answers. */
    @Composable
    private fun SplashOverlay(plex: PlexClient) {
        val signedIn = plex.isSignedIn && ProfileStore(this).ownerToken != null
        val states = if (signedIn) VerseStore.get(this, plex).servers.collectAsState().value else emptyMap()
        Splash(states.values.count { it != ServerState.Waiting }, states.size)
    }

    /** The remote's mic: Google's voice input, its words then searched for (in Kids mode, among the posters). */
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == RESULT_OK && !heard.isNullOrBlank()) VoiceSearch.heard = heard
    }

    private fun listen(): Boolean {
        if (!PlexClient.get(this).isSignedIn) return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search")
        return runCatching { voice.launch(intent) }.isSuccess
    }

    // The mic arrives as the search key on most remotes; Android asks for a search with it.
    override fun onSearchRequested() = listen()

    // A false alarm: overriding Activity.dispatchKeyEvent is public API; only ComponentActivity's copy is marked.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || event.keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (event.action == KeyEvent.ACTION_UP && listen()) return true
            if (event.action == KeyEvent.ACTION_DOWN) return true
        }
        // Holding Back: switching profile on Home, or a grown-up leaving Kids mode.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && longBack != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { backDownAt = SystemClock.uptimeMillis(); longBackFired = false }
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && !longBackFired && SystemClock.uptimeMillis() - backDownAt >= longBackMs) {
                longBackFired = true
                longBack?.invoke()
                return true
            }
            if (longBackFired) return true // the rest of that hold, and its release, aren't a Back
        }
        if (!splashDone && event.keyCode != KeyEvent.KEYCODE_BACK) return true
        // The next screen is still loading under the picture of the last one: keys would act unseen.
        if (cover != null && event.keyCode != KeyEvent.KEYCODE_BACK) return true
        RemoteKeys.repeating = event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { RemoteKeys.dx = -1; RemoteKeys.dy = 0 }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { RemoteKeys.dx = 1; RemoteKeys.dy = 0 }
            KeyEvent.KEYCODE_DPAD_UP -> { RemoteKeys.dx = 0; RemoteKeys.dy = -1 }
            KeyEvent.KEYCODE_DPAD_DOWN -> { RemoteKeys.dx = 0; RemoteKeys.dy = 1 }
            else -> { RemoteKeys.dx = 0; RemoteKeys.dy = 0 } // focus after OK or Back: no lean
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        resumed.value = true
        screenReader.value = getSystemService(AccessibilityManager::class.java)?.let {
            it.isEnabled && (it.isTouchExplorationEnabled || it.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty())
        } ?: false
        // Coming back from the player: progress and watched marks have changed.
        if (started) refresh.intValue++
        started = true
    }

    override fun onPause() {
        super.onPause()
        resumed.value = false
    }

    /** The phase to start in: sign-in, who's watching, or straight to the last person. */
    private fun startPhase(plex: PlexClient): Phase {
        val profiles = ProfileStore(this)
        if (!plex.isSignedIn || profiles.ownerToken == null) return Phase.SignIn
        val current = profiles.currentProfile
        if (current == null || (profiles.startup == Startup.ASK && profiles.all.size > 1)) return Phase.Picker
        return if (profiles.isKids(current)) Phase.Kids else Phase.Main
    }

    private fun afterPick(): Phase {
        val profiles = ProfileStore(this)
        return if (profiles.currentProfile?.let(profiles::isKids) == true) Phase.Kids else Phase.Main
    }

    @Composable
    private fun App(plex: PlexClient) {
        var phase by remember { mutableStateOf(startPhase(plex)) }
        var gate by remember { mutableStateOf(false) }

        // The splash stays while the first server answers: at least 0.8 s so it doesn't flash, at most 4 s.
        LaunchedEffect(Unit) {
            val t0 = SystemClock.uptimeMillis()
            if (phase == Phase.Main || phase == Phase.Kids) {
                val store = VerseStore.get(this@MainActivity, plex)
                store.refresh()
                withTimeoutOrNull(4000) {
                    store.servers.first { s -> s.isEmpty() || s.values.any { it is ServerState.Done } || s.values.none { it == ServerState.Waiting } }
                }
            }
            delay((800 - (SystemClock.uptimeMillis() - t0)).coerceAtLeast(0))
            splashDone = true
        }

        val profiles = remember(phase) { ProfileStore(this) }
        SideEffect {
            longBack = when (phase) {
                Phase.Kids -> ({ gate = true })
                Phase.Main -> if (profiles.all.size > 1) ({ phase = Phase.Picker }) else null
                else -> null
            }
            longBackMs = if (phase == Phase.Kids) 3000 else 1000
        }

        when (phase) {
            Phase.SignIn -> SignInScreen(plex) { phase = if (ProfileStore(this).all.size > 1) Phase.Picker else afterPick() }
            Phase.Picker -> ProfilePicker(plex) { phase = afterPick() }
            Phase.Main, Phase.Kids -> key(profiles.current, phase) {
                Shell(plex, kids = phase == Phase.Kids, onSignOut = { phase = Phase.SignIn }, onSwitch = { phase = Phase.Picker })
            }
        }
        if (gate) KidsGate(plex, onPassed = { gate = false; phase = Phase.Picker }, onDismiss = { gate = false })
    }

    /** The screens of one profile, with a back stack. In Kids mode, Back on the first screen does nothing. */
    @Composable
    private fun Shell(plex: PlexClient, kids: Boolean, onSignOut: () -> Unit, onSwitch: () -> Unit) {
        val profiles = remember { ProfileStore(this) }
        val profile = remember { profiles.currentProfile }
        val stack = remember {
            mutableStateListOf<Screen>(Screen.Home).apply {
                // First run for a grown-up: check the servers and categories once.
                if (!kids && !SetupPrefs(this@MainActivity).setup.confirmed) add(Screen.Setup)
            }
        }
        var kidsQuery by remember { mutableStateOf<String?>(null) }
        // Words from the mic open Search (in Kids mode, they narrow down the posters).
        LaunchedEffect(VoiceSearch.heard) {
            val heard = VoiceSearch.heard ?: return@LaunchedEffect
            if (kids) {
                VoiceSearch.heard = null
                kidsQuery = heard
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
            } else if (stack.last() != Screen.Search) stack.add(Screen.Search)
        }
        val states = rememberSaveableStateHolder()
        val base = remember {
            object : Nav {
                override fun push(screen: Screen) {
                    // The top menu's screens are visited once: going to one already open goes back to it.
                    val top = screen is Screen.Category || screen == Screen.Home || screen == Screen.Search || screen == Screen.Settings
                    val open = if (top) stack.indexOf(screen) else -1
                    if (open < 0) { stack.add(screen); return }
                    while (stack.size > open + 1) {
                        states.removeState(stateKey(stack.size - 1, stack.last()))
                        stack.removeAt(stack.lastIndex)
                    }
                }

                override fun play(ratingKeys: List<String>, resume: Boolean, shuffle: Boolean, startAt: Int, keepQueue: Boolean) {
                    startActivity(
                        Intent(this@MainActivity, PlayerActivity::class.java)
                            .putStringArrayListExtra(PlayerActivity.EXTRA_KEYS, ArrayList(ratingKeys))
                            .putExtra(PlayerActivity.EXTRA_RESUME, resume)
                            .putExtra(PlayerActivity.EXTRA_SHUFFLE, shuffle)
                            .putExtra(PlayerActivity.EXTRA_START_AT, startAt)
                            .putExtra(PlayerActivity.EXTRA_KEEP_QUEUE, keepQueue)
                    )
                }

                override fun signOut() = onSignOut()
            }
        }
        val nav = remember(kids) { if (kids) base.forKids(profile?.let { profiles.kidsSettings(it.id).episodeLimit } ?: 3) else base }

        BackHandler(enabled = stack.size > 1) {
            states.removeState(stateKey(stack.size - 1, stack.last()))
            stack.removeAt(stack.lastIndex)
        }
        // Kids mode: Back on its first screen goes nowhere (a grown-up holds it instead).
        BackHandler(enabled = kids && stack.size == 1) { }

        // A new screen starts empty while its content loads. So the screen as it was stays up, as a picture,
        // until the new one has its content (it takes focus then; two seconds at most), and then gives way
        // to it with a short crossfade. One full-screen picture while it lasts; the screens aren't faded.
        val target = stack.size - 1 to stack.last()
        var shown by remember { mutableStateOf(target) }
        LaunchedEffect(target) {
            if (target == shown) return@LaunchedEffect
            cover = snapshot()?.asImageBitmap()
            coverFade.snapTo(1f)
            shown = target
        }
        val (depth, s) = shown
        key(stateKey(depth, s)) {
            val backdrop = remember { mutableStateOf<ImageBitmap?>(null) }
            SideEffect { Backdrops.shown = backdrop }
            DisposableEffect(backdrop) { onDispose { if (Backdrops.shown === backdrop) Backdrops.shown = null } }
            val focused = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withTimeoutOrNull(2000) { snapshotFlow { focused.value }.first { it } }
                if (cover == null) return@LaunchedEffect
                withFrameNanos { }
                coverFade.animateTo(0f, tween(150))
                cover = null
            }
            // Keyed by depth, so going back restores the scroll position and focus of the previous screen.
            Box(Modifier.onFocusChanged { focused.value = it.hasFocus }) {
                CompositionLocalProvider(LocalScreenBackdrop provides backdrop) {
                    states.SaveableStateProvider(stateKey(depth, s)) {
                        if (kids && s == Screen.Home) KidsHome(plex, nav, profile?.name ?: "there", kidsQuery) { kidsQuery = null }
                        else ScreenContent(s, plex, nav, profile?.id ?: "none", onSwitch) { stack.removeAt(stack.lastIndex) }
                    }
                }
            }
        }
    }

    /** The window as it looks now, or null if Android can't copy it. */
    private suspend fun snapshot(): Bitmap? {
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { done ->
            PixelCopy.request(window, bitmap, { result -> done.resume(bitmap.takeIf { result == PixelCopy.SUCCESS }) }, Handler(Looper.getMainLooper()))
        }
    }

    @Composable
    private fun ScreenContent(s: Screen, plex: PlexClient, nav: Nav, profile: String, onSwitch: () -> Unit, pop: () -> Unit) {
        val store = remember { VerseStore.get(this, plex) }
        val connected by store.connected.collectAsState()
        when (s) {
            Screen.Home -> HomeScreen(plex, nav)
            Screen.Search -> SearchScreen(plex, nav)
            Screen.Settings -> SettingsScreen(plex, nav, onSwitch)
            is Screen.Category -> CategoryScreen(plex, s.id, profile, nav)
            is Screen.Show -> ShowScreen(s.sources.orEmpty().mapNotNull { src -> connected[src.machineId]?.let { Copy(it, src.item) } }, nav)
            is Screen.Folder -> s.machineId?.let { connected[it] }?.let { FolderScreen(it, s.library, s.key, s.path, nav) }
                ?: Message("This library's server can't be reached right now.")
            Screen.Setup -> SetupScreen(plex, nav, onDone = pop)
            Screen.ManageCategories -> ManageCategoriesScreen(nav)
            Screen.KidsSettings -> KidsSettingsScreen(plex)
        }
    }

    private fun stateKey(depth: Int, screen: Screen) = "$depth:$screen"
}
