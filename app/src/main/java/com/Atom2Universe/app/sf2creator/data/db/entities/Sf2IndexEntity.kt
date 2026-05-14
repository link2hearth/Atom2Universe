package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a lightweight index entry for fast navigation.
 *
 * This is part of the "Polyphone-like" architecture where:
 * - The SF2 file is the source of truth
 * - This index provides fast navigation without parsing the full SF2
 * - Created at import time by scanning the SF2 headers
 *
 * The index stores minimal information:
 * - Names for display
 * - Program/bank numbers for organization
 * - Deletion/addition flags
 *
 * Full details are read from the SF2 file on demand (lazy loading).
 */
@Entity(
    tableName = "sf2_index",
    foreignKeys = [
        ForeignKey(
            entity = Sf2ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("elementType"),
        Index("originalIndex")
    ]
)
data class Sf2IndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The project this index entry belongs to */
    val projectId: Long,

    /** Type of element: "PRESET", "INSTRUMENT", "SAMPLE" */
    val elementType: String,

    /** Index in the original SF2 file (-1 for new elements) */
    val originalIndex: Int,

    /** Display name (can be modified) */
    val name: String,

    /** MIDI program number (0-127) for presets, null for other types */
    val programNumber: Int? = null,

    /** MIDI bank number (0-127) for presets, null for other types */
    val bankNumber: Int? = null,

    /** Root note for samples, null for other types */
    val rootNote: Int? = null,

    /** Sample rate for samples, null for other types */
    val sampleRate: Int? = null,

    /** Whether this element has been marked as deleted */
    val isDeleted: Boolean = false,

    /** Whether this element was added (not in original SF2) */
    val isAdded: Boolean = false
) {
    companion object {
        // Element types
        const val TYPE_PRESET = "PRESET"
        const val TYPE_INSTRUMENT = "INSTRUMENT"
        const val TYPE_SAMPLE = "SAMPLE"
    }
}
