package com.localaudio.player.data.settings

const val EQUALIZER_BAND_COUNT = 5
const val EQUALIZER_MIN_GAIN_DB = -6
const val EQUALIZER_MAX_GAIN_DB = 6

val EQUALIZER_FREQUENCIES_HZ = listOf(60, 230, 1_000, 4_000, 14_000)
val EQUALIZER_FLAT_GAINS_DB = List(EQUALIZER_BAND_COUNT) { 0 }

enum class EqualizerPreset {
    FLAT,
    VOCAL,
    BASS,
    POP,
    ROCK,
    CLASSICAL,
    CUSTOM,
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val gainsDb: List<Int> = EQUALIZER_FLAT_GAINS_DB,
)

fun EqualizerPreset.defaultGainsDb(): List<Int> = when (this) {
    EqualizerPreset.FLAT -> listOf(0, 0, 0, 0, 0)
    EqualizerPreset.VOCAL -> listOf(-2, -1, 1, 3, 2)
    EqualizerPreset.BASS -> listOf(5, 3, 1, -1, -2)
    EqualizerPreset.POP -> listOf(2, 1, 0, 2, 3)
    EqualizerPreset.ROCK -> listOf(4, 2, -1, 2, 4)
    EqualizerPreset.CLASSICAL -> listOf(3, 1, 0, 2, 3)
    EqualizerPreset.CUSTOM -> EQUALIZER_FLAT_GAINS_DB
}

fun normalizeEqualizerGains(values: List<Int>): List<Int> =
    (0 until EQUALIZER_BAND_COUNT).map { index ->
        values.getOrNull(index)
            ?.coerceIn(EQUALIZER_MIN_GAIN_DB, EQUALIZER_MAX_GAIN_DB)
            ?: 0
    }
