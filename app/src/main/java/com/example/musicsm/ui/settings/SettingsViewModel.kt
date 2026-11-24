package com.example.musicsm.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.motionart.MotionArtProvider
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.domain.repository.BackupRepository
import com.example.musicsm.domain.repository.CachedSongsRepository
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.ui.player.MotionArtStyle
import com.example.musicsm.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Everything the settings screen renders. */
data class SettingsUiState(
    val restoreQueue: Boolean = true,
    val skipSilence: Boolean = false,
    val autoplayRadio: Boolean = false,
    val sleepTimerFadeOut: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val playbackSpeed: Float = 1f,
    val crossfadeMs: Int = 0,
    val mixMode: Boolean = true,
    val appearance: AppearanceUiState = AppearanceUiState(),
    val motionArt: MotionArtUiState = MotionArtUiState(),
)

/** The motion-cover toggles, grouped for the same `combine` arity reason as the appearance block. */
data class MotionArtUiState(
    val enabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val style: MotionArtStyle = MotionArtStyle.EDGE,
    val source: MotionArtProvider = MotionArtProvider.AUTO,
)

/** The Appearance section's state, kept separate so the 5-flow `combine` limit stays workable. */
data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = true,
    val materialYou: Boolean = false,
    val themeFromArtwork: Boolean = true,
    /** Packed ARGB, or [AppPreferences.NO_ACCENT] for the palette default. */
    val accentColor: Int = AppPreferences.NO_ACCENT,
) {
    /** Material You and the accent picker both drive the accent, so only one may win. */
    val accentPickerEnabled: Boolean get() = !materialYou && !themeFromArtwork
}

/** A lyrics source as shown in Settings: its place in the order, and whether it is asked at all. */
data class LyricsSourceItem(
    val source: LyricsSource,
    val enabled: Boolean,
)

