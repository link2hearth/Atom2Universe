package com.Atom2Universe.app.music

import android.content.Context
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.model.SyncArtistFavoriteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestionnaire des personnalisations d'artistes.
 * Stocke les icones, couleurs et favoris dans un fichier JSON dans le stockage externe de l'app.
 * Format: /Android/data/<package>/files/Music/.a2u_artist_customizations.json
 */
object ArtistCustomizationManager {

    private const val TAG = "ArtistCustomManager"
    private const val CUSTOMIZATIONS_FILENAME = ".a2u_artist_customizations.json"
    private const val JSON_VERSION = 2

    // Cache des personnalisations en memoire (cle = nom artiste lowercase)
    private val customizationsCache = mutableMapOf<String, ArtistCustomization>()

    // Cache des favoris artistes supprimés pour sync cloud (soft-delete)
    private val deletedFavoritesCache = mutableMapOf<String, DeletedArtistFavorite>()

    private var isLoaded = false

    data class ArtistCustomization(
        val artistName: String,
        var iconPath: String? = null,
        var color: String? = null,  // Format hex: "#FF5722"
        var isFavorite: Boolean = false,
        var addedToFavoritesAt: Long = 0  // Timestamp en millisecondes
    )

    data class DeletedArtistFavorite(
        val artistName: String,
        val addedAt: Long,
        val removedAt: Long
    )

    /**
     * Genere une cle unique basee sur le nom de l'artiste.
     */
    private fun generateKey(artistName: String): String {
        return artistName.lowercase().trim()
    }

    /**
     * Charge les personnalisations depuis le fichier JSON.
     */
    suspend fun loadCustomizations(context: Context) = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val currentFile = getCustomizationsFile(context)
        val legacyFile = getLegacyCustomizationsFile()
        if (legacyFile.exists() && legacyFile.canRead().not()) {
            Log.w(TAG, "Legacy artist customizations file exists but is not readable; ignoring ${legacyFile.absolutePath}")
        }
        val sourceFile = when {
            currentFile.exists() && currentFile.canRead() -> currentFile
            legacyFile.exists() && legacyFile.canRead() -> legacyFile
            else -> null
        }
        if (sourceFile == null) {
            isLoaded = true
            return@withContext
        }

        try {
            val jsonString = sourceFile.readText()
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 1)
            val customizationsArray = json.optJSONArray("customizations") ?: JSONArray()

            customizationsCache.clear()
            deletedFavoritesCache.clear()

