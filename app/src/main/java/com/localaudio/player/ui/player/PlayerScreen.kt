package com.localaudio.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.settings.REPEAT_ONE
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.PlayerAction
import com.localaudio.player.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
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
    val waveAmplitude by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 1_000),
        label = "waveAmplitude",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        CoverPlaceholder(modifier = Modifier.size(220.dp))
        Spacer(modifier = Modifier.height(14.dp))
        SwipeableTrackTitle(
            title = state.currentItem?.title ?: "还没有播放内容\n请到首页选择歌曲",
            onNext = onNext,
            onPrevious = onPrevious,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            val progress = { (sliderValue / max).coerceIn(0f, 1f) }
            LinearWavyProgressIndicator(
                progress = progress,
                amplitude = { value ->
                    waveAmplitude * WavyProgressIndicatorDefaults.indicatorAmplitude(value)
                },
                waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            )
            Slider(
                value = sliderValue.coerceIn(0f, max),
                onValueChange = { seeking = true; sliderValue = it },
                onValueChangeFinished = {
                    if (seeking) {
                        seeking = false
                        onSeekTo(sliderValue.toLong())
                    }
                },
                valueRange = 0f..max,
                enabled = state.currentItem != null,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (seeking) sliderValue.toLong() else state.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onSeekBy(-seekStepMs) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastRewind, contentDescription = "快退")
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一曲")
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp), enabled = state.currentItem != null) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一曲")
            }
            IconButton(onClick = { onSeekBy(seekStepMs) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastForward, contentDescription = "快进")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerAction(
                icon = Icons.Filled.Timer,
                label = timerLabel(state),
                onClick = onOpenTimer,
                active = state.timerActive,
            )
            PlayerAction(icon = playModeIcon(state), label = playModeLabel(state), onClick = onOpenMode)
            PlayerAction(icon = Icons.AutoMirrored.Filled.QueueMusic, label = "列表", onClick = onOpenQueue)
        }
    }
}

@Composable
private fun SwipeableTrackTitle(
    title: String,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val settleOffset = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val latestOnNext = rememberUpdatedState(onNext)
    val latestOnPrevious = rememberUpdatedState(onPrevious)
    val latestTitle = rememberUpdatedState(title)
    var displayedTitle by remember { mutableStateOf(title) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var contentWidth by remember { mutableFloatStateOf(0f) }
    val dragThreshold = with(density) { 56.dp.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }
    val maxOffset = contentWidth.coerceAtLeast(180f)

    LaunchedEffect(title, settling) {
        if (!settling) displayedTitle = title
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clipToBounds()
            .onSizeChanged { contentWidth = it.width.toFloat() }
            .pointerInput(maxOffset) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (settling) {
                        while (awaitPointerEvent().changes.any { it.pressed }) Unit
                        return@awaitEachGesture
                    }
                    var totalDrag = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange().x
                        totalDrag += delta
                        if (!dragging && abs(totalDrag) > touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            dragOffset = (dragOffset + delta).coerceIn(-maxOffset, maxOffset)
                        }
                        if (!change.pressed) break
                    }
                    if (dragging) {
                        val direction = when {
                            dragOffset <= -dragThreshold -> -1
                            dragOffset >= dragThreshold -> 1
                            else -> 0
                        }
                        if (!settling) {
                            val releasedOffset = dragOffset
                            settling = true
                            animationScope.launch {
                                try {
                                    settleOffset.snapTo(releasedOffset)
                                    if (direction == 0) {
                                        settleOffset.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 520f))
                                    } else {
                                        if (direction < 0) latestOnNext.value() else latestOnPrevious.value()
                                        settleOffset.animateTo(direction * maxOffset, tween(durationMillis = 180))
                                        settleOffset.snapTo(-direction * maxOffset)
                                        displayedTitle = latestTitle.value
                                        settleOffset.animateTo(0f, tween(durationMillis = 180))
                                    }
                                } finally {
                                    dragOffset = 0f
                                    settling = false
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = if (settling) settleOffset.value else dragOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = displayedTitle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
