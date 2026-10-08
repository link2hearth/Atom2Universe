package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores global equalizer settings in Room database.
 * Single row table (id = 1 always).
 */
@Entity(tableName = "eq_settings")
data class EqSettings(
    @PrimaryKey
    val id: Int = 1,

    /** Whether the equalizer is enabled */
    val isEnabled: Boolean = false,

    /** ID of the global/default preset */
    val globalPresetId: Long = 1L // Default to "Flat" preset (id=1)
)
