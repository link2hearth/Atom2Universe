package com.Atom2Universe.app.sf2creator.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility class for reading and writing WAV audio files.
 * Centralizes all WAV file I/O operations for the SF2 Creator module.
 */
object WavUtils {

    /**
     * Result of reading a WAV file, containing the audio samples and metadata.
     */
    data class WavData(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WavData
            return samples.contentEquals(other.samples) &&
                    sampleRate == other.sampleRate &&
                    channels == other.channels &&
                    bitsPerSample == other.bitsPerSample
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + channels
            result = 31 * result + bitsPerSample
            return result
        }
    }

    /**
     * Standard WAV header size in bytes.
     */
    private const val WAV_HEADER_SIZE = 44

    /**
     * Load audio samples from a WAV file.
     * Supports 16-bit PCM WAV files.
     *
     * @param file The WAV file to read
     * @return ShortArray of audio samples, or null if reading fails
     */
    fun loadWavFile(file: File): ShortArray? {
        if (!file.exists()) return null

        return try {
            file.inputStream().use { fis ->
                readWavFromStream(fis)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load audio samples with full metadata from a WAV file.
     *
     * @param file The WAV file to read
     * @return WavData containing samples and metadata, or null if reading fails
     */
    fun loadWavFileWithMetadata(file: File): WavData? {
        if (!file.exists()) return null

        return try {
            file.inputStream().use { fis ->
                readWavWithMetadataFromStream(fis)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read WAV samples from an input stream.
     */
    private fun readWavFromStream(inputStream: InputStream): ShortArray? {
        val header = ByteArray(WAV_HEADER_SIZE)
        val read = inputStream.read(header)
        if (read < WAV_HEADER_SIZE) return null

        // Verify RIFF header
        if (!isValidRiffHeader(header)) return null

        // Get data size from header (bytes 40-43)
        val dataSize = readInt32LE(header, 40)

        // Read PCM data
        val pcmData = ByteArray(dataSize)
        val bytesRead = inputStream.read(pcmData)

        return convertBytesToShorts(pcmData, bytesRead)
    }

    /**
     * Read WAV with full metadata from an input stream.
     */
    private fun readWavWithMetadataFromStream(inputStream: InputStream): WavData? {
        val header = ByteArray(WAV_HEADER_SIZE)
        val read = inputStream.read(header)
        if (read < WAV_HEADER_SIZE) return null

        // Verify RIFF header
        if (!isValidRiffHeader(header)) return null

        // Parse header fields
        val channels = readInt16LE(header, 22)
        val sampleRate = readInt32LE(header, 24)
        val bitsPerSample = readInt16LE(header, 34)
        val dataSize = readInt32LE(header, 40)

        // Read PCM data
        val pcmData = ByteArray(dataSize)
        val bytesRead = inputStream.read(pcmData)

        val samples = convertBytesToShorts(pcmData, bytesRead) ?: return null

        return WavData(
            samples = samples,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
    }

    /**
     * Check if the header is a valid RIFF/WAVE header.
     */
    private fun isValidRiffHeader(header: ByteArray): Boolean {
        return header[0].toInt().toChar() == 'R' &&
                header[1].toInt().toChar() == 'I' &&
                header[2].toInt().toChar() == 'F' &&
                header[3].toInt().toChar() == 'F' &&
                header[8].toInt().toChar() == 'W' &&
                header[9].toInt().toChar() == 'A' &&
                header[10].toInt().toChar() == 'V' &&
                header[11].toInt().toChar() == 'E'
    }

    /**
     * Read a 32-bit little-endian integer from a byte array.
     */
    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    /**
     * Read a 16-bit little-endian integer from a byte array.
     */
    private fun readInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * Convert byte array to short array (16-bit little-endian PCM).
     */
    private fun convertBytesToShorts(pcmData: ByteArray, bytesRead: Int): ShortArray? {
        val numSamples = bytesRead / 2
        if (numSamples <= 0) return null

        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            samples[i] = ((high shl 8) or low).toShort()
        }
        return samples
    }

    /**
     * Write audio samples to a WAV file.
     *
     * @param file The output file
     * @param samples The audio samples to write
     * @param sampleRate Sample rate in Hz (default: 44100)
     * @param channels Number of channels (default: 1 for mono)
     * @param bitsPerSample Bits per sample (default: 16)
     * @return true if successful, false otherwise
     */
    fun writeWavFile(
        file: File,
        samples: ShortArray,
        sampleRate: Int = Sf2Constants.DEFAULT_SAMPLE_RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): Boolean {
        return try {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = samples.size * 2

            FileOutputStream(file).use { fos ->
                // RIFF header
                fos.write("RIFF".toByteArray())
                fos.write(intToLittleEndian(36 + dataSize))
                fos.write("WAVE".toByteArray())

                // fmt chunk
                fos.write("fmt ".toByteArray())
                fos.write(intToLittleEndian(16)) // Chunk size
                fos.write(shortToLittleEndian(1)) // Audio format (PCM)
                fos.write(shortToLittleEndian(channels.toShort()))
                fos.write(intToLittleEndian(sampleRate))
                fos.write(intToLittleEndian(byteRate))
                fos.write(shortToLittleEndian(blockAlign.toShort()))
                fos.write(shortToLittleEndian(bitsPerSample.toShort()))

                // data chunk
                fos.write("data".toByteArray())
                fos.write(intToLittleEndian(dataSize))

                // Write samples using ByteBuffer for efficiency
                val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                samples.forEach { buffer.putShort(it) }
                fos.write(buffer.array())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert an integer to a little-endian byte array.
     */
    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    /**
     * Convert a short to a little-endian byte array.
     */
    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Get the duration of a WAV file in seconds.
     *
     * @param file The WAV file
     * @return Duration in seconds, or 0 if file cannot be read
     */
    fun getWavDuration(file: File): Float {
        if (!file.exists()) return 0f

        return try {
            file.inputStream().use { fis ->
                val header = ByteArray(WAV_HEADER_SIZE)
                if (fis.read(header) < WAV_HEADER_SIZE) return 0f
                if (!isValidRiffHeader(header)) return 0f

                val sampleRate = readInt32LE(header, 24)
                val channels = readInt16LE(header, 22)
                val bitsPerSample = readInt16LE(header, 34)
                val dataSize = readInt32LE(header, 40)

                val bytesPerSample = bitsPerSample / 8
                val totalSamples = dataSize / (channels * bytesPerSample)
                totalSamples.toFloat() / sampleRate
            }
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Get the sample count of a WAV file.
     *
     * @param file The WAV file
     * @return Number of samples (per channel), or 0 if file cannot be read
     */
    fun getWavSampleCount(file: File): Int {
        if (!file.exists()) return 0

        return try {
            file.inputStream().use { fis ->
                val header = ByteArray(WAV_HEADER_SIZE)
                if (fis.read(header) < WAV_HEADER_SIZE) return 0
                if (!isValidRiffHeader(header)) return 0

                val channels = readInt16LE(header, 22)
                val bitsPerSample = readInt16LE(header, 34)
                val dataSize = readInt32LE(header, 40)

                val bytesPerSample = bitsPerSample / 8
                dataSize / (channels * bytesPerSample)
            }
        } catch (e: Exception) {
            0
        }
    }
}
