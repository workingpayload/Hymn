package com.example.musicsm.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.musicsm.ui.components.ScreenBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.motionart.MotionArtProvider
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsm.ui.player.MotionArtStyle
import com.example.musicsm.data.prefs.AppPreferences
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.currentLocale
import com.example.musicsm.ui.update.UpdateDialog
import com.example.musicsm.ui.update.UpdateState
import com.example.musicsm.ui.update.UpdateViewModel
import com.example.musicsm.ui.account.GoogleSignInDialog
import com.example.musicsm.ui.account.YouTubeAccountViewModel
import com.example.musicsm.ui.theme.AccentPresets
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.ThemeMode
import com.example.musicsm.ui.theme.isDarkEnoughForWhiteText
import com.example.musicsm.ui.theme.SurfaceLow
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    accountViewModel: YouTubeAccountViewModel = hiltViewModel(),
) {
    ScreenBackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val youTubeSignedIn by accountViewModel.signedIn.collectAsStateWithLifecycle()
    val personalizeYouTube by accountViewModel.personalize.collectAsStateWithLifecycle()
    val showYouTubeSignIn by accountViewModel.showSignIn.collectAsStateWithLifecycle()
    val signedInMessage = stringResource(R.string.youtube_signed_in)
    val signedOutMessage = stringResource(R.string.youtube_signed_out)
    val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val recentSearchCount by viewModel.recentSearchCount.collectAsStateWithLifecycle()
    val minimizeBarOnScroll by viewModel.minimizeBarOnScroll.collectAsStateWithLifecycle()
    val searchVideos by viewModel.searchVideos.collectAsStateWithLifecycle()
    val cacheSongs by viewModel.cacheSongs.collectAsStateWithLifecycle()
    val cacheLimitMb by viewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    var confirmClearCache by remember { mutableStateOf(false) }
    val preferWordSyncedLyrics by viewModel.preferWordSyncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by viewModel.lyricsSources.collectAsStateWithLifecycle()
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "\u2013"
    }

    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    // System file pickers: create a .json to write the backup, or open one to restore from.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backupTo) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreFrom) }

    // Report the outcome of a backup/restore as a toast.
    LaunchedEffect(Unit) {
        viewModel.backupEvents.collect { event ->
            val message = when (event) {
                BackupEvent.BackupSuccess -> context.getString(R.string.backup_success)
                BackupEvent.BackupFailure -> context.getString(R.string.backup_failed)
                is BackupEvent.RestoreSuccess ->
                    context.getString(R.string.restore_success, event.songs, event.playlists)
                BackupEvent.RestoreFailure -> context.getString(R.string.restore_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Toast the result of a manual update check; the "available" case shows a dialog instead.
    LaunchedEffect(updateState) {
        when (updateState) {
            UpdateState.UpToDate -> {
                Toast.makeText(context, context.getString(R.string.update_up_to_date), Toast.LENGTH_SHORT).show()
                updateViewModel.clear()
            }
            UpdateState.Failed -> {
                Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                updateViewModel.clear()
            }
            else -> Unit
        }
    }

    Column(modifier = modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(OverlayTint.copy(alpha = 0.05f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.action_back), tint = OnDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnDark)
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + LocalBottomBarPadding.current)) {
            item { BuyMeACoffeeCard(onClick = { showSupport = true }) }

            item { SettingsSection(stringResource(R.string.settings_section_playback)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_resume_title),
                    subtitle = stringResource(R.string.settings_resume_subtitle),
                    checked = ui.restoreQueue,
                    onCheckedChange = viewModel::setRestoreQueue,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_autoplay_title),
                    subtitle = stringResource(R.string.settings_autoplay_subtitle),
                    checked = ui.autoplayRadio,
                    onCheckedChange = viewModel::setAutoplayRadio,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_skip_silence_title),
                    subtitle = stringResource(R.string.settings_skip_silence_subtitle),
                    checked = ui.skipSilence,
                    onCheckedChange = viewModel::setSkipSilence,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_fade_out_title),
                    subtitle = stringResource(R.string.settings_fade_out_subtitle),
                    checked = ui.sleepTimerFadeOut,
                    onCheckedChange = viewModel::setSleepTimerFadeOut,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_audio)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_equalizer_title),
                    subtitle = stringResource(R.string.settings_equalizer_subtitle),
                    onClick = onOpenEqualizer,
                )
            }
            item {
                val locale = currentLocale()
                SettingsSlider(
                    title = stringResource(R.string.settings_speed_title),
                    subtitle = stringResource(
                        R.string.settings_speed_subtitle,
                        String.format(locale, "%.2f", ui.playbackSpeed).trimEnd('0').trimEnd('.'),
                    ),
                    value = ui.playbackSpeed,
                    valueRange = AppPreferences.MIN_SPEED..AppPreferences.MAX_SPEED,
                    // 0.05x increments across the 0.5x - 2.0x range.
                    steps = 29,
                    onValueChange = viewModel::setPlaybackSpeed,
                )
            }
            item {
                val seconds = ui.crossfadeMs / 1000f
                val locale = currentLocale()
                SettingsSlider(
                    title = stringResource(R.string.settings_crossfade_title),
                    subtitle = if (ui.crossfadeMs == 0) {
                        stringResource(R.string.settings_crossfade_off)
                    } else {
                        stringResource(
                            R.string.settings_crossfade_on,
                            String.format(locale, "%.1f", seconds).trimEnd('0').trimEnd('.'),
                        )
                    },
                    value = seconds,
                    valueRange = 0f..(AppPreferences.MAX_CROSSFADE_MS / 1000f),
                    // Half-second increments from 0 to 12s.
                    steps = 23,
                    onValueChange = { viewModel.setCrossfadeMs((it * 1000).roundToInt()) },
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_mix_title),
                    subtitle = stringResource(R.string.settings_mix_subtitle),
                    checked = ui.mixMode,
                    onCheckedChange = viewModel::setMixMode,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_appearance)) }
            item {
                ThemeModePicker(
                    selected = ui.appearance.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_amoled_title),
                    subtitle = stringResource(R.string.settings_amoled_subtitle),
                    checked = ui.appearance.amoled,
                    enabled = ui.appearance.themeMode != ThemeMode.LIGHT,
                    onCheckedChange = viewModel::setAmoled,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_material_you_title),
                        subtitle = stringResource(R.string.settings_material_you_subtitle),
                        checked = ui.appearance.materialYou,
                        onCheckedChange = viewModel::setMaterialYou,
                    )
                }
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_artwork_theme_title),
                    subtitle = stringResource(R.string.settings_artwork_theme_subtitle),
                    checked = ui.appearance.themeFromArtwork,
                    onCheckedChange = viewModel::setThemeFromArtwork,
                )
            }
            item {
                AccentPicker(
                    selected = ui.appearance.accentColor,
                    enabled = ui.appearance.accentPickerEnabled,
                    onSelect = viewModel::setAccentColor,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_minimize_bar_title),
                    subtitle = stringResource(R.string.settings_minimize_bar_subtitle),
                    checked = minimizeBarOnScroll,
                    onCheckedChange = viewModel::setMinimizeBarOnScroll,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_motion_art_title),
                    subtitle = stringResource(R.string.settings_motion_art_subtitle),
                    checked = ui.motionArt.enabled,
                    onCheckedChange = viewModel::setAnimatedArtwork,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_motion_art_wifi_title),
                    subtitle = stringResource(R.string.settings_motion_art_wifi_subtitle),
                    checked = ui.motionArt.wifiOnly,
                    onCheckedChange = viewModel::setAnimatedArtworkWifiOnly,
                    enabled = ui.motionArt.enabled,
                )
            }
            item {
                MotionArtStylePicker(
                    selected = ui.motionArt.style,
                    enabled = ui.motionArt.enabled,
                    onSelect = viewModel::setAnimatedArtworkStyle,
                )
            }
            item {
                MotionArtSourcePicker(
                    selected = ui.motionArt.source,
                    enabled = ui.motionArt.enabled,
                    onSelect = viewModel::setAnimatedArtworkSource,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_lyrics)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_lyrics_prefer_word_title),
                    subtitle = stringResource(R.string.settings_lyrics_prefer_word_subtitle),
                    checked = preferWordSyncedLyrics,
                    onCheckedChange = viewModel::setPreferWordSyncedLyrics,
                )
            }
            item {
                LyricsSourcesCard(
                    items = lyricsSources,
                    onToggle = viewModel::setLyricsSourceEnabled,
                    onMove = viewModel::moveLyricsSource,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_search)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_search_videos_title),
                    subtitle = stringResource(R.string.settings_search_videos_subtitle),
                    checked = searchVideos,
                    onCheckedChange = viewModel::setSearchVideos,
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_downloads)) }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_cache_songs_title),
                    subtitle = stringResource(R.string.settings_cache_songs_subtitle),
                    checked = cacheSongs,
                    onCheckedChange = viewModel::setCacheSongs,
                )
            }
            item {
                val limits = AppPreferences.CACHE_LIMITS_MB
                // Held locally while dragging; saved (and trimmed) only when the thumb is released.
                var dragIndex by remember { mutableStateOf<Int?>(null) }
                val savedIndex = limits.indexOfFirst { it >= cacheLimitMb }.takeIf { it >= 0 } ?: limits.lastIndex
                val index = dragIndex ?: savedIndex
                SettingsSlider(
                    title = stringResource(R.string.settings_cache_limit_title),
                    subtitle = stringResource(
                        R.string.settings_cache_limit_subtitle,
                        formatBytes(cacheBytes),
                        formatBytes(limits[index] * 1024L * 1024L),
                    ),
                    value = index.toFloat(),
                    valueRange = 0f..limits.lastIndex.toFloat(),
                    steps = limits.size - 2,
                    onValueChange = { dragIndex = it.roundToInt().coerceIn(0, limits.lastIndex) },
                    onValueChangeFinished = {
                        dragIndex?.let { viewModel.setCacheLimitMb(limits[it]) }
                        dragIndex = null
                    },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_cache_clear_title),
                    subtitle = stringResource(R.string.settings_cache_clear_subtitle, formatBytes(cacheBytes)),
                    destructive = true,
                    onClick = { confirmClearCache = true },
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_wifi_only_title),
                    subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                    checked = ui.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_storage_title),
                    subtitle = stringResource(
                        R.string.settings_storage_subtitle,
                        pluralStringResource(R.plurals.track_count, downloadCount, downloadCount),
                        formatBytes(storageBytes),
                    ),
                    onClick = viewModel::refreshStorage,
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_remove_all_title),
                    subtitle = stringResource(R.string.settings_remove_all_subtitle, formatBytes(storageBytes)),
                    destructive = true,
                    onClick = { confirmClearDownloads = true },
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_youtube_account)) }
            item {
                if (youTubeSignedIn) {
                    SettingsRow(
                        title = stringResource(R.string.settings_youtube_signed_in_title),
                        subtitle = stringResource(R.string.settings_youtube_signed_in_subtitle),
                        destructive = true,
                        onClick = {
                            accountViewModel.signOut()
                            Toast.makeText(context, signedOutMessage, Toast.LENGTH_SHORT).show()
                        },
                    )
                } else {
                    SettingsRow(
                        title = stringResource(R.string.youtube_signin_action),
                        subtitle = stringResource(R.string.settings_youtube_signed_out_subtitle),
                        onClick = accountViewModel::openSignIn,
                    )
                }
            }
            if (youTubeSignedIn) {
                item {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_youtube_personalize_title),
                        subtitle = stringResource(R.string.settings_youtube_personalize_subtitle),
                        checked = personalizeYouTube,
                        onCheckedChange = accountViewModel::setPersonalize,
                    )
                }
            }

            item { SettingsSection(stringResource(R.string.settings_section_privacy)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_clear_history_title),
                    subtitle = if (recentSearchCount == 0) {
                        stringResource(R.string.settings_clear_history_empty)
                    } else {
                        pluralStringResource(R.plurals.saved_search_count, recentSearchCount, recentSearchCount)
                    },
                    onClick = viewModel::clearSearchHistory,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    SettingsRow(
                        title = stringResource(R.string.settings_language_title),
                        subtitle = stringResource(R.string.settings_language_subtitle),
                        onClick = { openLanguageSettings(context) },
                    )
                }
            }

            item { SettingsSection(stringResource(R.string.settings_section_backup)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = if (backupBusy) {
                        stringResource(R.string.backup_in_progress)
                    } else {
                        stringResource(R.string.settings_backup_subtitle)
                    },
                    onClick = if (backupBusy) null else {
                        { backupLauncher.launch("hymn-backup.json") }
                    },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_restore_title),
                    subtitle = if (backupBusy) {
                        stringResource(R.string.restore_in_progress)
                    } else {
                        stringResource(R.string.settings_restore_subtitle)
                    },
                    onClick = if (backupBusy) null else {
                        { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                    },
                )
            }

            item { SettingsSection(stringResource(R.string.settings_section_about)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_check_updates_title),
                    subtitle = if (updateState is UpdateState.Checking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.settings_check_updates_subtitle, versionName)
                    },
                    onClick = if (updateState is UpdateState.Checking) null else {
                        { updateViewModel.checkNow() }
                    },
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_version, versionName),
                    onClick = null,
                )
            }
        }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(stringResource(R.string.settings_remove_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_remove_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        confirmClearDownloads = false
                    },
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.settings_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_cache_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        confirmClearCache = false
                    },
                ) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showSupport) {
        SupportDialog(onDismiss = { showSupport = false })
    }

    if (showYouTubeSignIn) {
        GoogleSignInDialog(
            onSignedIn = { cookie ->
                accountViewModel.completeSignIn(cookie).also { ok ->
                    if (ok) Toast.makeText(context, signedInMessage, Toast.LENGTH_SHORT).show()
                }
            },
            onClose = accountViewModel::cancelSignIn,
        )
    }

    (updateState as? UpdateState.Available)?.let { available ->
        UpdateDialog(
            info = available.info,
            currentVersion = versionName,
            onUpdate = { updateViewModel.install(available.info) },
            onLater = { updateViewModel.clear() },
            onDismiss = { updateViewModel.clear() },
        )
    }
}

