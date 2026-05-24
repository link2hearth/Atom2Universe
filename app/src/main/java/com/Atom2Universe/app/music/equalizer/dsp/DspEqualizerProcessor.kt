package com.Atom2Universe.app.music.equalizer.dsp

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.min

/**
 * Custom DSP-based equalizer that processes audio samples directly.
 * Implements ExoPlayer's AudioProcessor interface to intercept audio data
 * before it reaches the Android audio system.
 *
 * This bypasses Samsung SoundAlive and other system-level audio effects
 * that interfere with the standard Android Equalizer API.
 */
@OptIn(UnstableApi::class)
class DspEqualizerProcessor : AudioProcessor {

    companion object {
        private const val TAG = "DspEqualizerProcessor"

        /** Number of EQ bands */
        const val BAND_COUNT = 10

        /** Center frequencies for each band in Hz */
        val BAND_FREQUENCIES = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Bass boost center frequency */
        private const val BASS_BOOST_FREQUENCY = 100

        /** Q factor for peaking EQ bands */
        private const val BAND_Q = 1.41

        /** Pre-gain reduction when boosting to prevent clipping (dB) */
        private const val PREGAIN_HEADROOM_DB = 3.0
    }

    // Filter banks - one biquad filter per band
    private val bandFilters = Array(BAND_COUNT) { BiquadFilter() }

    // Low-shelf filter for bass boost
    private val bassBoostFilter = BiquadFilter()

    // Current band gains in dB (-12 to +12)
    private val bandGains = DoubleArray(BAND_COUNT)

    // Bass boost strength (0-1000, converted to dB internally)
    @Volatile
    private var bassBoostStrength = 0

    // Virtualizer (stereo widening) strength (0-1000)
    @Volatile
    private var virtualizerStrength = 0

    // Enable/disable flag
    @Volatile
    private var isEnabled = true

    // Lock for thread-safe band level updates
    private val lock = ReentrantLock()

    // Audio format
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 44100
    private var channelCount = 2

    // Processing state
    private var inputEnded = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputBuffer = AudioProcessor.EMPTY_BUFFER

    // Pre-computed pre-gain value
    private var preGain = 1.0

