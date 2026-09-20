package com.hungryears.music.data.repository

import com.hungryears.music.data.local.db.dao.LibraryDao
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.recommendation.AffinityEngine
import com.hungryears.music.domain.recommendation.HomeSection
import com.hungryears.music.domain.recommendation.PlaybackEventType
import com.hungryears.music.domain.recommendation.PlaybackSignal
import com.hungryears.music.domain.recommendation.RecommendationSurfaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RecommendationRepository(
    private val libraryDao: LibraryDao,
) {

    /**
     * Deterministic Home sections (§order), observed from real local playback history + favourites.
     * Empty history → empty sections (Home empty-state; never fabricated).
     */
    fun observeHomeSections(
        favouriteTrackIds: Flow<Set<Long>>,
        tracks: Flow<List<Track>>,
        nowMs: () -> Long = System::currentTimeMillis,
    ): Flow<List<HomeSection>> {
        val history = libraryDao.observeRecentHistory(limit = AffinityEngine.W_BOUNDED_WINDOW)
        return history
            .combine(favouriteTrackIds) { recent, favIds ->
                recent to favIds
            }
            .combine(tracks) { (recent, favIds), libraryTracks ->
                RecommendationSurfaces.build(
                    input = RecommendationSurfaces.EngineInput(
                        signals = recent.map { row ->
                            PlaybackSignal(
                                trackId = row.trackId,
                                type = PlaybackEventType.valueOf(row.eventType),
                                positionMs = row.positionMs,
                                durationMs = row.durationMs,
                                occurredAt = row.occurredAt,
                            )
                        },
                        favouriteTrackIds = favIds,
                        tracks = libraryTracks,
                        nowMs = nowMs(),
                    ),
                )
            }
    }
}
