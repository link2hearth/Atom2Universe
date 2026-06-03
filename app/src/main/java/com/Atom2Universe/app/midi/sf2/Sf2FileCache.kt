package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer
import java.io.File

/**
 * Singleton cache for SF2 file instances with streaming support.
 *
 * SF2 files can be very large (100MB+), so this cache provides:
 * 1. Standard full loading for small files (<100MB)
 * 2. Streaming/selective loading for large files (100MB+)
 *
 * For streaming loading:
 * - Metadata is parsed first (presets, instruments, sample headers)
 * - Sample data is loaded only for instruments used in the MIDI file
 * - Memory savings of 80-95% for typical MIDI files
 */
object Sf2FileCache {

    // Threshold for streaming loading (100MB)
    private const val STREAMING_THRESHOLD_BYTES = 100L * 1024 * 1024

    // Cache for full-loaded SF2 files
    private var cachedPath: String? = null
    private var cachedFile: Sf2File? = null

    // Cache for streaming metadata
    private var cachedMetadataPath: String? = null
    private var cachedMetadata: Sf2Metadata? = null

    // Track what instruments are loaded in streaming mode
    private var loadedPresetsKey: String? = null

    // Cached validation result
    private var cachedValidationPath: String? = null
    private var cachedValidationResult: Sf2Validator.ValidationResult? = null
    private var cachedValidationWarning: String? = null
    private var cachedValidationDone: Boolean = false

    private val lock = Any()

