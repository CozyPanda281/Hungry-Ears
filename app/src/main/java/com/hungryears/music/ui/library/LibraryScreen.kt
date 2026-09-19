package com.hungryears.music.ui.library

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hungryears.music.R
import com.hungryears.music.data.permission.AudioPermissions
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Folder
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Track
import com.hungryears.music.ui.components.AddToPlaylistDialog
import com.hungryears.music.ui.components.AlbumRow
import com.hungryears.music.ui.components.ArtistRow
import com.hungryears.music.ui.components.EmptyState
import com.hungryears.music.ui.components.FolderRow
import com.hungryears.music.ui.components.GenreRow
import com.hungryears.music.ui.components.ScreenHeader
import com.hungryears.music.ui.components.TrackRow
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(AudioPermissions.hasReadAudioPermission(context)) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.refresh()
    }
    LifecycleResumeEffect(Unit) {
        permissionGranted = AudioPermissions.hasReadAudioPermission(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.refresh()
    }

    val addedTemplate = stringResource(R.string.playlist_added_to)
    val alreadyTemplate = stringResource(R.string.playlist_already_in)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            is LibraryMessage.AddedToPlaylist -> addedTemplate.format(message.name)
            is LibraryMessage.AlreadyInPlaylist -> alreadyTemplate.format(message.name)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    LibraryScreenContent(
        state = state,
        permissionGranted = permissionGranted,
        onSelectSection = viewModel::selectSection,
        onPlayTracks = viewModel::playTracks,
        onPlayAlbum = viewModel::playAlbum,
        onPlayArtist = viewModel::playArtist,
        onPlayGenre = viewModel::playGenre,
        onPlayFolder = viewModel::playFolder,
        onToggleFavourite = viewModel::toggleFavourite,
        onAddToPlaylist = viewModel::addTrackToPlaylist,
        onCreatePlaylistAndAdd = viewModel::createPlaylistAndAdd,
        onRequestPermission = {
            if (canRequestPermissionInContext(context)) {
                requestPermission.launch(AudioPermissions.readAudioPermission())
            } else {
                openAppSettings(context)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LibraryScreenContent(
    state: LibraryUiState,
    permissionGranted: Boolean,
    onSelectSection: (LibrarySection) -> Unit,
    onPlayTracks: (List<Track>, Long) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onPlayFolder: (Folder) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onAddToPlaylist: (Long, Long, String) -> Unit,
    onCreatePlaylistAndAdd: (String, Long) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(HungryEarsSpacing.xl)) {
            ScreenHeader(title = stringResource(R.string.library_title))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HungryEarsSpacing.xl),
            ) {
                items(LibrarySection.entries) { section ->
                    LibraryTab(
                        section = section,
                        selected = state.section == section,
                        onClick = { onSelectSection(section) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (state.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!permissionGranted) {
                PermissionBanner(
                    onAction = onRequestPermission,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = HungryEarsSpacing.xxl),
                )
            } else {
                SectionContent(
                    state = state,
                    onPlayTracks = onPlayTracks,
                    onPlayAlbum = onPlayAlbum,
                    onPlayArtist = onPlayArtist,
                    onPlayGenre = onPlayGenre,
                    onPlayFolder = onPlayFolder,
                    onToggleFavourite = onToggleFavourite,
                    onMoreTrack = { addToPlaylistTrack = it },
                )
            }
        }
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            trackTitle = track.displayTitle,
            playlists = state.playlists,
            onSelect = { playlist ->
                onAddToPlaylist(playlist.id, track.id, playlist.displayName)
                addToPlaylistTrack = null
            },
            onCreate = { name ->
                onCreatePlaylistAndAdd(name, track.id)
                addToPlaylistTrack = null
            },
            onDismiss = { addToPlaylistTrack = null },
        )
    }
}

@Composable
private fun SectionContent(
    state: LibraryUiState,
    onPlayTracks: (List<Track>, Long) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onPlayFolder: (Folder) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onMoreTrack: (Track) -> Unit,
) {
    when (state.section) {
        LibrarySection.Tracks -> ListPane(
            items = state.tracks,
            isEmpty = state.tracks.isEmpty(),
            emptyBody = stringResource(R.string.library_empty_body),
        ) { LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isFavourite = track.id in state.favouriteIds,
                    onClick = { onPlayTracks(state.tracks, track.id) },
                    onToggleFavourite = { onToggleFavourite(track.id) },
                    onMore = { onMoreTrack(track) },
                )
            }
        } }

        LibrarySection.Albums -> ListPane(
            items = state.albums,
            isEmpty = state.albums.isEmpty(),
            emptyBody = stringResource(R.string.library_empty_albums),
        ) { LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.albums, key = { it.albumId ?: -1L }) { album ->
                AlbumRow(album, onClick = { onPlayAlbum(album) })
            }
        } }

        LibrarySection.Artists -> ListPane(
            items = state.artists,
            isEmpty = state.artists.isEmpty(),
            emptyBody = stringResource(R.string.library_empty_artists),
        ) { LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.artists, key = { it.name }) { artist ->
                ArtistRow(artist, onClick = { onPlayArtist(artist) })
            }
        } }

        LibrarySection.Genres -> ListPane(
            items = state.genres,
            isEmpty = state.genres.isEmpty(),
            emptyBody = stringResource(R.string.library_empty_genres),
        ) { LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.genres, key = { it.name }) { genre ->
                GenreRow(genre, onClick = { onPlayGenre(genre) })
            }
        } }

        LibrarySection.Folders -> ListPane(
            items = state.folders,
            isEmpty = state.folders.isEmpty(),
            emptyBody = stringResource(R.string.library_empty_folders),
        ) { LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.folders, key = { it.path }) { folder ->
                FolderRow(folder, onClick = { onPlayFolder(folder) })
            }
        } }
    }
}

@Composable
private fun <T> ListPane(
    items: List<T>,
    isEmpty: Boolean,
    emptyBody: String,
    content: @Composable () -> Unit,
) {
    if (isEmpty) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.library_empty_title),
            body = emptyBody,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = HungryEarsSpacing.xxl),
        )
    } else {
        content()
    }
}

@Composable
private fun PermissionBanner(
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_library_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(HungryEarsSpacing.xs))
        Text(
            text = stringResource(R.string.library_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(HungryEarsSpacing.l))
        Button(onClick = onAction) {
            Text(text = stringResource(R.string.library_permission_action))
        }
    }
}

@Composable
private fun LibraryTab(
    section: LibrarySection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = stringResource(section.labelRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(vertical = HungryEarsSpacing.s),
        )
        HorizontalDivider(
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.width(HungryEarsSpacing.xl),
        )
    }
}

private fun canRequestPermissionInContext(context: Context): Boolean {
    val activity = context as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        AudioPermissions.readAudioPermission(),
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
            context.startActivity(it)
        }
    }
}
