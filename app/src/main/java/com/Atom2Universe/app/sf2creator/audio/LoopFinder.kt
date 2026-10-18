package com.Atom2Universe.app.sf2creator.audio

import kotlin.math.sqrt

/**
 * Automatic loop point detection for sustained samples.
 *
 * Finds optimal loop points by:
 * 1. Identifying the sustain region (after attack, before release)
 * 2. Finding zero-crossings with similar waveform shapes
 * 3. Using cross-correlation to find best matches
 */
class LoopFinder(private val sampleRate: Int = 44100) {

    data class LoopPoint(
        val start: Int,           // Loop start sample index
        val end: Int,             // Loop end sample index
        val quality: Float,       // 0-1, higher = better match
        val lengthMs: Int         // Loop length in milliseconds
    ) {
        val lengthSamples: Int get() = end - start
    }

    data class LoopSearchParams(
        val minLoopMs: Int = 50,          // Minimum loop duration
        val maxLoopMs: Int = 2000,        // Maximum loop duration
        val attackIgnoreMs: Int = 50,     // Ignore this much at start (attack)
        val releaseIgnoreMs: Int = 100,   // Ignore this much at end (release)
        val qualityThreshold: Float = 0.8f // Minimum quality to accept
    )

    /**
     * Find the best loop points in a sample.
     *
     * @param samples Audio samples
     * @param params Search parameters
     * @return Best loop point found, or null if no good loop exists
     */
    fun findBestLoop(
        samples: ShortArray,
        params: LoopSearchParams = LoopSearchParams()
    ): LoopPoint? {
        if (samples.isEmpty()) return null

        val minLoopSamples = (params.minLoopMs * sampleRate / 1000)
        val maxLoopSamples = (params.maxLoopMs * sampleRate / 1000)
        val attackSamples = (params.attackIgnoreMs * sampleRate / 1000)
        val releaseSamples = (params.releaseIgnoreMs * sampleRate / 1000)

        // Define search region (sustain portion)
        val searchStart = attackSamples.coerceAtMost(samples.size / 4)
        val searchEnd = (samples.size - releaseSamples).coerceAtLeast(searchStart + minLoopSamples)

        if (searchEnd <= searchStart + minLoopSamples) {
            // Sample too short for looping
            return null
        }

        // Find zero-crossings in the search region
        val zeroCrossings = findZeroCrossings(samples, searchStart, searchEnd)

        if (zeroCrossings.size < 2) return null

        var bestLoop: LoopPoint? = null
        var bestQuality = params.qualityThreshold

        // Try different loop start/end combinations
        for (startIdx in 0 until (zeroCrossings.size - 1).coerceAtMost(50)) {
            val loopStart = zeroCrossings[startIdx]

            for (endIdx in (startIdx + 1) until zeroCrossings.size.coerceAtMost(startIdx + 100)) {
                val loopEnd = zeroCrossings[endIdx]
                val loopLength = loopEnd - loopStart

                // Check loop length constraints
                if (loopLength < minLoopSamples || loopLength > maxLoopSamples) continue

                // Evaluate loop quality using cross-correlation
                val quality = evaluateLoopQuality(samples, loopStart, loopEnd)

                if (quality > bestQuality) {
                    bestQuality = quality
                    bestLoop = LoopPoint(
                        start = loopStart,
                        end = loopEnd,
                        quality = quality,
                        lengthMs = (loopLength * 1000 / sampleRate)
                    )
                }
            }
        }

        return bestLoop
    }

    /**
     * Find multiple candidate loop points, sorted by quality.
     */
    fun findLoopCandidates(
        samples: ShortArray,
        params: LoopSearchParams = LoopSearchParams(),
        maxCandidates: Int = 5
    ): List<LoopPoint> {
        if (samples.isEmpty()) return emptyList()

        val minLoopSamples = (params.minLoopMs * sampleRate / 1000)
        val maxLoopSamples = (params.maxLoopMs * sampleRate / 1000)
        val attackSamples = (params.attackIgnoreMs * sampleRate / 1000)
        val releaseSamples = (params.releaseIgnoreMs * sampleRate / 1000)

        val searchStart = attackSamples.coerceAtMost(samples.size / 4)
        val searchEnd = (samples.size - releaseSamples).coerceAtLeast(searchStart + minLoopSamples)

        if (searchEnd <= searchStart + minLoopSamples) return emptyList()

        val zeroCrossings = findZeroCrossings(samples, searchStart, searchEnd)
        val candidates = mutableListOf<LoopPoint>()

        for (startIdx in 0 until (zeroCrossings.size - 1).coerceAtMost(30)) {
            val loopStart = zeroCrossings[startIdx]

            for (endIdx in (startIdx + 1) until zeroCrossings.size.coerceAtMost(startIdx + 50)) {
                val loopEnd = zeroCrossings[endIdx]
                val loopLength = loopEnd - loopStart

                if (loopLength < minLoopSamples || loopLength > maxLoopSamples) continue

                val quality = evaluateLoopQuality(samples, loopStart, loopEnd)

                if (quality > params.qualityThreshold) {
                    candidates.add(
                        LoopPoint(
                            start = loopStart,
                            end = loopEnd,
                            quality = quality,
                            lengthMs = (loopLength * 1000 / sampleRate)
                        )
                    )
                }
            }
        }

        // Sort by quality and return top candidates
        return candidates
            .sortedByDescending { it.quality }
            .take(maxCandidates)
    }

