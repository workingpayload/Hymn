package com.example.musicsm.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.motionart.MotionArt
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.LyricsSyncResult
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsm.domain.repository.LyricsSyncRepository
import com.example.musicsm.domain.repository.MotionArtRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.playback.MediaControllerManager
import com.example.musicsm.playback.MixBlend
import com.example.musicsm.playback.MixTransitionBus
import com.example.musicsm.playback.PlayerState
import com.example.musicsm.playback.SleepTimerManager
import com.example.musicsm.playback.SleepTimerState
import com.example.musicsm.data.prefs.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** State of the lyrics fetch for the current track. */
sealed interface LyricsState {
    data object Empty : LyricsState
    data object Loading : LyricsState
    data object None : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
}

/** Lining the current track's lyrics up with its album audio (see [PlayerViewModel.syncLyricsNow]). */
sealed interface LyricsSyncState {
    data object Idle : LyricsSyncState
    data object Syncing : LyricsSyncState

    /** Measured and applied: the lyrics now run [offsetMs] later. */
    data class Synced(val offsetMs: Long) : LyricsSyncState

    /** Measured: the track already runs with the album audio. */
    data object InTime : LyricsSyncState
    data object AlreadyAlbumAudio : LyricsSyncState
    data object NoAlbumAudio : LyricsSyncState
    data object NoMatch : LyricsSyncState
    data object Failed : LyricsSyncState

    companion object {
        /** Differences smaller than this can't be heard against lyrics; treat them as none. */
        const val IN_TIME_MS = 150L

        /** A measured offset as applied: whole 10 ms, and 0 when within [IN_TIME_MS]. */
        fun roundOffset(offsetMs: Long): Long =
            if (kotlin.math.abs(offsetMs) < IN_TIME_MS) 0L else offsetMs / 10 * 10
    }
}

