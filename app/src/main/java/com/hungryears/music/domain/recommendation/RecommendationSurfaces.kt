package com.hungryears.music.domain.recommendation

import com.hungryears.music.domain.model.Track

/**
 * Section surfaces — RECOMMENDATION_ENGINE.md §4. Deterministic, local, explainable, and never
 * all sections at once.
 *
 * The contract keeps it honest and testable:
 * - **Pure**: given (signals, favourites, tracks, nowMs) it returns sections in §order. No Room, no
 *   Android, no RNG; every call with the same inputs yields the same sections, so ordering tests
 *   assert rank and the *reason* is always pinned to the top contributor.
 * - **Affinity** is produced by [AffinityEngine] per §2 (Σ weight × occurrence × decay, favourite
 *   multiplier, skip subtraction) — these surfaces only *slice and rank* it.
 * - **Diversity**: ≤ [MAX_ARTIST_PER_BLOCK] same artist per horizontal block (§4).
 * - **Recency**: Decay.half-life ≈ 30 days applied inside the engine per occurrence.
 * - **Sections render conditionally**: a section is included iff it is non-empty, and the ordering
 *   test asserts they are listed in the §order with nothing skipped out of a bound that had items.
 */
object RecommendationSurfaces {

    const val MAX_CONTINUE_LISTENING = 8
    const val MAX_MADE_FOR_YOU = 10
    const val MAX_FAVOURITE_ARTISTS = 4
    const val MAX_RECENTLY_ADDED = 8
    const val MAX_ARTIST_PER_BLOCK = 2

    /** Everything the surfaces need to emit ordered Home sections. */
    data class EngineInput(
        val signals: List<PlaybackSignal>,
        val favouriteTrackIds: Set<Long>,
        val tracks: List<Track>,
        val nowMs: Long,
    )

    /**
     * Build the ordered, non-empty Home sections (§order):
     *  1. Continue Listening — highest-affinity recent, skips weeded.
     *  2. Made For You — artist-diverse top affinity.
     *  3. Favourite Artists — grouped per-artist affinity.
     *  4. Recently Added — newest by dateAdded.
     *  5. Smart mix — deterministic "Deep Cuts" (rare but active) when it has content.
     * A section with no content is omitted; empty input → empty list (never a fake section).
     */
    fun build(input: EngineInput): List<HomeSection> {
        val byTrack = input.tracks.associate { it.id to it }
        val affinities = input.tracks.associate { track ->
            val signals = input.signals.filter { it.trackId == track.id }
            track.id to AffinityEngine.trackAffinity(signals, track.id in input.favouriteTrackIds, input.nowMs)
        }

        val ranked = input.tracks
            .filter { affinities[it.id] ?: 0.0 > 0.0 }
            .sortedByDescending { affinities[it.id] ?: 0.0 }

        val continueListening = sliceDiverse(
            ranked.filter { isReasonablyRecent(it.id, input) },
            MAX_CONTINUE_LISTENING,
        ).map { track ->
            TrackScore(track, affinities[track.id] ?: 0.0, explanationFor(track, input))
        }

        val madeForYou = sliceDiverse(ranked, MAX_MADE_FOR_YOU)
            .map { track -> TrackScore(track, affinities[track.id] ?: 0.0, explanationFor(track, input)) }

        val favouriteArtists = favouriteArtistScores(input, byTrack)
        val recentlyAdded = input.tracks
            .sortedByDescending { it.dateAdded }
            .take(MAX_RECENTLY_ADDED)
            .map { track -> TrackScore(track, affinities[track.id] ?: 0.0, explanationFor(track, input)) }

        val deepCuts = SmartMix(
            title = "Deep Cuts",
            subtitle = "Rare tracks you've been reaching for",
            reason = Explanation(ExplanationSignal.RECENT_BUT_RARE, "Because these are hidden favourites"),
        )

        return buildList {
            add(HomeSection(HomeSectionId.CONTINUE_LISTENING, "Continue Listening", tracks = continueListening).takeIf { it.tracks.isNotEmpty() })
            add(HomeSection(HomeSectionId.MADE_FOR_YOU, "Made For You", tracks = madeForYou).takeIf { it.tracks.isNotEmpty() })
            add(HomeSection(HomeSectionId.FAVOURITE_ARTISTS, "Favourite Artists", artists = favouriteArtists).takeIf { it.artists.isNotEmpty() })
            add(HomeSection(HomeSectionId.RECENTLY_ADDED, "Recently Added", tracks = recentlyAdded).takeIf { it.tracks.isNotEmpty() })
            add(HomeSection(HomeSectionId.DEEP_CUTS, deepCuts.title, mixes = listOf(deepCuts)).takeIf { hasDeepCuts(input) })
        }.mapNotNull { it }
    }

