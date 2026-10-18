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
        const val MODULE_MIDI_PRACTICE = "midi_practice"
        const val MODULE_RADIO = "radio"
        const val MODULE_BOOK = "book"
        const val MODULE_COMIC = "comic"
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
     * Récupère la durée totale d'écoute MIDI simple (hors entraînement) pour une période.
     */
    suspend fun getMidiListeningTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModule(MODULE_MIDI, startDate, endDate)
    }

    /**
     * Récupère la durée totale de pratique/entraînement MIDI pour une période.
     * Combine les sessions MODULE_MIDI_PRACTICE et les résultats de la table practice.
     */
    suspend fun getMidiPracticeTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        // Sessions d'entraînement trackées par StatsTracker (MODULE_MIDI_PRACTICE uniquement)
        val practiceSessionTime = usageSessionDao.getTotalDurationByModule(MODULE_MIDI_PRACTICE, startDate, endDate)

        // Résultats détaillés de practice depuis MidiDatabase (scores, exercices)
        val practiceSessions = practiceSessionDao.getSessionsBetween(startDate, endDate)
        val practiceTime = practiceSessions.sumOf { it.sessionDurationMs }

        practiceSessionTime + practiceTime
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
     * Récupère le top des fichiers MIDI les plus écoutés/travaillés.
     * Combine lectures simples, entraînements et résultats de practice.
     */
    suspend fun getTopMidiFiles(startDate: Long, endDate: Long, limit: Int = 5): List<MidiFileStats> = withContext(Dispatchers.IO) {
        // Stats depuis les sessions d'écoute et d'entraînement (les deux types)
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

    // ===== Lecture (livres + BD) =====

    /**
     * Récupère l'historique complet des sessions de lecture (livres et BD),
     * de la plus récente à la plus ancienne.
     */
    suspend fun getReadingHistory(): List<UsageSessionEntity> = withContext(Dispatchers.IO) {
        usageSessionDao.getSessionsByModules(listOf(MODULE_BOOK, MODULE_COMIC))
    }

    /**
     * Récupère la durée totale de lecture (un module donné : livre ou BD) pour une période.
     */
    suspend fun getReadingTime(moduleType: String, startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModule(moduleType, startDate, endDate)
    }

    /**
     * Récupère le temps de lecture total par titre pour un module (book ou comic),
     * pour l'affichage sur les tuiles des bibliothèques.
     */
    suspend fun getReadingTimeByTitle(moduleType: String): Map<String, Long> = withContext(Dispatchers.IO) {
        usageSessionDao.getReadingTimeByTitle(moduleType).associate { it.readingTitle to it.totalDuration }
    }

    /**
     * Récupère le top des titres (livres/BD) les plus lus sur une période.
     */
    suspend fun getTopReadingTitles(startDate: Long, endDate: Long, limit: Int = 5): List<ReadingTitleStats> = withContext(Dispatchers.IO) {
        usageSessionDao.getTopReadingTitles(listOf(MODULE_BOOK, MODULE_COMIC), startDate, endDate, limit)
    }

    /**
     * Jours du mois avec au moins une session de lecture (livre ou BD).
     * Calculé à la volée depuis les sessions brutes (pas de table de résumé pour la lecture,
     * volumes faibles par nature — pas besoin de précalcul).
     */
    suspend fun getActiveReadingDaysInMonth(year: Int, month: Int): List<Int> = withContext(Dispatchers.IO) {
        val start = java.time.LocalDate.of(year, month, 1)
        val startMs = start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = start.plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = usageSessionDao.getSessionsByModulesBetween(listOf(MODULE_BOOK, MODULE_COMIC), startMs, endMs)
        sessions.map { sessionLocalDate(it.startTimestamp).dayOfMonth }.distinct()
    }

    /**
     * Détail des sessions de lecture d'un jour donné, groupées par titre.
     */
    suspend fun getReadingDayHistory(year: Int, month: Int, day: Int): ReadingCalendarDayHistory? = withContext(Dispatchers.IO) {
        val date = java.time.LocalDate.of(year, month, day)
        val zone = java.time.ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = usageSessionDao.getSessionsByModulesBetween(listOf(MODULE_BOOK, MODULE_COMIC), startMs, endMs)
        if (sessions.isEmpty()) return@withContext null

        val entries = sessions
            .groupBy { (it.readingTitle ?: "") to it.moduleType }
            .map { (key, group) ->
                ReadingHistoryEntry(
                    title = key.first.ifBlank { "?" },
                    isComic = key.second == MODULE_COMIC,
                    durationMs = group.sumOf { it.durationMs }
                )
            }
            .sortedByDescending { it.durationMs }

        ReadingCalendarDayHistory(
            year = year,
            month = month,
            day = day,
            totalDurationMs = sessions.sumOf { it.durationMs },
            bookDurationMs = sessions.filter { it.moduleType == MODULE_BOOK }.sumOf { it.durationMs },
            comicDurationMs = sessions.filter { it.moduleType == MODULE_COMIC }.sumOf { it.durationMs },
            entries = entries
        )
    }

    private fun sessionLocalDate(timestampMs: Long): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(timestampMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

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

/**
 * Détail des sessions de lecture (livres/BD) d'un jour donné, pour la vue calendrier.
 */
data class ReadingCalendarDayHistory(
    val year: Int,
    val month: Int,
    val day: Int,
    val totalDurationMs: Long,
    val bookDurationMs: Long,
    val comicDurationMs: Long,
    val entries: List<ReadingHistoryEntry>
)

data class ReadingHistoryEntry(
    val title: String,
    val isComic: Boolean,
    val durationMs: Long
)
