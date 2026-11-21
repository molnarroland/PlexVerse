package hu.molnarroland.plexverse.data

import android.content.Context
import android.os.SystemClock
import com.embergabor.simplex.core.plex.Item
import com.embergabor.simplex.core.plex.Library
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.plex.PlexServer
import hu.molnarroland.plexverse.profile.KidsSettings
import hu.molnarroland.plexverse.profile.ProfileStore
import hu.molnarroland.plexverse.profile.Ratings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

sealed interface ServerState {
    data object Waiting : ServerState
    data class Done(val items: Int) : ServerState
    data class Failed(val reason: String) : ServerState
}

/**
 * PlexVerse's lists from every server, for one profile: the copy saved on the TV straight away, then
 * each server's current lists as they arrive, all servers at once. Kept while the profile is, so grids
 * open instantly; switching profile starts a new one (each profile has its own tokens and progress).
 */
class VerseStore private constructor(context: Context, private val plex: PlexClient, val profile: String) {
    private val app = context.applicationContext
    private val setupPrefs = SetupPrefs(app)
    private val profiles = ProfileStore(app)
    private val speed = SpeedPrefs(app)
    private val dir = File(app.cacheDir, "verse/$profile")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var started = 0L

    val setup: VerseSetup get() = setupPrefs.setup

    /** The Kids profile's settings, or null for a grown-up. */
    val kids: KidsSettings?
        get() = profiles.currentProfile?.takeIf { profiles.isKids(it) }?.let { profiles.kidsSettings(it.id) }

    private val _servers = MutableStateFlow<Map<String, ServerState>>(emptyMap())
    /** Each server with a library in use, by machine id, and how its fetch went. */
    val servers: StateFlow<Map<String, ServerState>> = _servers

    private val _lists = MutableStateFlow<Map<String, List<Item>>>(emptyMap())
    /** Each library's titles, by "machineId/sectionKey". */
    val lists: StateFlow<Map<String, List<Item>>> = _lists

    private val _ratings = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Content ratings by "machineId|ratingKey" (simplex-core's items don't carry them). */
    val ratings: StateFlow<Map<String, String>> = _ratings

