package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Crossfader for smooth transitions between audio segments.
 *
 * Used primarily for:
 * - Loop point crossfades (eliminate clicks at loop junction)
 * - Sample concatenation
 * - Fade transitions
 */
class Crossfader(private val sampleRate: Int = 44100) {

    enum class CurveType {
        LINEAR,       // Simple linear fade
        EQUAL_POWER,  // Constant power (best for loops)
        EQUAL_GAIN,   // Constant gain (sum = 1)
        S_CURVE,      // Smooth S-curve
        LOGARITHMIC   // Natural sounding for music
    }

    /**
     * Apply crossfade at loop points to eliminate clicks (in-place modification).
     * The end of the loop is blended with the pre-loop audio (the audio
     * just before loopStart) so that the last crossfaded sample naturally
     * leads into loopStart when the synthesizer wraps.
     *
     * @param samples Audio samples (modified in-place)
     * @param loopStart Start of the loop region (inclusive)
     * @param loopEnd End of the loop region (inclusive — last sample in the loop)
     * @param crossfadeMs Duration of crossfade in milliseconds
     * @param curveType Type of crossfade curve
     */
    fun applyCrossfadeLoopInPlace(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int,
        crossfadeMs: Int = 20,
        curveType: CurveType = CurveType.EQUAL_POWER
    ) {
        if (samples.isEmpty() || loopEnd <= loopStart) return

        val loopLength = loopEnd - loopStart + 1
        val crossfadeSamples = (crossfadeMs * sampleRate / 1000)
            .coerceAtMost(loopLength / 2)
            .coerceAtMost(loopStart)

        if (crossfadeSamples <= 0) return

        // Pre-calculate gains to avoid allocating Pair objects in the loop
        val fadeOutGains = FloatArray(crossfadeSamples)
        val fadeInGains = FloatArray(crossfadeSamples)
        for (i in 0 until crossfadeSamples) {
            val progress = i.toFloat() / crossfadeSamples
            when (curveType) {
                CurveType.LINEAR -> {
                    fadeOutGains[i] = 1f - progress
                    fadeInGains[i] = progress
                }
                CurveType.EQUAL_POWER -> {
                    val angle = progress * PI.toFloat() / 2f
                    fadeOutGains[i] = cos(angle)
                    fadeInGains[i] = sin(angle)
                }
                CurveType.EQUAL_GAIN -> {
                    val fadeIn = progress * progress
                    val fadeOut = (1f - progress) * (1f - progress)
                    val normalizer = fadeIn + fadeOut
                    if (normalizer > 0) {
                        fadeOutGains[i] = fadeOut / normalizer
                        fadeInGains[i] = fadeIn / normalizer
                    } else {
                        fadeOutGains[i] = 0.5f
                        fadeInGains[i] = 0.5f
                    }
                }
                CurveType.S_CURVE -> {
                    val fadeIn = ((1 - cos(progress * PI)) / 2).toFloat()
                    fadeInGains[i] = fadeIn
                    fadeOutGains[i] = 1f - fadeIn
                }
                CurveType.LOGARITHMIC -> {
                    fadeInGains[i] = if (progress <= 0f) 0f else {
                        (kotlin.math.log10(progress * 9f + 1f)).toFloat()
                    }
                    fadeOutGains[i] = if (progress >= 1f) 0f else {
                        (kotlin.math.log10((1f - progress) * 9f + 1f)).toFloat()
                    }
                }
            }
        }

        // Apply crossfade in-place
        for (i in 0 until crossfadeSamples) {
            val endIndex = loopEnd + 1 - crossfadeSamples + i
            val preLoopIndex = loopStart - crossfadeSamples + i

            if (endIndex in samples.indices && preLoopIndex in samples.indices) {
                val endSample = samples[endIndex].toFloat()
                val preLoopSample = samples[preLoopIndex].toFloat()

                val blended = (endSample * fadeOutGains[i] + preLoopSample * fadeInGains[i]).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                samples[endIndex] = blended.toShort()
            }
        }
    }

