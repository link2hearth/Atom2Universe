package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.abs
import kotlin.math.pow

/**
 * Audio sample trimmer for removing silence and applying fades.
 *
 * Features:
 * - Automatic silence detection and removal
 * - Configurable threshold and minimum silence duration
 * - Fade in/out with multiple curve types
 */
class SampleTrimmer(private val sampleRate: Int = 44100) {

    enum class FadeCurve {
        LINEAR,      // Linear fade
        QUADRATIC,   // Quadratic (smooth)
        EXPONENTIAL, // Exponential (natural)
        S_CURVE      // S-curve (very smooth)
    }

    data class TrimResult(
        val samples: ShortArray,
        val trimmedStartMs: Int,
        val trimmedEndMs: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TrimResult
            return samples.contentEquals(other.samples) &&
                    trimmedStartMs == other.trimmedStartMs &&
                    trimmedEndMs == other.trimmedEndMs
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + trimmedStartMs
            result = 31 * result + trimmedEndMs
            return result
        }
    }

    /**
     * Remove silence from the beginning and end of the sample.
     *
     * @param samples Audio samples
     * @param thresholdDb Threshold in dB below which audio is considered silence
     * @param minSilenceMs Minimum silence duration to remove (keeps some pre-attack)
     * @param keepPreAttackMs Amount of silence to keep before the sound starts
     * @return TrimResult containing trimmed samples and trim info
     */
    fun trimSilence(
        samples: ShortArray,
        thresholdDb: Float = -40f,
        minSilenceMs: Int = 10,
        keepPreAttackMs: Int = 5
    ): TrimResult {
        if (samples.isEmpty()) return TrimResult(samples, 0, 0)

        val threshold = (Short.MAX_VALUE * 10f.pow(thresholdDb / 20f)).toInt()
        val minSilenceSamples = (minSilenceMs * sampleRate / 1000)
        val keepPreAttackSamples = (keepPreAttackMs * sampleRate / 1000)

        // Find start: first sample above threshold
        var startIndex = 0
        for (i in samples.indices) {
            if (abs(samples[i].toInt()) > threshold) {
                // Keep a small amount before the attack
                startIndex = maxOf(0, i - keepPreAttackSamples)
                break
            }
        }

        // Find end: last sample above threshold
        var endIndex = samples.lastIndex
        for (i in samples.indices.reversed()) {
            if (abs(samples[i].toInt()) > threshold) {
                // Keep a small tail
                endIndex = minOf(samples.lastIndex, i + minSilenceSamples)
                break
            }
        }

        // Validate indices
        if (endIndex <= startIndex) {
            return TrimResult(samples.copyOf(), 0, 0)
        }

        val trimmedStartMs = (startIndex * 1000 / sampleRate)
        val trimmedEndMs = ((samples.size - endIndex) * 1000 / sampleRate)

        return TrimResult(
            samples = samples.copyOfRange(startIndex, endIndex + 1),
            trimmedStartMs = trimmedStartMs,
            trimmedEndMs = trimmedEndMs
        )
    }

    /**
     * Apply fade in to the beginning of the sample.
     *
     * @param samples Audio samples
     * @param fadeMs Fade duration in milliseconds
     * @param curve Fade curve type
     * @return Samples with fade applied
     */
    fun applyFadeIn(
        samples: ShortArray,
        fadeMs: Int = 10,
        curve: FadeCurve = FadeCurve.QUADRATIC
    ): ShortArray {
        if (samples.isEmpty() || fadeMs <= 0) return samples.copyOf()

        val output = samples.copyOf()
        val fadeSamples = (fadeMs * sampleRate / 1000).coerceAtMost(samples.size)

        for (i in 0 until fadeSamples) {
            val progress = i.toFloat() / fadeSamples
            val gain = calculateFadeGain(progress, curve)
            output[i] = (output[i] * gain).toInt().toShort()
        }

        return output
    }

    /**
     * Apply fade out to the end of the sample.
     *
     * @param samples Audio samples
     * @param fadeMs Fade duration in milliseconds
     * @param curve Fade curve type
     * @return Samples with fade applied
     */
    fun applyFadeOut(
        samples: ShortArray,
        fadeMs: Int = 20,
        curve: FadeCurve = FadeCurve.QUADRATIC
    ): ShortArray {
        if (samples.isEmpty() || fadeMs <= 0) return samples.copyOf()

        val output = samples.copyOf()
        val fadeSamples = (fadeMs * sampleRate / 1000).coerceAtMost(samples.size)

        for (i in 0 until fadeSamples) {
            val index = samples.lastIndex - i
            val progress = i.toFloat() / fadeSamples
            val gain = calculateFadeGain(progress, curve) // Progress is inverted for fade out
            output[index] = (output[index] * gain).toInt().toShort()
        }

        return output
    }

    /**
     * Apply both fade in and fade out.
     *
     * @param samples Audio samples
     * @param fadeInMs Fade in duration in milliseconds
     * @param fadeOutMs Fade out duration in milliseconds
     * @param curve Fade curve type
     * @return Samples with fades applied
     */
    fun applyFades(
        samples: ShortArray,
        fadeInMs: Int = 5,
        fadeOutMs: Int = 20,
        curve: FadeCurve = FadeCurve.QUADRATIC
    ): ShortArray {
        if (samples.isEmpty()) return samples

        var output = samples.copyOf()

        if (fadeInMs > 0) {
            output = applyFadeIn(output, fadeInMs, curve)
        }

        if (fadeOutMs > 0) {
            output = applyFadeOut(output, fadeOutMs, curve)
        }

        return output
    }

    /**
     * Trim silence and apply fades in one operation.
     *
     * @param samples Audio samples
     * @param thresholdDb Silence threshold in dB
     * @param fadeInMs Fade in duration
     * @param fadeOutMs Fade out duration
     * @param curve Fade curve type
     * @return Processed samples
     */
    fun trimAndFade(
        samples: ShortArray,
        thresholdDb: Float = -40f,
        fadeInMs: Int = 5,
        fadeOutMs: Int = 20,
        curve: FadeCurve = FadeCurve.QUADRATIC
    ): ShortArray {
        val trimResult = trimSilence(samples, thresholdDb)
        return applyFades(trimResult.samples, fadeInMs, fadeOutMs, curve)
    }

    /**
     * Calculate fade gain based on curve type.
     */
    private fun calculateFadeGain(progress: Float, curve: FadeCurve): Float {
        return when (curve) {
            FadeCurve.LINEAR -> progress

            FadeCurve.QUADRATIC -> progress * progress

            FadeCurve.EXPONENTIAL -> {
                // Attempt to create natural-sounding exponential curve
                if (progress <= 0f) 0f
                else ((kotlin.math.exp(progress * 3) - 1) / (kotlin.math.exp(3f) - 1)).toFloat()
            }

            FadeCurve.S_CURVE -> {
                // Smooth S-curve using sine
                ((1 - kotlin.math.cos(progress * kotlin.math.PI)) / 2).toFloat()
            }
        }
    }

    /**
     * Detect the attack point (where the sound actually starts).
     * Useful for precise trimming of percussive sounds.
     *
     * @param samples Audio samples
     * @param thresholdDb Detection threshold in dB
     * @return Sample index of the attack, or 0 if not found
     */
    fun detectAttackPoint(samples: ShortArray, thresholdDb: Float = -20f): Int {
        if (samples.isEmpty()) return 0

        val threshold = (Short.MAX_VALUE * 10f.pow(thresholdDb / 20f)).toInt()

        // Use energy-based attack detection
        val windowSize = (sampleRate / 1000) // 1ms window
        var prevEnergy = 0f

        for (i in 0 until samples.size - windowSize step windowSize) {
            var energy = 0f
            for (j in 0 until windowSize) {
                val sample = samples[i + j].toFloat() / Short.MAX_VALUE
                energy += sample * sample
            }
            energy /= windowSize

            // Detect sudden increase in energy
            if (energy > prevEnergy * 10 && energy > 0.001f) {
                return i
            }

            prevEnergy = maxOf(energy, 0.0001f)
        }

        // Fallback: first sample above threshold
        for (i in samples.indices) {
            if (abs(samples[i].toInt()) > threshold) {
                return i
            }
        }

        return 0
    }
}
