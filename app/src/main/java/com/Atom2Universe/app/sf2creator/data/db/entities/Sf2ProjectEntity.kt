package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an SF2 project.
 *
 * In the "Polyphone-like" architecture:
 * - Each project corresponds to exactly ONE SF2 file
 * - The SF2 file is the source of truth (stored at sourceFilePath)
 * - We store only modifications (patches) in the database
 * - Export applies patches to the original SF2 file
 *
 * For projects created before v14 (isLegacyProject = true):
 * - Data is stored in the old format (full samples in DB)
 * - Will be migrated to the new format when first opened
 */
@Entity(tableName = "sf2_projects")
data class Sf2ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Display name of the project */
    val name: String,

    /**
     * Path to the SF2 source file (copied locally).
     * Null for legacy projects created from scratch before v14.
     */
    val sourceFilePath: String? = null,

    /**
     * True for projects created before v14.
     * These use the old full-data storage model.
     * Will be migrated when first opened.
     */
    val isLegacyProject: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
