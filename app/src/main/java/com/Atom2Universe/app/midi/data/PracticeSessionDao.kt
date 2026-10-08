package com.Atom2Universe.app.midi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for practice session history.
 */
@Dao
interface PracticeSessionDao {

    /**
     * Insert a new practice session result
     */
    @Insert
    suspend fun insert(session: PracticeSessionResult): Long

    /**
     * Get all practice sessions ordered by most recent first
     */
    @Query("SELECT * FROM practice_sessions ORDER BY timestampMs DESC")
    fun getAllSessions(): Flow<List<PracticeSessionResult>>

    /**
     * Get sessions for a specific track
     */
    @Query("SELECT * FROM practice_sessions WHERE trackFilePath = :trackPath ORDER BY timestampMs DESC")
    fun getSessionsForTrack(trackPath: String): Flow<List<PracticeSessionResult>>

    /**
     * Get the best score for a specific track
     */
    @Query("SELECT MAX(score) FROM practice_sessions WHERE trackFilePath = :trackPath")
    suspend fun getBestScoreForTrack(trackPath: String): Long?

    /**
     * Get the best grade for a specific track
     */
    @Query("SELECT grade FROM practice_sessions WHERE trackFilePath = :trackPath ORDER BY " +
            "CASE grade " +
            "WHEN 'S' THEN 1 " +
            "WHEN 'A' THEN 2 " +
            "WHEN 'B' THEN 3 " +
            "WHEN 'C' THEN 4 " +
            "WHEN 'D' THEN 5 " +
            "ELSE 6 END " +
            "LIMIT 1")
    suspend fun getBestGradeForTrack(trackPath: String): String?

    /**
     * Get recent sessions (last N)
     */
    @Query("SELECT * FROM practice_sessions ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<PracticeSessionResult>

    /**
     * Get total number of sessions
     */
    @Query("SELECT COUNT(*) FROM practice_sessions")
    suspend fun getSessionCount(): Int

    /**
     * Delete all sessions (for testing/reset)
     */
    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAll()

    /**
     * Delete sessions older than a given timestamp
     */
    @Query("DELETE FROM practice_sessions WHERE timestampMs < :beforeTimestamp")
    suspend fun deleteSessionsBefore(beforeTimestamp: Long): Int

    /**
     * Get sessions between two timestamps (for stats)
     */
    @Query("SELECT * FROM practice_sessions WHERE timestampMs >= :startTime AND timestampMs <= :endTime ORDER BY timestampMs DESC")
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<PracticeSessionResult>
}
