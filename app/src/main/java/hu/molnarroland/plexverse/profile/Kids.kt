package hu.molnarroland.plexverse.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a Kids profile sees: [libraries] ("machineId/sectionKey") chosen by a grown-up, nothing rated
 * above [maxAge] (null: no ceiling), and at most [episodeLimit] episodes in a row (0: no limit). Plex's
 * own restrictions for managed users apply on top, on the server.
 */
data class KidsSettings(
    val libraries: Set<String> = emptySet(),
    val maxAge: Int? = 6,
    val episodeLimit: Int = 3,
) {
    fun toJson(): String = JSONObject()
        .put("libraries", JSONArray(libraries.toList()))
        .put("maxAge", maxAge ?: JSONObject.NULL)
        .put("episodeLimit", episodeLimit)
        .toString()

    companion object {
        fun fromJson(json: String): KidsSettings {
            val o = JSONObject(json)
            val libs = o.optJSONArray("libraries") ?: JSONArray()
            return KidsSettings(
                libraries = (0 until libs.length()).map { libs.getString(it) }.toSet(),
                maxAge = if (o.isNull("maxAge")) null else o.optInt("maxAge", 6),
                episodeLimit = o.optInt("episodeLimit", 3),
            )
        }

        /** The ceilings offered in the Kids profile's settings. */
        val AGES = listOf(0, 6, 9, 12, 16)
    }
}

/** Content ratings as ages, across the systems Plex uses ("PG", "TV-Y7", "de/12", "gb/15", "12"). */
object Ratings {
    private val us = mapOf(
        "G" to 0, "TV-Y" to 0, "TV-G" to 0, "E" to 0, "U" to 0, "APPROVED" to 0, "PASSED" to 0,
        "TV-Y7" to 7, "TV-Y7-FV" to 7,
        "PG" to 6, "TV-PG" to 6, "UC" to 6,
        "PG-13" to 13, "TV-14" to 14, "12A" to 12,
        "R" to 17, "TV-MA" to 17, "NC-17" to 18, "X" to 18,
    )

    /** The youngest age [rating] is for, or null when it doesn't say (unrated). */
    fun age(rating: String?): Int? {
        val r = rating?.trim()?.uppercase()?.ifEmpty { null } ?: return null
        // "de/12", "gb/15", "hu/16": the country, then the age.
        val bare = r.substringAfter('/')
        us[bare]?.let { return it }
        bare.trimEnd('+').toIntOrNull()?.let { return it }
        // "FSK 12", "Ages 6+"
        Regex("(\\d{1,2})").find(bare)?.let { return it.groupValues[1].toInt() }
        return null
    }

    /**
     * Whether a title rated [rating] is allowed under [maxAge]. Unrated titles are let through: they're
     * in libraries a grown-up chose for the child.
     */
    fun allowed(rating: String?, maxAge: Int?): Boolean {
        if (maxAge == null) return true
        val age = age(rating) ?: return true
        return age <= maxAge
    }

    fun label(maxAge: Int?) = when (maxAge) {
        null -> "No limit"
        0 -> "All ages"
        else -> "Up to $maxAge"
    }
}
