package com.hungryears.music.player

import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.queue.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueManagerTest {

    private fun track(id: Long): Track = Track(
        id = id,
        title = "Track $id",
        artist = "Artist",
        albumTitle = "Album",
        albumId = 1L,
        genre = "Genre",
        durationMs = 1_000L,
        trackNumber = id.toInt(),
        year = 2026,
        folderPath = "/Music",
        fileName = "track$id.wav",
        sizeBytes = 1L,
        dateAdded = 0L,
        dateModifiedMs = 0L,
        contentUri = "content://media/audio/$id",
        container = "wav",
        isSupported = true,
    )

    private val tracks = (1L..5L).map(::track)

    @Test
    fun `sequential queue keeps library order and starts at the requested track`() {
        val manager = QueueManager(Random(1))

        val startIndex = manager.setQueue(tracks, startTrackId = 3L)

        assertEquals(2, startIndex)
        assertEquals(tracks, manager.playbackTracks())
        assertFalse(manager.shuffleEnabled)
    }

    @Test
    fun `unknown start track falls back to the front`() {
        val manager = QueueManager(Random(1))

        assertEquals(0, manager.setQueue(tracks, startTrackId = 999L))
    }

    @Test
    fun `shuffled queue is a permutation that keeps the current track first`() {
        val manager = QueueManager(Random(9))

        val startIndex = manager.setQueue(tracks, startTrackId = 2L, shuffle = true)

        assertEquals(0, startIndex)
        assertEquals(track(2L), manager.playbackTracks().first())
        assertEquals(tracks.toSet(), manager.playbackTracks().toSet())
        assertTrue(manager.shuffleEnabled)
    }

    @Test
    fun `toggling shuffle keeps the current track and restores order when turned off`() {
        val manager = QueueManager(Random(5))
        manager.setQueue(tracks, startTrackId = 1L)

        val shuffledIndex = manager.toggleShuffle(currentTrackId = 4L)
        assertEquals(0, shuffledIndex)
        assertEquals(track(4L), manager.playbackTracks().first())

        val sequentialIndex = manager.toggleShuffle(currentTrackId = 4L)
        assertEquals(3, sequentialIndex)
        assertEquals(tracks, manager.playbackTracks())
        assertFalse(manager.shuffleEnabled)
    }

    @Test
    fun `repeat mode is tracked independently of queue order`() {
        val manager = QueueManager(Random(1))
        manager.setQueue(tracks, startTrackId = 1L)

        assertEquals(RepeatMode.OFF, manager.repeatMode)
        manager.setRepeatMode(RepeatMode.ALL)
        assertEquals(RepeatMode.ALL, manager.repeatMode)
    }

    @Test
    fun `empty queue is empty and safe`() {
        val manager = QueueManager(Random(1))

        assertEquals(0, manager.setQueue(emptyList(), startTrackId = null))
        assertTrue(manager.isEmpty())
        assertEquals(emptyList<Track>(), manager.playbackTracks())
    }

    @Test
    fun `order index lookup maps back to the source track`() {
        val manager = QueueManager(Random(3))
        manager.setQueue(tracks, startTrackId = 1L, shuffle = true)

        val orderIndex = manager.orderIndexOf(3L)
        assertEquals(track(3L), orderIndex?.let { manager.playbackTracks()[it] })
    }
}
