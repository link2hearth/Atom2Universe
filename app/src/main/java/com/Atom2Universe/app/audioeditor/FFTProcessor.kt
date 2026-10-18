package com.Atom2Universe.app.audioeditor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fast Fourier Transform (FFT) processor for audio spectral analysis.
 * Uses Cooley-Tukey radix-2 decimation-in-time algorithm.
 */
class FFTProcessor {

    companion object {
        // Common FFT size (must be power of 2)
        const val FFT_SIZE_1024 = 1024

        private const val MAX_SPECTROGRAM_FRAMES = 2000
    }

    /**
     * Perform FFT on real-valued input samples.
     *
     * @param samples Input samples (should be power of 2 length)
     * @return Array of magnitude values (half the input size, representing positive frequencies)
     * @throws IllegalArgumentException if sample size is not a power of 2
     */
    fun computeMagnitude(samples: FloatArray): FloatArray {
        val n = samples.size
        // Bug 2.18: Replace require() with explicit validation for better error handling
        if (n <= 0) {
            throw IllegalArgumentException("Sample size must be positive, got $n")
        }
        if (!isPowerOfTwo(n)) {
            throw IllegalArgumentException("Sample size must be a power of 2, got $n")
        }

        // Convert to complex (imaginary parts = 0)
        val real = samples.copyOf()
        val imag = FloatArray(n)

        // Perform in-place FFT
        fft(real, imag)

        // Compute magnitudes (only need first half due to symmetry)
        val magnitudes = FloatArray(n / 2)
        for (i in 0 until n / 2) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        return magnitudes
    }

    /**
     * Perform FFT and return magnitude in dB scale.
     *
     * @param samples Input samples
     * @param minDb Minimum dB value (for normalization)
     * @param maxDb Maximum dB value (for normalization)
     * @return Array of normalized dB values [0, 1]
     */
    fun computeMagnitudeDb(
        samples: FloatArray,
        minDb: Float = -60f,
        maxDb: Float = 0f
    ): FloatArray {
        val magnitudes = computeMagnitude(samples)
        val dbValues = FloatArray(magnitudes.size)
        val range = maxDb - minDb

        for (i in magnitudes.indices) {
            // Bug 2.34: Convert to dB with protection against log10(0)
            // Use coerceAtLeast to ensure we never take log of zero or negative
            val safeMagnitude = magnitudes[i].coerceAtLeast(Float.MIN_VALUE)
            val db = (20 * kotlin.math.log10(safeMagnitude.toDouble())).toFloat()
            // Normalize to [0, 1]
            dbValues[i] = ((db - minDb) / range).coerceIn(0f, 1f)
        }

        return dbValues
    }

