package com.Atom2Universe.app.midi.sf2

import kotlin.math.*

/**
 * Biquad Low-Pass Resonant Filter for SF2 synthesis.
 *
 * This implements a 2-pole IIR (Infinite Impulse Response) low-pass filter
 * with resonance control, as specified in the SoundFont 2.01 standard.
 *
 * The filter is used per-voice to shape the timbre of each note.
 *
 * SF2 Parameters:
 * - initialFilterFc (generator 8): Initial cutoff frequency in absolute cents
 *   Formula: fc_hz = 8.176 * 2^(cents/1200)
 *   Range: ~8 Hz to ~20 kHz (1500 to 13500 cents typical)
 *
 * - initialFilterQ (generator 9): Resonance in centibels
 *   Formula: Q = 10^(cB/200)
 *   Range: 0 to 960 cB (Q from 1.0 to ~31.6)
 *
 * Filter topology: Direct Form II Transposed (better numerical stability)
 */
class BiquadFilter(
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "BiquadFilter"

        // SF2 default values
        const val DEFAULT_FC_CENTS = 13500  // ~20 kHz (essentially bypassed)
        const val DEFAULT_Q_CB = 0          // Q = 1.0 (no resonance)

        // Limits
        const val MIN_FC_HZ = 20f           // Minimum cutoff frequency
        const val MAX_Q = 8f                // Maximum Q to prevent instability (reduced from 12)

        // Soft clipping threshold - prevents filter instability from causing extreme values
        // Reduced from 4.0 to 1.5 to prevent downstream hard clipping
        const val SOFT_CLIP_MAX = 1.5f

        // Reference frequency for absolute cents conversion
        // 8.176 Hz = MIDI note 0 at A4=440Hz tuning
        private const val FREQ_REFERENCE = 8.176f

        /**
         * Converts SF2 absolute cents to Hz.
         * Formula: freq = 8.176 * 2^(cents/1200)
         */
        fun centsToHz(cents: Int): Float {
            return FREQ_REFERENCE * 2f.pow(cents / 1200f)
        }

        /**
         * Converts SF2 centibels to Q factor.
         * Formula: Q = 10^(cB/200)
         */
        fun centibelsToQ(centibels: Int): Float {
            return 10f.pow(centibels / 200f)
        }
    }

    // Filter coefficients
    private var b0: Float = 1f
    private var b1: Float = 0f
    private var b2: Float = 0f
    private var a1: Float = 0f
    private var a2: Float = 0f

    // Filter state (delay elements)
    private var z1: Float = 0f
    private var z2: Float = 0f

    // Current parameters
    private var currentFcHz: Float = 20000f
    private var currentQ: Float = 1f
    private var isEnabled: Boolean = false

    // Base (configured) cutoff - the unmodulated cutoff set by configure/setParameters.
    // Used by modulateCutoff() as the reference point for applying modulation factors.
    // This is SEPARATE from targetFcHz to prevent process() smoothing from fighting
    // against block-based modulation (which caused a 750 Hz sawtooth artifact).
    private var baseFcHz: Float = 20000f

    // Smoothing for parameter changes (to avoid clicks)
    private var targetFcHz: Float = 20000f
    private var targetQ: Float = 1f
    private val smoothingCoeff: Float = 0.001f  // ~10ms at 44100Hz

    /**
     * Configures the filter with SF2 parameters.
     *
     * @param fcCents Cutoff frequency in absolute cents (SF2 generator 8)
     * @param qCentibels Q/resonance in centibels (SF2 generator 9)
     */
    fun configure(fcCents: Int?, qCentibels: Int?) {
        // If no filter parameters specified, filter is bypassed
        if (fcCents == null && qCentibels == null) {
            isEnabled = false
            return
        }

        val fc = fcCents ?: DEFAULT_FC_CENTS
        val q = qCentibels ?: DEFAULT_Q_CB

        targetFcHz = centsToHz(fc).coerceIn(MIN_FC_HZ, sampleRate / 2f - 100f)
        targetQ = centibelsToQ(q).coerceIn(0.5f, MAX_Q)

        // Store the base (unmodulated) cutoff for modulateCutoff() reference
        baseFcHz = targetFcHz

        // Initialize current values to target (no smoothing on first configure)
        currentFcHz = targetFcHz
        currentQ = targetQ

        calculateCoefficients()

        // Enable filter only if cutoff is significantly below Nyquist
        // Removed the 5000 Hz lower bound - let SF2 filters work as designed, even for low cutoffs
        isEnabled = targetFcHz < (sampleRate / 2f - 1000f)
    }

    /**
     * Configures the filter with Hz and Q directly.
     * Useful for real-time modulation (uses smoothing).
     */
    fun setParameters(fcHz: Float, q: Float) {
        targetFcHz = fcHz.coerceIn(MIN_FC_HZ, sampleRate / 2f - 100f)
        targetQ = q.coerceIn(0.5f, MAX_Q)
        baseFcHz = targetFcHz
        isEnabled = targetFcHz < (sampleRate / 2f - 1000f)
    }

    /**
     * Configures the filter with Hz and Q directly, with immediate effect (no smoothing).
     * Use this when configuring a new note to avoid transient artifacts.
     */
    fun setParametersImmediate(fcHz: Float, q: Float) {
        targetFcHz = fcHz.coerceIn(MIN_FC_HZ, sampleRate / 2f - 100f)
        targetQ = q.coerceIn(0.5f, MAX_Q)
        baseFcHz = targetFcHz
        currentFcHz = targetFcHz
        currentQ = targetQ
        calculateCoefficients()
        isEnabled = targetFcHz < (sampleRate / 2f - 1000f)
    }

    /**
     * Calculates the biquad coefficients for a low-pass filter.
     * Uses the "cookbook" formulas by Robert Bristow-Johnson.
     */
    private fun calculateCoefficients() {
        // Normalized frequency (0 to 0.5)
        val omega = 2f * PI.toFloat() * currentFcHz / sampleRate

        // Clamp omega to avoid numerical issues near Nyquist
        val clampedOmega = omega.coerceIn(0.0001f, PI.toFloat() * 0.99f)

        val sinOmega = sin(clampedOmega)
        val cosOmega = cos(clampedOmega)

        // Alpha controls bandwidth/resonance
        val alpha = sinOmega / (2f * currentQ)

        // Low-pass filter coefficients
        val a0 = 1f + alpha

        b0 = ((1f - cosOmega) / 2f) / a0
        b1 = (1f - cosOmega) / a0
        b2 = ((1f - cosOmega) / 2f) / a0
        a1 = (-2f * cosOmega) / a0
        a2 = (1f - alpha) / a0
    }

    /**
     * Processes a single sample through the filter.
     * Uses Direct Form II Transposed for better numerical stability.
     */
    fun process(input: Float): Float {
        if (!isEnabled) return input

        // Smooth parameter changes
        if (abs(currentFcHz - targetFcHz) > 1f || abs(currentQ - targetQ) > 0.01f) {
            currentFcHz += (targetFcHz - currentFcHz) * smoothingCoeff
            currentQ += (targetQ - currentQ) * smoothingCoeff
            calculateCoefficients()
        }

        // Direct Form II Transposed
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output

        // Anti-denormal: zero out tiny values in filter state to prevent
        // denormalized floats that cause massive CPU spikes on some architectures.
        // Inspired by FluidSynth's explicit denormal zeroing.
        if (z1 > -1e-20f && z1 < 1e-20f) z1 = 0f
        if (z2 > -1e-20f && z2 < 1e-20f) z2 = 0f

        // Soft clip to prevent filter instability from causing extreme values
        // Use tanh-based soft saturation for musical clipping instead of hard clip
        val absOutput = if (output >= 0f) output else -output
        return if (absOutput <= SOFT_CLIP_MAX) {
            output
        } else {
            // Soft saturation using fast polynomial approximation
            // Maps values above threshold asymptotically toward max
            val sign = if (output >= 0f) 1f else -1f
            val excess = absOutput - SOFT_CLIP_MAX
            sign * (SOFT_CLIP_MAX + 0.3f * excess / (excess + 0.5f))
        }
    }

    /**
     * Processes a buffer of samples in-place.
     */
    fun processBuffer(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset) {
        if (!isEnabled) return

        val end = minOf(offset + length, buffer.size)
        for (i in offset until end) {
            buffer[i] = process(buffer[i])
        }
    }

    /**
     * Modulates the cutoff frequency by a factor relative to the base (configured) cutoff.
     * Useful for envelope or LFO modulation in block-based rendering.
     *
     * Sets BOTH targetFcHz and currentFcHz to the modulated value so that the
     * per-sample smoothing in process() doesn't fight back toward the unmodulated base.
     * (Previously only currentFcHz was set, causing the smoothing to create a sawtooth
     * artifact at the block rate — the root cause of crackling on filtered instruments.)
     *
     * @param factor Multiplier for cutoff (1.0 = no change, 2.0 = one octave up)
     */
    fun modulateCutoff(factor: Float) {
        if (!isEnabled) return
        val modulatedFc = (baseFcHz * factor).coerceIn(MIN_FC_HZ, sampleRate / 2f - 100f)
        targetFcHz = modulatedFc
        currentFcHz = modulatedFc
        calculateCoefficients()
    }

    /**
     * Resets the filter state (clears delay elements).
     * Call this when starting a new note to avoid artifacts.
     */
    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    /**
     * Returns true if the filter is active.
     */
    fun isActive(): Boolean = isEnabled

    /**
     * Returns the current cutoff frequency in Hz.
     */
    fun getCutoffHz(): Float = currentFcHz

    /**
     * Returns the current Q factor.
     */
    fun getQ(): Float = currentQ

    /**
     * Creates a copy of this filter with the same configuration.
     */
    fun copy(): BiquadFilter {
        val copy = BiquadFilter(sampleRate)
        copy.b0 = this.b0
        copy.b1 = this.b1
        copy.b2 = this.b2
        copy.a1 = this.a1
        copy.a2 = this.a2
        copy.currentFcHz = this.currentFcHz
        copy.currentQ = this.currentQ
        copy.targetFcHz = this.targetFcHz
        copy.targetQ = this.targetQ
        copy.baseFcHz = this.baseFcHz
        copy.isEnabled = this.isEnabled
        // Don't copy state (z1, z2) - new note should start fresh
        return copy
    }

    /**
     * Returns debug info about the filter state.
     */
    fun getDebugInfo(): String {
        return if (isEnabled) {
            "Filter: ${currentFcHz.toInt()}Hz Q=${String.format("%.1f", currentQ)}"
        } else {
            "Filter: bypassed"
        }
    }
}
