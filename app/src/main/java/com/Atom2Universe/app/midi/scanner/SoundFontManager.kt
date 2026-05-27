package com.Atom2Universe.app.midi.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gestionnaire pour l'import et la gestion des fichiers SoundFont (.sf2)
 *
 * Fonctionnalités:
 * - Import SF2 depuis content URI → copie vers filesDir
 * - Validation taille et format SF2
 * - Persistence du chemin dans SettingsRepository
 * - Nettoyage ancien SF2 si remplacement
 */
class SoundFontManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Résultat d'un import de SoundFont
     */
    sealed class ImportResult {
        data class Success(
            val filePath: String,
            val fileName: String,
            val fileSize: Long,
            val warning: String? = null
        ) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Importe un fichier SoundFont depuis un content URI
     *
     * @param sourceUri URI du fichier SF2 sélectionné par l'utilisateur
     * @return ImportResult avec succès ou erreur
     */
    suspend fun importSoundFont(sourceUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Récupère le DocumentFile
            val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)

            if (sourceFile == null || !sourceFile.exists()) {
                return@withContext ImportResult.Error("File not found or access denied")
            }

            val fileName = sourceFile.name ?: "soundfont.sf2"
            val fileSize = sourceFile.length()

            // Validation: extension .sf2
            if (!fileName.lowercase().endsWith(".sf2")) {
                return@withContext ImportResult.Error("Invalid file format. Only .sf2 files are supported.")
            }

            // Validation: taille minimale (SF2 valide fait au moins quelques KB)
            val minSize = 10 * 1024L // 10 KB
            if (fileSize < minSize) {
                return@withContext ImportResult.Error("File too small to be a valid SoundFont.")
            }

            // Avertissement pour les très gros fichiers (>500 MB)
            val warnSize = 500 * 1024 * 1024L // 500 MB
            val sizeWarning = if (fileSize > warnSize) "large" else null

            // Supprime l'ancien SoundFont si existant
            deleteOldSoundFont()

            // Copie le fichier vers filesDir
            val destFile = File(context.filesDir, "soundfonts/$fileName")
            destFile.parentFile?.mkdirs() // Crée le dossier si nécessaire

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Sauvegarde le chemin dans les settings
            settingsRepository.saveSoundFontPath(destFile.absolutePath)
            settingsRepository.saveSoundFontLabel(fileName)

            // Valide le contenu du SF2 et récupère les avertissements
            val contentWarning = Sf2FileCache.validateAndGetWarning(destFile.absolutePath)

            // Combine les avertissements (taille + contenu)
            val combinedWarning = when {
                sizeWarning != null && contentWarning != null -> {
                    "Warning: Large file (${fileSize / 1024 / 1024} MB)\n\n$contentWarning"
                }
                sizeWarning != null -> {
                    "Info: Large file (${fileSize / 1024 / 1024} MB). Loading may take time."
                }
                contentWarning != null -> contentWarning
                else -> null
            }

            return@withContext ImportResult.Success(
                filePath = destFile.absolutePath,
                fileName = fileName,
                fileSize = fileSize,
                warning = combinedWarning
            )

        } catch (e: SecurityException) {
            return@withContext ImportResult.Error("Permission denied. Please grant file access.")
        } catch (e: Exception) {
            return@withContext ImportResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Récupère le chemin du SoundFont actuellement configuré
     * @return Chemin absolu du SF2, ou null si non configuré
     */
    suspend fun getCurrentSoundFontPath(): String? {
        return settingsRepository.getSoundFontPath()
    }

    /**
     * Récupère le nom du SoundFont actuellement configuré
     * @return Nom du fichier SF2, ou null si non configuré
     */
    suspend fun getCurrentSoundFontName(): String? {
        return settingsRepository.getSoundFontLabel()
    }

    /**
     * Vérifie si un SoundFont est configuré et le fichier existe
     * @return true si SF2 configuré et fichier existe, false sinon
     */
    suspend fun isSoundFontConfigured(): Boolean {
        val path = getCurrentSoundFontPath()
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.exists() && file.canRead()
    }

    /**
     * Supprime l'ancien SoundFont stocké
     */
    private suspend fun deleteOldSoundFont() {
        val oldPath = getCurrentSoundFontPath()
        if (oldPath.isNullOrBlank()) return

        try {
            // Vider le cache mémoire du SF2 avant de supprimer le fichier
            Sf2FileCache.clear()

            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                oldFile.delete()
            }
        } catch (e: Exception) { }
    }

    /**
     * Supprime le SoundFont configuré et nettoie les settings
     */
    suspend fun removeSoundFont() = withContext(Dispatchers.IO) {
        deleteOldSoundFont()  // Vide le cache et supprime le fichier
        settingsRepository.saveSoundFontPath("")
        settingsRepository.saveSoundFontLabel("")
    }

    /**
     * Récupère la taille du SoundFont configuré
     * @return Taille en bytes, ou 0 si non configuré
     */
    suspend fun getSoundFontSize(): Long {
        val path = getCurrentSoundFontPath() ?: return 0L
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Valide si un fichier SF2 est correctement formé (header SF2)
     *
     * Format SF2 header:
     * - Offset 0-3: "RIFF" (magic number)
     * - Offset 8-11: "sfbk" (format identifier)
     *
     * @param filePath Chemin absolu du fichier
     * @return true si header SF2 valide, false sinon
     */
    suspend fun validateSf2Format(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || file.length() < 12) {
                return@withContext false
            }

            file.inputStream().use { input ->
                val header = ByteArray(12)
                input.read(header)

                // Vérifie "RIFF" magic number
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                if (riff != "RIFF") {
                    return@withContext false
                }

                // Vérifie "sfbk" format identifier
                val sfbk = String(header, 8, 4, Charsets.US_ASCII)
                return@withContext sfbk == "sfbk"
            }

        } catch (e: Exception) {
            return@withContext false
        }
    }

    companion object {
        private const val TAG = "SoundFontManager"
    }
}
