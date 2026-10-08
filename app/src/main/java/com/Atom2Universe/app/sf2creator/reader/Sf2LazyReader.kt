package com.Atom2Universe.app.sf2creator.reader

import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * Lazy reader for SF2 files in the "Polyphone-like" architecture.
 *
 * This class wraps Sf2Reader and provides:
 * - Cached access to parsed SF2 data
 * - On-demand sample audio extraction
 * - Lightweight list accessors for navigation
 *
 * The SF2 file is the source of truth - we read from it on demand
 * rather than storing everything in the database.
 */
class Sf2LazyReader(private val sf2FilePath: String) {

    companion object {
        private const val TAG = "Sf2LazyReader"
        private const val AUDIO_CACHE_SIZE = 10 // Number of samples to cache
    }

    private val sf2File = File(sf2FilePath)
    private val reader = Sf2Reader()

    // Cached parse result (parsed once, reused)
    private var cachedParseResult: Sf2ParseResult? = null

    // LRU cache for recently extracted sample audio
    private val audioCache = LruCache<Int, ShortArray>(AUDIO_CACHE_SIZE)

    /**
     * Check if the SF2 file exists and is readable.
     */
    fun isValid(): Boolean = sf2File.exists() && sf2File.canRead()

    /**
     * Get the parsed SF2 data, parsing if necessary.
     * Returns null if parsing fails.
     */
    fun getParseResult(): Sf2ParseResult? {
        if (cachedParseResult == null) {
            cachedParseResult = reader.parse(sf2File)
            if (cachedParseResult == null) {
                Log.e(TAG, "Failed to parse SF2 file: $sf2FilePath")
            }
        }
        return cachedParseResult
    }

    /**
     * Force re-parsing the SF2 file (use if file has been modified externally).
     */
    fun invalidateCache() {
        cachedParseResult = null
        audioCache.evictAll()
    }

    // ==================== Lightweight List Accessors ====================

    /**
     * Get list of presets for quick navigation.
     */
    fun getPresetList(): List<PresetInfo> {
        val result = getParseResult() ?: return emptyList()
        return result.presets.map { preset ->
            PresetInfo(
                index = preset.index,
                name = preset.name,
                programNumber = preset.programNumber,
                bankNumber = preset.bankNumber,
                instrumentCount = preset.getInstrumentCount(),
                sampleCount = preset.getSampleCount()
            )
        }
    }

    /**
     * Get list of instruments for quick navigation.
     */
    fun getInstrumentList(): List<InstrumentInfo> {
        val result = getParseResult() ?: return emptyList()
        return result.instruments.map { instrument ->
            InstrumentInfo(
                index = instrument.index,
                name = instrument.name,
                zoneCount = instrument.zones.size
            )
        }
    }

    /**
     * Get list of samples for quick navigation.
     */
    fun getSampleList(): List<SampleInfo> {
        val result = getParseResult() ?: return emptyList()
        return result.samples.map { sample ->
            SampleInfo(
                index = sample.index,
                name = sample.name,
                sampleRate = sample.sampleRate,
                rootNote = sample.originalPitch,
                durationSeconds = sample.getDurationSeconds(),
                hasLoop = sample.hasLoop()
            )
        }
    }

    // ==================== Detail Accessors ====================

    /**
     * Get full preset details by index.
     */
    fun getPresetDetails(index: Int): Sf2ParsedPreset? {
        val result = getParseResult() ?: return null
        return result.presets.getOrNull(index)
    }

    /**
     * Get full instrument details by index.
     */
    fun getInstrumentDetails(index: Int): Sf2ParsedInstrument? {
        val result = getParseResult() ?: return null
        return result.instruments.getOrNull(index)
    }

    /**
     * Get full sample details by index.
     */
    fun getSampleDetails(index: Int): Sf2ParsedSample? {
        val result = getParseResult() ?: return null
        return result.samples.getOrNull(index)
    }

    // ==================== Audio Extraction ====================

    /**
     * Extract audio data for a sample.
     * Uses LRU cache to avoid repeated reads for frequently accessed samples.
     *
     * @param sampleIndex Index of the sample in the SF2 file
     * @return Audio data as ShortArray, or null if extraction fails
     */
    fun extractSampleAudio(sampleIndex: Int): ShortArray? {
        // Check cache first
        audioCache.get(sampleIndex)?.let { return it }

        // Get sample info
        val result = getParseResult() ?: return null
        val sample = result.samples.getOrNull(sampleIndex) ?: return null

        // Extract audio
        val audio = reader.extractSampleAudio(sample)
        if (audio != null) {
            audioCache.put(sampleIndex, audio)
        }
        return audio
    }

    /**
     * Extract audio data for a sample by name.
     */
    fun extractSampleAudioByName(name: String): ShortArray? {
        val result = getParseResult() ?: return null
        val sample = result.samples.find { it.name == name } ?: return null
        return extractSampleAudio(sample.index)
    }

    // ==================== Chunk Information ====================

    /**
     * Scan chunks in the SF2 file.
     * Used for hybrid passthrough export.
     */
    fun scanChunks(): Map<String, ChunkScanInfo>? {
        return reader.scanChunks(sf2File)
    }

    /**
     * Compute hash of the entire SF2 file.
     */
    fun computeFileHash(): String? {
        return reader.computeFileHash(sf2File)
    }

    // ==================== Info Accessors ====================

    /**
     * Get SF2 file info (name, engine, comment).
     */
    fun getInfo(): Sf2Info? {
        return getParseResult()?.info
    }

    /**
     * Get the file path this reader is associated with.
     */
    fun getFilePath(): String = sf2FilePath

    /**
     * Get file size in bytes.
     */
    fun getFileSize(): Long = if (sf2File.exists()) sf2File.length() else 0
}

// ==================== Lightweight Info Classes ====================

/**
 * Lightweight preset information for navigation.
 */
data class PresetInfo(
    val index: Int,
    val name: String,
    val programNumber: Int,
    val bankNumber: Int,
    val instrumentCount: Int,
    val sampleCount: Int
)

/**
 * Lightweight instrument information for navigation.
 */
data class InstrumentInfo(
    val index: Int,
    val name: String,
    val zoneCount: Int
)

/**
 * Lightweight sample information for navigation.
 */
data class SampleInfo(
    val index: Int,
    val name: String,
    val sampleRate: Int,
    val rootNote: Int,
    val durationSeconds: Float,
    val hasLoop: Boolean
)
