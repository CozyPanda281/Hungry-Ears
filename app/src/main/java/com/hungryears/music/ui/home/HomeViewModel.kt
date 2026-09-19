package com.hungryears.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hungryears.music.HungryEarsApplication
import com.hungryears.music.data.repository.LibraryRepository
import com.hungryears.music.data.repository.RecommendationRepository
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.recommendation.HomeSection
import com.hungryears.music.domain.recommendation.HomeSectionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val continueListening: List<Track> = emptyList(),
    val madeForYou: List<Track> = emptyList(),
    val favouriteArtists: List<Artist> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
)

class HomeViewModel(
    private val recommendationRepository: RecommendationRepository,
    libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            recommendationRepository
                .observeHomeSections(
                    favouriteTrackIds = libraryRepository.observeFavouriteIds(),
                    tracks = libraryRepository.observeTracks(),
                )
                .collect { sections -> _state.value = sections.toUiState() }
        }
    }

    private fun List<HomeSection>.toUiState(): HomeUiState {
        var continueListening = emptyList<Track>()
        var madeForYou = emptyList<Track>()
        var favouriteArtists = emptyList<Artist>()
        var recentlyAdded = emptyList<Track>()
        forEach { section ->
            when (section.id) {
                HomeSectionId.CONTINUE_LISTENING -> continueListening = section.tracks
                HomeSectionId.MADE_FOR_YOU -> madeForYou = section.tracks
                HomeSectionId.FAVOURITE_ARTISTS -> favouriteArtists = section.artists
                HomeSectionId.RECENTLY_ADDED -> recentlyAdded = section.tracks
            }
        }
        return HomeUiState(
            continueListening = continueListening,
            madeForYou = madeForYou,
            favouriteArtists = favouriteArtists,
            recentlyAdded = recentlyAdded,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as HungryEarsApplication
                HomeViewModel(
                    recommendationRepository = app.container.recommendationRepository,
                    libraryRepository = app.container.libraryRepository,
                )
            }
        }
    }
}
