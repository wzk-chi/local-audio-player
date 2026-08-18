package com.localaudio.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = label,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
internal fun PlayerTransportSegment(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 28.dp,
    active: Boolean = false,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WavyPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isSeeking = isDragged || isPressed
    var renderedValue by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    LaunchedEffect(value, isSeeking) {
        if (!isSeeking) renderedValue = value.coerceIn(0f, 1f)
    }
    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isSeeking) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "thumbInteraction",
    )
    val waveAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isSeeking) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "waveAmplitude",
    )
    val density = LocalDensity.current
    val trackStroke = remember(density) {
        Stroke(
            width = with(density) { 5.dp.toPx() },
            cap = StrokeCap.Round,
        )
    }
    val thumbRadiusPx = with(density) { 8.dp.toPx() }
    val thumbLineHeightPx = with(density) { 24.dp.toPx() }
    val trackEdgePaddingPx = with(density) { 8.dp.toPx() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = primaryColor.copy(alpha = 0.2f)

    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { renderedValue },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            color = primaryColor,
            trackColor = trackColor,
            stroke = trackStroke,
            trackStroke = trackStroke,
            gapSize = 12.dp * (1f + 0.1573f * waveAmplitude * waveAmplitude),
            stopSize = 3.dp,
            amplitude = { progress -> if (progress > 0f) waveAmplitude else 0f },
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStart = trackEdgePaddingPx
            val trackEnd = size.width - trackEdgePaddingPx
            val thumbX = trackStart + (trackEnd - trackStart) * renderedValue
            val thumbWidth = thumbRadiusPx * 2f * (1f - thumbInteractionFraction) +
                trackStroke.width * 1.2f * thumbInteractionFraction
            val thumbHeight = thumbRadiusPx * 2f * (1f - thumbInteractionFraction) +
                thumbLineHeightPx * thumbInteractionFraction
            val centerX = thumbX.coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
            val thumbCenterY = size.height / 2f

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(centerX - thumbWidth / 2f, thumbCenterY - thumbHeight / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f),
            )
        }
        Slider(
            value = renderedValue,
            onValueChange = { newValue ->
                renderedValue = newValue
                onValueChange(newValue)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
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
        )
    }
}
