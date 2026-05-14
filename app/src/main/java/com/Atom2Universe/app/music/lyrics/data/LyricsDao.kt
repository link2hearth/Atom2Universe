package com.Atom2Universe.app.music.lyrics.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object pour le cache des paroles.
 */
@Dao
interface LyricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lyrics: LyricsEntity)

    @Query("SELECT * FROM lyrics_cache WHERE metadataKey = :key")
    suspend fun getByKey(key: String): LyricsEntity?

    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: Long): LyricsEntity?

    @Query("DELETE FROM lyrics_cache WHERE metadataKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM lyrics_cache WHERE fetchedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun count(): Int

    @Query("UPDATE lyrics_cache SET isSyncedToFile = :synced WHERE metadataKey = :key")
    suspend fun updateSyncStatus(key: String, synced: Boolean)

    /**
     * Returns all lyrics entries (for cloud sync export).
     */
    @Query("SELECT * FROM lyrics_cache")
    suspend fun getAll(): List<LyricsEntity>

    @Query("SELECT * FROM lyrics_cache WHERE lastModified >= :since AND noLyricsFound = 0 AND lyrics != ''")
    suspend fun getModifiedSince(since: Long): List<LyricsEntity>

    @Query("SELECT MAX(lastModified) FROM lyrics_cache WHERE noLyricsFound = 0 AND lyrics != ''")
    suspend fun getLatestModifiedTimestamp(): Long?
}
