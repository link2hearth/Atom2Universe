package com.Atom2Universe.app.util

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.midi.data.MidiDatabase
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.stats.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestionnaire de nettoyage du cache et des fichiers orphelins.
 *
 * Ce manager scanne les bases de données Room et le système de fichiers pour :
 * - Identifier les fichiers référencés dans les BDD
 * - Trouver les fichiers orphelins (non référencés)
 * - Supprimer les fichiers inutilisés pour libérer de l'espace
 *
 * Types de fichiers nettoyés :
 * - SoundFonts (.sf2) non utilisés
 * - Fichiers audio de samples orphelins
 * - Fichiers source SF2 orphelins
 * - Fichiers temporaires et cache
 *
 * Appeler cleanOrphanedFiles() au démarrage de l'app ou périodiquement.
 */
class CacheCleanerManager(private val context: Context) {

    companion object {
        private const val TAG = "CacheCleanerManager"

        // Dossiers à scanner
        private const val SF2_SOURCES_DIR = "sf2_sources"
        private const val SF2_SAMPLES_DIR = "sf2_samples"
        private const val SF2_PROJECTS_DIR = "sf2_projects"

        // Taille minimale pour considérer un fichier comme "gros"
        private const val LARGE_FILE_THRESHOLD_MB = 1
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
     * Lance le nettoyage des fichiers orphelins.
     * Cette méthode est asynchrone et sûre à appeler au démarrage.
     *
     * @param dryRun Si true, simule le nettoyage sans supprimer les fichiers
     * @return Rapport du nettoyage effectué
     */
    suspend fun cleanOrphanedFiles(dryRun: Boolean = false): CleanupReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧹 Début du nettoyage des fichiers orphelins (dryRun=$dryRun)")

        try {
            // 1. Collecter tous les fichiers référencés dans les BDD
            val referencedFiles = collectReferencedFiles()
            Log.d(TAG, "📋 ${referencedFiles.size} fichiers référencés dans les BDD")

            // 2. Scanner les dossiers de l'app pour trouver tous les fichiers
            val existingFiles = scanAppFiles()
            Log.d(TAG, "📂 ${existingFiles.size} fichiers trouvés dans le stockage")

            // 3. Identifier les orphelins
            val orphanedFiles = existingFiles.filter { file ->
                !isFileReferenced(file, referencedFiles)
            }

            Log.d(TAG, "🗑️ ${orphanedFiles.size} fichiers orphelins détectés")

            // 4. Supprimer les orphelins
            var deletedCount = 0
            var freedSpace = 0L
            var errorCount = 0

            for (file in orphanedFiles) {
                try {
                    val fileSize = file.length()
                    val fileSizeMB = fileSize / (1024.0 * 1024.0)

                    if (fileSizeMB > LARGE_FILE_THRESHOLD_MB) {
                        Log.d(TAG, "  🗑️ Fichier orphelin: ${file.name} (${String.format("%.2f", fileSizeMB)} MB)")
                    }

                    if (!dryRun) {
                        if (file.delete()) {
                            deletedCount++
                            freedSpace += fileSize
                        } else {
                            Log.w(TAG, "  ⚠️ Échec de suppression: ${file.name}")
                            errorCount++
                        }
                    } else {
                        // Mode simulation
                        deletedCount++
                        freedSpace += fileSize
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ Erreur lors de la suppression de ${file.name}", e)
                    errorCount++
                }
            }

            val freedSpaceMB = freedSpace / (1024.0 * 1024.0)

            // Nettoyage des sessions de stats de plus d'un an
            // Les daily_summaries conservent l'historique complet pour la vue Calendrier,
            // donc les sessions brutes > 365 jours peuvent être supprimées sans perte.
            val deletedStatsRows = try {
                val statsRepo = StatsRepository(context)
                statsRepo.ensureDailySummariesPopulated()
                statsRepo.cleanOldSessions(daysToKeep = 365)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors du nettoyage des sessions de stats", e)
                0
            }
            if (deletedStatsRows > 0) {
                Log.i(TAG, "📊 $deletedStatsRows sessions de stats > 1 an supprimées")
            }

            val report = CleanupReport(
                scannedFiles = existingFiles.size,
                deletedFiles = deletedCount,
                freedSpaceMB = freedSpaceMB,
                errors = errorCount,
                deletedStatsRows = deletedStatsRows
            )

            Log.i(TAG, "✅ Nettoyage terminé: ${report.deletedFiles} fichiers supprimés, ${String.format("%.2f", report.freedSpaceMB)} MB libérés")
            if (report.errors > 0) {
                Log.w(TAG, "⚠️ ${report.errors} erreurs rencontrées")
            }

            return@withContext report

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors du nettoyage", e)
            return@withContext CleanupReport(0, 0, 0.0, 1)
        }
    }

    /**
     * Collecte tous les chemins de fichiers référencés dans les bases de données.
     */
    private suspend fun collectReferencedFiles(): Set<String> = withContext(Dispatchers.IO) {
        val referencedFiles = mutableSetOf<String>()

        try {
            // 1. Fichiers référencés dans MidiDatabase
            val midiDb = MidiDatabase.getInstance(context)

            // - SoundFonts dans AppSettings
            val soundFontPath = midiDb.settingsDao().getSetting("soundfont_path")
            if (!soundFontPath.isNullOrBlank()) {
                referencedFiles.add(soundFontPath)
                Log.d(TAG, "  📌 SoundFont MIDI: $soundFontPath")
            }

            // - Fichiers MIDI dans MidiTrack
            val midiTracks = midiDb.midiTrackDao().getAllTracksDirect()
            midiTracks.forEach { track ->
                referencedFiles.add(track.filePath)
            }
            Log.d(TAG, "  📌 ${midiTracks.size} fichiers MIDI")

            // 2. Fichiers référencés dans Sf2ProjectDatabase
            val sf2Db = Sf2ProjectDatabase.getInstance(context)

            // - Projets SF2
            val projects = sf2Db.projectDao().getAllProjects()
            projects.forEach { project ->
                // Fichier source SF2
                project.sourceFilePath?.let { referencedFiles.add(it) }
            }
            Log.d(TAG, "  📌 ${projects.size} projets SF2")

            // - Samples audio
            for (project in projects) {
                val samples = sf2Db.projectDao().getAllSamplesForProject(project.id)
                samples.forEach { sample ->
                    referencedFiles.add(sample.audioFilePath)
                }
                Log.d(TAG, "  📌 ${samples.size} samples pour projet ${project.name}")
            }

            // - Sources SF2 dans sf2_source_metadata
            for (project in projects) {
                val sourceMetadata = sf2Db.projectDao().getSourceMetadata(project.id)
                sourceMetadata?.sourceFilePath?.let { referencedFiles.add(it) }
            }

            // 3. Fichiers référencés dans MusicDatabase
            val musicDb = MusicDatabase.getInstance(context)

            // - Fichiers de musique dans cached_tracks
            val cachedTracks = musicDb.cachedTrackDao().getAllTracks()
            cachedTracks.forEach { track ->
                track.filePath?.let { referencedFiles.add(it) }
            }
            Log.d(TAG, "  📌 ${cachedTracks.size} pistes en cache")

            // - Cover art dans pending_tag_edits
            val pendingEdits = musicDb.pendingTagEditDao().getAllPendingEdits()
            pendingEdits.forEach { edit ->
                edit.coverArtPath?.let { referencedFiles.add(it) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la collecte des fichiers référencés", e)
        }

        return@withContext referencedFiles
    }

    /**
     * Scanne tous les fichiers dans les dossiers de l'application.
     */
    private fun scanAppFiles(): List<File> {
        val allFiles = mutableListOf<File>()

        try {
            // Dossiers internes de l'app
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            val externalFilesDir = context.getExternalFilesDir(null)

            // Scanner filesDir
            filesDir.walk().forEach { file ->
                if (file.isFile && shouldScanFile(file)) {
                    allFiles.add(file)
                }
            }

            // Scanner cacheDir (peut être volumineux)
            cacheDir.walk().forEach { file ->
                if (file.isFile && shouldScanFile(file)) {
                    allFiles.add(file)
                }
            }

            // Scanner external storage
            externalFilesDir?.walk()?.forEach { file ->
                if (file.isFile && shouldScanFile(file)) {
                    allFiles.add(file)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du scan des fichiers", e)
        }

        return allFiles
    }

    /**
     * Détermine si un fichier doit être scanné pour nettoyage.
     * Filtre les fichiers système et les bases de données.
     */
    private fun shouldScanFile(file: File): Boolean {
        val name = file.name.lowercase()

        // Ne pas supprimer les bases de données
        if (name.endsWith(".db") || name.endsWith(".db-shm") || name.endsWith(".db-wal")) {
            return false
        }

        // Ne pas supprimer les fichiers de configuration
        if (name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".prefs")) {
            return false
        }

        // Scanner les fichiers audio, SF2, et autres médias
        val extensions = listOf(".sf2", ".wav", ".mp3", ".flac", ".ogg", ".m4a", ".mid", ".midi", ".jpg", ".png")
        return extensions.any { name.endsWith(it) }
    }

    /**
     * Vérifie si un fichier est référencé dans les bases de données.
     */
    private fun isFileReferenced(file: File, referencedFiles: Set<String>): Boolean {
        val absolutePath = file.absolutePath

        // Vérification directe du chemin absolu
        if (referencedFiles.contains(absolutePath)) {
            return true
        }

        // Vérification par nom de fichier (pour Content URIs et chemins relatifs)
        val fileName = file.name
        if (referencedFiles.any { ref ->
            ref.endsWith(fileName) || ref.contains(fileName)
        }) {
            return true
        }

        // Vérification par parent directory
        val parentName = file.parentFile?.name
        return parentName != null && referencedFiles.any { ref ->
            ref.contains(parentName) && ref.contains(fileName)
        }
    }

    /**
     * Nettoie le cache de l'application (dossier cache/).
     * Cette méthode est plus agressive et peut être appelée séparément.
     */
    suspend fun cleanAppCache(): CleanupReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "🧹 Nettoyage du cache de l'application")

        val cacheDir = context.cacheDir
        var deletedCount = 0
        var freedSpace = 0L
        var errorCount = 0
        var scannedCount = 0

        try {
            cacheDir.walk().forEach { file ->
                if (file.isFile) {
                    scannedCount++
                    try {
                        val fileSize = file.length()
                        if (file.delete()) {
                            deletedCount++
                            freedSpace += fileSize
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur lors de la suppression de ${file.name}", e)
                        errorCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage du cache", e)
            errorCount++
        }

        val freedSpaceMB = freedSpace / (1024.0 * 1024.0)
        val report = CleanupReport(
            scannedFiles = scannedCount,
            deletedFiles = deletedCount,
            freedSpaceMB = freedSpaceMB,
            errors = errorCount
        )

        Log.i(TAG, "✅ Cache nettoyé: ${report.deletedFiles} fichiers supprimés, ${String.format("%.2f", report.freedSpaceMB)} MB libérés")

        return@withContext report
    }
}
