package com.hungryears.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.queue.QueueLogic
import com.hungryears.music.domain.queue.RepeatMode
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-side playback facade. It connects to [PlaybackService] through a `MediaController` (a `Player`
 * proxy) and exposes the same [PlayerUiState] surface the rest of the app already observes.
 *
 * Queue order/shuffle stay in [QueueManager] and are baked into the session playlist; the actual
 * ExoPlayer lives in the service so playback continues in the background.
 */
class PlayerController(
    context: Context,
    private val queueManager: QueueManager = QueueManager(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()

        override fun onPlaybackStateChanged(playbackState: Int) = updateState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateState(error = null)

        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)

        override fun onRepeatModeChanged(repeatMode: Int) = updateState()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateState()
    }

    init {
        connect()
        startPositionTicker()
    }

    /** Replaces the queue and starts playing at [startTrackId] (or the first track). */
    fun playQueue(tracks: List<Track>, startTrackId: Long? = null) {
        if (tracks.isEmpty()) return
        val startIndex = queueManager.setQueue(tracks, startTrackId)
        val items = queueManager.playbackTracks().map { it.toMediaItem() }
        if (items.isEmpty()) return
        withController { controller ->
            controller.repeatMode = queueManager.repeatMode.toPlayerRepeatMode()
            controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            controller.prepare()
            controller.play()
        }
        updateState(error = null)
    }

    fun togglePlayPause() {
        withController { controller ->
            when (controller.playbackState) {
                Player.STATE_IDLE -> Unit
                Player.STATE_ENDED -> {
                    controller.seekTo(0, 0L)
                    controller.play()
                }

                else -> if (controller.isPlaying) controller.pause() else controller.play()
            }
        }
        updateState()
    }

    fun next() {
        val currentIndex = controller?.currentMediaItemIndex ?: 0
        val target = QueueLogic.nextIndex(
            currentIndex,
            queueManager.size,
            queueManager.repeatMode,
        ) ?: return
        withController { controller ->
            controller.seekTo(target, 0L)
            controller.play()
        }
        updateState()
    }

    fun previous() {
        val currentIndex = controller?.currentMediaItemIndex ?: 0
        val target = QueueLogic.previousIndex(
            currentIndex,
            queueManager.size,
            queueManager.repeatMode,
        ) ?: return
        withController { controller ->
            controller.seekTo(target, 0L)
            controller.play()
        }
        updateState()
    }

    fun seekTo(positionMs: Long) {
        val duration = resolvedDuration()
        val clamped = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        withController { it.seekTo(clamped) }
        updateState()
    }

    fun toggleShuffle() {
        val currentTrackId = _state.value.currentTrack?.id
        val newIndex = queueManager.toggleShuffle(currentTrackId)
        val items = queueManager.playbackTracks().map { it.toMediaItem() }
        if (items.isEmpty()) {
            updateState()
            return
        }
        val position = controller?.currentPosition ?: 0L
        withController { controller ->
            val wasPlaying = controller.isPlaying
            controller.setMediaItems(items, newIndex.coerceIn(0, items.lastIndex), position)
            controller.repeatMode = queueManager.repeatMode.toPlayerRepeatMode()
            controller.prepare()
            if (wasPlaying) controller.play() else controller.pause()
        }
        updateState(error = null)
    }

    fun setRepeatMode(mode: RepeatMode) {
        if (queueManager.repeatMode == mode) return
        queueManager.setRepeatMode(mode)
        withController { it.repeatMode = mode.toPlayerRepeatMode() }
        updateState()
    }

    fun cycleRepeatMode() {
        setRepeatMode(
            when (queueManager.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            },
        )
    }

    fun release() {
        positionJob?.cancel()
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                while (pendingActions.isNotEmpty()) {
                    pendingActions.removeFirst().invoke(connected)
                }
                updateState(error = null)
            },
            Executor { it.run() },
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) action(current) else pendingActions.addLast(action)
    }

    private fun handlePlayerError(error: PlaybackException) {
        val mapped = PlaybackErrorMapper.fromErrorCode(error.errorCode)
        controller?.let { controller ->
            val index = controller.currentMediaItemIndex
            if (index < queueManager.size - 1) {
                controller.seekTo(index + 1, 0L)
                controller.play()
            } else {
                controller.pause()
            }
        }
        updateState(error = mapped)
    }

    private fun updateState(error: PlaybackError? = _state.value.error) {
        val controller = controller
        if (controller == null) {
            _state.value = PlayerUiState(error = error)
            return
        }
        val tracks = queueManager.playbackTracks()
        val orderIndex = controller.currentMediaItemIndex
        val current = tracks.getOrNull(orderIndex)
        val repeat = queueManager.repeatMode
        _state.value = PlayerUiState(
            currentTrack = current,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = resolvedDuration(),
            repeatMode = repeat,
            shuffleEnabled = queueManager.shuffleEnabled,
            hasNext = QueueLogic.hasNext(orderIndex, tracks.size, repeat),
            hasPrevious = QueueLogic.hasPrevious(orderIndex, tracks.size, repeat),
            upNext = QueueLogic.upNextIndex(orderIndex, tracks.size, repeat)?.let { tracks.getOrNull(it) },
            error = error,
        )
    }

    private fun resolvedDuration(): Long {
        val controller = controller ?: return 0L
        val fromPlayer = controller.duration
        if (fromPlayer != C.TIME_UNSET && fromPlayer > 0L) return fromPlayer
        return queueManager.playbackTracks().getOrNull(controller.currentMediaItemIndex)?.durationMs ?: 0L
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val controller = controller
                if (controller != null && controller.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = resolvedDuration(),
                        isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                    )
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val artworkUri = albumId?.let { Uri.parse("content://media/external/audio/albumart/$it") }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setArtist(displayArtist)
                    .setAlbumTitle(displayAlbum)
                    .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
                    .build(),
            )
            .build()
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
