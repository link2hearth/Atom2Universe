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
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced sample player for previewing recorded samples at different pitches.
 *
 * Features:
 * - Two pitch shifting algorithms: WSOLA (fast) and Phase Vocoder (quality)
 * - Full audio processing pipeline: noise gate, normalization, trim, fades, loop detection
 * - Caching of processed samples for faster playback
 */
class SamplePlayer {

    companion object {
        private const val TAG = "SamplePlayer"
        private const val SAMPLE_RATE = 44100
        private const val MAX_VOICES = 4  // Polyphony: max simultaneous notes
    }

    /**
     * Pitch shifting algorithm selection.
     */
    enum class PitchShiftAlgorithm {
        WSOLA,         // Fast, good for small shifts (< 6 semitones)
        PHASE_VOCODER, // Higher quality, better for large shifts
        HYBRID         // Auto-select best algorithm based on interval size
    }

    // Fade applied after pitch shifting to eliminate clicks
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
    private val trackLock = Any()

    // Reusable silence buffer for clearing STATIC AudioTrack buffer between playbacks
    private val silenceChunk = ShortArray(4096)

    // Audio processors
    private val pitchShifterWSOLA = PitchShifter(SAMPLE_RATE)
    private val pitchShifterPV = PitchShifterPV(SAMPLE_RATE)
    private val audioProcessor = AudioProcessor(SAMPLE_RATE)
    private val deClicker = DeClicker(SAMPLE_RATE)
    private val normalizer = Normalizer()
    private val envelopeGenerator = EnvelopeGenerator(SAMPLE_RATE)

    // Settings
    private var algorithm = PitchShiftAlgorithm.HYBRID  // Default to hybrid for best quality
    private var processingOptions = AudioProcessor.ProcessingOptions()

    // Cache for pitch-shifted versions (thread-safe)
    private val pitchCache = ConcurrentHashMap<Int, ShortArray>()
    private val computingNotes = ConcurrentHashMap.newKeySet<Int>() // Notes currently being computed
    private var cacheEnabled = true

    /**
     * Set the pitch shifting algorithm.
     */
    @Suppress("unused")
    fun setAlgorithm(algo: PitchShiftAlgorithm) {
        if (algorithm != algo) {
            algorithm = algo
            pitchCache.clear() // Clear cache when algorithm changes
        }
    }

