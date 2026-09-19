package com.hungryears.music.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hungryears.music.HungryEarsApplication
import com.hungryears.music.R
import com.hungryears.music.data.repository.FavouritesRepository
import com.hungryears.music.data.repository.LibraryRepository
import com.hungryears.music.data.repository.LibraryScanState
import com.hungryears.music.data.repository.PlaylistRepository
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Folder
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.model.Track
import com.hungryears.music.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class LibrarySection(@StringRes val labelRes: Int) {
    Tracks(R.string.library_section_tracks),
    Albums(R.string.library_section_albums),
    Artists(R.string.library_section_artists),
    Genres(R.string.library_section_genres),
    Folders(R.string.library_section_folders),
}

data class LibraryUiState(
    val section: LibrarySection = LibrarySection.Tracks,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favouriteIds: Set<Long> = emptySet(),
    val isScanning: Boolean = false,
    val message: LibraryMessage? = null,
)

sealed interface LibraryMessage {
    data class AddedToPlaylist(val name: String) : LibraryMessage
    data class AlreadyInPlaylist(val name: String) : LibraryMessage
}

private data class LibrarySections(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>,
    val folders: List<Folder>,
)

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val playerController: PlayerController,
    private val favouritesRepository: FavouritesRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        val sections = combine(
            repository.observeTracks(),
            repository.observeAlbums(),
            repository.observeArtists(),
            repository.observeGenres(),
            repository.observeFolders(),
        ) { tracks, albums, artists, genres, folders ->
            LibrarySections(tracks, albums, artists, genres, folders)
        }
        viewModelScope.launch {
            combine(
                sections,
                repository.scanState,
                favouritesRepository.observeFavouriteIds(),
                playlistRepository.observePlaylists(),
            ) { content, scanState, favouriteIds, playlists ->
                LibraryUiState(
                    section = _state.value.section,
                    tracks = content.tracks,
                    albums = content.albums,
                    artists = content.artists,
                    genres = content.genres,
                    folders = content.folders,
                    playlists = playlists,
                    favouriteIds = favouriteIds,
                    isScanning = scanState is LibraryScanState.Scanning,
                )
            }.collect { _state.value = it }
        }
    }

    fun selectSection(section: LibrarySection) {
        _state.value = _state.value.copy(section = section)
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun toggleFavourite(trackId: Long) {
        viewModelScope.launch { favouritesRepository.toggle(trackId) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long, playlistName: String) {
        viewModelScope.launch {
            val added = playlistRepository.addToPlaylist(playlistId, trackId)
            _state.value = _state.value.copy(
                message = if (added) {
                    LibraryMessage.AddedToPlaylist(playlistName)
                } else {
                    LibraryMessage.AlreadyInPlaylist(playlistName)
                },
            )
        }
    }

    fun createPlaylistAndAdd(name: String, trackId: Long) {
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            playlistRepository.addToPlaylist(playlistId, trackId)
            _state.value = _state.value.copy(
                message = LibraryMessage.AddedToPlaylist(name.trim()),
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun playTracks(tracks: List<Track>, startTrackId: Long) {
        if (tracks.isEmpty()) return
        playerController.playQueue(tracks, startTrackId)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val tracks = repository.tracksForAlbum(album.albumId)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val tracks = repository.tracksForArtist(artist.name)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    fun playGenre(genre: Genre) {
        viewModelScope.launch {
            val tracks = repository.tracksForGenre(genre.name)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    fun playFolder(folder: Folder) {
        viewModelScope.launch {
            val tracks = repository.tracksForFolder(folder.path)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as HungryEarsApplication
                LibraryViewModel(
                    app.container.libraryRepository,
                    app.container.playerController,
                    app.container.favouritesRepository,
                    app.container.playlistRepository,
                )
            }
        }
    }
}