    /**
     * Get or parse an Sf2File (full loading).
     */
    fun get(path: String): Sf2File? {
        synchronized(lock) {
            if (cachedPath == path && cachedFile != null) {
                return cachedFile
            }

            val file = File(path)
            if (!file.exists()) {
                return null
            }

            // Fermer l'ancien SF2 AVANT de tenter le parse pour éviter fuite mémoire
            // en cas d'exception pendant le parsing
            val oldFile = cachedFile
            cachedFile = null
            cachedPath = null
            oldFile?.close()

            return try {
                val parser = Sf2Parser()
                val sf2File = parser.parse(file)

                cachedPath = path
                cachedFile = sf2File

                sf2File
            } catch (e: Exception) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2: ${file.absolutePath}", e)
                null
            }
        }
    }

    /**
     * Get SF2 file with streaming/selective loading based on MIDI requirements.
     */
    fun getStreaming(
        sf2Path: String,
        requiredInstruments: MidiFileAnalyzer.RequiredInstruments
    ): Sf2File? {
        synchronized(lock) {
            val file = File(sf2Path)
            if (!file.exists()) {
                return null
            }

            val sizeBytes = file.length()

            if (sizeBytes < STREAMING_THRESHOLD_BYTES) {
                return get(sf2Path)
            }

            try {
                val parser = Sf2StreamingParser()

                val metadata = getOrParseMetadata(sf2Path, parser) ?: return null

                val presetsKey = buildPresetsKey(sf2Path, requiredInstruments)

                if (cachedPath == sf2Path && cachedFile != null && loadedPresetsKey == presetsKey) {
                    return cachedFile
                }

                // Fermer l'ancien SF2 AVANT le parse pour éviter fuite mémoire en cas d'exception
                val oldFile = cachedFile
                cachedFile = null
                cachedPath = null
                loadedPresetsKey = null
                oldFile?.close()

                val sf2File = parser.loadSamplesForPresets(metadata, requiredInstruments.presets)

                cachedPath = sf2Path
                cachedFile = sf2File
                loadedPresetsKey = presetsKey

                return sf2File

            } catch (e: Sf2ParseException) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2 (streaming): $sf2Path", e)
                return null
            } catch (e: Exception) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2 (streaming): $sf2Path", e)
                return null
            }
        }
    }

    /**
     * Get or parse SF2 metadata (without loading sample data).
     */
    @Suppress("unused")
    fun getMetadata(sf2Path: String): Sf2Metadata? {
        synchronized(lock) {
            return getOrParseMetadata(sf2Path, Sf2StreamingParser())
        }
    }

    /**
     * Get SF2 file using full memory-mapping (zero RAM for sample data).
     */
    fun getMemoryMapped(sf2Path: String): Sf2File? {
        synchronized(lock) {
            val file = File(sf2Path)
            if (!file.exists()) {
                return null
            }

            try {
                val parser = Sf2StreamingParser()

                val metadata = getOrParseMetadata(sf2Path, parser) ?: return null

                // Fermer l'ancien SF2 AVANT le parse pour éviter fuite mémoire en cas d'exception
                val oldFile = cachedFile
                cachedFile = null
                cachedPath = null
                loadedPresetsKey = null
                oldFile?.close()

                val sf2File = parser.loadWithMemoryMapping(metadata)

                cachedPath = sf2Path
                cachedFile = sf2File
                loadedPresetsKey = "mmap:$sf2Path"

                return sf2File

            } catch (e: Sf2ParseException) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2 (mmap): $sf2Path", e)
                return null
            } catch (e: Exception) {
                android.util.Log.e("Sf2FileCache", "Failed to parse SF2 (mmap): $sf2Path", e)
                return null
            }
        }
    }

    private const val MMAP_RECOMMENDED_THRESHOLD_BYTES = 150L * 1024 * 1024

    /**
     * Check if memory-mapping should be used for a file.
     */
    fun shouldUseMmap(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.length() > MMAP_RECOMMENDED_THRESHOLD_BYTES
    }

    /**
     * Validate an SF2 file and return a user-friendly warning message if issues are found.
     */
    fun validateAndGetWarning(sf2Path: String): String? {
        synchronized(lock) {
            if (cachedValidationPath == sf2Path && cachedValidationDone) {
                return cachedValidationWarning
            }

            val file = File(sf2Path)
            if (!file.exists()) {
                return "Fichier SF2 introuvable"
            }

            try {
                val parser = Sf2StreamingParser()
                val metadata = getOrParseMetadata(sf2Path, parser)
                if (metadata == null) {
                    return "Impossible de lire le fichier SF2. Il pourrait etre corrompu."
                }

                val result = Sf2Validator.validate(metadata)
                val warning = Sf2Validator.quickCheck(metadata)

                cachedValidationPath = sf2Path
                cachedValidationResult = result
                cachedValidationWarning = warning
                cachedValidationDone = true

                return warning

            } catch (_: Exception) {
                return "Erreur lors de la validation du fichier SF2"
            }
        }
    }

    /**
     * Get the full validation result for an SF2 file.
     */
    fun getValidationResult(sf2Path: String): Sf2Validator.ValidationResult? {
        synchronized(lock) {
            if (cachedValidationPath == sf2Path) {
                return cachedValidationResult
            }
            validateAndGetWarning(sf2Path)
            return cachedValidationResult
        }
    }

    /**
     * Get a detailed validation report for debugging.
     */
    fun getValidationReport(sf2Path: String): String {
        val result = getValidationResult(sf2Path)
        return result?.getDetailedReport() ?: "Validation not available"
    }

    private fun getOrParseMetadata(sf2Path: String, parser: Sf2StreamingParser): Sf2Metadata? {
        if (cachedMetadataPath == sf2Path && cachedMetadata != null) {
            return cachedMetadata
        }

        return try {
            val metadata = parser.parseMetadata(sf2Path)
            cachedMetadataPath = sf2Path
            cachedMetadata = metadata
            metadata
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPresetsKey(
        sf2Path: String,
        instruments: MidiFileAnalyzer.RequiredInstruments
    ): String {
        val presetKeys = instruments.presets
            .sortedWith(compareBy({ it.bank }, { it.program }))
            .joinToString(",") { "${it.bank}:${it.program}" }
        return "$sf2Path|$presetKeys"
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

    @Suppress("unused")
    fun getEstimatedMemoryMB(path: String): Long {
        val file = File(path)
        if (!file.exists()) return 0
        return file.length() * 2 / 1024 / 1024
    }

    fun shouldUseStreaming(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.length() > STREAMING_THRESHOLD_BYTES
    }

    fun clear() {
        synchronized(lock) {
            cachedFile?.close()
            cachedPath = null
            cachedFile = null
            cachedMetadataPath = null
            cachedMetadata = null
            loadedPresetsKey = null
            cachedValidationPath = null
            cachedValidationResult = null
            cachedValidationWarning = null
            cachedValidationDone = false
        }
    }

    @Suppress("unused")
    fun clearMetadataCache() {
        synchronized(lock) {
            cachedMetadataPath = null
            cachedMetadata = null
        }
    }

    @Suppress("unused")
    fun getCachedPath(): String? {
        synchronized(lock) {
            return cachedPath
        }
    }

    fun getStats(): CacheStats {
        synchronized(lock) {
            val sf2SampleCount = cachedFile?.sampleCount ?: 0
            val sf2PresetCount = cachedFile?.presetCount ?: 0
            val sf2MemoryMB = sf2SampleCount.toLong() * 2 / 1024 / 1024

            val metadataMemoryKB = cachedMetadata?.estimatedMemoryUsage?.div(1024) ?: 0

            return CacheStats(
                hasCachedFile = cachedFile != null,
                cachedPath = cachedPath,
                sf2PresetCount = sf2PresetCount,
                sf2SampleCount = sf2SampleCount,
                sf2MemoryMB = sf2MemoryMB,
                hasMetadataCache = cachedMetadata != null,
                metadataPath = cachedMetadataPath,
                metadataMemoryKB = metadataMemoryKB,
                isStreamingLoaded = loadedPresetsKey != null
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
        val metadataMemoryKB: Long,
        val isStreamingLoaded: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                append("Sf2FileCache Stats:\n")
                if (hasCachedFile) {
                    append("  SF2: $cachedPath\n")
                    append("       $sf2PresetCount presets, $sf2SampleCount samples, ~${sf2MemoryMB}MB\n")
                    if (isStreamingLoaded) {
                        append("       (streaming loaded)\n")
                    }
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
