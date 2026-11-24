package com.example.musicsm.ui.artist

import android.content.Context
import com.example.musicsm.R
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.cache.CatalogPreviewCache
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val name: String = "",
    val artworkUrl: String? = null,
    val subscribers: String? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val previews: CatalogPreviewCache,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>(Routes.ARG_ARTIST_ID).orEmpty()

    private val _state = MutableStateFlow(ArtistDetailUiState())
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    val liked: StateFlow<Boolean> = libraryRepository.isArtistLiked(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    /** Re-fetch the artist after a failure. */
    fun retry() = load()

    fun toggleLike() {
        val s = _state.value
        val name = s.name.ifBlank { displayableArtistSeed(artistId) }
        viewModelScope.launch {
            libraryRepository.toggleArtistLike(Artist(id = artistId, name = name, artworkUrl = s.artworkUrl))
        }
    }

    private fun load() {
        viewModelScope.launch {
            // Seed only human-readable artist names. Channel/browse ids are internal routing keys
            // and should never flash as visible page text while metadata is loading.
            val seed = displayableArtistSeed(artistId)
            // Whatever a card already showed for this artist paints the header straight away.
            val preview = previews.get(artistId) ?: seed.takeIf { it.isNotEmpty() }?.let(previews::artistNamed)
            _state.value = ArtistDetailUiState(
                loading = true,
                name = preview?.title.visibleCatalogText() ?: seed,
                artworkUrl = preview?.artworkUrl,
            )
            if (preview?.artworkUrl == null) {
                // Followed artists keep their photo in the library, which covers the rows built
                // from it (Home's "Your artists", Library) that never went through the catalog.
                launch {
                    val followed = runCatching { libraryRepository.likedArtists().first() }.getOrNull()
                        ?.firstOrNull { it.id == artistId || (seed.isNotEmpty() && it.name.equals(seed, ignoreCase = true)) }
                        ?: return@launch
                    _state.update { current ->
                        if (!current.loading) {
                            current
                        } else {
                            current.copy(
                                name = current.name.ifBlank { followed.name.visibleCatalogText().orEmpty() },
                                artworkUrl = current.artworkUrl ?: followed.artworkUrl,
                            )
                        }
                    }
                }
            }
            runCatching { musicRepository.artist(artistId) }
                .onSuccess { artist ->
                    val topSongs = artist.topSongs.map { it.withVisibleArtistFallback(artist.name) }
                    val name = resolvedArtistDetailName(artist.name, artistId, topSongs)
                    _state.value = ArtistDetailUiState(
                        name = name,
                        artworkUrl = artist.artworkUrl,
                        subscribers = artist.subscribers,
                        topSongs = topSongs,
                        albums = artist.albums.map { album ->
                            album.copy(artist = album.artist.visibleCatalogText() ?: name)
                        },
                        loading = false,
                    )
                }
                .onFailure {
                    _state.value = ArtistDetailUiState(loading = false, error = context.getString(R.string.artist_error))
                }
        }
    }
}


internal fun displayableArtistSeed(raw: String): String = raw.visibleCatalogText().orEmpty()

internal fun resolvedArtistDetailName(rawName: String, artistId: String, songs: List<Song>): String =
    rawName.visibleCatalogText()
        ?: songs.firstNotNullOfOrNull { it.artist.visibleCatalogText() }
        ?: displayableArtistSeed(artistId)

private fun Song.withVisibleArtistFallback(fallback: String): Song {
    val resolvedArtist = artist.visibleCatalogText()
        ?: fallback.visibleCatalogText()
        ?: ""
    return if (artist == resolvedArtist) this else copy(artist = resolvedArtist)
}

internal fun String?.visibleCatalogText(): String? {
    val trimmed = this?.trim().orEmpty()
    return trimmed.takeIf { it.isNotEmpty() && !looksLikeInternalYouTubeId(it) }
}

internal fun looksLikeInternalYouTubeId(value: String): Boolean {
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
