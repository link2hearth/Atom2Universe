package com.Atom2Universe.app.midi.sf2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Audio limiter and compressor for preventing clipping and balancing levels.
 *
 * Uses:
 * - RMS-based level detection for consistent perceived loudness
 * - Peak limiting for catching transients
 * - Soft clipping (tanh-based) for musical-sounding limiting
 * - Fast attack / medium release for natural dynamics
 *
 * Optimized for SF2 playback where multiple instruments can produce
 * very loud combined peaks.
 */
class AudioLimiter {
    companion object {
        // Threshold above which soft clipping starts (in linear scale)
        // Raised from 0.6 to 0.75 - starts soft clipping closer to max to preserve dynamics
        private const val SOFT_CLIP_THRESHOLD = 0.75f

        // Maximum output level (slightly below 1.0 to avoid any digital clipping)
        private const val MAX_OUTPUT = 0.98f

        // Target RMS level (perceived loudness target)
        // Raised slightly to allow more dynamic range
        private const val TARGET_RMS = 0.25f

        // Compression ratio above target (e.g., 4:1 means 4dB input = 1dB output above threshold)
        // Reduced from 6:1 to 4:1 for more natural dynamics and less "squashing"
        private const val COMPRESSION_RATIO = 4f

        // Attack coefficient - how fast gain reduces when signal gets loud
        // Slightly slower to avoid "ducking" effect on transients
        private const val ATTACK_COEFF = 0.03f

        // Release coefficient - how fast gain recovers when signal gets quiet
        // CRITICAL FIX: Increased from 0.04 to 0.08 to prevent "stuck" low volume
        // At 44100Hz with 512 samples: ~90ms time constant (was ~180ms)
        private const val RELEASE_COEFF = 0.08f

        // RMS smoothing coefficient
        // Slightly reduced for smoother response (less pumping)
        private const val RMS_SMOOTHING = 0.08f

        // Minimum gain (never reduce below this - keeps quiet parts audible)
        private const val MIN_GAIN = 0.5f

        // Maximum gain (never boost above this - prevents noise amplification)
        // Set to 1.0 to disable auto-boost entirely (prevents pumping on quiet passages)
        private const val MAX_GAIN = 1.0f

        // Makeup gain to compensate for compression
        private const val MAKEUP_GAIN = 1.0f

        // Peak limiter threshold (start limiting peaks above this)
        // Raised from 0.85 to 0.9 for more headroom
        private const val PEAK_LIMIT_THRESHOLD = 0.90f
    }

    // Current compression gain (1.0 = no compression)
    private var compGain = 1.0f

    // Peak gain for instant transient limiting
    private var peakGain = 1.0f

    // Smoothed RMS level
    private var rmsLevel = TARGET_RMS

    // Logging for diagnostics (only log significant changes)
    private var lastLoggedGain = 1.0f
    private var logCounter = 0

    // Enable/disable auto-gain
    var autoGainEnabled = true

    // Enable/disable soft clipping
    var softClipEnabled = true

    // Enable/disable peak limiting (highly recommended for SF2)
    var peakLimitEnabled = true

