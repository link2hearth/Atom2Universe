package com.Atom2Universe.app.midi.sf2

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * Low Frequency Oscillator (LFO) for SF2 synthesis.
 *
 * SF2 defines two LFOs:
 * 1. Vibrato LFO - modulates pitch only
 * 2. Modulation LFO - modulates pitch, filter cutoff, and volume
 *
 * Both use the same underlying oscillator with different destinations.
 *
 * SF2 Parameters:
 * - delayModLfo/delayVibLfo (generators 21, 23): Delay before LFO starts, in timecents
 *   Formula: seconds = 2^(timecents/1200)
 *
 * - freqModLfo/freqVibLfo (generators 22, 24): LFO frequency in absolute cents
 *   Formula: freq_hz = 8.176 * 2^(cents/1200)
 *   Default: 8.176 Hz (0 cents), typical range: ~0.1 Hz to ~20 Hz
 *
 * - modLfoToPitch/vibLfoToPitch (generators 5, 6): Modulation depth in cents
 *   Typical vibrato: 10-50 cents (subtle) to 100+ cents (wide)
 *
 * - modLfoToFilterFc (generator 10): Modulation depth to filter cutoff in cents
 *
 * - modLfoToVolume (generator 13): Modulation depth to volume in centibels
 *   Creates tremolo effect
 */
