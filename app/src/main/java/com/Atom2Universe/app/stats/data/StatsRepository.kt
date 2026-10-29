package com.Atom2Universe.app.stats.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Repository pour accéder aux statistiques d'utilisation (StatsDatabase).
 * L'entraînement MIDI y arrive comme une session MIDI portant son score : il n'est pas relu
 * dans la base du module MIDI, qui le compterait une seconde fois.
 */
class StatsRepository(context: Context) {

    private val statsDb = StatsDatabase.getInstance(context)
    private val usageSessionDao = statsDb.usageSessionDao()
    private val dailySummaryDao = statsDb.dailySummaryDao()

    private val dailySummaryBuilder = DailySummaryBuilder(usageSessionDao, dailySummaryDao)

    companion object {
        const val MODULE_MUSIC = "music"
        const val MODULE_MIDI = "midi"
        const val MODULE_MIDI_PRACTICE = "midi_practice"
        const val MODULE_RADIO = "radio"
        const val MODULE_BOOK = "book"
        const val MODULE_COMIC = "comic"

        /** Écoute et entraînement : la carte MIDI, le top des morceaux et le calendrier les additionnent. */
        val MIDI_MODULES = listOf(MODULE_MIDI, MODULE_MIDI_PRACTICE)
    }

    /**
     * Enregistre une nouvelle session d'utilisation et recalcule le résumé de son jour, dans la
     * même transaction : deux sessions finies en même temps ne laissent pas un résumé périmé.
     */
    suspend fun insertSession(session: UsageSessionEntity): Long = withContext(Dispatchers.IO) {
        statsDb.withTransaction {
            val id = usageSessionDao.insertSession(session)
            dailySummaryBuilder.updateSummaryForDay(session.startTimestamp)
            id
        }
    }

    /**
     * Récupère la durée totale d'écoute de musique pour une période.
     */
    suspend fun getMusicListeningTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModule(MODULE_MUSIC, startDate, endDate)
    }

    /**
     * Récupère la durée totale MIDI (écoute + entraînement) pour une période.
     */
    suspend fun getMidiTime(startDate: Long, endDate: Long): Long = withContext(Dispatchers.IO) {
        usageSessionDao.getTotalDurationByModules(MIDI_MODULES, startDate, endDate)
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
     * Récupère le top des fichiers MIDI les plus écoutés/travaillés (écoute et entraînement).
     */
    suspend fun getTopMidiFiles(startDate: Long, endDate: Long, limit: Int = 5): List<MidiFileStats> = withContext(Dispatchers.IO) {
        usageSessionDao.getTopMidiFiles(startDate, endDate, limit)
    }

    /**
     * Récupère le score moyen d'entraînement pour une période (null sans entraînement noté).
     */
    suspend fun getAveragePracticeScore(startDate: Long, endDate: Long): Float? = withContext(Dispatchers.IO) {
        usageSessionDao.getAveragePracticeScore(startDate, endDate)
    }

    /**
     * Récupère le nombre de sessions pour un module.
     */
    suspend fun getSessionCount(moduleType: String, startDate: Long, endDate: Long): Int = withContext(Dispatchers.IO) {
        usageSessionDao.getSessionCount(moduleType, startDate, endDate)
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
                    title = key.first,
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
        // Un jour sans audio n'a rien à montrer (vieux résumés d'un jour de lecture seule)
        val summary = dailySummaryDao.getSummaryForDay(epochDay)
            ?.takeIf { it.totalDurationMs > 0 } ?: return@withContext null

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
