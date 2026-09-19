package com.hungryears.music.ui.playerbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hungryears.music.R
import com.hungryears.music.domain.model.Track
import com.hungryears.music.domain.queue.RepeatMode
import com.hungryears.music.player.PlayerUiState
import com.hungryears.music.ui.theme.HungryEarsSpacing

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
    viewModel: MiniPlayerViewModel = viewModel(factory = MiniPlayerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.isActive) return

    val track = state.currentTrack ?: return
    val fraction = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenNowPlaying),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HungryEarsSpacing.m, vertical = HungryEarsSpacing.xs),
            ) {
                MiniArtwork(track)
                Spacer(modifier = Modifier.width(HungryEarsSpacing.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.upNext?.let {
                            stringResource(R.string.mini_player_up_next, it.displayTitle)
                        } ?: track.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = stringResource(R.string.mini_player_shuffle),
                        tint = if (state.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = viewModel::previous, enabled = state.hasPrevious) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = stringResource(R.string.mini_player_previous),
                    )
                }
                IconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.mini_player_pause else R.string.mini_player_play,
                        ),
                    )
                }
                IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = stringResource(R.string.mini_player_next),
                    )
                }
                IconButton(onClick = viewModel::cycleRepeat) {
                    val repeatActive = state.repeatMode != RepeatMode.OFF
                    Icon(
                        painter = painterResource(
                            if (state.repeatMode == RepeatMode.ONE) {
                                R.drawable.ic_repeat_one
                            } else {
                                R.drawable.ic_repeat
                            },
                        ),
                        contentDescription = stringResource(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> R.string.mini_player_repeat_off
                                RepeatMode.ALL -> R.string.mini_player_repeat_all
                                RepeatMode.ONE -> R.string.mini_player_repeat_one
                            },
                        ),
                        tint = if (repeatActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniArtwork(track: Track) {
    val artworkModifier = Modifier
        .size(44.dp)
        .clip(MaterialTheme.shapes.small)
    val uri = track.albumId?.let { id -> "content://media/external/audio/albumart/$id" }
    if (uri == null) {
        Box(
            modifier = artworkModifier.background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_library_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = uri,
            contentDescription = track.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = artworkModifier,
            error = painterResource(R.drawable.ic_library_music),
        )
    }
}
