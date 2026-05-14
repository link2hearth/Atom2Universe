package com.Atom2Universe.app.music

import android.content.Context
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.sync.CloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.music.sync.model.SyncFavoriteEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestionnaire des favoris musicaux.
 * Stocke les favoris dans un fichier JSON dans le stockage interne de l'app.
 * Format: {app_internal}/favorites.json
 *
 * Note: On utilise le stockage interne au lieu du stockage externe car:
 * - Android 11+ bloque l'accès direct au stockage externe (scoped storage)
 * - Le stockage interne fonctionne sur tous les appareils
 * - Les favoris sont synchronisés via Google Drive de toute façon
 *
 * Version 2: Matching par métadonnées (artiste + titre + album) au lieu du chemin.
 * Compatible avec l'ancien format (version 1) pour migration.
 */
object MusicFavoritesManager {

    private const val TAG = "MusicFavoritesManager"
    private const val FAVORITES_FILENAME = "favorites.json"
    private const val LEGACY_FAVORITES_FILENAME = ".a2u_favorites.json"
    private const val JSON_VERSION = 2

    // Context pour accès au stockage interne
    private var appContext: Context? = null

    // Cache des favoris en mémoire (clé métadonnées -> FavoriteEntry)
    // Bug fix: Utilisation de ConcurrentHashMap pour thread-safety
    private val favoritesCache = java.util.concurrent.ConcurrentHashMap<String, FavoriteEntry>()

    // Cache des favoris supprimés récemment (pour sync cloud avec soft-delete)
    // Clé métadonnées -> timestamp de suppression
    // Bug fix: Utilisation de ConcurrentHashMap pour thread-safety
    private val deletedFavoritesCache = java.util.concurrent.ConcurrentHashMap<String, DeletedFavoriteEntry>()

    private var isLoaded = false