    /**
     * Process stereo audio buffers in-place.
     * Applies compression, peak limiting, and soft clipping.
     */
    fun process(leftBuffer: FloatArray, rightBuffer: FloatArray, numSamples: Int) {
        if (!softClipEnabled && !autoGainEnabled && !peakLimitEnabled) return

        // Calculate RMS level and peak for this buffer
        var sumSquares = 0f
        var peak = 0f
        for (i in 0 until numSamples) {
            val l = leftBuffer[i]
            val r = rightBuffer[i]
            sumSquares += l * l + r * r
            peak = max(peak, max(abs(l), abs(r)))
        }
        val bufferRms = sqrt(sumSquares / (numSamples * 2))

        // Smooth RMS level (prevents pumping)
        rmsLevel += (bufferRms - rmsLevel) * RMS_SMOOTHING

        // Calculate compression gain based on RMS
        if (autoGainEnabled && rmsLevel > 0.001f) {
            val targetGain = calculateCompressionGain(rmsLevel, peak)

            // Smooth gain changes (fast attack, slower release)
            val coeff = if (targetGain < compGain) ATTACK_COEFF else RELEASE_COEFF
            compGain += (targetGain - compGain) * coeff
            compGain = compGain.coerceIn(MIN_GAIN, MAX_GAIN)
        }

        // Calculate peak limiting gain
        if (peakLimitEnabled && peak > 0.001f) {
            val effectivePeak = peak * compGain * MAKEUP_GAIN
            val targetPeakGain = if (effectivePeak > PEAK_LIMIT_THRESHOLD) {
                PEAK_LIMIT_THRESHOLD / effectivePeak
            } else {
                1.0f
            }
            if (targetPeakGain < peakGain) {
                // INSTANT attack for peak limiting - no smoothing when signal exceeds threshold
                // This prevents the first buffer of a loud transient from clipping
                peakGain = targetPeakGain
            } else {
                // Slow release to avoid pumping
                peakGain += (targetPeakGain - peakGain) * 0.01f
            }
            peakGain = peakGain.coerceIn(0.1f, 1.0f)
        }

        // Apply gain and soft clipping
        val totalGain = if (autoGainEnabled) {
            compGain * MAKEUP_GAIN * peakGain
        } else if (peakLimitEnabled) {
            peakGain
        } else {
            1f
        }

        for (i in 0 until numSamples) {
            var left = leftBuffer[i] * totalGain
            var right = rightBuffer[i] * totalGain

            // Per-sample peak protection for extreme transients that escape buffer-level limiting
            // This catches cases where individual samples spike far above the buffer average
            val samplePeak = max(abs(left), abs(right))
            if (samplePeak > MAX_OUTPUT) {
                val sampleGain = MAX_OUTPUT / samplePeak
                left *= sampleGain
                right *= sampleGain
            }

            // Apply soft clipping
            left = processSample(left)
            right = processSample(right)

            // Final NaN/Inf protection - critical safety net before audio output
            if (left.isNaN() || left.isInfinite()) left = 0f
            if (right.isNaN() || right.isInfinite()) right = 0f

            leftBuffer[i] = left
            rightBuffer[i] = right
        }
    }

    /**
     * Calculate compression gain based on RMS and peak levels.
     * Uses a soft-knee compressor curve.
     */
    private fun calculateCompressionGain(rms: Float, peak: Float): Float {
        // Use combination of RMS (for consistency) and peak (for protection)
        val effectiveLevel = max(rms, peak * 0.5f)

        return when {
            // Below target: slight boost (but limited)
            effectiveLevel < TARGET_RMS * 0.5f -> {
                min(MAX_GAIN, TARGET_RMS / max(effectiveLevel, 0.01f) * 0.7f)
            }
            // Around target: minimal adjustment
            effectiveLevel < TARGET_RMS * 1.5f -> {
                1.0f
            }
            // Above target: compress
            else -> {
                val excess = effectiveLevel / TARGET_RMS
                // Soft knee compression
                1.0f / (1.0f + (excess - 1.5f) / COMPRESSION_RATIO)
            }
        }
    }

    /**
     * Process a single sample with soft clipping.
     * Uses a smoother curve that preserves more dynamics while still protecting against clipping.
     */
    private fun processSample(sample: Float): Float {
        if (!softClipEnabled) {
            return sample.coerceIn(-MAX_OUTPUT, MAX_OUTPUT)
        }

        val absValue = abs(sample)

        return when {
            // Below threshold: pass through unchanged (most common case - fast path)
            absValue <= SOFT_CLIP_THRESHOLD -> sample

            // Above threshold: apply soft clipping using a gentler curve
            else -> {
                val sign = if (sample >= 0) 1f else -1f

                // Smooth saturation curve using tanh with reduced aggression
                // The multiplier is reduced from 1.5 to 1.0 for a gentler knee
                val headroom = MAX_OUTPUT - SOFT_CLIP_THRESHOLD
                val normalized = (absValue - SOFT_CLIP_THRESHOLD) / headroom

                // Use gentler tanh curve (multiplier 1.0 instead of 1.5)
                // This preserves more transient detail while still limiting
                val saturated = SOFT_CLIP_THRESHOLD + headroom * tanh(normalized).toFloat()

                sign * saturated
            }
        }
    }

    /**
     * Reset the limiter state.
     */
    fun reset() {
        compGain = 1.0f
        peakGain = 1.0f
        rmsLevel = TARGET_RMS
    }

    /**
     * Get current compression gain for debugging.
     */
    fun getAutoGain(): Float = compGain

    /**
     * Get current peak limiting gain for debugging.
     */
    fun getPeakGain(): Float = peakGain
}
