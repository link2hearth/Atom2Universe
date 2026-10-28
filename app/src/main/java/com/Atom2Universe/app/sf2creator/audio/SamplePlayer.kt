package com.Atom2Universe.app.sf2creator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Sample player for previewing a recorded sample at its own pitch.
 *
 * Features:
 * - Full audio processing pipeline: noise gate, normalization, trim, fades, loop detection
 * - The cleaned-up playback buffer is computed once and reused
 */
class SamplePlayer {

    companion object {
        private const val TAG = "SamplePlayer"
        private const val SAMPLE_RATE = 44100
        private const val MAX_VOICES = 4  // Polyphony: max simultaneous notes
    }

    // Fade applied to the playback buffer to eliminate clicks
    // Increased values for smoother transitions on short/loud samples
    private val postPitchFadeInMs = 8
    private val postPitchFadeOutMs = 25

    /**
     * Voice class for polyphonic playback.
     * Each voice can play one note at a time.
     */
    private class Voice {
        var audioTrack: AudioTrack? = null
        var ownsTrack: Boolean = false  // true for STREAM (owns its track), false for STATIC (shared track)
        var playbackJob: Job? = null
        var startTime: Long = 0
        var isPlaying: Boolean = false
        var note: Int = -1

        /**
         * Stop playback gracefully (used when stealing a voice).
         * Only releases the AudioTrack if this voice owns it (STREAM mode).
         * Shared tracks (STATIC mode) are managed by playAudioStatic.
         */
        fun stopGracefully() {
            isPlaying = false
            playbackJob?.cancel()
            playbackJob = null

            try {
                val track = audioTrack
                audioTrack = null
                if (track != null && ownsTrack) {
                    // Only release for STREAM tracks (voice owns the track)
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.flush()
                    track.release()
                }
            } catch (_: Exception) {
                // Ignore errors during stop
            }
            ownsTrack = false
            note = -1
        }

