package com.localaudio.player.playback

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.localaudio.player.data.settings.EQUALIZER_BAND_COUNT
import com.localaudio.player.data.settings.EQUALIZER_FREQUENCIES_HZ
import com.localaudio.player.data.settings.EqualizerSettings
import kotlin.math.pow
import kotlin.math.roundToInt

sealed interface PlayerEvent {
    val generation: Long

    data class Prepared(override val generation: Long, val durationMs: Long) : PlayerEvent
    data class SeekCompleted(override val generation: Long) : PlayerEvent
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
    private var dynamicsProcessing: AudioEffect? = null
    private var legacyEqualizer: Equalizer? = null
    private var dynamicsProcessingUnavailable = false
    private var legacyEqualizerUnavailable = false
    private var appliedEqualizerSettings: EqualizerSettings? = null
    private var equalizerHeadroomDb = 0f
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
        player.setOnSeekCompleteListener {
            if (mediaPlayer === player && currentGeneration == generation) {
                onEvent(PlayerEvent.SeekCompleted(generation))
            }
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

    fun setGainAndVolume(
        gainDb: Float,
        volume: Float,
        equalizer: EqualizerSettings,
    ) {
        if (!isPrepared) return
        val normalizedVolume = volume.coerceIn(0f, 1f)
        val normalizedGain = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val enhancerGainDb = normalizedGain.coerceAtLeast(0f)
        if (appliedEqualizerSettings != equalizer) {
            equalizerHeadroomDb = applyEqualizer(equalizer)
            appliedEqualizerSettings = equalizer
        }
        val mediaGainDb = normalizedGain.coerceAtMost(0f) + equalizerHeadroomDb
        val mediaVolume = (normalizedVolume * 10f.pow(mediaGainDb / 20f)).coerceIn(0f, 1f)
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
        releaseDynamicsProcessing()
        releaseLegacyEqualizer()
        dynamicsProcessingUnavailable = false
        legacyEqualizerUnavailable = false
        appliedEqualizerSettings = null
        equalizerHeadroomDb = 0f
        preparedGeneration = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            player.setOnPreparedListener(null)
            player.setOnSeekCompleteListener(null)
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

    private fun applyEqualizer(settings: EqualizerSettings): Float {
        if (!settings.enabled) {
            disableAudioEffects()
            return 0f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !dynamicsProcessingUnavailable) {
            val effect = ensureDynamicsProcessing()
            if (effect != null && configureDynamicsProcessing(effect, settings)) {
                releaseLegacyEqualizer()
                return 0f
            }
            dynamicsProcessingUnavailable = true
            releaseDynamicsProcessing()
        }

        val effect = ensureLegacyEqualizer()
        if (effect != null && configureLegacyEqualizer(effect, settings)) {
            return -maxPositiveGainDb(settings)
        }
        return 0f
    }

    @TargetApi(28)
    private fun ensureDynamicsProcessing(): AudioEffect? {
        dynamicsProcessing?.let { return it }
        val sessionId = mediaPlayer?.audioSessionId ?: return null
        if (sessionId == 0) return null
        return runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                1,
                false,
                0,
                false,
                0,
                true,
                EQUALIZER_BAND_COUNT,
                true,
            ).build()
            DynamicsProcessing(0, sessionId, config)
        }.onFailure {
            dynamicsProcessingUnavailable = true
        }.getOrNull()
            ?.also { dynamicsProcessing = it }
    }

    @TargetApi(28)
    private fun configureDynamicsProcessing(
        effect: AudioEffect,
        settings: EqualizerSettings,
    ): Boolean = runCatching {
        val dynamics = effect as? DynamicsProcessing ?: return@runCatching false
        val equalizer = DynamicsProcessing.Eq(
            true,
            true,
            EQUALIZER_BAND_COUNT,
        )
        repeat(EQUALIZER_BAND_COUNT) { index ->
            equalizer.setBand(
                index,
                DynamicsProcessing.EqBand(
                    true,
                    EQUALIZER_FREQUENCIES_HZ[index].toFloat(),
                    settings.gainsDb.getOrElse(index) { 0 }.toFloat(),
                ),
            )
        }
        dynamics.setInputGainAllChannelsTo(-maxPositiveGainDb(settings))
        dynamics.setPostEqAllChannelsTo(equalizer)
        dynamics.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,
                true,
                0,
                5f,
                50f,
                10f,
                -1f,
                0f,
            ),
        )
        dynamics.enabled = true
    }.isSuccess

    private fun ensureLegacyEqualizer(): Equalizer? {
        if (legacyEqualizerUnavailable) return null
        legacyEqualizer?.let { return it }
        val sessionId = mediaPlayer?.audioSessionId ?: return null
        if (sessionId == 0) return null
        return runCatching { Equalizer(0, sessionId) }
            .onFailure { legacyEqualizerUnavailable = true }
            .getOrNull()
            ?.also { legacyEqualizer = it }
    }

    private fun configureLegacyEqualizer(
        effect: Equalizer,
        settings: EqualizerSettings,
    ): Boolean = runCatching {
        val bandCount = effect.numberOfBands.toInt()
        val levelRange = effect.bandLevelRange
        if (bandCount <= 0 || levelRange.size < 2) return@runCatching false
        val minimumLevel = levelRange[0].toInt()
        val maximumLevel = levelRange[1].toInt()
        repeat(bandCount) { bandIndex ->
            val centerFrequencyHz = effect.getCenterFreq(bandIndex.toShort()) / 1_000f
            val nearestPresetBand = EQUALIZER_FREQUENCIES_HZ.indices.minByOrNull { presetIndex ->
                kotlin.math.abs(centerFrequencyHz - EQUALIZER_FREQUENCIES_HZ[presetIndex])
            } ?: 0
            val level = (settings.gainsDb.getOrElse(nearestPresetBand) { 0 } * 100)
                .coerceIn(minimumLevel, maximumLevel)
                .toShort()
            effect.setBandLevel(bandIndex.toShort(), level)
        }
        effect.enabled = true
        true
    }.getOrElse {
        legacyEqualizerUnavailable = true
        releaseLegacyEqualizer()
        false
    }

    private fun maxPositiveGainDb(settings: EqualizerSettings): Float =
        settings.gainsDb.maxOrNull()?.coerceAtLeast(0)?.toFloat() ?: 0f

    private fun disableAudioEffects() {
        dynamicsProcessing?.let { runCatching { it.enabled = false } }
        legacyEqualizer?.let { runCatching { it.enabled = false } }
    }

    private fun releaseDynamicsProcessing() {
        dynamicsProcessing?.let { runCatching { it.release() } }
        dynamicsProcessing = null
    }

    private fun releaseLegacyEqualizer() {
        legacyEqualizer?.let { runCatching { it.release() } }
        legacyEqualizer = null
    }

    private companion object {
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 6f
    }
}
