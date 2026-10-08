package com.Atom2Universe.app.stats.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_summaries",
    indices = [
        Index(value = ["year", "month"]),
        Index(value = ["dateEpochDay"], unique = true)
    ]
)
data class DailySummaryEntity(
    @PrimaryKey
    val dateEpochDay: Int,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val totalDurationMs: Long,
    val musicDurationMs: Long,
    val midiDurationMs: Long,
    val radioDurationMs: Long,
    val sessionCount: Int,
    val musicDetailsJson: String?,
    val midiDetailsJson: String?,
    val radioDetailsJson: String?,
    val lastUpdatedMs: Long
)
