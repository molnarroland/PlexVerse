package hu.molnarroland.plexverse.ui

import com.embergabor.simplex.core.plex.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    private val setup = VerseSetup(
        servers = listOf("A", "B"),
        serversOverride = true,
        libraries = mapOf(
            "A/3" to LibraryChoice(VerseSetup.MOVIES, "Films", Library.Kind.MOVIES),
            "A/5" to LibraryChoice("c1", "Docs", Library.Kind.MOVIES),
            "B/1" to LibraryChoice(VerseSetup.VIDEOS, "Training", Library.Kind.OTHER),
            "B/2" to LibraryChoice(VerseSetup.HIDDEN, "Old", Library.Kind.SHOWS),
        ),
        moviesName = "Films",
        others = listOf(OtherCategory("c1", "Documentaries"), OtherCategory(VerseSetup.VIDEOS, "Videos")),
        pinned = "c1",
        confirmed = true,
    )

    @Test fun roundTrips() {
        assertEquals(setup, VerseSetup.fromJson(setup.toJson()))
    }

    @Test fun newLibrariesGoByType() {
        val s = VerseSetup().withLibraries("C", listOf(
            Library("1", "Movies", Library.Kind.MOVIES),
            Library("2", "TV", Library.Kind.SHOWS),
            Library("3", "Home", Library.Kind.OTHER),
        ))
        assertEquals(VerseSetup.MOVIES, s.libraries["C/1"]?.category)
        assertEquals(VerseSetup.SERIES, s.libraries["C/2"]?.category)
        // Other videos get a "Videos" category of their own, made when the first one shows up.
        assertEquals(VerseSetup.VIDEOS, s.libraries["C/3"]?.category)
        assertEquals(listOf(OtherCategory(VerseSetup.VIDEOS, "Videos")), s.others)
    }

    @Test fun aServersLibrariesMergeWithItsChoices() {
        val merged = setup.withLibraries("B", listOf(Library("1", "Training 2", Library.Kind.OTHER), Library("7", "TV", Library.Kind.SHOWS)))
        assertEquals(LibraryChoice(VerseSetup.VIDEOS, "Training 2", Library.Kind.OTHER), merged.libraries["B/1"]) // kept, title refreshed
        assertEquals(VerseSetup.SERIES, merged.libraries["B/7"]?.category) // new, default
        assertNull(merged.libraries["B/2"]) // gone from the server
        assertEquals(setup.libraries["A/5"], merged.libraries["A/5"]) // other servers untouched
    }

    @Test fun deletingSendsLibrariesBackByType() {
        val s = setup.deleteOther("c1").deleteOther(VerseSetup.VIDEOS)
        assertEquals(VerseSetup.MOVIES, s.libraries["A/5"]?.category)
        assertEquals(VerseSetup.HIDDEN, s.libraries["B/1"]?.category) // other videos have no built-in home
        assertNull(s.pinned)
        assertTrue(s.others.isEmpty())
    }

    @Test fun onePinAtATime() {
        assertEquals(VerseSetup.VIDEOS, setup.togglePin(VerseSetup.VIDEOS).pinned)
        assertNull(setup.togglePin("c1").pinned)
    }

    @Test fun addRenameAndOrder() {
        val s = setup.addOther("Anime").rename(VerseSetup.SERIES, "TV").rename("c1", " Docs ")
        assertEquals("Anime", s.others.last().name)
        assertTrue(s.others.map { it.id }.distinct().size == s.others.size)
        assertEquals("TV", s.seriesName)
        assertEquals("Docs", s.name("c1"))
        assertEquals(setup, setup.rename("c1", "  ")) // blank names are ignored
        assertEquals(listOf(VerseSetup.VIDEOS, "c1"), setup.moveOther(VerseSetup.VIDEOS, -1).others.map { it.id })
    }

    @Test fun v1MigratesWithVideosAsAnOtherCategory() {
        val v1 = """{"servers":["A","B"],"kidsSeparate":true,"libraries":{
            "A/1":{"category":"MOVIES","title":"Films"},
            "A/2":{"category":"SERIES","title":"Cartoons","kids":true},
            "B/3":{"category":"VIDEOS","title":"Home videos"},
            "B/4":{"category":"NONE","title":"Music videos"}}}"""
        val s = VerseSetup.fromJson(v1)
        assertEquals(LibraryChoice(VerseSetup.MOVIES, "Films", Library.Kind.MOVIES), s.libraries["A/1"])
        // Kids' libraries leave the grown-ups' categories and go to the first Kids profile.
        assertEquals(VerseSetup.HIDDEN, s.libraries["A/2"]?.category)
        assertEquals(setOf("A/2"), VerseSetup.legacyKids(v1))
        assertEquals(VerseSetup.VIDEOS, s.libraries["B/3"]?.category)
        assertEquals(listOf(OtherCategory(VerseSetup.VIDEOS, "Videos")), s.others)
        assertEquals(VerseSetup.HIDDEN, s.libraries["B/4"]?.category)
        // The hand-made server order stays an override.
        assertTrue(s.serversOverride)
        assertTrue(s.confirmed)
        assertTrue(VerseSetup.legacyKids(s.toJson()).isEmpty())
    }
}
