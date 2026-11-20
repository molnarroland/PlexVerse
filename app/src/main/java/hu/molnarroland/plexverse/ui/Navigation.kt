@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package hu.molnarroland.plexverse.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.Library
import hu.molnarroland.plexverse.data.Source
import hu.molnarroland.plexverse.profile.KidsSettings

// --- Navigation and refresh plumbing ---

interface Nav {
    fun push(screen: Screen)
    /**
     * Plays [ratingKeys] from [startAt]. Each is a ratingKey on the home server, or a [queueEntry] of
     * copies on several servers, tried in order. An episode played in order gets its whole show as the
     * queue, unless [keepQueue] (e.g. a show's favourites, which play on to the next favourite). Videos
     * start from the beginning; with [resume], the first one continues where it was left.
     */
    fun play(ratingKeys: List<String>, resume: Boolean = false, shuffle: Boolean = false, startAt: Int = 0, keepQueue: Boolean = false)

    /** Back to the sign-in screen, after [hu.molnarroland.plexverse.profile.Session.signOut]. */
    fun signOut()
}

/** Changes whenever lists should reload (returning from the player, marking watched). */
val LocalRefresh = compositionLocalOf { 0 }

val LocalRefreshBump = compositionLocalOf<() -> Unit> { {} }

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    /** A category's merged grid: Movies, Series, or one of the Others. */
    data class Category(val id: String) : Screen
    /** [sources]: the show's copies across servers. */
    data class Show(val show: Item, val sources: List<Source>? = null) : Screen
    /** An other-videos library on the server with [machineId], by folder. */
    data class Folder(val library: Library, val key: String?, val path: List<String>, val machineId: String? = null) : Screen
    /** Servers & categories, in two steps. */
    data object Setup : Screen
    data object ManageCategories : Screen
    data object KidsSettings : Screen
}

/** What the remote's mic heard, for Search (or Kids mode) to take as its query (set by MainActivity). */
object VoiceSearch {
    var heard by mutableStateOf<String?>(null)
}
