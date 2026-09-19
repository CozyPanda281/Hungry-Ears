package com.hungryears.music.player

import com.hungryears.music.domain.history.PlaybackEventSink
import com.hungryears.music.domain.model.PlaybackEventType
import com.hungryears.music.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns `PlayerController` state transitions into play-history events: PLAY on start, then
 * COMPLETE/SKIP for the outgoing track, plus REPLAY when the current track restarts. Heuristic but
 * deterministic — the exact completion threshold is a named constant.
 */
class PlaybackHistoryRecorder(
    private val playerController: PlayerController,
    private val sink: PlaybackEventSink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    fun start(): Job = scope.launch {
        var previousTrack: Track? = null
        var previousPositionMs = 0L
        var previousDurationMs = 0L

        playerController.state.collect { state ->
            val current = state.currentTrack
            val last = previousTrack

            if (last != null && current?.id != last.id) {
                sink.record(
                    trackId = last.id,
                    type = classify(previousPositionMs, previousDurationMs),
                    positionMs = previousPositionMs,
                    durationMs = previousDurationMs,
                )
            }

            if (last != null && current != null && current.id == last.id) {
                val restarted = previousPositionMs > RESTART_MIN_POSITION_MS &&
                    state.positionMs < previousPositionMs - RESTART_JUMP_MS
                if (restarted) {
                    sink.record(current.id, PlaybackEventType.REPLAY, state.positionMs, state.durationMs)
                }
            }

            if (current != null && (last == null || current.id != last.id)) {
                sink.record(current.id, PlaybackEventType.PLAY, 0L, state.durationMs)
            }

            previousTrack = current
            previousPositionMs = state.positionMs
            previousDurationMs = state.durationMs
        }
    }

    private fun classify(positionMs: Long, durationMs: Long): PlaybackEventType {
        val reachedEnd = durationMs > 0 && positionMs >= durationMs - END_THRESHOLD_MS
        return if (reachedEnd) PlaybackEventType.COMPLETE else PlaybackEventType.SKIP
    }

    private companion object {
        const val END_THRESHOLD_MS = 3_000L
        const val RESTART_MIN_POSITION_MS = 5_000L
        const val RESTART_JUMP_MS = 5_000L
    }
}
