package com.hungryears.music.domain.history

import com.hungryears.music.domain.model.PlaybackEventType

/**
 * Port the playback layer uses to report playback events. Implemented by the data layer so
 * `player/` does not need to know where history is stored.
 */
fun interface PlaybackEventSink {
    suspend fun record(
        trackId: Long,
        type: PlaybackEventType,
        positionMs: Long,
        durationMs: Long,
    )
}
