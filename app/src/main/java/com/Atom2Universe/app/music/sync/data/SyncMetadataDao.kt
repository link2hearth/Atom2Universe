package com.Atom2Universe.app.music.sync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for sync_metadata table (single-row).
 */
@Dao
interface SyncMetadataDao {

    /**
     * Insert or replace the sync metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: SyncMetadata)

    /**
     * Get the sync metadata (always id=1).
     */
    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    suspend fun get(): SyncMetadata?

    /**
     * Update the last sync timestamp.
     */
    @Query("UPDATE sync_metadata SET lastSyncTimestamp = :timestamp WHERE id = 1")
    suspend fun updateLastSyncTimestamp(timestamp: Long)

    /**
     * Update the last uploaded delta date.
     */
    @Query("UPDATE sync_metadata SET lastUploadedDeltaDate = :date WHERE id = 1")
    suspend fun updateLastUploadedDeltaDate(date: String)

    /**
     * Enable or disable sync.
     */
    @Query("UPDATE sync_metadata SET syncEnabled = :enabled WHERE id = 1")
    suspend fun setSyncEnabled(enabled: Boolean)

    /**
     * Check if sync is enabled.
     */
    @Query("SELECT syncEnabled FROM sync_metadata WHERE id = 1")
    suspend fun isSyncEnabled(): Boolean?

    /**
     * Get the device ID.
     */
    @Query("SELECT deviceId FROM sync_metadata WHERE id = 1")
    suspend fun getDeviceId(): String?

    /**
     * Get the last sync timestamp.
     */
    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE id = 1")
    suspend fun getLastSyncTimestamp(): Long?

    /**
     * Check if baseline deltas have been created.
     */
    @Query("SELECT baselineDeltasCreated FROM sync_metadata WHERE id = 1")
    suspend fun areBaselineDeltasCreated(): Boolean?

    /**
     * Mark baseline deltas as created (prevents recreation on each sync).
     */
    @Query("UPDATE sync_metadata SET baselineDeltasCreated = 1 WHERE id = 1")
    suspend fun markBaselineDeltasCreated()

    /**
     * Reset the baseline deltas flag (allows recreation after a reset).
     */
    @Query("UPDATE sync_metadata SET baselineDeltasCreated = 0 WHERE id = 1")
    suspend fun resetBaselineDeltasCreated()
}
