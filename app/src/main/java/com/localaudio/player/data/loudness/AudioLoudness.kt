package com.localaudio.player.data.loudness

import kotlin.math.log10

const val LOUDNESS_TARGET_LUFS = -14f
const val MIN_LOUDNESS_OFFSET_DB = -6
const val MAX_LOUDNESS_OFFSET_DB = 6
const val LOUDNESS_OFFSET_STEP_DB = 1
const val MIN_LOUDNESS_GAIN_DB = -12f
const val MAX_LOUDNESS_GAIN_DB = 6f
const val LOUDNESS_PEAK_CEILING_DB = -1f
const val LOUDNESS_ANALYSIS_VERSION = 1
const val LOUDNESS_SMOOTH_DURATION_MS = 5_000L

/** Loudness measurement cached by content hash. Values are linear peak and LUFS. */
data class AudioLoudness(
    val contentHash: String,
    val integratedLufs: Float,
    val peak: Float,
    val analysisVersion: Int,
    val analyzedAtMs: Long,
)

/** Calculates the bounded gain that should be applied to a measured audio track. */
fun AudioLoudness.gainDb(offsetDb: Int): Float {
    val measuredLufs = integratedLufs.takeIf { it.isFinite() } ?: LOUDNESS_TARGET_LUFS
    val normalizationGain = LOUDNESS_TARGET_LUFS - measuredLufs
    val peakDb = peak
        .coerceAtLeast(0f)
        .takeIf { it > 0f }
        ?.let { 20f * log10(it) }
    val peakLimitedNormalizationGain = if (peakDb != null && peakDb.isFinite()) {
        minOf(normalizationGain, LOUDNESS_PEAK_CEILING_DB - peakDb)
    } else {
        normalizationGain
    }
    return (peakLimitedNormalizationGain + offsetDb)
        .coerceIn(MIN_LOUDNESS_GAIN_DB, MAX_LOUDNESS_GAIN_DB)
}
