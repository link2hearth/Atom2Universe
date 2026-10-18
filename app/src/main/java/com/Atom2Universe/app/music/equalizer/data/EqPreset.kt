package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an equalizer preset with 10 frequency bands plus bass boost and virtualizer.
 * Band values are in millibels (-1200 to +1200 = -12dB to +12dB).
 * BassBoost and Virtualizer strengths are 0-1000.
 */
@Entity(tableName = "eq_presets")
data class EqPreset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** True for built-in presets (Flat, Rock, Pop, etc.) that cannot be deleted */
    val isSystemPreset: Boolean = false,

    // 10-band equalizer values in millibels (-1200 to +1200)
    val band32Hz: Int = 0,
    val band64Hz: Int = 0,
    val band125Hz: Int = 0,
    val band250Hz: Int = 0,
    val band500Hz: Int = 0,
    val band1kHz: Int = 0,
    val band2kHz: Int = 0,
    val band4kHz: Int = 0,
    val band8kHz: Int = 0,
    val band16kHz: Int = 0,

    /** Bass boost strength (0-1000) */
    val bassBoostStrength: Int = 0,

    /** Virtualizer/surround strength (0-1000) */
    val virtualizerStrength: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_BAND_LEVEL = -1200
        const val MAX_BAND_LEVEL = 1200
        const val MIN_EFFECT_STRENGTH = 0
        const val MAX_EFFECT_STRENGTH = 1000

        /** Band center frequencies in Hz */
        val BAND_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Create a flat (neutral) preset */
        fun flat(name: String = "Flat", isSystem: Boolean = false) = EqPreset(
            name = name,
            isSystemPreset = isSystem
        )
    }

    /** Get all band values as a list (ordered by frequency) */
    fun getBandLevels(): List<Int> = listOf(
        band32Hz, band64Hz, band125Hz, band250Hz, band500Hz,
        band1kHz, band2kHz, band4kHz, band8kHz, band16kHz
    )

    /** Create a copy with updated band level at specified index (0-9) */
    fun withBandLevel(index: Int, level: Int): EqPreset {
        val clampedLevel = level.coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL)
        return when (index) {
            0 -> copy(band32Hz = clampedLevel)
            1 -> copy(band64Hz = clampedLevel)
            2 -> copy(band125Hz = clampedLevel)
            3 -> copy(band250Hz = clampedLevel)
            4 -> copy(band500Hz = clampedLevel)
            5 -> copy(band1kHz = clampedLevel)
            6 -> copy(band2kHz = clampedLevel)
            7 -> copy(band4kHz = clampedLevel)
            8 -> copy(band8kHz = clampedLevel)
            9 -> copy(band16kHz = clampedLevel)
            else -> this
        }
    }

    /** Create a copy with all band levels from a list */
    fun withBandLevels(levels: List<Int>): EqPreset {
        require(levels.size == 10) { "Must provide exactly 10 band levels" }
        return copy(
            band32Hz = levels[0].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band64Hz = levels[1].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band125Hz = levels[2].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band250Hz = levels[3].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band500Hz = levels[4].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band1kHz = levels[5].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band2kHz = levels[6].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band4kHz = levels[7].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band8kHz = levels[8].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL),
            band16kHz = levels[9].coerceIn(MIN_BAND_LEVEL, MAX_BAND_LEVEL)
        )
    }
}
