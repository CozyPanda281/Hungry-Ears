package com.hungryears.music.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hungryears.music.data.local.db.entity.PlaylistEntity
import com.hungryears.music.data.local.db.entity.PlaylistTrackEntity
import com.hungryears.music.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS createdAt, p.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS trackCount
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS createdAt, p.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS trackCount
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    suspend fun getPlaylists(): List<PlaylistSummaryRow>

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun containsTrack(playlistId: Long, trackId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        JOIN tracks t ON t.id = pt.track_id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position
        """,
    )
    suspend fun getPlaylistTracks(playlistId: Long): List<TrackEntity>

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        JOIN tracks t ON t.id = pt.track_id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position
        """,
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    /** Rewrites the whole playlist order atomically; personal-scale playlists make this cheap. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>, updatedAt: Long) {
        clearTracks(playlistId)
        orderedTrackIds.forEachIndexed { index, trackId ->
            insertEntry(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    position = index,
                    trackId = trackId,
                    addedAt = updatedAt,
                ),
            )
        }
        touchPlaylist(playlistId, updatedAt)
    }

    @Transaction
    suspend fun appendTrack(playlistId: Long, trackId: Long, addedAt: Long): Boolean {
        if (containsTrack(playlistId, trackId) > 0) return false
        val nextPosition = (maxPosition(playlistId) ?: -1) + 1
        insertEntry(
            PlaylistTrackEntity(
                playlistId = playlistId,
                position = nextPosition,
                trackId = trackId,
                addedAt = addedAt,
            ),
        )
        touchPlaylist(playlistId, addedAt)
        return true
    }
}
