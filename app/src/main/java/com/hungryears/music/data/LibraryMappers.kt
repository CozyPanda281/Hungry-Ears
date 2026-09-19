package com.hungryears.music.data

import com.hungryears.music.data.local.db.entity.TrackEntity
import com.hungryears.music.data.scanner.ScannedTrack
import com.hungryears.music.domain.model.Track

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    albumId = albumId,
    genre = genre,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    folderPath = folderPath,
    fileName = fileName,
    sizeBytes = sizeBytes,
    dateAdded = dateAdded,
    dateModifiedMs = dateModifiedMs,
    contentUri = contentUri,
    container = container,
    isSupported = isSupported,
)

fun ScannedTrack.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    albumId = albumId,
    genre = genre,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    folderPath = folderPath,
    fileName = fileName,
    sizeBytes = sizeBytes,
    dateAdded = dateAdded,
    dateModifiedMs = dateModifiedMs,
    contentUri = contentUri,
    container = container,
    isSupported = isSupported,
)