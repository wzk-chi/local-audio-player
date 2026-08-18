package com.localaudio.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localaudio.player.playback.PlaybackState
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlaybackBar(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val settleOffset = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val latestOnNext = rememberUpdatedState(onNext)
    val latestOnPrevious = rememberUpdatedState(onPrevious)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var titleWidth by remember { mutableFloatStateOf(0f) }
    val dragThreshold = with(density) { 56.dp.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }
    val maxOffset = titleWidth.coerceAtLeast(180f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clipToBounds()
                    .onSizeChanged { titleWidth = it.width.toFloat() }
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
                                                settleOffset.animateTo(0f, tween(durationMillis = 180))
                                            }
                                        } finally {
                                            dragOffset = 0f
                                            settling = false
                                        }
                                    }
                                }
                            } else {
                                onOpenPlayer()
                            }
                        }
                    },
            ) {
                Text(
                    state.currentItem?.title ?: "未播放",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer { translationX = if (settling) settleOffset.value else dragOffset },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(
                        enabled = state.currentItem != null,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
