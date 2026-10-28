package com.Atom2Universe.app.midi.practice

import android.util.Log
import com.Atom2Universe.app.midi.sf2.AudioRenderer
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/**
 * Enregistre le flux audio en temps reel dans un fichier WAV.
 *
 * Le fil audio ne fait que convertir les echantillons dans un tampon circulaire en memoire ;
 * un fil d'ecriture dedie les vide sur le disque. Ainsi le fil audio n'attend jamais le disque.
 */
class AudioStreamRecorder(
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "AudioStreamRecorder"
        private const val CHANNELS = 2
        private const val BITS_PER_SAMPLE = 16
        // ~6 s de stereo 16 bits a 44,1 kHz : large marge si le disque traine
        private const val RING_SIZE = 1 shl 20
        private const val RING_MASK = RING_SIZE - 1L
        private val WRITER_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(40)
    }

    private var raf: RandomAccessFile? = null
    private var outputFile: File? = null
    // Ecrit seulement par le fil d'ecriture, lu par stop() apres l'avoir attendu
    private var dataSize = 0
    private val isRecording = AtomicBoolean(false)

    // Tampon circulaire : un seul producteur (fil audio), un seul consommateur (fil d'ecriture)
    private val ring = ByteArray(RING_SIZE)
    private val writePos = AtomicLong(0L)
    private val readPos = AtomicLong(0L)
    private var writerThread: Thread? = null

    val tapCallback = AudioRenderer.AudioTapCallback { interleavedBuffer, numFrames ->
        if (isRecording.get()) {
            pushFrames(interleavedBuffer, numFrames)
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
            writePos.set(0L)
            readPos.set(0L)
            isRecording.set(true)
            writerThread = Thread({ writerLoop(randomAccess) }, "PracticeRecorder-Writer").apply { start() }

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

        // Le fil d'ecriture vide ce qui reste puis s'arrete : plus personne n'ecrit ensuite
        val writer = writerThread
        writerThread = null
        if (writer != null) {
            LockSupport.unpark(writer)
            try {
                writer.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

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
     * Convertit des frames audio interleaved float en PCM 16 bits dans le tampon circulaire.
     * Appele depuis le thread audio : aucune entree/sortie, aucune allocation.
     */
    private fun pushFrames(interleavedBuffer: FloatArray, numFrames: Int) {
        val bytesNeeded = numFrames * CHANNELS * (BITS_PER_SAMPLE / 8)
        val write = writePos.get()
        val free = RING_SIZE - (write - readPos.get())
        if (bytesNeeded > free) return  // Disque trop lent : on perd ce bloc plutot que bloquer le son

        // Convertir float interleaved [-1,1] -> PCM 16-bit signed LE
        val totalSamples = numFrames * CHANNELS
        var pos = write
        for (i in 0 until totalSamples) {
            val sample = (interleavedBuffer[i].coerceIn(-1f, 1f) * 32767f).toInt()
            ring[(pos and RING_MASK).toInt()] = (sample and 0xFF).toByte()
            ring[((pos + 1) and RING_MASK).toInt()] = (sample shr 8 and 0xFF).toByte()
            pos += 2
        }
        writePos.set(pos)
    }

    private fun writerLoop(randomAccess: RandomAccessFile) {
        try {
            while (true) {
                val recording = isRecording.get()
                val read = readPos.get()
                val available = writePos.get() - read
                if (available > 0) {
                    // Au plus deux morceaux : jusqu'a la fin du tampon, puis depuis le debut
                    val start = (read and RING_MASK).toInt()
                    val first = minOf(available, (RING_SIZE - start).toLong()).toInt()
                    randomAccess.write(ring, start, first)
                    val second = (available - first).toInt()
                    if (second > 0) randomAccess.write(ring, 0, second)
                    dataSize += available.toInt()
                    readPos.set(read + available)
                } else if (!recording) {
                    break
                } else {
                    LockSupport.parkNanos(WRITER_POLL_NANOS)
                }
            }
        } catch (e: Exception) {
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
