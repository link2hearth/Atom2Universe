package com.Atom2Universe.app.sf2creator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Simple, robust sample player designed for click-free playback.
 *
 * Key design principles:
 * 1. Single persistent AudioTrack (no creation/destruction)
 * 2. All audio processing done BEFORE writing to track
 * 3. Proper amplitude ramping to eliminate clicks
 * 4. Zero-crossing alignment at sample boundaries
 *
 * Based on Android AudioTrack best practices:
 * - https://developer.android.com/reference/android/media/AudioTrack
 * - Uses MODE_STATIC for low-latency short sample playback
 */
class SimpleSamplePlayer {

    companion object {
        private const val TAG = "SimpleSamplePlayer"
        private const val SAMPLE_RATE = 44100

        // Maximum buffer size (10 seconds at 44.1kHz mono 16-bit)
        private const val MAX_BUFFER_SAMPLES = SAMPLE_RATE * 10
        private const val MAX_BUFFER_BYTES = MAX_BUFFER_SAMPLES * 2

        // Fade parameters for click prevention (minimum 10ms recommended)
        private const val FADE_IN_MS = 10
        private const val FADE_OUT_MS = 15

        // Crossfade duration for loops
        private const val CROSSFADE_MS = 20
    }

    // AudioTrack (created on-demand for each playback)
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentBufferSize = 0

    // Current loaded sample (after processing)
    private var loadedSamples: ShortArray? = null

    // Lock for thread safety
    private val lock = Any()

    /**
     * Ensure AudioTrack is ready for the given sample size.
     * Always creates a fresh track to avoid buffer caching issues.
     */
    private fun ensureTrackReady(sampleCount: Int): Boolean {
        val requiredBytes = sampleCount * 2  // 16-bit samples

        // Always release and recreate to avoid cached audio data issues
        // MODE_STATIC keeps old data in buffer which causes echo/artifacts
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing old track", e)
        }
        audioTrack = null

