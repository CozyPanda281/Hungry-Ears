package com.hungryears.music.player

import androidx.media3.common.PlaybackException
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.queue.RepeatMode

/** Typed playback failures, mapped to user-facing copy by the UI. */
enum class PlaybackError {
    UNSUPPORTED_FORMAT,
    PLAYBACK_FAILED,
}

object PlaybackErrorMapper {
    fun fromErrorCode(errorCode: Int): PlaybackError = when (errorCode) {
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> PlaybackError.UNSUPPORTED_FORMAT

        else -> PlaybackError.PLAYBACK_FAILED
    }
}

/** Single observable playback model for every screen (local today, remote after Phase 9). */
data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val upNext: Track? = null,
    val error: PlaybackError? = null,
) {
    val isActive: Boolean get() = currentTrack != null
}
