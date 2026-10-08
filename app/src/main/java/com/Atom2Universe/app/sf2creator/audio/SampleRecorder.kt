package com.Atom2Universe.app.sf2creator.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Records audio samples for SF2 creation using a 3-phase approach:
 *
 * 1. IDLE: Waiting for user to start
 * 2. PREPARING: Microphone active, circular buffer filling (user clicked "Get Ready")
 * 3. RECORDING: User clicked "Record", marking start position in buffer
 * 4. COMPLETED: User clicked "Stop", sample extracted from buffer
 *
 * This approach eliminates microphone startup transients by having the mic
 * already running when the user starts recording.
 */
class SampleRecorder(private val context: Context) {

    companion object {
        private const val TAG = "SampleRecorder"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val DEFAULT_MAX_DURATION_MS = 5000L // 5 seconds default

        // Circular buffer capacity (must be larger than max recording duration)
        private const val BUFFER_CAPACITY_SECONDS = 12f

        // Pre-roll: include audio from before "Record" was pressed
        const val PREROLL_MS = 100
        private const val PREROLL_SAMPLES = (SAMPLE_RATE * PREROLL_MS / 1000)

        // Noise profiling: use pre-recording countdown audio to reduce background noise
        private const val NOISE_PROFILE_MAX_MS = 2000
        private const val NOISE_PROFILE_MAX_SAMPLES = SAMPLE_RATE * NOISE_PROFILE_MAX_MS / 1000
        private const val NOISE_PROFILE_MIN_SAMPLES = 8820 // 200 ms minimum for a valid profile
    }

    /** Configurable max recording duration in milliseconds (1-10 seconds) */
    var maxDurationMs: Long = DEFAULT_MAX_DURATION_MS
        set(value) {
            field = value.coerceIn(1000L, 10000L)
        }

    // Circular buffer for continuous capture
    private val circularBuffer = CircularAudioBuffer(BUFFER_CAPACITY_SECONDS, SAMPLE_RATE)

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    @Volatile
    private var isCapturing = false // Mic is active (Preparing or Recording)

    @Volatile
    private var isRecording = false // User pressed Record (marking samples)

    private var recordStartPosition: Int = 0 // Position in buffer when Record was pressed
    private var recordStartTimeMs: Long = 0L

    private var outputFile: File? = null

    // Noise reduction: captures ambient noise during countdown, applies before saving
    private val noiseProfiler = NoiseProfiler(SAMPLE_RATE)
    @Volatile
    private var totalSamplesAtRecordStart: Long = 0L

    // State flows for UI updates
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _preparingDuration = MutableStateFlow(0L)
    @Suppress("unused")
    val preparingDuration: StateFlow<Long> = _preparingDuration

    /**
     * Recording states for the 3-phase approach.
     */
    sealed class RecordingState {
        /** Initial state - mic is off */
        object Idle : RecordingState()

        /** Mic is on, buffer is filling - waiting for user to press Record */
        object Preparing : RecordingState()

        /** User pressed Record - capturing audio */
        object Recording : RecordingState()

