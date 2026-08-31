package com.localaudio.player.playback

import com.localaudio.player.data.model.AudioItem

sealed interface PlaybackCommand {
    data class PlayQueue(val items: List<AudioItem>, val index: Int) : PlaybackCommand
    data class PreviewAutoSkip(
        val queue: List<AudioItem>,
        val index: Int,
        val startMs: Long,
        val endMs: Long,
    ) : PlaybackCommand
    data object CancelAutoSkipPreview : PlaybackCommand
    data class JumpToItem(val index: Int) : PlaybackCommand
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data class SeekBy(val deltaMs: Long) : PlaybackCommand
    data class RemoveItems(val keys: Set<String>) : PlaybackCommand
    data class ReplaceItem(val oldKey: String, val item: AudioItem) : PlaybackCommand
    data class SetPlayMode(val repeatMode: Int, val shuffleEnabled: Boolean) : PlaybackCommand
    data class StartTimer(val durationMs: Long) : PlaybackCommand
    data object StopTimer : PlaybackCommand
}
