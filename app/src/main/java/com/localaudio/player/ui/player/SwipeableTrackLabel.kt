package com.localaudio.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localaudio.player.R
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun SwipeableTrackLabel(
    title: String,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 2,
    fontWeight: FontWeight? = null,
    horizontalPadding: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val settleOffset = remember { Animatable(0f) }
    val latestOnClick = rememberUpdatedState(onClick)
    val latestOnNext = rememberUpdatedState(onNext)
    val latestOnPrevious = rememberUpdatedState(onPrevious)
    val latestTitle = rememberUpdatedState(title)
    val previousLabel = stringResource(R.string.player_previous)
    val nextLabel = stringResource(R.string.player_next)
    var displayedTitle by remember { mutableStateOf(title) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var contentWidth by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val latestSettling = rememberUpdatedState(settling)
    val dragThreshold = with(density) { 56.dp.toPx() }
    val touchSlop = with(density) { 8.dp.toPx() }
    val maxOffset = contentWidth.coerceAtLeast(180f)

    LaunchedEffect(title, settling) {
        if (!settling) displayedTitle = title
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { contentWidth = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                if (latestOnClick.value != null) {
                    this.onClick {
                        latestOnClick.value?.invoke()
                        true
                    }
                }
                customActions = listOf(
                    CustomAccessibilityAction(label = previousLabel) {
                        latestOnPrevious.value()
                        true
                    },
                    CustomAccessibilityAction(label = nextLabel) {
                        latestOnNext.value()
                        true
                    },
                )
            }
            .pointerInput(maxOffset) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (latestSettling.value) {
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
                        val releasedOffset = dragOffset
                        settling = true
                        animationScope.launch {
                            try {
                                settleOffset.snapTo(releasedOffset)
                                if (direction == 0) {
                                    settleOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = 520f,
                                        ),
                                    )
                                } else {
                                    if (direction < 0) latestOnNext.value() else latestOnPrevious.value()
                                    settleOffset.animateTo(
                                        targetValue = direction * maxOffset,
                                        animationSpec = tween(durationMillis = 180),
                                    )
                                    settleOffset.snapTo(-direction * maxOffset)
                                    displayedTitle = latestTitle.value
                                    settleOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 180),
                                    )
                                }
                            } finally {
                                dragOffset = 0f
                                settling = false
                            }
                        }
                    } else {
                        latestOnClick.value?.invoke()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayedTitle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .graphicsLayer {
                    translationX = if (settling) settleOffset.value else dragOffset
                },
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = textStyle,
            color = textColor,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    }
}
