package com.localaudio.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlin.math.pow
import kotlin.math.roundToInt

sealed interface PlayerEvent {
    val generation: Long

    data class Prepared(override val generation: Long, val durationMs: Long) : PlayerEvent
    data class Completed(override val generation: Long) : PlayerEvent
    data class Failed(override val generation: Long) : PlayerEvent
}

class PlatformPlayer(
    private val context: Context,
    private val onEvent: (PlayerEvent) -> Unit,
    private val onAudioFocusLost: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0 && isPlaying()) onAudioFocusLost()
        }
        .build()
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerUnavailable = false
    private var currentGeneration = 0L
    private var preparedGeneration: Long? = null

    val isPrepared: Boolean
        get() = mediaPlayer != null && preparedGeneration == currentGeneration

    fun load(uri: Uri): Long {
        release()
        val generation = ++currentGeneration
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(audioAttributes)
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

    fun play(): Boolean {
        if (!isPrepared) return false
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        mediaPlayer?.start()
        return true
    }

    fun setGainAndVolume(gainDb: Float, volume: Float) {
        if (!isPrepared) return
        val normalizedVolume = volume.coerceIn(0f, 1f)
        val normalizedGain = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val enhancerGainDb = normalizedGain.coerceAtLeast(0f)
        val mediaVolume = if (normalizedGain < 0f) {
            normalizedVolume * 10f.pow(normalizedGain / 20f)
        } else {
            normalizedVolume
        }.coerceIn(0f, 1f)
        ensureLoudnessEnhancer()?.let { enhancer ->
            runCatching {
                enhancer.setTargetGain((enhancerGainDb * 1_000f).roundToInt())
                if (!enhancer.enabled) enhancer.enabled = true
            }.onFailure {
                loudnessEnhancerUnavailable = true
                runCatching { enhancer.release() }
                loudnessEnhancer = null
            }
        }
        mediaPlayer?.setVolume(mediaVolume, mediaVolume)
    }

    fun pause() {
        if (isPrepared && mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        abandonAudioFocus()
    }

    fun seekTo(positionMs: Long) {
        if (isPrepared) mediaPlayer?.seekTo(positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun positionMs(): Long = if (isPrepared) mediaPlayer?.currentPosition?.toLong() ?: 0L else 0L

    fun durationMs(): Long = if (isPrepared) mediaPlayer?.duration?.toLong() ?: 0L else 0L

    fun isPlaying(): Boolean = isPrepared && mediaPlayer?.isPlaying == true

    fun release() {
        abandonAudioFocus()
        loudnessEnhancer?.let { enhancer -> runCatching { enhancer.release() } }
        loudnessEnhancer = null
        loudnessEnhancerUnavailable = false
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

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun ensureLoudnessEnhancer(): LoudnessEnhancer? {
        if (loudnessEnhancerUnavailable) return null
        loudnessEnhancer?.let { return it }
        val sessionId = mediaPlayer?.audioSessionId ?: return null
        if (sessionId == 0) return null
        return runCatching { LoudnessEnhancer(sessionId) }
            .onFailure { loudnessEnhancerUnavailable = true }
            .getOrNull()
            ?.also { loudnessEnhancer = it }
    }

    private companion object {
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 6f
    }
}
