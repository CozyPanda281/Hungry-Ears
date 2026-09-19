package com.hungryears.music.domain.model

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"
const val UNKNOWN_GENRE = "Unknown genre"

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val albumId: Long?,
    val genre: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val folderPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModifiedMs: Long,
    val contentUri: String,
    val container: String,
    val isSupported: Boolean,
) {
    val displayArtist: String get() = artist.ifBlank { UNKNOWN_ARTIST }
    val displayAlbum: String get() = albumTitle.ifBlank { UNKNOWN_ALBUM }
    val displayGenre: String get() = genre.ifBlank { UNKNOWN_GENRE }
    val displayTitle: String get() = title.ifBlank { fileName }
}

data class Album(
    val albumId: Long?,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val year: Int,
) {
    val displayTitle: String get() = title.ifBlank { UNKNOWN_ALBUM }
    val displayArtist: String get() = artist.ifBlank { UNKNOWN_ARTIST }
}

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
) {
    val displayName: String get() = name.ifBlank { UNKNOWN_ARTIST }
}

data class Genre(
    val name: String,
    val trackCount: Int,
) {
    val displayName: String get() = name.ifBlank { UNKNOWN_GENRE }
}

data class Folder(
    val path: String,
    val trackCount: Int,
) {
    val displayName: String get() = path.substringAfterLast('/').ifBlank { path }
}

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
) {
    val displayName: String get() = name.ifBlank { UNKNOWN_PLAYLIST }
}

const val UNKNOWN_PLAYLIST = "Untitled playlist"

enum class PlaybackEventType {
    PLAY,
    COMPLETE,
    SKIP,
    REPLAY,
}