/**
 * The "Buy me a coffee" support card — the first thing in Settings. On entry the cup pops in with a
 * bounce and a few steam wisps rise once (driven by [Animatable]s in `LaunchedEffect(Unit)`, so the
 * animation plays exactly once each time you navigate here). Tapping opens the support link.
 */
@Composable
private fun BuyMeACoffeeCard(onClick: () -> Unit) {
    val cupScale = remember { Animatable(0.5f) }
    val steam = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        cupScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    LaunchedEffect(Unit) {
        steam.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Coral, Lavender)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            // Steam: three wisps that rise and fade a single time as `steam` runs 0 -> 1.
            Canvas(modifier = Modifier.matchParentSize()) {
                val s = steam.value
                val fade = sin(s * PI).toFloat().coerceIn(0f, 1f)
                if (fade > 0f) {
                    listOf(-9f, 0f, 9f).forEachIndexed { i, dx ->
                        drawCircle(
                            color = Color.White.copy(alpha = fade * 0.5f),
                            radius = 2.4.dp.toPx(),
                            center = Offset(
                                x = size.width / 2f + dx + sin(s * 6f + i) * 2f,
                                y = size.height * 0.30f - s * size.height * 0.34f,
                            ),
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Filled.LocalDrink,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer { scaleX = cupScale.value; scaleY = cupScale.value },
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_coffee_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnAccent,
            )
            Text(
                stringResource(R.string.settings_coffee_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = OnAccent.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * The UPI "buy me a coffee" chooser, mirroring the Vercel landing page: pick an app (which
 * deep-links straight into it) or copy the UPI ID. Each app has its own URL scheme; if the chosen
 * app isn't installed we fall back to a generic `upi://` chooser so any UPI app can handle it.
 */
@Composable
private fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val params = "pa=${Uri.encode(UPI_VPA)}&pn=${Uri.encode(UPI_NAME)}&cu=INR"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.settings_coffee_title)} 🥤") },
        text = {
            Column {
                Text(
                    stringResource(R.string.coffee_lede),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpiAppButton(stringResource(R.string.coffee_gpay)) { launchUpi(context, "tez://upi/pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_phonepe)) { launchUpi(context, "phonepe://pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_paytm)) { launchUpi(context, "paytmmp://pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_any)) { launchUpi(context, "upi://pay?$params", params) }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coffee_upi_label), style = MaterialTheme.typography.labelMedium, color = Coral)
                Text(UPI_VPA, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OnDark)
            }
        },
        // Copy leaves the dialog open (you may still want to pick an app); Close dismisses it.
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(UPI_VPA))
                Toast.makeText(context, context.getString(R.string.coffee_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.coffee_copy)) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun UpiAppButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = OnDark,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OverlayTint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Deep-links into [primary]'s UPI app; falls back to a generic `upi://` chooser, then a toast. */
private fun launchUpi(context: Context, primary: String, params: String) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primary)))
    }.isSuccess
    if (opened) return
    val chooser = runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?$params")),
                context.getString(R.string.coffee_pay_with),
            ),
        )
    }.isSuccess
    if (!chooser) {
        Toast.makeText(context, context.getString(R.string.coffee_no_upi), Toast.LENGTH_LONG).show()
    }
}

