package com.hungryears.music.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hungryears.music.R
import com.hungryears.music.ui.components.EmptyState
import com.hungryears.music.ui.components.PlaylistNameDialog
import com.hungryears.music.ui.components.PlaylistRow
import com.hungryears.music.ui.components.ScreenHeader
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    onOpenPlaylist: (Long) -> Unit = {},
    viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaylistsScreenContent(
        state = state,
        onOpenPlaylist = onOpenPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        modifier = modifier,
    )
}

@Composable
fun PlaylistsScreenContent(
    state: PlaylistsUiState,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(HungryEarsSpacing.xl)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ScreenHeader(
                    title = stringResource(R.string.playlists_title),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.playlists_new),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (state.playlists.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.playlists_empty_title),
                    body = stringResource(R.string.playlists_empty_body),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = HungryEarsSpacing.xxl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(HungryEarsSpacing.xs),
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlists_create_title),
            confirmLabel = stringResource(R.string.dialog_create),
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}