        // Get minimum buffer size
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size from getMinBufferSize: $minBufferSize")
            return false
        }

        // Buffer size: exactly the required size (to avoid old data in buffer)
        // Must be at least minBufferSize for Android requirements
        val bufferSize = maxOf(requiredBytes, minBufferSize).coerceAtMost(MAX_BUFFER_BYTES)

        return try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // For MODE_STATIC, state can be STATE_INITIALIZED (1) or STATE_NO_STATIC_DATA (2)
            // Both are valid - STATE_NO_STATIC_DATA just means no data written yet
            val state = audioTrack?.state
            val initialized = state == AudioTrack.STATE_INITIALIZED || state == AudioTrack.STATE_NO_STATIC_DATA
            if (initialized) {
                currentBufferSize = bufferSize
                Log.d(TAG, "AudioTrack created successfully, state=$state")
            } else {
                Log.e(TAG, "AudioTrack failed, state=$state")
                audioTrack?.release()
                audioTrack = null
            }
            initialized
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioTrack: ${e.message}", e)
            audioTrack = null
            false
        }
    }

    /**
     * Play raw samples with automatic click prevention.
     * The samples will be processed (fades, zero-crossings) before playback.
     */
    fun play(samples: ShortArray) {
        if (samples.isEmpty()) {
            Log.w(TAG, "Empty samples array")
            return
        }

        // Limit to max buffer size
        val samplesToPlay = if (samples.size > MAX_BUFFER_SAMPLES) {
            Log.w(TAG, "Sample too long (${samples.size}), truncating to $MAX_BUFFER_SAMPLES")
            samples.copyOfRange(0, MAX_BUFFER_SAMPLES)
        } else {
            samples
        }

        // Process for click-free playback
        val processed = prepareForPlayback(samplesToPlay)

        playProcessed(processed)
    }

    /**
     * Play samples that have already been processed (no additional fades).
     */
    @Suppress("unused")
    fun playRaw(samples: ShortArray) {
        if (samples.isEmpty()) return

        val samplesToPlay = if (samples.size > MAX_BUFFER_SAMPLES) {
            samples.copyOfRange(0, MAX_BUFFER_SAMPLES)
        } else {
            samples
        }

        playProcessed(samplesToPlay)
    }

    /**
     * Internal play function.
     */
    private fun playProcessed(samples: ShortArray) {
        synchronized(lock) {
            // Ensure track is ready for this sample size
            if (!ensureTrackReady(samples.size)) {
                Log.e(TAG, "Failed to create AudioTrack for ${samples.size} samples")
                return
            }

            val track = audioTrack ?: return

            try {
                // Stop any current playback (only if actually playing)
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                isPlaying = false

                // Write the sample data
                val written = track.write(samples, 0, samples.size)
                if (written != samples.size) {
                    Log.w(TAG, "Only wrote $written of ${samples.size} samples")
                }

                // Set to play once (no loop)
                val setLoop = track.setLoopPoints(0, samples.size, 0)
                if (setLoop != AudioTrack.SUCCESS) {
                    Log.w(TAG, "setLoopPoints failed: $setLoop")
                }

                // Reload static data to ensure clean start
                val reload = track.reloadStaticData()
                if (reload != AudioTrack.SUCCESS) {
                    Log.w(TAG, "reloadStaticData failed: $reload")
                }

                // Start playback
                track.play()
                isPlaying = true
                loadedSamples = samples

                Log.d(TAG, "Playing ${samples.size} samples (${samples.size * 1000 / SAMPLE_RATE}ms)")

            } catch (e: Exception) {
                Log.e(TAG, "Error during playback: ${e.message}", e)
                isPlaying = false
            }
        }
    }

    /**
     * Stop playback immediately.
     */
    fun stop() {
        synchronized(lock) {
            try {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                    }
                }
                isPlaying = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping playback", e)
            }
        }
    }

    /**
     * Check if currently playing.
     */
    fun isPlaying(): Boolean {
        synchronized(lock) {
            return audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
        }
    }

    /**
     * Release all resources. Call when done with the player.
     */
    fun release() {
        synchronized(lock) {
            try {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
                audioTrack = null
                currentBufferSize = 0
                isPlaying = false
                loadedSamples = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
        }
    }

    /**
     * Prepare samples for click-free playback.
     *
     * Steps:
     * 1. Find same-direction zero crossings near start/end
     * 2. Apply smooth fade in/out with proper slope matching
     * 3. Ensure first and last samples are zero
     *
     * Based on DSP best practices:
     * - Zero crossings alone aren't enough - slopes must match
     * - Look for crossings in the same direction (negative to positive or vice versa)
     * - Minimum 10ms fades recommended
     */
    private fun prepareForPlayback(samples: ShortArray): ShortArray {
        if (samples.size < 100) return samples

        val output = samples.copyOf()

        // 1. Find a good starting point (zero crossing going positive)
        val searchWindow = (SAMPLE_RATE * 0.005).toInt() // 5ms search window
        val startOffset = findZeroCrossingPositive(output, 0, searchWindow)

        // 2. Find a good ending point (zero crossing going negative)
        val endOffset = findZeroCrossingNegative(output, output.size - searchWindow, output.size)

        // If we found good zero crossings, trim to them
        val trimmedStart = startOffset
        val trimmedEnd = if (endOffset > trimmedStart + 100) endOffset else output.size

        // 3. Apply smooth fade in (S-curve) - longer for better click prevention
        val fadeInSamples = (FADE_IN_MS * SAMPLE_RATE / 1000).coerceAtMost(output.size / 6)
        for (i in 0 until fadeInSamples) {
            val t = i.toFloat() / fadeInSamples
            // S-curve: smoother than linear, starts and ends with zero slope
            val gain = (1 - cos(t * PI)) / 2
            val idx = trimmedStart + i
            if (idx < output.size) {
                output[idx] = (output[idx] * gain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // 4. Apply smooth fade out (S-curve)
        val fadeOutSamples = (FADE_OUT_MS * SAMPLE_RATE / 1000).coerceAtMost(output.size / 6)
        val fadeOutStart = (trimmedEnd - fadeOutSamples).coerceAtLeast(trimmedStart + fadeInSamples)
        for (i in 0 until fadeOutSamples) {
            val t = i.toFloat() / fadeOutSamples
            // Inverse S-curve for fade out
            val gain = (1 + cos(t * PI)) / 2
            val idx = fadeOutStart + i
            if (idx < output.size) {
                output[idx] = (output[idx] * gain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // 5. Force first and last samples to exactly zero (prevents any residual click)
        val zeroSamples = 8
        for (i in 0 until zeroSamples.coerceAtMost(trimmedStart)) {
            output[i] = 0
        }
        for (i in 0 until zeroSamples.coerceAtMost(output.size - trimmedEnd)) {
            val idx = output.size - 1 - i
            if (idx >= 0) output[idx] = 0
        }

        return output
    }

    /**
     * Find a zero crossing where waveform goes from negative to positive.
     * Returns the index of the first positive sample after crossing.
     */
    @Suppress("SameParameterValue")
    private fun findZeroCrossingPositive(samples: ShortArray, start: Int, end: Int): Int {
        for (i in start until minOf(end - 1, samples.size - 1)) {
            val curr = samples[i].toInt()
            val next = samples[i + 1].toInt()
            // Looking for negative to positive transition
            if (curr <= 0 && next > 0) {
                return i + 1
            }
        }
        return start // No crossing found, return start
    }

    /**
     * Find a zero crossing where waveform goes from positive to negative.
     * Returns the index of the last positive sample before crossing.
     */
    @Suppress("SameParameterValue")
    private fun findZeroCrossingNegative(samples: ShortArray, start: Int, end: Int): Int {
        for (i in (end - 1) downTo maxOf(start, 1)) {
            val curr = samples[i].toInt()
            val prev = samples[i - 1].toInt()
            // Looking for positive to negative transition (going backwards)
            if (curr <= 0 && prev > 0) {
                return i
            }
        }
        return end // No crossing found, return end
    }

    /**
     * Prepare a looped sample for seamless playback.
     * Creates a version with crossfade for smooth loop transitions.
     *
     * @param samples The audio samples
     * @param loopStart Loop start point (in samples)
     * @param loopEnd Loop end point (in samples)
     * @param loopCount Number of times to repeat the loop
     * @return Processed samples ready for playback
     */
    fun prepareLoopedSample(
        samples: ShortArray,
        loopStart: Int,
        loopEnd: Int,
        loopCount: Int
    ): ShortArray {
        if (samples.isEmpty() || loopEnd <= loopStart) return samples

        val attackPortion = samples.copyOfRange(0, loopStart.coerceAtMost(samples.size))
        val loopPortion = samples.copyOfRange(
            loopStart.coerceAtLeast(0),
            loopEnd.coerceAtMost(samples.size)
        )

        if (loopPortion.size < 100) {
            Log.w(TAG, "Loop portion too short")
            return samples
        }

        // Calculate crossfade duration based on loop length
        val loopMs = loopPortion.size * 1000 / SAMPLE_RATE
        val crossfadeMs = when {
            loopMs < 30 -> 2
            loopMs < 80 -> 5
            loopMs < 200 -> 10
            else -> CROSSFADE_MS
        }
        val crossfadeSamples = (crossfadeMs * SAMPLE_RATE / 1000)
            .coerceIn(8, loopPortion.size / 4)

        // Build result with overlapping crossfades between loop copies
        // Each loop copy overlaps with the next by crossfadeSamples
        val effectiveLoopLength = loopPortion.size - crossfadeSamples
        // Account for crossfade overlap between attack and first loop
        val attackLoopOverlap = if (attackPortion.size >= crossfadeSamples) crossfadeSamples else 0
        val totalLength = attackPortion.size - attackLoopOverlap + loopPortion.size + effectiveLoopLength * (loopCount - 1)

        if (totalLength > MAX_BUFFER_SAMPLES) {
            val maxLoops = (MAX_BUFFER_SAMPLES - attackPortion.size - crossfadeSamples) / effectiveLoopLength
            return prepareLoopedSample(samples, loopStart, loopEnd, maxLoops.coerceAtLeast(1))
        }

        val result = ShortArray(totalLength)

        // Copy attack
        attackPortion.copyInto(result, 0)

        // Copy first loop with crossfade from attack
        var offset = attackPortion.size
        if (attackPortion.size >= crossfadeSamples && crossfadeSamples > 0) {
            // Overlap: move offset back to superpose attack end with loop start
            offset -= crossfadeSamples
            for (j in 0 until crossfadeSamples) {
                val t = j.toFloat() / crossfadeSamples
                val fadeOut = cos(t * PI / 2)
                val fadeIn = sin(t * PI / 2)
                val existingSample = result[offset + j].toInt()
                val newSample = loopPortion[j].toInt()
                result[offset + j] = ((existingSample * fadeOut) + (newSample * fadeIn))
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            // Copy rest of first loop (after crossfade region)
            for (j in crossfadeSamples until loopPortion.size) {
                result[offset + j] = loopPortion[j]
            }
            offset += loopPortion.size - crossfadeSamples
        } else {
            // No attack or crossfade too long: copy loop directly
            loopPortion.copyInto(result, offset)
            offset += loopPortion.size - crossfadeSamples
        }

        // Copy remaining loops with crossfade overlap
        val remainingLoops = (loopCount - 1).coerceAtLeast(0)
        repeat(remainingLoops) {
            // Crossfade: blend end of previous with start of new loop
            for (j in 0 until crossfadeSamples) {
                val t = j.toFloat() / crossfadeSamples
                val fadeOut = cos(t * PI / 2)  // Previous loop fading out
                val fadeIn = sin(t * PI / 2)   // New loop fading in

                val existingSample = result[offset + j].toInt()
                val newSample = loopPortion[j].toInt()
                result[offset + j] = ((existingSample * fadeOut) + (newSample * fadeIn))
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            // Copy rest of loop (after crossfade region)
            for (j in crossfadeSamples until loopPortion.size) {
                result[offset + j] = loopPortion[j]
            }

            offset += effectiveLoopLength
        }

        return result
    }

    /**
     * Apply minimal fade for very short loops - just smooths the boundaries.
     * Uses 4-8 samples to avoid clicks without affecting the sound.
     */
    @Suppress("unused")
    private fun applyMicroFade(loop: ShortArray): ShortArray {
        if (loop.size < 16) return loop

        val result = loop.copyOf()
        val fadeSamples = 4

        // Tiny fade at start
        for (i in 0 until fadeSamples) {
            val gain = i.toFloat() / fadeSamples
            result[i] = (result[i] * gain).toInt().toShort()
        }

        // Tiny fade at end
        for (i in 0 until fadeSamples) {
            val gain = (fadeSamples - i).toFloat() / fadeSamples
            val idx = loop.size - fadeSamples + i
            result[idx] = (result[idx] * gain).toInt().toShort()
        }

        return result
    }

    /**
     * Apply crossfade to a loop section for seamless repetition.
     * Uses S-curve (equal-power) crossfade for smoother transitions.
     *
     * Based on DSP best practices:
     * - Linear crossfade can cause volume dip at the center
     * - S-curve (cosine) crossfade maintains perceived volume
     * - Longer crossfade = smoother but may affect loop character
     */
    @Suppress("unused")
    private fun applyCrossfade(loop: ShortArray, crossfadeSamples: Int): ShortArray {
        if (loop.size < crossfadeSamples * 2) return loop

        val result = loop.copyOf()
        val cfSamples = crossfadeSamples.coerceAtMost(loop.size / 4)

        // Equal-power crossfade using cosine curves
        for (i in 0 until cfSamples) {
            val t = i.toFloat() / cfSamples

            // S-curve (equal power): sqrt of cosine crossfade
            // This maintains perceived volume during the transition
            val fadeOut = cos(t * PI / 2)  // 1 -> 0
            val fadeIn = sin(t * PI / 2)   // 0 -> 1

            val endIndex = loop.size - cfSamples + i
            val startSample = loop[i].toInt()
            val endSample = loop[endIndex].toInt()

            // Blend at the end of the loop (which connects back to the start)
            result[endIndex] = ((endSample * fadeOut) + (startSample * fadeIn)).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return result
    }

    /**
     * Get the sample rate used by this player.
     */
    @Suppress("unused")
    fun getSampleRate(): Int = SAMPLE_RATE
}
