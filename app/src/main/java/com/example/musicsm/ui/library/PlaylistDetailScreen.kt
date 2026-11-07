package com.example.musicsm.ui.library

import android.content.Intent
import com.example.musicsm.ui.components.ScreenBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.components.SortMenuButton
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.components.rememberDominantColorState
import com.example.musicsm.ui.actions.SongExtraAction
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.share.PlaylistShareSheet
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant

@Composable
fun PlaylistDetailScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    downloadViewModel: com.example.musicsm.ui.player.DownloadViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val accent = rememberDominantColorState(
        url = ui.songs.firstOrNull()?.artworkUrl,
        fallback = accentColorFor(ui.title),
    )
    var showDelete by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var optionsSong by remember { mutableStateOf<com.example.musicsm.domain.model.Song?>(null) }
    ScreenBackHandler { onBack() }
    val context = LocalContext.current
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setArtwork(uri.toString())
        }
    }

    Column(modifier = modifier.fillMaxWidth().background(AppBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.weight(1f))
            if (ui.songs.isNotEmpty()) {
                SortMenuButton(
                    current = sort,
                    onSelect = viewModel::setSort,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(onClick = { showShare = true }) {
                    Icon(Icons.Filled.QrCode2, contentDescription = stringResource(R.string.share_playlist), tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { downloadViewModel.downloadAll(ui.songs) }) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.playlist_download_all), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (!ui.isLiked) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.playlist_delete), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Palette tokens are composable reads, so hoist before the draw lambda below.
        val backdrop = AppBackground
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .drawBehind {
                            val c = accent.value
                            drawRect(
                                Brush.verticalGradient(
                                    0.0f to c.copy(alpha = 0.85f),
                                    0.5f to c.copy(alpha = 0.35f),
                                    1.0f to backdrop,
                                ),
                            )
                        },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Cover art (tap to change, for editable playlists).
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(if (!ui.isLiked) Modifier.clickable { pickCover.launch(pickImageRequest()) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArtworkImage(
                                url = ui.artworkUrl,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                            if (!ui.isLiked && ui.artworkUrl == null) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.playlist_add_cover),
                                    tint = OnDark,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ui.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "${ui.songs.size} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnDarkVariant,
                            )
                            if (!ui.isLiked) {
                                Text(
                                    text = stringResource(if (ui.artworkUrl == null) R.string.playlist_add_cover else R.string.playlist_change_cover),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { pickCover.launch(pickImageRequest()) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = {
                            if (ui.songs.isNotEmpty()) playerViewModel.shufflePlay(ui.songs, isPlaylist = true)
                        },
                        color = Color.Transparent,
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.playlist_shuffle_play), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { if (ui.songs.isNotEmpty()) playerViewModel.play(ui.songs, 0, isPlaylist = true) },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play), tint = OnAccent, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            if (ui.songs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.MusicNote,
                        title = stringResource(R.string.playlist_empty_title),
                        subtitle = "Long-press any track and choose \u201CAdd to playlist\u201D.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                itemsIndexed(ui.songs, key = { _, song -> song.id }) { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SongRow(
                            song = song,
                            onClick = { playerViewModel.play(ui.songs, index, isPlaylist = true) },
                            onMore = { optionsSong = song },
                            modifier = Modifier.weight(1f),
                        )
                        if (!ui.isLiked) {
                            IconButton(onClick = { viewModel.remove(song) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove), tint = OnDarkVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    optionsSong?.let { song ->
        SongOptionsSheet(
            song = song,
            playerViewModel = playerViewModel,
            onDismiss = { optionsSong = null },
            extraAction = if (ui.isLiked) null else SongExtraAction(
                label = stringResource(R.string.playlist_remove_from),
                destructive = true,
                onAction = { viewModel.remove(song) },
            ),
        )
    }

    if (showShare) {
        PlaylistShareSheet(
            playlistName = ui.title,
            songs = ui.songs,
            onDismiss = { showShare = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.playlist_delete_confirm_title)) },
            text = { Text("\"${ui.title}\" will be removed from your library. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.delete()
                        onBack()
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun pickImageRequest() =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
