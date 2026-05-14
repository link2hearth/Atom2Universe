package com.Atom2Universe.app.music.sync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for sync_play_count_deltas table.
 */
@Dao
interface SyncPlayCountDeltaDao {

    /**
     * Insert a new delta entry. If the key already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delta: SyncPlayCountDelta)

    /**
     * Get a delta entry by metadata key.
     */
    @Query("SELECT * FROM sync_play_count_deltas WHERE metadataKey = :metadataKey")
    suspend fun getByKey(metadataKey: String): SyncPlayCountDelta?

    /**
     * Get all delta entries (for upload during sync).
     */
    @Query("SELECT * FROM sync_play_count_deltas")
    suspend fun getAll(): List<SyncPlayCountDelta>

    /**
     * Increment the delta for an existing entry.
     * Call this when a track is played again before sync occurs.
     */
    @Query("""
        UPDATE sync_play_count_deltas
        SET delta = delta + 1, updatedAt = :timestamp
        WHERE metadataKey = :metadataKey
    """)
    suspend fun incrementDelta(metadataKey: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Delete a specific entry (after successful sync).
     */
    @Query("DELETE FROM sync_play_count_deltas WHERE metadataKey = :metadataKey")
    suspend fun deleteByKey(metadataKey: String)

    /**
     * Delete all entries (after successful sync upload).
     */
    @Query("DELETE FROM sync_play_count_deltas")
    suspend fun deleteAll()

    /**
     * Get the count of pending deltas.
     */
    @Query("SELECT COUNT(*) FROM sync_play_count_deltas")
    suspend fun count(): Int

    /**
     * Get the sum of all deltas (total plays since last sync).
     */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM sync_play_count_deltas")
    suspend fun getTotalDelta(): Long
}
