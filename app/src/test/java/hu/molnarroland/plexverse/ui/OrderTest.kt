package hu.molnarroland.plexverse.ui

import com.embergabor.simplex.core.plex.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTest {

    @Test fun ownedThenLocalThenFaster() {
        val facts = listOf(
            ServerFacts("dad", owned = false, local = false, latencyMs = 20),
            ServerFacts("office", owned = true, local = false, latencyMs = 80),
            ServerFacts("nas", owned = true, local = true, latencyMs = 12),
            ServerFacts("new", owned = true, local = null, latencyMs = null),
            ServerFacts("nas2", owned = true, local = true, latencyMs = 5),
        )
        assertEquals(listOf("nas2", "nas", "office", "new", "dad"), ServerOrder.auto(facts))
    }

    @Test fun aManualOrderIsAnOverride() {
        val facts = listOf(ServerFacts("A", true, true, 10), ServerFacts("B", true, true, 50), ServerFacts("C", false, false, 5))
        assertEquals(listOf("A", "B", "C"), ServerOrder.order(VerseSetup(servers = listOf("C", "B")), facts))
        assertEquals(listOf("C", "B", "A"), ServerOrder.order(VerseSetup(servers = listOf("C", "B"), serversOverride = true), facts))
    }

    @Test fun localAddresses() {
        assertTrue(ServerOrder.isLocal("http://192.168.0.13:32400"))
        assertTrue(ServerOrder.isLocal("https://10-0-0-5.0123abcd.plex.direct:32400"))
        assertTrue(ServerOrder.isLocal("http://172.20.1.1:32400"))
        assertFalse(ServerOrder.isLocal("https://84-2-10-7.0123abcd.plex.direct:32400"))
        assertFalse(ServerOrder.isLocal("https://172.32.0.1:32400"))
    }

    private fun item(key: String, title: String, year: Int? = null, addedAt: Long = 0, viewCount: Int = 0) = Item(
        ratingKey = key, type = "movie", title = title, thumb = null, art = null, grandparentRatingKey = null,
        grandparentTitle = null, parentIndex = null, index = null, viewOffset = 0, viewCount = viewCount,
        duration = 1000, leafCount = 0, viewedLeafCount = 0, librarySectionId = null, media = emptyList(),
        year = year, addedAt = addedAt,
    )

    @Test fun sorts() {
        val alien = VerseEntry("a", item("1", "Alien", 1979, addedAt = 10, viewCount = 1), listOf(Source("A", item("1", "Alien", addedAt = 10)), Source("B", item("9", "Alien", addedAt = 50))))
        val brazil = VerseEntry("b", item("2", "Brazil", 1985, addedAt = 30), listOf(Source("A", item("2", "Brazil", addedAt = 30))))
        val cube = VerseEntry("c", item("3", "Cube", 1997, addedAt = 20), listOf(Source("A", item("3", "Cube", addedAt = 20))))
        val az = listOf(alien, brazil, cube)
        // A title counts as added when its newest copy was.
        assertEquals(listOf(alien, brazil, cube), GridSort.ADDED.apply(az))
        assertEquals(listOf(cube, brazil, alien), GridSort.YEAR.apply(az))
        assertEquals(listOf(brazil, cube, alien), GridSort.UNWATCHED.apply(az))
        assertEquals(az, GridSort.TITLE.apply(az))
    }
}
