package com.hungryears.music.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hungryears.music.R
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.model.Track
import com.hungryears.music.ui.components.AlbumRow
import com.hungryears.music.ui.components.ArtistRow
import com.hungryears.music.ui.components.EmptyState
import com.hungryears.music.ui.components.GenreRow
import com.hungryears.music.ui.components.PlaylistRow
import com.hungryears.music.ui.components.ScreenHeader
import com.hungryears.music.ui.components.TrackRow
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onOpenPlaylist: (Long) -> Unit = {},
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreenContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onRecentSelected = viewModel::onRecentSelected,
        onClearRecent = viewModel::clearRecentQueries,
        onSubmit = viewModel::onSubmit,
        onPlayTracks = viewModel::playTracks,
        onPlayAlbum = viewModel::playAlbum,
        onPlayArtist = viewModel::playArtist,
        onPlayGenre = viewModel::playGenre,
        onToggleFavourite = viewModel::toggleFavourite,
        onOpenPlaylist = onOpenPlaylist,
        modifier = modifier,
    )
}

@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRecentSelected: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSubmit: () -> Unit,
    onPlayTracks: (List<Track>, Long) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(HungryEarsSpacing.xl),
        ) {
            ScreenHeader(title = stringResource(R.string.tab_search))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(text = stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.search_recent_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HungryEarsSpacing.m),
            )

            when {
                !state.hasQuery -> RecentQueries(
                    recentQueries = state.recentQueries,
                    onSelect = onRecentSelected,
                    onClear = onClearRecent,
                )

                state.isSearching && !state.hasResults -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = HungryEarsSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                state.showNoResults -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_no_results_title),
                    body = stringResource(R.string.search_no_results_body, state.query.trim()),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = HungryEarsSpacing.xxl),
                )

                else -> SearchResultsList(
                    state = state,
                    onPlayTracks = onPlayTracks,
                    onPlayAlbum = onPlayAlbum,
                    onPlayArtist = onPlayArtist,
                    onPlayGenre = onPlayGenre,
                    onToggleFavourite = onToggleFavourite,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }
    }
}

@Composable
private fun RecentQueries(
    recentQueries: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recentQueries.isEmpty()) {
        Text(
            text = stringResource(R.string.search_empty_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = HungryEarsSpacing.l),
        )
        return
    }
    Column(modifier = Modifier.padding(top = HungryEarsSpacing.l)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.search_recent_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text(text = stringResource(R.string.search_recent_clear))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(HungryEarsSpacing.s)) {
            items(recentQueries, key = { it }) { query ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(query) },
                    label = { Text(text = query) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState,
    onPlayTracks: (List<Track>, Long) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val results = state.results
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = HungryEarsSpacing.m),
        verticalArrangement = Arrangement.spacedBy(HungryEarsSpacing.xs),
    ) {
        if (results.tracks.isNotEmpty()) {
            item(key = "tracks-header") {
                SearchSectionHeader(title = stringResource(R.string.search_section_tracks))
            }
            items(results.tracks, key = { "track-${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isFavourite = track.id in state.favouriteIds,
                    onClick = { onPlayTracks(results.tracks, track.id) },
                    onToggleFavourite = { onToggleFavourite(track.id) },
                )
            }
        }
        if (results.artists.isNotEmpty()) {
            item(key = "artists-header") {
                SearchSectionHeader(title = stringResource(R.string.search_section_artists))
            }
            items(results.artists, key = { "artist-${it.name}" }) { artist ->
                ArtistRow(artist = artist, onClick = { onPlayArtist(artist) })
            }
        }
        if (results.albums.isNotEmpty()) {
            item(key = "albums-header") {
                SearchSectionHeader(title = stringResource(R.string.search_section_albums))
            }
            items(results.albums, key = { "album-${it.albumId ?: it.title}" }) { album ->
                AlbumRow(album = album, onClick = { onPlayAlbum(album) })
            }
        }
        if (results.genres.isNotEmpty()) {
            item(key = "genres-header") {
                SearchSectionHeader(title = stringResource(R.string.search_section_genres))
            }
            items(results.genres, key = { "genre-${it.name}" }) { genre ->
                GenreRow(genre = genre, onClick = { onPlayGenre(genre) })
            }
        }
        if (results.playlists.isNotEmpty()) {
            item(key = "playlists-header") {
                SearchSectionHeader(title = stringResource(R.string.search_section_playlists))
            }
            items(results.playlists, key = { "playlist-${it.id}" }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = HungryEarsSpacing.m, bottom = HungryEarsSpacing.xs),
    )
}
