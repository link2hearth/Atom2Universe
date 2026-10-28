package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Noise gate for reducing background noise in recorded samples.
 *
 * Features:
 * - Configurable threshold, attack, hold, release
 * - Soft knee option for smoother transitions
 * - Look-ahead for preserving transients
 */
class NoiseGate(private val sampleRate: Int = 44100) {

    data class Settings(
        val thresholdDb: Float = -40f,      // Gate opens above this level
        val attackMs: Float = 1f,            // Time to fully open
        val holdMs: Float = 50f,             // Time to stay open after signal drops
        val releaseMs: Float = 100f,         // Time to fully close
        val rangeDb: Float = -80f,           // Minimum level when closed (full close = -inf)
        val softKneeDb: Float = 6f,          // Soft knee width (0 = hard knee)
        val lookAheadMs: Float = 5f          // Look-ahead for transient preservation
    )

    /**
     * Apply noise gate to samples.
     *
     * @param samples Input audio samples
     * @param settings Gate settings
     * @return Gated audio samples
     */
    fun apply(samples: ShortArray, settings: Settings = Settings()): ShortArray {
        if (samples.isEmpty()) return samples

        val output = ShortArray(samples.size)

        val thresholdLinear = dbToLinear(settings.thresholdDb)
        val rangeLinear = dbToLinear(settings.rangeDb)
        val kneeWidth = dbToLinear(settings.softKneeDb) - 1f

        val attackCoef = exp(-1f / (settings.attackMs * sampleRate / 1000f))
        val releaseCoef = exp(-1f / (settings.releaseMs * sampleRate / 1000f))
        val holdSamples = (settings.holdMs * sampleRate / 1000).toInt()
        val lookAheadSamples = (settings.lookAheadMs * sampleRate / 1000).toInt()

        // Calculate envelope with look-ahead
        val envelope = calculateEnvelope(samples, lookAheadSamples)

        var gateGain = 0f  // Start closed
        var holdCounter = 0

        for (i in samples.indices) {
            val inputLevel = envelope[i]

            // Determine target gain based on input level
            val targetGain = calculateGateGain(
                inputLevel, thresholdLinear, rangeLinear, kneeWidth
            )

            // Update hold counter
            if (inputLevel > thresholdLinear) {
                holdCounter = holdSamples
            } else if (holdCounter > 0) {
                holdCounter--
            }

            // Apply attack/release smoothing
            val effectiveTarget = if (holdCounter > 0) 1f else targetGain

            gateGain = if (effectiveTarget > gateGain) {
                // Opening (attack)
                effectiveTarget + attackCoef * (gateGain - effectiveTarget)
            } else {
                // Closing (release)
                effectiveTarget + releaseCoef * (gateGain - effectiveTarget)
            }

            // Apply gain
            val outputSample = (samples[i] * gateGain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i] = outputSample.toShort()
        }

        return output
    }

    /**
     * Calculate the signal envelope with optional look-ahead.
     * Sliding maximum over [i, i + lookAhead] kept in a monotonic queue.
     */
    private fun calculateEnvelope(samples: ShortArray, lookAhead: Int): FloatArray {
        val size = samples.size
        val envelope = FloatArray(size)
        val windowSize = (sampleRate / 1000).coerceAtLeast(1) // 1ms window
        if (lookAhead < 0) return smoothEnvelope(envelope, windowSize)

        val queue = IntArray(size)
        var head = 0
        var tail = 0
        var next = 0
        for (i in samples.indices) {
            // Look ahead to catch transients early
            val searchEnd = (i + lookAhead).coerceAtMost(size - 1)
            while (next <= searchEnd) {
                val level = abs(samples[next].toInt())
                while (tail > head && abs(samples[queue[tail - 1]].toInt()) <= level) tail--
                queue[tail++] = next++
            }
            while (queue[head] < i) head++
            envelope[i] = abs(samples[queue[head]].toFloat()) / Short.MAX_VALUE
        }

        // Smooth the envelope slightly
        return smoothEnvelope(envelope, windowSize)
    }

