package com.hungryears.music.data.repository

import com.hungryears.music.data.local.db.dao.LibraryDao
import com.hungryears.music.domain.recommendation.AffinityEngine
import com.hungryears.music.domain.recommendation.HomeSection
import com.hungryears.music.domain.recommendation.PlaybackEventType
import com.hungryears.music.domain.recommendation.PlaybackSignal
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.recommendation.RecommendationSurfaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RecommendationRepository(
    private val libraryDao: LibraryDao,
) {

    /**
     * Deterministic Home sections (§order) from real playback + favouriting, explained in
     * RECOMMENDATION_ENGINE.md §4. Surface logic is pure (RecommendationSurfaces ^ engine data in,
     * sections out); Room is read once, surface rank is never touched by UI or RNG.
     *
     * Empty history → empty HomeSection list → Home shows its EmptyState (never fabricated rails).
     */
    fun observeHomeSections(
        favouriteTrackIds: Flow<Set<Long>>,
        tracks: Flow<List<Track>>,
        nowMs: () -> Long = System::currentTimeMillis,
    ): Flow<List<HomeSection>> =
        libraryDao.observeRecentHistory(limit = AffinityEngine.W_BOUNDED_WINDOW)
            .combine(favouriteTrackIds, tracks) { history, favIds, trackList ->
                RecommendationSurfaces.build(
                    input = RecommendationSurfaces.EngineInput(
                        signals = history.map { signal ->
                            PlaybackSignal(
                                trackId = signal.trackId,
                                type = PlaybackEventType.valueOf(signal.eventType),
                                positionMs = signal.positionMs,
                                durationMs = signal.durationMs,
                                occurredAt = signal.occurredAt,
                            )
                        },
                        favouriteTrackIds = favIds,
                        tracks = trackList,
                        nowMs = nowMs(),
                    ),
                )
            }
}
