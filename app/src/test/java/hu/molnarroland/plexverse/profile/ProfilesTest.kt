package hu.molnarroland.plexverse.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    @Test fun pickerOverflow() {
        val all = (1..12).map { Profile("p$it", "P$it") }
        val (few, none) = splitForPicker(all.take(10), null, 10)
        assertEquals(10, few.size)
        assertTrue(none.isEmpty())
        val (shown, rest) = splitForPicker(all, "p2", 10)
        assertEquals((1..9).map { "p$it" }, shown.map { it.id })
        assertEquals(listOf("p10", "p11", "p12"), rest.map { it.id })
        val (withMe, others) = splitForPicker(all, "p11", 10)
        assertEquals((1..8).map { "p$it" } + "p11", withMe.map { it.id })
        assertEquals(listOf("p9", "p10", "p12"), others.map { it.id })
    }

    @Test fun plexHomeUsers() {
        val json = """{"id":1,"name":"Home","users":[
            {"id":1,"uuid":"aaa","title":"Roland","username":"roland","thumb":"https://plex.tv/users/aaa/avatar","admin":true,"restricted":false,"protected":true},
            {"id":2,"uuid":"bbb","title":"","username":"","admin":false,"restricted":true,"protected":false},
            {"id":3,"title":"no uuid"}]}"""
        val users = PlexHome.parseUsers(json)
        assertEquals(listOf("aaa", "bbb"), users.map { it.id })
        assertTrue(users[0].admin && users[0].protected)
        assertEquals("Profile", users[1].name)
        assertTrue(users[1].restricted)
        assertEquals(users, PlexHome.parseUsers("""[${json.substringAfter("[").substringBeforeLast("]")}]"""))
    }

    @Test fun ratingsAsAges() {
        assertEquals(0, Ratings.age("G"))
        assertEquals(0, Ratings.age("TV-Y"))
        assertEquals(7, Ratings.age("TV-Y7"))
        assertEquals(6, Ratings.age("PG"))
        assertEquals(13, Ratings.age("PG-13"))
        assertEquals(12, Ratings.age("de/12"))
        assertEquals(15, Ratings.age("gb/15"))
        assertEquals(16, Ratings.age("16+"))
        assertEquals(17, Ratings.age("R"))
        assertNull(Ratings.age(null))
        assertNull(Ratings.age("Not Rated"))
    }

    @Test fun ceiling() {
        assertTrue(Ratings.allowed("G", 6))
        assertTrue(Ratings.allowed("PG", 6))
        assertFalse(Ratings.allowed("PG-13", 6))
        assertFalse(Ratings.allowed("de/12", 9))
        assertTrue(Ratings.allowed("de/12", 12))
        // Unrated titles are in libraries a grown-up chose, so they're let through.
        assertTrue(Ratings.allowed(null, 0))
        assertTrue(Ratings.allowed("R", null))
    }

    @Test fun kidsSettingsRoundTrip() {
        val s = KidsSettings(setOf("A/1", "B/2"), maxAge = null, episodeLimit = 0)
        assertEquals(s, KidsSettings.fromJson(s.toJson()))
        assertEquals(KidsSettings(), KidsSettings.fromJson(KidsSettings().toJson()))
    }
}
