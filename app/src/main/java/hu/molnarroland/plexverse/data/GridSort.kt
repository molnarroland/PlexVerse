package hu.molnarroland.plexverse.data

import android.content.Context
import com.embergabor.simplex.core.plex.Item
import java.text.Normalizer

/** How a category's grid is ordered. */
enum class GridSort(val label: String) {
    TITLE("A–Z"),
    ADDED("Recently added"),
    YEAR("Year"),
    UNWATCHED("Unwatched");

    /**
     * [entries] come in title order. A title on several servers counts as added when its newest copy was.
     * Every sort is stable, so titles stay A–Z within a year or within watched and unwatched.
     */
    fun apply(entries: List<VerseEntry>): List<VerseEntry> = when (this) {
        TITLE -> entries
        ADDED -> entries.sortedByDescending { e -> e.sources.maxOf { it.item.addedAt } }
        YEAR -> entries.sortedByDescending { it.item.year ?: 0 }
        UNWATCHED -> entries.sortedBy { it.item.watched }
    }

    companion object {
        /** The letter an item files under: A–Z with accents dropped ("Ö" → O), "#" for anything else. */
        fun letter(item: Item): Char {
            val c = Normalizer.normalize(item.titleSort.trim().take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
            return if (c in 'A'..'Z') c else '#'
        }

        /** Each letter that occurs, with the position of its first item in [items] (in title order). */
        fun letters(items: List<Item>): List<Pair<Char, Int>> {
            val seen = linkedMapOf<Char, Int>()
            items.forEachIndexed { i, item -> seen.getOrPut(letter(item)) { i } }
            return seen.toList()
        }
    }
}

/** Each category's sort, per profile. Kept on the TV. */
class SortPrefs(context: Context, private val profile: String) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun sort(category: String): GridSort =
        runCatching { GridSort.valueOf(prefs.getString("sort.$profile.$category", null) ?: "") }.getOrDefault(GridSort.TITLE)

    fun setSort(category: String, sort: GridSort) = prefs.edit().putString("sort.$profile.$category", sort.name).apply()
}
