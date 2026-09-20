package com.hungryears.music.domain.autoplay

import com.hungryears.music.domain.recommendation.PlaybackSignal
import com.hungryears.music.domain.recommendation.PlaybackEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AutoplayEngine] — PHASE 8 core, pinned: bounded + deterministic + explainable; COLD START
 * (empty history) queues up nothing, SKIP never seeds a rail, and the highest-affinity edge out of
 * the last REAL-played track IS the next track. No RNG, no Room, no Android in this rail.
 */
class AutoplayEngineTest {

    private val nowMs = 1_726_000_000_000L

    private fun complete(trackId: Long, at: Long) = PlaybackSignal(
        trackId = trackId,
        type = PlaybackEventType.COMPLETE,
        positionMs = 200_000L,
        durationMs = 250_000L,
        occurredAt = at,
    )

    private fun skip(trackId: Long, at: Long) = PlaybackSignal(
        trackId = trackId,
        type = PlaybackEventType.SKIP,
        positionMs = 4_000L,
        durationMs = 250_000L,
        occurredAt = at,
    )

    @Test
    fun coldStart_emptyHistory_returnsNull() {
        assertNull(
            AutoplayEngine.nextTrack(
                signals = emptyList(),
                coOccurrence = emptyMap(),
                nowMs = nowMs,
            )
        )
    }

    @Test
    fun newestIsSkip_seedNothing() {
        val seed = 101L
        val signals = listOf(
            complete(trackId = seed, at = nowMs - 2_000L),
            skip(trackId = seed, at = nowMs - 1_000L),
        )
        assertNull(
            AutoplayEngine.nextTrack(
                signals = signals,
                coOccurrence = mapOf((seed to 902L) to 7),
                nowMs = nowMs,
            )
        )
    }

    @Test
    fun complete_seedsHighestAffinityEdge() {
        val seed = 101L
        val best = 915L
        val signals = listOf(complete(trackId = seed, at = nowMs - 1_000L))
        val coOccurrence = mapOf(
            (seed to 812L) to 2,
            (seed to best) to 11,
        )

        val next = AutoplayEngine.nextTrack(signals, coOccurrence, nowMs)

        assertEquals(best, next)
    }

    @Test
    fun deterministic_sameInputs_sameNext() {
        val seed = 101L
        val signals = listOf(complete(trackId = seed, at = nowMs - 1_000L))
        val coOccurrence = mapOf((seed to 777L) to 4)

        val first = AutoplayEngine.nextTrack(signals, coOccurrence, nowMs)
        val second = AutoplayEngine.nextTrack(signals, coOccurrence, nowMs)

        assertEquals(first, second)
        assertEquals(777L, first)
    }
}
