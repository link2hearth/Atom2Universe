package com.Atom2Universe.app.stats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: DailySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummaries(summaries: List<DailySummaryEntity>)

    @Query("SELECT dayOfMonth FROM daily_summaries WHERE year = :year AND month = :month")
    suspend fun getActiveDaysInMonth(year: Int, month: Int): List<Int>

    @Query("SELECT * FROM daily_summaries WHERE dateEpochDay = :epochDay")
    suspend fun getSummaryForDay(epochDay: Int): DailySummaryEntity?

    @Query("SELECT COUNT(*) FROM daily_summaries")
    suspend fun getSummaryCount(): Int

    @Query("DELETE FROM daily_summaries WHERE dateEpochDay = :epochDay")
    suspend fun deleteSummaryForDay(epochDay: Int)
}