        /**
         * Release all resources (final cleanup).
         */
        fun release() {
            isPlaying = false
            playbackJob?.cancel()
            playbackJob = null
            try {
                val track = audioTrack
                audioTrack = null
                if (track != null && ownsTrack) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.flush()
                    track.release()
                }
            } catch (_: Exception) {
                // Ignore errors during release
            }
            ownsTrack = false
            note = -1
        }
    }

    // Voice pool for polyphony
    private val voices = Array(MAX_VOICES) { Voice() }
    private var playbackJob: Job? = null
    private var rawSamples: ShortArray? = null       // Original samples
    private var processedSamples: ShortArray? = null // Preprocessed samples
    private var processedResult: AudioProcessor.ProcessedResult? = null // Full processing result
    private var rootNote: Int = 60 // Default C4
    private val scope = CoroutineScope(Dispatchers.Default)

    // Single reusable AudioTrack to avoid creation/destruction overhead
    private var reusableTrack: AudioTrack? = null
    private var reusableTrackSize: Int = 0
    // Frames of the static buffer that may hold sound from an earlier playback (beyond: silence)
    private var reusableTrackDirtyFrames: Int = 0
    private val trackLock = Any()

    // Reusable silence buffer for clearing STATIC AudioTrack buffer between playbacks
    private val silenceChunk = ShortArray(4096)

    // Audio processors
    private val audioProcessor = AudioProcessor(SAMPLE_RATE)
    private val deClicker = DeClicker(SAMPLE_RATE)

    // Settings
    private var processingOptions = AudioProcessor.ProcessingOptions()

    // Cleaned-up playback buffer, computed on first play (null when the sample changes)
    @Volatile
    private var playbackCache: ShortArray? = null

    /**
     * Set processing options.
     */
    @Suppress("unused")
    fun setProcessingOptions(options: AudioProcessor.ProcessingOptions) {
        processingOptions = options
        // Reprocess if samples are loaded
        rawSamples?.let { preprocess(it) }
    }

    /**
     * Get the current processing options.
     */
    @Suppress("unused")
    fun getProcessingOptions(): AudioProcessor.ProcessingOptions = processingOptions

    /**
     * Auto-detect and apply optimal processing options based on sample characteristics.
     */
    @Suppress("unused")
    fun autoDetectProcessingOptions() {
        rawSamples?.let { samples ->
            processingOptions = audioProcessor.analyzeAndSuggest(samples)
            preprocess(samples)
            playbackCache = null
        }
    }

    /**
     * Apply percussive processing (no loops, fast response).
     */
    @Suppress("unused")
    fun applyPercussiveProcessing() {
        processingOptions = AudioProcessor.ProcessingOptions(
            applyNoiseGate = true,
            autoDetectNoiseGate = true,
            trimSilence = true,
            silenceThresholdDb = -45f,
            normalize = true,
            fadeInMs = 2,
            fadeOutMs = 10,
            detectLoop = false
        )
        rawSamples?.let {
            preprocess(it)
            playbackCache = null
        }
    }

    /**
     * Apply sustained processing (with loop detection).
     */
    @Suppress("unused")
    fun applySustainedProcessing() {
        processingOptions = AudioProcessor.ProcessingOptions(
            applyNoiseGate = true,
            autoDetectNoiseGate = true,
            trimSilence = true,
            silenceThresholdDb = -40f,
            normalize = true,
            fadeInMs = 5,
            fadeOutMs = 20,
            detectLoop = true,
            minLoopMs = 100,
            maxLoopMs = 2000,
            loopQualityThreshold = 0.75f,
            crossfadeLoopMs = 25
        )
        rawSamples?.let {
            preprocess(it)
            playbackCache = null
        }
    }

    /**
     * Load a sample from a WAV file.
     */
    @Suppress("unused")
    fun loadSample(wavFile: File): Boolean {
        try {
            val samples = loadWavFile(wavFile)
            if (samples.isEmpty()) {
                Log.e(TAG, "Failed to load WAV file or file is empty")
                return false
            }
            loadSample(samples)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sample", e)
            return false
        }
    }

    /**
     * Load a sample from a ShortArray directly.
     */
    fun loadSample(samples: ShortArray) {
        rawSamples = samples.copyOf()
        playbackCache = null
        preprocess(samples)
    }

    /**
     * Preprocess the loaded sample using the full audio processing pipeline.
     */
    private fun preprocess(samples: ShortArray) {
        // Use the AudioProcessor for full pipeline processing
        val result = audioProcessor.process(samples, processingOptions)

        processedSamples = result.samples
        processedResult = result

        Log.d(TAG, "Processed sample: ${samples.size} -> ${result.samples.size} samples")
        Log.d(TAG, result.processingInfo)

        if (result.hasLoop) {
            Log.d(TAG, "Loop detected: ${result.loopStart} - ${result.loopEnd} (quality: ${(result.loopQuality * 100).toInt()}%)")
        }
    }

    /**
     * Set the root note (the original pitch of the sample).
     */
    fun setRootNote(note: Int) {
        if (rootNote != note) {
            rootNote = note.coerceIn(0, 127)
            playbackCache = null
        }
    }

    /**
     * Get the current root note.
     */
    @Suppress("unused")
    fun getRootNote(): Int = rootNote

    /**
     * Play the sample at its original pitch.
     * The cleaned-up buffer (DC offset, soft clipping, anti-click fades) is computed once.
     */
    fun play() {
        val samples = processedSamples ?: rawSamples
        if (samples == null || samples.isEmpty()) {
            Log.w(TAG, "No sample loaded")
            return
        }

        // Don't stop - polyphony handles multiple voices
        playbackJob = scope.launch {
            try {
                val data = playbackCache ?: run {
                    var cleaned = deClicker.removeDCOffset(samples.copyOf())
                    cleaned = deClicker.applySoftClipping(cleaned, threshold = 0.95f)
                    applyAntiClickFades(cleaned).also { playbackCache = it }
                }
                withContext(Dispatchers.Main) {
                    playAudio(data, rootNote)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sample", e)
            }
        }
    }

    /**
     * Apply short fades to eliminate clicks at start/end.
     * Uses smooth curves to avoid audible artifacts.
     */
    private fun applyAntiClickFades(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples

        val output = samples.copyOf()
        val fadeInSamples = (postPitchFadeInMs * SAMPLE_RATE / 1000).coerceAtMost(samples.size / 4)
        val fadeOutSamples = (postPitchFadeOutMs * SAMPLE_RATE / 1000).coerceAtMost(samples.size / 4)

        // Fade in with S-curve for smooth attack
        // First sample starts at 0, last fade-in sample reaches ~1
        for (i in 0 until fadeInSamples) {
            val progress = i.toFloat() / fadeInSamples
            // S-curve: smoother than linear (0 -> 1)
            val gain = (1 - kotlin.math.cos(progress * kotlin.math.PI)) / 2
            output[i] = (output[i] * gain).toInt().toShort()
        }

        // Fade out: from full volume down to zero at the very end
        // fadeStart is where we begin fading, samples.lastIndex is where we hit zero
        val fadeOutStart = samples.size - fadeOutSamples
        for (i in 0 until fadeOutSamples) {
            val index = fadeOutStart + i
            // progress goes 0 -> 1 as we approach the end
            val progress = i.toFloat() / fadeOutSamples
            // gain goes 1 -> 0 with S-curve for smooth release
            val gain = (1 + kotlin.math.cos(progress * kotlin.math.PI)) / 2
            output[index] = (output[index] * gain).toInt().toShort()
        }

        // Force last few samples to absolute zero to prevent any residual click
        val zeroSamples = minOf(8, samples.size)
        for (i in 0 until zeroSamples) {
            output[samples.lastIndex - i] = 0
        }

        return output
    }

    /**
     * Get the preprocessed samples (for export or further processing).
     */
    @Suppress("unused")
    fun getProcessedSamples(): ShortArray? = processedSamples?.copyOf()

    /**
     * Get the raw (unprocessed) samples.
     */
    @Suppress("unused")
    fun getRawSamples(): ShortArray? = rawSamples?.copyOf()

    /**
     * Stop all voice playback.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null

        // Stop all voices gracefully
        for (voice in voices) {
            voice.stopGracefully()
        }

        // Also stop the shared reusable track (use stop(), not pause(), for STATIC)
        synchronized(trackLock) {
            reusableTrack?.let { track ->
                try {
                    if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        track.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping reusable track", e)
                }
            }
        }
    }

    /**
     * Soft stop - stops audio playback without cancelling processing jobs.
     * Use this when you want to stop sound but preserve loaded samples.
     */
    @Suppress("unused")
    fun stopPlayback() {
        // Stop all voices without cancelling jobs
        for (voice in voices) {
            voice.isPlaying = false
            try {
                voice.audioTrack?.let { track ->
                    if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        track.stop()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice playback", e)
            }
        }

        // Also stop the reusable track (use stop(), not pause(), for STATIC)
        synchronized(trackLock) {
            reusableTrack?.let { track ->
                try {
                    if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        track.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping reusable track", e)
                }
            }
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        // Cancel the scope to stop all coroutines
        scope.cancel()
        playbackJob?.cancel()
        playbackJob = null

        // Release all voices
        for (voice in voices) {
            voice.release()
        }

        // Release reusable track
        synchronized(trackLock) {
            reusableTrack?.release()
            reusableTrack = null
            reusableTrackSize = 0
            reusableTrackDirtyFrames = 0
        }

        rawSamples = null
        processedSamples = null
        playbackCache = null
    }

    /**
     * Play audio data through AudioTrack.
     * Uses MODE_STATIC for short samples (cleaner) and MODE_STREAM for longer ones.
     * @param samples Audio samples to play
     * @param note MIDI note number for tracking
     */
    private fun playAudio(samples: ShortArray, note: Int = -1) {
        if (samples.isEmpty()) return

        // Find an available voice
        val voice = findAvailableVoice()

        // Stop previous playback on this voice gracefully
        voice.stopGracefully()

        // For short samples (< 2 seconds), use MODE_STATIC (cleaner, no buffer issues)
        // For longer samples, use MODE_STREAM
        val useStaticMode = samples.size < SAMPLE_RATE * 2

        if (useStaticMode) {
            playAudioStatic(voice, samples, note)
        } else {
            playAudioStream(voice, samples, note)
        }

        Log.d(TAG, "Playing on voice ${voices.indexOf(voice)}, note $note, mode=${if (useStaticMode) "STATIC" else "STREAM"}")
    }

    /**
     * Play audio using a reusable AudioTrack to avoid creation/destruction clicks.
     */
    private fun playAudioStatic(voice: Voice, samples: ShortArray, note: Int) {
        voice.playbackJob = scope.launch {
            try {
                synchronized(trackLock) {
                    // Stop current playback - must use stop() (not pause()) for proper
                    // STATIC track reuse: reloadStaticData() requires STOPPED state
                    // to reset the buffer write position back to 0.
                    // Note: flush() is a no-op for MODE_STATIC.
                    reusableTrack?.let { track ->
                        try {
                            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                                track.stop()
                            }
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }

                    // Check if we need to create or resize the track
                    val requiredSize = samples.size * 2
                    val needNewTrack = reusableTrack == null ||
                                       reusableTrackSize < requiredSize ||
                                       reusableTrack?.state != AudioTrack.STATE_INITIALIZED

                    if (needNewTrack) {
                        reusableTrack?.release()

                        // Create buffer large enough for most samples (5 seconds)
                        val bufferSize = maxOf(requiredSize, SAMPLE_RATE * 5 * 2)

                        reusableTrack = AudioTrack.Builder()
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

                        reusableTrackSize = bufferSize
                        reusableTrackDirtyFrames = bufferSize / 2
                        Log.d(TAG, "Created new reusable AudioTrack with buffer size: $bufferSize")
                    }

                    val track = reusableTrack ?: return@launch

                    // Reset buffer write position to 0 for STATIC track reuse
                    // (requires STOPPED state, which we ensured above)
                    try {
                        track.reloadStaticData()
                    } catch (_: Exception) {
                        // May fail on first use (no data written yet), that's OK
                    }

                    // Don't assign shared track to voice - voice doesn't own it
                    // voice.ownsTrack stays false (default)
                    voice.startTime = System.currentTimeMillis()
                    voice.isPlaying = true
                    voice.note = note

                    // Write new samples from position 0
                    track.write(samples, 0, samples.size)

                    // Zero-fill rest of buffer to prevent old data from causing clicks.
                    // With setLoopPoints(0, N, 0), loopCount=0 DISABLES looping and the
                    // track plays the ENTIRE buffer. Old data beyond our samples would
                    // create an audible click at the boundary. Only the part an earlier
                    // playback wrote needs it: the rest is still silence.
                    var remainingFrames = reusableTrackDirtyFrames - samples.size
                    reusableTrackDirtyFrames = samples.size
                    while (remainingFrames > 0) {
                        val toWrite = minOf(remainingFrames, silenceChunk.size)
                        track.write(silenceChunk, 0, toWrite)
                        remainingFrames -= toWrite
                    }

                    // Reload to sync hardware with the new buffer content, then play
                    track.reloadStaticData()
                    track.play()
                }

                // Wait for sample playback to complete
                val durationMs = samples.size * 1000L / SAMPLE_RATE
                kotlinx.coroutines.delay(durationMs + 50)

                // Stop playback (track is playing silence at this point, so no click)
                synchronized(trackLock) {
                    reusableTrack?.let { track ->
                        try {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.stop()
                            }
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in static audio playback", e)
            } finally {
                voice.isPlaying = false
            }
        }
    }

    /**
     * Play audio using MODE_STREAM - best for longer samples.
     */
    private fun playAudioStream(voice: Voice, samples: ShortArray, note: Int) {
        voice.playbackJob = scope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val bufferSize = maxOf(minBufferSize * 2, 4096)

                val track = AudioTrack.Builder()
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
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                voice.audioTrack = track
                voice.ownsTrack = true  // STREAM voice owns its track
                voice.startTime = System.currentTimeMillis()
                voice.isPlaying = true
                voice.note = note

                track.play()

                // Stream data in chunks
                val chunkSize = bufferSize / 4
                var offset = 0

                while (offset < samples.size && voice.isPlaying) {
                    val remaining = samples.size - offset
                    val toWrite = minOf(chunkSize, remaining)

                    val written = track.write(samples, offset, toWrite)
                    if (written > 0) {
                        offset += written
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }

                    if (offset < samples.size) {
                        kotlinx.coroutines.yield()
                    }
                }

                // Wait for playback to finish
                if (voice.isPlaying) {
                    val totalDurationMs = samples.size * 1000L / SAMPLE_RATE
                    val elapsedMs = System.currentTimeMillis() - voice.startTime
                    val remainingMs = (totalDurationMs - elapsedMs).coerceAtLeast(100)
                    kotlinx.coroutines.delay(remainingMs)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in stream audio playback", e)
            } finally {
                voice.isPlaying = false
            }
        }
    }

    /**
     * Find an available voice or steal the oldest one.
     * Prioritizes: free voices > finished voices > oldest playing voice
     */
    private fun findAvailableVoice(): Voice {
        // First pass: find a completely free voice (no job running)
        for (voice in voices) {
            if (voice.playbackJob == null || !voice.isPlaying) {
                return voice
            }
        }

        // Second pass: find a voice that has finished (job completed)
        for (voice in voices) {
            if (voice.playbackJob?.isCompleted == true) {
                return voice
            }
        }

        // Third pass: steal the oldest voice
        var oldestVoice = voices[0]
        var oldestTime = Long.MAX_VALUE

        for (voice in voices) {
            if (voice.startTime < oldestTime) {
                oldestTime = voice.startTime
                oldestVoice = voice
            }
        }

        Log.d(TAG, "Voice stealing: taking voice with note ${oldestVoice.note}")
        return oldestVoice
    }

    /**
     * Load PCM samples from a WAV file.
     */
    private fun loadWavFile(wavFile: File): ShortArray {
        if (!wavFile.exists()) return ShortArray(0)

        try {
            FileInputStream(wavFile).use { fis ->
                val header = ByteArray(44)
                val read = fis.read(header)
                if (read < 44) {
                    Log.e(TAG, "Invalid WAV header")
                    return ShortArray(0)
                }

                // Verify RIFF header
                if (header[0].toInt().toChar() != 'R' ||
                    header[1].toInt().toChar() != 'I' ||
                    header[2].toInt().toChar() != 'F' ||
                    header[3].toInt().toChar() != 'F') {
                    Log.e(TAG, "Not a valid RIFF file")
                    return ShortArray(0)
                }

                // Get data size from header (bytes 40-43)
                val dataSize = (header[40].toInt() and 0xFF) or
                        ((header[41].toInt() and 0xFF) shl 8) or
                        ((header[42].toInt() and 0xFF) shl 16) or
                        ((header[43].toInt() and 0xFF) shl 24)

                // Read PCM data
                val pcmData = ByteArray(dataSize)
                val bytesRead = fis.read(pcmData)

                // Convert bytes to shorts (16-bit little-endian)
                val numSamples = bytesRead / 2
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val low = pcmData[i * 2].toInt() and 0xFF
                    val high = pcmData[i * 2 + 1].toInt()
                    samples[i] = ((high shl 8) or low).toShort()
                }

                return samples
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading WAV file", e)
            return ShortArray(0)
        }
    }

    /**
     * Check if a sample is currently loaded.
     */
    @Suppress("unused")
    fun hasSampleLoaded(): Boolean {
        return processedSamples != null || rawSamples != null
    }

    /**
     * Get the duration of the loaded sample in seconds.
     */
    @Suppress("unused")
    fun getSampleDuration(): Float {
        val samples = processedSamples ?: rawSamples ?: return 0f
        return samples.size.toFloat() / SAMPLE_RATE
    }

    /**
     * Get the full processing result (for SF2 export).
     */
    @Suppress("unused")
    fun getProcessingResult(): AudioProcessor.ProcessedResult? = processedResult

}
