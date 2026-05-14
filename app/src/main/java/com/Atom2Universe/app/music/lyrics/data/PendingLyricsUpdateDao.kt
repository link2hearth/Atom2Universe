package com.Atom2Universe.app.music.lyrics.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object pour la file d'attente des écritures USLT.
 */
@Dao
interface PendingLyricsUpdateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(update: PendingLyricsUpdate)

    @Query("SELECT * FROM pending_lyrics_updates WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): PendingLyricsUpdate?

    @Query("SELECT * FROM pending_lyrics_updates ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingLyricsUpdate>

    @Query("SELECT * FROM pending_lyrics_updates WHERE filePath != :excludeFilePath ORDER BY createdAt ASC")
    suspend fun getAllExcept(excludeFilePath: String): List<PendingLyricsUpdate>

    @Query("DELETE FROM pending_lyrics_updates WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("UPDATE pending_lyrics_updates SET lastAttempt = :timestamp, attemptCount = attemptCount + 1 WHERE filePath = :filePath")
    suspend fun updateAttemptInfo(filePath: String, timestamp: Long)
}
