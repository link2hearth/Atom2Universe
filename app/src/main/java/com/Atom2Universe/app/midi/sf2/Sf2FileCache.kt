package com.Atom2Universe.app.midi.sf2

import android.content.Context
import com.Atom2Universe.app.R
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton cache for SF2 file instances.
 *
 * Every SoundFont, whatever its size, is memory-mapped: its samples stay in the file and the
 * system pages them in on demand, nothing is copied into the Java heap (the old full copy of
 * files under 150 MB froze the main thread and could run out of memory). Only the metadata
 * (presets, instruments, sample headers) is parsed, once per file.
 *
 * Right after mapping, a background thread reads the samples once so that the system keeps
 * them in its file cache: the first notes then don't wait for the storage.
 *
 * Thread-safety: every method can be called from any thread (one lock for the whole cache).
 * A file rewritten at the same path (preview SoundFonts of the SF2 creator) is detected by its
 * size and modification date and mapped again.
 */
object Sf2FileCache {

    // Préchargement : au-delà, le fichier ne tiendrait pas utilement dans le cache du système
    private const val PRELOAD_MAX_BYTES = 512L * 1024 * 1024
    private const val PRELOAD_CHUNK_BYTES = 1024 * 1024

    /** Size + modification date: a file rewritten at the same path is not the same file. */
    private data class FileStamp(val length: Long, val lastModified: Long)

    private fun stampOf(file: File) = FileStamp(file.length(), file.lastModified())

    // Cache for the mapped SF2 file
    private var cachedPath: String? = null
    private var cachedStamp: FileStamp? = null
    private var cachedFile: Sf2File? = null

    // Cache for metadata
    private var cachedMetadataPath: String? = null
    private var cachedMetadataStamp: FileStamp? = null
    private var cachedMetadata: Sf2Metadata? = null

    // Cached validation result
    private var cachedValidationPath: String? = null
    private var cachedValidationStamp: FileStamp? = null
    private var cachedValidationResult: Sf2Validator.ValidationResult? = null

    // Génération du préchargement en cours : un nouveau fichier (ou clear) arrête l'ancien
    private val preloadGeneration = AtomicInteger(0)

    private val lock = Any()

    /**
     * Get or load an Sf2File. Same as [getMemoryMapped]: every SoundFont is memory-mapped.
     */
    fun get(path: String): Sf2File? = getMemoryMapped(path)

    /**
     * Get or parse SF2 metadata (without loading sample data).
     */
    @Suppress("unused")
    fun getMetadata(sf2Path: String): Sf2Metadata? {
        synchronized(lock) {
            val file = File(sf2Path)
            if (!file.exists()) return null
            return getOrParseMetadata(sf2Path, stampOf(file))
        }
    }

