package com.localaudio.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

sealed interface PlayerEvent {
    val generation: Long

    data class Prepared(override val generation: Long, val durationMs: Long) : PlayerEvent
    data class Completed(override val generation: Long) : PlayerEvent
    data class Failed(override val generation: Long) : PlayerEvent
}

class PlatformPlayer(
    private val context: Context,
    private val onEvent: (PlayerEvent) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var currentGeneration = 0L
    private var preparedGeneration: Long? = null

    val generation: Long
        get() = currentGeneration

    val isPrepared: Boolean
        get() = mediaPlayer != null && preparedGeneration == currentGeneration

    fun load(uri: Uri): Long {
        release()
        val generation = ++currentGeneration
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setOnPreparedListener {
            if (mediaPlayer !== player || currentGeneration != generation) return@setOnPreparedListener
            preparedGeneration = generation
            onEvent(PlayerEvent.Prepared(generation, it.duration.toLong()))
        }
        player.setOnCompletionListener {
            if (mediaPlayer === player && currentGeneration == generation) {
                onEvent(PlayerEvent.Completed(generation))
            }
        }
        player.setOnErrorListener { _, _, _ ->
            if (mediaPlayer === player && currentGeneration == generation) {
                release()
                handler.post { onEvent(PlayerEvent.Failed(generation)) }
            }
            true
        }
        try {
            player.setDataSource(context, uri)
            player.prepareAsync()
        } catch (_: Exception) {
            if (mediaPlayer === player && currentGeneration == generation) {
                release()
                handler.post { onEvent(PlayerEvent.Failed(generation)) }
            }
        }
        return generation
    }

    fun play() {
        if (isPrepared) mediaPlayer?.start()
    }

    fun pause() {
        if (isPrepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        if (isPrepared) mediaPlayer?.seekTo(positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun positionMs(): Long = if (isPrepared) mediaPlayer?.currentPosition?.toLong() ?: 0L else 0L

    fun durationMs(): Long = if (isPrepared) mediaPlayer?.duration?.toLong() ?: 0L else 0L

    fun isPlaying(): Boolean = isPrepared && mediaPlayer?.isPlaying == true

    fun release() {
        preparedGeneration = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
}
