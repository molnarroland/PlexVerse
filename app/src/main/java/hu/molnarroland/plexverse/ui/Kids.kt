@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.profile.KidsSettings
import hu.molnarroland.plexverse.profile.Profile
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Ratings
import hu.molnarroland.plexverse.profile.Session
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Kids mode, the whole app for a Kids profile: no menu with ways out, no Settings, and Back does
 * nothing (a grown-up holds it for 3 seconds, see [KidsGate]). Big posters of the profile's own
 * libraries within its age ceiling, Surprise me!, and the child's own Continue watching. Words from the
 * remote's mic narrow the posters down ([query]).
 */
@Composable
fun KidsHome(plex: PlexClient, nav: Nav, name: String, query: String?, onClearQuery: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VerseStore.get(context, plex) }
    LaunchedEffect(Unit) { store.refresh() }
    DisposableEffect(Unit) {
        Sky.kids = true
        onDispose { Sky.kids = false }
    }
    val states by store.servers.collectAsState()
    val lists by store.lists.collectAsState()
    val ratings by store.ratings.collectAsState()
    val connected by store.connected.collectAsState()
    val all = remember(lists, ratings) { store.kidsEntries(lists, ratings) }
    val entries = remember(all, query) {
        if (query.isNullOrBlank()) all
        else SearchMatch.terms(query).let { terms -> all.filter { SearchMatch.matches(terms, SearchMatch.Text(it.item.title)) } }
    }
    val memory = rememberFocusMemory()
    var surprise by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    PreloadImages(grid, entries, connected) { i ->
        entries.getOrNull(i - 2)?.let { e -> e.sources.firstOrNull { it.machineId in connected }?.let { connected[it.machineId]?.image(it.item.thumb, 248, 372) } }
    }

    WithBackdrop {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hi $name!", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                        Logo(Modifier.padding(end = 8.dp).size(48.dp))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    // The grid has its own side margins, so the row goes without the extra ones.
                    Box(Modifier.layout { m, c ->
                        val bleed = 48.dp.roundToPx()
                        val p = m.measure(c.copy(maxWidth = c.maxWidth + 2 * bleed))
                        layout(c.maxWidth, p.height) { p.place(-bleed, 0) }
                    }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContinueRow(store, kids = true, nav, memory, leading = if (all.isEmpty()) null else {
                                { SurpriseButton({ surprise = true }, Modifier.remembersFocus(memory, "surprise", default = true)) }
                            })
                            UpdatingBar(states)
                            if (!query.isNullOrBlank()) {
                                Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("“$query”", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                                    PillButton("Show everything", onClearQuery)
                                }
                            }
                        }
                    }
                }
                if (entries.isEmpty() && states.values.none { it == ServerState.Waiting }) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (all.isEmpty()) "Nothing here yet. A grown-up picks the libraries in Settings › Kids profiles." else "Nothing found.",
                            fontSize = 15.sp, color = Palette.Secondary,
                        )
                    }
                }
                items(entries, key = { it.key }) { entry ->
                    EntryPoster(entry, connected, nav, kids = true, onMenu = {}, modifier = Modifier.remembersFocus(memory, entry.key))
                }
            }
            Text(
                "Grown-ups: hold Back for 3 seconds",
                Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 14.dp),
                fontSize = 10.sp, color = Palette.Secondary,
            )
        }
    }
    if (surprise) Surprise(all, connected, nav) { surprise = false }
}

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

/**
 * Settings › Kids profiles: which Plex Home users open Kids mode, the local Kids profile, and for each
 * Kids profile its libraries, age ceiling and how many episodes play in a row.
 */
