package hu.molnarroland.plexverse.tv

import android.net.Uri

/**
 * What a card on the TV's home screen opens, as a plexverse:// link: playing a queue entry (copies on
 * several servers, see [hu.molnarroland.plexverse.data.queueEntry]), or a show's page by one copy's ref.
 */
sealed interface HomeLink {
    data class Play(val queue: String, val resume: Boolean) : HomeLink
    data class Show(val ref: String) : HomeLink

    fun uri(): Uri = when (this) {
        is Play -> Uri.Builder().scheme(SCHEME).authority("play").appendQueryParameter("q", queue).appendQueryParameter("resume", if (resume) "1" else "0").build()
        is Show -> Uri.Builder().scheme(SCHEME).authority("show").appendQueryParameter("ref", ref).build()
    }

    companion object {
        const val SCHEME = "plexverse"

        fun parse(uri: Uri?): HomeLink? {
            if (uri?.scheme != SCHEME) return null
            return when (uri.host) {
                "play" -> uri.getQueryParameter("q")?.ifEmpty { null }?.let { Play(it, uri.getQueryParameter("resume") == "1") }
                "show" -> uri.getQueryParameter("ref")?.ifEmpty { null }?.let { Show(it) }
                else -> null
            }
        }
    }
}
