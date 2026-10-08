package com.Atom2Universe.app.midi.practice

import android.util.Log
import com.Atom2Universe.app.midi.sf2.AudioRenderer
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enregistre le flux audio en temps reel dans un fichier WAV.
 * Thread-safe : peut etre appele depuis le thread audio.
 */
class AudioStreamRecorder(
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "AudioStreamRecorder"
        private const val CHANNELS = 2
        private const val BITS_PER_SAMPLE = 16
    }

    private var raf: RandomAccessFile? = null
    private var outputFile: File? = null
    private var dataSize = 0
    private val isRecording = AtomicBoolean(false)

    // Reusable byte buffer to avoid allocations on audio thread
    private var pcmBuffer: ByteArray? = null

    val tapCallback = AudioRenderer.AudioTapCallback { interleavedBuffer, numFrames ->
        if (isRecording.get()) {
            writeFrames(interleavedBuffer, numFrames)
        }
    }

    /**
     * Demarre l'enregistrement dans le fichier specifie.
     * Doit etre appele depuis un thread non-audio.
     */
    fun start(file: File): Boolean {
        try {
            file.parentFile?.mkdirs()
            val randomAccess = RandomAccessFile(file, "rw")
            randomAccess.setLength(0)

            // Ecrire header WAV placeholder
            writeWavHeader(randomAccess, 0)

            raf = randomAccess
            outputFile = file
            dataSize = 0
            isRecording.set(true)

            Log.d(TAG, "Recording started: ${file.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            return false
        }
    }

    /**
     * Arrete l'enregistrement et finalise le fichier WAV.
     * Retourne le fichier enregistre, ou null en cas d'erreur.
     */
    fun stop(): File? {
        isRecording.set(false)

        val randomAccess = raf ?: return null
        val file = outputFile

        try {
            // Mettre a jour le header WAV avec la taille reelle
            randomAccess.seek(0)
            writeWavHeader(randomAccess, dataSize)
            randomAccess.close()

            Log.d(TAG, "Recording stopped: $dataSize bytes written to ${file?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recording", e)
        }

        raf = null
        outputFile = null
        return file
    }

    fun isRecording(): Boolean = isRecording.get()

    /**
     * Ecrit des frames audio interleaved float dans le fichier WAV.
     * Appele depuis le thread audio - doit etre rapide.
     */
    private fun writeFrames(interleavedBuffer: FloatArray, numFrames: Int) {
        val randomAccess = raf ?: return
        val bytesNeeded = numFrames * CHANNELS * (BITS_PER_SAMPLE / 8)

        // Reutiliser le buffer si possible
        var buf = pcmBuffer
        if (buf == null || buf.size < bytesNeeded) {
            buf = ByteArray(bytesNeeded)
            pcmBuffer = buf
        }

        // Convertir float interleaved [-1,1] -> PCM 16-bit signed LE
        val totalSamples = numFrames * CHANNELS
        for (i in 0 until totalSamples) {
            val sample = (interleavedBuffer[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            val offset = i * 2
            buf[offset] = (sample.toInt() and 0xFF).toByte()
            buf[offset + 1] = (sample.toInt() shr 8 and 0xFF).toByte()
        }

        try {
            synchronized(this) {
                randomAccess.write(buf, 0, bytesNeeded)
                dataSize += bytesNeeded
            }
        } catch (e: Exception) {
            // Don't crash the audio thread - just log and stop
            Log.e(TAG, "Write error, stopping recording", e)
            isRecording.set(false)
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Int) {
        val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        raf.writeBytes("RIFF")
        writeIntLE(raf, 36 + dataSize)
        raf.writeBytes("WAVE")

        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1) // PCM
        writeShortLE(raf, CHANNELS)
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, blockAlign)
        writeShortLE(raf, BITS_PER_SAMPLE)

        raf.writeBytes("data")
        writeIntLE(raf, dataSize)
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write(value shr 8 and 0xFF)
        raf.write(value shr 16 and 0xFF)
        raf.write(value shr 24 and 0xFF)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write(value shr 8 and 0xFF)
    }
}
