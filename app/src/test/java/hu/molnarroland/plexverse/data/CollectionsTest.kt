package hu.molnarroland.plexverse.data

import com.embergabor.simplex.core.plex.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsTest {

    private fun entry(title: String, year: Int) = Item(
        ratingKey = title, type = "movie", title = title, thumb = null, art = null, grandparentRatingKey = null,
        grandparentTitle = null, parentIndex = null, index = null, viewOffset = 0, viewCount = 0,
        duration = 1000, leafCount = 0, viewedLeafCount = 0, librarySectionId = null, media = emptyList(), year = year,
    ).let { VerseEntry(title, it, listOf(Source("A", it))) }

    private val tags = mapOf(
        "Aliens" to listOf("Alien Collection"),
        "Alien" to listOf("alien collection", "Ridley Scott"),
        "Blade Runner" to listOf("Ridley Scott"),
        "Gladiator" to listOf("Ridley Scott"),
        "Prometheus" to listOf("Alien Collection", "Ridley Scott"),
        "Up" to listOf("Pixar"),
    )
    private val entries = listOf("Alien" to 1979, "Aliens" to 1986, "Blade Runner" to 1982, "Gladiator" to 2000, "Heat" to 1995, "Prometheus" to 2012, "Up" to 2009)
        .map { (t, y) -> entry(t, y) }

    private fun describe(cells: List<GridCell>) = cells.map { c ->
        when (c) {
            is GridCell.Title -> c.entry.item.title
            is GridCell.Collection -> c.name + c.members.map { it.item.title }
        }
    }

    @Test fun offKeepsEveryTitle() {
        assertEquals(entries.map { it.item.title }, describe(Collections.cells(entries, CollectionView.OFF) { tags[it.key].orEmpty() }))
    }

    @Test fun groupsWhereTheFirstMemberWas() {
        // Ridley Scott has more titles here, so Alien and Prometheus go there; "Alien Collection" is left
        // with Aliens alone and isn't one. Pixar has one title: not a collection.
        val cells = Collections.cells(entries, CollectionView.GROUPED) { tags[it.key].orEmpty() }
        assertEquals(
            listOf("Ridley Scott[Alien, Blade Runner, Gladiator, Prometheus]", "Aliens", "Heat", "Up"),
            describe(cells),
        )
        assertEquals("Alien", cells.first().lead.item.title)
    }

    @Test fun membersInReleaseOrder() {
        val sorted = GridSort.YEAR.apply(entries)
        val c = Collections.cells(sorted, CollectionView.COLLAPSED) { tags[it.key].orEmpty() }.filterIsInstance<GridCell.Collection>().single()
        assertEquals(listOf("Alien", "Blade Runner", "Gladiator", "Prometheus"), c.members.map { it.item.title })
        // Placed where its newest member is, in the year sort.
        assertEquals("Prometheus", c.lead.item.title)
    }
}