    private val _collections = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** The Plex collections each title is in, by "machineId|ratingKey" (from the lists' Collection tags). */
    val collections: StateFlow<Map<String, List<String>>> = _collections

    /** The collections of any copy of [entry]. */
    fun collectionsOf(entry: VerseEntry, collections: Map<String, List<String>>): List<String> =
        entry.sources.flatMap { collections[it.ref].orEmpty() }

    private val _continuing = MutableStateFlow<Map<String, List<Item>>>(emptyMap())
    /** Each server's Continue watching items, by machine id. */
    val continuing: StateFlow<Map<String, List<Item>>> = _continuing

    private val _connected = MutableStateFlow<Map<String, PlexServer>>(emptyMap())
    /** The servers reached so far. */
    val connected: StateFlow<Map<String, PlexServer>> = _connected

    private fun file(key: String) = File(dir, "verse-${key.replace('/', '-')}.json")

    /** The libraries this profile lists: a Kids profile's chosen ones, or every category's except Hidden. */
    private fun listedKeys(): Set<String> {
        val s = setup
        val k = kids
        val keys = if (k != null) k.libraries else s.libraries.filterValues { it.category != VerseSetup.HIDDEN }.keys
        return keys.filter { s.libraries[it]?.kind != Library.Kind.OTHER }.toSet()
    }

    /** The libraries in use, including other-video ones (browsed by folder, so only their server counts). */
    private fun usedKeys(): Set<String> {
        val k = kids
        return if (k != null) k.libraries else setup.libraries.filterValues { it.category != VerseSetup.HIDDEN }.keys
    }

    /** Whether any list was saved before, i.e. whether this isn't the very first open. */
    fun hasCache() = listedKeys().any { file(it).exists() }

    /** The server order: by hand if moved in setup, else ranked by ownership, network and speed. */
    fun order(ids: Collection<String>): List<String> {
        val owned = plex.cachedServers.associate { it.machineId to it.owned }
        val facts = ids.distinct().map { ServerFacts(it, owned[it] ?: false, speed.local(it), speed.latency(it)) }
        return ServerOrder.order(setup, facts)
    }

    /** Fetches every server again, unless that's already running or happened in the last few minutes. */
    fun refresh(force: Boolean = false) {
        // ponytail: "once per visit" is approximated by 5 minutes, so Back from a grid doesn't refetch.
        if (job?.isActive == true || (!force && System.currentTimeMillis() - started < 5 * 60_000)) return
        started = System.currentTimeMillis()
        job = scope.launch {
            val listed = listedKeys()
            val ids = usedKeys().map { it.substringBefore('/') }.distinct()
            _servers.value = ids.associateWith { ServerState.Waiting }
            if (_lists.value.isEmpty()) {
                val saved = withContext(Dispatchers.IO) {
                    listed.mapNotNull { k -> runCatching { k to file(k).readText() }.getOrNull() }
                }
                withContext(Dispatchers.Default) { publish(saved) }
            }
            // Also picks up servers shared recently and tokens that changed.
            launch { runCatching { plex.servers() } }
            for (id in ids) launch {
                val state = try {
                    withTimeoutOrNull(15_000) { fetch(id, listed.filter { it.startsWith("$id/") }) }
                        ?: ServerState.Failed("not reachable")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ServerState.Failed(e.message ?: "failed")
                }
                _servers.update { it + (id to state) }
            }
        }
    }

    /** Asks the reached servers again for what's in progress, e.g. after the player closes. */
    fun refreshContinuing() {
        for ((id, server) in _connected.value) scope.launch {
            runCatching { server.continueWatching() }.onSuccess { c -> _continuing.update { it + (id to c) } }
        }
    }

    private suspend fun fetch(id: String, keys: List<String>): ServerState {
        val t0 = SystemClock.elapsedRealtime()
        val server = plex.server(id) ?: return ServerState.Failed("not reachable")
        _connected.update { it + (id to server) }
        // Extra: if it fails, the lists still count.
        scope.launch { runCatching { server.continueWatching() }.onSuccess { c -> _continuing.update { it + (id to c) } } }
        // How fast it answers a small request, for ranking it among the others.
        scope.launch {
            val t = SystemClock.elapsedRealtime()
            if (runCatching { server.libraries() }.isSuccess) speed.record(id, SystemClock.elapsedRealtime() - t, server.uri)
        }
        val fresh = coroutineScope {
            keys.map { k -> async { k to server.libraryJson(k.substringAfter('/'), episodes = false) } }.awaitAll()
        }
        val count = withContext(Dispatchers.Default) { publish(fresh) }
        if (keys.isEmpty()) speed.record(id, SystemClock.elapsedRealtime() - t0, server.uri)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            for ((k, json) in fresh) runCatching {
                val tmp = File(file(k).path + ".part")
                tmp.writeText(json)
                tmp.renameTo(file(k))
            }
        }
        return ServerState.Done(count)
    }

    /** Parses raw lists into [lists], [ratings] and [collections]; returns how many titles they hold. */
    private fun publish(raw: List<Pair<String, String>>): Int {
        val parsed = raw.associate { (k, json) -> k to PlexClient.parseItems(json) }
        val ratings = HashMap<String, String>()
        val collections = HashMap<String, List<String>>()
        for ((k, json) in raw) {
            val id = k.substringBefore('/')
            val meta = runCatching { JSONObject(json).optJSONObject("MediaContainer")?.optJSONArray("Metadata") }.getOrNull() ?: continue
            for (i in 0 until meta.length()) {
                val o = meta.optJSONObject(i) ?: continue
                val ref = "$id|${o.optString("ratingKey")}"
                o.optString("contentRating").ifEmpty { null }?.let { ratings[ref] = it }
                val tags = o.optJSONArray("Collection")
                if (tags != null) collections[ref] = (0 until tags.length()).mapNotNull { j -> tags.optJSONObject(j)?.optString("tag")?.ifEmpty { null } }
            }
        }
        _lists.update { it + parsed }
        _ratings.update { it + ratings }
        _collections.update { it + collections }
        return parsed.values.sumOf { it.size }
    }

