package com.Atom2Universe.app.midi.sf2

import kotlin.math.min

/**
 * Freeverb-style stereo reverb effect.
 *
 * Based on the Schroeder-Moorer reverb model with:
 * - 8 parallel comb filters (main reverb body)
 * - 4 series allpass filters (diffusion)
 * - Stereo output with configurable width
 *
 * Can be completely bypassed for CPU savings on low-end devices.
 */
class Reverb(private val sampleRate: Int = 44100) {

    companion object {
        // Comb filter delay times (in samples at 44100Hz)
        // These are tuned to avoid metallic resonances
        private val COMB_DELAYS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)

        // Allpass filter delay times (in samples at 44100Hz)
        private val ALLPASS_DELAYS = intArrayOf(556, 441, 341, 225)

        // Stereo spread (additional delay for right channel)
        private const val STEREO_SPREAD = 23

        // Default allpass feedback coefficient
        private const val ALLPASS_FEEDBACK = 0.5f

        // Reference sample rate for delay scaling
        private const val REFERENCE_SAMPLE_RATE = 44100
    }

    // Enable/disable reverb (when disabled, processing is completely bypassed)
    // Disabled by default to reduce CPU usage and avoid potential crackling on complex pieces
    @Volatile
    var enabled: Boolean = false

    // Reverb parameters
    var roomSize: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateCombFeedback()
        }

    var damping: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateDamping()
        }

    var wetLevel: Float = 0.3f
        set(value) { field = value.coerceIn(0f, 1f) }

    var dryLevel: Float = 0.7f
        set(value) { field = value.coerceIn(0f, 1f) }

    var width: Float = 1.0f
        set(value) { field = value.coerceIn(0f, 1f) }

    // Comb filters (8 for left, 8 for right)
    private val combFiltersL: Array<CombFilter>
    private val combFiltersR: Array<CombFilter>

    // Allpass filters (4 for left, 4 for right)
    private val allpassFiltersL: Array<AllpassFilter>
    private val allpassFiltersR: Array<AllpassFilter>

    init {
        // Scale delay times for sample rate
        val scale = sampleRate.toFloat() / REFERENCE_SAMPLE_RATE

        // Initialize comb filters
        combFiltersL = Array(COMB_DELAYS.size) { i ->
            CombFilter((COMB_DELAYS[i] * scale).toInt())
        }
        combFiltersR = Array(COMB_DELAYS.size) { i ->
            CombFilter(((COMB_DELAYS[i] + STEREO_SPREAD) * scale).toInt())
        }

        // Initialize allpass filters
        allpassFiltersL = Array(ALLPASS_DELAYS.size) { i ->
            AllpassFilter((ALLPASS_DELAYS[i] * scale).toInt(), ALLPASS_FEEDBACK)
        }
        allpassFiltersR = Array(ALLPASS_DELAYS.size) { i ->
            AllpassFilter(((ALLPASS_DELAYS[i] + STEREO_SPREAD) * scale).toInt(), ALLPASS_FEEDBACK)
        }

        // Apply initial settings
        updateCombFeedback()
        updateDamping()
    }

    /**
     * Updates comb filter feedback based on room size.
     */
    private fun updateCombFeedback() {
        // Feedback ranges from 0.5 to 0.85 based on room size
        // Lower values for more natural decay (0.98 was way too long/cathedral-like)
        val feedback = 0.5f + roomSize * 0.35f
        for (comb in combFiltersL) comb.feedback = feedback
        for (comb in combFiltersR) comb.feedback = feedback
    }

    /**
     * Updates comb filter damping.
     */
    private fun updateDamping() {
        for (comb in combFiltersL) comb.damping = damping
        for (comb in combFiltersR) comb.damping = damping
    }

    /**
     * Processes stereo audio buffers in-place.
     * If reverb is disabled, this method returns immediately (zero CPU cost).
     */
    fun process(left: FloatArray, right: FloatArray, numSamples: Int) {
        if (!enabled) return

        val samples = min(numSamples, min(left.size, right.size))
        if (samples <= 0) return

        // Pre-calculate wet/dry mix coefficients (loop invariant)
        val wet1 = wetLevel * ((1f + width) * 0.5f)
        val wet2 = wetLevel * ((1f - width) * 0.5f)
        val dry = dryLevel

        for (i in 0 until samples) {
            // Get input (mono mix for reverb input)
            val inputL = left[i]
            val inputR = right[i]
            val monoInput = (inputL + inputR) * 0.5f

            // Process through parallel comb filters
            var combOutL = 0f
            var combOutR = 0f

            for (j in combFiltersL.indices) {
                combOutL += combFiltersL[j].process(monoInput)
                combOutR += combFiltersR[j].process(monoInput)
            }

            // Process through series allpass filters
            var outL = combOutL
            var outR = combOutR

            for (j in allpassFiltersL.indices) {
                outL = allpassFiltersL[j].process(outL)
                outR = allpassFiltersR[j].process(outR)
            }

            // Mix dry and wet signals
            left[i] = inputL * dry + outL * wet1 + outR * wet2
            right[i] = inputR * dry + outR * wet1 + outL * wet2
        }
    }

    /**
     * Applies a preset configuration.
     * @param preset -1 = Off, 0 = Large Hall, 1 = Hall, 2 = Chamber, 3 = Room
     *
     * Presets are calibrated for musical realism:
     * - Room: Short decay (~0.3s), subtle ambience like a small practice room
     * - Chamber: Medium decay (~0.6s), intimate concert setting
     * - Hall: Longer decay (~1.2s), concert hall feel
     * - Large Hall: Long decay (~2s), cathedral/large venue
     */
    fun applyPreset(preset: Int) {
        when (preset) {
            -1 -> {
                // Off - disable reverb entirely
                enabled = false
            }
            0 -> {
                // Large Hall - spacious, long decay (cathedral/large venue)
                enabled = true
                roomSize = 0.85f      // High room size for long decay
                damping = 0.4f        // Moderate damping, some brightness
                wetLevel = 0.18f      // Subtle wet mix to avoid washing out
                dryLevel = 0.82f
                width = 1.0f          // Full stereo
            }
            1 -> {
                // Hall - concert hall, balanced reverb
                enabled = true
                roomSize = 0.65f      // Medium-high room size
                damping = 0.5f        // Balanced damping
                wetLevel = 0.14f      // Moderate wet mix
                dryLevel = 0.86f
                width = 0.85f
            }
            2 -> {
                // Chamber - smaller venue, tighter sound
                enabled = true
                roomSize = 0.45f      // Medium room size
                damping = 0.6f        // More damping for warmer tone
                wetLevel = 0.10f      // Subtle wet mix
                dryLevel = 0.90f
                width = 0.65f
            }
            3 -> {
                // Room - small practice room, very short decay
                enabled = true
                roomSize = 0.25f      // Small room size for short decay
                damping = 0.7f        // High damping for quick absorption
                wetLevel = 0.07f      // Very subtle - just adds ambience
                dryLevel = 0.93f
                width = 0.4f          // Narrow stereo for intimate feel
            }
            else -> {
                // Default to Hall
                applyPreset(1)
            }
        }
    }

    /**
     * Clears all delay buffers (call when stopping playback).
     */
    fun reset() {
        for (comb in combFiltersL) comb.reset()
        for (comb in combFiltersR) comb.reset()
        for (allpass in allpassFiltersL) allpass.reset()
        for (allpass in allpassFiltersR) allpass.reset()
    }

    /**
     * Returns debug info about the reverb state.
     */
    fun getDebugInfo(): String {
        return "Reverb: ${if (enabled) "ON" else "OFF"} room=$roomSize damp=$damping wet=$wetLevel"
    }
}

