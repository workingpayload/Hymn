package com.example.musicsm.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicsm.R
import com.example.musicsm.domain.model.Song
import com.example.musicsm.ui.actions.NewPlaylistDialog
import com.example.musicsm.ui.actions.SongExtraAction
import com.example.musicsm.ui.actions.SongOptionsSheet
import com.example.musicsm.ui.components.EmptyState
import com.example.musicsm.ui.components.SongRow
import com.example.musicsm.ui.library.LibraryViewModel
import com.example.musicsm.ui.theme.AppBackground
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Fixed row height, so a drag distance maps cleanly onto a number of positions moved. */
private val RowHeight = 64.dp

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf<QueueSelection?>(null) }
    var showSave by remember { mutableStateOf(false) }

    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }
    // Index currently being dragged (-1 = none) and its live pixel offset.
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    // Read inside the gesture lambda, which is never recomposed — keep it fresh.
    val queueSize by rememberUpdatedState(state.queue.size)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                stringResource(R.string.queue_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (state.queue.isNotEmpty()) {
                IconButton(onClick = { showSave = true }) {
                    Icon(
                        Icons.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.queue_save_as_playlist),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.QueueMusic,
                    title = stringResource(R.string.queue_empty_title),
                    subtitle = stringResource(R.string.queue_empty_subtitle),
                )
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(state.queue) { index, song ->
                val dragging = index == dragIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .background(if (dragging) OverlayTint.copy(alpha = 0.10f) else Color.Transparent),
                ) {
                    SongRow(
                        song = song,
                        onClick = { viewModel.seekToIndex(index) },
                        isCurrent = index == state.currentIndex,
                        onMore = { options = QueueSelection(song, index) },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.queue_drag_to_reorder),
                        tint = OnDarkVariant,
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(24.dp)
                            // Keyed by index, which never changes for a given row slot, so an
                            // in-flight drag is never cancelled by the list reordering itself.
                            .pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = { dragIndex = index; dragOffset = 0f },
                                    onDragEnd = { dragIndex = -1; dragOffset = 0f },
                                    onDragCancel = { dragIndex = -1; dragOffset = 0f },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragOffset += delta.y
                                        val from = dragIndex
                                        if (from < 0) return@detectDragGestures
                                        val shift = (dragOffset / rowHeightPx).roundToInt()
                                        if (shift == 0) return@detectDragGestures
                                        val to = (from + shift).coerceIn(0, queueSize - 1)
                                        if (to == from) return@detectDragGestures
                                        viewModel.moveQueueItem(from, to)
                                        dragIndex = to
                                        dragOffset -= (to - from) * rowHeightPx
                                    },
                                )
                            },
                    )
                    IconButton(onClick = { viewModel.removeQueueItem(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove), tint = OnDarkVariant)
                    }
                }
            }
        }
    }

    if (showSave) {
        val defaultName = stringResource(
            R.string.queue_default_playlist_name,
            remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date()) },
        )
        NewPlaylistDialog(
            initialName = defaultName,
            onCreate = { name ->
                // Snapshot now: the queue may move on while the dialog was open.
                val songs = state.queue.distinctBy { it.id }
                libraryViewModel.createPlaylistWithSongs(name, songs)
                showSave = false
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.queue_saved_as_playlist, songs.size, songs.size, name.trim()),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showSave = false },
        )
    }

    options?.let { selection ->
        SongOptionsSheet(
            song = selection.song,
            playerViewModel = viewModel,
            onDismiss = { options = null },
            extraAction = SongExtraAction(
                label = stringResource(R.string.queue_remove_from),
                destructive = true,
                onAction = { viewModel.removeQueueItem(selection.index) },
            ),
        )
    }
}

private data class QueueSelection(val song: Song, val index: Int)

