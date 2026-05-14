package com.Atom2Universe.app.stats.data

import android.content.Context
import com.Atom2Universe.app.midi.data.MidiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Repository pour accéder aux statistiques d'utilisation.
 * Combine les données de StatsDatabase et MidiDatabase (pour les sessions de practice).
 */
class StatsRepository(context: Context) {

    private val statsDb = StatsDatabase.getInstance(context)
    private val usageSessionDao = statsDb.usageSessionDao()
    private val dailySummaryDao = statsDb.dailySummaryDao()

    private val midiDb = MidiDatabase.getInstance(context)
    private val practiceSessionDao = midiDb.practiceSessionDao()

    private val dailySummaryBuilder = DailySummaryBuilder(usageSessionDao, dailySummaryDao)

    companion object {
        const val MODULE_MUSIC = "music"
        const val MODULE_MIDI = "midi"
        const val MODULE_RADIO = "radio"
    }

    /**
     * Enregistre une nouvelle session d'utilisation.
     */
    suspend fun insertSession(session: UsageSessionEntity) = withContext(Dispatchers.IO) {
        usageSessionDao.insertSession(session)
    }

    /**
     * Récupère la durée totale d'écoute de musique pour une période.
     */
    suspend fun getMusicListeningTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModule(MODULE_MUSIC, startDate, endDate)
    }

    /**
     * Récupère la durée totale de pratique MIDI pour une période.
     * Combine les sessions d'écoute MIDI et les sessions de practice.
     */
    suspend fun getMidiPracticeTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        // Sessions d'écoute MIDI normales
        val listeningTime = usageSessionDao.getTotalDurationByModule(MODULE_MIDI, startDate, endDate)

        // Sessions de practice (depuis MidiDatabase)
        val practiceSessions = practiceSessionDao.getSessionsBetween(startDate, endDate)
        val practiceTime = practiceSessions.sumOf { it.sessionDurationMs }

        listeningTime + practiceTime
    }

    /**
     * Récupère la durée totale d'écoute de radio pour une période.
     */
    suspend fun getRadioListeningTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModule(MODULE_RADIO, startDate, endDate)
    }

    /**
     * Récupère le top des artistes les plus écoutés.
     */
    suspend fun getTopArtists(startDate: Long, endDate: Long, limit: Int = 5): List<ArtistStats> = withContext(Dispatchers.IO) {
        usageSessionDao.getTopArtists(startDate, endDate, limit)
    }

    /**
     * Récupère le top des albums les plus écoutés.
     */
    suspend fun getTopAlbums(startDate: Long, endDate: Long, limit: Int = 5): List<AlbumStats> = withContext(Dispatchers.IO) {
        usageSessionDao.getTopAlbums(startDate, endDate, limit)
    }

    /**
     * Récupère le top des fichiers MIDI les plus travaillés.
     * Combine les sessions d'écoute et de practice.
     */
    suspend fun getTopMidiFiles(startDate: Long, endDate: Long, limit: Int = 5): List<MidiFileStats> = withContext(Dispatchers.IO) {
        // Stats depuis les sessions d'écoute
        val listeningStats = usageSessionDao.getTopMidiFiles(startDate, endDate, limit * 2)

        // Stats depuis les sessions de practice (grouper par fichier)
        val practiceSessions = practiceSessionDao.getSessionsBetween(startDate, endDate)
        val practiceStats = practiceSessions
            .groupBy { it.trackFilePath.substringAfterLast("/") }
            .map { (fileName, sessions) ->
                MidiFileStats(
                    midiFileName = fileName,
                    totalDuration = sessions.sumOf { it.sessionDurationMs }
                )
            }

        // Combiner et agréger
        val combined = (listeningStats + practiceStats)
            .groupBy { it.midiFileName }
            .map { (fileName, stats) ->
                MidiFileStats(
                    midiFileName = fileName,
                    totalDuration = stats.sumOf { it.totalDuration }
                )
            }
            .sortedByDescending { it.totalDuration }
            .take(limit)

        combined
    }

    /**
     * Récupère le score moyen de practice pour une période.
     * Utilise les vraies sessions de practice depuis MidiDatabase.
     */
    suspend fun getAveragePracticeScore(startDate: Long, endDate: Long): Float? = withContext(Dispatchers.IO) {
        val sessions = practiceSessionDao.getSessionsBetween(startDate, endDate)
        if (sessions.isEmpty()) {
            null
        } else {
            sessions.map { it.score.toFloat() }.average().toFloat()
        }
    }

    /**
     * Récupère le nombre de sessions pour un module.
     */
    suspend fun getSessionCount(moduleType: String, startDate: Long, endDate: Long): Int = withContext(Dispatchers.IO) {
        usageSessionDao.getSessionCount(moduleType, startDate, endDate)
    }

    /**
     * Supprime les sessions plus anciennes qu'un certain nombre de jours.
     * Par défaut, rétention permanente (Int.MAX_VALUE).
     */
    suspend fun cleanOldSessions(daysToKeep: Int = Int.MAX_VALUE): Int = withContext(Dispatchers.IO) {
        if (daysToKeep == Int.MAX_VALUE) return@withContext 0
        val cutoffTimestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        usageSessionDao.deleteSessionsOlderThan(cutoffTimestamp)
    }

    // ===== Calendrier =====

    suspend fun getActiveDaysInMonth(year: Int, month: Int): List<Int> = withContext(Dispatchers.IO) {
        dailySummaryDao.getActiveDaysInMonth(year, month)
    }

    suspend fun getDayHistory(year: Int, month: Int, day: Int): CalendarDayHistory? = withContext(Dispatchers.IO) {
        val date = java.time.LocalDate.of(year, month, day)
        val epochDay = date.toEpochDay().toInt()
        val summary = dailySummaryDao.getSummaryForDay(epochDay) ?: return@withContext null

        val musicEntries = parseMusicDetailsJson(summary.musicDetailsJson)
        val midiEntries = parseMidiDetailsJson(summary.midiDetailsJson)
        val radioEntries = parseRadioDetailsJson(summary.radioDetailsJson)

        CalendarDayHistory(
            year = year,
            month = month,
            day = day,
            totalDurationMs = summary.totalDurationMs,
            musicDurationMs = summary.musicDurationMs,
            midiDurationMs = summary.midiDurationMs,
            radioDurationMs = summary.radioDurationMs,
            musicEntries = musicEntries,
            midiEntries = midiEntries,
            radioEntries = radioEntries
        )
    }

    suspend fun updateDailySummaryForDay(timestampMs: Long) = withContext(Dispatchers.IO) {
        dailySummaryBuilder.updateSummaryForDay(timestampMs)
    }

    suspend fun ensureDailySummariesPopulated() = withContext(Dispatchers.IO) {
        val count = dailySummaryDao.getSummaryCount()
        if (count == 0) {
            dailySummaryBuilder.backfillAllSummaries()
        }
    }

    private fun parseMusicDetailsJson(json: String?): List<MusicHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MusicHistoryEntry(
                artist = obj.getString("a"),
                trackCount = obj.getInt("t"),
                durationMs = obj.getLong("d")
            )
        }
    }

    private fun parseMidiDetailsJson(json: String?): List<MidiHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MidiHistoryEntry(
                fileName = obj.getString("f"),
                durationMs = obj.getLong("d"),
                averageScore = if (obj.has("s")) obj.getDouble("s").toFloat() else null
            )
        }
    }

    private fun parseRadioDetailsJson(json: String?): List<RadioHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            RadioHistoryEntry(
                stationName = obj.getString("n"),
                durationMs = obj.getLong("d")
            )
        }
    }
}

data class CalendarDayHistory(
    val year: Int,
    val month: Int,
    val day: Int,
    val totalDurationMs: Long,
    val musicDurationMs: Long,
    val midiDurationMs: Long,
    val radioDurationMs: Long,
    val musicEntries: List<MusicHistoryEntry>,
    val midiEntries: List<MidiHistoryEntry>,
    val radioEntries: List<RadioHistoryEntry>
)

data class MusicHistoryEntry(
    val artist: String,
    val trackCount: Int,
    val durationMs: Long
)

data class MidiHistoryEntry(
    val fileName: String,
    val durationMs: Long,
    val averageScore: Float?
)

data class RadioHistoryEntry(
    val stationName: String,
    val durationMs: Long
)
