package com.hungryears.music.ui.playerbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hungryears.music.HungryEarsApplication
import com.hungryears.music.player.PlayerController
import com.hungryears.music.player.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

class MiniPlayerViewModel(
    private val playerController: PlayerController,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = playerController.state

    fun togglePlayPause() = playerController.togglePlayPause()

    fun next() = playerController.next()

    fun previous() = playerController.previous()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun cycleRepeat() = playerController.cycleRepeatMode()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as HungryEarsApplication
                MiniPlayerViewModel(app.container.playerController)
            }
        }
    }
}
