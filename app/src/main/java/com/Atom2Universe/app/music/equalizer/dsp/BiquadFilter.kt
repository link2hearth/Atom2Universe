package com.Atom2Universe.app.music.equalizer.dsp

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Biquad IIR filter for digital signal processing.
 * Implements a peaking EQ filter for frequency band gain control.
 *
 * Uses the standard biquad transfer function:
 * H(z) = (b0 + b1*z^-1 + b2*z^-2) / (a0 + a1*z^-1 + a2*z^-2)
 *
 * The filter is configured using the Audio EQ Cookbook formulas by Robert Bristow-Johnson.
 */
class BiquadFilter {

    companion object {
        private const val PI = Math.PI

        /** Default Q factor (approximately 1 octave bandwidth) */
        const val DEFAULT_Q = 1.41
    }

    // Filter coefficients (normalized by a0)
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Filter state per channel (x[n-1], x[n-2], y[n-1], y[n-2])
    // Support up to 8 channels (stereo typical, but support for surround)
    private val stateX1 = DoubleArray(8)
    private val stateX2 = DoubleArray(8)
    private val stateY1 = DoubleArray(8)
    private val stateY2 = DoubleArray(8)

    // Configuration cache
    private var configuredSampleRate = 0
    private var configuredFrequency = 0
    private var configuredGainDb = 0.0
    private var configuredQ = DEFAULT_Q

    /**
     * Configure the filter as a peaking EQ.
     *
     * @param sampleRate Sample rate in Hz (e.g., 44100, 48000)
     * @param frequency Center frequency in Hz (e.g., 1000 for 1kHz)
     * @param gainDb Gain in decibels (-12 to +12 typical)
     * @param q Q factor (bandwidth control, higher = narrower, typical 1.41)
     */
    fun configure(sampleRate: Int, frequency: Int, gainDb: Double, q: Double = DEFAULT_Q) {
        // Skip if already configured with same parameters
        if (sampleRate == configuredSampleRate &&
            frequency == configuredFrequency &&
            gainDb == configuredGainDb &&
            q == configuredQ) {
            return
        }

        configuredSampleRate = sampleRate
        configuredFrequency = frequency
        configuredGainDb = gainDb
        configuredQ = q

        if (gainDb == 0.0) {
            // Unity gain - pass through
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
            return
        }

        // Calculate intermediate values
        // A = 10^(dBgain/40) for peaking EQ
        val a = 10.0.pow(gainDb / 40.0)

        // w0 = 2*PI*f0/Fs (angular frequency)
        val w0 = 2.0 * PI * frequency / sampleRate

        // alpha = sin(w0) / (2*Q)
        val alpha = sin(w0) / (2.0 * q)

        val cosW0 = cos(w0)

        // Peaking EQ coefficients (from Audio EQ Cookbook)
        val b0Unnorm = 1.0 + alpha * a
        val b1Unnorm = -2.0 * cosW0
        val b2Unnorm = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1Unnorm = -2.0 * cosW0
        val a2Unnorm = 1.0 - alpha / a

        // Normalize by a0
        b0 = b0Unnorm / a0
        b1 = b1Unnorm / a0
        b2 = b2Unnorm / a0
        a1 = a1Unnorm / a0
        a2 = a2Unnorm / a0
    }

    /**
     * Configure as a low-shelf filter (for bass boost).
     *
     * @param sampleRate Sample rate in Hz
     * @param frequency Shelf frequency in Hz (typically 150-200 for bass)
     * @param gainDb Gain in decibels
     * @param s Shelf slope (1.0 = 6dB/octave, 0.5 = 3dB/octave)
     */
    fun configureLowShelf(sampleRate: Int, frequency: Int, gainDb: Double, s: Double = 1.0) {
        if (gainDb == 0.0) {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
            return
        }

        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)

        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / s - 1.0) + 2.0)
        val sqrtAAlpha = 2.0 * sqrt(a) * alpha

        val b0Unnorm = a * ((a + 1.0) - (a - 1.0) * cosW0 + sqrtAAlpha)
        val b1Unnorm = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val b2Unnorm = a * ((a + 1.0) - (a - 1.0) * cosW0 - sqrtAAlpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + sqrtAAlpha
        val a1Unnorm = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val a2Unnorm = (a + 1.0) + (a - 1.0) * cosW0 - sqrtAAlpha

        b0 = b0Unnorm / a0
        b1 = b1Unnorm / a0
        b2 = b2Unnorm / a0
        a1 = a1Unnorm / a0
        a2 = a2Unnorm / a0

        // Update config cache (use negative frequency to distinguish from peaking)
        configuredSampleRate = sampleRate
        configuredFrequency = -frequency
        configuredGainDb = gainDb
        configuredQ = s
    }

    /**
     * Process a single audio sample through the filter.
     *
     * @param sample Input sample (normalized -1.0 to 1.0)
     * @param channel Channel index (0 for left, 1 for right, etc.)
     * @return Filtered output sample
     */
    fun process(sample: Double, channel: Int): Double {
        val ch = channel.coerceIn(0, 7)

        // Difference equation:
        // y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
        val output = b0 * sample +
                     b1 * stateX1[ch] +
                     b2 * stateX2[ch] -
                     a1 * stateY1[ch] -
                     a2 * stateY2[ch]

        // Shift state
        stateX2[ch] = stateX1[ch]
        stateX1[ch] = sample
        stateY2[ch] = stateY1[ch]
        stateY1[ch] = output

        return output
    }

    /**
     * Reset filter state to zero.
     * Call this on seek or track change to avoid audio artifacts.
     */
    fun reset() {
        stateX1.fill(0.0)
        stateX2.fill(0.0)
        stateY1.fill(0.0)
        stateY2.fill(0.0)
    }

    /**
     * Reset filter state for a specific channel.
     */
    fun reset(channel: Int) {
        val ch = channel.coerceIn(0, 7)
        stateX1[ch] = 0.0
        stateX2[ch] = 0.0
        stateY1[ch] = 0.0
        stateY2[ch] = 0.0
    }

    /**
     * Check if the filter is configured for unity gain (pass-through).
     */
    fun isPassthrough(): Boolean {
        return b0 == 1.0 && b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0
    }

    /**
     * Get the current gain setting in dB.
     */
    fun getGainDb(): Double = configuredGainDb

    /**
     * Get the current center frequency.
     */
    fun getFrequency(): Int = if (configuredFrequency < 0) -configuredFrequency else configuredFrequency
}