    private fun allowedForKids(machineId: String, item: Item, kids: KidsSettings, ratings: Map<String, String>) =
        Ratings.allowed(ratings["$machineId|${item.ratingKey}"], kids.maxAge)

    /** One category's titles merged across servers, A–Z (movie and show libraries; folders aren't merged). */
    fun entries(category: String, lists: Map<String, List<Item>>): List<VerseEntry> {
        val keys = setup.keysIn(category)
        val movies = sourcesOf(keys.filter { setup.libraries[it]?.kind == Library.Kind.MOVIES }, lists)
        val shows = sourcesOf(keys.filter { setup.libraries[it]?.kind == Library.Kind.SHOWS }, lists)
        // A film and a show never match, so they're merged separately.
        return (merge(movies) + merge(shows)).sortedBy { Verse.sortKey(it.item) }
    }

    /** The other-video libraries of a category, browsed by folder: (machineId, sectionKey, title) in server order. */
    fun folders(category: String): List<Triple<String, String, String>> {
        val s = setup
        val keys = s.keysIn(category).filter { s.libraries[it]?.kind == Library.Kind.OTHER }
        return order(keys.map { it.substringBefore('/') }).flatMap { id ->
            keys.filter { it.startsWith("$id/") }.sorted().map { Triple(id, it.substringAfter('/'), s.libraries.getValue(it).title) }
        }
    }

    /** The Kids profile's movies and series together, A–Z, without anything rated above its ceiling. */
    fun kidsEntries(lists: Map<String, List<Item>>, ratings: Map<String, String>): List<VerseEntry> {
        val k = kids ?: return emptyList()
        val s = setup
        fun of(kind: Library.Kind) = sourcesOf(k.libraries.filter { s.libraries[it]?.kind == kind }, lists)
            .filter { allowedForKids(it.machineId, it.item, k, ratings) }
        return (merge(of(Library.Kind.MOVIES)) + merge(of(Library.Kind.SHOWS))).sortedBy { Verse.sortKey(it.item) }
    }

    /** The newest titles of a category, newest first. */
    fun recentlyAdded(category: String, lists: Map<String, List<Item>>, count: Int = 20): List<VerseEntry> =
        GridSort.ADDED.apply(entries(category, lists)).take(count)

    /**
     * Continue watching across servers: items from libraries in use (a Kids profile's own, within its
     * ceiling), newest first.
     */
    fun continuingRow(lists: Map<String, List<Item>>, continuing: Map<String, List<Item>>, ratings: Map<String, String>): List<Continuing> {
        val s = setup
        val k = kids
        val used = usedKeys()
        val inProgress = continuing.flatMap { (id, items) ->
            items.filter { item ->
                val key = item.librarySectionId?.let { VerseSetup.key(id, it) } ?: return@filter false
                // An episode's rating is its show's, which is what's in the lists.
                key in used && (k == null || allowedForKids(id, item, k, ratings) &&
                    (item.grandparentRatingKey == null || Ratings.allowed(ratings["$id|${item.grandparentRatingKey}"], k.maxAge)))
            }.map { Source(id, it) }
        }
        val titles = if (k != null) kidsEntries(lists, ratings) else entries(VerseSetup.MOVIES, lists) + s.others.flatMap { entries(it.id, lists) }
        return Verse.continuing(inProgress, order(continuing.keys), titles)
    }

    private fun sourcesOf(keys: Collection<String>, lists: Map<String, List<Item>>) =
        keys.flatMap { k -> lists[k].orEmpty().map { Source(k.substringBefore('/'), it) } }

    private fun merge(sources: List<Source>) = Verse.merge(sources, order(sources.map { it.machineId }))

    private fun close() = scope.cancel()

    companion object {
        @Volatile private var instance: VerseStore? = null

        /** The store of the current profile. */
        fun get(context: Context, plex: PlexClient): VerseStore {
            val profile = ProfileStore(context).current ?: "none"
            instance?.takeIf { it.profile == profile }?.let { return it }
            return synchronized(this) {
                instance?.takeIf { it.profile == profile } ?: VerseStore(context, plex, profile).also {
                    instance?.close()
                    instance = it
                }
            }
        }

        /** Forgets the lists in memory, e.g. after the setup changed which libraries are used. */
        fun reset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
