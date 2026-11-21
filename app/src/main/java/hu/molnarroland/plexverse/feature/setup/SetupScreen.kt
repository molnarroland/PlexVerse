@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.setup

import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewNav
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.ServerFacts
import hu.molnarroland.plexverse.data.ServerOrder
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.SpeedPrefs
import hu.molnarroland.plexverse.data.VerseSetup
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.ui.ChoiceDialog
import hu.molnarroland.plexverse.ui.ErrorMessage
import hu.molnarroland.plexverse.ui.Nav
import hu.molnarroland.plexverse.ui.Screen
import hu.molnarroland.plexverse.ui.ServersBar
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus
import hu.molnarroland.plexverse.ui.theme.Look
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Reach {
    data object Waiting : Reach
    data class Done(val libraries: List<Library>, val ms: Long) : Reach
    data object Failed : Reach
}

/**
 * Servers & categories, in two steps. 1: the servers, ranked for you (owned before shared, local before
 * remote, faster first), with Move as an override. 2: every library in Movies, Series, one of your
 * Others, or Hidden; OK on one moves it. The first time, step 2 shows only Movies and Series, then asks
 * whether to make other categories. "Looks right" on both keeps the first run to three presses.
 * [onDone] is called after step 2.
 */
@Composable
fun SetupScreen(plex: PlexClient, nav: Nav, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SetupPrefs(context) }
    val speed = remember { SpeedPrefs(context) }
    var setup by remember { mutableStateOf(prefs.setup) }
    val save: (VerseSetup) -> Unit = { setup = it; prefs.setup = it }
    var servers by remember { mutableStateOf(plex.cachedServers) }
    var listError by remember { mutableStateOf<String?>(null) }
    val reach = remember { mutableStateMapOf<String, Reach>() }
    var step by remember { mutableStateOf(1) }
    val firstRun = remember { !setup.confirmed }
    BackHandler(enabled = step == 2) { step = 1 }

    // The saved list first, then plex.tv's, which also brings servers shared since.
    LaunchedEffect(Unit) {
        try {
            servers = plex.servers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (servers.isEmpty()) listError = "Couldn't list your servers on plex.tv.\n${e.message}"
        }
    }
    LaunchedEffect(servers) {
        for (server in servers) {
            if (reach[server.machineId] is Reach.Done) continue
            reach[server.machineId] = Reach.Waiting
            launch {
                val t0 = SystemClock.elapsedRealtime()
                val result = try {
                    withTimeoutOrNull(15_000) {
                        val s = plex.server(server.machineId) ?: return@withTimeoutOrNull null
                        val t = SystemClock.elapsedRealtime()
                        val libraries = s.libraries()
                        speed.record(server.machineId, SystemClock.elapsedRealtime() - t, s.uri)
                        libraries to (SystemClock.elapsedRealtime() - t0)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                reach[server.machineId] = result?.let { (libs, ms) -> Reach.Done(libs, ms) } ?: Reach.Failed
                if (result != null) save(setup.withLibraries(server.machineId, result.first))
            }
        }
    }

    listError?.let { return ErrorMessage(it) }
    val facts = servers.map { ServerFacts(it.machineId, it.owned, speed.local(it.machineId), speed.latency(it.machineId)) }
    val order = ServerOrder.order(setup, facts)
    val ordered = order.mapNotNull { id -> servers.find { it.machineId == id } }

    if (step == 1) {
        ServersStep(ordered, reach, setup, speed::local, speed::latency, save, onNext = { step = 2 })
    } else {
        CategoriesStep(setup, ordered.associate { it.machineId to it.name }, reach.mapValues { it.value is Reach.Done }, save, nav, simpleFirst = firstRun) {
            save(setup.copy(confirmed = true))
            VerseStore.reset()
            onDone()
        }
    }
}

@Composable
private fun ServersStep(
    ordered: List<PlexClient.ServerInfo>,
    reach: Map<String, Reach>,
    setup: VerseSetup,
    local: (String) -> Boolean?,
    latency: (String) -> Long?,
    save: (VerseSetup) -> Unit,
    onNext: () -> Unit,
) {
    val memory = rememberFocusMemory()
    var moving by remember { mutableStateOf(false) }
    val ids = ordered.map { it.machineId }
    val move: (String, Int) -> Unit = { id, by ->
        val list = ids.toMutableList()
        val from = list.indexOf(id)
        val to = from + by
        if (from >= 0 && to in list.indices) {
            list.add(to, list.removeAt(from))
            save(setup.copy(servers = list, serversOverride = true))
        }
    }
    BackHandler(enabled = moving) { moving = false }
    val answered = ordered.count { reach[it.machineId].let { r -> r != null && r != Reach.Waiting } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Steps(1)
        Text(
            if (moving) "◀ ▶ or ▲ ▼ on a server moves it. Back when it's right."
            else "Titles play from the first server that has them and answers. Ranked for you: yours before shared, on this network before remote, faster first.",
            fontSize = 12.sp, color = Palette.Secondary,
        )
        if (ordered.isNotEmpty() && answered < ordered.size) ServersBar(answered, ordered.size)
        ordered.forEachIndexed { i, server ->
            val r = reach[server.machineId]
            val where = when (local(server.machineId)) {
                true -> "local"
                false -> "remote"
                null -> null
            }
            val state = when (r) {
                is Reach.Done -> listOfNotNull(where, latency(server.machineId)?.let { "$it ms" }, "${r.libraries.size} libraries").joinToString(" · ")
                Reach.Failed -> "not answering"
                else -> "checking…"
            }
            ServerRow(
                rank = i + 1,
                name = server.name,
                about = "${if (server.owned) "yours" else "shared"} · $state",
                ok = r is Reach.Done,
                failed = r == Reach.Failed,
                moving = moving,
                modifier = Modifier.remembersFocus(memory, "s" + server.machineId, default = false)
                    .onPreviewKeyEvent { e ->
                        if (!moving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft, Key.DirectionUp -> { move(server.machineId, -1); true }
                            Key.DirectionRight, Key.DirectionDown -> { move(server.machineId, 1); true }
                            else -> false
                        }
                    },
                onClick = { if (moving) moving = false },
            )
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Looks right", onNext, Modifier.remembersFocus(memory, "next", default = true))
            PillButton(if (moving) "Done moving" else "Move a server", { moving = !moving }, icon = Icons.Move)
            if (setup.serversOverride) PillButton("Rank for me again", { save(setup.copy(serversOverride = false, servers = emptyList())) })
        }
    }
}

@Composable
private fun ServerRow(rank: Int, name: String, about: String, ok: Boolean, failed: Boolean, moving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PillButton("$rank. $name", onClick, modifier.width(280.dp), icon = if (moving) Icons.Move else null)
        Box(Modifier.size(9.dp).background(if (ok) Look.Good else if (failed) Look.Bad else Palette.Secondary, CircleShape))
        Text(about, fontSize = 12.sp, color = Palette.Secondary)
    }
}