/**
 * Shared player VM (obtained once at the nav root). Exposes [state] and forwards transport
 * commands to [MediaControllerManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: MediaControllerManager,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val lyricsSyncRepository: LyricsSyncRepository,
    private val libraryRepository: LibraryRepository,
    private val motionArtRepository: MotionArtRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val preferences: AppPreferences,
    mixTransitions: MixTransitionBus,
) : ViewModel() {

    val state: StateFlow<PlayerState> = controller.state

    /** The crossfade/Mix blend lined up into the next track, for the artwork to follow. */
    val mixBlend: StateFlow<MixBlend?> = mixTransitions.blend

    /** Fine-grained playback position (ms) for the scrubber, synced lyrics and ambient progress. */
    val position: StateFlow<Long> = controller.position

    /** Countdown state of the sleep timer, or an inactive snapshot. */
    val sleepTimer: StateFlow<SleepTimerState> = sleepTimerManager.state

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Empty)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    /** Progress and outcome of lining the current track's lyrics up with its album audio. */
    private val _lyricsSync = MutableStateFlow<LyricsSyncState>(LyricsSyncState.Idle)
    val lyricsSync: StateFlow<LyricsSyncState> = _lyricsSync.asStateFlow()
    private var lyricsSyncJob: Job? = null

    /** Tracks already lined up automatically this session, so it runs once per track. */
    private val autoSyncTried = HashSet<String>()

    /**
     * Looping cover video for the current track, or null when it has none.
     *
     * Cleared the instant the track changes rather than when the next lookup resolves, so the old
     * track's loop can never be left playing under the new track's title.
     */
    private val _motionArt = MutableStateFlow<MotionArt?>(null)
    val motionArt: StateFlow<MotionArt?> = _motionArt.asStateFlow()

    /** Where a resolved cover loop plays: the card, the top edge, or the whole player. */
    val motionArtStyle: StateFlow<MotionArtStyle> = preferences.animatedArtworkStyle
        .map(MotionArtStyle::fromKey)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MotionArtStyle.fromKey(preferences.animatedArtworkStyleNow),
        )

    /** Ambient mode shows a playing cassette tape instead of the cover art. */
    val ambientCassette: StateFlow<Boolean> = preferences.ambientCassette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.ambientCassetteNow)

    fun setAmbientCassette(enabled: Boolean) = preferences.setAmbientCassette(enabled)

    /** Per-track lyric timing correction, applied immediately to synced lyric highlighting. */
    val lyricsOffsetMs: StateFlow<Long> = controller.state
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            if (songId == null) flowOf(0L) else preferences.lyricsOffset(songId)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            controller.state.value.currentSong?.id?.let(preferences::lyricsOffsetNow) ?: 0L,
        )

    /** Guards against re-triggering autoplay for the same track. */
    private var autoplayedAfter: String? = null

    /**
     * Whether the current queue is a radio, and therefore ours to keep extending.
     *
     * Topping up an album or a playlist the listener deliberately queued would be an unwanted
     * edit to their choice, so only queues this class started as a radio grow on their own.
     */
    private val radioActive = MutableStateFlow(false)

    /** Stops two top-ups racing when several player events land close together. */
    private var extending = false

    init {
        // Fetch lyrics whenever the current track changes.
        viewModelScope.launch {
            controller.state
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { song ->
                    lyricsSyncJob?.cancel()
                    _lyricsSync.value = LyricsSyncState.Idle
                    if (song == null) {
                        _lyrics.value = LyricsState.Empty
                        return@collectLatest
                    }
                    _lyrics.value = LyricsState.Loading
                    // The real length of the stream being played is what picks the matching
                    // recording, and so the right timings. It only exists once the player has
                    // prepared the item; the catalog's own duration is a fallback.
                    val durationMs = withTimeoutOrNull(DURATION_WAIT_MS) {
                        controller.state
                            .first { it.currentSong?.id == song.id && it.durationMs > 0 }
                            .durationMs
                    } ?: song.durationMs
                    val result = runCatching {
                        lyricsRepository.forSong(song.copy(durationMs = durationMs))
                    }.getOrNull()
                    _lyrics.value = if (result == null) LyricsState.None else LyricsState.Loaded(result)
                }
        }
        // Record play history when the current track changes (capped in the repository).
        viewModelScope.launch {
            controller.state
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { song ->
                    if (song != null) runCatching { libraryRepository.recordPlay(song) }
                }
        }
        // Look up the track's motion cover, if the release has one.
        viewModelScope.launch {
            controller.state
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .combine(motionArtSettings()) { song, enabled -> song.takeIf { enabled } }
                .collectLatest { song ->
                    _motionArt.value = null
                    if (song == null) return@collectLatest
                    _motionArt.value = runCatching { motionArtRepository.forSong(song) }.getOrNull()
                }
        }
        // Endless playback: when the queue runs dry, extend it with related tracks.
        viewModelScope.launch {
            controller.state
                .map { Pair(it.isEnded && !it.hasNext, it.currentSong) }
                .distinctUntilChanged()
                .collectLatest { (ended, song) ->
                    if (!ended || song == null) return@collectLatest
                    if (!preferences.autoplayRadioNow) return@collectLatest
                    if (autoplayedAfter == song.id) return@collectLatest
                    autoplayedAfter = song.id
                    val related = runCatching { musicRepository.relatedTo(song.id) }
                        .getOrDefault(emptyList())
                        .filterNot { it.id == song.id }
                    if (related.isEmpty()) return@collectLatest
                    related.forEach { controller.addToQueue(it) }
                    controller.next()
                }
        }

        // Keep a running radio topped up. Waiting for the queue to actually run out means the
        // listener hears a gap while the next batch is fetched; refilling once the end is in
        // sight keeps it seamless.
        viewModelScope.launch {
            controller.state
                .map { Triple(it.queue.size, it.currentIndex, it.currentSong?.id) }
                .distinctUntilChanged()
                .collectLatest { (size, index, _) ->
                    if (!radioActive.value || extending || size == 0) return@collectLatest
                    if (size - index > RADIO_LOW_WATER) return@collectLatest
                    if (size >= RADIO_MAX_QUEUE) return@collectLatest
                    extendRadio()
                }
        }
    }

    /**
     * Emits whether motion covers are on, re-emitting whenever the chosen catalog changes.
     *
     * The catalog is folded in so re-pinning it re-resolves the track already on screen, rather
     * than leaving the previous catalog's loop up until the listener happens to skip.
     */
    private fun motionArtSettings() =
        combine(preferences.animatedArtwork, preferences.animatedArtworkSource) { enabled, _ -> enabled }

    /**
     * Grow the running radio from its most recent track.
     *
     * The tail is used as the seed rather than the original one, so a long listen keeps moving
     * instead of being pulled back to where it started every time it refills.
     */
    private suspend fun extendRadio() {
        val snapshot = state.value
        val seed = snapshot.queue.lastOrNull() ?: return
        extending = true
        try {
            val more = runCatching {
                musicRepository.radio(
                    seed = seed,
                    limit = RADIO_TOP_UP,
                    exclude = snapshot.queue.mapTo(HashSet()) { it.id },
                )
            }.getOrDefault(emptyList())
            more.forEach { controller.addToQueue(it) }
        } finally {
            extending = false
        }
    }

    /** Play a list of songs starting at [startIndex] (e.g. an album, playlist, or search list). */
    fun play(songs: List<Song>, startIndex: Int = 0) {
        radioActive.value = false
        controller.playSongs(songs, startIndex)
    }

    /**
     * Play a single song and build a radio around it.
     *
     * Playback starts on the seed immediately and the rest of the queue arrives behind it, so the
     * wait to hear something is one stream resolution rather than a whole recommendation fan-out.
     */
    fun playWithRadio(song: Song) {
        controller.playSongs(listOf(song))
        radioActive.value = true
        viewModelScope.launch {
            runCatching { musicRepository.radio(song, RADIO_SIZE) }
                .getOrDefault(emptyList())
                .forEach { controller.addToQueue(it) }
        }
    }

    /**
     * Play a track id that arrived from a deep link, share or widget. Returns false when the id
     * couldn't be resolved (bad link / offline).
     */
    suspend fun playSongId(songId: String): Boolean {
        val song = runCatching { musicRepository.song(songId) }.getOrNull() ?: return false
        playWithRadio(song)
        return true
    }

    /** Voice search ("play X on Hymn"): play the best match for [query]. */
    suspend fun searchAndPlay(query: String): Boolean {
        val song = runCatching { musicRepository.search(query).songs.firstOrNull() }.getOrNull()
            ?: return false
        playWithRadio(song)
        return true
    }

    /** Resume the restored/paused queue. Returns false if there is nothing to resume. */
    suspend fun resumePlayback(): Boolean {
        val ready = withTimeoutOrNull(RESUME_TIMEOUT_MS) {
            state.first { it.isConnected && it.currentSong != null }
        } ?: return false
        if (!ready.isPlaying) controller.togglePlayPause()
        return true
    }

    /** Load an album by id and play its tracks. */
    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            runCatching { musicRepository.album(albumId).songs }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { play(it) }
        }
    }

    /** Load an artist by id and play their top tracks. */
    fun playArtist(artistId: String) {
        viewModelScope.launch {
            runCatching { musicRepository.artist(artistId).topSongs }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { play(it) }
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekToFraction(fraction: Float) {
        val duration = state.value.durationMs
        if (duration > 0) controller.seekTo((fraction * duration).toLong())
    }
    fun seekToMs(ms: Long) = controller.seekTo(ms)

    fun adjustLyricsOffset(deltaMs: Long) {
        state.value.currentSong?.id?.let { preferences.adjustLyricsOffset(it, deltaMs) }
    }

    fun setLyricsOffset(offsetMs: Long) {
        state.value.currentSong?.id?.let { preferences.setLyricsOffset(it, offsetMs) }
    }

    fun resetLyricsOffset() = setLyricsOffset(0L)

    fun syncLyricLineToNow(lineTimeMs: Long, positionMs: Long) {
        setLyricsOffset(positionMs - lineTimeMs)
    }

    /**
     * Lyrics seen on screen may be off: when they're timed lyrics from another cut of the song and
     * the listener hasn't set an offset, line them up with the album audio once per track.
     */
    fun autoSyncLyrics() {
        val song = state.value.currentSong ?: return
        val lyrics = (_lyrics.value as? LyricsState.Loaded)?.lyrics ?: return
        if (!lyrics.synced || lyrics.timingVerified) return
        if (preferences.lyricsOffsetNow(song.id) != 0L) return
        if (!autoSyncTried.add(song.id)) return
        startLyricsSync(song)
    }

    /** "Sync now": measures the playing track against its album audio and applies the result. */
    fun syncLyricsNow() {
        val song = state.value.currentSong ?: return
        if ((_lyrics.value as? LyricsState.Loaded)?.lyrics?.synced != true) return
        autoSyncTried.add(song.id)
        startLyricsSync(song)
    }

    private fun startLyricsSync(song: Song) {
        if (lyricsSyncJob?.isActive == true) return
        _lyricsSync.value = LyricsSyncState.Syncing
        lyricsSyncJob = viewModelScope.launch {
            val result = withTimeoutOrNull(LYRICS_SYNC_TIMEOUT_MS) {
                lyricsSyncRepository.measure(song, position.value, state.value.durationMs)
            } ?: LyricsSyncResult.Failed
            // The track may have changed while measuring; the result belongs to the old one.
            if (state.value.currentSong?.id != song.id) return@launch
            _lyricsSync.value = when (result) {
                is LyricsSyncResult.Measured -> {
                    val offset = LyricsSyncState.roundOffset(result.offsetMs)
                    preferences.setLyricsOffset(song.id, offset)
                    if (offset == 0L) LyricsSyncState.InTime else LyricsSyncState.Synced(offset)
                }
                LyricsSyncResult.AlreadyAlbumAudio -> LyricsSyncState.AlreadyAlbumAudio
                LyricsSyncResult.NoAlbumAudio -> LyricsSyncState.NoAlbumAudio
                LyricsSyncResult.NoMatch -> LyricsSyncState.NoMatch
                LyricsSyncResult.Failed -> LyricsSyncState.Failed
            }
        }
    }

    fun seekToIndex(index: Int) = controller.seekToIndex(index)
    fun setVolume(volume: Float) = controller.setVolume(volume)
    fun setSystemVolume(fraction: Float) = controller.setSystemVolume(fraction)
    fun toggleShuffle() = controller.toggleShuffle()

    /** Shuffle-play a list. Turns shuffle *on* (never off) and starts on a random track. */
    fun shufflePlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        radioActive.value = false
        controller.setShuffle(true)
        controller.playSongs(songs, songs.indices.random())
    }
    fun setShuffle(enabled: Boolean) = controller.setShuffle(enabled)
    fun cycleRepeat() = controller.cycleRepeat()
    fun addToQueue(song: Song) = controller.addToQueue(song)
    fun playNext(song: Song) = controller.playNext(song)
    fun moveQueueItem(from: Int, to: Int) = controller.moveItem(from, to)
    fun removeQueueItem(index: Int) = controller.removeItem(index)

    // --- sleep timer -------------------------------------------------------

    fun startSleepTimer(minutes: Int) = sleepTimerManager.start(minutes)
    fun startSleepTimerAtEndOfTrack() = sleepTimerManager.startAtEndOfTrack()
    fun cancelSleepTimer() = sleepTimerManager.cancel()

    private companion object {
        const val RESUME_TIMEOUT_MS = 5_000L

        /** How long lyrics wait for the player to learn the track's real length. */
        const val DURATION_WAIT_MS = 8_000L

        /** Finding the album audio and decoding both; slower than this and it gives up. */
        const val LYRICS_SYNC_TIMEOUT_MS = 60_000L

        /** Tracks queued when a radio starts — enough for a few hours without another request. */
        const val RADIO_SIZE = 80

        /** Added each time a running radio is topped up. */
        const val RADIO_TOP_UP = 40

        /** Refill once this few tracks remain, so the fetch finishes before they run out. */
        const val RADIO_LOW_WATER = 10

        /** A ceiling so an all-day session cannot grow the queue without bound. */
        const val RADIO_MAX_QUEUE = 500
    }
}