        /** Recording complete with extracted (and noise-reduced) samples */
        data class Completed(
            val file: File,
            val samples: ShortArray,
            val noiseLevelDb: Float = Float.NEGATIVE_INFINITY,
            val noiseReductionApplied: Boolean = false
        ) : RecordingState() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Completed
                return file == other.file &&
                        samples.contentEquals(other.samples) &&
                        noiseReductionApplied == other.noiseReductionApplied
            }
            override fun hashCode(): Int {
                var result = file.hashCode()
                result = 31 * result + samples.contentHashCode()
                result = 31 * result + noiseReductionApplied.hashCode()
                return result
            }
        }

        /** Max duration (5s) was reached */
        object MaxDurationReached : RecordingState()

        /** Error occurred */
        data class Error(val message: String) : RecordingState()
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Phase 1: Start preparing (mic on, buffer filling).
     * Call this when user clicks "Get Ready".
     */
    fun startPreparing(): Boolean {
        if (!hasRecordPermission()) {
            Log.e(TAG, "No record permission")
            _recordingState.value = RecordingState.Error("Permission d'enregistrement non accordée")
            return false
        }

        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $minBufferSize")
                _recordingState.value = RecordingState.Error("Configuration audio invalide")
                return false
            }

            val bufferSize = minBufferSize * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                _recordingState.value = RecordingState.Error("Impossible d'initialiser l'enregistrement")
                return false
            }

            // Clear buffer and state
            circularBuffer.clear()
            _amplitude.value = 0f
            _preparingDuration.value = 0L
            _recordingDuration.value = 0L
            _progress.value = 0f

            isCapturing = true
            isRecording = false

            audioRecord?.startRecording()
            _recordingState.value = RecordingState.Preparing

            // Start capture thread
            val prepareStartTime = System.currentTimeMillis()
            captureThread = Thread {
                captureAudioLoop(bufferSize, prepareStartTime)
            }.apply {
                priority = Thread.MAX_PRIORITY
                name = "SF2-AudioCapture"
                start()
            }

            Log.d(TAG, "Preparing started - mic is active")
            return true

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            _recordingState.value = RecordingState.Error("Permission refusée: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preparation", e)
            _recordingState.value = RecordingState.Error("Erreur: ${e.message}")
            return false
        }
    }

    /**
     * Phase 2: Start recording (mark start position in buffer).
     * Call this when user clicks "Record".
     */
    fun startRecording() {
        if (!isCapturing) {
            Log.e(TAG, "Cannot start recording - not preparing")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Mark the current position in the circular buffer
        recordStartPosition = circularBuffer.getCurrentPosition()
        recordStartTimeMs = System.currentTimeMillis()
        // Save how much audio was captured during PREPARING — used for noise profiling
        totalSamplesAtRecordStart = circularBuffer.getTotalSamplesWritten()
        isRecording = true

        _recordingState.value = RecordingState.Recording
        _recordingDuration.value = 0L
        _progress.value = 0f

        Log.d(TAG, "Recording started at buffer position: $recordStartPosition")
    }

    /**
     * Phase 3: Stop recording and extract samples.
     * Call this when user clicks "Stop".
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return
        }

        Log.d(TAG, "Stopping recording")

        val endPosition = circularBuffer.getCurrentPosition()
        isRecording = false
        isCapturing = false

        // Wait for capture thread to finish
        val thread = captureThread
        captureThread = null
        try {
            thread?.join(1000)
            // If thread is still alive after timeout, interrupt it
            if (thread?.isAlive == true) {
                Log.w(TAG, "Capture thread did not stop in time, interrupting")
                thread.interrupt()
                thread.join(500) // Give it a bit more time after interrupt
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for capture thread", e)
        }

        // Stop AudioRecord (safe now that capture thread is stopped)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        // Extract samples from buffer with pre-roll
        var samples = circularBuffer.extract(recordStartPosition, endPosition, PREROLL_SAMPLES)

        if (samples == null || samples.isEmpty()) {
            Log.e(TAG, "Failed to extract samples from buffer")
            _recordingState.value = RecordingState.Error("Aucune donnée enregistrée")
            return
        }

        Log.d(TAG, "Extracted ${samples.size} samples (${samples.size * 1000 / SAMPLE_RATE}ms) with ${PREROLL_MS}ms pre-roll")

        // Apply noise reduction using ambient sound captured during the countdown
        var noiseLevelDb = Float.NEGATIVE_INFINITY
        var noiseReductionApplied = false
        val noiseProfileSamples = extractNoiseProfile()
        if (noiseProfileSamples != null) {
            Log.d(TAG, "Noise profile: ${noiseProfileSamples.size} samples (${noiseProfileSamples.size * 1000 / SAMPLE_RATE}ms)")
            noiseProfiler.captureNoiseProfile(noiseProfileSamples)
            noiseLevelDb = noiseProfiler.getMeasuredNoiseDb()
            if (noiseProfiler.hasSignificantNoise()) {
                samples = noiseProfiler.apply(samples)
                noiseReductionApplied = true
                Log.d(TAG, "Noise reduction applied (noise floor: ${"%.1f".format(noiseLevelDb)} dBFS)")
            } else {
                Log.d(TAG, "Noise floor (${"%.1f".format(noiseLevelDb)} dBFS) not significant, skipping reduction")
            }
        } else {
            Log.d(TAG, "Not enough pre-recording audio for noise profile (need ${NOISE_PROFILE_MIN_SAMPLES * 1000 / SAMPLE_RATE}ms)")
        }

        // Save to WAV file
        try {
            val cacheDir = File(context.cacheDir, "sf2_samples")
            cacheDir.mkdirs()
            outputFile = File(cacheDir, "sample_${System.currentTimeMillis()}.wav")

            saveWavFile(outputFile!!, samples)
            Log.d(TAG, "WAV file created: ${outputFile!!.absolutePath}")

            _recordingState.value = RecordingState.Completed(outputFile!!, samples, noiseLevelDb, noiseReductionApplied)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving WAV file", e)
            _recordingState.value = RecordingState.Error("Erreur de sauvegarde: ${e.message}")
        }

        _amplitude.value = 0f
    }

    /**
     * Extract the ambient noise samples captured during the countdown (PREPARING phase),
     * just before the actual recording started. Used to build the noise profile.
     */
    private fun extractNoiseProfile(): ShortArray? {
        val capacity = circularBuffer.getCapacity()
        // Samples available before recording, excluding the pre-roll window
        val availableForNoise = totalSamplesAtRecordStart - PREROLL_SAMPLES
        if (availableForNoise < NOISE_PROFILE_MIN_SAMPLES) return null

        val samplesToExtract = minOf(availableForNoise, NOISE_PROFILE_MAX_SAMPLES.toLong()).toInt()

        // Calculate buffer positions for the noise window
        val noiseEndPos = ((recordStartPosition - PREROLL_SAMPLES) % capacity + capacity) % capacity
        val noiseStartPos = ((noiseEndPos - samplesToExtract) % capacity + capacity) % capacity
        return circularBuffer.extract(noiseStartPos, noiseEndPos, 0)
    }

    /**
     * Cancel preparation or recording.
     */
    fun cancel() {
        Log.d(TAG, "Cancelling")
        isRecording = false
        isCapturing = false

        try {
            captureThread?.join(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for capture thread", e)
        }
        captureThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        outputFile?.delete()
        outputFile = null

        circularBuffer.clear()
        _amplitude.value = 0f
        _recordingDuration.value = 0L
        _preparingDuration.value = 0L
        _progress.value = 0f
        _recordingState.value = RecordingState.Idle
    }

    /**
     * Reset to idle state.
     */
    fun reset() {
        cancel()
    }

    /**
     * Main capture loop - runs in background thread.
     * Continuously reads from mic and writes to circular buffer.
     */
    private fun captureAudioLoop(bufferSize: Int, prepareStartTime: Long) {
        val buffer = ShortArray(bufferSize / 2)
        var maxDurationReached = false

        try {
            while (isCapturing) {
                val recorder = audioRecord ?: break

                val readResult = recorder.read(buffer, 0, buffer.size)

                if (readResult > 0) {
                    // Write to circular buffer
                    circularBuffer.write(buffer, 0, readResult)

                    // Calculate amplitude for visualization
                    var maxAmp = 0
                    for (i in 0 until readResult) {
                        val amp = abs(buffer[i].toInt())
                        if (amp > maxAmp) maxAmp = amp
                    }
                    _amplitude.value = maxAmp.toFloat() / Short.MAX_VALUE

                    // Update duration based on phase
                    if (isRecording) {
                        // Recording phase - track recording duration
                        val elapsedMs = System.currentTimeMillis() - recordStartTimeMs
                        _recordingDuration.value = elapsedMs.coerceAtMost(maxDurationMs)
                        _progress.value = (elapsedMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)

                        // Auto-stop at max duration
                        if (elapsedMs >= maxDurationMs) {
                            Log.d(TAG, "Max duration reached")
                            maxDurationReached = true
                            break
                        }
                    } else {
                        // Preparing phase - just track how long mic has been on
                        _preparingDuration.value = System.currentTimeMillis() - prepareStartTime
                    }

                } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Invalid operation during read")
                    break
                } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Bad value during read")
                    break
                }
            }

            // Handle max duration reached
            if (maxDurationReached && isRecording) {
                val endPosition = circularBuffer.getCurrentPosition()
                isRecording = false
                isCapturing = false

                // Stop AudioRecord
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord", e)
                }
                audioRecord = null

                // Extract samples
                var samples = circularBuffer.extract(recordStartPosition, endPosition, PREROLL_SAMPLES)

                if (samples != null && samples.isNotEmpty()) {
                    // Apply noise reduction using pre-recording countdown audio
                    var noiseLevelDb = Float.NEGATIVE_INFINITY
                    var noiseReductionApplied = false
                    val noiseProfileSamples = extractNoiseProfile()
                    if (noiseProfileSamples != null) {
                        noiseProfiler.captureNoiseProfile(noiseProfileSamples)
                        noiseLevelDb = noiseProfiler.getMeasuredNoiseDb()
                        if (noiseProfiler.hasSignificantNoise()) {
                            samples = noiseProfiler.apply(samples)
                            noiseReductionApplied = true
                            Log.d(TAG, "Noise reduction applied (max-dur) floor: ${"%.1f".format(noiseLevelDb)} dBFS")
                        }
                    }

                    try {
                        val cacheDir = File(context.cacheDir, "sf2_samples")
                        cacheDir.mkdirs()
                        outputFile = File(cacheDir, "sample_${System.currentTimeMillis()}.wav")

                        saveWavFile(outputFile!!, samples)

                        _recordingState.value = RecordingState.MaxDurationReached
                        Thread.sleep(100)
                        _recordingState.value = RecordingState.Completed(outputFile!!, samples, noiseLevelDb, noiseReductionApplied)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving WAV file", e)
                        _recordingState.value = RecordingState.Error("Erreur de sauvegarde: ${e.message}")
                    }
                } else {
                    _recordingState.value = RecordingState.Error("Aucune donnée enregistrée")
                }

                _amplitude.value = 0f
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in capture loop", e)
            _recordingState.value = RecordingState.Error("Erreur de capture: ${e.message}")
        }
    }

    /**
     * Save samples to a WAV file.
     */
    private fun saveWavFile(wavFile: File, samples: ShortArray) {
        val channels = 1 // Mono
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val dataSize = samples.size * 2
        val totalDataLen = dataSize + 36

        RandomAccessFile(wavFile, "rw").use { raf ->
            // RIFF header
            raf.writeBytes("RIFF")
            raf.write(intToByteArray(totalDataLen))
            raf.writeBytes("WAVE")

            // fmt subchunk
            raf.writeBytes("fmt ")
            raf.write(intToByteArray(16)) // Subchunk1Size (16 for PCM)
            raf.write(shortToByteArray(1)) // AudioFormat (1 = PCM)
            raf.write(shortToByteArray(channels.toShort())) // NumChannels
            raf.write(intToByteArray(SAMPLE_RATE)) // SampleRate
            raf.write(intToByteArray(byteRate)) // ByteRate
            raf.write(shortToByteArray((channels * bitsPerSample / 8).toShort())) // BlockAlign
            raf.write(shortToByteArray(bitsPerSample.toShort())) // BitsPerSample

            // data subchunk
            raf.writeBytes("data")
            raf.write(intToByteArray(dataSize))

            // Write samples as bytes (little endian)
            val byteBuffer = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val sample = samples[i]
                byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
            }
            raf.write(byteBuffer)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }

    /**
     * Check if currently capturing (mic is on).
     */
    @Suppress("unused")
    fun isCapturing(): Boolean = isCapturing

    /**
     * Check if currently recording (user pressed Record).
     */
    @Suppress("unused")
    fun isRecording(): Boolean = isRecording

    /**
     * Clean up old sample recordings from cache.
     */
    @Suppress("unused")
    fun cleanupOldSamples(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val cacheDir = File(context.cacheDir, "sf2_samples")
        if (cacheDir.exists()) {
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMs) {
                    file.delete()
                }
            }
        }
    }
}