    /**
     * Apply crossfade at loop points to eliminate clicks.
     * The end of the loop is blended with the pre-loop audio (the audio
     * just before loopStart) so that the last crossfaded sample naturally
     * leads into loopStart when the synthesizer wraps.
     *
     * @param samples Audio samples
     * @param loopStart Start of the loop region (inclusive)
     * @param loopEnd End of the loop region (inclusive — last sample in the loop)
     * @param crossfadeMs Duration of crossfade in milliseconds
     * @param curveType Type of crossfade curve
     * @return Samples with crossfade applied
     */
    fun applyCrossfadeLoop(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int,
        crossfadeMs: Int = 20,
        curveType: CurveType = CurveType.EQUAL_POWER
    ): ShortArray {
        if (samples.isEmpty() || loopEnd <= loopStart) return samples.copyOf()

        val output = samples.copyOf()
        applyCrossfadeLoopInPlace(output, loopStart, loopEnd, crossfadeMs, curveType)
        return output
    }

    /**
     * Apply crossfade between two separate audio segments.
     * Useful for concatenating samples.
     *
     * @param samplesA First audio segment
     * @param samplesB Second audio segment
     * @param crossfadeMs Duration of crossfade
     * @param curveType Type of crossfade curve
     * @return Concatenated audio with crossfade
     */
    fun crossfadeSegments(
        samplesA: ShortArray,
        samplesB: ShortArray,
        crossfadeMs: Int = 20,
        curveType: CurveType = CurveType.EQUAL_POWER
    ): ShortArray {
        if (samplesA.isEmpty()) return samplesB.copyOf()
        if (samplesB.isEmpty()) return samplesA.copyOf()

        val crossfadeSamples = (crossfadeMs * sampleRate / 1000)
            .coerceAtMost(samplesA.size)
            .coerceAtMost(samplesB.size)

        // Output length: A + B - crossfade overlap
        val outputLength = samplesA.size + samplesB.size - crossfadeSamples
        val output = ShortArray(outputLength)

        // Copy first segment (up to crossfade region)
        for (i in 0 until samplesA.size - crossfadeSamples) {
            output[i] = samplesA[i]
        }

        // Crossfade region
        for (i in 0 until crossfadeSamples) {
            val progress = i.toFloat() / crossfadeSamples
            val (fadeOut, fadeIn) = calculateCrossfadeGains(progress, curveType)

            val aIndex = samplesA.size - crossfadeSamples + i
            val bIndex = i
            val outIndex = samplesA.size - crossfadeSamples + i

            val sampleA = samplesA[aIndex].toFloat()
            val sampleB = samplesB[bIndex].toFloat()

            val blended = (sampleA * fadeOut + sampleB * fadeIn).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            output[outIndex] = blended.toShort()
        }

        // Copy second segment (after crossfade region)
        for (i in crossfadeSamples until samplesB.size) {
            val outIndex = samplesA.size - crossfadeSamples + i
            output[outIndex] = samplesB[i]
        }

        return output
    }

    /**
     * Apply fade in to the beginning of samples.
     *
     * @param samples Audio samples
     * @param fadeMs Fade duration in milliseconds
     * @param curveType Type of fade curve
     * @return Samples with fade applied
     */
    fun fadeIn(
        samples: ShortArray,
        fadeMs: Int = 10,
        curveType: CurveType = CurveType.S_CURVE
    ): ShortArray {
        if (samples.isEmpty() || fadeMs <= 0) return samples.copyOf()

        val output = samples.copyOf()
        val fadeSamples = (fadeMs * sampleRate / 1000).coerceAtMost(samples.size)

        for (i in 0 until fadeSamples) {
            val progress = i.toFloat() / fadeSamples
            val gain = calculateFadeInGain(progress, curveType)
            output[i] = (output[i] * gain).toInt().toShort()
        }

        return output
    }

    /**
     * Apply fade out to the end of samples.
     *
     * @param samples Audio samples
     * @param fadeMs Fade duration in milliseconds
     * @param curveType Type of fade curve
     * @return Samples with fade applied
     */
    fun fadeOut(
        samples: ShortArray,
        fadeMs: Int = 20,
        curveType: CurveType = CurveType.S_CURVE
    ): ShortArray {
        if (samples.isEmpty() || fadeMs <= 0) return samples.copyOf()

        val output = samples.copyOf()
        val fadeSamples = (fadeMs * sampleRate / 1000).coerceAtMost(samples.size)

        for (i in 0 until fadeSamples) {
            val index = samples.lastIndex - i
            val progress = i.toFloat() / fadeSamples // Progress from end
            val gain = calculateFadeInGain(progress, curveType) // Fade out = reverse fade in
            output[index] = (output[index] * gain).toInt().toShort()
        }

        return output
    }

