package com.localaudio.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.settings.REPEAT_ONE
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.PlayerAction
import com.localaudio.player.ui.components.PlayerTransportSegment
import com.localaudio.player.ui.components.WavyPlayerSlider
import com.localaudio.player.ui.util.formatTime

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    state: PlaybackState,
    seekStepMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMode: () -> Unit,
) {
    val currentItemKey = state.currentItem?.key
    var sliderValue by remember(currentItemKey) { mutableFloatStateOf(state.positionMs.toFloat()) }
    var seeking by remember(currentItemKey) { mutableStateOf(false) }
    LaunchedEffect(state.positionMs, seeking) {
        if (!seeking) sliderValue = state.positionMs.toFloat()
    }
    val max = state.durationMs.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toFloat()
    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .semantics {
                if (!visible) hideFromAccessibility()
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        CoverPlaceholder(modifier = Modifier.size(220.dp))
        Spacer(modifier = Modifier.height(14.dp))
        SwipeableTrackLabel(
            title = state.currentItem?.title ?: "还没有播放内容\n请到首页选择歌曲",
            onNext = onNext,
            onPrevious = onPrevious,
            textStyle = MaterialTheme.typography.headlineSmall,
            textColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            textAlign = TextAlign.Center,
            maxLines = 3,
            horizontalPadding = 8.dp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        val segmentStartShape = RoundedCornerShape(
            topStart = 60.dp,
            bottomStart = 60.dp,
            topEnd = 8.dp,
            bottomEnd = 8.dp,
        )
        val segmentEndShape = RoundedCornerShape(
            topStart = 8.dp,
            bottomStart = 8.dp,
            topEnd = 60.dp,
            bottomEnd = 60.dp,
        )
        val segmentInnerShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            WavyPlayerSlider(
                value = (sliderValue / max).coerceIn(0f, 1f),
                onValueChange = { seeking = true; sliderValue = it * max },
                onValueChangeFinished = {
                    if (seeking) {
                        seeking = false
                        onSeekTo(sliderValue.toLong())
                    }
                },
                enabled = state.currentItem != null,
                isPlaying = state.isPlaying,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-4).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(if (seeking) sliderValue.toLong() else state.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTransportSegment(
                    icon = Icons.Filled.FastRewind,
                    contentDescription = "快退",
                    onClick = { onSeekBy(-seekStepMs) },
                    shape = segmentStartShape,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PlayerTransportSegment(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "上一曲",
                    onClick = onPrevious,
                    shape = segmentInnerShape,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PlayerTransportSegment(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    onClick = onPlayPause,
                    shape = segmentInnerShape,
                    enabled = state.currentItem != null,
                    iconSize = 36.dp,
                    active = true,
                    modifier = Modifier.weight(1.3f).fillMaxHeight(),
                )
                PlayerTransportSegment(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "下一曲",
                    onClick = onNext,
                    shape = segmentInnerShape,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PlayerTransportSegment(
                    icon = Icons.Filled.FastForward,
                    contentDescription = "快进",
                    onClick = { onSeekBy(seekStepMs) },
                    shape = segmentEndShape,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val actionStartShape = RoundedCornerShape(
            topStart = 24.dp,
            bottomStart = 24.dp,
            topEnd = 8.dp,
            bottomEnd = 8.dp,
        )
        val actionEndShape = RoundedCornerShape(
            topStart = 8.dp,
            bottomStart = 8.dp,
            topEnd = 24.dp,
            bottomEnd = 24.dp,
        )
        val actionInnerShape = RoundedCornerShape(8.dp)
        Row(
            modifier = Modifier
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAction(
                icon = playModeIcon(state),
                label = playModeLabel(state),
                onClick = onOpenMode,
                shape = actionStartShape,
                modifier = Modifier.size(width = 76.dp, height = 52.dp),
            )
            PlayerAction(
                icon = Icons.Filled.Timer,
                label = timerLabel(state),
                onClick = onOpenTimer,
                shape = actionInnerShape,
                active = state.timerActive,
                modifier = Modifier.size(width = 76.dp, height = 52.dp),
            )
            PlayerAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "列表",
                onClick = onOpenQueue,
                shape = actionEndShape,
                modifier = Modifier.size(width = 76.dp, height = 52.dp),
            )
        }
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun playModeLabel(state: PlaybackState): String = when {
    state.shuffleEnabled -> "随机"
    state.repeatMode == REPEAT_ONE -> "单曲循环"
    state.repeatMode == REPEAT_ALL -> "列表循环"
    else -> "顺序"
}

private fun playModeIcon(state: PlaybackState): ImageVector = when {
    state.shuffleEnabled -> Icons.Filled.Shuffle
    state.repeatMode == REPEAT_ONE -> Icons.Filled.RepeatOne
    state.repeatMode == REPEAT_ALL -> Icons.Filled.Repeat
    else -> Icons.AutoMirrored.Filled.PlaylistPlay
}

private fun timerLabel(state: PlaybackState): String = when {
    state.timerWaitingForEnd -> "播完暂停"
    state.timerActive -> {
        val minutes = (state.timerRemainingMs / 60_000L).coerceAtLeast(0L)
        if (minutes > 999L) "999+" else "${minutes}分钟"
    }
    else -> "定时"
}
