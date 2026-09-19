package com.hungryears.music.data.repository

import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.local.db.entity.HistoryEntity
import com.hungryears.music.domain.history.PlaybackEventSink
import com.hungryears.music.domain.model.PlaybackEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Captures play/completion/skip/replay events. Surfacing them (Home, recommendations) is PHASE 7. */
class HistoryRepository(database: HungryEarsDatabase) : PlaybackEventSink {

    private val dao = database.libraryDao()

    override suspend fun record(
        trackId: Long,
        type: PlaybackEventType,
        positionMs: Long,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        dao.insertHistory(
            HistoryEntity(
                trackId = trackId,
                eventType = type.name,
                positionMs = positionMs,
                durationMs = durationMs,
                occurredAt = System.currentTimeMillis(),
            ),
        )
    }
}
