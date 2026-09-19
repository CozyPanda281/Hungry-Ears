package com.hungryears.music.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.hungryears.music.data.local.db.entity.FavouriteEntity
import com.hungryears.music.data.local.db.entity.HistoryEntity
import com.hungryears.music.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class AlbumRow(
    val albumId: Long?,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val year: Int,
)

data class ArtistRow(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)

data class GenreRow(
    val name: String,
    val trackCount: Int,
)

data class FolderRow(
    val path: String,
    val trackCount: Int,
)

data class PlayCountRow(
    val trackId: Long,
    val playCount: Int,
)

@Dao
interface LibraryDao {

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("SELECT id FROM tracks")
    suspend fun getAllTrackIds(): List<Long>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun countTracks(): Int

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE album_id IS :albumId
        ORDER BY track_number, title COLLATE NOCASE
        """,
    )
    suspend fun getTracksForAlbum(albumId: Long?): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE artist = :artist
        ORDER BY album_title COLLATE NOCASE, track_number, title COLLATE NOCASE
        """,
    )
    suspend fun getTracksForArtist(artist: String): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE genre = :genre
        ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE
        """,
    )
    suspend fun getTracksForGenre(genre: String): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE folder_path = :path
        ORDER BY title COLLATE NOCASE
        """,
    )
    suspend fun getTracksForFolder(path: String): List<TrackEntity>

    @Query(
        """
        SELECT album_id AS albumId, MAX(album_title) AS title, MAX(artist) AS artist,
               COUNT(*) AS trackCount, SUM(duration_ms) AS totalDurationMs, MAX(year) AS year
        FROM tracks
        GROUP BY album_id
        ORDER BY title COLLATE NOCASE
        """,
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT album_id AS albumId, MAX(album_title) AS title, MAX(artist) AS artist,
               COUNT(*) AS trackCount, SUM(duration_ms) AS totalDurationMs, MAX(year) AS year
        FROM tracks
        GROUP BY album_id
        ORDER BY title COLLATE NOCASE
        """,
    )
    suspend fun getAlbums(): List<AlbumRow>

    @Query(
        """
        SELECT artist AS name, COUNT(*) AS trackCount, COUNT(DISTINCT album_id) AS albumCount
        FROM tracks
        GROUP BY artist
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeArtists(): Flow<List<ArtistRow>>

    @Query(
        """
        SELECT artist AS name, COUNT(*) AS trackCount, COUNT(DISTINCT album_id) AS albumCount
        FROM tracks
        GROUP BY artist
        ORDER BY name COLLATE NOCASE
        """,
    )
    suspend fun getArtists(): List<ArtistRow>

    @Query(
        """
        SELECT genre AS name, COUNT(*) AS trackCount
        FROM tracks
        GROUP BY genre
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeGenres(): Flow<List<GenreRow>>

    @Query(
        """
        SELECT genre AS name, COUNT(*) AS trackCount
        FROM tracks
        GROUP BY genre
        ORDER BY name COLLATE NOCASE
        """,
    )
    suspend fun getGenres(): List<GenreRow>

    @Query(
        """
        SELECT folder_path AS path, COUNT(*) AS trackCount
        FROM tracks
        GROUP BY folder_path
        ORDER BY path COLLATE NOCASE
        """,
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE track_id = :trackId")
    suspend fun removeFavourite(trackId: Long)

    @Query("SELECT track_id FROM favourites ORDER BY added_at DESC")
    fun observeFavouriteIds(): Flow<List<Long>>

    @Query("SELECT track_id FROM favourites")
    suspend fun getFavouriteIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE track_id = :trackId)")
    suspend fun isFavourite(trackId: Long): Boolean

    @Query(
        """
        SELECT track_id AS trackId, COUNT(*) AS playCount FROM history
        WHERE event_type IN ('PLAY', 'COMPLETE', 'REPLAY')
        GROUP BY track_id
        """,
    )
    suspend fun getPlayCounts(): List<PlayCountRow>

    @Insert
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int = 100): Flow<List<HistoryEntity>>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun countHistory(): Int
}