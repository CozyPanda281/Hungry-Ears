package com.hungryears.music.data.repository

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.permission.AudioPermissions
import com.hungryears.music.data.scanner.MediaStoreScanner
import com.hungryears.music.data.scanner.ScanOutcome
import com.hungryears.music.data.toDomain
import com.hungryears.music.data.toEntity
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Folder
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanCounts(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val unsupported: Int,
    val errorCount: Int,
)

sealed interface LibraryScanState {
    data object Idle : LibraryScanState
    data object Scanning : LibraryScanState
    data object PermissionDenied : LibraryScanState
    data class Completed(val result: ScanCounts) : LibraryScanState
    data class Failed(val message: String) : LibraryScanState
}

class LibraryRepository(
    private val context: Context,
    database: HungryEarsDatabase,
    private val scanner: MediaStoreScanner,
) {
    private val dao = database.libraryDao()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _scanState = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    val scanState: StateFlow<LibraryScanState> = _scanState.asStateFlow()

    fun observeTracks(): Flow<List<Track>> = dao.observeTracks().map { list -> list.map { it.toDomain() } }

    fun observeAlbums(): Flow<List<Album>> = dao.observeAlbums().map { rows ->
        rows.map { Album(it.albumId, it.title, it.artist, it.trackCount, it.totalDurationMs, it.year) }
    }

    fun observeArtists(): Flow<List<Artist>> = dao.observeArtists().map { rows ->
        rows.map { Artist(it.name, it.trackCount, it.albumCount) }
    }

    fun observeGenres(): Flow<List<Genre>> = dao.observeGenres().map { rows ->
        rows.map { Genre(it.name, it.trackCount) }
    }

    fun observeFolders(): Flow<List<Folder>> = dao.observeFolders().map { rows ->
        rows.map { Folder(it.path, it.trackCount) }
    }

    suspend fun tracksForAlbum(albumId: Long?): List<Track> =
        withContext(Dispatchers.IO) { dao.getTracksForAlbum(albumId).map { it.toDomain() } }

    suspend fun tracksForArtist(artist: String): List<Track> =
        withContext(Dispatchers.IO) { dao.getTracksForArtist(artist).map { it.toDomain() } }

    suspend fun tracksForGenre(genre: String): List<Track> =
        withContext(Dispatchers.IO) { dao.getTracksForGenre(genre).map { it.toDomain() } }

    suspend fun tracksForFolder(path: String): List<Track> =
        withContext(Dispatchers.IO) { dao.getTracksForFolder(path).map { it.toDomain() } }

    suspend fun refresh() {
        if (!AudioPermissions.hasReadAudioPermission(context)) {
            _scanState.value = LibraryScanState.PermissionDenied
            return
        }
        if (_scanState.value is LibraryScanState.Scanning) return
        _scanState.value = LibraryScanState.Scanning
        val outcome: ScanOutcome = scanner.scan()
        if (outcome.permissionDenied) {
            _scanState.value = LibraryScanState.PermissionDenied
            return
        }
        if (outcome.tracks.isEmpty() && outcome.errors.isNotEmpty()) {
            _scanState.value = LibraryScanState.Failed(outcome.errors.first())
            return
        }
        val counts = withContext(Dispatchers.IO) {
            val existingIds = dao.getAllTrackIds()
            val scannedIds = outcome.tracks.map { it.id }.toSet()
            val toDelete = existingIds.filterNot { it in scannedIds }
            dao.upsertTracks(outcome.tracks.map { it.toEntity() })
            dao.deleteTracksByIds(toDelete)
            ScanCounts(
                added = scannedIds.size - existingIds.count { it in scannedIds },
                updated = existingIds.count { it in scannedIds },
                removed = toDelete.size,
                unsupported = outcome.unsupportedCount,
                errorCount = outcome.errors.size,
            )
        }
        _scanState.value = LibraryScanState.Completed(counts)
    }

    fun startWatchingMediaStore() {
        if (observerRegistered) return
        context.applicationContext.contentResolver
            .registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        observerRegistered = true
    }

    private var observerRegistered = false
    private var refreshJob: Job? = null

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = applicationScope.launch {
                delay(OBSERVER_DEBOUNCE_MS)
                refresh()
            }
        }
    }

    private companion object {
        const val OBSERVER_DEBOUNCE_MS = 1_500L
    }
}