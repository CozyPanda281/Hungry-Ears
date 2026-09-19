package com.hungryears.music.domain.recommendation

import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Track
import kotlin.math.pow

/**
 * Affinity engine — RECOMMENDATION_ENGINE.md §2. Deterministic, local, explainable.
 *
 * Heres the contract that makes it both honest and testable:
 * - **Deterministic** — same (signals, favourites, nowMs) in, same ordering out; no RNG, no clock
 *   reads at score time beyond the passed [nowMs].
 * - **Affinity = Σ (weight_signal × occurrence) × decay(age)** — weights and completion buckets are
 *   named constants below so tests pin the math.
 * - **Completion ≥0.8 counts full, <0.3 counts 0.25** (§2); between is a linear completion.
 * - **ski skips subtract, replays add, favourites multiply** (§2).
 * - **Recency decay: half-life ≈ 30 days**, applied per-occurrence by age at [nowMs]; a 0-age
 *   signal is a no-op so first reads match raw weights.
 * - **Bounded window**: caller passes ≤ N signals (5000 cap in §1). The engine never caches
 *   beyond what it is given, so a "Clear history" reset is just an empty input — nothing to sweep.
 *
 * Output affinities are raw sums (not normalized on write). The *surfaces* (RecommendationSurfaces)
 * re-normalize on read (decay math is inside Decay.decay) exactly as §2 requires, so ordering tests
 * assert rank, not absolute scale.
 */
object AffinityEngine {

    /** Weights (single source of truth, mirrored in RECOMMENDATION_ENGINE.md §2). */
    const val W_PLAY = 1.0
    const val W_COMPLETE_FULL = W_PLAY
    const val W_COMPLETION_PARTIAL = 0.25
    const val COMPLETION_FULL_THRESHOLD = 0.8
    const val COMPLETION_PARTIAL_THRESHOLD = 0.3
    const val W_SKIP = -1.5
    const val W_REPLAY = 0.5
    const val W_FAVOURITE_MULTIPLIER = 2.0
    const val W_ARTIST_EDGE = 2.0
    const val W_GENRE_EDGE = 1.5
    const val W_BOUNDED_WINDOW = 5_000

    /**
     * Per-occurrence score. `type == COMPLETE || completion ≥ 0.8` counts full; completion < 0.3
     * counts W_COMPLETION_PARTIAL; everything between is linear completion weight.
     */
    fun completionWeight(positionMs: Long, durationMs: Long): Double {
        if (durationMs <= 0L) return W_PLAY
        val ratio = positionMs.toDouble() / durationMs
        return when {
            ratio >= COMPLETION_FULL_THRESHOLD -> W_COMPLETE_FULL
            ratio < COMPLETION_PARTIAL_THRESHOLD -> W_COMPLETION_PARTIAL
            else -> ratio
        }
    }

    /**
     * One occurrence's contribution to a track's affinity, before decay. Skip subtracts; replay
     * adds; everything else scores by completion (PLAY uses its completion bucket too so an
     * interrupted listen counts less than a finished one).
     */
    fun signalWeight(type: PlaybackEventType, positionMs: Long, durationMs: Long): Double =
        when (type) {
            PlaybackEventType.SKIP -> W_SKIP
            PlaybackEventType.REPLAY -> W_REPLAY
            PlaybackEventType.PLAY, PlaybackEventType.COMPLETE -> completionWeight(positionMs, durationMs)
        }

    /**
     * Affinity for one track: Σ per-occurrence signalWeight × recency decay, then favourite
     * multiplier. Empty signals → 0.0 (never surfaces).
     */
    fun trackAffinity(
        signals: List<PlaybackSignal>,
        favourite: Boolean,
        nowMs: Long,
    ): Double {
        val weighted = signals
            .map { signalWeight(it.type, it.positionMs, it.durationMs) * Decay.decay(it.occurredAt, nowMs) }
            .sum()
        val fav = if (favourite) W_FAVOURITE_MULTIPLIER else 1.0
        return weighted * fav
    }

    /** Grouped affinity for an artist: sum of its tracks' affinities (see RECOMMENDATION_ENGINE §2 "per artist"). */
    fun affinityForArtist(
        artistTracks: List<Track>,
        trackAffinities: Map<Long, Double>,
    ): Double = artistTracks.sumOf { trackAffinities[it.id] ?: 0.0 }

    /** Co-occurrence edge A ⇒ B weight (P(B soon after A)): count adjacent history pairs (PHASE 8 consumes these). */
    fun coOccurrence(signals: List<PlaybackSignal>): Map<Pair<Long, Long>, Int> {
        val edges = mutableMapOf<Pair<Long, Long>, Int>()
        var prevTrackId: Long? = null
        var prevType: PlaybackEventType? = null
        signals.forEach { s ->
            val prev = prevTrackId
            if (prev != null && prev != s.trackId &&
                prevType != PlaybackEventType.SKIP &&
                s.type != PlaybackEventType.SKIP
            ) {
                val key = prev to s.trackId
                edges[key] = (edges[key] ?: 0) + 1
            }
            prevTrackId = s.trackId
            prevType = s.type
        }
        return edges
    }

    /** Conviction clamp for Skips-heavy tracks (RECOMMENDATION_ENGINE §4 "weed out skips-heavy"). */
    fun skipPenalty(combined: Double, signal: PlaybackEventType): Double =
        if (signal == PlaybackEventType.SKIP) combined + W_SKIP else combined
}

/**
 * Recency decay — RECOMMENDATION_ENGINE.md §2 ("recency decay: half-life ≈ 30 days").
 * Pure so the half-life math is pinned by tests: a signal aged exactly one half-life weighs 0.5.
 */
object Decay {
    const val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1_000L

    fun decay(occurredAt: Long, nowMs: Long): Double {
        val age = (nowMs - occurredAt).coerceAtLeast(0L)
        return 0.5.pow(age.toDouble() / HALF_LIFE_MS)
    }
}
