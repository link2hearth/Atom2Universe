package com.Atom2Universe.app.sf2creator.audio

import com.Atom2Universe.app.sf2creator.data.PitchResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Detects the fundamental pitch of audio samples using FFT analysis.
 * Uses autocorrelation-enhanced FFT for improved accuracy with harmonic sounds.
 */
class PitchDetector {

    companion object {
        private const val FFT_SIZE = 4096
        private const val MIN_FREQUENCY = 20f // Hz
        private const val MAX_FREQUENCY = 4000f // Hz (covers piano range)
        private const val NOISE_THRESHOLD = 0.01f
    }

    /**
     * Detect the fundamental pitch from audio samples.
     *
     * @param samples Audio samples as ShortArray
     * @param sampleRate Sample rate in Hz
     * @return PitchResult containing detected frequency, MIDI note, and confidence
     */
    fun detectPitch(samples: ShortArray, sampleRate: Int): PitchResult {
        if (samples.isEmpty()) {
            return PitchResult.UNKNOWN
        }

        // Convert to float and normalize
        val floatSamples = samples.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()

        return detectPitchFromFloat(floatSamples, sampleRate)
    }

    /**
     * Detect pitch from float samples (normalized -1 to 1).
     */
    fun detectPitchFromFloat(samples: FloatArray, sampleRate: Int): PitchResult {
        if (samples.isEmpty()) {
            return PitchResult.UNKNOWN
        }

        // Find the section with highest energy (most likely the sustained part)
        val analysisStart = findBestAnalysisWindow(samples, FFT_SIZE)
        val analysisEnd = minOf(analysisStart + FFT_SIZE, samples.size)
        val analysisLength = analysisEnd - analysisStart

        if (analysisLength < FFT_SIZE / 2) {
            return PitchResult.UNKNOWN
        }

        // Extract analysis window
        val window = FloatArray(FFT_SIZE)
        for (i in 0 until analysisLength) {
            window[i] = samples[analysisStart + i]
        }

        // Check if the signal is above noise threshold
        val rms = calculateRMS(window)
        if (rms < NOISE_THRESHOLD) {
            return PitchResult.UNKNOWN
        }

        // Apply Hann window to reduce spectral leakage
        applyHannWindow(window)

        // Compute FFT
        val (real, imag) = computeFFT(window)

        // Compute magnitude spectrum
        val magnitudes = FloatArray(FFT_SIZE / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        // Find fundamental frequency using peak detection with harmonic validation
        val result = findFundamentalFrequency(magnitudes, sampleRate)

        return result
    }

    /**
     * Find the best window for analysis (section with highest energy).
     */
    private fun findBestAnalysisWindow(samples: FloatArray, windowSize: Int): Int {
        if (samples.size <= windowSize) return 0

        var bestStart = 0
        var maxEnergy = 0f
        val hopSize = windowSize / 4

        var start = 0
        while (start + windowSize <= samples.size) {
            var energy = 0f
            for (i in 0 until windowSize) {
                val s = samples[start + i]
                energy += s * s
            }
            if (energy > maxEnergy) {
                maxEnergy = energy
                bestStart = start
            }
            start += hopSize
        }

        return bestStart
    }

    /**
     * Apply Hann window function in-place.
     */
    private fun applyHannWindow(samples: FloatArray) {
        for (i in samples.indices) {
            val multiplier = 0.5 * (1 - cos(2 * PI * i / (samples.size - 1)))
            samples[i] = (samples[i] * multiplier).toFloat()
        }
    }

    /**
     * Calculate RMS (Root Mean Square) of samples.
     */
    private fun calculateRMS(samples: FloatArray): Float {
        var sum = 0f
        for (sample in samples) {
            sum += sample * sample
        }
        return sqrt(sum / samples.size)
    }

    /**
     * Compute FFT using Cooley-Tukey algorithm.
     * Returns pair of (real, imaginary) arrays.
     */
    private fun computeFFT(samples: FloatArray): Pair<FloatArray, FloatArray> {
        val n = samples.size
        val real = samples.copyOf()
        val imag = FloatArray(n)

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp
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
            val wpi = kotlin.math.sin(theta)

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

        return Pair(real, imag)
    }

    /**
     * Find the fundamental frequency from the magnitude spectrum.
     * Uses harmonic product spectrum for better fundamental detection.
     */
    private fun findFundamentalFrequency(magnitudes: FloatArray, sampleRate: Int): PitchResult {
        { bin: Int -> bin * sampleRate.toFloat() / FFT_SIZE }
        val freqToBin = { freq: Float -> (freq * FFT_SIZE / sampleRate).toInt() }

        // Calculate frequency range bins
        val minBin = freqToBin(MIN_FREQUENCY).coerceAtLeast(1)
        val maxBin = freqToBin(MAX_FREQUENCY).coerceAtMost(magnitudes.size - 1)

        // Use Harmonic Product Spectrum (HPS) for fundamental detection
        val hps = FloatArray(magnitudes.size) { magnitudes[it] }

        // Downsample and multiply for harmonics 2, 3, 4
        for (harmonic in 2..4) {
            for (bin in minBin until magnitudes.size / harmonic) {
                val harmonicBin = bin * harmonic
                if (harmonicBin < magnitudes.size) {
                    hps[bin] *= magnitudes[harmonicBin]
                }
            }
        }

        // Find the peak in the HPS
        var maxMag = 0f
        var peakBin = minBin

        for (bin in minBin until maxBin) {
            if (hps[bin] > maxMag) {
                maxMag = hps[bin]
                peakBin = bin
            }
        }

        // Parabolic interpolation for sub-bin accuracy
        val refinedBin = if (peakBin > 0 && peakBin < hps.size - 1) {
            val alpha = hps[peakBin - 1]
            val beta = hps[peakBin]
            val gamma = hps[peakBin + 1]
            val denominator = alpha - 2 * beta + gamma
            if (denominator != 0f) {
                val delta = (alpha - gamma) / (2 * denominator)
                peakBin + delta
            } else {
                peakBin.toFloat()
            }
        } else {
            peakBin.toFloat()
        }

        val frequency = refinedBin * sampleRate / FFT_SIZE

        // Calculate confidence based on peak prominence
        val avgMag = magnitudes.slice(minBin until maxBin).average().toFloat()
        val confidence = if (avgMag > 0) {
            (magnitudes[peakBin] / avgMag / 10f).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Validate frequency is in reasonable range
        if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
            return PitchResult.UNKNOWN
        }

        return PitchResult.fromFrequency(frequency, confidence)
    }

    /**
     * Analyze the whole sample and return multiple pitch candidates.
     * Useful for letting the user choose if auto-detection is uncertain.
     */
    fun analyzePitchCandidates(samples: ShortArray, sampleRate: Int, numCandidates: Int = 3): List<PitchResult> {
        if (samples.isEmpty()) return emptyList()

        // Convert to float
        val floatSamples = samples.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()

        // Find best analysis window
        val analysisStart = findBestAnalysisWindow(floatSamples, FFT_SIZE)
        val analysisLength = minOf(FFT_SIZE, floatSamples.size - analysisStart)

        if (analysisLength < FFT_SIZE / 2) return emptyList()

        // Extract and window
        val window = FloatArray(FFT_SIZE)
        for (i in 0 until analysisLength) {
            window[i] = floatSamples[analysisStart + i]
        }
        applyHannWindow(window)

        // Compute FFT
        val (real, imag) = computeFFT(window)

        // Compute magnitude spectrum
        val magnitudes = FloatArray(FFT_SIZE / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        // Find multiple peaks
        return findTopPeaks(magnitudes, sampleRate, numCandidates)
    }

    /**
     * Find the top N peaks in the magnitude spectrum.
     */
    private fun findTopPeaks(magnitudes: FloatArray, sampleRate: Int, n: Int): List<PitchResult> {
        val binToFreq = { bin: Int -> bin * sampleRate.toFloat() / FFT_SIZE }
        val freqToBin = { freq: Float -> (freq * FFT_SIZE / sampleRate).toInt() }

        val minBin = freqToBin(MIN_FREQUENCY).coerceAtLeast(1)
        val maxBin = freqToBin(MAX_FREQUENCY).coerceAtMost(magnitudes.size - 1)

        // Find all local maxima
        val peaks = mutableListOf<Pair<Int, Float>>()
        for (bin in minBin + 1 until maxBin - 1) {
            if (magnitudes[bin] > magnitudes[bin - 1] && magnitudes[bin] > magnitudes[bin + 1]) {
                peaks.add(Pair(bin, magnitudes[bin]))
            }
        }

        // Sort by magnitude and take top N
        val avgMag = magnitudes.slice(minBin until maxBin).average().toFloat()

        return peaks
            .sortedByDescending { it.second }
            .take(n)
            .map { (bin, mag) ->
                val freq = binToFreq(bin)
                val confidence = if (avgMag > 0) (mag / avgMag / 10f).coerceIn(0f, 1f) else 0f
                PitchResult.fromFrequency(freq, confidence)
            }
            .distinctBy { it.midiNote } // Remove duplicates that map to same note
    }
}
