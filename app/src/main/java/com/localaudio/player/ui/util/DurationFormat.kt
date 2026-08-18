package com.localaudio.player.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.localaudio.player.R

@Composable
internal fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes % 60L == 0L) {
        stringResource(R.string.duration_hours, minutes / 60L)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
