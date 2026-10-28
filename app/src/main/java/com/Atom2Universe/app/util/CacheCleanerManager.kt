package com.Atom2Universe.app.util

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.stats.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nettoyage des fichiers jetables.
 *
 * Seuls des emplacements connus sont visités (liste blanche) :
 * - les fichiers temporaires du cache (exports, imports, partages, MIDI temporaires…) ;
 * - les WAV et SF2 du créateur SF2 qu'aucun projet ne référence plus (lecture complète de
 *   sa base exigée : si elle échoue, rien n'est supprimé dans ces dossiers).
 * Tout fichier modifié depuis moins d'une heure est gardé : un export, un partage ou un
 * enregistrement en cours garde son fichier.
 */
class CacheCleanerManager(private val context: Context) {

    companion object {
        private const val TAG = "CacheCleanerManager"

        private const val MIN_AGE_MS = 60 * 60 * 1000L

        /** Sous-dossiers du cache qui ne contiennent que des fichiers temporaires. */
        private val CACHE_TEMP_DIRS = listOf(
            "audio_exports",   // éditeur audio : rendus avant copie vers la destination
            "audio_import",    // éditeur audio : décodage avant rangement dans le projet
            "recordings",      // pratique : enregistrement avant partage
            "pixelart_share",
            "cloud_projects",
            "sf2_samples",     // créateur SF2 : prise micro avant copie dans le projet
            "sf2_import",
            "exports"          // Toybox Racers : monde partagé
        )

        /** Copies des MIDI lus : le moteur du service peut relire la sienne. */
        private val MIDI_TEMP_PREFIXES = listOf("midi_temp_", "sf2_midi_temp_", "hybrid_midi_temp_", "fluidsynth_midi_temp_")

        /** Préfixes des autres fichiers temporaires posés directement dans le cache. */
        private val CACHE_TEMP_PREFIXES = listOf(
            "temp_artist_icon_", "compressed_artist_icon_", "temp_cover_", "compressed_cover_",
            "compressed_album_cover_",
            "sf2_export_", "preset_preview_", "preview_keyboard.sf2", "temp_export.sf2", "pitch_preview.sf2"
        )

        private const val SF2_SAMPLES_DIR = "sf2_samples"
        private const val SF2_SOURCES_DIR = "sf2_sources"
    }

    /**
     * État du nettoyage (pour rapport à l'utilisateur)
     */
    data class CleanupReport(
        val scannedFiles: Int,
        val deletedFiles: Int,
        val freedSpaceMB: Double,
        val errors: Int,
        val deletedStatsRows: Int = 0
    )

    /**
     * Supprime les fichiers jetables (voir la classe) et les sessions de stats de plus d'un an.
     *
     * @param dryRun Si true, simule le nettoyage sans supprimer les fichiers
     */
    suspend fun cleanOrphanedFiles(dryRun: Boolean = false): CleanupReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val candidates = cacheTempFiles() + sf2OrphanFiles()
        val disposable = candidates.filter { now - it.lastModified() > MIN_AGE_MS }

        var deletedCount = 0
        var freedSpace = 0L
        var errorCount = 0
        for (file in disposable) {
            val size = file.length()
            if (dryRun || file.delete()) {
                deletedCount++
                freedSpace += size
            } else {
                Log.w(TAG, "Échec de suppression: ${file.path}")
                errorCount++
            }
        }

        // Les daily_summaries gardent l'historique complet pour le calendrier :
        // les sessions brutes de plus d'un an peuvent partir sans perte.
        val deletedStatsRows = if (dryRun) 0 else try {
            val statsRepo = StatsRepository(context)
            statsRepo.ensureDailySummariesPopulated()
            statsRepo.cleanOldSessions(daysToKeep = 365)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage des sessions de stats", e)
            0
        }

        val report = CleanupReport(
            scannedFiles = candidates.size,
            deletedFiles = deletedCount,
            freedSpaceMB = freedSpace / (1024.0 * 1024.0),
            errors = errorCount,
            deletedStatsRows = deletedStatsRows
        )
        Log.i(TAG, "Nettoyage: ${report.deletedFiles} fichiers, ${freedSpace / 1024} Ko, ${report.errors} erreurs, $deletedStatsRows sessions")
        report
    }

    private fun cacheTempFiles(): List<File> {
        val cache = context.cacheDir
        val files = mutableListOf<File>()
        for (dir in CACHE_TEMP_DIRS) {
            File(cache, dir).walk().filterTo(files) { it.isFile }
        }
        val prefixes = if (MidiPlaybackService.isRunning) CACHE_TEMP_PREFIXES else CACHE_TEMP_PREFIXES + MIDI_TEMP_PREFIXES
        cache.listFiles()?.filterTo(files) { file ->
            file.isFile && prefixes.any { file.name.startsWith(it) }
        }
        return files
    }

    /**
     * Fichiers des dossiers du créateur SF2 qu'aucune ligne de sa base ne cite (chemin exact).
     * Une erreur de lecture, ou une base sans projet (base recréée), ne supprime rien.
     */
    private suspend fun sf2OrphanFiles(): List<File> {
        val files = listOf(SF2_SAMPLES_DIR, SF2_SOURCES_DIR).flatMap { dir ->
            File(context.filesDir, dir).walk().filter { it.isFile }.toList()
        }
        if (files.isEmpty()) return emptyList()

        val referenced = HashSet<String>()
        try {
            val dao = Sf2ProjectDatabase.getInstance(context).projectDao()
            val projects = dao.getAllProjects()
            if (projects.isEmpty()) return emptyList()
            for (project in projects) {
                project.sourceFilePath?.let { referenced += canonical(it) }
                dao.getSourceMetadata(project.id)?.sourceFilePath?.let { referenced += canonical(it) }
                for (sample in dao.getAllSamplesForProject(project.id)) {
                    if (sample.audioFilePath.isNotEmpty()) referenced += canonical(sample.audioFilePath)
                    sample.sourceFilePath?.let { referenced += canonical(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Base SF2 illisible, aucun fichier SF2 supprimé", e)
            return emptyList()
        }
        return files.filter { canonical(it.path) !in referenced }
    }

    private fun canonical(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }
}
