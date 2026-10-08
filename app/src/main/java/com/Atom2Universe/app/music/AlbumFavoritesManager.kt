package com.Atom2Universe.app.music

import android.content.Context
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.model.SyncAlbumFavoriteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestionnaire des albums favoris.
 * Stocke les favoris dans un fichier JSON dans le stockage externe de l'app.
 * Format: /Android/data/<package>/files/Music/.a2u_album_favorites.json
 *
 * Version 2: Supporte soft-delete pour la synchronisation cloud avec last-write-wins.
 */
object AlbumFavoritesManager {

    private const val TAG = "AlbumFavoritesManager"
    private const val FAVORITES_FILENAME = ".a2u_album_favorites.json"
    private const val JSON_VERSION = 2

    // Cache des favoris en memoire (cle = "artistName|albumName" lowercase)
    private val favoritesCache = mutableMapOf<String, AlbumFavorite>()

    // Cache des favoris supprimés pour sync cloud (soft-delete)
    private val deletedFavoritesCache = mutableMapOf<String, DeletedAlbumFavorite>()

    private var isLoaded = false

    data class AlbumFavorite(
        val artistName: String,
        val albumName: String,
        val addedAt: Long  // Timestamp en millisecondes
    )

    data class DeletedAlbumFavorite(
        val artistName: String,
        val albumName: String,
        val addedAt: Long,
        val removedAt: Long
    )

    /**
     * Genere une cle unique basee sur le nom de l'artiste et de l'album.
     */
    private fun generateKey(artistName: String, albumName: String): String {
        return "${artistName.lowercase().trim()}|${albumName.lowercase().trim()}"
    }

    /**
     * Charge les favoris depuis le fichier JSON.
     */
    suspend fun loadFavorites(context: Context) = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val currentFile = getFavoritesFile(context)
        val legacyFile = getLegacyFavoritesFile()
        if (legacyFile.exists() && legacyFile.canRead().not()) {
            Log.w(TAG, "Legacy album favorites file exists but is not readable; ignoring ${legacyFile.absolutePath}")
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
            val favoritesArray = json.optJSONArray("favorites") ?: JSONArray()

            favoritesCache.clear()
            deletedFavoritesCache.clear()

            for (i in 0 until favoritesArray.length()) {
                val entry = favoritesArray.getJSONObject(i)
                val artistName = entry.optString("artistName", "")
                val albumName = entry.optString("albumName", "")
                if (artistName.isBlank() || albumName.isBlank()) continue

                val key = generateKey(artistName, albumName)

                // Migration v1 -> v2: convertir addedAt string en timestamp
                val addedAt = if (version >= 2) {
                    entry.optLong("addedAt", System.currentTimeMillis())
                } else {
                    // Ancien format: addedAt était une string ISO
                    val addedAtStr: String? = if (entry.has("addedAt")) entry.optString("addedAt") else null
                    if (!addedAtStr.isNullOrBlank()) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                .parse(addedAtStr)?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    } else {
                        System.currentTimeMillis()
                    }
                }

                favoritesCache[key] = AlbumFavorite(
                    artistName = artistName,
                    albumName = albumName,
                    addedAt = addedAt
                )
            }

            // Charge les favoris supprimés (soft-delete) - v2 only
            if (version >= 2) {
                val deletedArray = json.optJSONArray("deletedFavorites") ?: JSONArray()
                for (i in 0 until deletedArray.length()) {
                    val entry = deletedArray.getJSONObject(i)
                    val artistName = entry.optString("artistName", "")
                    val albumName = entry.optString("albumName", "")
                    if (artistName.isBlank() || albumName.isBlank()) continue

                    val key = generateKey(artistName, albumName)
                    deletedFavoritesCache[key] = DeletedAlbumFavorite(
                        artistName = artistName,
                        albumName = albumName,
                        addedAt = entry.optLong("addedAt", 0),
                        removedAt = entry.optLong("removedAt", 0)
                    )
                }
            }

            isLoaded = true
            Log.d(TAG, "Loaded ${favoritesCache.size} album favorites, ${deletedFavoritesCache.size} deleted")