    /**
     * Set the gain for a specific band.
     *
     * @param bandIndex Band index (0-9)
     * @param level Level in millibels (-1200 to +1200)
     */
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in 0 until BAND_COUNT) return

        lock.withLock {
            // Convert millibels to dB (1 millibel = 0.01 dB)
            val gainDb = level / 100.0
            bandGains[bandIndex] = gainDb.coerceIn(-12.0, 12.0)

            bandFilters[bandIndex].configure(
                sampleRate,
                BAND_FREQUENCIES[bandIndex],
                bandGains[bandIndex],
                BAND_Q
            )

            updatePreGain()
            rebuildActiveFiltersCache()
        }
    }

    /**
     * Get the current gain for a specific band.
     *
     * @param bandIndex Band index (0-9)
     * @return Level in millibels
     */
    fun getBandLevel(bandIndex: Int): Int {
        if (bandIndex !in 0 until BAND_COUNT) return 0
        return lock.withLock {
            (bandGains[bandIndex] * 100).toInt()
        }
    }

    /**
     * Set all band levels at once.
     *
     * @param levels List of levels in millibels (must be 10 elements)
     */
    fun setAllBandLevels(levels: List<Int>) {
        if (levels.size != BAND_COUNT) return

        lock.withLock {
            for (i in 0 until BAND_COUNT) {
                val gainDb = levels[i] / 100.0
                bandGains[i] = gainDb.coerceIn(-12.0, 12.0)
                bandFilters[i].configure(
                    sampleRate,
                    BAND_FREQUENCIES[i],
                    bandGains[i],
                    BAND_Q
                )
            }
            updatePreGain()
            rebuildActiveFiltersCache()
        }
    }

    /**
     * Set bass boost strength.
     *
     * @param strength 0-1000
     */
    fun setBassBoost(strength: Int) {
        lock.withLock {
            bassBoostStrength = strength.coerceIn(0, 1000)
            // Convert 0-1000 to 0-12 dB
            val gainDb = bassBoostStrength / 1000.0 * 12.0
            bassBoostFilter.configureLowShelf(sampleRate, BASS_BOOST_FREQUENCY, gainDb, 0.7)
            updatePreGain()
        }
    }

    /**
     * Get current bass boost strength.
     */
    fun getBassBoost(): Int = bassBoostStrength

    /**
     * Set virtualizer (stereo widening) strength.
     *
     * @param strength 0-1000
     */
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
    }

    /**
     * Get current virtualizer strength.
     */
    fun getVirtualizer(): Int = virtualizerStrength

    /**
     * Enable or disable the equalizer processing.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "DSP Equalizer enabled: $enabled")
    }

    /**
     * Check if equalizer is enabled.
     */
    fun isEnabled(): Boolean = isEnabled

    /**
     * Reset all filter states. Call on seek or track change.
     */
    fun resetFilters() {
        lock.withLock {
            bandFilters.forEach { it.reset() }
            bassBoostFilter.reset()
            prevLeft = 0.0
            prevRight = 0.0
        }
    }

    /**
     * Update the pre-gain value to prevent clipping when boosting.
     * Should be called whenever band levels change.
     */
    private fun updatePreGain() {
        // Find the maximum positive gain across all bands and bass boost
        var maxBoost = 0.0
        for (gain in bandGains) {
            if (gain > maxBoost) maxBoost = gain
        }

        // Add bass boost contribution
        val bassBoostDb = bassBoostStrength / 1000.0 * 12.0
        if (bassBoostDb > maxBoost) maxBoost = bassBoostDb

        // Apply headroom if there's any boost
        preGain = if (maxBoost > 0) {
            val headroom = min(maxBoost, PREGAIN_HEADROOM_DB)
            Math.pow(10.0, -headroom / 20.0)
        } else {
            1.0
        }
    }

    // AudioProcessor implementation

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Check if we can process this format
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            // Cannot process this format, pass through
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            outputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }

        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        lock.withLock {
            for (i in 0 until BAND_COUNT) {
                bandFilters[i].configure(sampleRate, BAND_FREQUENCIES[i], bandGains[i], BAND_Q)
            }
            val bassBoostDb = bassBoostStrength / 1000.0 * 12.0
            bassBoostFilter.configureLowShelf(sampleRate, BASS_BOOST_FREQUENCY, bassBoostDb, 0.7)
            rebuildActiveFiltersCache()
        }

        Log.d(TAG, "Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        // Output format is same as input
        outputFormat = inputAudioFormat
        return outputFormat
    }

    override fun isActive(): Boolean {
        // Processor is active when enabled and format is valid
        return inputFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || inputBuffer.remaining() == 0) {
            return
        }

        this.inputBuffer = inputBuffer
        val remaining = inputBuffer.remaining()

        // Allocate output buffer
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (!isEnabled) {
            // Passthrough - copy input to output
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputFormat.encoding
        when (encoding) {
            C.ENCODING_PCM_16BIT -> processInt16(inputBuffer, outputBuffer)
            C.ENCODING_PCM_FLOAT -> processFloat32(inputBuffer, outputBuffer)
            else -> {
                Log.w(TAG, "Unsupported audio encoding $encoding, passthrough.")
                outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
    }

    /**
     * Process 16-bit PCM audio.
     */
    private fun processInt16(input: ByteBuffer, output: ByteBuffer) {
        val shortInput = input.asShortBuffer()
        val shortOutput = output.asShortBuffer()
        val sampleCount = input.remaining() / 2
        val frameCount = sampleCount / channelCount

        lock.withLock {
            // Hoist loop-invariant computations out of the per-sample loop.
            val bassActive = bassBoostStrength > 0
            val virtActive = virtualizerStrength > 0 && channelCount == 2
            val virtAmount = virtualizerStrength / 1000.0 * 0.3
            val activeFilters = activeFiltersCache

            for (frame in 0 until frameCount) {
                for (channel in 0 until channelCount) {
                    var sample = shortInput.get() / 32768.0 * preGain

                    for (filter in activeFilters) {
                        sample = filter.process(sample, channel)
                    }

                    if (bassActive) sample = bassBoostFilter.process(sample, channel)
                    if (virtActive) sample = applyVirtualizer(sample, channel, virtAmount)

                    sample = softClip(sample)
                    shortOutput.put((sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }

        // Update ByteBuffer positions
        input.position(input.position() + sampleCount * 2)
        output.position(output.position() + sampleCount * 2)
    }

    /**
     * Process 32-bit float PCM audio.
     */
    private fun processFloat32(input: ByteBuffer, output: ByteBuffer) {
        val floatInput = input.asFloatBuffer()
        val floatOutput = output.asFloatBuffer()
        val sampleCount = input.remaining() / 4
        val frameCount = sampleCount / channelCount

        lock.withLock {
            val bassActive = bassBoostStrength > 0
            val virtActive = virtualizerStrength > 0 && channelCount == 2
            val virtAmount = virtualizerStrength / 1000.0 * 0.3
            val activeFilters = activeFiltersCache

            for (frame in 0 until frameCount) {
                for (channel in 0 until channelCount) {
                    var sample = floatInput.get().toDouble() * preGain

                    for (filter in activeFilters) {
                        sample = filter.process(sample, channel)
                    }

                    if (bassActive) sample = bassBoostFilter.process(sample, channel)
                    if (virtActive) sample = applyVirtualizer(sample, channel, virtAmount)

                    sample = softClip(sample)
                    floatOutput.put(sample.toFloat())
                }
            }
        }

        input.position(input.position() + sampleCount * 4)
        output.position(output.position() + sampleCount * 4)
    }

    private fun rebuildActiveFiltersCache() {
        val list = ArrayList<BiquadFilter>(BAND_COUNT)
        for (i in 0 until BAND_COUNT) {
            if (!bandFilters[i].isPassthrough()) list.add(bandFilters[i])
        }
        activeFiltersCache = list
    }

    // State for virtualizer (simple stereo widening)
    private var prevLeft = 0.0
    private var prevRight = 0.0

    // Cached list of active (non-passthrough) filters — rebuilt uniquement quand les gains changent
    private var activeFiltersCache: List<BiquadFilter> = emptyList()

    /**
     * Simple stereo widening effect.
     * Adds a portion of the opposite channel's previous sample (Haas effect simulation).
     * [amount] doit être pré-calculé avant la boucle (virtualizerStrength / 1000.0 * 0.3).
     */
    private fun applyVirtualizer(sample: Double, channel: Int, amount: Double): Double {
        return when (channel) {
            0 -> { // Left channel
                val widened = sample - prevRight * amount
                prevLeft = sample
                widened
            }
            1 -> { // Right channel
                val widened = sample - prevLeft * amount
                prevRight = sample
                widened
            }
            else -> sample
        }
    }

    /**
     * Soft clipping function to prevent harsh digital distortion.
     * Uses a tanh-based curve for smooth limiting.
     */
    private fun softClip(sample: Double): Double {
        val absVal = abs(sample)
        return if (absVal <= 0.5) {
            sample
        } else if (absVal <= 1.0) {
            // Smooth transition
            val sign = if (sample >= 0) 1.0 else -1.0
            sign * (0.5 + (absVal - 0.5) * 0.8)
        } else {
            // Hard limit with soft knee
            val sign = if (sample >= 0) 1.0 else -1.0
            sign * min(1.0, 0.9 + 0.1 * Math.tanh(absVal - 1.0))
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    @Deprecated("Deprecated in AudioProcessor interface", ReplaceWith("reset()"))
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        resetFilters()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        outputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44100
        channelCount = 2

        lock.withLock {
            bandGains.fill(0.0)
            for (i in 0 until BAND_COUNT) {
                bandFilters[i].configure(sampleRate, BAND_FREQUENCIES[i], 0.0, BAND_Q)
            }
            bassBoostStrength = 0
            virtualizerStrength = 0
            preGain = 1.0
            activeFiltersCache = emptyList()
        }
    }
}
