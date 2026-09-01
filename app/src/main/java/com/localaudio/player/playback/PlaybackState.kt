package com.localaudio.player.playback

import com.localaudio.player.data.settings.REPEAT_ALL
import com.localaudio.player.data.model.AudioItem

data class PlaybackState(
    val queue: List<AudioItem> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val repeatMode: Int = REPEAT_ALL,
    val shuffleEnabled: Boolean = false,
    val timerExpireAt: Long = 0L,
    val activeTimerDurationMs: Long = 0L,
    val timerRemainingMs: Long = 0L,
    val timerWaitingForEnd: Boolean = false,
    val loudnessEnabled: Boolean = true,
    val loudnessOffsetDb: Int = 0,
    val loudnessGainDb: Float? = null,
    val loudnessAnalyzing: Boolean = false,
) {
    val currentItem: AudioItem?
        get() = queue.getOrNull(currentIndex)

    val timerActive: Boolean
        get() = timerExpireAt > 0L || timerWaitingForEnd
}
