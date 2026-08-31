package com.localaudio.player.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.localaudio.player.R
import com.localaudio.player.playback.PlaybackState
import com.localaudio.player.ui.components.PlayerAction
import com.localaudio.player.ui.components.PlayerTransportSegment
import com.localaudio.player.ui.components.WavyPlayerSlider
import com.localaudio.player.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    state: PlaybackState,
    seekStepMs: Long,
    showAlbumCover: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenDirectorySkip: () -> Unit,
    isAutoSkipMarking: Boolean,
    onStartAutoSkipMark: () -> Unit,
    onFinishAutoSkipMark: () -> Unit,
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.25f))
        if (showAlbumCover) {
            CoverPlaceholder(
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .weight(1.75f, fill = false),
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        AutoScrollableTrackTitle(
            title = state.currentItem?.title ?: stringResource(R.string.player_empty),
            textStyle = MaterialTheme.typography.headlineSmall,
            textColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalPadding = 8.dp,
        )
        Spacer(modifier = Modifier.height(10.dp))
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
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset(y = (-6).dp),
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
                .offset(y = (-6).dp),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerTransportSegment(
                icon = Icons.Filled.FastRewind,
                contentDescription = stringResource(R.string.player_seek_back),
                onClick = { onSeekBy(-seekStepMs) },
                shape = segmentStartShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PlayerTransportSegment(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
                onClick = onPrevious,
                shape = segmentInnerShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PlayerTransportSegment(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.player_pause else R.string.player_play,
                ),
                onClick = onPlayPause,
                shape = segmentInnerShape,
                enabled = state.currentItem != null,
                iconSize = 36.dp,
                active = true,
                modifier = Modifier.weight(1.3f).fillMaxHeight(),
            )
            PlayerTransportSegment(
                icon = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                onClick = onNext,
                shape = segmentInnerShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PlayerTransportSegment(
                icon = Icons.Filled.FastForward,
                contentDescription = stringResource(R.string.player_seek_forward),
                onClick = { onSeekBy(seekStepMs) },
                shape = segmentEndShape,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
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
            Box {
                var modeMenuExpanded by remember { mutableStateOf(false) }
                PlayerAction(
                    icon = Icons.Filled.Menu,
                    label = stringResource(R.string.player_menu),
                    onClick = { modeMenuExpanded = true },
                    shape = actionStartShape,
                    modifier = Modifier.size(width = 76.dp, height = 52.dp),
                )
                PlayModeMenu(
                    expanded = modeMenuExpanded,
                    hasCurrentItem = state.currentItem != null,
                    onDismiss = { modeMenuExpanded = false },
                    onOpenQueue = {
                        modeMenuExpanded = false
                        onOpenQueue()
                    },
                    onOpenMode = {
                        modeMenuExpanded = false
                        onOpenMode()
                    },
                    onOpenDirectorySkip = {
                        modeMenuExpanded = false
                        onOpenDirectorySkip()
                    },
                )
            }
            PlayerAction(
                icon = Icons.Filled.Timer,
                label = timerLabel(state),
                onClick = onOpenTimer,
                shape = actionInnerShape,
                active = state.timerActive,
                modifier = Modifier.size(width = 76.dp, height = 52.dp),
            )
            PlayerAction(
                icon = Icons.Filled.BookmarkAdd,
                label = stringResource(
                    if (isAutoSkipMarking) {
                        R.string.player_auto_skip_finish
                    } else {
                        R.string.player_auto_skip_mark
                    },
                ),
                onClick = if (isAutoSkipMarking) onFinishAutoSkipMark else onStartAutoSkipMark,
                shape = actionEndShape,
                enabled = state.currentItem != null,
                active = isAutoSkipMarking,
                modifier = Modifier.size(width = 76.dp, height = 52.dp),
            )
        }
    }
}

@Composable
private fun PlayModeMenu(
    expanded: Boolean,
    hasCurrentItem: Boolean,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenDirectorySkip: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dialog_queue)) },
            onClick = onOpenQueue,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dialog_play_mode)) },
            onClick = onOpenMode,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_skip_boundaries)) },
            onClick = onOpenDirectorySkip,
            enabled = hasCurrentItem,
        )
    }
}

@Composable
private fun CoverPlaceholder(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 56.dp.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }
    var coverTranslationX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val latestSettling = rememberUpdatedState(settling)
    val latestOnNext = rememberUpdatedState(onNext)
    val latestOnPrevious = rememberUpdatedState(onPrevious)
    val animationScope = rememberCoroutineScope()
    Surface(
        modifier = modifier
            .graphicsLayer { translationX = coverTranslationX }
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(swipeThreshold, touchSlop, widthPx) {
                if (widthPx <= 0f) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (latestSettling.value) {
                    while (awaitPointerEvent().changes.any { it.pressed }) Unit
                    return@awaitEachGesture
                }
                var horizontalDrag = 0f
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    horizontalDrag += change.positionChange().x
                    if (!dragging && abs(horizontalDrag) > touchSlop) dragging = true
                    if (dragging) {
                        change.consume()
                        coverTranslationX = horizontalDrag.coerceIn(-widthPx, widthPx)
                    }
                    if (!change.pressed) break
                }
                val direction = when {
                    horizontalDrag <= -swipeThreshold -> -1
                    horizontalDrag >= swipeThreshold -> 1
                    else -> 0
                }
                val releasedTranslation = coverTranslationX
                settling = true
                animationScope.launch {
                    try {
                        if (direction == 0) {
                            animate(
                                initialValue = releasedTranslation,
                                targetValue = 0f,
                                animationSpec = tween(180),
                            ) { value, _ -> coverTranslationX = value }
                        } else {
                            val target = direction * widthPx
                            animate(
                                initialValue = releasedTranslation,
                                targetValue = target,
                                animationSpec = tween(180),
                            ) { value, _ -> coverTranslationX = value }
                            if (direction < 0) latestOnNext.value() else latestOnPrevious.value()
                            coverTranslationX = -target
                            animate(
                                initialValue = coverTranslationX,
                                targetValue = 0f,
                                animationSpec = tween(180),
                            ) { value, _ -> coverTranslationX = value }
                        }
                    } finally {
                        settling = false
                    }
                }
            }
        },
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

@Composable
private fun AutoScrollableTrackTitle(
    title: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1_000,
                    velocity = 50.dp,
                )
                .padding(horizontal = horizontalPadding),
            maxLines = 1,
            softWrap = false,
            style = textStyle,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun timerLabel(state: PlaybackState): String = when {
    state.timerWaitingForEnd -> stringResource(R.string.player_timer_waiting)
    state.timerActive -> {
        val minutes = (state.timerRemainingMs / 60_000L).coerceAtLeast(0L)
        if (minutes > 999L) "999+" else stringResource(R.string.player_timer_minutes, minutes)
    }
    else -> stringResource(R.string.player_timer)
}