// Same payee as the Vercel landing page's support dialog.
private const val UPI_VPA = "rs91963@pingpay"
private const val UPI_NAME = "Raj"

/** Opens the system per-app language screen (Android 13+). */
private fun openLanguageSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(currentLocale()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Coral,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Coral),
        )
    }
}

/**
 * Where lyrics come from, in the order they are tried. Each source can be switched off or moved;
 * the first one with lyrics timed to the playing recording wins.
 */
@Composable
private fun LyricsSourcesCard(
    items: List<LyricsSourceItem>,
    onToggle: (LyricsSource, Boolean) -> Unit,
    onMove: (LyricsSource, Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.settings_lyrics_sources_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Text(
            stringResource(R.string.settings_lyrics_sources_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.enabled) Coral else OnDarkVariant,
                    modifier = Modifier.width(20.dp),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .alpha(if (item.enabled) 1f else 0.45f),
                ) {
                    Text(item.source.label, style = MaterialTheme.typography.bodyMedium, color = OnDark)
                    Text(
                        lyricsSourceDetail(item.source),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkVariant,
                    )
                }
                IconButton(onClick = { onMove(item.source, -1) }, enabled = index > 0) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_lyrics_move_up, item.source.label),
                        tint = if (index > 0) OnDark else OnDarkVariant.copy(alpha = 0.3f),
                    )
                }
                IconButton(onClick = { onMove(item.source, 1) }, enabled = index < items.lastIndex) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_lyrics_move_down, item.source.label),
                        tint = if (index < items.lastIndex) OnDark else OnDarkVariant.copy(alpha = 0.3f),
                    )
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { onToggle(item.source, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Coral),
                )
            }
        }
    }
}

