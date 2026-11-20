package hu.molnarroland.plexverse.data

import android.content.Context
import java.net.URI

/** What ranks a server: whose it is, where it answered, and how fast it answers. */
data class ServerFacts(val machineId: String, val owned: Boolean, val local: Boolean?, val latencyMs: Long?)

/**
 * Which server a title plays from: the copy on the highest-ranked server that answers. Ranked
 * automatically (owned before shared, local before remote, faster first) unless moved by hand in
 * Servers & categories, which saves the order as an override.
 */
object ServerOrder {
    fun auto(facts: List<ServerFacts>): List<String> = facts
        .sortedWith(compareBy<ServerFacts>({ !it.owned }, { it.local != true }, { it.latencyMs ?: Long.MAX_VALUE }))
        .map { it.machineId }

    fun order(setup: VerseSetup, facts: List<ServerFacts>): List<String> =
        if (setup.serversOverride) setup.ordered(facts.map { it.machineId }) else auto(facts)

    /** Whether [uri] is on the TV's own network: a private address, or a plex.direct name carrying one. */
    fun isLocal(uri: String): Boolean {
        val host = runCatching { URI(uri).host }.getOrNull() ?: return false
        val address = if (host.endsWith(".plex.direct")) host.substringBefore('.').replace('-', '.') else host
        if (address.endsWith(".local")) return true
        val p = address.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31) || p[0] == 127
    }

    /** "fast", "ok" or "slow", from a server's answer time. */
    fun speedLabel(ms: Long?): String? = when {
        ms == null -> null
        ms < 80 -> "fast"
        ms < 300 -> "ok"
        else -> "slow"
    }
}

/**
 * Each server's answer time, measured whenever PlexVerse talks to it (a rolling average, so one slow
 * answer doesn't flip the order), and whether it was reached on the local network. Kept on the TV.
 */
class SpeedPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("speed", Context.MODE_PRIVATE)

    fun latency(machineId: String): Long? = prefs.getLong("ms-$machineId", -1).takeIf { it >= 0 }
    fun local(machineId: String): Boolean? = if (prefs.contains("local-$machineId")) prefs.getBoolean("local-$machineId", false) else null

    fun record(machineId: String, ms: Long, uri: String) {
        val avg = latency(machineId)?.let { (it * 2 + ms) / 3 } ?: ms
        prefs.edit().putLong("ms-$machineId", avg).putBoolean("local-$machineId", ServerOrder.isLocal(uri)).apply()
    }
}