            // Migration: save in new format if loaded from old version or legacy location
            if (version < JSON_VERSION || sourceFile != currentFile) {
                saveFavoritesInternal(currentFile)
                Log.d(TAG, "Album favorites migrated to v$JSON_VERSION: ${currentFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement favoris albums", e)
            isLoaded = true
        }
    }

    /**
     * Sauvegarde les favoris dans le fichier JSON.
     */
    suspend fun saveFavorites() = withContext(Dispatchers.IO) {
        val file = getFavoritesFile(context = null)
        saveFavoritesInternal(file)
    }

    private fun saveFavoritesInternal(file: File) {
        try {
            val json = JSONObject().apply {
                put("version", JSON_VERSION)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("favorites", JSONArray().apply {
                    favoritesCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("artistName", entry.artistName)
                            put("albumName", entry.albumName)
                            put("addedAt", entry.addedAt)
                        })
                    }
                })
                put("deletedFavorites", JSONArray().apply {
                    deletedFavoritesCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("artistName", entry.artistName)
                            put("albumName", entry.albumName)
                            put("addedAt", entry.addedAt)
                            put("removedAt", entry.removedAt)
                        })
                    }
                })
            }

            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
            Log.d(TAG, "Saved ${favoritesCache.size} album favorites, ${deletedFavoritesCache.size} deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde favoris albums", e)
        }
    }

    /**
     * Ajoute un album aux favoris.
     */
    suspend fun addToFavorites(artistName: String, albumName: String) {
        val key = generateKey(artistName, albumName)
        if (!favoritesCache.containsKey(key)) {
            favoritesCache[key] = AlbumFavorite(
                artistName = artistName,
                albumName = albumName,
                addedAt = System.currentTimeMillis()
            )
            deletedFavoritesCache.remove(key)
            saveFavorites()
            Log.d(TAG, "Album added to favorites: '$albumName' by '$artistName'")

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Retire un album des favoris.
     * Conserve l'entrée dans deletedFavoritesCache pour la sync cloud (soft-delete).
     */
    suspend fun removeFromFavorites(artistName: String, albumName: String) {
        val key = generateKey(artistName, albumName)
        val removedEntry = favoritesCache.remove(key)
        if (removedEntry != null) {
            deletedFavoritesCache[key] = DeletedAlbumFavorite(
                artistName = removedEntry.artistName,
                albumName = removedEntry.albumName,
                addedAt = removedEntry.addedAt,
                removedAt = System.currentTimeMillis()
            )
            saveFavorites()
            Log.d(TAG, "Album removed from favorites: '$albumName' by '$artistName'")

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Bascule l'etat favori d'un album.
     */
    suspend fun toggleFavorite(artistName: String, albumName: String): Boolean {
        val isFav = isFavorite(artistName, albumName)
        if (isFav) {
            removeFromFavorites(artistName, albumName)
        } else {
            addToFavorites(artistName, albumName)
        }
        return !isFav
    }

    /**
     * Verifie si un album est dans les favoris.
     */
    fun isFavorite(artistName: String, albumName: String): Boolean {
        val key = generateKey(artistName, albumName)
        return favoritesCache.containsKey(key)
    }

    /**
     * Retourne la liste des favoris (paires artistName, albumName).
     */
    fun getFavorites(): List<Pair<String, String>> {
        return favoritesCache.values.map { Pair(it.artistName, it.albumName) }
    }

    /**
     * Retourne le nombre d'albums favoris.
     */
    fun getFavoritesCount(): Int {
        return favoritesCache.size
    }

    /**
     * Force le rechargement des favoris.
     */
    fun invalidateCache() {
        isLoaded = false
        favoritesCache.clear()
        deletedFavoritesCache.clear()
    }

    /**
     * Obtient le fichier JSON des favoris.
     */
    private fun getFavoritesFile(context: Context?): File {
        val safeContext = context ?: com.Atom2Universe.app.A2UApplication.appContext
        val musicDir = safeContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: safeContext.filesDir
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }
        return File(musicDir, FAVORITES_FILENAME)
    }

    private fun getLegacyFavoritesFile(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, FAVORITES_FILENAME)
    }

    // ==================== Cloud Sync Methods ====================

    /**
     * Returns all album favorites (active AND deleted) for cloud sync.
     */
    fun getAllFavoritesForSync(): List<SyncAlbumFavoriteEntry> {
        val result = mutableListOf<SyncAlbumFavoriteEntry>()

        // Active favorites
        for ((key, entry) in favoritesCache) {
            result.add(SyncAlbumFavoriteEntry(
                key = key,
                artistName = entry.artistName,
                albumName = entry.albumName,
                addedAt = entry.addedAt,
                removedAt = null
            ))
        }

        // Deleted favorites (soft-delete)
        for ((key, entry) in deletedFavoritesCache) {
            result.add(SyncAlbumFavoriteEntry(
                key = key,
                artistName = entry.artistName,
                albumName = entry.albumName,
                addedAt = entry.addedAt,
                removedAt = entry.removedAt
            ))
        }

        return result
    }

    /**
     * Adds an album favorite from cloud sync.
     */
    suspend fun addFavoriteFromSync(artistName: String, albumName: String, addedTimestamp: Long) {
        val key = generateKey(artistName, albumName)

        if (!favoritesCache.containsKey(key)) {
            favoritesCache[key] = AlbumFavorite(
                artistName = artistName,
                albumName = albumName,
                addedAt = addedTimestamp
            )
            deletedFavoritesCache.remove(key)
            saveFavorites()
            Log.d(TAG, "Added album favorite from sync: $albumName by $artistName")
        }
    }

    /**
     * Removes an album favorite from cloud sync.
     */
    suspend fun removeFavoriteFromSync(artistName: String, albumName: String, removedTimestamp: Long) {
        val key = generateKey(artistName, albumName)
        val removedEntry = favoritesCache.remove(key)

        if (removedEntry != null) {
            deletedFavoritesCache[key] = DeletedAlbumFavorite(
                artistName = removedEntry.artistName,
                albumName = removedEntry.albumName,
                addedAt = removedEntry.addedAt,
                removedAt = removedTimestamp
            )
            saveFavorites()
            Log.d(TAG, "Removed album favorite from sync: $albumName by $artistName")
        }
    }

    /**
     * Clears the deleted favorites cache after successful sync upload.
     */
    suspend fun clearDeletedFavoritesCache() {
        deletedFavoritesCache.clear()
        saveFavorites()
        Log.d(TAG, "Cleared deleted album favorites cache after sync")
    }
}