class Lfo(
    private val sampleRate: Int = 44100
) {
    companion object {
        // Reference frequency for absolute cents conversion (MIDI note 0)
        private const val FREQ_REFERENCE = 8.176f

        // Default LFO frequency in Hz (corresponds to 0 absolute cents)
        private const val DEFAULT_FREQ_HZ = 8.176f

        // Two PI for oscillator
        private const val TWO_PI = 2.0 * PI
        // Half PI for triangle wave phase offset (quarter period shift)
        private const val HALF_PI = PI * 0.5

        /**
         * Converts SF2 absolute cents to Hz for LFO frequency.
         * Formula: freq = 8.176 * 2^(cents/1200)
         */
        fun absoluteCentsToHz(cents: Int): Float {
            return FREQ_REFERENCE * 2f.pow(cents / 1200f)
        }

        /**
         * Converts SF2 timecents to seconds for delay.
         * Formula: seconds = 2^(timecents/1200)
         */
        fun timecentsToSeconds(timecents: Int): Float {
            return 2f.pow(timecents / 1200f)
        }
    }

    // LFO state
    private var phase: Double = 0.0
    private var phaseIncrement: Double = 0.0

    // Delay handling
    private var delaySamples: Int = 0
    private var delayCounter: Int = 0
    private var isDelaying: Boolean = false

    // Current frequency
    private var frequencyHz: Float = DEFAULT_FREQ_HZ

    // Enable flag
    private var isEnabled: Boolean = false

    /**
     * Configures the LFO with SF2 parameters.
     *
     * @param delayTimecents Delay before LFO starts (timecents, can be negative for instant)
     * @param freqAbsoluteCents LFO frequency in absolute cents
     */
    fun configure(delayTimecents: Int?, freqAbsoluteCents: Int?) {
        // Calculate delay in samples
        val delaySeconds = if (delayTimecents != null && delayTimecents > -12000) {
            timecentsToSeconds(delayTimecents).coerceIn(0f, 10f)
        } else {
            0f
        }
        delaySamples = (delaySeconds * sampleRate).toInt()

        // Calculate frequency
        frequencyHz = if (freqAbsoluteCents != null) {
            absoluteCentsToHz(freqAbsoluteCents).coerceIn(0.01f, 100f)
        } else {
            DEFAULT_FREQ_HZ
        }

        // Calculate phase increment per sample
        phaseIncrement = (TWO_PI * frequencyHz) / sampleRate

        isEnabled = true
    }

    /**
     * Triggers the LFO (called on note-on).
     */
    fun trigger() {
        phase = 0.0
        delayCounter = 0
        isDelaying = delaySamples > 0
    }

    /**
     * Processes one sample and returns the LFO value.
     * Returns a value between -1.0 and +1.0 (triangle wave).
     *
     * Uses triangle wave instead of sine (inspired by FluidSynth).
     * At LFO frequencies (< 20 Hz), triangle and sine sound identical,
     * but triangle eliminates all sin() calls (~48000/s per active voice).
     */
    fun process(): Float {
        if (!isEnabled) return 0f

        // Handle delay
        if (isDelaying) {
            delayCounter++
            if (delayCounter >= delaySamples) {
                isDelaying = false
            }
            return 0f
        }

        // Generate triangle wave from phase (0 to 2π)
        // Triangle: rises from -1 to +1 in first half, falls from +1 to -1 in second half
        // Formula: 2 * |2 * (phase/2π) - 1| - 1, shifted by quarter period for sine-like start
        val normalized = ((phase + HALF_PI) % TWO_PI) / TWO_PI  // 0.0 to 1.0, shifted for cosine-like phase
        val value = (4.0 * abs(normalized - 0.5) - 1.0).toFloat()

        // Advance phase
        phase += phaseIncrement
        if (phase >= TWO_PI) {
            phase -= TWO_PI
        }

        return value
    }

    /**
     * Advances the LFO by blockSize samples in O(1) and returns the current value.
     * Used for block-based rendering: compute LFO value once per block of 64 samples
     * instead of per-sample. At LFO frequencies (< 20 Hz), the value barely changes
     * within a 1.3ms block, so one value per block is sufficient.
     */
    fun processBlock(blockSize: Int): Float {
        if (!isEnabled) return 0f

        if (isDelaying) {
            delayCounter += blockSize
            if (delayCounter >= delaySamples) {
                isDelaying = false
                // Process the overshoot samples after delay ends
                val overshoot = delayCounter - delaySamples
                phase += phaseIncrement * overshoot
                if (phase >= TWO_PI) phase %= TWO_PI
            } else {
                return 0f
            }
        } else {
            // Advance phase by blockSize in O(1) — no per-sample loop
            phase += phaseIncrement * blockSize
            if (phase >= TWO_PI) phase %= TWO_PI
        }

        // Return current triangle value
        val normalized = ((phase + HALF_PI) % TWO_PI) / TWO_PI
        return (4.0 * abs(normalized - 0.5) - 1.0).toFloat()
    }

    /**
     * Gets the current LFO value without advancing (for debugging).
     */
    fun getCurrentValue(): Float {
        if (!isEnabled || isDelaying) return 0f
        val normalized = ((phase + HALF_PI) % TWO_PI) / TWO_PI
        return (4.0 * abs(normalized - 0.5) - 1.0).toFloat()
    }

    /**
     * Resets the LFO state.
     */
    fun reset() {
        phase = 0.0
        delayCounter = 0
        isDelaying = false
    }

    /**
     * Returns true if the LFO is enabled.
     */
    fun isActive(): Boolean = isEnabled

    /**
     * Returns true if the LFO is still in delay phase.
     */
    fun isInDelay(): Boolean = isDelaying

    /**
     * Returns the current frequency in Hz.
     */
    fun getFrequencyHz(): Float = frequencyHz

    /**
     * Returns debug info.
     */
    fun getDebugInfo(): String {
        return if (isEnabled) {
            val state = if (isDelaying) "delay" else "active"
            "LFO: ${String.format("%.1f", frequencyHz)}Hz ($state)"
        } else {
            "LFO: disabled"
        }
    }
}

/**
 * Data class to hold LFO parameters from SF2.
 * Used for both Vibrato LFO and Modulation LFO.
 */
data class LfoParameters(
    val delay: Int? = null,           // Delay in timecents
    val frequency: Int? = null,       // Frequency in absolute cents
    val toPitch: Int? = null,         // Modulation depth to pitch in cents
    val toFilterFc: Int? = null,      // Modulation depth to filter in cents (Mod LFO only)
    val toVolume: Int? = null         // Modulation depth to volume in centibels (Mod LFO only)
) {
    /**
     * Returns true if this LFO has any effect (non-zero modulation depths).
     */
    fun hasEffect(): Boolean {
        return (toPitch != null && toPitch != 0) ||
               (toFilterFc != null && toFilterFc != 0) ||
               (toVolume != null && toVolume != 0)
    }

    /**
     * Returns the pitch modulation depth in cents, or 0 if not set.
     */
    fun getPitchDepthCents(): Int = toPitch ?: 0

    /**
     * Returns the filter modulation depth in cents, or 0 if not set.
     */
    fun getFilterDepthCents(): Int = toFilterFc ?: 0

    /**
     * Returns the volume modulation depth in centibels, or 0 if not set.
     */
    fun getVolumeDepthCentibels(): Int = toVolume ?: 0
}