@Composable
private fun lyricsSourceDetail(source: LyricsSource): String = when (source) {
    LyricsSource.APPLE_MUSIC -> stringResource(R.string.settings_lyrics_source_apple)
    LyricsSource.BINI_LYRICS -> stringResource(R.string.settings_lyrics_source_bini)
    LyricsSource.LYRICS_PLUS -> stringResource(R.string.settings_lyrics_source_lyricsplus)
    LyricsSource.SIMPMUSIC -> stringResource(R.string.settings_lyrics_source_simpmusic)
    LyricsSource.LRCLIB -> stringResource(R.string.settings_lyrics_source_lrclib)
    LyricsSource.KUGOU -> stringResource(R.string.settings_lyrics_source_kugou)
    LyricsSource.UNISON -> stringResource(R.string.settings_lyrics_source_unison)
    LyricsSource.YOUTUBE_MUSIC -> stringResource(R.string.settings_lyrics_source_youtube)
}

/** Segmented System / Light / Dark selector. */@Composable
private fun ThemeModePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Coral else OverlayTint.copy(alpha = 0.08f))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels.getValue(mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) OnAccent else OnDarkVariant,
                    )
                }
            }
        }
    }
}

/**
 * Which catalog cover loops come from.
 *
 * Coverage differs per catalog rather than overlapping, so "Auto" tries each in turn and is what
 * almost everyone should leave this on; the explicit picks are for listeners who want one
 * catalog's loops specifically and accept the misses that come with giving the others up.
 */