@Composable
fun KidsSettingsScreen(plex: PlexClient) {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    val setup = remember { SetupPrefs(context).setup }
    val names = remember { plex.cachedServers.associate { it.machineId to it.name } }
    var version by remember { mutableStateOf(0) }
    val profiles = remember(version) { store.all }
    val kidsProfiles = profiles.filter { store.isKids(it) }
    var selected by remember(version) { mutableStateOf(kidsProfiles.firstOrNull()?.id) }
    val memory = rememberFocusMemory()
    var naming by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Kids profiles")
        Text(
            "A Kids profile opens Kids mode as the whole app. Plex Home's managed users with restrictions are Kids profiles to begin with. " +
                "Without Plex Home, the local Kids profile shares the account (and its progress).",
            fontSize = 12.sp, color = Palette.Secondary,
        )
        SectionTitle("Who opens Kids mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.filter { !it.admin && !it.local && it.id != Profile.ACCOUNT }.forEachIndexed { i, p ->
                PillButton(p.name, { store.setKids(p, !store.isKids(p)); version++ }, Modifier.remembersFocus(memory, "k" + p.id, default = i == 0), selected = store.isKids(p))
            }
            PillButton(
                if (store.localKids) "Local Kids profile: ${store.localKidsName}" else "Add a local Kids profile",
                { store.localKids = !store.localKids; version++ },
                Modifier.remembersFocus(memory, "local", default = profiles.none { !it.admin && !it.local && it.id != Profile.ACCOUNT }),
                selected = store.localKids,
            )
            if (store.localKids) PillButton("Rename", { naming = true })
        }
        if (kidsProfiles.isEmpty()) return@Column
        SectionTitle("Settings for")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kidsProfiles.forEach { p -> PillButton(p.name, { selected = p.id }, selected = p.id == selected) }
        }
        val id = selected ?: return@Column
        var settings by remember(id) { mutableStateOf(store.kidsSettings(id)) }
        val save: (KidsSettings) -> Unit = { settings = it; store.setKidsSettings(id, it); VerseStore.reset() }

        SectionTitle("Libraries")
        val libs = setup.libraries.filterValues { it.kind != Library.Kind.OTHER }.toSortedMap()
        if (libs.isEmpty()) Text("No libraries yet: check Servers & categories first.", fontSize = 12.sp, color = Palette.Secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            libs.forEach { (key, c) ->
                val on = key in settings.libraries
                PillButton("${c.title} · ${names[key.substringBefore('/')] ?: "?"}", {
                    save(settings.copy(libraries = if (on) settings.libraries - key else settings.libraries + key))
                }, selected = on)
            }
        }
        SectionTitle("Age ceiling")
        Text("Across every server and category. Titles without a rating are shown.", fontSize = 11.sp, color = Palette.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (KidsSettings.AGES + listOf(null)).forEach { age ->
                PillButton(Ratings.label(age), { save(settings.copy(maxAge = age)) }, selected = settings.maxAge == age)
            }
        }
        SectionTitle("Episodes in a row")
        Text("Episodes play on by themselves up to this many, then stop.", fontSize = 11.sp, color = Palette.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 5, 0).forEach { n ->
                PillButton(if (n == 0) "No limit" else "$n", { save(settings.copy(episodeLimit = n)) }, selected = settings.episodeLimit == n)
            }
        }
    }
    if (naming) {
        TextEntryDialog("Name the Kids profile", store.localKidsName, onDone = { store.localKidsName = it.trim().ifEmpty { "Kids" }; naming = false; version++ }, onDismiss = { naming = false })
    }
}

/**
 * Kids' playing: every queue is cut to the profile's episode limit, and kept as given, so the player
 * doesn't fill it with the rest of the show.
 */
fun Nav.forKids(limit: Int): Nav {
    val nav = this
    return object : Nav by nav {
        override fun play(ratingKeys: List<String>, resume: Boolean, shuffle: Boolean, startAt: Int, keepQueue: Boolean) {
            val from = ratingKeys.drop(startAt)
            nav.play(if (limit > 0) from.take(limit) else from, resume, shuffle, 0, keepQueue = true)
        }
    }
}