    // Scope pour les opérations en arrière-plan (écriture des tags)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class FavoriteEntry(
        val title: String,
        val artist: String,
        val album: String,
        val addedAt: String
    )

    /**
     * Entrée pour un favori supprimé (soft-delete pour sync cloud).
     * Garde les métadonnées et le timestamp de suppression pour permettre
     * la résolution de conflits avec last-write-wins.
     */
    data class DeletedFavoriteEntry(
        val title: String,
        val artist: String,
        val album: String,
        val addedAt: String,
        val removedAt: Long  // Timestamp de suppression
    )

    /**
     * Génère une clé unique basée sur les métadonnées du track.
     * Format: "artiste|titre|album" en minuscules pour comparaison insensible à la casse.
     */
    private fun generateMetadataKey(title: String, artist: String, album: String): String {
        return "${artist.lowercase().trim()}|${title.lowercase().trim()}|${album.lowercase().trim()}"
    }

    private fun generateMetadataKey(track: MusicTrack): String {
        return generateMetadataKey(track.title, track.artist, track.album)
    }

    /**
     * Charge les favoris depuis le fichier JSON.
     * Supporte migration automatique:
     * - De version 1 (chemin) vers version 2 (métadonnées)
     * - Du stockage externe vers stockage interne (Android 11+)
     */
    suspend fun loadFavorites(context: Context) = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        appContext = context.applicationContext

        // Essaie d'abord de charger depuis le stockage interne
        var file = getInternalFavoritesFile(context)
        var loadedFromLegacy = false

        // Si pas de fichier dans le stockage interne, essaie l'ancien emplacement externe
        if (!file.exists()) {
            val legacyFile = getLegacyFavoritesFile()
            if (legacyFile != null && legacyFile.exists()) {
                Log.i(TAG, "Migrating favorites from external to internal storage")
                file = legacyFile
                loadedFromLegacy = true
            } else {
                isLoaded = true
                return@withContext
            }
        }

        try {
            val jsonString = file.readText()
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 1)
            val favoritesArray = json.optJSONArray("favorites") ?: JSONArray()

            favoritesCache.clear()
            deletedFavoritesCache.clear()

            if (version >= 2) {
                // Format version 2: métadonnées comme clé
                for (i in 0 until favoritesArray.length()) {
                    val entry = favoritesArray.getJSONObject(i)
                    val title = entry.optString("title", "")
                    val artist = entry.optString("artist", "")
                    val album = entry.optString("album", "")
                    val key = generateMetadataKey(title, artist, album)

                    favoritesCache[key] = FavoriteEntry(
                        title = title,
                        artist = artist,
                        album = album,
                        addedAt = entry.optString("addedAt", "")
                    )
                }

                // Charge les favoris supprimés (soft-delete) pour la sync cloud
                val deletedArray = json.optJSONArray("deletedFavorites") ?: JSONArray()
                for (i in 0 until deletedArray.length()) {
                    val entry = deletedArray.getJSONObject(i)
                    val title = entry.optString("title", "")
                    val artist = entry.optString("artist", "")
                    val album = entry.optString("album", "")
                    val key = generateMetadataKey(title, artist, album)

                    deletedFavoritesCache[key] = DeletedFavoriteEntry(
                        title = title,
                        artist = artist,
                        album = album,
                        addedAt = entry.optString("addedAt", ""),
                        removedAt = entry.optLong("removedAt", 0)
                    )
                }
            } else {
                // Format version 1: migration depuis relativePath vers métadonnées
                Log.d(TAG, "Migration depuis format v1 (${favoritesArray.length()} favoris)")
                for (i in 0 until favoritesArray.length()) {
                    val entry = favoritesArray.getJSONObject(i)
                    val title = entry.optString("title", "")
                    val artist = entry.optString("artist", "")
                    // L'ancien format n'avait pas d'album, on utilise une chaîne vide
                    val album = entry.optString("album", "")
                    val key = generateMetadataKey(title, artist, album)

                    // Évite les doublons lors de la migration
                    if (!favoritesCache.containsKey(key)) {
                        favoritesCache[key] = FavoriteEntry(
                            title = title,
                            artist = artist,
                            album = album,
                            addedAt = entry.optString("addedAt", "")
                        )
                    }
                }
            }

            // Si chargé depuis l'ancien emplacement, sauvegarde dans le nouveau
            if (loadedFromLegacy && favoritesCache.isNotEmpty()) {
                val internalFile = getInternalFavoritesFile(context)
                saveFavoritesInternal(internalFile)
                Log.i(TAG, "Migration vers stockage interne terminée: ${favoritesCache.size} favoris")
                // Optionnel: supprimer l'ancien fichier
                // file.delete()
            }

            isLoaded = true
            Log.d(TAG, "Loaded ${favoritesCache.size} favorites, ${deletedFavoritesCache.size} deleted (pending sync)")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement favoris", e)
            isLoaded = true
        }
    }

    /**
     * Sauvegarde les favoris dans le fichier JSON (version 2 avec métadonnées)
     */
    suspend fun saveFavorites() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        val file = getInternalFavoritesFile(ctx)
        saveFavoritesInternal(file)
    }

    /**
     * Méthode interne de sauvegarde (utilisée aussi pour la migration)
     * Sauvegarde les favoris actifs ET les favoris supprimés (soft-delete).
     */
    private fun saveFavoritesInternal(file: File) {
        try {
            val json = JSONObject().apply {
                put("version", JSON_VERSION)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("favorites", JSONArray().apply {
                    favoritesCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("title", entry.title)
                            put("artist", entry.artist)
                            put("album", entry.album)
                            put("addedAt", entry.addedAt)
                        })
                    }
                })
                // Sauvegarde les favoris supprimés pour la sync cloud
                put("deletedFavorites", JSONArray().apply {
                    deletedFavoritesCache.values.forEach { entry ->
                        put(JSONObject().apply {
                            put("title", entry.title)
                            put("artist", entry.artist)
                            put("album", entry.album)
                            put("addedAt", entry.addedAt)
                            put("removedAt", entry.removedAt)
                        })
                    }
                })
            }

            // Crée le dossier parent si nécessaire
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde favoris", e)
        }
    }

    /**
     * Ajoute une piste aux favoris
     */
    suspend fun addFavorite(track: MusicTrack) {
        val key = generateMetadataKey(track)

        if (!favoritesCache.containsKey(key)) {
            val entry = FavoriteEntry(
                title = track.title,
                artist = track.artist,
                album = track.album,
                addedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            favoritesCache[key] = entry

            // Retire du cache des supprimés si présent (re-ajout d'un favori supprimé)
            deletedFavoritesCache.remove(key)

            saveFavorites()

            // Lance l'écriture du tag POPM en arrière-plan sans bloquer
            // Cela évite les problèmes quand le fichier est en cours de lecture
            scope.launch {
                appContext?.let { context ->
                    MusicTagEditor.setFavoriteTag(context, track, true)
                }
                // Synchroniser le rating dans la queue POPM pour éviter que
                // processUpdateForFile() ne réécrase le tag avec une valeur obsolète
                MusicPopmSyncManager.queueRatingUpdate(track, 255L)
            }

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Retire une piste des favoris.
     * Conserve les métadonnées dans deletedFavoritesCache pour la sync cloud.
     */
    suspend fun removeFavorite(track: MusicTrack) {
        val key = generateMetadataKey(track)

        val removedEntry = favoritesCache.remove(key)
        if (removedEntry != null) {
            // Track la suppression pour la sync cloud (soft-delete)
            deletedFavoritesCache[key] = DeletedFavoriteEntry(
                title = removedEntry.title,
                artist = removedEntry.artist,
                album = removedEntry.album,
                addedAt = removedEntry.addedAt,
                removedAt = System.currentTimeMillis()
            )

            saveFavorites()

            // Lance l'écriture du tag POPM en arrière-plan sans bloquer
            // Cela évite les problèmes quand le fichier est en cours de lecture
            scope.launch {
                appContext?.let { context ->
                    MusicTagEditor.setFavoriteTag(context, track, false)
                }
                // Synchroniser le rating dans la queue POPM pour éviter que
                // processUpdateForFile() ne réécrase le tag avec une valeur obsolète (ex: 255)
                MusicPopmSyncManager.queueRatingUpdate(track, 0L)
            }

            // Trigger debounced cloud sync
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Bascule l'état favori d'une piste
     */
    suspend fun toggleFavorite(track: MusicTrack): Boolean {
        val isFav = isFavorite(track)
        if (isFav) {
            removeFavorite(track)
        } else {
            addFavorite(track)
        }
        return !isFav
    }

    /**
     * Vérifie si une piste est dans les favoris (matching par métadonnées)
     */
    fun isFavorite(track: MusicTrack): Boolean {
        val key = generateMetadataKey(track)
        return favoritesCache.containsKey(key)
    }

    /**
     * Retourne la liste des entrées de favoris pour résolution
     */
    @Suppress("unused")
    fun getFavoritesEntries(): Collection<FavoriteEntry> = favoritesCache.values

    /**
     * Retourne le nombre de favoris
     */
    fun getFavoritesCount(): Int = favoritesCache.size

    /**
     * Force le rechargement des favoris
     */
    fun invalidateCache() {
        isLoaded = false
        favoritesCache.clear()
        deletedFavoritesCache.clear()
    }

    /**
     * Obtient le fichier JSON des favoris dans le stockage interne de l'app.
     * Ce fichier est accessible sans permission spéciale et fonctionne sur tous les Android.
     */
    private fun getInternalFavoritesFile(context: Context): File {
        return File(context.filesDir, FAVORITES_FILENAME)
    }

    /**
     * Obtient l'ancien emplacement du fichier JSON des favoris (stockage externe).
     * Utilisé uniquement pour la migration depuis l'ancien format.
     */
    @Suppress("DEPRECATION")
    private fun getLegacyFavoritesFile(): File? {
        return try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            File(musicDir, LEGACY_FAVORITES_FILENAME)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot access legacy favorites file", e)
            null
        }
    }

    /**
     * Résout les favoris en MusicTrack à partir de la bibliothèque actuelle.
     * Utilise le matching par métadonnées (artiste + titre + album).
     */
    @Suppress("unused")
    fun resolveFavoriteTracks(allTracks: List<MusicTrack>): List<MusicTrack> {
        return allTracks.filter { track ->
            val key = generateMetadataKey(track)
            favoritesCache.containsKey(key)
        }
    }

    // ==================== Cloud Sync Methods ====================

    /**
     * Returns all favorites (active AND deleted) in a format suitable for cloud sync.
     * Les favoris supprimés sont inclus avec leur timestamp removedAt pour permettre
     * la résolution de conflits avec last-write-wins.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getAllFavoritesForSync(context: Context): List<SyncFavoriteEntry> {
        val result = mutableListOf<SyncFavoriteEntry>()

        // Ajoute les favoris actifs
        for ((key, entry) in favoritesCache) {
            val addedTimestamp = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .parse(entry.addedAt)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            result.add(SyncFavoriteEntry(
                key = key,
                artist = entry.artist,
                title = entry.title,
                album = entry.album,
                addedAt = addedTimestamp,
                removedAt = null
            ))
        }

        // Ajoute les favoris supprimés (soft-delete) pour la sync
        for ((key, deletedEntry) in deletedFavoritesCache) {
            val addedTimestamp = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .parse(deletedEntry.addedAt)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }

            result.add(SyncFavoriteEntry(
                key = key,
                artist = deletedEntry.artist,
                title = deletedEntry.title,
                album = deletedEntry.album,
                addedAt = addedTimestamp,
                removedAt = deletedEntry.removedAt
            ))
        }

        return result
    }

    /**
     * Adds a favorite by metadata (used by cloud sync merge).
     * Does not write to MP3 file - cloud sync only updates the JSON.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun addFavoriteByMetadata(
        context: Context,
        artist: String,
        title: String,
        album: String,
        addedTimestamp: Long
    ) {
        val key = generateMetadataKey(title, artist, album)

        if (!favoritesCache.containsKey(key)) {
            val addedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(Date(addedTimestamp))

            favoritesCache[key] = FavoriteEntry(
                title = title,
                artist = artist,
                album = album,
                addedAt = addedAt
            )

            // Retire du cache des supprimés si présent
            deletedFavoritesCache.remove(key)

            saveFavorites()
            Log.d(TAG, "Added favorite from cloud sync: $title by $artist")
        }
    }

    /**
     * Removes a favorite by metadata (used by cloud sync merge).
     * Tracks the deletion with timestamp for proper sync conflict resolution.
     * Does not modify MP3 file - cloud sync only updates the JSON.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun removeFavoriteByMetadata(
        context: Context,
        artist: String,
        title: String,
        album: String,
        removedTimestamp: Long
    ) {
        val key = generateMetadataKey(title, artist, album)

        val removedEntry = favoritesCache.remove(key)
        if (removedEntry != null) {
            // Track la suppression pour la sync cloud
            deletedFavoritesCache[key] = DeletedFavoriteEntry(
                title = removedEntry.title,
                artist = removedEntry.artist,
                album = removedEntry.album,
                addedAt = removedEntry.addedAt,
                removedAt = removedTimestamp
            )
            saveFavorites()
            Log.d(TAG, "Removed favorite from cloud sync: $title by $artist")
        }
    }

    /**
     * Clears the deleted favorites cache after successful sync upload.
     * Called by CloudSyncManager after uploading favorites to ensure
     * deleted entries are properly persisted on the cloud.
     */
    suspend fun clearDeletedFavoritesCache() {
        deletedFavoritesCache.clear()
        saveFavorites()  // Persiste le fait que les suppressions ont été sync
        Log.d(TAG, "Cleared deleted favorites cache after sync")
    }
}
