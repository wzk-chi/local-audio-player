package com.localaudio.player.playback

import com.localaudio.player.data.model.AudioItem

sealed interface PlaybackCommand {
    data class PlayQueue(val items: List<AudioItem>, val index: Int) : PlaybackCommand
    data class JumpToItem(val index: Int) : PlaybackCommand
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data class SeekBy(val deltaMs: Long) : PlaybackCommand
    data class SetPlayMode(val repeatMode: Int, val shuffleEnabled: Boolean) : PlaybackCommand
    data class StartTimer(val durationMs: Long) : PlaybackCommand
    data object StopTimer : PlaybackCommand
}
