@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.kids

import hu.molnarroland.plexverse.data.LibraryChoice
import hu.molnarroland.plexverse.ui.FocusMemory
import hu.molnarroland.plexverse.ui.preview.Sample
import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.PillButton
import hu.molnarroland.plexverse.data.SetupPrefs
import hu.molnarroland.plexverse.data.VerseStore
import hu.molnarroland.plexverse.profile.KidsSettings
import hu.molnarroland.plexverse.profile.Profile
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Ratings
import hu.molnarroland.plexverse.ui.ScreenTitle
import hu.molnarroland.plexverse.ui.SectionTitle
import hu.molnarroland.plexverse.ui.TextEntryDialog
import hu.molnarroland.plexverse.ui.rememberFocusMemory
import hu.molnarroland.plexverse.ui.remembersFocus

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
    var settings by remember(selected) { mutableStateOf(selected?.let { store.kidsSettings(it) }) }
    var naming by remember { mutableStateOf(false) }

    KidsSettingsContent(
        profiles = profiles,
        isKids = { store.isKids(it) },
        onToggleKids = { p -> store.setKids(p, !store.isKids(p)); version++ },
        localKids = store.localKids,
        localKidsName = store.localKidsName,
        onToggleLocal = { store.localKids = !store.localKids; version++ },
        onRenameLocal = { naming = true },
        selected = selected,
        onSelect = { selected = it },
        settings = settings,
        onSettings = { s -> selected?.let { store.setKidsSettings(it, s) }; settings = s; VerseStore.reset() },
        libraries = setup.libraries.filterValues { it.kind != Library.Kind.OTHER },
        names = names,
        memory = rememberFocusMemory(),
    )
    if (naming) {
        TextEntryDialog("Name the Kids profile", store.localKidsName, onDone = { store.localKidsName = it.trim().ifEmpty { "Kids" }; naming = false; version++ }, onDismiss = { naming = false })
    }
}

/**
 * Kids profiles as shown: who opens Kids mode (each Plex Home user, and the local Kids profile), then for
 * the [selected] Kids profile its [settings]: libraries (from [libraries], servers by [names]), age
 * ceiling and episodes in a row.
 */
@Composable
fun KidsSettingsContent(
    profiles: List<Profile>,
    isKids: (Profile) -> Boolean,
    onToggleKids: (Profile) -> Unit,
    localKids: Boolean,
    localKidsName: String,
    onToggleLocal: () -> Unit,
    onRenameLocal: () -> Unit,
    selected: String?,
    onSelect: (String) -> Unit,
    settings: KidsSettings?,
    onSettings: (KidsSettings) -> Unit,
    libraries: Map<String, LibraryChoice>,
    names: Map<String, String>,
    memory: FocusMemory,
) {
    val people = profiles.filter { !it.admin && !it.local && it.id != Profile.ACCOUNT }
    val kidsProfiles = profiles.filter(isKids)
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
            people.forEachIndexed { i, p ->
                PillButton(p.name, { onToggleKids(p) }, Modifier.remembersFocus(memory, "k" + p.id, default = i == 0), selected = isKids(p))
            }
            PillButton(
                if (localKids) "Local Kids profile: $localKidsName" else "Add a local Kids profile",
                onToggleLocal,
                Modifier.remembersFocus(memory, "local", default = people.isEmpty()),
                selected = localKids,
            )
            if (localKids) PillButton("Rename", onRenameLocal)
        }
        if (kidsProfiles.isEmpty() || selected == null || settings == null) return@Column
        SectionTitle("Settings for")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kidsProfiles.forEach { p -> PillButton(p.name, { onSelect(p.id) }, selected = p.id == selected) }
        }

        SectionTitle("Libraries")
        val libs = libraries.toSortedMap()
        if (libs.isEmpty()) Text("No libraries yet: check Servers & categories first.", fontSize = 12.sp, color = Palette.Secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            libs.forEach { (key, c) ->
                val on = key in settings.libraries
                PillButton("${c.title} · ${names[key.substringBefore('/')] ?: "?"}", {
                    onSettings(settings.copy(libraries = if (on) settings.libraries - key else settings.libraries + key))
                }, selected = on)
            }
        }
        SectionTitle("Age ceiling")
        Text("Across every server and category. Titles without a rating are shown.", fontSize = 11.sp, color = Palette.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (KidsSettings.AGES + listOf(null)).forEach { age ->
                PillButton(Ratings.label(age), { onSettings(settings.copy(maxAge = age)) }, selected = settings.maxAge == age)
            }
        }
        SectionTitle("Episodes in a row")
        Text("Episodes play on by themselves up to this many, then stop.", fontSize = 11.sp, color = Palette.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 5, 0).forEach { n ->
                PillButton(if (n == 0) "No limit" else "$n", { onSettings(settings.copy(episodeLimit = n)) }, selected = settings.episodeLimit == n)
            }
        }
    }
}

@TvPreviews
@Composable
internal fun KidsSettingsPreview() = PreviewTheme {
    KidsSettingsContent(
        Sample.profiles, { it.restricted || it.local }, {}, localKids = true, localKidsName = "Peti", {}, {}, "ccc", {},
        KidsSettings(setOf("nas/1", "dad/2"), maxAge = 6, episodeLimit = 3), {},
        Sample.setup.libraries.filterValues { it.kind != Library.Kind.OTHER }, Sample.serverNames, rememberFocusMemory(),
    )
}