    /**
     * Smooth the envelope to prevent rapid fluctuations: maximum over a window centered
     * on each sample (peak detection), kept in a monotonic queue.
     */
    private fun smoothEnvelope(envelope: FloatArray, windowSize: Int): FloatArray {
        if (windowSize <= 1) return envelope

        val smoothed = FloatArray(envelope.size)
        val half = windowSize / 2
        val queue = IntArray(envelope.size)
        var head = 0
        var tail = 0
        var next = 0
        for (i in envelope.indices) {
            val windowStart = (i - half).coerceAtLeast(0)
            val windowEnd = (i + half).coerceAtMost(envelope.lastIndex)
            while (next <= windowEnd) {
                val level = envelope[next]
                while (tail > head && envelope[queue[tail - 1]] <= level) tail--
                queue[tail++] = next++
            }
            while (queue[head] < windowStart) head++
            smoothed[i] = maxOf(0f, envelope[queue[head]])
        }

        return smoothed
    }

    /**
     * Calculate gate gain with soft knee.
     */
    private fun calculateGateGain(
        inputLevel: Float,
        threshold: Float,
        range: Float,
        kneeWidth: Float
    ): Float {
        return when {
            inputLevel >= threshold -> 1f
            kneeWidth <= 0f -> range // Hard knee
            inputLevel >= threshold - kneeWidth -> {
                // Soft knee region - smooth transition
                val kneePosition = (inputLevel - (threshold - kneeWidth)) / kneeWidth
                range + (1f - range) * kneePosition * kneePosition
            }
            else -> range
        }
    }

    /**
     * Analyze samples to suggest optimal gate settings.
     * Useful for auto-configuration.
     */
    fun analyzeAndSuggestSettings(samples: ShortArray): Settings {
        if (samples.isEmpty()) return Settings()

        // Find noise floor (lowest 10% of samples), read from a histogram of the levels
        val noiseFloorIndex = (samples.size * 0.1).toInt()
        val noiseFloor = if (noiseFloorIndex < samples.size) {
            val histogram = IntArray(-Short.MIN_VALUE.toInt() + 1)
            for (sample in samples) histogram[abs(sample.toInt())]++
            var level = 0
            var below = histogram[0]
            while (below <= noiseFloorIndex) below += histogram[++level]
            level.toFloat() / Short.MAX_VALUE
        } else {
            0.001f
        }

        // Suggest threshold slightly above noise floor
        val suggestedThreshold = if (noiseFloor > 0f) {
            linearToDb(noiseFloor * 2f).coerceIn(-60f, -20f)
        } else {
            -40f
        }

        // Detect if sample has percussive transients
        val hasTransients = detectTransients(samples)

        return Settings(
            thresholdDb = suggestedThreshold,
            attackMs = if (hasTransients) 0.5f else 2f,
            holdMs = if (hasTransients) 30f else 50f,
            releaseMs = if (hasTransients) 50f else 100f,
            rangeDb = -80f,
            softKneeDb = 6f,
            lookAheadMs = if (hasTransients) 5f else 2f
        )
    }

    /**
     * Detect if the sample contains sharp transients (percussive sounds).
     */
    private fun detectTransients(samples: ShortArray): Boolean {
        if (samples.size < 1000) return false

        val windowSize = sampleRate / 100 // 10ms windows
        var prevEnergy = 0f
        var transientCount = 0

        for (i in 0 until samples.size - windowSize step windowSize) {
            var energy = 0f
            for (j in 0 until windowSize) {
                val sample = samples[i + j].toFloat() / Short.MAX_VALUE
                energy += sample * sample
            }
            energy /= windowSize

            // Detect sudden energy increase (transient)
            if (prevEnergy > 0.0001f && energy > prevEnergy * 5f) {
                transientCount++
            }

            prevEnergy = energy
        }

        return transientCount > 0
    }

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    private fun linearToDb(linear: Float): Float = 20f * kotlin.math.log10(linear.coerceAtLeast(0.00001f))
}