/** One-shot results of a backup/restore, surfaced to the UI as a toast. */
sealed interface BackupEvent {
    data object BackupSuccess : BackupEvent
    data object BackupFailure : BackupEvent
    data class RestoreSuccess(val songs: Int, val playlists: Int) : BackupEvent
    data object RestoreFailure : BackupEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val downloadRepository: DownloadRepository,
    private val backupRepository: BackupRepository,
    private val cachedSongsRepository: CachedSongsRepository,
) : ViewModel() {

    private val playbackToggles = combine(
        preferences.restoreQueue,
        preferences.skipSilence,
        preferences.autoplayRadio,
        preferences.sleepTimerFadeOut,
        preferences.wifiOnlyDownloads,
    ) { restore, skipSilence, autoplay, fade, wifiOnly ->
        SettingsUiState(restore, skipSilence, autoplay, fade, wifiOnly)
    }

    private val appearance = combine(
        preferences.themeMode,
        preferences.amoled,
        preferences.materialYou,
        preferences.themeFromArtwork,
        preferences.accentColor,
    ) { mode, amoled, materialYou, fromArtwork, accent ->
        AppearanceUiState(ThemeMode.fromKey(mode), amoled, materialYou, fromArtwork, accent)
    }

    private val motionArt = combine(
        preferences.animatedArtwork,
        preferences.animatedArtworkWifiOnly,
        preferences.animatedArtworkStyle,
        preferences.animatedArtworkSource,
    ) { enabled, wifiOnly, style, source ->
        MotionArtUiState(enabled, wifiOnly, MotionArtStyle.fromKey(style), MotionArtProvider.fromName(source))
    }

    // Crossfade length + Mix mode, paired so the outer combine stays within its typed arity.
    private val playbackTuning = combine(
        preferences.crossfadeMs,
        preferences.mixMode,
    ) { crossfade, mix -> crossfade to mix }

    val state: StateFlow<SettingsUiState> = combine(
        playbackToggles,
        preferences.playbackSpeed,
        playbackTuning,
        appearance,
        motionArt,
    ) { toggles, speed, tuning, look, motion ->
        toggles.copy(
            playbackSpeed = speed,
            crossfadeMs = tuning.first,
            mixMode = tuning.second,
            appearance = look,
            motionArt = motion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val downloadCount: StateFlow<Int> = downloadRepository.downloads()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    /** True while a backup/restore is running, to disable the buttons and show progress. */
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents: SharedFlow<BackupEvent> = _backupEvents.asSharedFlow()

    val recentSearchCount: StateFlow<Int> = preferences.recentSearches
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val minimizeBarOnScroll: StateFlow<Boolean> = preferences.minimizeBarOnScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.minimizeBarOnScrollNow)

    fun setMinimizeBarOnScroll(value: Boolean) = preferences.setMinimizeBarOnScroll(value)

    val searchVideos: StateFlow<Boolean> = preferences.searchVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.searchVideosNow)

    fun setSearchVideos(value: Boolean) = preferences.setSearchVideos(value)

    val loopPlaylists: StateFlow<Boolean> = preferences.loopPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.loopPlaylistsNow)

    fun setLoopPlaylists(value: Boolean) = preferences.setLoopPlaylists(value)

    val cacheSongs: StateFlow<Boolean> = preferences.cacheSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.cacheSongsNow)

    fun setCacheSongs(value: Boolean) = preferences.setCacheSongs(value)

    val cacheLimitMb: StateFlow<Int> = preferences.cacheLimitMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.cacheLimitMbNow)

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    fun refreshCache() {
        viewModelScope.launch { _cacheBytes.value = cachedSongsRepository.usedBytes() }
    }

    /** Saves the new budget and, if it shrank, evicts the oldest songs straight away. */
    fun setCacheLimitMb(value: Int) {
        preferences.setCacheLimitMb(value)
        viewModelScope.launch {
            cachedSongsRepository.trimToLimit()
            _cacheBytes.value = cachedSongsRepository.usedBytes()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cachedSongsRepository.clear()
            _cacheBytes.value = cachedSongsRepository.usedBytes()
        }
    }

    val preferWordSyncedLyrics: StateFlow<Boolean> = preferences.preferWordSyncedLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.preferWordSyncedLyricsNow)

    fun setPreferWordSyncedLyrics(value: Boolean) = preferences.setPreferWordSyncedLyrics(value)

    /** Every lyrics source, in the order they are tried. */
    val lyricsSources: StateFlow<List<LyricsSourceItem>> = combine(
        preferences.lyricsSourceOrder,
        preferences.disabledLyricsSources,
    ) { order, disabled -> order.map { LyricsSourceItem(it, it !in disabled) } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            preferences.lyricsSourceOrderNow.map {
                LyricsSourceItem(it, it !in preferences.disabledLyricsSourcesNow)
            },
        )

    fun setLyricsSourceEnabled(source: LyricsSource, enabled: Boolean) =
        preferences.setLyricsSourceEnabled(source, enabled)

    /** Moves [source] one place earlier ([delta] = -1) or later (+1) in the lyrics order. */
    fun moveLyricsSource(source: LyricsSource, delta: Int) {
        val order = preferences.lyricsSourceOrderNow.toMutableList()
        val from = order.indexOf(source)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        preferences.setLyricsSourceOrder(order)
    }

    init {
        refreshStorage()
        refreshCache()
    }

    fun refreshStorage() {
        viewModelScope.launch { _storageBytes.value = downloadRepository.storageUsedBytes() }
    }

    fun setRestoreQueue(value: Boolean) {
        preferences.setRestoreQueue(value)
        if (!value) preferences.clearQueue()
    }

    fun setSkipSilence(value: Boolean) = preferences.setSkipSilence(value)
    fun setAutoplayRadio(value: Boolean) = preferences.setAutoplayRadio(value)
    fun setSleepTimerFadeOut(value: Boolean) = preferences.setSleepTimerFadeOut(value)
    fun setWifiOnlyDownloads(value: Boolean) = preferences.setWifiOnlyDownloads(value)
    fun setPlaybackSpeed(value: Float) = preferences.setPlaybackSpeed(value)
    fun setCrossfadeMs(value: Int) = preferences.setCrossfadeMs(value)

    fun setMixMode(value: Boolean) = preferences.setMixMode(value)

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode.name)
    fun setAmoled(value: Boolean) = preferences.setAmoled(value)

    /** Material You and artwork theming are mutually exclusive accent sources. */
    fun setMaterialYou(value: Boolean) {
        preferences.setMaterialYou(value)
        if (value) preferences.setThemeFromArtwork(false)
    }

    fun setThemeFromArtwork(value: Boolean) {
        preferences.setThemeFromArtwork(value)
        if (value) preferences.setMaterialYou(false)
    }

    fun setAnimatedArtwork(value: Boolean) = preferences.setAnimatedArtwork(value)

    fun setAnimatedArtworkWifiOnly(value: Boolean) = preferences.setAnimatedArtworkWifiOnly(value)

    fun setAnimatedArtworkStyle(value: MotionArtStyle) = preferences.setAnimatedArtworkStyle(value.name)

    fun setAnimatedArtworkSource(value: MotionArtProvider) =
        preferences.setAnimatedArtworkSource(value.name)

    /** Choosing an explicit accent turns off the automatic sources so the pick actually sticks. */
    fun setAccentColor(argb: Int) {
        preferences.setAccentColor(argb)
        if (argb != AppPreferences.NO_ACCENT) {
            preferences.setMaterialYou(false)
            preferences.setThemeFromArtwork(false)
        }
    }

    fun clearSearchHistory() = preferences.clearRecentSearches()

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAll()
            refreshStorage()
        }
    }

    /** Exports the whole library + settings to the user-chosen [uri]. */
    fun backupTo(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            val event = runCatching {
                val json = backupRepository.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: error("Could not open the destination file")
                }
                BackupEvent.BackupSuccess
            }.getOrElse { BackupEvent.BackupFailure }
            _backupBusy.value = false
            _backupEvents.emit(event)
        }
    }

    /** Reads a backup from [uri] and merges it into the current library. */
    fun restoreFrom(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            val event = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open the backup file")
                }
                val summary = backupRepository.import(text)
                BackupEvent.RestoreSuccess(summary.songs, summary.playlists)
            }.getOrElse { BackupEvent.RestoreFailure }
            _backupBusy.value = false
            _backupEvents.emit(event)
            refreshStorage()
        }
    }
}