/**
 * Comb filter with feedback and damping (low-pass filtered feedback).
 * Creates the main body of the reverb sound.
 */
private class CombFilter(size: Int) {
    private val buffer = FloatArray(size)
    private var bufferIndex = 0
    private var filterStore = 0f

    var feedback: Float = 0.7f
    var damping: Float = 0.5f

    fun process(input: Float): Float {
        val output = buffer[bufferIndex]

        // Low-pass filtered feedback (damping)
        // Higher damping = more high-frequency absorption
        filterStore = output * (1f - damping) + filterStore * damping

        // Write input + filtered feedback to buffer
        buffer[bufferIndex] = input + filterStore * feedback

        // Advance buffer index
        bufferIndex = (bufferIndex + 1) % buffer.size

        return output
    }

    fun reset() {
        buffer.fill(0f)
        bufferIndex = 0
        filterStore = 0f
    }
}

/**
 * Allpass filter for diffusion.
 * Spreads out the comb filter output for a smoother, more natural reverb.
 */
private class AllpassFilter(size: Int, private val feedback: Float = 0.5f) {
    private val buffer = FloatArray(size)
    private var bufferIndex = 0

    fun process(input: Float): Float {
        val bufferedSample = buffer[bufferIndex]

        // Allpass formula: output = -input + buffered + feedback * (input + buffered)
        val output = -input + bufferedSample
        buffer[bufferIndex] = input + bufferedSample * feedback

        // Advance buffer index
        bufferIndex = (bufferIndex + 1) % buffer.size

        return output
    }

    fun reset() {
        buffer.fill(0f)
        bufferIndex = 0
    }
}
