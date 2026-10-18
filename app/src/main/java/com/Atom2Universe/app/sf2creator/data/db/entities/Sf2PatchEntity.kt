package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a patch (modification) to the original SF2 file.
 *
 * This is part of the "Polyphone-like" architecture where:
 * - The SF2 file is the source of truth
 * - We store only modifications (patches/deltas) in the database
 * - Export applies patches to the original file
 *
 * Patch types:
 * - MODIFY_GEN: Modify a generator value
 * - MODIFY_NAME: Rename an element
 * - DELETE: Mark an element as deleted
 * - ADD: Add a new element (stores full data)
 */
@Entity(
    tableName = "sf2_patches",
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
        Index("targetType"),
        Index("targetIndex")
    ]
)
data class Sf2PatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The project this patch belongs to */
    val projectId: Long,

    /** Type of element being modified: "PRESET", "INSTRUMENT", "SAMPLE" */
    val targetType: String,

    /** Index of the element in the original SF2 file (-1 for new elements) */
    val targetIndex: Int,

    /** Type of patch: "MODIFY_GEN", "MODIFY_NAME", "DELETE", "ADD" */
    val patchType: String,

    /** JSON data containing the modification details */
    val patchData: String,

    /** Timestamp when this patch was created */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Target types
        const val TARGET_PRESET = "PRESET"
        const val TARGET_INSTRUMENT = "INSTRUMENT"
        const val TARGET_SAMPLE = "SAMPLE"

        // Patch types
        const val PATCH_MODIFY_GEN = "MODIFY_GEN"
        const val PATCH_MODIFY_NAME = "MODIFY_NAME"
        const val PATCH_DELETE = "DELETE"
        const val PATCH_ADD = "ADD"
    }
}
