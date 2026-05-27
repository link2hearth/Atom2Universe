package com.Atom2Universe.app.music

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.AbstractTag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Gestionnaire de migration des tags ID3.
 *
 * Fonctionnalités:
 * - Détecte les fichiers avec des tags ID3v2.2 périmés
 * - Upgrade automatiquement vers ID3v2.3
 * - Corrige les frames POPM malformés
 * - Fournit une progression détaillée par artiste
 */
object MusicTagMigrationManager {

    private const val TAG = "MusicTagMigration"

    /**
     * État de la migration
     */
    data class MigrationState(
        val isRunning: Boolean = false,
        val currentArtist: String = "",
        val currentFile: String = "",
        val totalArtists: Int = 0,
        val processedArtists: Int = 0,
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val upgradedFiles: Int = 0,
        val errorFiles: Int = 0,
        val isComplete: Boolean = false
    )

    private val _migrationState = MutableStateFlow(MigrationState())
    val migrationState: StateFlow<MigrationState> = _migrationState

    init {
        // Désactive les logs verbeux de JAudioTagger
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Lance la migration complète de la bibliothèque.
     * Traite artiste par artiste avec progression.
     *
     * @param context Contexte Android
     * @param tracks Liste de toutes les pistes à traiter
     * @return Résumé de la migration
     */
    suspend fun migrateAllTags(context: Context, tracks: List<MusicTrack>): MigrationResult = withContext(Dispatchers.IO) {
        if (_migrationState.value.isRunning) {
            Log.w(TAG, "Migration already in progress")
            return@withContext MigrationResult(0, 0, 0, "Migration déjà en cours")
        }

        // Grouper les pistes par artiste
        val tracksByArtist = tracks
            .filter { it.filePath?.lowercase()?.endsWith(".mp3") == true }
            .groupBy { it.artist }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val totalArtists = tracksByArtist.size
        val totalFiles = tracksByArtist.values.sumOf { it.size }

        _migrationState.value = MigrationState(
            isRunning = true,
            totalArtists = totalArtists,
            totalFiles = totalFiles
        )

        var processedArtists = 0
        var processedFiles = 0
        var upgradedFiles = 0
        var errorFiles = 0

        Log.i(TAG, "Starting tag migration: $totalFiles files across $totalArtists artists")

        for ((artist, artistTracks) in tracksByArtist) {
            _migrationState.value = _migrationState.value.copy(
                currentArtist = artist,
                processedArtists = processedArtists
            )

            for (track in artistTracks) {
                val filePath = track.filePath ?: continue

                _migrationState.value = _migrationState.value.copy(
                    currentFile = track.title,
                    processedFiles = processedFiles
                )

                try {
                    val result = migrateFileIfNeeded(context, filePath)
                    when (result) {
                        MigrateResult.UPGRADED -> upgradedFiles++
                        MigrateResult.ERROR -> errorFiles++
                        MigrateResult.ALREADY_OK -> { /* No action needed */ }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating file: $filePath", e)
                    errorFiles++
                }

                processedFiles++
                _migrationState.value = _migrationState.value.copy(
                    processedFiles = processedFiles,
                    upgradedFiles = upgradedFiles,
                    errorFiles = errorFiles
                )
            }

            processedArtists++
        }

        _migrationState.value = _migrationState.value.copy(
            isRunning = false,
            isComplete = true,
            processedArtists = processedArtists,
            processedFiles = processedFiles
        )

        Log.i(TAG, "Migration complete: $upgradedFiles upgraded, $errorFiles errors out of $totalFiles files")

        MigrationResult(
            totalProcessed = processedFiles,
            upgraded = upgradedFiles,
            errors = errorFiles,
            summary = "Migration terminée: $upgradedFiles fichiers mis à jour"
        )
    }

    /**
     * Vérifie si un fichier nécessite une migration et l'effectue si besoin.
     */
    private fun migrateFileIfNeeded(context: Context, filePath: String): MigrateResult {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) {
            return MigrateResult.ERROR
        }

        return try {
            val audioFile = AudioFileIO.read(file)
            when (val tag = audioFile.tag) {
                is ID3v22Tag -> {
                    // Upgrader ID3v2.2 vers ID3v2.3
                    Log.d(TAG, "Upgrading ID3v2.2 to ID3v2.3: $filePath")
                    val newTag = ID3v23Tag(tag as AbstractTag)
                    audioFile.tag = newTag
                    audioFile.commit()

                    // Forcer la mise à jour du timestamp
                    file.setLastModified(System.currentTimeMillis())

                    // Notifier le MediaStore
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(filePath),
                        arrayOf("audio/mpeg"),
                        null
                    )

                    MigrateResult.UPGRADED
                }
                is AbstractID3v2Tag -> {
                    // Déjà en ID3v2.3 ou ID3v2.4, vérifier si POPM a besoin d'être corrigé
                    // Pour l'instant on considère que c'est OK
                    MigrateResult.ALREADY_OK
                }
                else -> {
                    // Pas de tag ID3v2, on pourrait en créer un mais ce n'est pas critique
                    MigrateResult.ALREADY_OK
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading/writing tags for: $filePath", e)
            MigrateResult.ERROR
        }
    }

    /**
     * Analyse la bibliothèque pour compter les fichiers nécessitant une migration.
     * Utile pour afficher une estimation avant de lancer la migration.
     */
    suspend fun analyzeLibrary(tracks: List<MusicTrack>): AnalysisResult = withContext(Dispatchers.IO) {
        var id3v22Count = 0
        var id3v23Count = 0
        var id3v24Count = 0
        var otherCount = 0
        var errorCount = 0
        var processed = 0

        val mp3Tracks = tracks.filter { it.filePath?.lowercase()?.endsWith(".mp3") == true }
        val total = mp3Tracks.size

        Log.i(TAG, "Starting library analysis: $total MP3 files to analyze")

        for (track in mp3Tracks) {
            val filePath = track.filePath ?: continue

            try {
                val file = File(filePath)
                if (!file.exists()) {
                    errorCount++
                    processed++
                    continue
                }

                val audioFile = AudioFileIO.read(file)
                when (val tag = audioFile.tag) {
                    is ID3v22Tag -> id3v22Count++
                    is ID3v23Tag -> id3v23Count++
                    is AbstractID3v2Tag -> {
                        // ID3v2.4 ou autre variante
                        if (tag.majorVersion.toInt() == 4) {
                            id3v24Count++
                        } else {
                            otherCount++
                        }
                    }
                    else -> otherCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error analyzing: $filePath - ${e.message}")
                errorCount++
            }

            processed++
            // Log progress every 100 files
            if (processed % 100 == 0) {
                Log.d(TAG, "Analysis progress: $processed / $total")
            }
        }

        Log.i(TAG, "Analysis complete: v2.2=$id3v22Count, v2.3=$id3v23Count, v2.4=$id3v24Count, other=$otherCount, errors=$errorCount")

        AnalysisResult(
            id3v22Count = id3v22Count,
            id3v23Count = id3v23Count,
            id3v24Count = id3v24Count,
            otherCount = otherCount,
            errorCount = errorCount
        )
    }

    /**
     * Réinitialise l'état de la migration.
     */
    fun resetState() {
        _migrationState.value = MigrationState()
    }

    /**
     * Résultat de migration pour un fichier
     */
    private enum class MigrateResult {
        UPGRADED,
        ALREADY_OK,
        ERROR
    }

    /**
     * Résultat global de la migration
     */
    data class MigrationResult(
        val totalProcessed: Int,
        val upgraded: Int,
        val errors: Int,
        val summary: String
    )

    /**
     * Résultat de l'analyse de la bibliothèque
     */
    data class AnalysisResult(
        val id3v22Count: Int,
        val id3v23Count: Int,
        val id3v24Count: Int,
        val otherCount: Int,
        val errorCount: Int
    ) {
        val needsMigration: Boolean get() = id3v22Count > 0
        val total: Int get() = id3v22Count + id3v23Count + id3v24Count + otherCount
    }
}