    /**
     * Compute spectrogram from audio samples.
     *
     * @param samples All audio samples
     * @param fftSize Size of each FFT window
     * @param hopSize Number of samples to advance between windows
     * @param windowFunc Window function to apply (default: Hann)
     * @return 2D array (time x frequency) of magnitude values
     * @throws IllegalArgumentException if fftSize is not a power of 2 or hopSize is invalid
     */
    fun computeSpectrogram(
        samples: FloatArray,
        fftSize: Int = FFT_SIZE_1024,
        hopSize: Int = fftSize / 4,
        windowFunc: WindowFunction = WindowFunction.HANN
    ): Array<FloatArray> {
        // Bug 2.18: Replace require() with explicit validation
        if (fftSize <= 0) {
            throw IllegalArgumentException("FFT size must be positive, got $fftSize")
        }
        if (!isPowerOfTwo(fftSize)) {
            throw IllegalArgumentException("FFT size must be a power of 2, got $fftSize")
        }
        // Bug 2.19: Validate hopSize to prevent division issues
        if (hopSize <= 0) {
            throw IllegalArgumentException("hopSize must be positive, got $hopSize")
        }

        if (samples.size <= fftSize) {
            return emptyArray()
        }

        // Bug 2.19: Dynamically increase hopSize to cap the number of frames
        // Bug 2.35: Use floating-point calculations for precision, then round
        val availableSamples = samples.size - fftSize
        val naiveFrames = availableSamples.toDouble() / hopSize.toDouble() + 1.0
        val effectiveHopSize: Int
        val effectiveNumFrames: Int

        if (naiveFrames > MAX_SPECTROGRAM_FRAMES) {
            // Recalculate hopSize to fit within frame limit using floating-point division
            effectiveHopSize = kotlin.math.ceil(availableSamples.toDouble() / (MAX_SPECTROGRAM_FRAMES - 1).toDouble()).toInt().coerceAtLeast(1)
            // Recalculate frames with new hop size using float then round down
            effectiveNumFrames = (availableSamples.toDouble() / effectiveHopSize.toDouble() + 1.0).toInt()
                .coerceAtMost(MAX_SPECTROGRAM_FRAMES)
        } else {
            effectiveHopSize = hopSize
            effectiveNumFrames = naiveFrames.toInt().coerceAtMost(MAX_SPECTROGRAM_FRAMES)
        }

        val spectrogram = Array(effectiveNumFrames) { FloatArray(fftSize / 2) }
        val window = createWindow(fftSize, windowFunc)
        val windowedSamples = FloatArray(fftSize)

        for (frame in 0 until effectiveNumFrames) {
            val startIdx = frame * effectiveHopSize

            // Apply window function
            for (i in 0 until fftSize) {
                val sampleIdx = startIdx + i
                windowedSamples[i] = if (sampleIdx < samples.size) {
                    samples[sampleIdx] * window[i]
                } else {
                    0f
                }
            }

            // Compute FFT magnitudes in dB
            spectrogram[frame] = computeMagnitudeDb(windowedSamples)
        }

        return spectrogram
    }

    /**
     * In-place Cooley-Tukey FFT algorithm.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                // Swap real[i] and real[j]
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp
                // Swap imag[i] and imag[j]
                temp = imag[i]
                imag[i] = imag[j]
                imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey decimation-in-time
        var mmax = 1
        while (n > mmax) {
            val step = mmax * 2
            val theta = (-PI / mmax).toFloat()
            var wr = 1.0f
            var wi = 0.0f
            val wpr = cos(theta)
            val wpi = sin(theta)

            for (m in 0 until mmax) {
                for (i in m until n step step) {
                    val j2 = i + mmax
                    val tr = wr * real[j2] - wi * imag[j2]
                    val ti = wr * imag[j2] + wi * real[j2]

                    real[j2] = real[i] - tr
                    imag[j2] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                }
                val wtemp = wr
                wr = wr * wpr - wi * wpi
                wi = wi * wpr + wtemp * wpi
            }
            mmax = step
        }
    }

    /**
     * Create a window function array.
     */
    private fun createWindow(size: Int, type: WindowFunction): FloatArray {
        return when (type) {
            WindowFunction.RECTANGULAR -> FloatArray(size) { 1f }
            WindowFunction.HANN -> FloatArray(size) { i ->
                (0.5 * (1 - cos(2 * PI * i / (size - 1)))).toFloat()
            }
            WindowFunction.HAMMING -> FloatArray(size) { i ->
                (0.54 - 0.46 * cos(2 * PI * i / (size - 1))).toFloat()
            }
            WindowFunction.BLACKMAN -> FloatArray(size) { i ->
                val a0 = 0.42
                val a1 = 0.5
                val a2 = 0.08
                (a0 - a1 * cos(2 * PI * i / (size - 1)) + a2 * cos(4 * PI * i / (size - 1))).toFloat()
            }
        }
    }

    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }

    /**
     * Window functions for spectral analysis.
     */
    enum class WindowFunction {
        RECTANGULAR,
        HANN,
        HAMMING,
        BLACKMAN
    }

    /**
     * Result of spectral analysis containing both spectrogram and metadata.
     */
    data class SpectrogramResult(
        val data: Array<FloatArray>,
        val sampleRate: Int,
        val fftSize: Int,
        val hopSize: Int,
        val frequencyBins: Int,
        val timeFrames: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SpectrogramResult
            if (!data.contentDeepEquals(other.data)) return false
            if (sampleRate != other.sampleRate) return false
            if (fftSize != other.fftSize) return false
            return true
        }

        override fun hashCode(): Int {
            var result = data.contentDeepHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + fftSize
            return result
        }
    }
}
