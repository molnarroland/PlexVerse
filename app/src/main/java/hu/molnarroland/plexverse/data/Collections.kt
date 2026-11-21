package hu.molnarroland.plexverse.data

/** How a grid shows titles of the same Plex collection (Alien, Harry Potter…). */
enum class CollectionView(val label: String) {
    /** Every title on its own, as Plex lists them. */
    OFF("Off"),
    /** Side by side, framed together, where the first of them would be. */
    GROUPED("Grouped"),
    /** One tile; OK opens a drawer with the titles. */
    COLLAPSED("Collapsed"),
}

/** One place in a grid: a title, or a collection of them. */
sealed interface GridCell {
    val key: String
    /** The title the cell is filed under (its place in the sort, its letter, its picture). */
    val lead: VerseEntry

    data class Title(val entry: VerseEntry) : GridCell {
        override val key get() = entry.key
        override val lead get() = entry
    }

    /** [members] in release order; [lead] is the one that came first in the grid's sort. */
    data class Collection(val name: String, val members: List<VerseEntry>, override val lead: VerseEntry) : GridCell {
        override val key get() = "collection:" + SearchMatch.terms(name).joinToString(" ")
    }
}

object Collections {
    /**
     * [entries] (already sorted) as grid cells. A collection with at least two titles here becomes one
     * cell, where its first title in the sort was. Collections are matched by name across servers. A title
     * in several collections goes to the biggest (then A–Z), so it shows once.
     */
    fun cells(entries: List<VerseEntry>, view: CollectionView, tagsOf: (VerseEntry) -> List<String>): List<GridCell> {
        if (view == CollectionView.OFF) return entries.map { GridCell.Title(it) }
        fun id(name: String) = SearchMatch.terms(name).joinToString(" ")
        val tags = entries.associateWith { e -> tagsOf(e).filter { id(it).isNotEmpty() }.distinctBy(::id) }
        val sizes = HashMap<String, Int>()
        val names = HashMap<String, String>()
        for (list in tags.values) for (t in list) {
            sizes.merge(id(t), 1, Int::plus)
            names.putIfAbsent(id(t), t)
        }
        val home = entries.associateWith { e ->
            tags.getValue(e).map(::id).filter { (sizes[it] ?: 0) >= 2 }
                .sortedWith(compareByDescending<String> { sizes.getValue(it) }.thenBy { it }).firstOrNull()
        }
        val members = entries.filter { home[it] != null }.groupBy { home.getValue(it)!! }
        val done = HashSet<String>()
        return entries.mapNotNull { e ->
            val c = home[e]
            when {
                c == null -> GridCell.Title(e)
                // Its members may have landed in a smaller collection only: then it isn't one any more.
                members.getValue(c).size < 2 -> GridCell.Title(e)
                !done.add(c) -> null
                else -> GridCell.Collection(
                    names.getValue(c),
                    members.getValue(c).sortedWith(compareBy<VerseEntry> { it.item.year ?: Int.MAX_VALUE }.thenBy { Verse.sortKey(it.item) }),
                    lead = e,
                )
            }
        }
    }
}
