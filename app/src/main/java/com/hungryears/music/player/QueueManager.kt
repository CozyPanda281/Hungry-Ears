package com.hungryears.music.player

import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.queue.QueueLogic
import com.hungryears.music.domain.queue.RepeatMode
import kotlin.random.Random

/**
 * Holds the playback queue and the order it plays in.
 *
 * Shuffle is a fixed permutation ([QueueLogic.shuffledOrder]) that always keeps the current track
 * at the front, so toggling shuffle never re-rolls the whole queue or restarts an unrelated song.
 * The player layer bakes this order into ExoPlayer's playlist, which keeps gapless playback and
 * repeat semantics native while this class stays pure Kotlin and unit-testable.
 */
class QueueManager(
    private val random: Random = Random.Default,
) {
    var repeatMode: RepeatMode = RepeatMode.OFF
        private set

    var shuffleEnabled: Boolean = false
        private set

    private var tracks: List<Track> = emptyList()

    /** Playback order as indices into [tracks]. */
    private var order: List<Int> = emptyList()

    val size: Int get() = tracks.size

    fun isEmpty(): Boolean = tracks.isEmpty()

    fun playbackTracks(): List<Track> = order.map { tracks[it] }

    /**
     * Replaces the queue and returns the playback-order position for [startTrackId]
     * (first track when omitted or absent).
     */
    fun setQueue(
        tracks: List<Track>,
        startTrackId: Long?,
        shuffle: Boolean = shuffleEnabled,
        repeat: RepeatMode = repeatMode,
    ): Int {
        this.tracks = tracks
        this.shuffleEnabled = shuffle
        this.repeatMode = repeat
        val startOriginal = tracks.indexOfFirst { it.id == startTrackId }.takeIf { it >= 0 } ?: 0
        order = buildOrder(shuffle, startOriginal)
        return if (order.isEmpty()) 0 else order.indexOf(startOriginal)
    }

    /**
     * Flips shuffle, keeping [currentTrackId] first, and returns the playback-order position the
     * player should seek to so the same song keeps playing.
     */
    fun toggleShuffle(currentTrackId: Long?): Int {
        val currentOriginal = currentTrackId
            ?.let { id -> tracks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        shuffleEnabled = !shuffleEnabled
        order = buildOrder(shuffleEnabled, currentOriginal)
        return if (order.isEmpty()) 0 else order.indexOf(currentOriginal)
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    fun originalIndexAt(orderIndex: Int): Int? = order.getOrNull(orderIndex)

    fun orderIndexOf(trackId: Long): Int? {
        val original = tracks.indexOfFirst { it.id == trackId }
        if (original < 0) return null
        return order.indexOf(original).takeIf { it >= 0 }
    }

    private fun buildOrder(shuffle: Boolean, firstOriginal: Int): List<Int> {
        if (tracks.isEmpty()) return emptyList()
        if (!shuffle) return tracks.indices.toList()
        return QueueLogic.shuffledOrder(tracks.size, firstOriginal, random)
    }
}
