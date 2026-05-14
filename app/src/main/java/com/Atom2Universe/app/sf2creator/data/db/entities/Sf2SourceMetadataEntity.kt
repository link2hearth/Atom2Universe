package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Room entity storing metadata about the original SF2 source file.
 * Used for the "hybrid passthrough" export strategy to preserve unchanged chunks.
 *
 * When a SF2 file is imported:
 * 1. The original file is copied to app storage (sf2_sources/{projectId}.sf2)
 * 2. Chunk offsets/sizes/hashes are recorded in chunkRegistry (JSON)
 * 3. At export, unchanged chunks can be copied directly from the source
 *
 * This enables:
 * - Lossless round-trip for unmodified data
 * - Faster exports (no need to re-encode unchanged audio)
 * - Preservation of any unknown/unsupported SF2 data
 */
@Entity(
    tableName = "sf2_source_metadata",
    foreignKeys = [
        ForeignKey(
            entity = Sf2ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Sf2SourceMetadataEntity(
    @PrimaryKey
    val projectId: Long,

    /**
     * Path to the copied SF2 source file in app storage.
     * Format: {context.filesDir}/sf2_sources/{projectId}.sf2
     * Null if the project was created from scratch (not imported).
     */
    val sourceFilePath: String? = null,

    /**
     * SHA-256 hash of the original source file.
     * Used to verify the file hasn't been corrupted or modified externally.
     */
    val sourceFileHash: String? = null,

    /**
     * JSON-encoded map of chunk information.
     * Format: Map<String, ChunkInfo> where key is chunk identifier.
     *
     * Example:
     * {
     *   "smpl": {"chunkId": "smpl", "offset": 12345, "size": 1000000, "contentHash": "abc123...", "isModified": false},
     *   "shdr": {"chunkId": "shdr", "offset": 1012345, "size": 4600, "contentHash": "def456...", "isModified": false}
     * }
     */
    val chunkRegistry: String = "{}",

    /**
     * JSON-encoded map of sample index mappings for hybrid passthrough.
     * Maps our internal sample names/IDs to their original SF2 sample header info.
     *
     * Format: Map<String, SampleMappingInfo> where key is sample name (truncated to 20 chars).
     *
     * Example:
     * {
     *   "Piano_C4": {
     *     "name": "Piano_C4",
     *     "originalIndex": 0,
     *     "startOffset": 0,
     *     "endOffset": 44100,
     *     "loopStart": 1000,
     *     "loopEnd": 43000,
     *     "sampleRate": 44100,
     *     "originalPitch": 60,
     *     "pitchCorrection": 0
     *   }
     * }
     *
     * This allows the hybrid writer to reconstruct shdr entries with correct
     * sample offsets pointing into the passthrough smpl chunk.
     */
    val sampleIndexMapping: String = "{}",

    /**
     * Timestamp when the SF2 was imported.
     */
    val importedAt: Long = System.currentTimeMillis()
)

/**
 * Mapping information for a single sample from the original SF2.
 * Serialized to JSON and stored in sampleIndexMapping.
 *
 * This preserves the exact sample header (shdr) information needed
 * to reconstruct pdta when using smpl passthrough.
 */
data class SampleMappingInfo(
    /**
     * Sample name (up to 20 characters in SF2 spec).
     */
    val name: String,

    /**
     * Original index in the SF2 file's shdr chunk.
     * Used to maintain sample ordering.
     */
    val originalIndex: Int,

    /**
     * Start offset in the smpl chunk (in samples, not bytes).
     */
    val startOffset: Long,

    /**
     * End offset in the smpl chunk (in samples).
     * This is one sample past the last valid sample.
     */
    val endOffset: Long,

    /**
     * Loop start offset (in samples, relative to start of smpl chunk).
     */
    val loopStart: Long,

    /**
     * Loop end offset (in samples, relative to start of smpl chunk).
     */
    val loopEnd: Long,

    /**
     * Sample rate in Hz.
     */
    val sampleRate: Int,

    /**
     * Original pitch (MIDI note number, 60 = middle C).
     */
    val originalPitch: Int,

    /**
     * Pitch correction in cents.
     */
    val pitchCorrection: Int,

    /**
     * Sample link for stereo samples (0 for mono).
     */
    val sampleLink: Int = 0,

    /**
     * Sample type (1 = mono, 2 = right, 4 = left, etc.).
     */
    val sampleType: Int = 1
)

/**
 * Information about a single SF2 chunk.
 * Serialized to JSON and stored in chunkRegistry.
 */
data class ChunkInfo(
    /**
     * Chunk identifier (e.g., "smpl", "shdr", "phdr", "pbag", etc.)
     */
    val chunkId: String,

    /**
     * Byte offset from the start of the SF2 file where this chunk's data begins.
     * Note: This is the offset of the data, not the chunk header.
     */
    val offset: Long,

    /**
     * Size of the chunk data in bytes (not including the 8-byte header).
     */
    val size: Long,

    /**
     * SHA-256 hash of the chunk content.
     * Used to verify chunk integrity and detect modifications.
     */
    val contentHash: String,

    /**
     * Whether this chunk has been modified since import.
     * When true, the chunk must be reconstructed at export time.
     * When false, the chunk can be copied directly from source file.
     */
    val isModified: Boolean = false
)

/**
 * Constants for modification tracking flags.
 * These are stored in the modificationFlags bitmap field of entities.
 */
object ModificationFlags {
    /** Audio data was modified (trim, normalize, crossfade, etc.) */
    const val MOD_FLAG_AUDIO = 1

    /** Synthesis parameters were modified (envelope, filter, etc.) */
    const val MOD_FLAG_PARAMS = 2

    /** Name was changed */
    const val MOD_FLAG_NAME = 4

    /** Entity was added after import (not in original SF2) */
    const val MOD_FLAG_ADDED = 8

    /** Entity was marked for deletion */
    const val MOD_FLAG_DELETED = 16

    /** Key/velocity range was modified */
    const val MOD_FLAG_RANGE = 32

    /** Loop points were modified */
    const val MOD_FLAG_LOOP = 64

    /** Modulators were modified */
    const val MOD_FLAG_MODULATORS = 128
}
