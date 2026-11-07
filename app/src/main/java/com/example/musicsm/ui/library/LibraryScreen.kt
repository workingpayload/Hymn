package com.example.musicsm.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.ui.components.AlbumCard
import com.example.musicsm.ui.components.ArtistCircle
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalBottomBarPadding
import com.example.musicsm.ui.components.SectionHeader
import com.example.musicsm.ui.components.accentColorFor
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.Lavender
import com.example.musicsm.ui.theme.OnAccent
import com.example.musicsm.ui.theme.StitchBackground
import com.example.musicsm.ui.theme.Teal
import com.example.musicsm.ui.theme.SurfaceLow

@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onImportPlaylist: () -> Unit,
    onScanCode: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenCached: () -> Unit,
    onOpenRemotePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val accountLibrary by viewModel.accountLibrary.collectAsStateWithLifecycle()
    val liked by viewModel.likedSongs.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val followed by viewModel.followedArtists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(StitchBackground),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onScanCode)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.library_scan_code), tint = MaterialTheme.colorScheme.onBackground)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onImportPlaylist)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = stringResource(R.string.library_import_playlist), tint = MaterialTheme.colorScheme.onBackground)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showCreate = true }
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_new_playlist), tint = MaterialTheme.colorScheme.onBackground)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Liked Songs card
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceLow)
                    .padding(vertical = 6.dp),
            ) {
                CategoryRow(Icons.Filled.Favorite, Coral, stringResource(R.string.library_liked_songs), "${liked.size}", onOpenLiked)
                CategoryRow(Icons.Filled.LibraryMusic, Teal, stringResource(R.string.library_local_files), "", onOpenLocal)
                CategoryRow(Icons.Filled.OfflineBolt, Coral, stringResource(R.string.cached_title), "", onOpenCached)
                CategoryRow(Icons.Filled.BarChart, Lavender, stringResource(R.string.stats_open), "", onOpenStats)
            }
        }

        // Action chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledChip(stringResource(R.string.library_shuffle_all), Icons.Filled.Shuffle, filled = true) {
                    playerViewModel.shufflePlay(liked, isPlaylist = true)
                }
                FilledChip(stringResource(R.string.library_favorites_mix), Icons.Filled.Favorite, filled = false, onClick = onOpenLiked)
            }
        }

        // Recently played
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.shelf_recently_played)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    itemsIndexed(recent, key = { _, song -> song.id }) { index, song ->
                        AlbumCard(
                            title = song.title,
                            subtitle = song.artist,
                            artworkUrl = song.artworkUrl,
                            onClick = { playerViewModel.play(recent, index) },
                        )
                    }
                }
            }
        }

        // Downloaded (offline)
        if (downloads.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.shelf_downloaded)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    itemsIndexed(downloads, key = { _, song -> song.id }) { index, song ->
                        AlbumCard(
                            title = song.title,
                            subtitle = song.artist,
                            artworkUrl = song.artworkUrl,
                            onClick = { playerViewModel.play(downloads, index) },
                        )
                    }
                }
            }
        }

        // Followed artists
        if (followed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_followed_artists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(followed, key = { it.id }) { artist ->
                        ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                    }
                }
            }
        }

        // The signed-in YouTube Music library (Settings ▸ YouTube account ▸ Personalise)
        if (accountLibrary.playlists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_youtube_playlists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(accountLibrary.playlists, key = { it.id }) { playlist ->
                        AlbumCard(
                            title = playlist.name,
                            subtitle = stringResource(R.string.library_youtube_music),
                            artworkUrl = playlist.artworkUrl,
                            onClick = { onOpenRemotePlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
        if (accountLibrary.artists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_youtube_artists)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(accountLibrary.artists, key = { it.id }) { artist ->
                        ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                    }
                }
            }
        }

        // Pinned Playlists header
        item {
            Text(
                text = stringResource(R.string.library_pinned_playlists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }

        // Playlist grid (2 columns)
        items(playlists.chunked(2), key = { row -> row.first().id }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { pl ->
                    PlaylistGridCard(
                        playlist = pl,
                        onClick = { pl.id.toLongOrNull()?.let(onOpenPlaylist) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.library_new_custom_playlist), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onCreate = {
                viewModel.createPlaylist(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun CategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    trailing: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilledChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Coral else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) OnAccent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) OnAccent else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accentColorFor(playlist.name)
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            ArtworkImage(
                url = playlist.artworkUrl ?: playlist.songs.firstOrNull()?.artworkUrl,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f)),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play), tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.library_playlist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_new_playlist)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}