@Composable
private fun Steps(on: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("1 Servers", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (on == 1) Look.Accent else Palette.Secondary)
        Text("›", fontSize = 18.sp, color = Palette.Secondary)
        Text("2 Categories", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (on == 2) Look.Accent else Palette.Secondary)
    }
}

@Composable
private fun CategoriesStep(
    setup: VerseSetup,
    names: Map<String, String>,
    reachable: Map<String, Boolean>,
    save: (VerseSetup) -> Unit,
    nav: Nav,
    simpleFirst: Boolean = false,
    onDone: () -> Unit,
) {
    val memory = rememberFocusMemory()
    var moving by remember { mutableStateOf<String?>(null) }
    // The first time: just Movies and Series, then "other categories too?"
    var simple by remember { mutableStateOf(simpleFirst) }
    var askingOthers by remember { mutableStateOf(false) }
    // Libraries by server order, so the columns read the same way as step 1.
    val keys = setup.libraries.keys.sortedWith(compareBy({ names.keys.indexOf(it.substringBefore('/')).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { setup.libraries[it]?.title }))
    fun libsIn(category: String) = keys.filter { setup.libraries[it]?.category == category }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Steps(2)
        Text(
            if (simple) "${setup.moviesName} and ${setup.seriesName} fill themselves and make up Home. OK on a library moves it."
            else "${setup.moviesName} and ${setup.seriesName} fill themselves and make up Home. OK on a library moves it. New categories: Others › Manage categories.",
            fontSize = 12.sp, color = Palette.Secondary,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val bucket: @Composable (String, String, @Composable () -> Unit) -> Unit = { title, note, content ->
                Column(
                    Modifier.weight(1f).fillMaxSize().background(Palette.Tile.copy(alpha = 0.72f), RoundedCornerShape(12.dp)).padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text(note, fontSize = 10.sp, color = Palette.Secondary)
                    content()
                }
            }
            val lib: @Composable (String) -> Unit = { k ->
                val c = setup.libraries.getValue(k)
                val id = k.substringBefore('/')
                LibraryPill(
                    "${c.title} · ${names[id] ?: "?"}",
                    enabled = reachable[id] != false,
                    modifier = Modifier.remembersFocus(memory, k, default = k == keys.firstOrNull()),
                ) { moving = k }
            }
            bucket(setup.moviesName, "built in") { libsIn(VerseSetup.MOVIES).forEach { lib(it) } }
            bucket(setup.seriesName, "built in") { libsIn(VerseSetup.SERIES).forEach { lib(it) } }
            if (!simple) bucket("Others", "yours") {
                if (setup.others.isEmpty()) Text("None yet", fontSize = 11.sp, color = Palette.Secondary)
                setup.others.forEach { o ->
                    Text(o.name + if (o.id == setup.pinned) " ★" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary, modifier = Modifier.padding(top = 4.dp))
                    libsIn(o.id).forEach { lib(it) }
                }
            }
            if (!simple) bucket("Hidden", "not shown anywhere") { libsIn(VerseSetup.HIDDEN).forEach { lib(it) } }
        }
        if (simple) {
            // Libraries already elsewhere (other videos go to a Videos category by themselves), so nothing seems lost.
            val elsewhere = keys.count { setup.libraries[it]?.category.let { c -> c != VerseSetup.MOVIES && c != VerseSetup.SERIES } }
            if (elsewhere > 0) Text(
                "$elsewhere more ${if (elsewhere == 1) "library is" else "libraries are"} in other categories or hidden.",
                fontSize = 11.sp, color = Palette.Secondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Looks right", { if (simple) askingOthers = true else onDone() }, Modifier.remembersFocus(memory, "done", default = keys.isEmpty()))
            if (!simple) PillButton("Manage categories", { nav.push(Screen.ManageCategories) })
        }
    }
    if (askingOthers) {
        ChoiceDialog(
            title = "Make other categories too?",
            options = listOf("no" to "No, that's everything", "yes" to "Yes, like Documentaries or Anime"),
            selected = null,
            onPick = { answer ->
                askingOthers = false
                if (answer == "yes") simple = false else onDone()
            },
            onDismiss = { askingOthers = false },
        )
    }
    moving?.let { key ->
        ChoiceDialog(
            title = "Move ${setup.libraries[key]?.title} to…",
            // Before other categories are asked about, a library goes to Movies, Series, or nowhere.
            options = (if (simple) listOf(VerseSetup.MOVIES, VerseSetup.SERIES, VerseSetup.HIDDEN) else setup.allCategories)
                .map { it to if (simple && it == VerseSetup.HIDDEN) "Don't show" else setup.name(it) },
            selected = setup.libraries[key]?.category,
            onPick = { save(setup.move(key, it)); moving = null },
            onDismiss = { moving = null },
        )
    }
}

@Composable
private fun LibraryPill(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PillButton(label, onClick, modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f))
}

