package com.localaudio.player.data.settings

import com.localaudio.player.data.model.FolderLocation

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class HomeHeaderMode { FIXED, HIDDEN, AUTO }

const val REPEAT_OFF = 0
const val REPEAT_ONE = 1
const val REPEAT_ALL = 2

val DEFAULT_TIMER_DURATIONS_MS = listOf(
    30 * 60_000L,
    60 * 60_000L,
    2 * 60 * 60_000L,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val homeHeaderMode: HomeHeaderMode = HomeHeaderMode.FIXED,
    val homeListBottomAligned: Boolean = false,
    val showAlbumCover: Boolean = true,
    val showWhenLocked: Boolean = false,
    val timerEnabled: Boolean = true,
    val timerDurationMs: Long = 30 * 60_000L,
    val timerDurationOptionsMs: List<Long> = DEFAULT_TIMER_DURATIONS_MS,
    val waitForCurrentEnd: Boolean = true,
    val seekStepMs: Long = 10_000L,
    val repeatMode: Int = REPEAT_ALL,
    val shuffleEnabled: Boolean = false,
    val savedHomeLocation: FolderLocation? = null,
)
