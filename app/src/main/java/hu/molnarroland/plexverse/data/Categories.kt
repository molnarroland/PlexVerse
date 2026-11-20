package hu.molnarroland.plexverse.data

import android.content.Context
import com.embergabor.simplex.core.plex.Library
import org.json.JSONArray
import org.json.JSONObject

/** A category the viewer made, listed under Others in the main menu. */
data class OtherCategory(val id: String, val name: String)

/** Which category a server library goes into. [title] and [kind] are kept for when its server can't be reached. */
data class LibraryChoice(val category: String, val title: String = "", val kind: Library.Kind = Library.Kind.MOVIES)

/**
 * PlexVerse's layout, kept per TV: the server order (only when moved by hand, see [ServerOrder]), and
 * each library's category, keyed by "machineId/sectionKey" (section keys repeat on every server).
 *
 * Movies and Series are built in: they can be renamed but not deleted, and Home is built from them. Every
 * other category is an [OtherCategory] under Others, one of which may be [pinned] to the main menu.
 */
data class VerseSetup(
    val servers: List<String> = emptyList(),
    /** The server order was moved by hand; otherwise it's ranked automatically. */
    val serversOverride: Boolean = false,
    val libraries: Map<String, LibraryChoice> = emptyMap(),
    val moviesName: String = "Movies",
    val seriesName: String = "Series",
    val others: List<OtherCategory> = emptyList(),
    val pinned: String? = null,
    /** The first-run check of servers and categories was done (or skipped with "Looks right"). */
    val confirmed: Boolean = false,
) {
    fun name(category: String): String = when (category) {
        MOVIES -> moviesName
        SERIES -> seriesName
        HIDDEN -> "Hidden"
        else -> others.find { it.id == category }?.name ?: "?"
    }

    val pinnedOther get() = others.find { it.id == pinned }

    /** Every category a library can go into, in menu order, Hidden last. */
    val allCategories get() = listOf(MOVIES, SERIES) + others.map { it.id } + HIDDEN

    /** The servers in the manual order: [machineIds] in it first, new ones after. */
    fun ordered(machineIds: Collection<String>): List<String> =
        servers.filter { it in machineIds } + machineIds.filter { it !in servers }

    fun choice(machineId: String, library: Library) =
        libraries[key(machineId, library.key)] ?: LibraryChoice(defaultFor(library.kind), library.title, library.kind)

    /** The library keys in [category]. */
    fun keysIn(category: String) = libraries.filterValues { it.category == category }.keys

    /**
     * A server's current libraries: new ones get their default (other-video libraries a "Videos" category,
     * made if needed), titles and kinds are refreshed, gone ones dropped.
     */
    fun withLibraries(machineId: String, current: List<Library>): VerseSetup {
        val others = libraries.filterKeys { !it.startsWith("$machineId/") }
        val mine = current.associate { key(machineId, it.key) to choice(machineId, it).copy(title = it.title, kind = it.kind) }
        val needsVideos = mine.values.any { it.category == VIDEOS } && this.others.none { it.id == VIDEOS }
        return copy(
            libraries = others + mine,
            others = if (needsVideos) this.others + OtherCategory(VIDEOS, "Videos") else this.others,
        )
    }

    /** The stored choices of one server, in key order. */
    fun storedFor(machineId: String) = libraries.filterKeys { it.startsWith("$machineId/") }.toSortedMap().entries.toList()

    fun move(libraryKey: String, category: String): VerseSetup =
        libraries[libraryKey]?.let { copy(libraries = libraries + (libraryKey to it.copy(category = category))) } ?: this

    fun rename(category: String, name: String): VerseSetup {
        val n = name.trim().ifEmpty { return this }
        return when (category) {
            MOVIES -> copy(moviesName = n)
            SERIES -> copy(seriesName = n)
            else -> copy(others = others.map { if (it.id == category) it.copy(name = n) else it })
        }
    }

    fun addOther(name: String): VerseSetup {
        val n = name.trim().ifEmpty { return this }
        var i = others.size + 1
        while (others.any { it.id == "c$i" } || "c$i" == VIDEOS) i++
        return copy(others = others + OtherCategory("c$i", n))
    }

    /** Deleting a category sends its libraries back to Movies or Series by type (other videos to Hidden). */
    fun deleteOther(id: String): VerseSetup = copy(
        others = others.filter { it.id != id },
        pinned = pinned.takeIf { it != id },
        libraries = libraries.mapValues { (_, c) -> if (c.category == id) c.copy(category = defaultBuiltIn(c.kind)) else c },
    )

    fun moveOther(id: String, by: Int): VerseSetup {
        val list = others.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) return this
        list.add(to, list.removeAt(from))
        return copy(others = list)
    }

    /** Pins [id] to the main menu, or unpins it if it was the pinned one. One at a time. */
    fun togglePin(id: String) = copy(pinned = if (pinned == id) null else id)

    fun toJson(): String = JSONObject()
        .put("version", 2)
        .put("servers", JSONArray(servers))
        .put("serversOverride", serversOverride)
        .put("libraries", JSONObject().apply {
            for ((k, c) in libraries) put(k, JSONObject().put("category", c.category).put("title", c.title).put("kind", c.kind.name))
        })
        .put("moviesName", moviesName)
        .put("seriesName", seriesName)
        .put("others", JSONArray(others.map { JSONObject().put("id", it.id).put("name", it.name) }))
        .put("pinned", pinned ?: JSONObject.NULL)
        .put("confirmed", confirmed)
        .toString()

    companion object {
        const val MOVIES = "movies"
        const val SERIES = "series"
        const val HIDDEN = "hidden"
        /** The Other category for other-video libraries, made when the first one shows up. */
        const val VIDEOS = "videos"

        fun key(machineId: String, sectionKey: String) = "$machineId/$sectionKey"

        fun defaultFor(kind: Library.Kind) = when (kind) {
            Library.Kind.MOVIES -> MOVIES
            Library.Kind.SHOWS -> SERIES
            Library.Kind.OTHER -> VIDEOS
        }

        private fun defaultBuiltIn(kind: Library.Kind) = when (kind) {
            Library.Kind.MOVIES -> MOVIES
            Library.Kind.SHOWS -> SERIES
            Library.Kind.OTHER -> HIDDEN
        }

        private fun kindOf(name: String?) = Library.Kind.entries.firstOrNull { it.name == name }

        fun fromJson(json: String): VerseSetup {
            val o = JSONObject(json)
            val servers = o.optJSONArray("servers") ?: JSONArray()
            val serverList = (0 until servers.length()).map { servers.getString(it) }
            val libs = o.optJSONObject("libraries") ?: JSONObject()
            if (o.optInt("version", 1) < 2) return fromV1(o, serverList, libs)
            val others = o.optJSONArray("others") ?: JSONArray()
            return VerseSetup(
                servers = serverList,
                serversOverride = o.optBoolean("serversOverride"),
                libraries = libs.keys().asSequence().associateWith { k ->
                    val c = libs.getJSONObject(k)
                    LibraryChoice(c.optString("category", HIDDEN), c.optString("title"), kindOf(c.optString("kind")) ?: Library.Kind.MOVIES)
                },
                moviesName = o.optString("moviesName", "Movies"),
                seriesName = o.optString("seriesName", "Series"),
                others = (0 until others.length()).map { others.getJSONObject(it).let { c -> OtherCategory(c.getString("id"), c.getString("name")) } },
                pinned = if (o.isNull("pinned")) null else o.optString("pinned").ifEmpty { null },
                confirmed = o.optBoolean("confirmed"),
            )
        }

        /**
         * Today's setup (fixed Movies / Series / Videos / None, with a Kids flag): Videos becomes an Other
         * category named "Videos", None becomes Hidden. With kids kept separate, kids' libraries are hidden
         * from the grown-ups' categories; [legacyKids] hands them to the first Kids profile.
         */
        private fun fromV1(o: JSONObject, servers: List<String>, libs: JSONObject): VerseSetup {
            val kidsSeparate = o.optBoolean("kidsSeparate")
            val libraries = libs.keys().asSequence().associateWith { k ->
                val c = libs.getJSONObject(k)
                val (category, kind) = when (c.optString("category")) {
                    "MOVIES" -> MOVIES to Library.Kind.MOVIES
                    "SERIES" -> SERIES to Library.Kind.SHOWS
                    "VIDEOS" -> VIDEOS to Library.Kind.OTHER
                    else -> HIDDEN to Library.Kind.MOVIES
                }
                val kids = kidsSeparate && c.optBoolean("kids") && (category == MOVIES || category == SERIES)
                LibraryChoice(if (kids) HIDDEN else category, c.optString("title"), kind)
            }
            return VerseSetup(
                servers = servers,
                serversOverride = servers.isNotEmpty(),
                libraries = libraries,
                others = if (libraries.values.any { it.category == VIDEOS }) listOf(OtherCategory(VIDEOS, "Videos")) else emptyList(),
                confirmed = libraries.isNotEmpty(),
            )
        }

        /** The kids' libraries of a v1 setup kept separate, else empty. */
        fun legacyKids(json: String): Set<String> {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptySet()
            if (o.optInt("version", 1) >= 2 || !o.optBoolean("kidsSeparate")) return emptySet()
            val libs = o.optJSONObject("libraries") ?: return emptySet()
            return libs.keys().asSequence().filter { k ->
                val c = libs.getJSONObject(k)
                c.optBoolean("kids") && c.optString("category") in setOf("MOVIES", "SERIES")
            }.toSet()
        }
    }
}

/** [VerseSetup], kept on the TV. */
class SetupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var setup: VerseSetup
        get() = prefs.getString("plexVerse", null)?.let { runCatching { VerseSetup.fromJson(it) }.getOrNull() } ?: VerseSetup()
        set(value) { prefs.edit().putString("plexVerse", value.toJson()).apply() }

    /** Today's kids' libraries, once, for moving them into the first Kids profile. */
    fun takeLegacyKids(): Set<String> {
        val json = prefs.getString("plexVerse", null) ?: return emptySet()
        val kids = VerseSetup.legacyKids(json)
        if (kids.isNotEmpty()) setup = VerseSetup.fromJson(json)
        return kids
    }
}
