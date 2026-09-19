package com.hungryears.music.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hungryears.music.R
import com.hungryears.music.ui.components.EmptyState
import com.hungryears.music.ui.components.PlaylistNameDialog
import com.hungryears.music.ui.components.TrackRow
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = viewModel(
        factory = PlaylistDetailViewModel.factory(playlistId),
        key = "playlist-$playlistId",
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaylistDetailScreenContent(
        state = state,
        onBack = onBack,
        onPlayAll = viewModel::playAll,
        onPlayTrack = viewModel::playTrack,
        onToggleFavourite = viewModel::toggleFavourite,
        onRemoveTrack = viewModel::removeTrack,
        onMoveTrack = viewModel::moveTrack,
        onRename = viewModel::rename,
        onDelete = viewModel::deletePlaylist,
        modifier = modifier,
    )
}

@Composable
fun PlaylistDetailScreenContent(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Long) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(HungryEarsSpacing.xl)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.playlists_rename),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.playlists_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onPlayAll,
                enabled = state.tracks.isNotEmpty(),
                modifier = Modifier.padding(top = HungryEarsSpacing.s),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(HungryEarsSpacing.xs))
                Text(text = stringResource(R.string.playlists_play_all))
            }

            if (state.isLoaded && state.tracks.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.playlists_empty_tracks_title),
                    body = stringResource(R.string.playlists_empty_tracks_body),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = HungryEarsSpacing.xxl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(HungryEarsSpacing.xs),
                ) {
                    itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            TrackRow(
                                track = track,
                                isFavourite = track.id in state.favouriteIds,
                                onClick = { onPlayTrack(track.id) },
                                onToggleFavourite = { onToggleFavourite(track.id) },
                                onMore = { menuOpen = true },
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlists_move_up)) },
                                    enabled = index > 0,
                                    onClick = {
                                        onMoveTrack(index, index - 1)
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlists_move_down)) },
                                    enabled = index < state.tracks.lastIndex,
                                    onClick = {
                                        onMoveTrack(index, index + 1)
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlists_remove_track)) },
                                    onClick = {
                                        onRemoveTrack(track.id)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlists_rename_title),
            confirmLabel = stringResource(R.string.dialog_rename),
            initialName = state.name,
            onConfirm = { name ->
                onRename(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}
