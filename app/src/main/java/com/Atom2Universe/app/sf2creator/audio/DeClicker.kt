package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Audio de-clicker and artifact remover.
 *
 * Features:
 * - Click/pop detection and interpolation
 * - DC offset removal
 * - Soft clipping (prevents harsh digital clipping)
 * - Smoothing filter for harsh transients
 */
class DeClicker(private val sampleRate: Int = 44100) {

    /**
     * Full cleanup: DC removal + de-clicking + soft clipping.
     */
    fun fullCleanup(
        samples: ShortArray,
        removeDC: Boolean = true,
        deClick: Boolean = true,
        softClip: Boolean = true
    ): ShortArray {
        if (samples.isEmpty()) return samples

        var processed = samples.copyOf()

        // Step 1: Remove DC offset
        if (removeDC) {
            processed = removeDCOffset(processed)
        }

        // Step 2: Detect and fix clicks
        if (deClick) {
            processed = removeClicks(processed)
        }

        // Step 3: Apply soft clipping
        if (softClip) {
            processed = applySoftClipping(processed)
        }

        return processed
    }

    /**
     * Remove DC offset (constant component that shifts waveform from zero).
     * DC offset can cause clicks when starting/stopping playback.
     */
    fun removeDCOffset(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples

        // Calculate DC offset (average of all samples)
        var sum = 0L
        for (sample in samples) {
            sum += sample.toLong()
        }
        val dcOffset = (sum / samples.size).toInt()

        if (abs(dcOffset) < 10) {
            // Negligible DC offset
            return samples.copyOf()
        }

        // Remove DC offset
        return ShortArray(samples.size) { i ->
            (samples[i] - dcOffset).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Detect and remove clicks/pops using interpolation.
     * Clicks are detected as sudden large amplitude changes.
     */
    fun removeClicks(
        samples: ShortArray,
        sensitivity: Float = 0.3f,  // Lower = more aggressive
        windowMs: Float = 0.5f      // Detection window in ms
    ): ShortArray {
        if (samples.size < 100) return samples.copyOf()

        val output = samples.copyOf()
        val windowSize = (windowMs * sampleRate / 1000).toInt().coerceAtLeast(3)

        // Calculate local statistics for click detection
        val threshold = (Short.MAX_VALUE * sensitivity).toInt()

        var i = windowSize
        while (i < samples.size - windowSize) {
            val current = samples[i].toInt()
            val prev = samples[i - 1].toInt()
            val next = samples[i + 1].toInt()

            // Calculate expected value based on neighbors
            val expected = (prev + next) / 2
            val deviation = abs(current - expected)

            // Also check for sudden direction changes
            val prevDiff = current - prev
            val nextDiff = next - current
            val isDirectionChange = sign(prevDiff.toFloat()) != sign(nextDiff.toFloat())
            val isLargeChange = abs(prevDiff) > threshold && abs(nextDiff) > threshold

            // Detect click: large deviation from expected AND direction change
            if (deviation > threshold && isDirectionChange && isLargeChange) {
                // Interpolate to fix the click
                output[i] = interpolateSample(output, i, windowSize)

                // Also smooth a small area around the click
                for (j in 1 until windowSize / 2) {
                    if (i - j >= 0) {
                        output[i - j] = smoothSample(output, i - j)
                    }
                    if (i + j < output.size) {
                        output[i + j] = smoothSample(output, i + j)
                    }
                }
            }

            i++
        }

        return output
    }

    /**
     * Interpolate a sample value based on surrounding samples.
     */
    private fun interpolateSample(samples: ShortArray, index: Int, windowSize: Int): Short {
        var sum = 0.0
        var weight = 0.0

        for (offset in -windowSize..windowSize) {
            if (offset == 0) continue
            val idx = index + offset
            if (idx in samples.indices) {
                val w = 1.0 / abs(offset) // Closer samples have more weight
                sum += samples[idx] * w
                weight += w
            }
        }

        return if (weight > 0) {
            (sum / weight).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        } else {
            samples[index]
        }
    }

    /**
     * Smooth a single sample with its neighbors.
     */
    private fun smoothSample(samples: ShortArray, index: Int): Short {
        if (index <= 0 || index >= samples.size - 1) return samples[index]

        val prev = samples[index - 1].toInt()
        val current = samples[index].toInt()
        val next = samples[index + 1].toInt()

        // Weighted average (current has more weight)
        val smoothed = (prev + current * 2 + next) / 4
        return smoothed.toShort()
    }

    /**
     * Apply soft clipping to prevent harsh digital clipping.
     * Uses a tanh-like curve for natural saturation.
     */
    fun applySoftClipping(samples: ShortArray, threshold: Float = 0.9f): ShortArray {
        if (samples.isEmpty()) return samples

        val thresholdValue = (Short.MAX_VALUE * threshold).toInt()

        return ShortArray(samples.size) { i ->
            val sample = samples[i].toInt()
            val absSample = abs(sample)

            if (absSample <= thresholdValue) {
                // Below threshold: pass through unchanged
                sample.toShort()
            } else {
                // Above threshold: apply soft curve
                val excess = absSample - thresholdValue
                val maxExcess = Short.MAX_VALUE - thresholdValue
                val ratio = excess.toFloat() / maxExcess

                // Soft curve: asymptotic approach to max
                val softened = thresholdValue + (maxExcess * softCurve(ratio)).toInt()
                (sign(sample.toFloat()) * softened).toInt().toShort()
            }
        }
    }

    /**
     * Soft curve function (approximates tanh behavior).
     * Maps 0..1 to 0..1 with decreasing slope.
     */
    private fun softCurve(x: Float): Float {
        // Simple soft knee: x / (1 + x)
        return x / (1f + x)
    }

    /**
     * Apply a gentle low-pass filter to reduce harshness.
     * Useful for smoothing pitch-shifted audio.
     */
    fun applyGentleSmoothing(samples: ShortArray, strength: Float = 0.3f): ShortArray {
        if (samples.size < 3) return samples.copyOf()

        val output = ShortArray(samples.size)
        val alpha = 1f - strength.coerceIn(0f, 0.9f)

        output[0] = samples[0]
        for (i in 1 until samples.size) {
            val filtered = alpha * samples[i] + (1f - alpha) * output[i - 1]
            output[i] = filtered.toInt().toShort()
        }

        return output
    }

    /**
     * Detect clicks in the audio and return their positions.
     * Useful for diagnostics.
     */
    fun detectClickPositions(samples: ShortArray, sensitivity: Float = 0.3f): List<Int> {
        if (samples.size < 10) return emptyList()

        val clicks = mutableListOf<Int>()
        val threshold = (Short.MAX_VALUE * sensitivity).toInt()

        for (i in 1 until samples.size - 1) {
            val current = samples[i].toInt()
            val prev = samples[i - 1].toInt()
            val next = samples[i + 1].toInt()

            val expected = (prev + next) / 2
            val deviation = abs(current - expected)

            val prevDiff = current - prev
            val nextDiff = next - current
            val isDirectionChange = sign(prevDiff.toFloat()) != sign(nextDiff.toFloat())

            if (deviation > threshold && isDirectionChange) {
                clicks.add(i)
            }
        }

        return clicks
    }

    /**
     * Get statistics about audio quality issues.
     */
    fun analyzeQuality(samples: ShortArray): QualityReport {
        if (samples.isEmpty()) {
            return QualityReport(0f, 0, false, 0f)
        }

        // Calculate DC offset
        var sum = 0L
        for (sample in samples) {
            sum += sample.toLong()
        }
        val dcOffset = (sum.toFloat() / samples.size) / Short.MAX_VALUE

        // Count potential clicks
        val clicks = detectClickPositions(samples).size

        // Check for clipping
        var clippedSamples = 0
        var peakValue = 0
        for (sample in samples) {
            val abs = abs(sample.toInt())
            if (abs > peakValue) peakValue = abs
            if (abs >= Short.MAX_VALUE - 10) clippedSamples++
        }
        val isClipping = clippedSamples > samples.size / 1000 // More than 0.1% clipped

        // Calculate crest factor (peak to RMS ratio)
        var sumSquares = 0.0
        for (sample in samples) {
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / samples.size)
        val peak = peakValue.toDouble() / Short.MAX_VALUE
        val crestFactor = if (rms > 0) (peak / rms).toFloat() else 0f

        return QualityReport(dcOffset, clicks, isClipping, crestFactor)
    }

    data class QualityReport(
        val dcOffsetPercent: Float,    // DC offset as percentage of max
        val potentialClicks: Int,       // Number of detected clicks
        val hasClipping: Boolean,       // Whether audio is clipping
        val crestFactor: Float          // Peak to RMS ratio (dynamic range indicator)
    ) {
        fun needsProcessing(): Boolean {
            return abs(dcOffsetPercent) > 0.01f || potentialClicks > 0 || hasClipping
        }

        override fun toString(): String {
            return buildString {
                appendLine("Audio Quality Report:")
                appendLine("  DC Offset: ${(dcOffsetPercent * 100).format(2)}%")
                appendLine("  Potential Clicks: $potentialClicks")
                appendLine("  Clipping: ${if (hasClipping) "Yes" else "No"}")
                appendLine("  Crest Factor: ${crestFactor.format(1)} (${describeCrestFactor()})")
            }
        }

        private fun describeCrestFactor(): String = when {
            crestFactor < 2f -> "Very compressed"
            crestFactor < 4f -> "Compressed"
            crestFactor < 8f -> "Normal"
            crestFactor < 12f -> "Dynamic"
            else -> "Very dynamic"
        }

        private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
    }
}
