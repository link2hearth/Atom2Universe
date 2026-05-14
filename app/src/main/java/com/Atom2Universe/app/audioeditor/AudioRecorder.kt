package com.Atom2Universe.app.audioeditor

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
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Handles audio recording from the device microphone.
 * Records in WAV format for easy editing and exports.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var outputFile: File? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        data class Completed(val file: File) : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio to a WAV file.
     */
    fun startRecording(): File? {
        if (!hasRecordPermission()) {
            Log.e(TAG, "No record permission")
            _recordingState.value = RecordingState.Error(
                context.getString(com.Atom2Universe.app.R.string.audio_editor_error_no_permission)
            )
            return null
        }

        if (!isRecording.compareAndSet(false, true)) {
            Log.w(TAG, "Already recording")
            return outputFile
        }

        // Reset state FIRST to clear any previous Completed state
        _recordingState.value = RecordingState.Idle

        try {
            // Create output file in cache
            val cacheDir = File(context.cacheDir, "audio_recordings")
            cacheDir.mkdirs()
            outputFile = File(cacheDir, "recording_${System.currentTimeMillis()}.wav")

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $minBufferSize")
                _recordingState.value = RecordingState.Error(
                    context.getString(com.Atom2Universe.app.R.string.audio_editor_error_invalid_config)
                )
                return null
            }

            val bufferSize = minBufferSize * 2

            // Create AudioRecord with try-finally to ensure release on any failure
            val newRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            try {
                if (newRecorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not initialized")
                    newRecorder.release()
                    _recordingState.value = RecordingState.Error(
                        context.getString(com.Atom2Universe.app.R.string.audio_editor_error_init_failed)
                    )
                    return null
                }
                audioRecord = newRecorder
            } catch (e: Exception) {
                // Ensure AudioRecord is released if any exception occurs after creation
                newRecorder.release()
                throw e
            }

            // Clear previous data
            _recordingDuration.value = 0L
            _amplitude.value = 0f

            // isRecording already set to true via compareAndSet
            audioRecord?.startRecording()
            _recordingState.value = RecordingState.Recording

            // Start recording thread
            recordingThread = Thread {
                writeAudioDataToFile(bufferSize)
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(TAG, "Recording started")
            return outputFile

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            _recordingState.value = RecordingState.Error(
                context.getString(com.Atom2Universe.app.R.string.audio_editor_error_permission_denied, e.message ?: "")
            )
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _recordingState.value = RecordingState.Error(
                context.getString(com.Atom2Universe.app.R.string.audio_editor_error_generic, e.message ?: "")
            )
            return null
        }
    }

    private fun writeAudioDataToFile(bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        val tempPcmFile = File(context.cacheDir, "temp_recording_${System.currentTimeMillis()}.pcm")
        var totalBytesWritten = 0L
        val startTime = System.currentTimeMillis()

        // Ensure temp file is always deleted using try-finally
        try {
            FileOutputStream(tempPcmFile).use { fos ->
                while (isRecording.get()) {
                    val recorder = audioRecord ?: break

                    val readResult = recorder.read(buffer, 0, buffer.size)

                    if (readResult > 0) {
                        // Convert shorts to bytes (little endian)
                        val byteBuffer = ByteArray(readResult * 2)
                        for (i in 0 until readResult) {
                            val sample = buffer[i]
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
                        }
                        fos.write(byteBuffer)
                        totalBytesWritten += byteBuffer.size

                        // Bug 2.31: Calculate amplitude with double precision for accuracy
                        // Handle Short.MIN_VALUE edge case by using Long for abs()
                        var maxAmp = 0L
                        for (i in 0 until readResult) {
                            val amp = abs(buffer[i].toLong())
                            if (amp > maxAmp) maxAmp = amp
                        }
                        // Use double division for precision, then convert to float
                        _amplitude.value = (maxAmp.toDouble() / Short.MAX_VALUE.toDouble()).toFloat()

                        // Update duration
                        _recordingDuration.value = System.currentTimeMillis() - startTime

                    } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Invalid operation during read")
                        break
                    } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Bad value during read")
                        break
                    }
                }
            }

            Log.d(TAG, "Recording stopped, bytes written: $totalBytesWritten")

            // Convert PCM to WAV
            val wavFile = outputFile
            if (wavFile != null && totalBytesWritten > 0) {
                convertPcmToWav(tempPcmFile, wavFile)
                Log.d(TAG, "WAV file created: ${wavFile.absolutePath}")
                _recordingState.value = RecordingState.Completed(wavFile)
            } else {
                _recordingState.value = RecordingState.Error(
                    context.getString(com.Atom2Universe.app.R.string.audio_editor_error_no_data)
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data", e)
            _recordingState.value = RecordingState.Error(
                context.getString(com.Atom2Universe.app.R.string.audio_editor_error_write, e.message ?: "")
            )
        } finally {
            // Always clean up temp file
            try {
                if (tempPcmFile.exists()) {
                    tempPcmFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete temp PCM file", e)
            }
        }
    }

    /**
     * Stop the current recording.
     */
    fun stopRecording() {
        Log.d(TAG, "Stopping recording")
        isRecording.set(false)

        try {
            // Wait for thread to finish with increased timeout
            val thread = recordingThread
            if (thread != null) {
                thread.join(5000)
                if (thread.isAlive) {
                    Log.w(TAG, "Recording thread did not stop within timeout, interrupting")
                    thread.interrupt()
                    thread.join(1000) // Give it a bit more time after interrupt
                }
            }
            recordingThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        _amplitude.value = 0f
    }

    /**
     * Cancel the current recording and delete the file.
     */
    fun cancelRecording() {
        Log.d(TAG, "Cancelling recording")
        isRecording.set(false)

        try {
            // Wait for thread to finish with increased timeout
            val thread = recordingThread
            if (thread != null) {
                thread.join(5000)
                if (thread.isAlive) {
                    Log.w(TAG, "Recording thread did not stop within timeout during cancel, interrupting")
                    thread.interrupt()
                    thread.join(1000) // Give it a bit more time after interrupt
                }
            }
            recordingThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording", e)
        }

        // Bug 2.32: Check delete result and log if failed
        outputFile?.let { file ->
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    Log.w(TAG, "Failed to delete cancelled recording file: ${file.absolutePath}")
                }
            }
        }
        outputFile = null
        _amplitude.value = 0f
        _recordingDuration.value = 0L
        _recordingState.value = RecordingState.Idle
    }

    /**
     * Reset state to idle.
     */
    fun reset() {
        _recordingState.value = RecordingState.Idle
        _amplitude.value = 0f
        _recordingDuration.value = 0L
    }

    /**
     * Convert raw PCM data to WAV format.
     */
    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        val totalDataLen = pcmData.size + 36
        val channels = 1 // Mono
        val byteRate = SAMPLE_RATE * channels * 2 // 16-bit

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
            raf.write(shortToByteArray((channels * 2).toShort())) // BlockAlign
            raf.write(shortToByteArray(16)) // BitsPerSample

            // data subchunk
            raf.writeBytes("data")
            raf.write(intToByteArray(pcmData.size))
            raf.write(pcmData)
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
     * Clean up old recordings from cache.
     */
    fun cleanupOldRecordings(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val cacheDir = File(context.cacheDir, "audio_recordings")
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