    /**
     * Get SF2 file using memory-mapping (no heap memory for sample data).
     * Returns null when the file is missing, unreadable or memory runs out.
     */
    fun getMemoryMapped(sf2Path: String): Sf2File? {
        synchronized(lock) {
            val file = File(sf2Path)
            if (!file.exists()) {
                return null
            }
            val stamp = stampOf(file)

            // Même fichier déjà projeté : le partager (le lecteur et le mode pratique peuvent
            // l'utiliser en même temps)
            if (cachedPath == sf2Path && cachedStamp == stamp) {
                cachedFile?.let { return it }
            }

            return try {
                val metadata = getOrParseMetadata(sf2Path, stamp) ?: return null

                // L'ancien fichier reste valable pour qui le tient encore (projection)
                val oldFile = cachedFile
                cachedFile = null
                cachedPath = null
                cachedStamp = null
                oldFile?.close()

                val sf2File = Sf2StreamingParser().loadWithMemoryMapping(metadata)

                cachedPath = sf2Path
                cachedStamp = stamp
                cachedFile = sf2File
                startPreload(sf2Path, metadata.smplByteOffset, metadata.smplByteSize)

                sf2File
            } catch (e: Exception) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2 (mmap): $sf2Path", e)
                null
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("Sf2FileCache", "Out of memory while parsing SF2: $sf2Path", e)
                null
            }
        }
    }

    /**
     * Reads the samples once in the background so that the system keeps them in its file
     * cache. Reading through a separate file handle never touches the mapping itself.
     */
    private fun startPreload(path: String, offset: Long, size: Long) {
        val generation = preloadGeneration.incrementAndGet()
        if (size <= 0 || size > PRELOAD_MAX_BYTES) return

        Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Exception) { }
            try {
                RandomAccessFile(path, "r").use { raf ->
                    val buffer = ByteArray(PRELOAD_CHUNK_BYTES)
                    raf.seek(offset)
                    var remaining = size
                    while (remaining > 0 && preloadGeneration.get() == generation) {
                        val read = raf.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                        if (read <= 0) break
                        remaining -= read
                    }
                }
            } catch (_: Exception) { }
        }, "Sf2Preload").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Whether memory-mapping should be used for a file: always, as soon as the file exists.
     */
    fun shouldUseMmap(path: String): Boolean = File(path).exists()

    /**
     * Validate an SF2 file and return a user-friendly warning message if issues are found.
     * The message is built here, in the language of [context], from the cached validation result.
     */
    fun validateAndGetWarning(context: Context, sf2Path: String): String? {
        synchronized(lock) {
            if (!File(sf2Path).exists()) return context.getString(R.string.midi_sf2_val_missing)
            return try {
                val result = validate(sf2Path) ?: return context.getString(R.string.midi_sf2_val_unreadable)
                Sf2Validator.warningFor(context, result)
            } catch (_: Exception) {
                context.getString(R.string.midi_sf2_val_error)
            }
        }
    }

    /** Validates (once per file) and keeps the result; null when the file cannot be parsed. */
    private fun validate(sf2Path: String): Sf2Validator.ValidationResult? {
        val file = File(sf2Path)
        if (!file.exists()) return null
        val stamp = stampOf(file)
        if (cachedValidationPath == sf2Path && cachedValidationStamp == stamp) return cachedValidationResult
        val metadata = getOrParseMetadata(sf2Path, stamp) ?: return null
        val result = Sf2Validator.validate(metadata)
        cachedValidationPath = sf2Path
        cachedValidationStamp = stamp
        cachedValidationResult = result
        return result
    }

    /**
     * Get the full validation result for an SF2 file (reads the file: call it off the main thread).
     */
    fun getValidationResult(sf2Path: String): Sf2Validator.ValidationResult? {
        synchronized(lock) {
            return try { validate(sf2Path) } catch (_: Exception) { null }
        }
    }

    private fun getOrParseMetadata(sf2Path: String, stamp: FileStamp): Sf2Metadata? {
        if (cachedMetadataPath == sf2Path && cachedMetadataStamp == stamp) {
            cachedMetadata?.let { return it }
        }

        return try {
            val metadata = Sf2StreamingParser().parseMetadata(sf2Path)
            cachedMetadataPath = sf2Path
            cachedMetadataStamp = stamp
            cachedMetadata = metadata
            metadata
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    @Suppress("unused")
    fun isCached(path: String): Boolean {
        synchronized(lock) {
            return cachedPath == path && cachedFile != null
        }
    }

    @Suppress("unused")
    fun isMetadataCached(path: String): Boolean {
        synchronized(lock) {
            return cachedMetadataPath == path && cachedMetadata != null
        }
    }

    fun clear() {
        synchronized(lock) {
            preloadGeneration.incrementAndGet()
            cachedFile?.close()
            cachedPath = null
            cachedStamp = null
            cachedFile = null
            cachedMetadataPath = null
            cachedMetadataStamp = null
            cachedMetadata = null
            cachedValidationPath = null
            cachedValidationStamp = null
            cachedValidationResult = null
        }
    }

    @Suppress("unused")
    fun clearMetadataCache() {
        synchronized(lock) {
            cachedMetadataPath = null
            cachedMetadataStamp = null
            cachedMetadata = null
        }
    }

    @Suppress("unused")
    fun getCachedPath(): String? {
        synchronized(lock) {
            return cachedPath
        }
    }

    @Suppress("unused")
    fun getStats(): CacheStats {
        synchronized(lock) {
            val sf2SampleCount = cachedFile?.sampleCount ?: 0
            val sf2PresetCount = cachedFile?.presetCount ?: 0
            val sf2MemoryMB = (cachedFile?.memoryUsageBytes ?: 0L) / 1024 / 1024

            val metadataMemoryKB = cachedMetadata?.estimatedMemoryUsage?.div(1024) ?: 0

            return CacheStats(
                hasCachedFile = cachedFile != null,
                cachedPath = cachedPath,
                sf2PresetCount = sf2PresetCount,
                sf2SampleCount = sf2SampleCount,
                sf2MemoryMB = sf2MemoryMB,
                hasMetadataCache = cachedMetadata != null,
                metadataPath = cachedMetadataPath,
                metadataMemoryKB = metadataMemoryKB
            )
        }
    }

    data class CacheStats(
        val hasCachedFile: Boolean,
        val cachedPath: String?,
        val sf2PresetCount: Int,
        val sf2SampleCount: Int,
        val sf2MemoryMB: Long,
        val hasMetadataCache: Boolean,
        val metadataPath: String?,
        val metadataMemoryKB: Long
    ) {
        override fun toString(): String {
            return buildString {
                append("Sf2FileCache Stats:\n")
                if (hasCachedFile) {
                    append("  SF2: $cachedPath\n")
                    append("       $sf2PresetCount presets, $sf2SampleCount samples, ~${sf2MemoryMB}MB heap\n")
                } else {
                    append("  SF2: (none)\n")
                }
                if (hasMetadataCache) {
                    append("  Metadata: $metadataPath (~${metadataMemoryKB}KB)\n")
                } else {
                    append("  Metadata: (none)\n")
                }
            }
        }
    }
}
