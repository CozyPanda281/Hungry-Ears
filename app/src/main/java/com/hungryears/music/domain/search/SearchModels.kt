package com.hungryears.music.domain.search

import com.hungryears.music.domain.model.Album
import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Genre
import com.hungryears.music.domain.model.Playlist
import com.hungryears.music.domain.model.Track

enum class SearchCategory { TRACKS, ARTISTS, ALBUMS, GENRES, PLAYLISTS }

/** Ranking signals that come from user behaviour rather than the track's own metadata. */
interface SearchSignals {
    fun playCount(trackId: Long): Int
    fun isFavourite(trackId: Long): Boolean

    companion object {
        val None: SearchSignals = object : SearchSignals {
            override fun playCount(trackId: Long): Int = 0
            override fun isFavourite(trackId: Long): Boolean = false
        }
    }
}

data class SearchResults(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() &&
            artists.isEmpty() &&
            albums.isEmpty() &&
            genres.isEmpty() &&
            playlists.isEmpty()
}
