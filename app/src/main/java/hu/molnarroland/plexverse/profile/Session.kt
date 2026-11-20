package hu.molnarroland.plexverse.profile

import android.content.Context
import com.embergabor.simplex.core.plex.PlexClient
import hu.molnarroland.plexverse.ui.SetupPrefs
import hu.molnarroland.plexverse.ui.VerseStore

/**
 * Signing in and switching between profiles. simplex-core's [PlexClient] holds one account token and
 * one "home" server (the player needs one); a profile switch connects it again with that profile's
 * token, so every server hands out the profile's own access token, progress and restrictions.
 */
object Session {

    /** Keeps the account token from sign-in and lists Plex Home. [PlexClient.connect] has already run. */
    suspend fun signedIn(context: Context, plex: PlexClient, accountToken: String) {
        val store = ProfileStore(context)
        store.ownerToken = accountToken
        store.users = runCatching { PlexHome.users(plex, accountToken) }.getOrDefault(emptyList())
        val owner = store.owner
        store.current = owner?.id
        owner?.let { store.setToken(it.id, accountToken) }
        // Today's kids' libraries (kept separate) become the first Kids profile.
        val legacy = SetupPrefs(context).takeLegacyKids()
        if (legacy.isNotEmpty()) {
            store.localKids = true
            store.setKidsSettings(Profile.LOCAL_KIDS, KidsSettings(libraries = legacy, maxAge = null))
        }
        VerseStore.reset()
    }

    /** Lists Plex Home again (users added or changed since), keeping the last list if plex.tv doesn't answer. */
    suspend fun refreshUsers(context: Context, plex: PlexClient) {
        val store = ProfileStore(context)
        val owner = store.ownerToken ?: return
        runCatching { PlexHome.users(plex, owner) }.onSuccess { store.users = it }
    }

    /** Whether picking [profile] needs its PIN first. */
    fun needsPin(context: Context, profile: Profile) =
        profile.protected && !(profile.local || profile.id == Profile.ACCOUNT)

    /**
     * Becomes [profile]: its token from plex.tv (with [pin] when it has one), then every server again
     * with it. If none of its servers answers, the previous profile stays.
     */
    suspend fun switchTo(context: Context, plex: PlexClient, profile: Profile, pin: String?) {
        val store = ProfileStore(context)
        val owner = requireNotNull(store.ownerToken) { "Not signed in" }
        val token = when {
            profile.local || profile.id == Profile.ACCOUNT -> owner
            profile.admin && !profile.protected -> owner
            !profile.protected && store.token(profile.id) != null -> store.token(profile.id)!!
            else -> PlexHome.switchTo(plex, owner, profile, pin)
        }
        val previous = plex.accountToken
        if (token != previous || !plex.isSignedIn) {
            plex.signOut()
            try {
                plex.connect(token)
            } catch (e: Exception) {
                previous?.let { runCatching { plex.connect(it) } }
                throw e
            }
        }
        store.setToken(profile.id, token)
        store.current = profile.id
        VerseStore.reset()
    }

    /** Checks a grown-up's PIN by asking plex.tv to switch to the account owner with it. */
    suspend fun checkOwnerPin(context: Context, plex: PlexClient, pin: String): Boolean {
        val store = ProfileStore(context)
        val owner = store.owner ?: return false
        val token = store.ownerToken ?: return false
        return runCatching { PlexHome.switchTo(plex, token, owner, pin) }.isSuccess
    }

    fun signOut(context: Context, plex: PlexClient) {
        plex.signOut()
        ProfileStore(context).signOut()
        VerseStore.reset()
    }
}
