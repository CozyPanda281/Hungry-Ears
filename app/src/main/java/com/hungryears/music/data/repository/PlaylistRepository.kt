package com.hungryears.music.data.repository

import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.local.db.dao.PlaylistSummaryRow
import com.hungryears.music.data.local.db.entity.PlaylistEntity
import com.hungryears.music.data.toDomain
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepository(database: HungryEarsDatabase) {

    private val dao = database.playlistDao()

    fun observePlaylists(): Flow<List<Playlist>> =
        dao.observePlaylists().map { rows -> rows.map { it.toDomain() } }

    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        dao.observePlaylistTracks(playlistId).map { rows -> rows.map { it.toDomain() } }

    suspend fun playlistTracks(playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        dao.getPlaylistTracks(playlistId).map { it.toDomain() }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifBlank { DEFAULT_PLAYLIST_NAME },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        dao.renamePlaylist(playlistId, name.trim().ifBlank { DEFAULT_PLAYLIST_NAME }, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    /** Returns false when the track is already in the playlist. */
    suspend fun addToPlaylist(playlistId: Long, trackId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.appendTrack(playlistId, trackId, System.currentTimeMillis())
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        dao.removeTrack(playlistId, trackId)
    }

    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.reorder(playlistId, orderedTrackIds, System.currentTimeMillis())
    }

    suspend fun move(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val current = dao.getPlaylistTracks(playlistId).map { it.id }.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return@withContext
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        dao.reorder(playlistId, current, System.currentTimeMillis())
    }

    private fun PlaylistSummaryRow.toDomain(): Playlist =
        Playlist(id, name, createdAt, updatedAt, trackCount)

    private companion object {
        const val DEFAULT_PLAYLIST_NAME = "New playlist"
    }
}
