package com.hungryears.music.data.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFormatsTest {

    @Test
    fun containerIsParsedFromFileName() {
        assertEquals("mp3", SupportedFormats.containerOf("Song.mp3"))
        assertEquals("flac", SupportedFormats.containerOf("Album Track.flac"))
        assertEquals("m4a", SupportedFormats.containerOf("a.b.c.m4a"))
    }

    @Test
    fun containerParsingIsCaseInsensitive() {
        assertEquals("opus", SupportedFormats.containerOf("TRACK.OPUS"))
        assertTrue(SupportedFormats.isSupported("MP3"))
        assertTrue(SupportedFormats.isSupportedFile("Song.M4A"))
    }

    @Test
    fun fileNameWithoutExtensionYieldsEmptyContainer() {
        assertEquals("", SupportedFormats.containerOf("no-extension"))
        assertFalse(SupportedFormats.isSupported(""))
    }

    @Test
    fun knownAudioExtensionsAreSupported() {
        val known = listOf("song.mp3", "song.wav", "song.flac", "song.aac", "song.m4a", "song.ogg", "song.opus")
        known.forEach { assertTrue("$it should be supported", SupportedFormats.isSupportedFile(it)) }
    }

    @Test
    fun unknownExtensionsAreNotSupported() {
        val unknown = listOf("clip.xyz", "sound.wma", "movie.mkv", "notes.txt")
        unknown.forEach { assertFalse("$it should not be supported", SupportedFormats.isSupportedFile(it)) }
    }
}