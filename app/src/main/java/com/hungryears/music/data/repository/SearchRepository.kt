package com.hungryears.music.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.local.db.entity.RecentQueryEntity
import com.hungryears.music.data.toDomain
import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.search.SearchRanking
import com.hungryears.music.domain.search.SearchResults
import com.hungryears.music.domain.search.SearchSignals
import com.hungryears.music.domain.search.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Retrieval + ranking for unified search. FTS5 supplies candidates (fast, prefix-aware), then the
 * pure [SearchRanking] decides the order. Local-only in PHASE 5; the online fuse socket arrives
 * with PHASE 9.
 */
class SearchRepository(database: HungryEarsDatabase) {

    private val searchDao = database.searchDao()
    private val libraryDao = database.libraryDao()
    private val playlistDao = database.playlistDao()

    fun observeRecentQueries(limit: Int = RECENT_QUERY_LIMIT): Flow<List<String>> =
        searchDao.observeRecentQueries(limit).map { rows -> rows.map { it.query } }

    suspend fun recordQuery(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < MIN_RECORDED_QUERY_LENGTH) return
        searchDao.upsertRecentQuery(
            RecentQueryEntity(
                query = query,
                normalized = TextNormalizer.normalize(query),
                searchedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearRecentQueries() = searchDao.clearRecentQueries()

    suspend fun search(rawQuery: String): SearchResults = withContext(Dispatchers.IO) {
        val query = TextNormalizer.normalize(rawQuery)
        if (query.isBlank()) return@withContext SearchResults(query = rawQuery.trim())
        val matchExpression = buildMatchExpression(query)
        if (matchExpression == null) return@withContext SearchResults(query = rawQuery.trim())

        val candidates = searchDao
            .searchTracks(SimpleSQLiteQuery(CANDIDATE_SQL, arrayOf<Any?>(matchExpression, CANDIDATE_LIMIT)))
            .map { it.toDomain() }
        val signals = loadSignals()

        SearchResults(
            query = rawQuery.trim(),
            tracks = SearchRanking.rankTracks(query, candidates, signals),
            artists = SearchRanking.rankArtists(
                query,
                libraryDao.getArtists().map { Artist(it.name, it.trackCount, it.albumCount) },
            ),
            albums = SearchRanking.rankAlbums(
                query,
                libraryDao.getAlbums().map {
                    Album(it.albumId, it.title, it.artist, it.trackCount, it.totalDurationMs, it.year)
                },
            ),
            genres = SearchRanking.rankGenres(
                query,
                libraryDao.getGenres().map { Genre(it.name, it.trackCount) },
            ),
            playlists = SearchRanking.rankPlaylists(
                query,
                playlistDao.getPlaylists().map {
                    Playlist(it.id, it.name, it.createdAt, it.updatedAt, it.trackCount)
                },
            ),
        )
    }

    private suspend fun loadSignals(): SearchSignals {
        val favourites = libraryDao.getFavouriteIds().toHashSet()
        val playCounts = libraryDao.getPlayCounts().associate { it.trackId to it.playCount }
        return object : SearchSignals {
            override fun playCount(trackId: Long): Int = playCounts[trackId] ?: 0
            override fun isFavourite(trackId: Long): Boolean = trackId in favourites
        }
    }

    /** Builds an FTS5 `token* OR token*` expression, or null when nothing searchable remains. */
    private fun buildMatchExpression(normalizedQuery: String): String? {
        val tokens = normalizedQuery
            .split(' ')
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" OR ") { "$it*" }
    }

    private companion object {
        const val CANDIDATE_LIMIT = 300
        const val RECENT_QUERY_LIMIT = 8
        const val MIN_RECORDED_QUERY_LENGTH = 2
        const val CANDIDATE_SQL =
            "SELECT t.* FROM tracks_fts JOIN tracks t ON t.id = tracks_fts.rowid " +
                "WHERE tracks_fts MATCH ? ORDER BY bm25(tracks_fts) LIMIT ?"
    }
}
