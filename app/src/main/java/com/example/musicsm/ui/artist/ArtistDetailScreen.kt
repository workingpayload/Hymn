package com.example.musicsm.ui.artist

import com.example.musicsm.ui.components.ScreenBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.ChromeScrim
import com.example.musicsm.ui.components.ErrorState
import com.example.musicsm.ui.components.HeroArtwork
import com.example.musicsm.ui.components.DetailLoadingPreview
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.components.rememberHeroZoom
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.theme.SurfaceHighest
import com.example.musicsm.ui.theme.asDeepTint
import com.example.musicsm.ui.theme.isHueless
import com.example.musicsm.ui.theme.onTint

@Composable
fun ArtistDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    ScreenBackHandler { onBack() }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val artworkUrl = ui.artworkUrl ?: ui.topSongs.firstOrNull()?.artworkUrl
    val accent = rememberDominantColorState(
        url = artworkUrl,
        fallback = accentColorFor(ui.name),
    )
    val listState = rememberLazyListState()
    val (heroZoom, zoomModifier) = rememberHeroZoom()

    // Palette tokens are composable reads, so they are hoisted out of the draw lambda.
    val backdrop = AppBackground
    // Derived, not recomputed per draw: the conversion allocates, and the background repaints far
    // more often than the artwork behind it changes. A colour with no usable hue is left alone
    // rather than deepened, because deepening grey only produces a muddier grey.
    val tint by remember(backdrop) {
        derivedStateOf {
            val raw = accent.value
            if (raw.isHueless()) backdrop else raw.asDeepTint()
        }
    }
    // Progressive, because the back button needs a shade only once flat content is under it.
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
                // leaves a visible seam where the artwork the tint came from stops and the plain
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
            // The photo and name already seen (a card, a followed artist) stand in while it loads.
            ui.loading && (artworkUrl != null || ui.name.isNotBlank()) -> DetailLoadingPreview(
                artworkUrl = artworkUrl,
                title = ui.name,
                subtitle = "",
                tint = tint,
                rowArtwork = true,
            )

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
                    ArtistHeader(ui, tint, playerViewModel, liked, viewModel::toggleLike)
                }

                if (ui.topSongs.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.artist_top_songs)) }
                    itemsIndexed(ui.topSongs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { playerViewModel.play(ui.topSongs, index) },
                            onMore = { optionsSong = song },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }

                if (ui.albums.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.section_albums)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                            items(ui.albums, key = { it.id }) { album ->
                                AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                            }
                        }
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
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
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

    optionsSong?.let { song ->
        SongOptionsSheet(
            song = song,
            playerViewModel = playerViewModel,
            onDismiss = { optionsSong = null },
        )
    }
}

@Composable
private fun ArtistHeader(
    ui: ArtistDetailUiState,
    tint: Color,
    playerViewModel: PlayerViewModel,
    liked: Boolean,
    onToggleLike: () -> Unit,
) {
    // Text sits on the artwork's own colour now, not on the app background, so it takes its
    // contrast from the tint. Headings carry more of the hue than the subtitle under them.
    val onTintHeading = tint.onTint(emphasis = 1f)
    val onTintBody = tint.onTint(emphasis = 0.5f)
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ui.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = onTintHeading,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (!ui.subscribers.isNullOrBlank()) {
            Text(
                text = "${ui.subscribers} subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = onTintBody,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        // Follow / like pill.
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (liked) Coral else SurfaceHighest.copy(alpha = 0.6f))
                .clickable(onClick = onToggleLike)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.artist_follow_action),
                tint = if (liked) OnAccent else Coral,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(if (liked) R.string.artist_following else R.string.artist_follow),
                color = if (liked) OnAccent else onTintHeading,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Coral)
                    .clickable(enabled = ui.topSongs.isNotEmpty()) { playerViewModel.play(ui.topSongs, 0) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_play), color = OnAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceHighest.copy(alpha = 0.6f))
                    .clickable(enabled = ui.topSongs.isNotEmpty()) {
                        playerViewModel.shufflePlay(ui.topSongs)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_shuffle), color = onTintHeading, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * How far the artwork has to scroll before the chrome is fully shaded.
 *
 * A raw pixel figure rather than a dp one: it is compared against a scroll offset, which is already
 * in pixels, and converting per frame to compare two numbers is work for nothing.
 */
private const val SCRIM_RAMP_PX = 260f

/** Tall enough to cover the status bar and the back button, and to fade out above the artwork. */
private val CHROME_SCRIM_HEIGHT = 160.dp

