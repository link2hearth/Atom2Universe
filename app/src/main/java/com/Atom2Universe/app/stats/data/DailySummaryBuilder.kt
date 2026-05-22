package com.Atom2Universe.app.stats.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailySummaryBuilder(
    private val usageSessionDao: UsageSessionDao,
    private val dailySummaryDao: DailySummaryDao
) {
    companion object {
        private const val TAG = "DailySummaryBuilder"
    }

    suspend fun backfillAllSummaries() {
        val allSessions = usageSessionDao.getAllSessionsSince(0)
        if (allSessions.isEmpty()) {
            Log.d(TAG, "No sessions to backfill")
            return
        }

        val sessionsByDay = allSessions.groupBy { timestampToEpochDay(it.startTimestamp) }
        val summaries = sessionsByDay.map { (epochDay, sessions) ->
            buildSummaryFromSessions(epochDay, sessions)
        }

        dailySummaryDao.upsertSummaries(summaries)
        Log.d(TAG, "Backfilled ${summaries.size} daily summaries")
    }

    suspend fun updateSummaryForDay(timestampMs: Long) {
        val epochDay = timestampToEpochDay(timestampMs)
        val date = LocalDate.ofEpochDay(epochDay.toLong())

        val dayStartMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEndMs = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessions = usageSessionDao.getSessionsBetween(dayStartMs, dayEndMs)
        if (sessions.isEmpty()) {
            dailySummaryDao.deleteSummaryForDay(epochDay)
            return
        }

        val summary = buildSummaryFromSessions(epochDay, sessions)
        dailySummaryDao.upsertSummary(summary)
    }

    private fun buildSummaryFromSessions(epochDay: Int, sessions: List<UsageSessionEntity>): DailySummaryEntity {
        val date = LocalDate.ofEpochDay(epochDay.toLong())

        val musicSessions = sessions.filter { it.moduleType == StatsRepository.MODULE_MUSIC }
        val midiSessions = sessions.filter {
            it.moduleType == StatsRepository.MODULE_MIDI || it.moduleType == StatsRepository.MODULE_MIDI_PRACTICE
        }
        val radioSessions = sessions.filter { it.moduleType == StatsRepository.MODULE_RADIO }

        val musicDuration = musicSessions.sumOf { it.durationMs }
        val midiDuration = midiSessions.sumOf { it.durationMs }
        val radioDuration = radioSessions.sumOf { it.durationMs }

        return DailySummaryEntity(
            dateEpochDay = epochDay,
            year = date.year,
            month = date.monthValue,
            dayOfMonth = date.dayOfMonth,
            totalDurationMs = musicDuration + midiDuration + radioDuration,
            musicDurationMs = musicDuration,
            midiDurationMs = midiDuration,
            radioDurationMs = radioDuration,
            sessionCount = sessions.size,
            musicDetailsJson = buildMusicDetailsJson(musicSessions),
            midiDetailsJson = buildMidiDetailsJson(midiSessions),
            radioDetailsJson = buildRadioDetailsJson(radioSessions),
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    private fun buildMusicDetailsJson(sessions: List<UsageSessionEntity>): String? {
        if (sessions.isEmpty()) return null
        val byArtist = sessions.groupBy { it.trackArtist ?: "" }
        val arr = JSONArray()
        byArtist.entries
            .sortedByDescending { entry -> entry.value.sumOf { it.durationMs } }
            .take(10)
            .forEach { (artist, artistSessions) ->
                if (artist.isNotBlank()) {
                    val obj = JSONObject()
                    obj.put("a", artist)
                    obj.put("t", artistSessions.size)
                    obj.put("d", artistSessions.sumOf { it.durationMs })
                    arr.put(obj)
                }
            }
        return if (arr.length() > 0) arr.toString() else null
    }

    private fun buildMidiDetailsJson(sessions: List<UsageSessionEntity>): String? {
        if (sessions.isEmpty()) return null
        val byFile = sessions.groupBy { it.midiFileName ?: "" }
        val arr = JSONArray()
        byFile.entries
            .sortedByDescending { entry -> entry.value.sumOf { it.durationMs } }
            .take(10)
            .forEach { (fileName, fileSessions) ->
                if (fileName.isNotBlank()) {
                    val obj = JSONObject()
                    obj.put("f", fileName)
                    obj.put("d", fileSessions.sumOf { it.durationMs })
                    val scores = fileSessions.mapNotNull { it.practiceScore }
                    if (scores.isNotEmpty()) {
                        obj.put("s", scores.average())
                    }
                    arr.put(obj)
                }
            }
        return if (arr.length() > 0) arr.toString() else null
    }

    private fun buildRadioDetailsJson(sessions: List<UsageSessionEntity>): String? {
        if (sessions.isEmpty()) return null
        val byStation = sessions.groupBy { it.radioStationName ?: "" }
        val arr = JSONArray()
        byStation.entries
            .sortedByDescending { entry -> entry.value.sumOf { it.durationMs } }
            .take(10)
            .forEach { (name, stationSessions) ->
                if (name.isNotBlank()) {
                    val obj = JSONObject()
                    obj.put("n", name)
                    obj.put("d", stationSessions.sumOf { it.durationMs })
                    arr.put(obj)
                }
            }
        return if (arr.length() > 0) arr.toString() else null
    }

    private fun timestampToEpochDay(timestampMs: Long): Int {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
            .toInt()
    }
}
