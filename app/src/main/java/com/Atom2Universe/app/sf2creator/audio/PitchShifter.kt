package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * WSOLA-based pitch shifter that changes pitch without affecting duration.
 *
 * This implementation uses the WSOLA (Waveform Similarity Overlap-Add) algorithm
 * combined with resampling to achieve pitch shifting while preserving duration.
 *
 * The process:
 * 1. Calculate the pitch ratio (semitones to frequency ratio)
 * 2. Time-stretch to compensate for the duration change that resampling would cause
 * 3. Resample to achieve the desired pitch
 *
 * For example, to shift pitch up by 1 octave:
 * - Resampling alone would halve the duration (2x speed)
 * - So we first time-stretch by 2x (double duration)
 * - Then resample at 2x rate
 * - Net result: same duration, higher pitch
 */
class PitchShifter(
    private val sampleRate: Int = 44100
) {
    companion object {
        // WSOLA parameters optimized for musical content
        private const val WINDOW_SIZE_MS = 50      // Analysis window size in milliseconds
        private const val OVERLAP_MS = 25          // Overlap between windows
        private const val SEEK_WINDOW_MS = 15      // Search range for best overlap position
    }

    private val windowSize: Int = (WINDOW_SIZE_MS * sampleRate / 1000)
    private val overlap: Int = (OVERLAP_MS * sampleRate / 1000)
    private val seekWindow: Int = (SEEK_WINDOW_MS * sampleRate / 1000)
    private val hopSize: Int = windowSize - overlap

    /**
     * Shift the pitch of audio samples by the specified number of semitones.
     * Positive semitones = higher pitch, negative = lower pitch.
     * Duration is preserved (stays the same as input).
     *
     * @param samples Input audio samples (mono, normalized -1 to 1 as Short)
     * @param semitones Number of semitones to shift (-24 to +24 recommended)
     * @return Pitch-shifted samples with same duration as input
     */
    fun shiftPitch(samples: ShortArray, semitones: Float): ShortArray {
        if (samples.isEmpty() || semitones == 0f) {
            return samples.copyOf()
        }

        // Convert semitones to pitch ratio
        // Positive semitones = higher pitch = ratio > 1
        val pitchRatio = 2.0.pow(semitones / 12.0)

        // To maintain duration:
        // - If pitchRatio > 1 (higher pitch), resampling would shorten the audio
        //   So we first time-stretch to make it longer
        // - If pitchRatio < 1 (lower pitch), resampling would lengthen the audio
        //   So we first time-compress to make it shorter

        // Time stretch factor = pitchRatio (to compensate for resampling)
        val stretchedSamples = timeStretch(samples, pitchRatio)

        // Now resample to change pitch
        // Edge fades are handled by SamplePlayer.applyAntiClickFades() to avoid double fading
        return resample(stretchedSamples, pitchRatio)
    }

    /**
     * Apply smooth fades at the edges to prevent clicks.
     */
    private fun applyEdgeFades(samples: ShortArray): ShortArray {
        if (samples.size < 100) return samples

        val output = samples.copyOf()

        // Fade in: 5ms
        val fadeInSamples = (sampleRate * 0.005).toInt().coerceAtMost(samples.size / 8)
        for (i in 0 until fadeInSamples) {
            val progress = i.toFloat() / fadeInSamples
            // S-curve for smoothness (0 -> 1)
            val gain = ((1 - kotlin.math.cos(progress * kotlin.math.PI)) / 2).toFloat()
            output[i] = (output[i] * gain).toInt().toShort()
        }

        // Fade out: 15ms (longer for clean ending)
        val fadeOutSamples = (sampleRate * 0.015).toInt().coerceAtMost(samples.size / 8)
        for (i in 0 until fadeOutSamples) {
            val index = samples.lastIndex - i
            // Progress from 0 (at end) to 1 (at start of fade)
            val progress = (fadeOutSamples - 1 - i).toFloat() / fadeOutSamples
            // S-curve: 1 at start of fade, 0 at end
            val gain = ((1 + kotlin.math.cos((1 - progress) * kotlin.math.PI)) / 2).toFloat()
            output[index] = (output[index] * gain).toInt().toShort()
        }

        // Force last samples to zero
        val zeroSamples = minOf(16, samples.size)
        for (i in 0 until zeroSamples) {
            output[samples.lastIndex - i] = 0
        }

        return output
    }

    /**
     * WSOLA time-stretching algorithm.
     * Stretches or compresses audio duration without changing pitch.
     *
     * @param samples Input samples
     * @param stretchFactor Factor to stretch by (>1 = longer, <1 = shorter)
     * @return Time-stretched samples
     */
    private fun timeStretch(samples: ShortArray, stretchFactor: Double): ShortArray {
        if (stretchFactor == 1.0) return samples.copyOf()
        if (samples.size < windowSize * 2) {
            // Too short for WSOLA, use simple interpolation
            return simpleTimeStretch(samples, stretchFactor)
        }

        // Calculate output length
        val outputLength = (samples.size * stretchFactor).roundToInt()
        val output = FloatArray(outputLength)

        // Input hop (how much we advance in input)
        val inputHop = hopSize
        // Output hop (how much we advance in output) - stretched
        val outputHop = (inputHop * stretchFactor).roundToInt()

        // Create Hann window for smooth crossfading
        val window = createHannWindow(windowSize)

        var inputPos = 0
        var outputPos = 0
        var prevBestOffset = 0

        while (inputPos + windowSize < samples.size && outputPos + windowSize < outputLength) {
            // Find the best position in the seek window using cross-correlation
            val bestOffset = if (outputPos == 0) {
                0
            } else {
                findBestOverlapPosition(samples, inputPos, prevBestOffset)
            }
            prevBestOffset = bestOffset

            val actualInputPos = (inputPos + bestOffset).coerceIn(0, samples.size - windowSize)

            // Overlap-add with windowing
            for (i in 0 until windowSize) {
                if (outputPos + i < outputLength && actualInputPos + i < samples.size) {
                    val sample = samples[actualInputPos + i].toFloat() / Short.MAX_VALUE
                    output[outputPos + i] += sample * window[i]
                }
            }

            inputPos += inputHop
            outputPos += outputHop
        }

        // Handle remaining samples with crossfade to avoid clicks
        val remaining = samples.size - inputPos
        if (remaining > 0 && outputPos < outputLength) {
            val copyLen = minOf(remaining, outputLength - outputPos)
            // Use a fade-in for the remaining samples and fade-out for existing content
            val crossfadeLen = minOf(copyLen, overlap)

            for (i in 0 until copyLen) {
                if (inputPos + i < samples.size && outputPos + i < outputLength) {
                    val sample = samples[inputPos + i].toFloat() / Short.MAX_VALUE

                    if (i < crossfadeLen) {
                        // Crossfade region: blend existing output with new samples
                        val fadeIn = i.toFloat() / crossfadeLen
                        val fadeOut = 1f - fadeIn
                        output[outputPos + i] = output[outputPos + i] * fadeOut + sample * fadeIn
                    } else {
                        // After crossfade: just copy
                        output[outputPos + i] = sample
                    }
                }
            }
        }

        // Ensure output ends cleanly at zero
        val fadeOutLen = minOf(256, outputLength / 10)
        for (i in 0 until fadeOutLen) {
            val index = outputLength - 1 - i
            if (index >= 0) {
                val fade = i.toFloat() / fadeOutLen
                output[index] *= fade
            }
        }

        // Normalize and convert back to ShortArray
        return normalizeAndConvert(output)
    }

    /**
     * Find the best overlap position using normalized cross-correlation.
     * This finds where the waveforms align best for smooth crossfading.
     */
    private fun findBestOverlapPosition(samples: ShortArray, pos: Int, prevOffset: Int): Int {
        val searchStart = -seekWindow
        val searchEnd = seekWindow

        var bestOffset = 0
        var bestCorrelation = Float.MIN_VALUE

        // Reference position (where we expect to find good overlap)
        val refPos = (pos + prevOffset).coerceIn(0, samples.size - overlap)

        for (offset in searchStart..searchEnd) {
            val testPos = (pos + offset).coerceIn(0, samples.size - overlap)

            if (testPos + overlap > samples.size || refPos + overlap > samples.size) continue

            // Calculate cross-correlation for overlap region
            var correlation = 0f
            var energy1 = 0f
            var energy2 = 0f

            for (i in 0 until overlap) {
                val s1 = samples[refPos + i].toFloat()
                val s2 = samples[testPos + i].toFloat()
                correlation += s1 * s2
                energy1 += s1 * s1
                energy2 += s2 * s2
            }

            // Normalized cross-correlation
            val normalizer = kotlin.math.sqrt(energy1 * energy2)
            if (normalizer > 0) {
                correlation /= normalizer
            }

            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestOffset = offset
            }
        }

        return bestOffset
    }

    /**
     * Simple time-stretching for very short samples.
     * Uses linear interpolation.
     */
    private fun simpleTimeStretch(samples: ShortArray, stretchFactor: Double): ShortArray {
        val outputLength = (samples.size * stretchFactor).roundToInt()
        if (outputLength <= 0) return ShortArray(0)

        return ShortArray(outputLength) { i ->
            val srcPos = i / stretchFactor
            val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, samples.lastIndex)
            val frac = srcPos - idx0

            val sample0 = samples[idx0].toInt()
            val sample1 = samples[idx1].toInt()
            (sample0 + (sample1 - sample0) * frac).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Resample audio to change pitch.
     * Higher ratio = higher pitch (shorter if duration wasn't preserved).
     */
    private fun resample(samples: ShortArray, ratio: Double): ShortArray {
        if (ratio == 1.0) return samples.copyOf()

        // For pitch shifting: ratio > 1 means play faster (higher pitch)
        // Output length = input length / ratio
        val outputLength = (samples.size / ratio).roundToInt()
        if (outputLength <= 0) return ShortArray(0)

        return ShortArray(outputLength) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, samples.lastIndex)
            val frac = srcPos - idx0

            // Linear interpolation
            val sample0 = samples[idx0].toInt()
            val sample1 = samples[idx1].toInt()
            (sample0 + (sample1 - sample0) * frac).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Create a Hann window for smooth overlap-add.
     */
    private fun createHannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1 - kotlin.math.cos(2 * Math.PI * i / (size - 1)))).toFloat()
        }
    }

    /**
     * Normalize audio and convert to ShortArray.
     */
    private fun normalizeAndConvert(samples: FloatArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        // Find peak
        var maxAbs = 0f
        for (sample in samples) {
            val abs = sample.absoluteValue
            if (abs > maxAbs) maxAbs = abs
        }

        // Normalize to prevent clipping, but don't amplify quiet signals too much
        val normalizer = if (maxAbs > 1f) 1f / maxAbs else 1f

        return ShortArray(samples.size) { i ->
            (samples[i] * normalizer * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}

/**
 * Extension function to easily pitch-shift a sample.
 */
fun ShortArray.pitchShift(semitones: Float, sampleRate: Int = 44100): ShortArray {
    return PitchShifter(sampleRate).shiftPitch(this, semitones)
}
