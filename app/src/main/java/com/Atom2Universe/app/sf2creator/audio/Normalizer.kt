package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio normalizer supporting Peak and RMS normalization modes.
 *
 * Peak normalization: Adjusts gain so the loudest sample reaches target level.
 * RMS normalization: Adjusts gain based on average power (perceived loudness).
 */
class Normalizer {

    enum class Mode {
        PEAK,   // Normalize to highest peak
        RMS     // Normalize to average power (loudness)
    }

    /**
     * Normalize audio to target level.
     *
     * @param samples Audio samples
     * @param mode Normalization mode (PEAK or RMS)
     * @param targetDb Target level in dB (e.g., -1.0 for headroom, -3.0 for safety)
     * @return Normalized samples
     */
    fun normalize(samples: ShortArray, mode: Mode, targetDb: Float = -1f): ShortArray {
        if (samples.isEmpty()) return samples

        val currentLevel = when (mode) {
            Mode.PEAK -> measurePeak(samples)
            Mode.RMS -> measureRMS(samples)
        }

        if (currentLevel <= 0f) return samples.copyOf()

        val targetLinear = dbToLinear(targetDb)
        val gain = targetLinear / currentLevel

        // Limit gain to prevent excessive amplification of noise
        // and to prevent clipping on peaks when using RMS mode
        val safeGain = when (mode) {
            Mode.PEAK -> gain.coerceIn(0.1f, 10f)
            Mode.RMS -> {
                // For RMS, also check that peaks won't clip
                val peakAfterGain = measurePeak(samples) * gain
                if (peakAfterGain > 1f) {
                    gain / peakAfterGain * 0.99f // Leave 1% headroom
                } else {
                    gain.coerceIn(0.1f, 10f)
                }
            }
        }

        return applyGain(samples, safeGain)
    }

    /**
     * Measure peak level (0.0 to 1.0).
     */
    fun measurePeak(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f

        var maxAbs = 0
        for (sample in samples) {
            val absVal = abs(sample.toInt())
            if (absVal > maxAbs) maxAbs = absVal
        }
        return maxAbs.toFloat() / Short.MAX_VALUE
    }

    /**
     * Measure RMS level (0.0 to 1.0).
     */
    fun measureRMS(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f

        var sumSquares = 0.0
        for (sample in samples) {
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }

        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Measure level in dB.
     */
    fun measurePeakDb(samples: ShortArray): Float {
        val peak = measurePeak(samples)
        return if (peak > 0f) linearToDb(peak) else Float.NEGATIVE_INFINITY
    }

    /**
     * Measure RMS level in dB.
     */
    fun measureRMSDb(samples: ShortArray): Float {
        val rms = measureRMS(samples)
        return if (rms > 0f) linearToDb(rms) else Float.NEGATIVE_INFINITY
    }

    /**
     * Apply gain to samples.
     */
    fun applyGain(samples: ShortArray, gain: Float): ShortArray {
        return ShortArray(samples.size) { i ->
            (samples[i] * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * Apply gain in dB to samples.
     */
    fun applyGainDb(samples: ShortArray, gainDb: Float): ShortArray {
        return applyGain(samples, dbToLinear(gainDb))
    }

    /**
     * Convert dB to linear gain.
     */
    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    /**
     * Convert linear gain to dB.
     */
    private fun linearToDb(linear: Float): Float = 20f * kotlin.math.log10(linear)
}
