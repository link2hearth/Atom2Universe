package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.*

/**
 * Phase Vocoder-based pitch shifter with phase-locking and transient preservation.
 *
 * This provides higher quality pitch shifting than WSOLA, especially for:
 * - Large pitch shifts (> 6 semitones)
 * - Polyphonic material
 * - Preserving attack transients
 *
 * Based on Laroche & Dolson (1999) phase-locking technique.
 */
class PitchShifterPV(
    private val sampleRate: Int = 44100,
    private val fftSize: Int = 2048,
    private val hopSize: Int = 512
) {
    companion object {
        private const val PI = Math.PI
        private const val TWO_PI = 2.0 * PI
    }

    // Precomputed window
    private val window = createHannWindow(fftSize)
    private val windowSum = window.sum()

    /**
     * Shift pitch by the specified number of semitones.
     * Positive = higher pitch, negative = lower pitch.
     * Duration is preserved.
     */
    fun shiftPitch(samples: ShortArray, semitones: Float): ShortArray {
        if (samples.isEmpty() || semitones == 0f) {
            return samples.copyOf()
        }

        // Convert to float
        val floatSamples = FloatArray(samples.size) { samples[it].toFloat() / Short.MAX_VALUE }

        // Pitch ratio
        val pitchRatio = 2.0.pow(semitones / 12.0)

        // Detect transients for preservation
        val transients = detectTransients(floatSamples)

        // Phase vocoder processing
        val stretched = phaseVocoderTimeStretch(floatSamples, pitchRatio, transients)

        // Resample to change pitch while restoring original duration
        val resampled = resample(stretched, pitchRatio)

        // Edge fades are handled by SamplePlayer.applyAntiClickFades() to avoid double fading
        // Convert back to ShortArray
        return floatToShort(resampled)
    }

    /**
     * Apply smooth fades at the edges to prevent clicks.
     * Uses longer fades to properly eliminate STFT edge artifacts.
     */
    private fun applyEdgeFades(samples: FloatArray): FloatArray {
        if (samples.size < 100) return samples

        val output = samples.copyOf()

        // Fade in: 5ms
        val fadeInSamples = (sampleRate * 0.005).toInt().coerceAtMost(samples.size / 8)
        for (i in 0 until fadeInSamples) {
            val progress = i.toFloat() / fadeInSamples
            // S-curve for smoothness
            val gain = ((1 - cos(progress * PI)) / 2).toFloat()
            output[i] *= gain
        }

        // Fade out: 15ms (longer to ensure clean ending)
        val fadeOutSamples = (sampleRate * 0.015).toInt().coerceAtMost(samples.size / 8)
        for (i in 0 until fadeOutSamples) {
            val index = samples.lastIndex - i
            // Progress from 0 (at end) to 1 (at start of fade)
            val progress = (fadeOutSamples - 1 - i).toFloat() / fadeOutSamples
            // S-curve: 1 at start of fade, 0 at end
            val gain = ((1 + cos((1 - progress) * PI)) / 2).toFloat()
            output[index] *= gain
        }

        // Force last samples to zero
        val zeroSamples = minOf(16, samples.size)
        for (i in 0 until zeroSamples) {
            output[samples.lastIndex - i] = 0f
        }

        return output
    }

    /**
     * Phase vocoder time-stretching with phase-locking.
     * Uses proper COLA (Constant Overlap-Add) normalization to prevent amplitude modulation.
     */
    private fun phaseVocoderTimeStretch(
        samples: FloatArray,
        stretchFactor: Double,
        transients: Set<Int>
    ): FloatArray {
        val numFrames = (samples.size - fftSize) / hopSize + 1
        if (numFrames <= 0) {
            return samples.copyOf()
        }

        // Output hop size (stretched)
        val outputHop = (hopSize * stretchFactor).roundToInt().coerceAtLeast(1)
        val outputLength = ((numFrames - 1) * outputHop + fftSize)
        val output = FloatArray(outputLength.toInt())

        // Use a separate normalization array for proper COLA
        val normalization = FloatArray(outputLength.toInt())

        // Calculate synthesis window gain for COLA
        // For Hann window, we need to compensate based on overlap ratio
        val overlapRatio = 1.0 - outputHop.toDouble() / fftSize
        val synthesisGain = when {
            overlapRatio >= 0.75 -> 0.5f   // 75%+ overlap
            overlapRatio >= 0.5 -> 0.667f  // 50-75% overlap
            else -> 1.0f                    // Less overlap
        }

        // Phase accumulator for each bin
        val phaseAccum = FloatArray(fftSize / 2 + 1)
        val prevPhase = FloatArray(fftSize / 2 + 1)
        val prevMagnitude = FloatArray(fftSize / 2 + 1)

        // Expected phase advance per hop
        val expectedPhaseAdvance = FloatArray(fftSize / 2 + 1) { bin ->
            (TWO_PI * bin * hopSize / fftSize).toFloat()
        }

        // Process each frame
        for (frameIdx in 0 until numFrames) {
            val inputPos = frameIdx * hopSize
            val outputPos = (frameIdx * outputHop).coerceAtMost(output.size - fftSize)

            // Check if this frame contains a transient
            val isTransient = transients.any { it in inputPos until (inputPos + fftSize) }

            // Extract and window the frame
            val frame = FloatArray(fftSize)
            for (i in 0 until fftSize) {
                val idx = inputPos + i
                frame[i] = if (idx < samples.size) samples[idx] * window[i] else 0f
            }

            // FFT
            val (real, imag) = fft(frame)

            // Convert to magnitude and phase
            val magnitude = FloatArray(fftSize / 2 + 1)
            val phase = FloatArray(fftSize / 2 + 1)

            for (bin in 0 until fftSize / 2 + 1) {
                magnitude[bin] = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                phase[bin] = atan2(imag[bin], real[bin])
            }

            // Phase processing
            if (frameIdx == 0 || isTransient) {
                // First frame or transient: use original phase
                for (bin in 0 until fftSize / 2 + 1) {
                    phaseAccum[bin] = phase[bin]
                }
            } else {
                // Phase-locked phase vocoder
                processPhaseWithLocking(
                    magnitude, phase, prevMagnitude, prevPhase,
                    phaseAccum, expectedPhaseAdvance, stretchFactor
                )
            }

            // Store for next frame
            for (bin in 0 until fftSize / 2 + 1) {
                prevPhase[bin] = phase[bin]
                prevMagnitude[bin] = magnitude[bin]
            }

            // Convert back to complex
            val newReal = FloatArray(fftSize)
            val newImag = FloatArray(fftSize)

            for (bin in 0 until fftSize / 2 + 1) {
                newReal[bin] = magnitude[bin] * cos(phaseAccum[bin])
                newImag[bin] = magnitude[bin] * sin(phaseAccum[bin])

                // Mirror for negative frequencies
                if (bin > 0 && bin < fftSize / 2) {
                    newReal[fftSize - bin] = newReal[bin]
                    newImag[fftSize - bin] = -newImag[bin]
                }
            }

            // IFFT
            val outputFrame = ifft(newReal, newImag)

            // Overlap-add with synthesis window and proper scaling
            for (i in 0 until fftSize) {
                val outIdx = outputPos + i
                if (outIdx < output.size) {
                    // Apply synthesis window and add
                    output[outIdx] += outputFrame[i] * window[i] * synthesisGain
                    normalization[outIdx] += window[i] * window[i] * synthesisGain
                }
            }
        }

        // Normalize only where needed to prevent modulation
        // Use a smooth normalization to avoid discontinuities
        for (i in output.indices) {
            val norm = normalization[i]
            if (norm > 0.1f) {
                // Smooth normalization: only correct where overlap is insufficient
                val correction = if (norm < 0.9f) 1.0f / norm else 1.0f
                output[i] *= correction.coerceIn(0.5f, 2.0f)
            }
        }

        // Apply envelope smoothing to reduce any remaining amplitude modulation
        return smoothEnvelope(output, outputHop)
    }

    /**
     * Smooth the output envelope to reduce tremolo/vibrato artifacts.
     * Uses a simple low-pass filter on the amplitude envelope.
     */
    private fun smoothEnvelope(samples: FloatArray, hopSize: Int): FloatArray {
        if (samples.size < hopSize * 2) return samples

        val output = samples.copyOf()
        val windowSize = hopSize / 2  // Smooth over half a hop

        // Calculate local RMS envelope
        val envelope = FloatArray(samples.size)
        for (i in samples.indices) {
            val start = (i - windowSize).coerceAtLeast(0)
            val end = (i + windowSize).coerceAtMost(samples.size - 1)
            var sumSquares = 0f
            for (j in start..end) {
                sumSquares += samples[j] * samples[j]
            }
            envelope[i] = sqrt(sumSquares / (end - start + 1))
        }

        // Smooth the envelope with a larger window
        val smoothedEnvelope = FloatArray(samples.size)
        val smoothWindow = hopSize
        for (i in samples.indices) {
            val start = (i - smoothWindow).coerceAtLeast(0)
            val end = (i + smoothWindow).coerceAtMost(samples.size - 1)
            var sum = 0f
            for (j in start..end) {
                sum += envelope[j]
            }
            smoothedEnvelope[i] = sum / (end - start + 1)
        }

        // Apply envelope correction (only reduce modulation, don't amplify)
        for (i in samples.indices) {
            if (envelope[i] > 0.0001f && smoothedEnvelope[i] > 0.0001f) {
                val ratio = smoothedEnvelope[i] / envelope[i]
                // Only apply correction if there's significant modulation
                if (ratio > 1.1f || ratio < 0.9f) {
                    // Gentle correction - don't over-correct
                    val correction = 1f + (ratio - 1f) * 0.5f
                    output[i] *= correction.coerceIn(0.8f, 1.2f)
                }
            }
        }

        return output
    }

    /**
     * Phase-locked phase processing (Laroche & Dolson method).
     * Locks phases to peaks in the spectrum for better coherence.
     */
    private fun processPhaseWithLocking(
        magnitude: FloatArray,
        phase: FloatArray,
        prevMagnitude: FloatArray,
        prevPhase: FloatArray,
        phaseAccum: FloatArray,
        expectedPhaseAdvance: FloatArray,
        stretchFactor: Double
    ) {
        val numBins = magnitude.size

        // Find spectral peaks
        val peaks = mutableListOf<Int>()
        for (bin in 1 until numBins - 1) {
            if (magnitude[bin] > magnitude[bin - 1] && magnitude[bin] > magnitude[bin + 1]) {
                peaks.add(bin)
            }
        }

        // Process phases with peak locking
        val processed = BooleanArray(numBins)

        for (peakBin in peaks) {
            // Calculate true frequency deviation for this peak
            val phaseDiff = phase[peakBin] - prevPhase[peakBin]
            val expectedDiff = expectedPhaseAdvance[peakBin]

            // Unwrap phase difference to [-PI, PI]
            val deltaPhase = principalArg(phaseDiff - expectedDiff)

            // True frequency deviation
            val trueFreqDev = deltaPhase / hopSize

            // New phase for stretched output
            val outputPhaseAdvance = expectedPhaseAdvance[peakBin] * stretchFactor.toFloat() +
                    trueFreqDev * hopSize * stretchFactor.toFloat()

            phaseAccum[peakBin] = phaseAccum[peakBin] + outputPhaseAdvance
            processed[peakBin] = true

            // Lock nearby bins to this peak (region of influence)
            val influence = 4 // bins on each side (increased for better coherence)
            for (offset in -influence..influence) {
                val bin = peakBin + offset
                if (bin in 0 until numBins && !processed[bin]) {
                    // Lock phase relative to peak
                    val phaseDelta = phase[bin] - phase[peakBin]
                    phaseAccum[bin] = phaseAccum[peakBin] + phaseDelta
                    processed[bin] = true
                }
            }
        }

        // Process remaining bins with standard phase vocoder
        for (bin in 0 until numBins) {
            if (!processed[bin]) {
                val phaseDiff = phase[bin] - prevPhase[bin]
                val expectedDiff = expectedPhaseAdvance[bin]

                // Unwrap phase difference to [-PI, PI]
                val deltaPhase = principalArg(phaseDiff - expectedDiff)

                val trueFreqDev = deltaPhase / hopSize
                val outputPhaseAdvance = expectedPhaseAdvance[bin] * stretchFactor.toFloat() +
                        trueFreqDev * hopSize * stretchFactor.toFloat()

                phaseAccum[bin] = phaseAccum[bin] + outputPhaseAdvance
            }
        }
    }

    /**
     * Wrap angle to [-PI, PI] range (principal argument).
     */
    private fun principalArg(phase: Float): Float {
        var p = phase.toDouble()
        while (p > PI) p -= TWO_PI
        while (p < -PI) p += TWO_PI
        return p.toFloat()
    }

    /**
     * Detect transients (attacks) in the audio using spectral flux.
     */
    private fun detectTransients(samples: FloatArray): Set<Int> {
        val transients = mutableSetOf<Int>()
        val threshold = 0.3f // Sensitivity threshold

        var prevEnergy = 0f
        val windowSize = hopSize

        for (pos in 0 until samples.size - windowSize step windowSize) {
            // Calculate high-frequency energy (emphasizes transients)
            var energy = 0f
            for (i in 0 until windowSize) {
                val sample = samples[pos + i]
                energy += sample * sample
            }
            energy /= windowSize

            // Spectral flux: detect sudden increases in energy
            val flux = (energy - prevEnergy).coerceAtLeast(0f)

            if (prevEnergy > 0 && flux / prevEnergy > threshold) {
                transients.add(pos)
            }

            prevEnergy = energy
        }

        return transients
    }

    /**
     * Resample using linear interpolation.
     */
    private fun resample(samples: FloatArray, ratio: Double): FloatArray {
        val outputLength = (samples.size / ratio).roundToInt()
        if (outputLength <= 0) return FloatArray(0)

        return FloatArray(outputLength) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, samples.lastIndex)
            val frac = (srcPos - idx0).toFloat()

            samples[idx0] * (1 - frac) + samples[idx1] * frac
        }
    }

    /**
     * Create Hann window.
     */
    private fun createHannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1 - cos(TWO_PI * i / (size - 1)))).toFloat()
        }
    }

    /**
     * FFT using Cooley-Tukey algorithm.
     */
    private fun fft(samples: FloatArray): Pair<FloatArray, FloatArray> {
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

        // Cooley-Tukey
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

        return Pair(real, imag)
    }

    /**
     * Inverse FFT using conjugate-FFT-conjugate method.
     */
    private fun ifft(real: FloatArray, imag: FloatArray): FloatArray {
        val n = real.size

        // Step 1: Conjugate the input (negate imaginary)
        val conjImag = FloatArray(n) { -imag[it] }

        // Step 2: Apply forward FFT to the conjugated input
        val (resultReal, resultImag) = fft(real.copyOf().also { r ->
            // Actually we need to do FFT on the complex conjugate
            // So we pass real as-is, and negated imag
        })

        // Proper IFFT: swap real/imag, do FFT, swap back, scale
        // Simpler approach: use the property that IFFT(X) = conj(FFT(conj(X))) / N
        val inputReal = real.copyOf()
        val inputImag = FloatArray(n) { -imag[it] }  // Conjugate input

        // FFT of conjugated input
        val tempReal = inputReal.copyOf()
        val tempImag = inputImag.copyOf()

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var t = tempReal[i]; tempReal[i] = tempReal[j]; tempReal[j] = t
                t = tempImag[i]; tempImag[i] = tempImag[j]; tempImag[j] = t
            }
            var k = n / 2
            while (k <= j) { j -= k; k /= 2 }
            j += k
        }

        // Cooley-Tukey FFT
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
                    val tr = wr * tempReal[j2] - wi * tempImag[j2]
                    val ti = wr * tempImag[j2] + wi * tempReal[j2]
                    tempReal[j2] = tempReal[i] - tr
                    tempImag[j2] = tempImag[i] - ti
                    tempReal[i] += tr
                    tempImag[i] += ti
                }
                val wtemp = wr
                wr = wr * wpr - wi * wpi
                wi = wi * wpr + wtemp * wpi
            }
            mmax = step
        }

        // Conjugate result and scale by 1/N
        val output = FloatArray(n)
        for (i in 0 until n) {
            // The real part of conj(FFT(conj(X))) / N is what we want
            output[i] = tempReal[i] / n
        }

        return output
    }

    /**
     * Convert float samples to ShortArray with normalization.
     */
    private fun floatToShort(samples: FloatArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        // Find peak for normalization
        var maxAbs = 0f
        for (sample in samples) {
            val abs = abs(sample)
            if (abs > maxAbs) maxAbs = abs
        }

        val normalizer = if (maxAbs > 1f) 1f / maxAbs else 1f

        return ShortArray(samples.size) { i ->
            (samples[i] * normalizer * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
