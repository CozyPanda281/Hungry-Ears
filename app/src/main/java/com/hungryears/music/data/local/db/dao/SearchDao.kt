package com.hungryears.music.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.hungryears.music.data.local.db.entity.RecentQueryEntity
import com.hungryears.music.data.local.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    /**
     * FTS5 candidate retrieval. The `tracks_fts` virtual table is created outside Room (see
     * `FtsIndex`), so it must be queried raw; ranking happens in `domain/search`.
     */
    @RawQuery
    suspend fun searchTracks(query: SupportSQLiteQuery): List<TrackEntity>

    @Query("SELECT * FROM recent_queries ORDER BY searched_at DESC LIMIT :limit")
    fun observeRecentQueries(limit: Int): Flow<List<RecentQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentQuery(entry: RecentQueryEntity)

    @Query("DELETE FROM recent_queries WHERE normalized = :normalized")
    suspend fun deleteRecentQuery(normalized: String)

    @Query("DELETE FROM recent_queries")
    suspend fun clearRecentQueries()
}
