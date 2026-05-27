package com.Atom2Universe.app.music

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile

/**
 * Gestionnaire des dossiers de musique.
 * Par défaut, scanne uniquement le dossier Music.
 * L'utilisateur peut ajouter des dossiers supplémentaires via SAF.
 */
object MusicFoldersManager {

    private const val PREFS_NAME = "music_folders_prefs"
    private const val KEY_CUSTOM_FOLDERS = "custom_folders"

    private var prefs: SharedPreferences? = null

    /**
     * Initialise le gestionnaire avec le contexte
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Retourne le chemin du dossier Music par défaut
     */
    fun getDefaultMusicFolder(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }

    /**
     * Retourne la liste des dossiers personnalisés (URIs SAF)
     */
    fun getCustomFolderUris(): List<String> {
        val foldersString = prefs?.getString(KEY_CUSTOM_FOLDERS, "") ?: ""
        if (foldersString.isEmpty()) return emptyList()
        return foldersString.split("|").filter { it.isNotEmpty() }
    }

    /**
     * Ajoute un dossier personnalisé (URI SAF)
     */
    fun addCustomFolder(uriString: String) {
        val currentFolders = getCustomFolderUris().toMutableList()
        if (!currentFolders.contains(uriString)) {
            currentFolders.add(uriString)
            saveFolders(currentFolders)
        }
    }

    /**
     * Supprime un dossier personnalisé
     */
    fun removeCustomFolder(uriString: String) {
        val currentFolders = getCustomFolderUris().toMutableList()
        currentFolders.remove(uriString)
        saveFolders(currentFolders)
    }

    /**
     * Supprime tous les dossiers personnalisés
     */
    fun clearCustomFolders() {
        prefs?.edit()?.remove(KEY_CUSTOM_FOLDERS)?.apply()
    }

    /**
     * Retourne le nombre de dossiers personnalisés
     */
    fun getCustomFoldersCount(): Int = getCustomFolderUris().size

    /**
     * Convertit une URI SAF en chemin absolu si possible
     */
    fun getPathFromUri(context: Context, uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            DocumentFile.fromTreeUri(context, uri)

            // Essaie d'extraire le chemin depuis l'URI
            val docId = uri.lastPathSegment ?: return null

            // Format typique: "primary:Music/Subfolder" ou "1234-5678:Music"
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]

                if (type == "primary") {
                    "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                } else {
                    // Carte SD externe
                    "/storage/$type/$relativePath"
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retourne le nom d'affichage d'un dossier
     */
    fun getFolderDisplayName(context: Context, uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.name ?: uriString.substringAfterLast("/").substringAfterLast(":")
        } catch (e: Exception) {
            uriString.substringAfterLast("/")
        }
    }

    /**
     * Retourne tous les chemins à scanner (Music par défaut + dossiers personnalisés)
     */
    fun getAllFolderPaths(context: Context): List<String> {
        val paths = mutableListOf<String>()

        // Dossier Music par défaut
        paths.add(getDefaultMusicFolder())

        // Dossiers personnalisés
        for (uriString in getCustomFolderUris()) {
            getPathFromUri(context, uriString)?.let { path ->
                if (!paths.contains(path)) {
                    paths.add(path)
                }
            }
        }

        return paths
    }

    private fun saveFolders(folders: List<String>) {
        prefs?.edit()?.putString(KEY_CUSTOM_FOLDERS, folders.joinToString("|"))?.apply()
    }
}
