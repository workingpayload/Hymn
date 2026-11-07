package com.example.musicsm.ui.album

import com.example.musicsm.ui.components.ScreenBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.ChromeScrim
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.HeroArtwork
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.components.rememberHeroZoom
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.CoralLight
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.asDeepTint
import com.example.musicsm.ui.theme.isHueless
import com.example.musicsm.ui.theme.onTint
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceHigh
import com.example.musicsm.ui.theme.SurfaceHighest
import com.example.musicsm.ui.theme.Teal
import com.example.musicsm.ui.util.formatDuration

@Composable
fun AlbumDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    downloadViewModel: com.example.musicsm.ui.player.DownloadViewModel = hiltViewModel(),
) {
    ScreenBackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val favorited by viewModel.favorited.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val artworkUrl = ui.artworkUrl ?: ui.songs.firstOrNull()?.artworkUrl
    val accent = rememberDominantColorState(
        url = artworkUrl,
        fallback = accentColorFor(ui.title),
    )
    val listState = rememberLazyListState()
    val (heroZoom, zoomModifier) = rememberHeroZoom()

    // Palette tokens are composable reads, so they are hoisted out of the draw lambda.
    val backdrop = AppBackground
    // Derived, not recomputed per draw: the conversion allocates, and the background repaints far
    // more often than the cover behind it changes. A cover with no usable hue is left alone rather
    // than deepened, because deepening grey only produces a muddier grey.
    val tint by remember(backdrop) {
        derivedStateOf {
            val raw = accent.value
            if (raw.isHueless()) backdrop else raw.asDeepTint()
        }
    }
    val scrimProgress = remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / SCRIM_RAMP_PX).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Held flat over the top half, then eased out over the bottom. A single flat fill
                // leaves a visible seam where the cover the tint came from stops and the plain
                // track list starts; easing it turns that line into a deliberate wash.
                drawRect(
                    Brush.verticalGradient(
                        0.0f to tint,
                        0.5f to tint,
                        1.0f to backdrop,
                    ),
                )
            },
    ) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = Coral, modifier = Modifier.align(Alignment.Center))
            }

            ui.error != null -> Box(Modifier.fillMaxSize()) {
                ErrorState(
                    message = ui.error!!,
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(zoomModifier),
                contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
            ) {
                item(key = "hero") {
                    HeroArtwork(url = artworkUrl, scale = { heroZoom.scale })
                }
                item(key = "header") {
                    AlbumHeader(
                        ui = ui,
                        tint = tint,
                        playerViewModel = playerViewModel,
                        favorited = favorited,
                        added = added,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleAdd = viewModel::toggleAdd,
                        onDownloadAll = { ui.songs.forEach(downloadViewModel::download) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.album_tracks),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint.onTint(emphasis = 0.5f),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                if (ui.songs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.album_no_songs),
                            color = OnDarkVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    itemsIndexed(ui.songs, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = playerState.currentSong?.id == song.id
                        TrackRow(
                            index = index + 1,
                            title = song.title,
                            artist = song.artist,
                            duration = formatDuration(song.durationMs),
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && playerState.isPlaying,
                            onClick = { playerViewModel.play(ui.songs, index, isPlaylist = ui.isPlaylist) },
                            onLongClick = { optionsSong = song },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }

        // Floating above the artwork rather than in a bar above it, so nothing pushes the hero
        // down out from under the status bar.
        ChromeScrim(
            progress = { scrimProgress.value },
            modifier = Modifier.align(Alignment.TopCenter).height(CHROME_SCRIM_HEIGHT),
        )
        TopBar(onBack = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding())
    }

    optionsSong?.let { song ->
        SongOptionsSheet(
            song = song,
            playerViewModel = playerViewModel,
            onDismiss = { optionsSong = null },
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OverlayTint.copy(alpha = 0.18f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AlbumHeader(
    ui: AlbumDetailUiState,
    tint: Color,
    playerViewModel: PlayerViewModel,
    favorited: Boolean,
    added: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAdd: () -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The cover is the page's background now, so the title block takes its contrast from the
    // colour the cover produced rather than from the app's flat one.
    val onTintHeading = tint.onTint(emphasis = 1f)
    val onTintBody = tint.onTint(emphasis = 0.5f)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = onTintHeading,
            textAlign = TextAlign.Center,
        )
        if (ui.artist.isNotBlank()) {
            Text(
                text = ui.artist,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CoralLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val totalMs = ui.songs.sumOf { it.durationMs }
        val meta = buildList {
            ui.year?.let { add(it) }
            add("${ui.songs.size} songs")
            if (totalMs > 0) add("${totalMs / 60000} min")
        }.joinToString(" • ")
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = onTintBody,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
        ActionCluster(ui, playerViewModel, favorited, added, onToggleFavorite, onToggleAdd, onDownloadAll)
    }
}

@Composable
private fun ActionCluster(
    ui: AlbumDetailUiState,
    playerViewModel: PlayerViewModel,
    favorited: Boolean,
    added: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleAdd: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Play + Shuffle pills get the full width to themselves.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PlayArrow,
                iconTint = OnAccent,
                label = stringResource(R.string.action_play),
                labelColor = OnAccent,
                background = Coral,
                enabled = ui.songs.isNotEmpty(),
                onClick = { playerViewModel.play(ui.songs, 0, isPlaylist = ui.isPlaylist) },
            )
            PillButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Shuffle,
                iconTint = Coral,
                label = stringResource(R.string.action_shuffle),
                labelColor = MaterialTheme.colorScheme.onBackground,
                background = SurfaceHighest.copy(alpha = 0.6f),
                enabled = ui.songs.isNotEmpty(),
                onClick = { playerViewModel.shufflePlay(ui.songs, isPlaylist = ui.isPlaylist) },
            )
        }
        // Secondary actions on their own row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = if (added) Icons.Filled.Check else Icons.Filled.Add,
                tint = if (added) Teal else MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_add_to_library),
                onClick = onToggleAdd,
            )
            CircleIconButton(
                icon = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (favorited) Coral else MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_favorite),
                onClick = onToggleFavorite,
            )
            CircleIconButton(
                icon = Icons.Filled.Download,
                tint = MaterialTheme.colorScheme.onBackground,
                contentDescription = stringResource(R.string.album_download),
                onClick = onDownloadAll,
            )
        }
    }
}

@Composable
private fun PillButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    labelColor: Color,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceHighest.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    index: Int,
    title: String,
    artist: String,
    duration: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SurfaceHigh.copy(alpha = 0.8f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Coral, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnDarkVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) Coral else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            duration,
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) Coral else OnDarkVariant,
        )
    }
}


/**
 * How far the cover has to scroll before the chrome is fully shaded.
 *
 * A raw pixel figure rather than a dp one: it is compared against a scroll offset, which is already
 * in pixels, and converting per frame to compare two numbers is work for nothing.
 */
private const val SCRIM_RAMP_PX = 260f

/** Tall enough to cover the status bar and the back button, and to fade out above the artwork. */
private val CHROME_SCRIM_HEIGHT = 160.dp
