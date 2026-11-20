package hu.molnarroland.plexverse.profile

import android.content.Context
import com.embergabor.simplex.core.plex.PlexClient
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Someone who watches: a Plex Home user, whose watched state, restrictions and PIN live on Plex, or the
 * one local Kids profile, which shares the account's token. [id] is the Plex user's uuid, or [LOCAL_KIDS].
 */
data class Profile(
    val id: String,
    val name: String,
    val thumb: String? = null,
    val admin: Boolean = false,
    /** A managed user with Plex restrictions. */
    val restricted: Boolean = false,
    /** Has a Plex PIN. */
    val protected: Boolean = false,
) {
    val local get() = id == LOCAL_KIDS

    companion object {
        const val LOCAL_KIDS = "local-kids"
        /** Stands in for the account when Plex Home isn't set up. */
        const val ACCOUNT = "account"
    }
}

/** plex.tv's Plex Home: the account's users, and switching to one of them. */
object PlexHome {
    private const val API = "https://plex.tv/api/v2"

    private fun request(plex: PlexClient, url: String, token: String) = Request.Builder().url(url)
        .header("Accept", "application/json")
        .header("X-Plex-Product", "PlexVerse")
        .header("X-Plex-Client-Identifier", plex.clientId)
        .header("X-Plex-Token", token)

    /** The Plex Home users; just the account itself when Plex Home isn't set up. */
    suspend fun users(plex: PlexClient, ownerToken: String): List<Profile> =
        parseUsers(plex.call(request(plex, "$API/home/users", ownerToken).build()))

    /**
     * The token of [profile], asked of plex.tv with [pin] when the user has one. A wrong PIN comes back
     * as a [com.embergabor.simplex.core.plex.PlexStatus] (401 or 403).
     */
    suspend fun switchTo(plex: PlexClient, ownerToken: String, profile: Profile, pin: String?): String {
        val query = pin?.let { "?pin=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val body = ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val json = plex.call(request(plex, "$API/home/users/${profile.id}/switch$query", ownerToken).post(body).build())
        return JSONObject(json).optString("authToken").ifEmpty { throw java.io.IOException("plex.tv didn't hand over the profile") }
    }

    /** The answer as an array of users, or an object holding them under "users". */
    fun parseUsers(json: String): List<Profile> {
        val trimmed = json.trim()
        val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("users") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val u = array.optJSONObject(i) ?: return@mapNotNull null
            val id = u.optString("uuid").ifEmpty { return@mapNotNull null }
            Profile(
                id = id,
                name = u.optString("title").ifEmpty { u.optString("username") }.ifEmpty { "Profile" },
                thumb = u.optString("thumb").ifEmpty { null },
                admin = u.optBoolean("admin"),
                restricted = u.optBoolean("restricted"),
                protected = u.optBoolean("protected"),
            )
        }
    }
}

/** How the app starts: asking who's watching, or as the last person. */
enum class Startup { ASK, LAST }

/** The profiles and their tokens, kept on the TV. The account's own token is the [ownerToken]. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    var ownerToken: String?
        get() = prefs.getString("ownerToken", null)
        set(v) { prefs.edit().putString("ownerToken", v).apply() }

    /** The Plex Home users as last listed. */
    var users: List<Profile>
        get() = prefs.getString("users", null)?.let { runCatching { PlexHome.parseUsers(it) }.getOrNull() }.orEmpty()
        set(v) {
            val array = JSONArray(v.map {
                JSONObject().put("uuid", it.id).put("title", it.name).put("thumb", it.thumb ?: "")
                    .put("admin", it.admin).put("restricted", it.restricted).put("protected", it.protected)
            })
            prefs.edit().putString("users", array.toString()).apply()
        }

    /** The local Kids profile, for households without Plex Home (or with no managed users). */
    var localKids: Boolean
        get() = prefs.getBoolean("localKids", false)
        set(v) { prefs.edit().putBoolean("localKids", v).apply() }

    var localKidsName: String
        get() = prefs.getString("localKidsName", null) ?: "Kids"
        set(v) { prefs.edit().putString("localKidsName", v).apply() }

    /** Everyone to pick from: the Plex Home users, then the local Kids profile. */
    val all: List<Profile>
        get() = users.ifEmpty { listOf(Profile(Profile.ACCOUNT, "Me", admin = true)) } +
            listOfNotNull(Profile(Profile.LOCAL_KIDS, localKidsName).takeIf { localKids })

    val owner: Profile? get() = all.firstOrNull { it.admin }

    var current: String?
        get() = prefs.getString("current", null)
        set(v) { prefs.edit().putString("current", v).apply() }

    val currentProfile: Profile? get() = current?.let { id -> all.find { it.id == id } }

    fun token(id: String): String? = prefs.getString("token-$id", null)
    fun setToken(id: String, token: String) = prefs.edit().putString("token-$id", token).apply()

    var startup: Startup
        get() = runCatching { Startup.valueOf(prefs.getString("startup", null) ?: "") }.getOrDefault(Startup.ASK)
        set(v) { prefs.edit().putString("startup", v.name).apply() }

    /** Whether [profile] opens Kids mode: managed users with restrictions do by default, and the local Kids profile always. */
    fun isKids(profile: Profile): Boolean =
        profile.local || (if (prefs.contains("kids-${profile.id}")) prefs.getBoolean("kids-${profile.id}", false) else profile.restricted)

    fun setKids(profile: Profile, kids: Boolean) = prefs.edit().putBoolean("kids-${profile.id}", kids).apply()

    fun kidsSettings(id: String): KidsSettings =
        prefs.getString("kidsSettings-$id", null)?.let { runCatching { KidsSettings.fromJson(it) }.getOrNull() } ?: KidsSettings()

    fun setKidsSettings(id: String, settings: KidsSettings) = prefs.edit().putString("kidsSettings-$id", settings.toJson()).apply()

    fun signOut() = prefs.edit().clear().apply()
}
