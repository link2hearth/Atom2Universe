package com.Atom2Universe.app.midi.sf2

import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Interface for accessing SF2 sample data.
 * Allows different implementations: in-memory array, memory-mapped file, or hybrid.
 */
interface SampleDataProvider : Closeable {
    /**
     * Get a single sample at the given index, normalized to float (-1.0 to 1.0)
     */
    fun getSample(index: Long): Float

    /**
     * Get a raw sample value (16-bit signed)
     */
    fun getSampleRaw(index: Long): Short

    /**
     * Number of samples available
     */
    val sampleCount: Long

    /**
     * Memory usage in bytes (0 for mmap since it doesn't use heap)
     */
    val memoryUsageBytes: Long

    /**
     * Whether this provider uses memory-mapping
     */
    val isMemoryMapped: Boolean
}

/**
 * Sample data provider backed by an in-memory ShortArray.
 * Fast access, but uses heap memory.
 */
class ArraySampleProvider(
    private val data: ShortArray
) : SampleDataProvider {

    override fun getSample(index: Long): Float {
        val i = index.toInt()
        return if (i >= 0 && i < data.size) {
            data[i] / 32768f
        } else {
            0f
        }
    }

    override fun getSampleRaw(index: Long): Short {
        val i = index.toInt()
        return if (i >= 0 && i < data.size) {
            data[i]
        } else {
            0
        }
    }

    override val sampleCount: Long get() = data.size.toLong()
    override val memoryUsageBytes: Long get() = data.size.toLong() * 2
    override val isMemoryMapped: Boolean get() = false

    override fun close() {
        // Nothing to close for array
    }
}

/**
 * Sample data provider using memory-mapped file access.
 * Minimal heap usage, OS handles caching.
 * Ideal for very large SF2 files (500MB+).
 *
 * Le fichier est fermé dès que la projection est faite : une projection mémoire reste
 * valide après la fermeture du fichier et n'est libérée qu'une fois plus référencée.
 * [close] ne coupe donc rien : un même Sf2File est partagé par Sf2FileCache entre le
 * lecteur, le mode pratique et l'export, et la fermeture par l'un rendait muets les autres.
 */
class MemoryMappedSampleProvider(
    filePath: String,
    smplByteOffset: Long,
    private val smplByteSize: Long
) : SampleDataProvider {

    private val mappedBuffer: MappedByteBuffer? = try {
        RandomAccessFile(File(filePath), "r").use { file ->
            file.channel.map(FileChannel.MapMode.READ_ONLY, smplByteOffset, smplByteSize)
                .also { it.order(ByteOrder.LITTLE_ENDIAN) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to memory-map SF2 file: $filePath", e)
        null
    }

    companion object {
        private const val TAG = "MemoryMappedSampleProvider"
    }

    override fun getSample(index: Long): Float {
        val buffer = mappedBuffer ?: return 0f
        // BUG FIX 1.5: Utiliser Long pour éviter overflow sur SF2 > 2GB
        // (index * 2) peut dépasser Int.MAX_VALUE pour index > 1073741823
        val byteIndexLong = index * 2
        // Vérifier que l'index est dans les limites de Int et du buffer avant conversion
        if (byteIndexLong < 0 || byteIndexLong > Int.MAX_VALUE - 1) {
            return 0f
        }
        val byteIndex = byteIndexLong.toInt()
        return if (byteIndex >= 0 && byteIndex + 1 < buffer.capacity()) {
            buffer.getShort(byteIndex) / 32768f
        } else {
            0f
        }
    }

    override fun getSampleRaw(index: Long): Short {
        val buffer = mappedBuffer ?: return 0
        // BUG FIX 1.5: Utiliser Long pour éviter overflow sur SF2 > 2GB
        val byteIndexLong = index * 2
        if (byteIndexLong < 0 || byteIndexLong > Int.MAX_VALUE - 1) {
            return 0
        }
        val byteIndex = byteIndexLong.toInt()
        return if (byteIndex >= 0 && byteIndex + 1 < buffer.capacity()) {
            buffer.getShort(byteIndex)
        } else {
            0
        }
    }

    override val sampleCount: Long get() = smplByteSize / 2
    override val memoryUsageBytes: Long get() = 0  // mmap doesn't use heap
    override val isMemoryMapped: Boolean get() = true

    override fun close() {
        // Rien à fermer : voir la documentation de la classe
    }
}
