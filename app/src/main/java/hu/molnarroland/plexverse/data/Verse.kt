package hu.molnarroland.plexverse.data

import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.PlexServer

/** One copy of a title, on the server with [machineId]. */
data class Source(val machineId: String, val item: Item) {
    val ref get() = "$machineId|${item.ratingKey}"
}

/** One copy of an item on a server that's been reached. */
data class Copy(val server: PlexServer, val item: Item) {
    val ref get() = server.ref(item.ratingKey)
}

/** A player queue entry that tries [copies] in order. */
fun queueEntry(copies: List<Copy>) = copies.joinToString(",") { it.ref }

/**
 * A title merged across servers. [sources] are in server preference order; [item] is the first one's,
 * with the progress and watched state of the most recently watched copy.
 */
data class VerseEntry(val key: String, val item: Item, val sources: List<Source>)

/** A Continue watching card: the copy to show, and the sources to play it from, in order. */
data class Continuing(val item: Item, val sources: List<Source>)

object Verse {
    // Ids that mean the same title on every server. Others (local://, the "none" agent) are per server.
    private val shared = Regex("^(plex|imdb|tmdb|tvdb)://")

    // The legacy agents' ids ("com.plexapp.agents.imdb://tt0078748?lang=en"), for libraries not moved to
    // Plex's current agents. They carry no other ids, so without this they never match a current copy.
    private val legacy = Regex("^com\\.plexapp\\.agents\\.(imdb|themoviedb|thetvdb)://([^/?]+)(\\?.*)?$")
    private val legacyScheme = mapOf("imdb" to "imdb", "themoviedb" to "tmdb", "thetvdb" to "tvdb")

    /** A title's id as the current agents write it; legacy ids of a whole title are rewritten, the rest kept. */
    fun normalizeGuid(guid: String): String =
        legacy.matchEntire(guid)?.let { m -> "${legacyScheme.getValue(m.groupValues[1])}://${m.groupValues[2]}" } ?: guid

    private fun sharedIds(item: Item) = item.guids.map(::normalizeGuid).filter { shared.containsMatchIn(it) }.distinct()

    private fun normalize(title: String) = SearchMatch.terms(title).joinToString(" ")

    /** A–Z order: the sort title (without "The"), ignoring case and accents. */
    fun sortKey(item: Item) = normalize(item.titleSort)

    /**
     * What a title is matched by: its shared ids, and its title with its year. The title also counts when
     * ids are there, since two servers can disagree on them (one matched to another id, a wrong match).
     * Without a year only ids match, unless there are none: a remake must not merge with the original.
     */
    fun matchKeys(item: Item): List<String> {
        val ids = sharedIds(item)
        val title = "title:${normalize(item.title)}|${item.year ?: ""}".takeIf { item.year != null || ids.isEmpty() }
        return ids + listOfNotNull(title)
    }

    /** An episode's: its shared ids, or without any, its season and number. */
    fun episodeKeys(item: Item): List<String> =
        sharedIds(item).ifEmpty { listOf("episode:${item.parentIndex ?: 0}|${item.index ?: 0}") }

    /** Groups [sources] that share any of their [keys] (a union-find over the keys), sorted A–Z. */
    fun merge(sources: List<Source>, order: List<String>, keys: (Item) -> List<String> = ::matchKeys): List<VerseEntry> {
        val parent = IntArray(sources.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x].also { parent[x] = parent[parent[x]] }
            return x
        }
        val owner = HashMap<String, Int>()
        sources.forEachIndexed { i, s ->
            for (k in keys(s.item)) owner.putIfAbsent(k, i)?.let { parent[root(i)] = root(it) }
        }
        val rank = order.withIndex().associate { (i, id) -> id to i }
        return sources.indices.groupBy(::root).values.map { members ->
            val group = members.map { sources[it] }.sortedBy { rank[it.machineId] ?: Int.MAX_VALUE }
            val first = group.first().item
            val newest = group.maxBy { it.item.lastViewedAt }.item
            VerseEntry(
                key = keys(first).first(),
                item = first.copy(viewOffset = newest.viewOffset, viewCount = newest.viewCount, viewedLeafCount = newest.viewedLeafCount),
                sources = group,
            )
        }.sortedBy { sortKey(it.item) }
    }
}

/**
 * The Continue watching row from every server's in-progress items. A movie plays with every copy of
 * its merged title in [titles]; an episode or video on several servers shows once, from the preferred
 * server. Newest first.
 */
fun Verse.continuing(inProgress: List<Source>, order: List<String>, titles: List<VerseEntry>): List<Continuing> {
    val rank = order.withIndex().associate { (i, id) -> id to i }
    val titleOf = HashMap<String, VerseEntry>()
    for (e in titles) for (s in e.sources) titleOf[s.ref] = e
    return inProgress
        .groupBy { s -> titleOf[s.ref]?.key ?: episodeKeys(s.item).firstOrNull { !it.startsWith("episode:") } ?: s.ref }
        .values
        .sortedByDescending { group -> group.maxOf { it.item.lastViewedAt } }
        .map { group ->
            val first = group.minBy { rank[it.machineId] ?: Int.MAX_VALUE }
            Continuing(first.item, titleOf[first.ref]?.sources ?: listOf(first))
        }
}
