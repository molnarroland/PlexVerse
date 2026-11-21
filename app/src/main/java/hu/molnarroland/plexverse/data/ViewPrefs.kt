package hu.molnarroland.plexverse.data

import android.content.Context

/** Viewing options from Settings, kept on the TV. The player's own are in [com.embergabor.simplex.core.ui.PlayerPrefs]. */
class ViewPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Unwatched episodes' pictures blurred on the show page and in search, so they don't give anything away. */
    var blurUnwatched: Boolean
        get() = prefs.getBoolean("blurUnwatched", false)
        set(value) { prefs.edit().putBoolean("blurUnwatched", value).apply() }

    /** How titles of the same Plex collection show in the grids. */
    var collections: CollectionView
        get() = runCatching { CollectionView.valueOf(prefs.getString("collections", null) ?: "") }.getOrDefault(CollectionView.OFF)
        set(value) { prefs.edit().putString("collections", value.name).apply() }
}