    /**
     * Find zero-crossing points (where waveform crosses zero).
     * These are ideal loop points as they minimize clicks.
     */
    private fun findZeroCrossings(
        samples: ShortArray,
        startIndex: Int,
        endIndex: Int
    ): List<Int> {
        val crossings = mutableListOf<Int>()

        for (i in startIndex until endIndex - 1) {
            val current = samples[i].toInt()
            val next = samples[i + 1].toInt()

            // Detect positive-going zero crossing (more stable for loops)
            if (current <= 0 && next > 0) {
                crossings.add(i)
            }
        }

        return crossings
    }

    /**
     * Evaluate loop quality using normalized cross-correlation.
     * Compares the waveform around the loop start with the loop end.
     */
    private fun evaluateLoopQuality(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int
    ): Float {
        // Compare waveforms around the junction point
        val compareWindow = 256.coerceAtMost((loopEnd - loopStart) / 4)

        if (loopStart + compareWindow > samples.size || loopEnd + compareWindow > samples.size) {
            return 0f
        }

        var correlation = 0.0
        var energy1 = 0.0
        var energy2 = 0.0

        // Compare samples before loop end with samples after loop start
        for (i in 0 until compareWindow) {
            val s1 = samples[loopEnd - compareWindow + i].toDouble()
            val s2 = samples[loopStart + i].toDouble()

            correlation += s1 * s2
            energy1 += s1 * s1
            energy2 += s2 * s2
        }

        val normalizer = sqrt(energy1 * energy2)
        if (normalizer <= 0) return 0f

        val normalizedCorrelation = (correlation / normalizer).toFloat()

        // Also check for spectral similarity (optional, more expensive)
        val spectralMatch = evaluateSpectralSimilarity(samples, loopStart, loopEnd, compareWindow)

        // Combine metrics (correlation is more important)
        return normalizedCorrelation * 0.7f + spectralMatch * 0.3f
    }

    /**
     * Evaluate spectral similarity between loop points.
     * Helps ensure tonal consistency across the loop.
     */
    private fun evaluateSpectralSimilarity(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int,
        windowSize: Int
    ): Float {
        // Simple energy-based comparison (proper spectral analysis would use FFT)
        val bands = 4
        val bandSize = windowSize / bands

        var similarity = 0f

        for (band in 0 until bands) {
            var energy1 = 0f
            var energy2 = 0f

            for (i in 0 until bandSize) {
                val idx = band * bandSize + i

                val s1Idx = loopStart + idx
                val s2Idx = loopEnd - windowSize + idx

                if (s1Idx < samples.size && s2Idx >= 0 && s2Idx < samples.size) {
                    val s1 = samples[s1Idx].toFloat()
                    val s2 = samples[s2Idx].toFloat()
                    energy1 += s1 * s1
                    energy2 += s2 * s2
                }
            }

            // Compare band energies
            val maxEnergy = maxOf(energy1, energy2)
            val minEnergy = minOf(energy1, energy2)

            if (maxEnergy > 0) {
                similarity += minEnergy / maxEnergy
            }
        }

        return similarity / bands
    }

    /**
     * Check if a sample is suitable for looping.
     * Sustained sounds (pads, organs, strings) are good candidates.
     * Percussive sounds (piano, drums) are not.
     */
    fun isSuitableForLooping(samples: ShortArray): Boolean {
        if (samples.size < sampleRate / 2) return false // Too short (< 0.5s)

        // Analyze the sustain portion
        val attackEnd = (samples.size * 0.2).toInt()
        val releaseStart = (samples.size * 0.8).toInt()

        if (releaseStart <= attackEnd) return false

        // Check if energy is relatively constant in the sustain region
        val windowSize = sampleRate / 20 // 50ms windows
        val energies = mutableListOf<Float>()

        for (i in attackEnd until releaseStart - windowSize step windowSize) {
            var energy = 0f
            for (j in 0 until windowSize) {
                val sample = samples[i + j].toFloat() / Short.MAX_VALUE
                energy += sample * sample
            }
            energies.add(energy / windowSize)
        }

        if (energies.size < 3) return false

        // Calculate variance in energy
        val meanEnergy = energies.average().toFloat()
        if (meanEnergy < 0.001f) return false // Too quiet

        var variance = 0f
        for (e in energies) {
            val diff = e - meanEnergy
            variance += diff * diff
        }
        variance /= energies.size

        // Coefficient of variation - lower = more stable = better for looping
        val cv = sqrt(variance) / meanEnergy

        return cv < 0.5f // Accept if variation is less than 50%
    }

    /**
     * Get loop info for display.
     */
    fun describeLoop(loop: LoopPoint): String {
        val qualityDesc = when {
            loop.quality >= 0.95f -> "Excellent"
            loop.quality >= 0.9f -> "Very Good"
            loop.quality >= 0.8f -> "Good"
            loop.quality >= 0.7f -> "Acceptable"
            else -> "Poor"
        }
        return "Loop: ${loop.lengthMs}ms (${loop.lengthSamples} samples), Quality: $qualityDesc (${(loop.quality * 100).toInt()}%)"
    }
}
