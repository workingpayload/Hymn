package com.example.musicsm.ui.album

import android.content.Context
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val year: String? = null,
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** A YouTube (Music) playlist shown on this screen rather than an album; it loops when played. */
    val isPlaylist: Boolean = false,
)

/**
 * Whether an id on the album route is a playlist. Albums arrive as `MPREb_…` browse ids or their
 * `OLAK5uy_…` album playlists; anything else (`VL…`, `PL…`, `RD…`, `LM`) is a real playlist.
 */
internal fun isRemotePlaylistId(id: String): Boolean {
    val text = id.trim()
    return text.isNotEmpty() && !text.contains("MPREb") && !text.contains("OLAK5")
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>(Routes.ARG_ALBUM_ID).orEmpty()

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state

    /** True when every album track is in Liked Songs. */
    val favorited: StateFlow<Boolean> =
        combine(_state, libraryRepository.likedSongs()) { st, liked ->
            val likedIds = liked.mapTo(HashSet()) { it.id }
            st.songs.isNotEmpty() && st.songs.all { it.id in likedIds }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when a playlist named after this album already exists. */
    val added: StateFlow<Boolean> =
        combine(_state, libraryRepository.playlists()) { st, playlists ->
            st.title.isNotBlank() && playlists.any { it.name == st.title }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    /** Re-fetch the album after a failure. */
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = AlbumDetailUiState(loading = true)
            runCatching { musicRepository.album(albumId) }
                .onSuccess { album ->
                    val title = album.title.visibleAlbumText().orEmpty()
                    val songs = album.songs.map { it.withAlbumDetailFallback(title, album.artist) }
                    _state.value = AlbumDetailUiState(
                        title = title,
                        artist = resolvedAlbumDetailArtist(album.artist, songs, title),
                        artworkUrl = album.artworkUrl,
                        year = album.year,
                        songs = songs,
                        loading = false,
                        isPlaylist = isRemotePlaylistId(albumId),
                    )
                }
                .onFailure {
                    _state.value = AlbumDetailUiState(loading = false, error = context.getString(R.string.album_error))
                }
        }
    }

    /** Like all album tracks, or unlike all if already favorited. */
    fun toggleFavorite() {
        viewModelScope.launch {
            val songs = _state.value.songs
            if (songs.isEmpty()) return@launch
            val likedIds = libraryRepository.likedSongs().first().mapTo(HashSet()) { it.id }
            val allLiked = songs.all { it.id in likedIds }
            songs.forEach { song ->
                val isLiked = song.id in likedIds
                // toggleLike flips state — only call when it moves toward the target.
                if (allLiked && isLiked) libraryRepository.toggleLike(song)
                else if (!allLiked && !isLiked) libraryRepository.toggleLike(song)
            }
        }
    }

    /** Save the album as a playlist, or delete that playlist if already added. */
    fun toggleAdd() {
        viewModelScope.launch {
            val st = _state.value
            if (st.title.isBlank() || st.songs.isEmpty()) return@launch
            val existing = libraryRepository.playlists().first().firstOrNull { it.name == st.title }
            if (existing != null) {
                existing.id.toLongOrNull()?.let { libraryRepository.deletePlaylist(it) }
            } else {
                val id = libraryRepository.createPlaylist(st.title)
                st.songs.forEach { libraryRepository.addToPlaylist(id, it) }
            }
        }
    }
}


internal fun resolvedAlbumDetailArtist(rawArtist: String, songs: List<Song>, title: String): String =
    rawArtist.visibleAlbumText()
        ?: songs.firstNotNullOfOrNull { it.artist.visibleAlbumText() }
        ?: title.visibleAlbumText()
        ?: ""

internal fun String?.visibleAlbumText(): String? {
    val trimmed = this?.trim().orEmpty()
    return trimmed.takeIf { it.isNotEmpty() && !looksLikeInternalAlbumId(it) }
}

private fun Song.withAlbumDetailFallback(title: String, albumArtist: String): Song {
    val resolvedAlbum = album.visibleAlbumText() ?: title.visibleAlbumText()
    val resolvedArtist = artist.visibleAlbumText()
        ?: albumArtist.visibleAlbumText()
        ?: title.visibleAlbumText()
        ?: ""
    return if (artist == resolvedArtist && album == resolvedAlbum) {
        this
    } else {
        copy(artist = resolvedArtist, album = resolvedAlbum)
    }
}

internal fun looksLikeInternalAlbumId(value: String): Boolean {
    val text = value.trim()
    if (text.contains("://") || text.contains("%2F", ignoreCase = true) || text.contains("%3A", ignoreCase = true)) {
        return true
    }
    if (!YOUTUBE_ID_TOKEN.matches(text)) return false
    return when {
        text.startsWith("UC") && text.length >= 12 -> true
        text.startsWith("MPREb") && text.length >= 8 -> true
        text.startsWith("VL") && text.length >= 12 -> true
        text.startsWith("OLAK5") && text.length >= 12 -> true
        text.startsWith("PL") && text.length >= 12 -> true
        text.startsWith("RD") && text.length >= 12 -> true
        text.startsWith("UU") && text.length >= 12 -> true
        text.startsWith("LM") && text.length >= 12 -> true
        text.length == 11 && text.any { it.isDigit() } && text.any { it == '-' || it == '_' || it.isUpperCase() } -> true
        else -> false
    }
}

private val YOUTUBE_ID_TOKEN = Regex("^[A-Za-z0-9_-]+$")