    /**
     * Apply both fade in and fade out.
     */
    fun applyFades(
        samples: ShortArray,
        fadeInMs: Int = 5,
        fadeOutMs: Int = 10,
        curveType: CurveType = CurveType.S_CURVE
    ): ShortArray {
        var output = samples
        if (fadeInMs > 0) {
            output = fadeIn(output, fadeInMs, curveType)
        }
        if (fadeOutMs > 0) {
            output = fadeOut(output, fadeOutMs, curveType)
        }
        return output
    }

    /**
     * Calculate crossfade gains for a given progress value.
     * Returns (fadeOut, fadeIn) gains that sum appropriately based on curve type.
     */
    private fun calculateCrossfadeGains(progress: Float, curveType: CurveType): Pair<Float, Float> {
        return when (curveType) {
            CurveType.LINEAR -> {
                Pair(1f - progress, progress)
            }
            CurveType.EQUAL_POWER -> {
                // Constant power: maintains perceived loudness
                val angle = progress * PI.toFloat() / 2f
                Pair(cos(angle), sin(angle))
            }
            CurveType.EQUAL_GAIN -> {
                // Constant gain: fadeOut + fadeIn = 1
                val fadeIn = progress * progress // Quadratic for smooth start
                val fadeOut = (1f - progress) * (1f - progress)
                val normalizer = fadeIn + fadeOut
                if (normalizer > 0) {
                    Pair(fadeOut / normalizer, fadeIn / normalizer)
                } else {
                    Pair(0.5f, 0.5f)
                }
            }
            CurveType.S_CURVE -> {
                // Smooth S-curve using cosine
                val fadeIn = ((1 - cos(progress * PI)) / 2).toFloat()
                val fadeOut = 1f - fadeIn
                Pair(fadeOut, fadeIn)
            }
            CurveType.LOGARITHMIC -> {
                // Natural sounding logarithmic curve
                val fadeIn = if (progress <= 0f) 0f else {
                    (kotlin.math.log10(progress * 9f + 1f)).toFloat()
                }
                val fadeOut = if (progress >= 1f) 0f else {
                    (kotlin.math.log10((1f - progress) * 9f + 1f)).toFloat()
                }
                Pair(fadeOut, fadeIn)
            }
        }
    }

    /**
     * Calculate fade in gain for a given progress value.
     */
    private fun calculateFadeInGain(progress: Float, curveType: CurveType): Float {
        return when (curveType) {
            CurveType.LINEAR -> progress
            CurveType.EQUAL_POWER -> sin(progress * PI.toFloat() / 2f)
            CurveType.EQUAL_GAIN -> sqrt(progress)
            CurveType.S_CURVE -> ((1 - cos(progress * PI)) / 2).toFloat()
            CurveType.LOGARITHMIC -> {
                if (progress <= 0f) 0f
                else (kotlin.math.log10(progress * 9f + 1f)).toFloat()
            }
        }
    }

    /**
     * Get recommended crossfade duration based on sample characteristics.
     */
    fun recommendCrossfadeMs(samples: ShortArray, loopLengthMs: Int): Int {
        // Crossfade should be:
        // - At least 5ms to avoid clicks
        // - At most 10% of loop length
        // - Longer for lower frequency content

        val maxCrossfade = (loopLengthMs * 0.1).toInt()

        // Estimate fundamental frequency from zero-crossing rate
        var zeroCrossings = 0
        for (i in 0 until samples.size - 1) {
            if ((samples[i] >= 0 && samples[i + 1] < 0) ||
                (samples[i] < 0 && samples[i + 1] >= 0)) {
                zeroCrossings++
            }
        }

        val estimatedFreq = zeroCrossings * sampleRate / (2 * samples.size)

        // Lower frequencies need longer crossfades
        val recommendedMs = when {
            estimatedFreq < 100 -> 30   // Bass
            estimatedFreq < 300 -> 20   // Low-mid
            estimatedFreq < 1000 -> 15  // Mid
            else -> 10                   // High
        }

        return recommendedMs.coerceIn(5, maxCrossfade.coerceAtLeast(5))
    }
}
