package com.localaudio.player.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.WavyPlayerSlider

@Composable
fun PlaybackBar(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    onLocateCurrent: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeableTrackLabel(
                title = state.currentItem?.title ?: stringResource(R.string.player_not_playing),
                onNext = onNext,
                onPrevious = onPrevious,
                textStyle = MaterialTheme.typography.bodyMedium,
                textColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                onClick = onOpenPlayer,
                onLongClick = if (state.currentItem != null) onLocateCurrent else null,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onPlayPause,
                modifier = Modifier.size(width = 56.dp, height = 48.dp),
                enabled = state.currentItem != null,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.player_pause else R.string.player_play,
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

internal class PlaybackSliderState(initialPositionMs: Long) {
    var positionMs by mutableFloatStateOf(initialPositionMs.toFloat())
    var seeking by mutableStateOf(false)
}

@Composable
internal fun rememberPlaybackSliderState(state: PlaybackState): PlaybackSliderState {
    val currentItemKey = state.currentItem?.key
    val sliderState = remember(currentItemKey) { PlaybackSliderState(state.positionMs) }
    LaunchedEffect(state.positionMs, sliderState.seeking) {
        if (!sliderState.seeking) sliderState.positionMs = state.positionMs.toFloat()
    }
    return sliderState
}

@Composable
fun PlaybackProgressSlider(
    state: PlaybackState,
    onSeekTo: (Long) -> Unit,
) {
    val sliderState = rememberPlaybackSliderState(state)
    val sliderValue = sliderState.positionMs
    val seeking = sliderState.seeking
    val max = state.durationMs.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toFloat()

    WavyPlayerSlider(
        value = (sliderValue / max).coerceIn(0f, 1f),
        onValueChange = {
            sliderState.seeking = true
            sliderState.positionMs = it * max
        },
        onValueChangeFinished = {
            if (sliderState.seeking) {
                val targetPositionMs = sliderState.positionMs.toLong()
                sliderState.seeking = false
                onSeekTo(targetPositionMs)
            }
        },
        enabled = state.currentItem != null,
        isPlaying = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    )
}
