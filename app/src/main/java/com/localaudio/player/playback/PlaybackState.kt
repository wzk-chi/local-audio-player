package com.localaudio.player.playback

import com.localaudio.player.data.model.AudioItem

data class PlaybackState(
    val queue: List<AudioItem> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val timerExpireAt: Long = 0L,
    val activeTimerDurationMs: Long = 0L,
    val timerRemainingMs: Long = 0L,
    val timerWaitingForEnd: Boolean = false,
    val timerSource: TimerSource? = null,
) {
    val currentItem: AudioItem?
        get() = queue.getOrNull(currentIndex)

    val timerActive: Boolean
        get() = timerExpireAt > 0L || timerWaitingForEnd
}
