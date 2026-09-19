package com.hungryears.music.domain.autoplay

import com.hungryears.music.domain.recommendation.AffinityEngine
import com.hungryears.music.domain.recommendation.Decay
import com.hungryears.music.domain.recommendation.PlaybackEventType
import com.hungryears.music.domain.recommendation.PlaybackSignal

/**
 * AutoplayEngine — PHASE 8 §8 "what you'd hear next: the highest-coOccurrence edge from the last
 * REAL-played track; deterministic + bounded + explainable; COLD START (empty signals) = null.
 *
 * Deterministic: same (signals, coOccurrence, nowMs) -> same next, always. No RNG, no Room, no
 * Android — only [AffinityEngine] + [Decay] (both pure, both on disk, both unit-pinned PHASES 6-7).
 */
object AutoplayEngine {

    /** §8 bound: how many NEWEST signals may seed the "what next" decision. */
    const val AUTOPLAY_LOOKBACK_WINDOW = 3

    /**
     * @param signals  bounded playback history, newest last (repo slice — never Room here).
     * @param coOccurrence A⇒B edges from [AffinityEngine.coOccurrence] (§co-occurrence read the
     *   surfaces §playback consume — deterministic per input).
     * @param nowMs    wall-clock for decay (the only clock read; same value in = same out).
     * @return trackId to play next, or `null` when there is no bounded history to seed it
     *   (§8 cold start — an empty memory queues up nothing, never a fabricated rail).
     */
    fun nextTrack(
        signals: List<PlaybackSignal>,
        coOccurrence: Map<Pair<Long, Long>, Int>,
        nowMs: Long,
    ): Long? {
        val last = signals.lastOrNull() ?: return null
        if (last.type == PlaybackEventType.SKIP) return null

        val seed = signals.lastOrNull { it.type != PlaybackEventType.SKIP }?.trackId ?: return null

        val best = coOccurrence.entries
            .asSequence()
            .filter { it.key.first == seed }
            .maxByOrNull { it.value } ?: return null

        return best.key.second.takeIf { it != seed }
    }
}
