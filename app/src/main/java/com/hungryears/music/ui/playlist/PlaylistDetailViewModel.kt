package com.hungryears.music.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hungryears.music.HungryEarsApplication
import com.hungryears.music.data.repository.FavouritesRepository
import com.hungryears.music.data.repository.PlaylistRepository
import com.hungryears.music.domain.model.Track
import com.hungryears.music.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlistId: Long = 0L,
    val name: String = "",
    val tracks: List<Track> = emptyList(),
    val favouriteIds: Set<Long> = emptySet(),
    val isLoaded: Boolean = false,
    val isDeleted: Boolean = false,
)

class PlaylistDetailViewModel(
    private val playlistRepository: PlaylistRepository,
    private val favouritesRepository: FavouritesRepository,
    private val playerController: PlayerController,
    private val playlistId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistDetailUiState(playlistId = playlistId))
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    private var playlistSeen = false

    init {
        viewModelScope.launch {
            combine(
                playlistRepository.observePlaylistTracks(playlistId),
                favouritesRepository.observeFavouriteIds(),
            ) { tracks, favourites -> tracks to favourites }
                .collect { (tracks, favourites) ->
                    _state.update {
                        it.copy(tracks = tracks, favouriteIds = favourites, isLoaded = true)
                    }
                }
        }
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                val playlist = playlists.firstOrNull { it.id == playlistId }
                when {
                    playlist != null -> {
                        playlistSeen = true
                        _state.update { it.copy(name = playlist.displayName) }
                    }

                    playlistSeen -> _state.update { it.copy(isDeleted = true) }
                }
            }
        }
    }

    fun playAll() {
        val tracks = _state.value.tracks
        if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
    }

    fun playTrack(trackId: Long) {
        val tracks = _state.value.tracks
        if (tracks.isNotEmpty()) playerController.playQueue(tracks, trackId)
    }

    fun toggleFavourite(trackId: Long) {
        viewModelScope.launch { favouritesRepository.toggle(trackId) }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch { playlistRepository.removeFromPlaylist(playlistId, trackId) }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepository.move(playlistId, fromIndex, toIndex) }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.renamePlaylist(playlistId, name) }
    }

    fun deletePlaylist() {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }

    companion object {
        fun factory(playlistId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as HungryEarsApplication
                PlaylistDetailViewModel(
                    app.container.playlistRepository,
                    app.container.favouritesRepository,
                    app.container.playerController,
                    playlistId,
                )
            }
        }
    }
}
