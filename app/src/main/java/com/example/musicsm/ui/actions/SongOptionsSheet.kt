package com.example.musicsm.ui.actions

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.components.ArtworkImage
import com.example.musicsm.ui.components.LocalSongNavigator
import com.example.musicsm.ui.library.AddToPlaylistSheet
import com.example.musicsm.ui.library.LibraryViewModel
import com.example.musicsm.ui.player.DownloadViewModel
import com.example.musicsm.ui.player.PlayerViewModel
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import com.example.musicsm.ui.util.shareSong

/** An optional screen-specific row, e.g. "Remove from playlist" or "Remove from queue". */
data class SongExtraAction(
    val label: String,
    val icon: ImageVector = Icons.Filled.Delete,
    val destructive: Boolean = false,
    val onAction: () -> Unit,
)

/**
 * The long-press / overflow menu for a track. One sheet used by every list in the app so the
 * same actions are always available: queue, playlists, likes, downloads, navigation, share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    extraAction: SongExtraAction? = null,
    extraActions: List<SongExtraAction> = emptyList(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel(),
    actionsViewModel: SongActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navigator = LocalSongNavigator.current

    val isLiked by remember(song.id) { libraryViewModel.isLiked(song.id) }
        .collectAsStateWithLifecycle(initialValue = false)
    val isDownloaded by remember(song.id) { downloadViewModel.isDownloaded(song.id) }
        .collectAsStateWithLifecycle(initialValue = false)
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val resolving by actionsViewModel.resolving.collectAsStateWithLifecycle()

    var showPlaylists by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // Only ever one surface at a time — nesting modal sheets doesn't work well.
    when {
        showCreate -> NewPlaylistDialog(
            onCreate = { name ->
                libraryViewModel.createPlaylistWithSong(name, song)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        showPlaylists -> AddToPlaylistSheet(
            playlists = playlists,
            onPick = {
                libraryViewModel.addToPlaylist(it, song)
                onDismiss()
            },
            onCreateNew = {
                showPlaylists = false
                showCreate = true
            },
            onDismiss = onDismiss,
        )

        else -> ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                SheetHeader(song)
                HorizontalDivider(
                    color = OverlayTint.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                ActionRow(Icons.Filled.PlaylistPlay, stringResource(R.string.song_play_next)) {
                    playerViewModel.playNext(song)
                    onDismiss()
                }
                ActionRow(Icons.Filled.QueueMusic, stringResource(R.string.song_add_to_queue)) {
                    playerViewModel.addToQueue(song)
                    onDismiss()
                }
                ActionRow(Icons.Filled.PlaylistAdd, stringResource(R.string.add_to_playlist)) { showPlaylists = true }
                ActionRow(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = stringResource(if (isLiked) R.string.song_remove_from_liked else R.string.song_add_to_liked),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                ) {
                    libraryViewModel.toggleLike(song)
                    onDismiss()
                }
                if (isDownloaded) {
                    ActionRow(Icons.Filled.DownloadDone, stringResource(R.string.downloads_remove), tint = MaterialTheme.colorScheme.primary) {
                        downloadViewModel.delete(song.id)
                        onDismiss()
                    }
                } else {
                    ActionRow(Icons.Filled.Download, stringResource(R.string.player_download)) {
                        downloadViewModel.download(song)
                        onDismiss()
                    }
                }

                HorizontalDivider(
                    color = OverlayTint.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                ActionRow(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.song_go_to_artist),
                    enabled = song.artist.isNotBlank(),
                ) {
                    // The artist page loads by name, so open it directly — no pre-lookup, no wait.
                    onDismiss()
                    navigator.openArtist(song.artist)
                }
                if (!song.album.isNullOrBlank()) {
                    ActionRow(
                        icon = Icons.Filled.Album,
                        label = stringResource(R.string.song_go_to_album),
                        enabled = !resolving,
                        trailing = { if (resolving) SmallSpinner() },
                    ) {
                        actionsViewModel.resolveAlbum(song.artist, song.album.orEmpty()) { id ->
                            onDismiss()
                            if (id != null) {
                                navigator.openAlbum(id)
                            } else {
                                Toast.makeText(context, context.getString(R.string.error_album_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                ActionRow(Icons.Filled.Share, stringResource(R.string.action_share)) {
                    shareSong(context, song)
                    onDismiss()
                }

                val extras = listOfNotNull(extraAction) + extraActions
                if (extras.isNotEmpty()) {
                    HorizontalDivider(
                        color = OverlayTint.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                extras.forEach { action ->
                    ActionRow(
                        icon = action.icon,
                        label = action.label,
                        tint = if (action.destructive) MaterialTheme.colorScheme.error else null,
                    ) {
                        action.onAction()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(song: Song) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = song.artworkUrl,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> OnDarkVariant.copy(alpha = 0.5f)
        tint != null -> tint
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun SmallSpinner() {
    Box(Modifier.size(18.dp)) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun NewPlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
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
