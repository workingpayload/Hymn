package com.example.musicsm.navigation

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.StringRes
import com.example.musicsm.R

/**
 * Something the app was asked to do from outside: a launcher shortcut, a `musicsm://` deep link,
 * a YouTube link opened or shared from another app, the widget/tile, or a voice command.
 *
 * Parsing lives here (pure, testable); [MusicSmRoot] decides how to act on it.
 */
sealed interface AppIntent {
    /** Resume whatever was last playing. */
    data object Resume : AppIntent

    /** Jump to one of the bottom-nav tabs. */
    data class OpenTab(val route: String) : AppIntent

    /** Open Search, pre-filled with [query]; [playFirst] auto-plays the top hit (voice search). */
    data class Search(val query: String, val playFirst: Boolean = false) : AppIntent

    data class PlaySong(val songId: String) : AppIntent
    data class OpenAlbum(val albumId: String) : AppIntent
    data class OpenArtist(val artistId: String) : AppIntent
    data class OpenPlaylist(val playlistId: Long) : AppIntent
    data object OpenLiked : AppIntent
    data object OpenDownloads : AppIntent

    /** A `musicsm://shared/playlist?d=…` link — the whole track list travels in [payload]. */
    data class ImportSharedPlaylist(val payload: String) : AppIntent

    /** The link was understood as "ours" but couldn't be used; surface [messageRes] to the user. */
    data class Unsupported(@param:StringRes val messageRes: Int) : AppIntent
}

/** Custom scheme for in-app links (`musicsm://song/<id>`). */
const val APP_SCHEME = "musicsm"

/** Translate a launcher/share/voice [Intent] into an [AppIntent], or null if it's just a cold start. */
fun Intent?.toAppIntent(): AppIntent? {
    val intent = this ?: return null
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.let(::appIntentFromUri)

        Intent.ACTION_SEND -> {
            if (intent.type?.startsWith("text/") != true) return null
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = FIRST_URL.find(shared)?.value?.trimEnd('.', ',', ')')
            when {
                url != null -> appIntentFromUri(url.toUri())
                    ?: AppIntent.Unsupported(R.string.deeplink_not_a_track)
                // Plain text with no link: treat it as something to search for.
                shared.isNotBlank() -> AppIntent.Search(shared.trim().take(120))
                else -> null
            }
        }

        // "Play <x> on Hymn" from Assistant / a car head unit.
        MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().trim()
            if (query.isBlank()) AppIntent.Resume else AppIntent.Search(query, playFirst = true)
        }

        else -> null
    }
}

/**
 * Parse a link that reached the app outside an [Intent] — currently a scanned QR code.
 *
 * Accepts a bare link or a link embedded in surrounding text, matching what [toAppIntent] does for
 * shared text, so a code carrying "Listen to X: musicsm://…" still works.
 */
fun appIntentFromLink(raw: String): AppIntent? {
    val url = FIRST_URL.find(raw.trim())?.value?.trimEnd('.', ',', ')') ?: return null
    return appIntentFromUri(url.toUri())
}

private fun appIntentFromUri(uri: Uri): AppIntent? = when (uri.scheme?.lowercase()) {
    APP_SCHEME -> fromAppUri(uri)
    "http", "https" -> fromYouTubeUri(uri)
    else -> null
}

/** `musicsm://<host>/<arg>` links, also used by the shortcuts, widget and tile. */
private fun fromAppUri(uri: Uri): AppIntent? {
    val arg = uri.pathSegments.firstOrNull()?.let(Uri::decode)
    return when (uri.host?.lowercase()) {
        "resume" -> AppIntent.Resume
        "home" -> AppIntent.OpenTab(Routes.HOME)
        "library" -> AppIntent.OpenTab(Routes.LIBRARY)
        "search" -> AppIntent.Search(uri.getQueryParameter("q").orEmpty())
        "liked" -> AppIntent.OpenLiked
        "downloads" -> AppIntent.OpenDownloads
        "song" -> arg?.let(AppIntent::PlaySong)
        "album" -> arg?.let(AppIntent::OpenAlbum)
        "artist" -> arg?.let(AppIntent::OpenArtist)
        "playlist" -> arg?.toLongOrNull()?.let(AppIntent::OpenPlaylist)
        // musicsm://shared/playlist?d=<payload> — produced by the QR / link share sheet.
        "shared" -> uri.getQueryParameter("d")
            ?.takeIf { it.isNotBlank() }
            ?.let(AppIntent::ImportSharedPlaylist)
            ?: AppIntent.Unsupported(R.string.shared_playlist_bad_link)
        else -> null
    }
}

/**
 * YouTube / YouTube Music links, whether opened directly or shared as text. A watch link with
 * both a video and a list prefers the video, since that's what the user tapped.
 */
private fun fromYouTubeUri(uri: Uri): AppIntent? {
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    if (host != "youtu.be" && host != "youtube.com" && host != "music.youtube.com") return null

    youTubeVideoId(host, uri)?.let { return AppIntent.PlaySong(it) }
    youTubePlaylistId(uri)?.let { return AppIntent.OpenAlbum(PLAYLIST_URL + it) }
    return AppIntent.Unsupported(R.string.deeplink_unsupported_youtube_link)
}

private fun youTubeVideoId(host: String, uri: Uri): String? {
    val raw = when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        else -> uri.getQueryParameter("v")
            ?: uri.pathSegments.takeIf { it.size >= 2 && it[0].lowercase() in VIDEO_PATHS }?.get(1)
    }
    return raw?.takeIf { it.isNotBlank() && it.all(::isIdChar) }
}

private fun youTubePlaylistId(uri: Uri): String? =
    uri.getQueryParameter("list")?.takeIf { it.isNotBlank() && it.all(::isIdChar) }

private fun isIdChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_'

private fun String.toUri(): Uri = Uri.parse(this)

private const val PLAYLIST_URL = "https://www.youtube.com/playlist?list="
private val VIDEO_PATHS = setOf("shorts", "embed", "live", "v")

/** Also matches our own scheme so a shared playlist link pasted into "Share to Hymn" works. */
private val FIRST_URL = Regex("""(?:https?|musicsm)://\S+""")
