package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.*

/**
 * Noise profiler using spectral subtraction for background noise reduction.
 *
 * Workflow:
 * 1. captureNoiseProfile() — analyze ambient sound captured during the countdown
 *    (before the user's instrument/voice starts).
 * 2. apply() — apply spectral subtraction to the recorded sample using that profile.
 *
 * The algorithm:
 * - Split input into overlapping frames (Hann window, 50% overlap).
 * - FFT each frame → subtract the averaged noise power spectrum → IFFT.
 * - Overlap-add synthesis for smooth reconstruction.
 *
 * This effectively removes steady-state noise (room tone, fans, HVAC, hum)
 * while preserving the musical signal.
 */
class NoiseProfiler(private val sampleRate: Int = 44100) {

    companion object {
        // 1024-point FFT ≈ 23 ms frame at 44100 Hz (must be power of 2)
        private const val FFT_SIZE = 1024
        private const val HOP_SIZE = FFT_SIZE / 2  // 50% overlap

        // Spectral subtraction parameters
        private const val ALPHA = 2.5f   // Over-subtraction factor (higher = more aggressive)
        private const val BETA = 0.05f   // Spectral floor (5% of noise → prevents musical noise)

        /** Minimum samples needed to build a valid noise profile (200 ms). */
        const val MIN_NOISE_SAMPLES = 8820
    }

    private var noiseSpectrum: FloatArray? = null  // Averaged noise power spectrum
    private var measuredNoiseRms: Float = 0f

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Analyse background noise samples and build a spectral noise profile.
     * Call this with audio recorded during the pre-recording silence
     * (countdown period, before the instrument starts playing).
     *
     * @param noiseSamples PCM 16-bit mono samples containing only ambient noise.
     */
    fun captureNoiseProfile(noiseSamples: ShortArray) {
        if (noiseSamples.size < FFT_SIZE) return

        val window = hannWindow(FFT_SIZE)
        val avgSpectrum = FloatArray(FFT_SIZE / 2 + 1)
        var frameCount = 0

        // Measure overall RMS noise level
        var sumSq = 0.0
        for (s in noiseSamples) {
            val norm = s.toFloat() / Short.MAX_VALUE
            sumSq += norm * norm
        }
        measuredNoiseRms = sqrt(sumSq / noiseSamples.size).toFloat()

        // Average the power spectrum across all frames
        var pos = 0
        while (pos + FFT_SIZE <= noiseSamples.size) {
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)
            for (i in 0 until FFT_SIZE) {
                re[i] = (noiseSamples[pos + i].toFloat() / Short.MAX_VALUE) * window[i]
            }
            fft(re, im)
            for (k in 0..FFT_SIZE / 2) {
                avgSpectrum[k] += re[k] * re[k] + im[k] * im[k]
            }
            frameCount++
            pos += HOP_SIZE
        }

        if (frameCount > 0) {
            for (k in avgSpectrum.indices) avgSpectrum[k] /= frameCount
        }
        noiseSpectrum = avgSpectrum
    }

    /**
     * Apply spectral subtraction to remove background noise from recorded audio.
     *
     * @param samples Raw recorded PCM 16-bit mono samples.
     * @return Noise-reduced samples (same length as input).
     */
    fun apply(samples: ShortArray): ShortArray {
        val profile = noiseSpectrum ?: return samples
        if (samples.size < FFT_SIZE) return samples

        val window = hannWindow(FFT_SIZE)
        val inputNorm = FloatArray(samples.size) { samples[it].toFloat() / Short.MAX_VALUE }
        val outputAccum = FloatArray(samples.size)
        val windowAccum = FloatArray(samples.size)

        var pos = 0
        while (pos + FFT_SIZE <= samples.size) {
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)

            // Analysis: windowed frame
            for (i in 0 until FFT_SIZE) {
                re[i] = inputNorm[pos + i] * window[i]
            }
            fft(re, im)

            // Spectral subtraction: reduce each frequency bin by the noise estimate
            for (k in 0..FFT_SIZE / 2) {
                val mag2 = re[k] * re[k] + im[k] * im[k]
                val noiseMag2 = if (k < profile.size) profile[k] else 0f
                // Wiener-inspired gain with spectral floor to avoid musical noise
                val cleanMag2 = max(mag2 - ALPHA * noiseMag2, BETA * noiseMag2)
                val gain = if (mag2 > 1e-10f) sqrt(cleanMag2 / mag2).toFloat() else 0f
                re[k] *= gain
                im[k] *= gain
            }

            // Restore Hermitian symmetry for real-valued IFFT
            for (k in 1 until FFT_SIZE / 2) {
                re[FFT_SIZE - k] = re[k]
                im[FFT_SIZE - k] = -im[k]
            }
            im[0] = 0f
            im[FFT_SIZE / 2] = 0f

            ifft(re, im)

            // Overlap-add synthesis (analysis window only — COLA condition for Hann 50%)
            for (i in 0 until FFT_SIZE) {
                if (pos + i < samples.size) {
                    outputAccum[pos + i] += re[i]
                    windowAccum[pos + i] += window[i]
                }
            }
            pos += HOP_SIZE
        }

        // Normalise by overlapping window sum and convert back to 16-bit
        val result = ShortArray(samples.size)
        for (i in result.indices) {
            val normalized = if (windowAccum[i] > 1e-6f) {
                outputAccum[i] / windowAccum[i]
            } else {
                inputNorm[i]  // Edge case: no window coverage, keep original
            }
            result[i] = (normalized * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return result
    }

    /** Measured noise floor in dBFS. Returns negative infinity if no profile was captured. */
    fun getMeasuredNoiseDb(): Float {
        return if (measuredNoiseRms > 0f) {
            20f * log10(measuredNoiseRms.coerceAtLeast(1e-5f))
        } else {
            Float.NEGATIVE_INFINITY
        }
    }

    /** Returns true if a noise profile has been captured and is ready to use. */
    fun hasProfile(): Boolean = noiseSpectrum != null

    /**
     * Returns true if the measured noise level is significant enough that
     * applying noise reduction would be beneficial (noise floor above −60 dBFS).
     */
    fun hasSignificantNoise(): Boolean = measuredNoiseRms > 0.001f

    // -------------------------------------------------------------------------
    // DSP internals
    // -------------------------------------------------------------------------

    private fun hannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat()
        }
    }

    /**
     * In-place Cooley-Tukey radix-2 FFT.
     * [re] and [im] must be the same power-of-2 length.
     */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        // Butterfly stages
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wBaseRe = cos(ang).toFloat()
            val wBaseIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val newCurRe = curRe * wBaseRe - curIm * wBaseIm
                    curIm = curRe * wBaseIm + curIm * wBaseRe
                    curRe = newCurRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * In-place IFFT using the conjugate trick: IFFT(x) = conj(FFT(conj(x))) / N.
     */
    private fun ifft(re: FloatArray, im: FloatArray) {
        val n = re.size
        for (i in im.indices) im[i] = -im[i]
        fft(re, im)
        val scale = 1f / n
        for (i in re.indices) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }
}
