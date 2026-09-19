package com.hungryears.music.domain.search

import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.model.Track
import kotlin.math.min

/**
 * Deterministic, explainable ranking (SEARCH_SYSTEM.md §4). Pure Kotlin so the ordering is unit
 * tested without a database. All weights live here in one place so they can be tuned and the
 * tests pin the ordering.
 */
object SearchRanking {

    const val EXACT_TITLE = 100
    const val COMBINED_TITLE_ARTIST = 95
    const val EXACT_ARTIST = 90
    const val EXACT_ALBUM = 85
    const val PREFIX_TITLE = 60
    const val PREFIX_ARTIST = 50
    const val CONTAINS_TITLE = 40
    const val CONTAINS_ARTIST = 30
    const val PARTIAL_TITLE = 20
    const val PARTIAL_ARTIST = 10
    const val METADATA_RELEVANCE = 20
    const val NAME_EXACT = 100
    const val NAME_PREFIX = 60
    const val NAME_CONTAINS = 40
    const val NAME_PARTIAL = 20
    const val HISTORY_PER_PLAY = 5
    const val HISTORY_CAP = 15
    const val FAVOURITE_BONUS = 10

    private val ARTICLES = setOf("the", "a", "an")

    fun scoreTrack(
        query: String,
        track: Track,
        signals: SearchSignals = SearchSignals.None,
    ): Int {
        val tokens = TextNormalizer.tokenize(query)
        if (tokens.isEmpty()) return 0
        val normalizedQuery = tokens.joinToString(" ")
        val title = TextNormalizer.normalize(track.displayTitle)
        val artist = TextNormalizer.normalize(track.displayArtist)
        val album = TextNormalizer.normalize(track.displayAlbum)
        val genre = TextNormalizer.normalize(track.displayGenre)

        val titleCoverage = coverage(tokens, title)
        val artistCoverage = coverage(tokens, artist)

        var base = 0
        when {
            matchesExact(normalizedQuery, title) -> base = EXACT_TITLE
            matchesExact(normalizedQuery, artist) -> base = EXACT_ARTIST
            matchesExact(normalizedQuery, album) -> base = EXACT_ALBUM
            titleCoverage.anyHit && artistCoverage.anyHit &&
                titleCoverage.hitCount + artistCoverage.hitCount >= tokens.size ->
                base = COMBINED_TITLE_ARTIST

            titleCoverage.prefixMatch -> base = PREFIX_TITLE
            artistCoverage.prefixMatch -> base = PREFIX_ARTIST
            titleCoverage.fullCoverage -> base = CONTAINS_TITLE
            artistCoverage.fullCoverage -> base = CONTAINS_ARTIST
            titleCoverage.anyHit -> base = PARTIAL_TITLE
            artistCoverage.anyHit -> base = PARTIAL_ARTIST
            coverage(tokens, album).anyHit || coverage(tokens, genre).anyHit ->
                base = METADATA_RELEVANCE

            else -> return 0
        }

        val history = min(signals.playCount(track.id) * HISTORY_PER_PLAY, HISTORY_CAP)
        val favourite = if (signals.isFavourite(track.id)) FAVOURITE_BONUS else 0
        return base + history + favourite
    }

    fun rankTracks(
        query: String,
        tracks: List<Track>,
        signals: SearchSignals = SearchSignals.None,
    ): List<Track> = tracks
        .map { it to scoreTrack(query, it, signals) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<Track, Int>> { it.second }
                .thenBy { TextNormalizer.normalize(it.first.displayTitle) }
                .thenBy { it.first.id },
        )
        .map { it.first }

    fun scoreName(query: String, name: String): Int {
        val tokens = TextNormalizer.tokenize(query)
        if (tokens.isEmpty()) return 0
        val normalizedQuery = tokens.joinToString(" ")
        val field = TextNormalizer.normalize(name)
        val coverage = coverage(tokens, field)
        return when {
            matchesExact(normalizedQuery, field) -> NAME_EXACT
            coverage.prefixMatch -> NAME_PREFIX
            coverage.fullCoverage -> NAME_CONTAINS
            coverage.anyHit -> NAME_PARTIAL
            else -> 0
        }
    }

    fun rankArtists(query: String, artists: List<Artist>): List<Artist> =
        rankByName(query, artists) { it.displayName }

    fun rankAlbums(query: String, albums: List<Album>): List<Album> =
        rankByName(query, albums) { it.displayTitle }

    fun rankGenres(query: String, genres: List<Genre>): List<Genre> =
        rankByName(query, genres) { it.displayName }

    fun rankPlaylists(query: String, playlists: List<Playlist>): List<Playlist> =
        rankByName(query, playlists) { it.displayName }

    private fun <T> rankByName(query: String, items: List<T>, name: (T) -> String): List<T> = items
        .map { it to scoreName(query, name(it)) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<T, Int>> { it.second }
                .thenBy { TextNormalizer.normalize(name(it.first)) },
        )
        .map { it.first }

    private data class Coverage(
        val hitCount: Int,
        val anyHit: Boolean,
        val fullCoverage: Boolean,
        val prefixMatch: Boolean,
    )

    private fun coverage(tokens: List<String>, fieldNormalized: String): Coverage {
        if (fieldNormalized.isEmpty()) return Coverage(0, false, false, false)
        val fieldTokens = fieldNormalized.split(' ').filter { it.isNotEmpty() }
        val hitCount = tokens.count { queryToken ->
            fieldTokens.any { fieldToken ->
                fieldToken == queryToken || fieldToken.startsWith(queryToken)
            }
        }
        return Coverage(
            hitCount = hitCount,
            anyHit = hitCount > 0,
            fullCoverage = hitCount == tokens.size,
            prefixMatch = fieldNormalized.startsWith(tokens.joinToString(" ")),
        )
    }

    private fun matchesExact(normalizedQuery: String, fieldNormalized: String): Boolean {
        if (fieldNormalized == normalizedQuery) return true
        val strippedField = stripLeadingArticle(fieldNormalized)
        if (strippedField == normalizedQuery) return true
        val strippedQuery = stripLeadingArticle(normalizedQuery)
        return strippedField == strippedQuery || fieldNormalized == strippedQuery
    }

    private fun stripLeadingArticle(normalized: String): String {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.size > 1 && tokens.first() in ARTICLES) {
            return tokens.drop(1).joinToString(" ")
        }
        return normalized
    }
}