@Composable
private fun MotionArtSourcePicker(
    selected: MotionArtProvider,
    enabled: Boolean,
    onSelect: (MotionArtProvider) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(alpha),
    ) {
        Text(
            stringResource(R.string.settings_motion_art_source_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MotionArtProvider.entries.forEach { provider ->
                val active = provider == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Coral else OverlayTint.copy(alpha = 0.08f))
                        .clickable(enabled = enabled) { onSelect(provider) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        motionArtSourceLabel(provider),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (active) OnAccent else OnDarkVariant,
                    )
                }
            }
        }
    }
}

/**
 * Where the cover loop plays: inside the album card, borderless across the top of the player
 * (Apple Music style), or across the whole player.
 */
@Composable
private fun MotionArtStylePicker(
    selected: MotionArtStyle,
    enabled: Boolean,
    onSelect: (MotionArtStyle) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(alpha),
    ) {
        Text(
            stringResource(R.string.settings_motion_art_style_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Text(
            stringResource(
                when (selected) {
                    MotionArtStyle.CARD -> R.string.settings_motion_art_card_subtitle
                    MotionArtStyle.EDGE -> R.string.settings_motion_art_edge_subtitle
                    MotionArtStyle.FULL_SCREEN -> R.string.settings_motion_art_full_subtitle
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                MotionArtStyle.CARD to R.string.settings_motion_art_style_card,
                MotionArtStyle.EDGE to R.string.settings_motion_art_style_edge,
                MotionArtStyle.FULL_SCREEN to R.string.settings_motion_art_style_full,
            ).forEach { (value, label) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Coral else OverlayTint.copy(alpha = 0.08f))
                        .clickable(enabled = enabled) { onSelect(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (active) OnAccent else OnDarkVariant,
                    )
                }
            }
        }
    }
}

/** Catalog names are trademarks and stay as-is; only the automatic option is translated. */
@Composable
private fun motionArtSourceLabel(provider: MotionArtProvider): String = when (provider) {
    MotionArtProvider.AUTO -> stringResource(R.string.settings_motion_art_source_auto)
    MotionArtProvider.APPLE -> "Apple"
    MotionArtProvider.TIDAL -> "TIDAL"
    MotionArtProvider.VIVI -> "Vivi"
}

/** Swatch grid for the accent override, with a "default" entry that clears it. */
@Composable
private fun AccentPicker(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Text(
            stringResource(R.string.settings_accent_title),
            style = MaterialTheme.typography.bodyLarge,
            color = OnDark,
        )
        Text(
            stringResource(
                if (enabled) R.string.settings_accent_subtitle else R.string.settings_accent_locked,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Swatch(
                color = Coral,
                selected = selected == AppPreferences.NO_ACCENT,
                enabled = enabled,
                isDefault = true,
                onClick = { onSelect(AppPreferences.NO_ACCENT) },
            )
            AccentPresets.forEach { preset ->
                val argb = preset.toArgb()
                Swatch(
                    color = preset,
                    selected = selected == argb,
                    enabled = enabled,
                    isDefault = false,
                    onClick = { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.5.dp,
                color = if (selected) OnDark else OverlayTint.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isDefault) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.settings_accent_default),
                tint = OnAccent,
                modifier = Modifier.size(18.dp),
            )
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.isDarkEnoughForWhiteText()) Color.White else Color(0xFF1B1B1F),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) Coral else OnDark,
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLow)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = OnDark)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnDarkVariant)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Coral)
                        .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                )
            },
            track = { sliderState ->
                val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
                val fraction = ((sliderState.value - valueRange.start) / span).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Coral, Lavender))),
                    )
                }
            },
        )
    }
}

/** Human-readable byte count, e.g. "142 MB". */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
