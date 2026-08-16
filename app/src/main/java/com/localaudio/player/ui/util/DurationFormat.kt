package com.localaudio.player.ui.util

internal fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes % 60L == 0L) "${minutes / 60L} 小时" else "$minutes 分钟"
}
