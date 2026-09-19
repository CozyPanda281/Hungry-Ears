package com.hungryears.music.data.scanner

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreScannerTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var scanner: MediaStoreScanner
    private val insertedUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        scanner = MediaStoreScanner(context) { true }
    }

    @After
    fun tearDown() {
        insertedUris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        insertedUris.clear()
    }

    @Test
    fun discoversInsertedAudioFromMediaStore() = runBlocking {
        insertAudio(
            displayName = "hungry-ears-tone.wav",
            title = "Tone Alpha",
            artist = "Test Artist",
            album = "Test Album",
        )

        val outcome = scanner.scan()

        assertFalse(outcome.permissionDenied)

        val discovered = outcome.tracks.joinToString { it.fileName }
        val supported = outcome.tracks.firstOrNull { it.fileName == "hungry-ears-tone.wav" }
        assertNotNull("expected hungry-ears-tone.wav among [$discovered]", supported)
        assertTrue(supported!!.isSupported)
        assertEquals("wav", supported.container)
        assertTrue("artist should be populated", supported.artist.isNotBlank())
        assertTrue("album should be populated", supported.albumTitle.isNotBlank())
        assertTrue(
            "folder should be derived from the Music subfolder",
            supported.folderPath.contains("HungryEarsTest"),
        )
        assertTrue("duration should be read", supported.durationMs > 0L)
        assertTrue("content uri should be resolvable", supported.contentUri.startsWith("content://"))
    }

    @Test
    fun scanReportsPermissionDeniedWhenNotGranted() = runBlocking {
        val denied = MediaStoreScanner(context) { false }.scan()
        assertTrue(denied.permissionDenied)
    }

    private fun insertAudio(
        displayName: String,
        title: String,
        artist: String,
        album: String,
    ): Uri {
        val mimeType = if (displayName.endsWith(".wav")) "audio/wav" else "audio/mpeg"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/HungryEarsTest",
            )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(context.contentResolver.insert(collection, values))
        insertedUris += uri
        context.contentResolver.openOutputStream(uri)?.use { it.write(SilentWav.bytes()) }
        val complete = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, complete, null, null)
        return uri
    }

    private object SilentWav {
        fun bytes(durationMs: Int = 250, sampleRate: Int = 8_000): ByteArray {
            val sampleCount = sampleRate * durationMs / 1_000
            val dataSize = sampleCount * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }
            val out = ByteArrayOutputStream()
            out.write(header.array())
            out.write(ByteArray(dataSize))
            return out.toByteArray()
        }
    }
}