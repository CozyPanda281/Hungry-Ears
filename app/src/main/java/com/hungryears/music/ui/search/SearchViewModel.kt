package com.hungryears.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hungryears.music.HungryEarsApplication
import com.hungryears.music.data.repository.FavouritesRepository
import com.hungryears.music.data.repository.LibraryRepository
import com.hungryears.music.data.repository.SearchRepository
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.search.SearchResults
import com.hungryears.music.player.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: SearchResults = SearchResults(query = ""),
    val recentQueries: List<String> = emptyList(),
    val favouriteIds: Set<Long> = emptySet(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val showNoResults: Boolean get() = hasQuery && !isSearching && results.isEmpty
    val hasResults: Boolean get() = hasQuery && !results.isEmpty
}

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    private val playerController: PlayerController,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val results = MutableStateFlow(SearchResults(query = ""))

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            query.collectLatest { raw ->
                val value = raw.trim()
                if (value.isBlank()) {
                    isSearching.value = false
                    results.value = SearchResults(query = "")
                    return@collectLatest
                }
                delay(SEARCH_DEBOUNCE_MS)
                isSearching.value = true
                results.value = searchRepository.search(value)
                isSearching.value = false
            }
        }
        viewModelScope.launch {
            combine(
                query,
                isSearching,
                results,
                searchRepository.observeRecentQueries(),
                favouritesRepository.observeFavouriteIds(),
            ) { currentQuery, searching, found, recent, favourites ->
                SearchUiState(
                    query = currentQuery,
                    isSearching = searching,
                    results = found,
                    recentQueries = recent,
                    favouriteIds = favourites,
                )
            }.collect { _state.value = it }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onClearQuery() {
        query.value = ""
    }

    fun onRecentSelected(value: String) {
        query.value = value
        submitQuery(value)
    }

    fun onSubmit() {
        submitQuery(query.value)
    }

    private fun submitQuery(value: String) {
        viewModelScope.launch { searchRepository.recordQuery(value) }
    }

    fun clearRecentQueries() {
        viewModelScope.launch { searchRepository.clearRecentQueries() }
    }

    fun toggleFavourite(trackId: Long) {
        viewModelScope.launch { favouritesRepository.toggle(trackId) }
    }

    fun playTracks(tracks: List<Track>, startTrackId: Long) {
        if (tracks.isEmpty()) return
        playerController.playQueue(tracks, startTrackId)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val tracks = libraryRepository.tracksForAlbum(album.albumId)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val tracks = libraryRepository.tracksForArtist(artist.name)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    fun playGenre(genre: Genre) {
        viewModelScope.launch {
            val tracks = libraryRepository.tracksForGenre(genre.name)
            if (tracks.isNotEmpty()) playerController.playQueue(tracks, tracks.first().id)
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as HungryEarsApplication
                SearchViewModel(
                    app.container.searchRepository,
                    app.container.libraryRepository,
                    app.container.playerController,
                    app.container.favouritesRepository,
                )
            }
        }
    }
}
