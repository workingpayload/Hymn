package com.example.musicsm.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicsm.data.auth.YouTubePersonalization
import com.example.musicsm.domain.model.AccountLibrary
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    downloadRepository: DownloadRepository,
    musicRepository: MusicRepository,
    personalization: YouTubePersonalization,
) : ViewModel() {

    /**
     * The signed-in listener's YouTube Music playlists and artists, while personalisation is on.
     * Read again each time the Library comes back into view, so changes made elsewhere show up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val accountLibrary: StateFlow<AccountLibrary> = personalization.enabled
        .flatMapLatest { enabled ->
            if (enabled) {
                flow { emit(runCatching { musicRepository.accountLibrary() }.getOrDefault(AccountLibrary())) }
            } else {
                flowOf(AccountLibrary())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountLibrary())

    val downloads: StateFlow<List<Song>> = downloadRepository.downloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs: StateFlow<List<Song>> = repository.likedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = repository.recentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val followedArtists: StateFlow<List<Artist>> = repository.likedArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { repository.addToPlaylist(playlistId, song) }
    }

    fun createPlaylistWithSong(name: String, song: Song) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name.trim())
            repository.addToPlaylist(id, song)
        }
    }

    /** New playlist holding [songs] in order; repeats (e.g. a song queued twice) are kept once. */
    fun createPlaylistWithSongs(name: String, songs: List<Song>) {
        if (name.isBlank() || songs.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name.trim())
            songs.distinctBy { it.id }.forEach { repository.addToPlaylist(id, it) }
        }
    }

    fun isLiked(songId: String): Flow<Boolean> = repository.isLiked(songId)

    fun toggleLike(song: Song) {
        viewModelScope.launch { repository.toggleLike(song) }
    }
}
