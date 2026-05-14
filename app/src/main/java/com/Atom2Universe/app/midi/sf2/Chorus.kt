package com.Atom2Universe.app.midi.sf2

import kotlin.math.floor
import kotlin.math.sin

/**
 * Stereo chorus effect for SF2 synthesis.
 *
 * Implements a 3-voice modulated delay line (inspired by FluidSynth's chorus).
 * Each voice uses a sine LFO at a different phase (0°, 120°, 240°) to modulate
 * the delay time, creating a rich, thick sound.
 *
 * Used as a send effect: voices accumulate their chorus send contributions
 * into dedicated send buffers, this effect processes them, and the result
 * is mixed back into the main output.
 *
 * SF2 Parameter: CC93 (Chorus Send) controls per-channel send level.
 */
class Chorus(private val sampleRate: Int = 44100) {

    companion object {
        // Number of modulated delay taps
        const val NUM_VOICES = 3

        // Maximum delay for buffer allocation (ms)
        // Must accommodate center delay + max modulation depth
        private const val MAX_DELAY_MS = 50f

        // Center delay time (ms) - the midpoint around which modulation occurs
        private const val CENTER_DELAY_MS = 10f

        private const val TWO_PI = 2.0 * Math.PI
    }

    // Enable/disable chorus (when disabled, processing is completely bypassed)
    @Volatile
    var enabled: Boolean = false

    // Chorus parameters
    var speed: Float = 0.3f           // LFO rate in Hz (0.1 - 5.0)
        set(value) { field = value.coerceIn(0.1f, 5f) }

    var depth: Float = 8f             // Modulation depth in ms (0.5 - 20.0)
        set(value) { field = value.coerceIn(0.5f, 20f) }

    var level: Float = 0.4f           // Output level for mixing back into main output
        set(value) { field = value.coerceIn(0f, 1f) }

    // Stereo delay lines (circular buffers)
    private val delayBufferSize = ((MAX_DELAY_MS * sampleRate / 1000f) + 2).toInt()
    private val delayBufferL = FloatArray(delayBufferSize)
    private val delayBufferR = FloatArray(delayBufferSize)
    private var writePos = 0

    // LFO phases for each voice (spread evenly: 0°, 120°, 240°)
    private val lfoPhases = DoubleArray(NUM_VOICES) { i ->
        i.toDouble() / NUM_VOICES * TWO_PI
    }

    /**
     * Processes the chorus send buffers in-place.
     *
     * Input: accumulated voice signals at per-channel chorus send levels.
     * Output: chorus-processed signal (100% wet, no dry signal).
     * The caller mixes this back into the main output at the desired level.
     *
     * @param left Left channel send buffer (modified in-place)
     * @param right Right channel send buffer (modified in-place)
     * @param numSamples Number of samples to process
     */
    fun process(left: FloatArray, right: FloatArray, numSamples: Int) {
        if (!enabled) return

        val phaseInc = TWO_PI * speed / sampleRate
        val depthSamp = depth * sampleRate / 1000f
        val centerDelay = CENTER_DELAY_MS * sampleRate / 1000f

        for (i in 0 until numSamples) {
            // Write current input to delay buffer
            delayBufferL[writePos] = left[i]
            delayBufferR[writePos] = right[i]

            var wetL = 0f
            var wetR = 0f

            for (v in 0 until NUM_VOICES) {
                // Sine LFO modulates the delay time for this voice
                val lfoVal = sin(lfoPhases[v]).toFloat()

                // Modulated delay in samples (center ± depth)
                val delaySamp = centerDelay + lfoVal * depthSamp

                // Read position with fractional part for linear interpolation
                val readPosF = writePos.toFloat() - delaySamp
                val readPosI = floor(readPosF).toInt()
                val frac = readPosF - readPosI

                // Wrap indices into circular buffer (handles negative positions)
                val idx0 = ((readPosI % delayBufferSize) + delayBufferSize) % delayBufferSize
                val idx1 = (idx0 + 1) % delayBufferSize

                // Linear interpolation for smooth delay reading
                wetL += delayBufferL[idx0] + (delayBufferL[idx1] - delayBufferL[idx0]) * frac
                wetR += delayBufferR[idx0] + (delayBufferR[idx1] - delayBufferR[idx0]) * frac

                // Advance LFO phase
                lfoPhases[v] += phaseInc
            }

            // Output average of all voices (100% wet, caller handles mix level)
            left[i] = wetL / NUM_VOICES
            right[i] = wetR / NUM_VOICES

            writePos = (writePos + 1) % delayBufferSize
        }

        // Wrap LFO phases to prevent floating-point accumulation
        for (v in 0 until NUM_VOICES) {
            lfoPhases[v] = lfoPhases[v] % TWO_PI
        }
    }

    /**
     * Applies a preset configuration.
     * @param preset -1 = Off, 0 = Light, 1 = Default, 2 = Rich
     */
    fun applyPreset(preset: Int) {
        when (preset) {
            -1 -> enabled = false
            0 -> { enabled = true; speed = 0.3f; depth = 5f; level = 0.3f }
            1 -> { enabled = true; speed = 0.5f; depth = 8f; level = 0.4f }
            2 -> { enabled = true; speed = 0.8f; depth = 12f; level = 0.5f }
        }
    }

    /**
     * Resets the chorus state (clears delay buffers and LFO phases).
     */
    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writePos = 0
        for (v in 0 until NUM_VOICES) {
            lfoPhases[v] = v.toDouble() / NUM_VOICES * TWO_PI
        }
    }

    /**
     * Returns debug info.
     */
    fun getDebugInfo(): String {
        return if (enabled) {
            "Chorus: ${String.format("%.1f", speed)}Hz depth=${String.format("%.0f", depth)}ms level=${String.format("%.1f", level)}"
        } else {
            "Chorus: disabled"
        }
    }
}
