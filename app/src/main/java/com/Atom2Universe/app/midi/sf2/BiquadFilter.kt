package com.Atom2Universe.app.midi.sf2

import kotlin.math.*

/**
 * Biquad Low-Pass Resonant Filter for SF2 synthesis.
 *
 * This implements a 2-pole IIR (Infinite Impulse Response) low-pass filter
 * with resonance control, as specified in the SoundFont 2.01 standard.
 *
 * The filter is used per-voice to shape the timbre of each note. Like in the standard
 * (and FluidSynth), every voice goes through it: with the default cutoff (13500 cents,
 * ~20 kHz) and no resonance it is nearly transparent.
 *
 * SF2 Parameters:
 * - initialFilterFc (generator 8): Initial cutoff frequency in absolute cents
 *   Formula: fc_hz = 8.176 * 2^(cents/1200)
 *   Range: 1500 to 13500 cents (20 Hz to 20 kHz)
 *
 * - initialFilterQ (generator 9): height of the resonance peak above the DC gain,
 *   in centibels (0 to 960). The standard also lowers the DC gain by half that height:
 *   for 100 cB, DC is at -5 dB and the peak at +5 dB.
 *
 * Filter topology: Direct Form II Transposed (better numerical stability)
 */
class BiquadFilter(
    private val sampleRate: Int = 44100
) {
    companion object {
        // SF2 default value: ~20 kHz
        const val DEFAULT_FC_CENTS = 13500

        // Limits
        const val MIN_FC_HZ = 20f           // Minimum cutoff frequency (1500 cents)
        const val MAX_Q = 60f               // Numerical safety for extreme resonances

        // Soft clipping threshold - prevents filter instability from causing extreme values
        const val SOFT_CLIP_MAX = 1.5f

        // Reference frequency for absolute cents conversion
        // 8.176 Hz = MIDI note 0 at A4=440Hz tuning
        private const val FREQ_REFERENCE = 8.176f

        /**
         * Converts SF2 absolute cents to Hz.
         * Formula: freq = 8.176 * 2^(cents/1200)
         */
        fun centsToHz(cents: Float): Float {
            return FREQ_REFERENCE * 2f.pow(cents / 1200f)
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

    // Base (configured) cutoff - the unmodulated cutoff, reference for modulateCutoff()
    private var baseFcHz: Float = 20000f
    private var currentFcHz: Float = 20000f

    // Biquad Q and DC gain derived from the SF2 resonance
    private var q: Float = 0.7071f
    private var dcGain: Float = 1f

    private var isEnabled: Boolean = false

    // Highest usable cutoff (FluidSynth uses the same 0.45 × sample rate limit)
    private val maxFcHz: Float = sampleRate * 0.45f

    /**
     * Configures the filter with SF2 parameters.
     *
     * @param fcCents Cutoff frequency in absolute cents (generator 8 plus modulators)
     * @param qCentibels Resonance in centibels (generator 9)
     */
    fun configure(fcCents: Float, qCentibels: Int) {
        val resonanceDb = qCentibels.coerceIn(0, 960) / 10f
        // 0 dB of resonance = flat Butterworth response (Q = 1/√2)
        q = 10f.pow((resonanceDb - 3.01f) / 20f).coerceIn(0.7071f, MAX_Q)
        dcGain = 10f.pow(-resonanceDb / 40f)

        baseFcHz = centsToHz(fcCents).coerceIn(MIN_FC_HZ, maxFcHz)
        currentFcHz = baseFcHz
        calculateCoefficients()
        isEnabled = true
    }

    /** Bypasses the filter until the next [configure]. */
    fun disable() {
        isEnabled = false
    }

    /**
     * Calculates the biquad coefficients for a low-pass filter.
     * Uses the "cookbook" formulas by Robert Bristow-Johnson, with the SF2 DC gain.
     */
    private fun calculateCoefficients() {
        // Normalized frequency (0 to 0.5)
        val omega = 2f * PI.toFloat() * currentFcHz / sampleRate

        val sinOmega = sin(omega)
        val cosOmega = cos(omega)

        // Alpha controls bandwidth/resonance
        val alpha = sinOmega / (2f * q)

        // Low-pass filter coefficients
        val a0 = 1f + alpha
        val gain = dcGain / a0

        b0 = ((1f - cosOmega) / 2f) * gain
        b1 = (1f - cosOmega) * gain
        b2 = b0
        a1 = (-2f * cosOmega) / a0
        a2 = (1f - alpha) / a0
    }

    /**
     * Processes a single sample through the filter.
     * Uses Direct Form II Transposed for better numerical stability.
     */
    fun process(input: Float): Float {
        if (!isEnabled) return input

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
     * Modulates the cutoff frequency by a factor relative to the base (configured) cutoff.
     * Used for envelope or LFO modulation in block-based rendering.
     *
     * @param factor Multiplier for cutoff (1.0 = no change, 2.0 = one octave up)
     */
    fun modulateCutoff(factor: Float) {
        if (!isEnabled) return
        currentFcHz = (baseFcHz * factor).coerceIn(MIN_FC_HZ, maxFcHz)
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
     * Returns debug info about the filter state.
     */
    fun getDebugInfo(): String {
        return if (isEnabled) {
            "Filter: ${currentFcHz.toInt()}Hz Q=${String.format("%.1f", q)}"
        } else {
            "Filter: bypassed"
        }
    }
}