private val previewServers = listOf(
    com.embergabor.simplex.core.plex.PlexClient.ServerInfo("nas", "NAS", true, "", emptyList()),
    com.embergabor.simplex.core.plex.PlexClient.ServerInfo("office", "Office", true, "", emptyList()),
    com.embergabor.simplex.core.plex.PlexClient.ServerInfo("dad", "Dad's server", false, "", emptyList()),
)

@TvPreviews
@Composable
internal fun SetupServersPreview() = PreviewTheme {
    val reach = mapOf("nas" to Reach.Done(List(3) { Library("$it", "", Library.Kind.MOVIES) }, 40), "office" to Reach.Done(List(2) { Library("$it", "", Library.Kind.MOVIES) }, 120), "dad" to Reach.Failed)
    ServersStep(previewServers, reach, Sample.setup, { it == "nas" }, { mapOf("nas" to 12L, "office" to 80L)[it] }, {}, onNext = {})
}

@TvPreviews
@Composable
internal fun SetupCategoriesPreview() = PreviewTheme {
    CategoriesStep(Sample.setup, Sample.serverNames, mapOf("dad" to false), {}, PreviewNav) {}
}

@TvPreviews
@Composable
internal fun SetupCategoriesFirstRunPreview() = PreviewTheme {
    CategoriesStep(Sample.setup, Sample.serverNames, mapOf("dad" to false), {}, PreviewNav, simpleFirst = true) {}
}

