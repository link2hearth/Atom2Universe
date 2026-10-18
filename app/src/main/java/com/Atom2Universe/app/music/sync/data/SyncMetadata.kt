package com.Atom2Universe.app.music.sync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table storing cloud sync state for this device.
 *
 * Contains:
 * - Unique device identifier (generated once, never changes)
 * - Last successful sync timestamp
 * - Date of last uploaded delta file
 * - User's sync preference
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey
    val id: Int = 1,
    val deviceId: String,
    val deviceName: String,
    val lastSyncTimestamp: Long = 0,
    val lastUploadedDeltaDate: String? = null,
    val syncEnabled: Boolean = false,
    /**
     * Flag indiquant si les baseline deltas ont déjà été créés.
     * Empêche la recréation multiple des deltas à chaque sync,
     * ce qui causerait une multiplication des compteurs.
     */
    val baselineDeltasCreated: Boolean = false
)
