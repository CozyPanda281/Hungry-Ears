package com.hungryears.music.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorMapperTest {

    @Test
    fun `unsupported container and decoder failures map to unsupported format`() {
        assertEquals(
            PlaybackError.UNSUPPORTED_FORMAT,
            PlaybackErrorMapper.fromErrorCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED),
        )
        assertEquals(
            PlaybackError.UNSUPPORTED_FORMAT,
            PlaybackErrorMapper.fromErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED),
        )
        assertEquals(
            PlaybackError.UNSUPPORTED_FORMAT,
            PlaybackErrorMapper.fromErrorCode(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED),
        )
    }

    @Test
    fun `other failures map to a generic playback error`() {
        assertEquals(
            PlaybackError.PLAYBACK_FAILED,
            PlaybackErrorMapper.fromErrorCode(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
        )
        assertEquals(
            PlaybackError.PLAYBACK_FAILED,
            PlaybackErrorMapper.fromErrorCode(PlaybackException.ERROR_CODE_UNSPECIFIED),
        )
    }
}
