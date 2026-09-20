package com.hungryears.music.di

import android.content.Context
import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.permission.AudioPermissions
import com.hungryears.music.data.repository.FavouritesRepository
import com.hungryears.music.data.repository.HistoryRepository
import com.hungryears.music.data.repository.LibraryRepository
import com.hungryears.music.data.repository.PlaylistRepository
import com.hungryears.music.data.repository.RecommendationRepository
import com.hungryears.music.data.repository.SearchRepository
import com.hungryears.music.data.scanner.MediaStoreScanner
import com.hungryears.music.player.PlaybackHistoryRecorder
import com.hungryears.music.player.PlayerController

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val database by lazy { HungryEarsDatabase.build(appContext) }

    private val mediaStoreScanner by lazy {
        MediaStoreScanner(appContext, AudioPermissions::hasReadAudioPermission)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(appContext, database, mediaStoreScanner)
            .also { it.startWatchingMediaStore() }
    }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database) }

    val favouritesRepository: FavouritesRepository by lazy { FavouritesRepository(database) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(database) }

    val searchRepository: SearchRepository by lazy { SearchRepository(database) }

    val recommendationRepository: RecommendationRepository by lazy {
        RecommendationRepository(libraryDao = database.libraryDao())
    }

    val playerController: PlayerController by lazy {
        PlayerController(appContext)
    }

    private val playbackHistoryRecorder: PlaybackHistoryRecorder by lazy {
        PlaybackHistoryRecorder(playerController, historyRepository).also { it.start() }
    }

    init {
        playbackHistoryRecorder
    }
}
