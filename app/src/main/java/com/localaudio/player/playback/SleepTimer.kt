package com.localaudio.player.playback

import android.os.SystemClock

enum class TimerSource { MANUAL, AUTOMATIC }

data class SleepTimerState(
    val expireAt: Long = 0L,
    val durationMs: Long = 0L,
    val waitingForTrackEnd: Boolean = false,
    val source: TimerSource? = null,
)

sealed interface TimerDecision {
    data object None : TimerDecision
    data object StopNow : TimerDecision
    data object WaitForTrackEnd : TimerDecision
}

class SleepTimer(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    fun start(durationMs: Long, source: TimerSource): SleepTimerState {
        val actualDurationMs = durationMs.coerceAtLeast(1_000L)
        return SleepTimerState(
            expireAt = now() + actualDurationMs,
            durationMs = actualDurationMs,
            source = source,
        )
    }

    fun stop(): SleepTimerState = SleepTimerState()

    fun check(
        state: SleepTimerState,
        isPlaying: Boolean,
        waitForCurrentEnd: Boolean,
    ): TimerDecision {
        if (state.waitingForTrackEnd || state.expireAt <= 0L || state.expireAt > now()) {
            return TimerDecision.None
        }
        if (!isPlaying || !waitForCurrentEnd) return TimerDecision.StopNow
        return TimerDecision.WaitForTrackEnd
    }

    fun remainingMs(state: SleepTimerState): Long = if (state.expireAt > 0L) {
        (state.expireAt - now()).coerceAtLeast(0L)
    } else {
        0L
    }
}