    /**
     * Get the current algorithm.
     */
    @Suppress("unused")
    fun getAlgorithm(): PitchShiftAlgorithm = algorithm

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
            pitchCache.clear()
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
            pitchCache.clear()
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
            pitchCache.clear()
        }
    }

    /**
     * Enable or disable pitch cache.
     */
    @Suppress("unused")
    fun setCacheEnabled(enabled: Boolean) {
        cacheEnabled = enabled
        if (!enabled) pitchCache.clear()
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
        pitchCache.clear()
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
            pitchCache.clear() // Clear cache when root note changes
        }
    }

    /**
     * Get the current root note.
     */
    @Suppress("unused")
    fun getRootNote(): Int = rootNote

    /**
     * Play the sample at its original pitch.
     */
    fun play() {
        playSampleAtNote(rootNote)
    }

    /**
     * Play the sample with an ADSR envelope applied for preview.
     * This gives an approximation of how the sound will behave with the given envelope.
     */
    @Suppress("unused")
    fun playWithEnvelope(adsrSettings: EnvelopeGenerator.ADSRSettings) {
        playWithEnvelopeAtNote(rootNote, adsrSettings)
    }

    /**
     * Play the sample at a specific note with ADSR envelope applied.
     */
    fun playWithEnvelopeAtNote(targetNote: Int, adsrSettings: EnvelopeGenerator.ADSRSettings) {
        val samples = processedSamples ?: rawSamples
        if (samples == null || samples.isEmpty()) {
            Log.w(TAG, "No sample loaded")
            return
        }

        // Don't stop - polyphony handles multiple voices
        playbackJob = scope.launch {
            try {
                val semitones = (targetNote - rootNote).toFloat()

                // Get pitch-shifted samples (use cache if available)
                var shiftedSamples = if (cacheEnabled && pitchCache.containsKey(targetNote)) {
                    pitchCache[targetNote]!!.copyOf()
                } else {
                    var shifted = when {
                        semitones == 0f -> samples.copyOf()
                        else -> selectAndApplyPitchShift(samples, semitones)
                    }

                    // Always apply cleanup (even for original pitch)
                    shifted = deClicker.removeDCOffset(shifted)
                    shifted = deClicker.applySoftClipping(shifted, threshold = 0.95f)

                    shifted
                }

                // Apply ADSR envelope
                Log.d(TAG, "Applying ADSR: attack=${adsrSettings.attackMs}ms, decay=${adsrSettings.decayMs}ms, sustain=${adsrSettings.sustainLevel}, release=${adsrSettings.releaseMs}ms")
                Log.d(TAG, "Sample length before envelope: ${shiftedSamples.size} samples (${shiftedSamples.size * 1000 / SAMPLE_RATE}ms)")

                shiftedSamples = envelopeGenerator.applyEnvelope(shiftedSamples, adsrSettings)

                Log.d(TAG, "Sample length after envelope: ${shiftedSamples.size} samples")

                // Trim trailing zeros to avoid playing silence
                shiftedSamples = trimTrailingZeros(shiftedSamples)
                Log.d(TAG, "Sample length after trim: ${shiftedSamples.size} samples (${shiftedSamples.size * 1000 / SAMPLE_RATE}ms)")

                // Ensure zero crossings at start/end to prevent clicks
                shiftedSamples = ensureZeroCrossings(shiftedSamples)

                // Play the result with polyphony
                withContext(Dispatchers.Main) {
                    playAudio(shiftedSamples, targetNote)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sample with envelope", e)
            }
        }
    }

    /**
     * Play the sample transposed to the target MIDI note.
     * Uses the selected pitch shifting algorithm to maintain original duration.
     */
    fun playSampleAtNote(targetNote: Int) {
        val samples = processedSamples ?: rawSamples
        if (samples == null || samples.isEmpty()) {
            Log.w(TAG, "No sample loaded")
            return
        }

        // Don't stop - polyphony handles multiple voices
        playbackJob = scope.launch {
            try {
                val semitones = (targetNote - rootNote).toFloat()

                // Check cache first
                val pitchShiftedData = if (cacheEnabled && pitchCache.containsKey(targetNote)) {
                    Log.d(TAG, "Using cached pitch shift for note $targetNote")
                    pitchCache[targetNote]!!
                } else {
                    // Wait if this note is currently being computed by precompute
                    var waitAttempts = 0
                    while (computingNotes.contains(targetNote) && waitAttempts < 50) {
                        kotlinx.coroutines.delay(50) // Wait 50ms
                        waitAttempts++
                    }

                    // Check cache again after waiting
                    if (pitchCache.containsKey(targetNote)) {
                        Log.d(TAG, "Using cached pitch shift for note $targetNote (after wait)")
                        pitchCache[targetNote]!!
                    } else {
                        // Perform pitch shifting using selected algorithm
                        Log.d(TAG, "Computing pitch shift on-demand for note $targetNote")
                        var shifted = when {
                            semitones == 0f -> samples.copyOf()
                            else -> selectAndApplyPitchShift(samples, semitones)
                        }

                        // Always apply cleanup (even for original pitch)
                        shifted = deClicker.removeDCOffset(shifted)
                        shifted = deClicker.applySoftClipping(shifted, threshold = 0.95f)

                        // Apply fade after processing to eliminate edge clicks
                        shifted = applyAntiClickFades(shifted)

                        // Cache the result
                        if (cacheEnabled) {
                            pitchCache[targetNote] = shifted
                        }

                        shifted
                    }
                }

                // Play the pitch-shifted audio with polyphony
                withContext(Dispatchers.Main) {
                    playAudio(pitchShiftedData, targetNote)
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
     * Trim trailing zeros/near-zeros from the end of samples.
     * This avoids playing silence which can cause glitches on some devices.
     * Adds a gentle fade out at the end to prevent abrupt cutoff.
     */
    private fun trimTrailingZeros(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples

        // Find the last non-zero sample (with threshold for near-zero)
        val threshold = 50 // Small values considered as silence
        var lastNonZero = samples.lastIndex

        while (lastNonZero > 0 && kotlin.math.abs(samples[lastNonZero].toInt()) < threshold) {
            lastNonZero--
        }

        // Keep a small buffer of samples after the last non-zero for smooth fade
        val keepExtra = (SAMPLE_RATE * 0.02).toInt() // 20ms extra for fade
        val endIndex = (lastNonZero + keepExtra).coerceAtMost(samples.lastIndex)

        // Minimum length to avoid issues
        val minLength = (SAMPLE_RATE * 0.05).toInt() // At least 50ms
        if (endIndex < minLength) return samples

        val trimmed = samples.copyOfRange(0, endIndex + 1)

        // Apply a gentle fade out at the end (15ms)
        val fadeOutSamples = (SAMPLE_RATE * 0.015).toInt().coerceAtMost(trimmed.size / 4)
        val fadeStart = trimmed.size - fadeOutSamples

        for (i in 0 until fadeOutSamples) {
            val index = fadeStart + i
            val progress = i.toFloat() / fadeOutSamples
            // S-curve fade out
            val gain = (1 + kotlin.math.cos(progress * kotlin.math.PI)) / 2
            trimmed[index] = (trimmed[index] * gain).toInt().toShort()
        }

        // Force last few samples to zero
        val zeroCount = minOf(8, trimmed.size)
        for (i in 0 until zeroCount) {
            trimmed[trimmed.lastIndex - i] = 0
        }

        return trimmed
    }

    /**
     * Apply a gentle fade at the start only.
     * The envelope handles the end, so we only need to prevent start clicks.
     */
    /**
     * Ensure the sample starts and ends at zero crossings to prevent clicks.
     * Zero crossing = where the waveform crosses through zero.
     */
    private fun ensureZeroCrossings(samples: ShortArray): ShortArray {
        if (samples.size < 100) return samples

        val output = samples.copyOf()
        val searchWindow = (SAMPLE_RATE * 0.002).toInt() // 2ms search window

        // Find zero crossing near start
        var startZeroCrossing = 0
        for (i in 0 until minOf(searchWindow, samples.size - 1)) {
            val curr = samples[i].toInt()
            val next = samples[i + 1].toInt()
            // Zero crossing: sign change or exactly zero
            if (curr == 0 || (curr > 0 && next <= 0) || (curr < 0 && next >= 0)) {
                startZeroCrossing = i
                break
            }
        }

        // Fade in from start to zero crossing (very short fade)
        if (startZeroCrossing > 0) {
            for (i in 0 until startZeroCrossing) {
                val progress = i.toFloat() / startZeroCrossing
                output[i] = (output[i] * progress).toInt().toShort()
            }
        }

        // Find zero crossing near end
        var endZeroCrossing = samples.lastIndex
        for (i in samples.lastIndex downTo maxOf(0, samples.lastIndex - searchWindow)) {
            val curr = samples[i].toInt()
            if (i > 0) {
                val prev = samples[i - 1].toInt()
                if (curr == 0 || (curr > 0 && prev <= 0) || (curr < 0 && prev >= 0)) {
                    endZeroCrossing = i
                    break
                }
            }
        }

        // Fade out from zero crossing to end (very short fade)
        if (endZeroCrossing < samples.lastIndex) {
            val fadeLength = samples.lastIndex - endZeroCrossing
            for (i in endZeroCrossing..samples.lastIndex) {
                val progress = 1f - ((i - endZeroCrossing).toFloat() / fadeLength)
                output[i] = (output[i] * progress).toInt().toShort()
            }
        }

        return output
    }

    /**
     * Select and apply the best pitch shifting algorithm based on settings and interval.
     *
     * HYBRID mode strategy:
     * - Small shifts (0-4 semitones): WSOLA (fast, stable, minimal artifacts)
     * - Large shifts (5+ semitones): Phase Vocoder (better for preserving formants)
     */
    private fun selectAndApplyPitchShift(samples: ShortArray, semitones: Float): ShortArray {
        val absSemitones = kotlin.math.abs(semitones)

        return when (algorithm) {
            PitchShiftAlgorithm.WSOLA -> {
                Log.d(TAG, "Using WSOLA for $semitones semitones")
                pitchShifterWSOLA.shiftPitch(samples, semitones)
            }
            PitchShiftAlgorithm.PHASE_VOCODER -> {
                Log.d(TAG, "Using Phase Vocoder for $semitones semitones")
                pitchShifterPV.shiftPitch(samples, semitones)
            }
            PitchShiftAlgorithm.HYBRID -> {
                // Auto-select based on interval size
                if (absSemitones <= 4f) {
                    // Small intervals: WSOLA is more stable
                    Log.d(TAG, "HYBRID: Using WSOLA for small shift ($semitones semitones)")
                    pitchShifterWSOLA.shiftPitch(samples, semitones)
                } else {
                    // Larger intervals: Phase Vocoder handles better
                    Log.d(TAG, "HYBRID: Using Phase Vocoder for large shift ($semitones semitones)")
                    pitchShifterPV.shiftPitch(samples, semitones)
                }
            }
        }
    }

    /**
     * Precompute pitch-shifted versions for common notes.
     * Call this after loading a sample to reduce latency during playback.
     *
     * @param notes List of MIDI notes to precompute (e.g., one octave around root)
     */
    @Suppress("unused")
    suspend fun precomputePitchCache(notes: List<Int>) {
        val samples = processedSamples ?: rawSamples ?: return

        withContext(Dispatchers.Default) {
            for (note in notes) {
                // Skip if already cached or currently being computed
                if (pitchCache.containsKey(note)) continue

                // Try to claim this note for computation
                if (!computingNotes.add(note)) {
                    // Another thread is already computing this note
                    continue
                }

                try {
                    // Double-check cache after claiming (another thread might have just finished)
                    if (pitchCache.containsKey(note)) continue

                    val semitones = (note - rootNote).toFloat()
                    var shifted = when {
                        semitones == 0f -> samples.copyOf()
                        else -> selectAndApplyPitchShift(samples, semitones)
                    }

                    // Always apply cleanup (even for original pitch)
                    shifted = deClicker.removeDCOffset(shifted)
                    shifted = deClicker.applySoftClipping(shifted, threshold = 0.95f)

                    // Apply anti-click fades
                    shifted = applyAntiClickFades(shifted)
                    pitchCache[note] = shifted
                    Log.d(TAG, "Precomputed pitch cache for note $note")
                } finally {
                    computingNotes.remove(note)
                }
            }
        }
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
        }

        rawSamples = null
        processedSamples = null
        pitchCache.clear()
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
                    // create an audible click at the boundary. Filling with silence
                    // eliminates this completely.
                    val totalFrames = reusableTrackSize / 2
                    var remainingFrames = totalFrames - samples.size
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
     * Get sample info for display.
     */
    @Suppress("unused")
    fun getSampleInfo(): SampleInfo? {
        val raw = rawSamples ?: return null
        val result = processedResult

        return SampleInfo(
            rawDurationMs = (raw.size * 1000 / SAMPLE_RATE),
            processedDurationMs = result?.processedLengthMs ?: (raw.size * 1000 / SAMPLE_RATE),
            peakDb = result?.peakDb ?: normalizer.measurePeakDb(raw),
            rmsDb = result?.rmsDb ?: normalizer.measureRMSDb(raw),
            rootNote = rootNote,
            algorithm = algorithm,
            hasLoop = result?.hasLoop ?: false,
            loopStart = result?.loopStart ?: 0,
            loopEnd = result?.loopEnd ?: 0,
            loopQuality = result?.loopQuality ?: 0f,
            processingInfo = result?.processingInfo ?: ""
        )
    }

    /**
     * Get the full processing result (for SF2 export).
     */
    @Suppress("unused")
    fun getProcessingResult(): AudioProcessor.ProcessedResult? = processedResult

    data class SampleInfo(
        val rawDurationMs: Int,
        val processedDurationMs: Int,
        val peakDb: Float,
        val rmsDb: Float,
        val rootNote: Int,
        val algorithm: PitchShiftAlgorithm,
        val hasLoop: Boolean = false,
        val loopStart: Int = 0,
        val loopEnd: Int = 0,
        val loopQuality: Float = 0f,
        val processingInfo: String = ""
    )
}
