package com.hungryears.music.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.hungryears.music.data.format.SupportedFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedTrack(
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
)

data class ScanOutcome(
    val permissionDenied: Boolean = false,
    val tracks: List<ScannedTrack> = emptyList(),
    val unsupportedCount: Int = 0,
    val errors: List<String> = emptyList(),
) {
    companion object {
        fun permissionDenied() = ScanOutcome(permissionDenied = true)
    }
}

class MediaStoreScanner(
    private val context: Context,
    private val hasPermission: (Context) -> Boolean,
) {

    suspend fun scan(): ScanOutcome = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext ScanOutcome.permissionDenied()
        scanInternal()
    }

    private fun scanInternal(): ScanOutcome {
        val resolver = context.contentResolver
        return try {
            val genreById = loadGenreMap(resolver)
            val errors = mutableListOf<String>()
            val tracks = queryAudio(resolver, genreById, errors)
            ScanOutcome(
                tracks = tracks,
                unsupportedCount = tracks.count { !it.isSupported },
                errors = errors,
            )
        } catch (e: Exception) {
            ScanOutcome(errors = listOf(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun queryAudio(
        resolver: ContentResolver,
        genreById: Map<Long, String>,
        errors: MutableList<String>,
    ): List<ScannedTrack> {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val tracks = mutableListOf<ScannedTrack>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        try {
            resolver.query(uri, projection.toTypedArray(), selection, null, sortOrder)
                ?.use { cursor ->
                    val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        -1
                    }
                    while (cursor.moveToNext()) {
                        val id = cursor.long(MediaStore.Audio.Media._ID)
                        val fileName = cursor.str(MediaStore.Audio.Media.DISPLAY_NAME)
                        val data = cursor.str(MediaStore.Audio.Media.DATA)
                        val relativePath = if (relativePathCol >= 0) {
                            cursor.orEmptyString(relativePathCol)
                        } else {
                            ""
                        }
                        val container = SupportedFormats.containerOf(fileName)
                        val durationMs = resolveDurationMs(
                            data = data,
                            contentUri = uri.buildUpon().appendPath(id.toString()).build().toString(),
                            queryDuration = cursor.long(MediaStore.Audio.Media.DURATION),
                            errorSink = errors,
                        )
                        tracks += ScannedTrack(
                            id = id,
                            title = cursor.str(MediaStore.Audio.Media.TITLE),
                            artist = cursor.str(MediaStore.Audio.Media.ARTIST),
                            albumTitle = cursor.str(MediaStore.Audio.Media.ALBUM),
                            albumId = cursor.longOrNull(MediaStore.Audio.Media.ALBUM_ID),
                            genre = genreById[id].orEmpty(),
                            durationMs = durationMs,
                            trackNumber = cursor.int(MediaStore.Audio.Media.TRACK),
                            year = cursor.int(MediaStore.Audio.Media.YEAR),
                            folderPath = folderOf(data, relativePath),
                            fileName = fileName,
                            sizeBytes = cursor.long(MediaStore.Audio.Media.SIZE),
                            dateAdded = cursor.long(MediaStore.Audio.Media.DATE_ADDED),
                            dateModifiedMs = cursor.long(MediaStore.Audio.Media.DATE_MODIFIED),
                            contentUri = MediaStore.Audio.Media
                                .getContentUri(VOLUME_EXTERNAL)
                                .buildUpon()
                                .appendPath(id.toString())
                                .build()
                                .toString(),
                            container = container,
                            isSupported = SupportedFormats.isSupported(container),
                        )
                    }
                }
        } catch (e: Exception) {
            errors += "MediaStore query failed: ${e.message ?: e.javaClass.simpleName}"
        }
        return tracks
    }

    private fun resolveDurationMs(
        data: String,
        contentUri: String,
        queryDuration: Long,
        errorSink: MutableList<String>,
    ): Long {
        if (queryDuration > 0L) return queryDuration
        val readableTarget = if (data.isNotBlank()) data else contentUri
        if (readableTarget.isBlank()) return 0L
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(if (readableTarget.startsWith("content://")) readableTarget else "file://$readableTarget")
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrElse {
            errorSink += "Duration read failed for $readableTarget"
            0L
        }
    }

    private fun loadGenreMap(resolver: ContentResolver): Map<Long, String> {
        val result = HashMap<Long, String>()
        val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        try {
            resolver.query(genresUri, null, null, null, null)?.use { genres ->
                val idCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameCol = genres.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(idCol)
                    val name = genres.orEmptyString(nameCol)
                    val membersUri = MediaStore.Audio.Genres.Members
                        .getContentUri(VOLUME_EXTERNAL, genreId)
                    resolver.query(membersUri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                        ?.use { members ->
                            val audioIdCol = members.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            while (members.moveToNext()) {
                                result.putIfAbsent(members.getLong(audioIdCol), name)
                            }
                        }
                }
            }
        } catch (_: Exception) {
            // Genres are best-effort; a failure must not abort the whole scan.
        }
        return result
    }
}

private const val VOLUME_EXTERNAL = "external"

private fun folderOf(data: String, relativePath: String): String {    val index = data.lastIndexOf('/')
    if (index > 0) return data.substring(0, index)
    val cleaned = relativePath.trimEnd('/')
    return if (cleaned.isNotBlank()) "storage/emulated/0/$cleaned" else "Unknown folder"
}

private fun android.database.Cursor.long(column: String): Long =
    orEmptyLong(getColumnIndexOrThrow(column))

private fun android.database.Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

private fun android.database.Cursor.int(column: String): Int =
    orEmptyLong(getColumnIndexOrThrow(column)).toInt()

private fun android.database.Cursor.str(column: String): String =
    orEmptyString(getColumnIndexOrThrow(column))

private fun android.database.Cursor.orEmptyString(index: Int): String =
    if (isNull(index)) "" else getString(index).orEmpty()

private fun android.database.Cursor.orEmptyLong(index: Int): Long =
    if (isNull(index)) 0L else getLong(index)