@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Icons
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import com.embergabor.simplex.core.ui.okKeys
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
 * Others, or Hidden; OK on one moves it. "Looks right" on both keeps the first run to two presses.
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
        ServersStep(ordered, reach, setup, speed, save, onNext = { step = 2 })
    } else {
        CategoriesStep(setup, ordered.associate { it.machineId to it.name }, reach.mapValues { it.value is Reach.Done }, save, nav) {
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
    speed: SpeedPrefs,
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
            val where = when (speed.local(server.machineId)) {
                true -> "local"
                false -> "remote"
                null -> null
            }
            val state = when (r) {
                is Reach.Done -> listOfNotNull(where, speed.latency(server.machineId)?.let { "$it ms" }, "${r.libraries.size} libraries").joinToString(" · ")
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
    onDone: () -> Unit,
) {
    val memory = rememberFocusMemory()
    var moving by remember { mutableStateOf<String?>(null) }
    // Libraries by server order, so the columns read the same way as step 1.
    val keys = setup.libraries.keys.sortedWith(compareBy({ names.keys.indexOf(it.substringBefore('/')).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { setup.libraries[it]?.title }))
    fun libsIn(category: String) = keys.filter { setup.libraries[it]?.category == category }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Steps(2)
        Text(
            "${setup.moviesName} and ${setup.seriesName} fill themselves and make up Home. OK on a library moves it. New categories: Others › Manage categories.",
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
            bucket("Others", "yours") {
                if (setup.others.isEmpty()) Text("None yet", fontSize = 11.sp, color = Palette.Secondary)
                setup.others.forEach { o ->
                    Text(o.name + if (o.id == setup.pinned) " ★" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Secondary, modifier = Modifier.padding(top = 4.dp))
                    libsIn(o.id).forEach { lib(it) }
                }
            }
            bucket("Hidden", "not shown anywhere") { libsIn(VerseSetup.HIDDEN).forEach { lib(it) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Looks right", onDone, Modifier.remembersFocus(memory, "done", default = keys.isEmpty()))
            PillButton("Manage categories", { nav.push(Screen.ManageCategories) })
        }
    }
    moving?.let { key ->
        ChoiceDialog(
            title = "Move ${setup.libraries[key]?.title} to…",
            options = setup.allCategories.map { it to setup.name(it) },
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

/** A small popup to pick one of [options] (id to label). */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).background(Palette.Tile, RoundedCornerShape(14.dp)).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            options.forEachIndexed { i, (id, label) ->
                PillButton(label, { onPick(id) }, Modifier.fillMaxWidth().then(if (id == selected || (selected == null && i == 0)) Modifier.focusRequester(first) else Modifier), selected = id == selected)
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
}

/**
 * Others › Manage categories: add (named with the TV keyboard, or its mic), rename, order, pin one to
 * the main menu, and delete (its libraries go back to Movies or Series). Movies and Series can be renamed.
 */
@Composable
fun ManageCategoriesScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { SetupPrefs(context) }
    var setup by remember { mutableStateOf(prefs.setup) }
    val save: (VerseSetup) -> Unit = { setup = it; prefs.setup = it; VerseStore.reset() }
    var naming by remember { mutableStateOf<String?>(null) } // a category id, or "" for a new one
    var deleting by remember { mutableStateOf<OtherCategory?>(null) }
    val memory = rememberFocusMemory()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Manage categories")
        Text("Others are shown in the Others popup. ★ is pinned to the main menu, one at a time.", fontSize = 12.sp, color = Palette.Secondary)
        for (id in listOf(VerseSetup.MOVIES, VerseSetup.SERIES)) {
            CategoryRow(setup.name(id), "built in · ${setup.keysIn(id).size} libraries", pinned = null) {
                PillButton("Rename", { naming = id }, Modifier.remembersFocus(memory, "rename-$id", default = id == VerseSetup.MOVIES && setup.others.isEmpty()))
            }
        }
        setup.others.forEachIndexed { i, o ->
            CategoryRow(o.name, "${setup.keysIn(o.id).size} libraries${if (o.id == VerseSetup.VIDEOS) " · from other-video libraries" else ""}", pinned = o.id == setup.pinned) {
                PillButton(if (o.id == setup.pinned) "Pinned" else "Pin", { save(setup.togglePin(o.id)) }, Modifier.remembersFocus(memory, "pin-${o.id}", default = i == 0), selected = o.id == setup.pinned)
                PillButton("Rename", { naming = o.id })
                if (i > 0) PillButton("▲", { save(setup.moveOther(o.id, -1)) })
                if (i < setup.others.lastIndex) PillButton("▼", { save(setup.moveOther(o.id, 1)) })
                PillButton("Delete", { deleting = o })
            }
        }
        Row(Modifier.padding(top = 10.dp)) { PillButton("+ New category", { naming = "" }) }
    }
    naming?.let { id ->
        TextEntryDialog(
            title = if (id.isEmpty()) "New category" else "Rename ${setup.name(id)}",
            initial = if (id.isEmpty()) "" else setup.name(id),
            onDone = { name ->
                save(if (id.isEmpty()) setup.addOther(name) else setup.rename(id, name))
                naming = null
            },
            onDismiss = { naming = null },
        )
    }
    deleting?.let { o ->
        ChoiceDialog(
            title = "Delete ${o.name}? Its libraries go back to ${setup.moviesName} or ${setup.seriesName}.",
            options = listOf("keep" to "Keep it", "delete" to "Delete"),
            selected = "keep",
            onPick = { if (it == "delete") save(setup.deleteOther(o.id)); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryRow(name: String, about: String, pinned: Boolean?, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Tile.copy(alpha = 0.72f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(when (pinned) { true -> "★"; false -> "☆"; null -> " " }, fontSize = 16.sp, color = Look.Accent, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(about, fontSize = 11.sp, color = Palette.Secondary)
        }
        actions()
    }
}

/**
 * A line of text typed with the TV keyboard (which also takes voice). OK opens the keyboard; its Done saves.
 * [secret] hides what's typed (a PIN) and asks for digits.
 */
@Composable
fun TextEntryDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit, secret: Boolean = false, note: String? = null) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(380.dp).background(Palette.Tile, RoundedCornerShape(14.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            note?.let { Text(it, fontSize = 12.sp, color = Palette.Secondary) }
            Box(
                Modifier.fillMaxWidth().height(46.dp).background(Palette.Control, RoundedCornerShape(10.dp))
                    .then(if (focused) Modifier.border(3.dp, Look.AccentSoft, RoundedCornerShape(10.dp)) else Modifier)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Ink),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (secret) KeyboardType.NumberPassword else KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { e ->
                            if (e.key in okKeys && e.type == KeyEventType.KeyDown) { keyboard?.show(); true } else false
                        },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save", { onDone(text) })
                Spacer(Modifier.width(2.dp))
                PillButton("Cancel", onDismiss)
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
}
