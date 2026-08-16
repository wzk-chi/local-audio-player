package com.localaudio.player.playback

data class SleepTimerState(
    val expireAt: Long = 0L,
    val waitingForTrackEnd: Boolean = false,
)

sealed interface TimerDecision {
    data object None : TimerDecision
    data object StopNow : TimerDecision
    data object WaitForTrackEnd : TimerDecision
}

class SleepTimer(
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun start(durationMs: Long): SleepTimerState = SleepTimerState(
        expireAt = now() + durationMs.coerceAtLeast(1_000L),
    )

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