            for (i in 0 until customizationsArray.length()) {
                val entry = customizationsArray.getJSONObject(i)
                val artistName = entry.optString("artistName", "")
                if (artistName.isBlank()) continue

                val key = generateKey(artistName)

                // Migration: convertir addedToFavoritesAt string en timestamp
                val addedToFavoritesAt = if (version >= 2) {
                    entry.optLong("addedToFavoritesAt", 0)
                } else {
                    val addedAtStr: String? = if (entry.has("addedToFavoritesAt")) entry.optString("addedToFavoritesAt") else null
                    if (!addedAtStr.isNullOrBlank()) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                .parse(addedAtStr)?.time ?: 0L
                        } catch (_: Exception) {
                            0L
                        }
                    } else {
                        0L
                    }
                }

                customizationsCache[key] = ArtistCustomization(
                    artistName = artistName,
                    iconPath = if (entry.has("iconPath")) entry.optString("iconPath").takeIf { it.isNotBlank() } else null,
                    color = if (entry.has("color")) entry.optString("color").takeIf { it.isNotBlank() } else null,
                    isFavorite = entry.optBoolean("isFavorite", false),
                    addedToFavoritesAt = addedToFavoritesAt
                )
            }

            // Charge les favoris artistes supprimés (soft-delete) - v2 only
            if (version >= 2) {
                val deletedArray = json.optJSONArray("deletedFavorites") ?: JSONArray()
                for (i in 0 until deletedArray.length()) {
                    val entry = deletedArray.getJSONObject(i)
                    val artistName = entry.optString("artistName", "")
                    if (artistName.isBlank()) continue

                    val key = generateKey(artistName)
                    deletedFavoritesCache[key] = DeletedArtistFavorite(
                        artistName = artistName,
                        addedAt = entry.optLong("addedAt", 0),
                        removedAt = entry.optLong("removedAt", 0)
                    )
                }
            }

            isLoaded = true
            Log.d(TAG, "Loaded ${customizationsCache.size} artist customizations, ${deletedFavoritesCache.size} deleted favorites")

            // Migration: save in new format if needed
            if (version < JSON_VERSION || sourceFile != currentFile) {
                saveCustomizationsInternal(currentFile)
                Log.d(TAG, "Artist customizations migrated to v$JSON_VERSION: ${currentFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement personnalisations", e)
            isLoaded = true
        }
    }

    /**
     * Sauvegarde les personnalisations dans le fichier JSON.
     */
    suspend fun saveCustomizations() = withContext(Dispatchers.IO) {
        val file = getCustomizationsFile(context = null)
        saveCustomizationsInternal(file)
    }

    private fun saveCustomizationsInternal(file: File) {
        try {
            val json = JSONObject().apply {
                put("version", JSON_VERSION)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("customizations", JSONArray().apply {
                    customizationsCache.values.forEach { entry ->
                        // Ne sauvegarde que si au moins une personnalisation existe
                        if (entry.iconPath != null || entry.color != null || entry.isFavorite) {
                            put(JSONObject().apply {
                                put("artistName", entry.artistName)
                                entry.iconPath?.let { put("iconPath", it) }
                                entry.color?.let { put("color", it) }
                                put("isFavorite", entry.isFavorite)
                                if (entry.addedToFavoritesAt > 0) {
                                    put("addedToFavoritesAt", entry.addedToFavoritesAt)
                                }
                            })
                        }
                    }
                })
                put("deletedFavorites", JSONArray().apply {
                    deletedFavoritesCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("artistName", entry.artistName)
                            put("addedAt", entry.addedAt)
                            put("removedAt", entry.removedAt)
                        })
                    }
                })
            }

            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
            Log.d(TAG, "Saved ${customizationsCache.size} artist customizations, ${deletedFavoritesCache.size} deleted favorites")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde personnalisations", e)
        }
    }

    /**
     * Obtient ou cree une personnalisation pour un artiste.
     */
    private fun getOrCreateCustomization(artistName: String): ArtistCustomization {
        val key = generateKey(artistName)
        return customizationsCache.getOrPut(key) {
            ArtistCustomization(artistName = artistName)
        }
    }

    /**
     * Obtient la personnalisation d'un artiste (null si aucune).
     */
    fun getCustomization(artistName: String): ArtistCustomization? {
        val key = generateKey(artistName)
        return customizationsCache[key]
    }

    // --- Gestion des icones ---

    /**
     * Definit l'icone d'un artiste.
     */
    suspend fun setArtistIcon(artistName: String, iconPath: String?) {
        val customization = getOrCreateCustomization(artistName)
        customization.iconPath = iconPath
        saveCustomizations()
        Log.d(TAG, "Icon set for artist '$artistName': $iconPath")
    }

    /**
     * Obtient le chemin de l'icone d'un artiste.
     */
    fun getArtistIcon(artistName: String): String? {
        return getCustomization(artistName)?.iconPath
    }

    /**
     * Supprime l'icone d'un artiste.
     */
    suspend fun removeArtistIcon(artistName: String) {
        setArtistIcon(artistName, null)
    }

    // --- Gestion des couleurs ---

    /**
     * Definit la couleur d'un artiste.
     */
    suspend fun setArtistColor(artistName: String, color: String?) {
        val customization = getOrCreateCustomization(artistName)
        customization.color = color
        saveCustomizations()
        Log.d(TAG, "Color set for artist '$artistName': $color")
    }

    /**
     * Obtient la couleur d'un artiste.
     */
    fun getArtistColor(artistName: String): String? {
        return getCustomization(artistName)?.color
    }

    /**
     * Supprime la couleur d'un artiste.
     */
    @Suppress("unused")
    suspend fun removeArtistColor(artistName: String) {
        setArtistColor(artistName, null)
    }

    // --- Gestion des favoris ---

    /**
     * Ajoute un artiste aux favoris.
     */
    suspend fun addArtistToFavorites(artistName: String) {
        val key = generateKey(artistName)
        val customization = getOrCreateCustomization(artistName)
        if (!customization.isFavorite) {
            customization.isFavorite = true
            customization.addedToFavoritesAt = System.currentTimeMillis()
            deletedFavoritesCache.remove(key)  // Retire du cache soft-delete
            saveCustomizations()
            Log.d(TAG, "Artist added to favorites: '$artistName'")

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Retire un artiste des favoris.
     * Conserve l'entrée dans deletedFavoritesCache pour la sync cloud (soft-delete).
     */
    suspend fun removeArtistFromFavorites(artistName: String) {
        val key = generateKey(artistName)
        customizationsCache[key]?.let { customization ->
            if (customization.isFavorite) {
                // Track pour soft-delete avant de modifier
                deletedFavoritesCache[key] = DeletedArtistFavorite(
                    artistName = customization.artistName,
                    addedAt = customization.addedToFavoritesAt,
                    removedAt = System.currentTimeMillis()
                )
                customization.isFavorite = false
                customization.addedToFavoritesAt = 0
                saveCustomizations()
                Log.d(TAG, "Artist removed from favorites: '$artistName'")

                // Trigger debounced cloud sync
                CloudSyncManager.triggerDebouncedSync()
            }
        }
    }

    /**
     * Bascule l'etat favori d'un artiste.
     */
    suspend fun toggleArtistFavorite(artistName: String): Boolean {
        val isFav = isArtistFavorite(artistName)
        if (isFav) {
            removeArtistFromFavorites(artistName)
        } else {
            addArtistToFavorites(artistName)
        }
        return !isFav
    }

    /**
     * Verifie si un artiste est dans les favoris.
     */
    fun isArtistFavorite(artistName: String): Boolean {
        return getCustomization(artistName)?.isFavorite == true
    }

    /**
     * Retourne la liste des noms d'artistes favoris.
     */
    fun getFavoriteArtistNames(): List<String> {
        return customizationsCache.values
            .filter { it.isFavorite }
            .map { it.artistName }
    }

    /**
     * Retourne le nombre d'artistes favoris.
     */
    fun getFavoriteArtistsCount(): Int {
        return customizationsCache.values.count { it.isFavorite }
    }

    /**
     * Force le rechargement des personnalisations.
     */
    fun invalidateCache() {
        isLoaded = false
        customizationsCache.clear()
        deletedFavoritesCache.clear()
    }

    /**
     * Obtient le fichier JSON des personnalisations.
     */
    private fun getCustomizationsFile(context: Context?): File {
        val safeContext = context ?: com.Atom2Universe.app.A2UApplication.appContext
        val musicDir = safeContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: safeContext.filesDir
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }
        return File(musicDir, CUSTOMIZATIONS_FILENAME)
    }

    private fun getLegacyCustomizationsFile(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, CUSTOMIZATIONS_FILENAME)
    }

    // ==================== Cloud Sync Methods ====================

    /**
     * Returns all artist favorites (active AND deleted) for cloud sync.
     */
    fun getAllFavoritesForSync(): List<SyncArtistFavoriteEntry> {
        val result = mutableListOf<SyncArtistFavoriteEntry>()

        // Active favorites
        for ((key, customization) in customizationsCache) {
            if (customization.isFavorite) {
                result.add(SyncArtistFavoriteEntry(
                    key = key,
                    artistName = customization.artistName,
                    addedAt = customization.addedToFavoritesAt,
                    removedAt = null
                ))
            }
        }

        // Deleted favorites (soft-delete)
        for ((key, entry) in deletedFavoritesCache) {
            result.add(SyncArtistFavoriteEntry(
                key = key,
                artistName = entry.artistName,
                addedAt = entry.addedAt,
                removedAt = entry.removedAt
            ))
        }

        return result
    }

    /**
     * Adds an artist favorite from cloud sync.
     */
    suspend fun addFavoriteFromSync(artistName: String, addedTimestamp: Long) {
        val key = generateKey(artistName)
        val customization = getOrCreateCustomization(artistName)

        if (!customization.isFavorite) {
            customization.isFavorite = true
            customization.addedToFavoritesAt = addedTimestamp
            deletedFavoritesCache.remove(key)
            saveCustomizations()
            Log.d(TAG, "Added artist favorite from sync: $artistName")
        }
    }

    /**
     * Removes an artist favorite from cloud sync.
     */
    suspend fun removeFavoriteFromSync(artistName: String, removedTimestamp: Long) {
        val key = generateKey(artistName)
        customizationsCache[key]?.let { customization ->
            if (customization.isFavorite) {
                deletedFavoritesCache[key] = DeletedArtistFavorite(
                    artistName = customization.artistName,
                    addedAt = customization.addedToFavoritesAt,
                    removedAt = removedTimestamp
                )
                customization.isFavorite = false
                customization.addedToFavoritesAt = 0
                saveCustomizations()
                Log.d(TAG, "Removed artist favorite from sync: $artistName")
            }
        }
    }

    /**
     * Clears the deleted favorites cache after successful sync upload.
     */
    suspend fun clearDeletedFavoritesCache() {
        deletedFavoritesCache.clear()
        saveCustomizations()
        Log.d(TAG, "Cleared deleted artist favorites cache after sync")
    }
}