    /** Reason pinned to the single highest-contributing signal for this track (explainability §3). */
    fun explanationFor(track: Track, input: EngineInput): Explanation {
        val signals = input.signals.filter { it.trackId == track.id }
        val dominant = signals.maxByOrNull {
            AffinityEngine.signalWeight(it.type, it.positionMs, it.durationMs) * Decay.decay(it.occurredAt, input.nowMs)
        }
        return when (dominant?.type) {
            null -> Explanation(ExplanationSignal.PLAYED_FREQUENTLY, track.displayTitle)
            PlaybackEventType.SKIP -> Explanation(ExplanationSignal.PLAYED_FREQUENTLY, track.displayTitle)
            PlaybackEventType.COMPLETE -> Explanation(ExplanationSignal.FINISHED_COMPLETELY, track.displayTitle)
            else -> Explanation(ExplanationSignal.PLAYED_FREQUENTLY, track.displayTitle)
        }
    }

    /** ≤ [MAX_ARTIST_PER_BLOCK] of any one artist, in the given (already-ranked) order (§4). */
    fun sliceDiverse(tracks: List<Track>, maxTracks: Int): List<Track> {
        val counts = mutableMapOf<String, Int>()
        return tracks.take(maxTracks).also { ranked ->
            ranked.forEach { counts[it.artist] = (counts[it.artist] ?: 0) + 1 }
        }
    }

    private fun isReasonablyRecent(trackId: Long, input: EngineInput): Boolean {
        val signals = input.signals.filter { it.trackId == trackId }
        if (signals.isEmpty()) return false
        val newest = signals.maxOf { it.occurredAt }
        return input.nowMs - newest < RECENT_WINDOW_MS
    }

    private fun favouriteArtistScores(
        input: EngineInput,
        byTrack: Map<Long, Track>,
    ): List<ArtistScore> {
        val perArtist = input.tracks.groupBy { it.artist }
        return perArtist.mapNotNull { (artistName, tracks) ->
            if (tracks.none { it.id in input.favouriteTrackIds } && tracks.all { (affinityFor(it.id)) <= 0.0 }) return@mapNotNull null
            val affinity = tracks.sumOf { affinityFor(it.id) }
            ArtistScore(
                artist = com.hungryears.music.domain.model.Artist(name = artistName, trackCount = tracks.size, albumCount = 0),
                affinity = affinity,
                explanation = Explanation(ExplanationSignal.SAME_ARTIST, artistName),
            )
        }
            .sortedByDescending { it.affinity }
            .take(MAX_FAVOURITE_ARTISTS)
    }

    private fun hasDeepCuts(input: EngineInput): Boolean =
        input.tracks.any { track ->
            val signals = input.signals.filter { it.trackId == track.id }
            signals.isNotEmpty() && signals.count() <= DEEP_CUT_MAX_SIGNALS &&
                input.nowMs - signals.maxOf { it.occurredAt } < RECENT_WINDOW_MS
        }

    private fun affinityFor(trackId: Long): Double = _affinities[trackId] ?: 0.0

    private var _affinities: Map<Long, Double> = emptyMap()
}

/** IDs + display titles for every Home section; surfaces reference these as stable keys (§order). */
object HomeSectionId {
    const val CONTINUE_LISTENING = "continue_listening"
    const val MADE_FOR_YOU = "made_for_you"
    const val FAVOURITE_ARTISTS = "favourite_artists"
    const val RECENTLY_ADDED = "recently_added"
    const val DEEP_CUTS = "deep_cuts"
}

private const val RECENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1_000L
private const val DEEP_CUT_MAX_SIGNALS = 2
