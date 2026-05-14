package com.Atom2Universe.app.music

import android.content.Context
import android.os.Environment
import android.util.Log
import com.Atom2Universe.app.A2UApplication
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.model.PlaylistData
import com.Atom2Universe.app.music.model.PlaylistTrackEntry
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.sync.model.SyncPlaylistEntry
import com.Atom2Universe.app.music.sync.model.SyncPlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gestionnaire des playlists musicales personnalisées.
 * Stocke les playlists dans un fichier JSON dans le stockage externe de l'app.
 * Format: /Android/data/<package>/files/Music/.a2u_playlists.json
 *
 * Version 2: Matching par métadonnées (artiste + titre + album) au lieu du chemin.
 * Compatible avec l'ancien format (version 1) pour migration.
 */
object MusicPlaylistManager {

    private const val TAG = "MusicPlaylistManager"
    private const val PLAYLISTS_FILENAME = ".a2u_playlists.json"
    private const val JSON_VERSION = 3  // v3: ajout timestamps et soft-delete pour sync

    // Cache des playlists en mémoire
    private val playlistsCache = mutableListOf<PlaylistData>()

    // Métadonnées de sync pour chaque playlist (id -> timestamps)
    private val playlistMetadata = mutableMapOf<String, PlaylistSyncMetadata>()

    // Cache des playlists supprimées pour sync cloud (soft-delete)
    private val deletedPlaylistsCache = mutableMapOf<String, DeletedPlaylist>()

    private var isLoaded = false

    data class PlaylistSyncMetadata(
        val createdAt: Long,
        var modifiedAt: Long
    )

    data class DeletedPlaylist(
        val id: String,
        val name: String,
        val createdAt: Long,
        val deletedAt: Long
    )

    /**
     * Génère une clé unique basée sur les métadonnées du track.
     */
    private fun generateMetadataKey(title: String, artist: String, album: String): String {
        return "${artist.lowercase().trim()}|${title.lowercase().trim()}|${album.lowercase().trim()}"
    }

    private fun generateMetadataKey(track: MusicTrack): String {
        return generateMetadataKey(track.title, track.artist, track.album)
    }

    private fun generateMetadataKey(entry: PlaylistTrackEntry): String {
        return generateMetadataKey(entry.title, entry.artist, entry.album)
    }

    // Listeners pour les changements
    private val listeners = mutableListOf<PlaylistChangeListener>()

    interface PlaylistChangeListener {
        fun onPlaylistsChanged()
    }

    fun addListener(listener: PlaylistChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PlaylistChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onPlaylistsChanged() }
    }

    /**
     * Charge les playlists depuis le fichier JSON.
     * Supporte migration automatique vers version 3 (métadonnées + timestamps sync).
     */
    suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val currentFile = getPlaylistsFile()
        val legacyFile = getLegacyPlaylistsFile()
        if (legacyFile.exists() && legacyFile.canRead().not()) {
            Log.w(TAG, "Legacy playlists file exists but is not readable; ignoring ${legacyFile.absolutePath}")
        }
        val sourceFile = when {
            currentFile?.exists() == true && currentFile.canRead() -> currentFile
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
            val playlistsArray = json.optJSONArray("playlists") ?: JSONArray()

            playlistsCache.clear()
            playlistMetadata.clear()
            deletedPlaylistsCache.clear()
            val targetFile = currentFile ?: sourceFile

            if (version >= 2) {
                // Format version 2+: métadonnées dans tracks
                for (i in 0 until playlistsArray.length()) {
                    val entry = playlistsArray.getJSONObject(i)
                    val tracksArray = entry.optJSONArray("tracks") ?: JSONArray()
                    val tracks = mutableListOf<PlaylistTrackEntry>()

                    for (j in 0 until tracksArray.length()) {
                        val trackObj = tracksArray.getJSONObject(j)
                        tracks.add(PlaylistTrackEntry(
                            title = trackObj.optString("title", ""),
                            artist = trackObj.optString("artist", ""),
                            album = trackObj.optString("album", "")
                        ))
                    }

                    val id = entry.getString("id")
                    val createdAtStr = entry.optString("createdAt", "")

                    playlistsCache.add(
                        PlaylistData(
                            id = id,
                            name = entry.getString("name"),
                            tracks = tracks,
                            createdAt = createdAtStr
                        )
                    )

                    // Timestamps pour sync (v3+)
                    val createdAtTs = if (version >= 3) {
                        entry.optLong("createdAtTimestamp", parseIsoDate(createdAtStr))
                    } else {
                        parseIsoDate(createdAtStr)
                    }
                    val modifiedAt = if (version >= 3) {
                        entry.optLong("modifiedAt", createdAtTs)
                    } else {
                        createdAtTs
                    }

                    playlistMetadata[id] = PlaylistSyncMetadata(
                        createdAt = createdAtTs,
                        modifiedAt = modifiedAt
                    )
                }

                // Charge les playlists supprimées (v3+)
                if (version >= 3) {
                    val deletedArray = json.optJSONArray("deletedPlaylists") ?: JSONArray()
                    for (i in 0 until deletedArray.length()) {
                        val entry = deletedArray.getJSONObject(i)
                        val id = entry.optString("id", "")
                        if (id.isBlank()) continue

                        deletedPlaylistsCache[id] = DeletedPlaylist(
                            id = id,
                            name = entry.optString("name", ""),
                            createdAt = entry.optLong("createdAt", 0),
                            deletedAt = entry.optLong("deletedAt", 0)
                        )
                    }
                }
            } else {
                // Format version 1: migration depuis trackPaths (chemins) vers métadonnées
                // Note: Impossible de migrer sans les métadonnées dans l'ancien format
                // Les playlists v1 stockaient uniquement les chemins, pas les métadonnées
                // On crée des playlists vides - l'utilisateur devra les recréer
                Log.w(TAG, "Migration v1→v3: playlists conservées mais tracks perdus (chemins non migrables)")
                val now = System.currentTimeMillis()
                for (i in 0 until playlistsArray.length()) {
                    val entry = playlistsArray.getJSONObject(i)
                    val id = entry.getString("id")
                    val createdAtStr = entry.optString("createdAt", "")

                    playlistsCache.add(
                        PlaylistData(
                            id = id,
                            name = entry.getString("name"),
                            tracks = emptyList(), // Tracks perdus lors de la migration
                            createdAt = createdAtStr
                        )
                    )

                    playlistMetadata[id] = PlaylistSyncMetadata(
                        createdAt = parseIsoDate(createdAtStr).takeIf { it > 0 } ?: now,
                        modifiedAt = now
                    )
                }
                // Sauvegarde immédiate en version 3
                if (playlistsCache.isNotEmpty()) {
                    savePlaylistsInternal(targetFile)
                    Log.d(TAG, "Migration vers v3 terminée: ${playlistsCache.size} playlists (tracks reset)")
                }
            }
            if (sourceFile != targetFile) {
                savePlaylistsInternal(targetFile)
                Log.d(TAG, "Playlists migrated to app storage: ${targetFile.absolutePath}")
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement playlists", e)
            isLoaded = true
        }
    }

    /**
     * Sauvegarde les playlists dans le fichier JSON (version 3 avec timestamps sync)
     */
    suspend fun savePlaylists() = withContext(Dispatchers.IO) {
        val file = getPlaylistsFile() ?: return@withContext
        savePlaylistsInternal(file)
    }

    /**
     * Méthode interne de sauvegarde (utilisée aussi pour la migration)
     */
    private fun savePlaylistsInternal(file: File) {
        try {
            val json = JSONObject().apply {
                put("version", JSON_VERSION)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("playlists", JSONArray().apply {
                    playlistsCache.forEach { playlist ->
                        val metadata = playlistMetadata[playlist.id]
                        put(JSONObject().apply {
                            put("id", playlist.id)
                            put("name", playlist.name)
                            put("createdAt", playlist.createdAt)
                            put("createdAtTimestamp", metadata?.createdAt ?: System.currentTimeMillis())
                            put("modifiedAt", metadata?.modifiedAt ?: System.currentTimeMillis())
                            put("tracks", JSONArray().apply {
                                playlist.tracks.forEach { track ->
                                    put(JSONObject().apply {
                                        put("title", track.title)
                                        put("artist", track.artist)
                                        put("album", track.album)
                                    })
                                }
                            })
                        })
                    }
                })
                put("deletedPlaylists", JSONArray().apply {
                    deletedPlaylistsCache.values.forEach { deleted ->
                        put(JSONObject().apply {
                            put("id", deleted.id)
                            put("name", deleted.name)
                            put("createdAt", deleted.createdAt)
                            put("deletedAt", deleted.deletedAt)
                        })
                    }
                })
            }

            // Crée le dossier parent si nécessaire
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde playlists", e)
        }
    }

    /**
     * Parse une date ISO 8601 en timestamp.
     */
    private fun parseIsoDate(dateStr: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Crée une nouvelle playlist
     * @return L'ID de la playlist créée
     */
    suspend fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val playlist = PlaylistData(
            id = id,
            name = name.trim(),
            tracks = emptyList(),
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        )
        playlistsCache.add(playlist)
        playlistMetadata[id] = PlaylistSyncMetadata(createdAt = now, modifiedAt = now)
        deletedPlaylistsCache.remove(id)  // Au cas où on recrée une playlist supprimée
        savePlaylists()
        notifyListeners()
        CloudSyncManager.triggerDebouncedSync()
        return id
    }

    /**
     * Supprime une playlist.
     * Conserve l'entrée dans deletedPlaylistsCache pour la sync cloud (soft-delete).
     */
    suspend fun deletePlaylist(id: String) {
        val playlistToDelete = playlistsCache.find { it.id == id }
        val removed = playlistsCache.removeAll { it.id == id }
        if (removed && playlistToDelete != null) {
            val metadata = playlistMetadata.remove(id)
            deletedPlaylistsCache[id] = DeletedPlaylist(
                id = id,
                name = playlistToDelete.name,
                createdAt = metadata?.createdAt ?: System.currentTimeMillis(),
                deletedAt = System.currentTimeMillis()
            )
            savePlaylists()
            notifyListeners()
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Renomme une playlist
     */
    suspend fun renamePlaylist(id: String, newName: String) {
        val index = playlistsCache.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = playlistsCache[index]
            playlistsCache[index] = old.copy(name = newName.trim())
            updateModifiedTimestamp(id)
            savePlaylists()
            notifyListeners()
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Ajoute un track à une playlist
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: MusicTrack) {
        val trackKey = generateMetadataKey(track)
        val index = playlistsCache.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlistsCache[index]
            // Évite les doublons via clé métadonnées
            val alreadyExists = playlist.tracks.any { generateMetadataKey(it) == trackKey }
            if (!alreadyExists) {
                val newEntry = PlaylistTrackEntry(
                    title = track.title,
                    artist = track.artist,
                    album = track.album
                )
                playlistsCache[index] = playlist.copy(
                    tracks = playlist.tracks + newEntry
                )
                updateModifiedTimestamp(playlistId)
                savePlaylists()
                notifyListeners()
                CloudSyncManager.triggerDebouncedSync()
            }
        }
    }

    /**
     * Retire un track d'une playlist
     */
    suspend fun removeTrackFromPlaylist(playlistId: String, track: MusicTrack) {
        val trackKey = generateMetadataKey(track)
        val index = playlistsCache.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlistsCache[index]
            playlistsCache[index] = playlist.copy(
                tracks = playlist.tracks.filter { generateMetadataKey(it) != trackKey }
            )
            updateModifiedTimestamp(playlistId)
            savePlaylists()
            notifyListeners()
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Met à jour l'ordre des tracks d'une playlist
     */
    suspend fun updatePlaylistTracks(playlistId: String, tracks: List<MusicTrack>) {
        val index = playlistsCache.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlistsCache[index]
            val newEntries = tracks.map { track ->
                PlaylistTrackEntry(
                    title = track.title,
                    artist = track.artist,
                    album = track.album
                )
            }
            playlistsCache[index] = playlist.copy(tracks = newEntries)
            updateModifiedTimestamp(playlistId)
            savePlaylists()
            notifyListeners()
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Vérifie si un track est dans une playlist (matching par métadonnées)
     */
    fun isTrackInPlaylist(playlistId: String, track: MusicTrack): Boolean {
        val trackKey = generateMetadataKey(track)
        val playlist = playlistsCache.find { it.id == playlistId }
        return playlist?.tracks?.any { generateMetadataKey(it) == trackKey } == true
    }

    /**
     * Retourne la liste des playlists
     */
    fun getPlaylists(): List<PlaylistData> = playlistsCache.toList()

    /**
     * Retourne une playlist par son ID
     */
    fun getPlaylist(id: String): PlaylistData? = playlistsCache.find { it.id == id }

    /**
     * Retourne le nombre de playlists
     */
    fun getPlaylistsCount(): Int = playlistsCache.size

    /**
     * Résout les entrées de playlist en MusicTrack via matching par métadonnées.
     * Si un track n'est plus dans la bibliothèque, il est ignoré (skip).
     */
    fun getTracksForPlaylist(playlistId: String): List<MusicTrack> {
        val playlist = playlistsCache.find { it.id == playlistId } ?: return emptyList()
        val allTracks = MusicLibrary.getAllTracks()

        // Créer un index pour recherche rapide
        val tracksByKey = allTracks.associateBy { generateMetadataKey(it) }

        return playlist.tracks.mapNotNull { entry ->
            val key = generateMetadataKey(entry)
            tracksByKey[key]
        }
    }

    /**
     * Force le rechargement des playlists
     */
    fun invalidateCache() {
        isLoaded = false
        playlistsCache.clear()
        playlistMetadata.clear()
        deletedPlaylistsCache.clear()
    }

    /**
     * Met à jour le timestamp de modification d'une playlist.
     */
    private fun updateModifiedTimestamp(playlistId: String) {
        playlistMetadata[playlistId]?.modifiedAt = System.currentTimeMillis()
    }

    /**
     * Obtient le fichier JSON des playlists
     */
    private fun getPlaylistsFile(): File? {
        val storageDir = getAppMusicDir() ?: return null
        return File(storageDir, PLAYLISTS_FILENAME)
    }

    private fun getLegacyPlaylistsFile(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, PLAYLISTS_FILENAME)
    }

    private fun getAppMusicDir(): File? {
        val context: Context = A2UApplication.appContext
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }
        return musicDir
    }

    // ==================== Cloud Sync Methods ====================

    /**
     * Returns all playlists (active AND deleted) for cloud sync.
     */
    fun getAllPlaylistsForSync(): List<SyncPlaylistEntry> {
        val result = mutableListOf<SyncPlaylistEntry>()

        // Active playlists
        for (playlist in playlistsCache) {
            val metadata = playlistMetadata[playlist.id]
            result.add(SyncPlaylistEntry(
                id = playlist.id,
                name = playlist.name,
                tracks = playlist.tracks.map { SyncPlaylistTrack(it.title, it.artist, it.album) },
                createdAt = metadata?.createdAt ?: System.currentTimeMillis(),
                modifiedAt = metadata?.modifiedAt ?: System.currentTimeMillis(),
                deletedAt = null
            ))
        }

        // Deleted playlists (soft-delete)
        for ((_, deleted) in deletedPlaylistsCache) {
            result.add(SyncPlaylistEntry(
                id = deleted.id,
                name = deleted.name,
                tracks = emptyList(),
                createdAt = deleted.createdAt,
                modifiedAt = deleted.deletedAt,  // Use deletedAt as last modification
                deletedAt = deleted.deletedAt
            ))
        }

        return result
    }

    /**
     * Creates a playlist from cloud sync.
     */
    suspend fun createPlaylistFromSync(
        id: String,
        name: String,
        tracks: List<SyncPlaylistTrack>,
        createdAt: Long,
        modifiedAt: Long
    ) {
        // Check if playlist already exists
        if (playlistsCache.any { it.id == id }) {
            // Update existing
            updatePlaylistFromSync(id, name, tracks, modifiedAt)
            return
        }

        val playlist = PlaylistData(
            id = id,
            name = name,
            tracks = tracks.map { PlaylistTrackEntry(it.title, it.artist, it.album) },
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(createdAt))
        )
        playlistsCache.add(playlist)
        playlistMetadata[id] = PlaylistSyncMetadata(createdAt = createdAt, modifiedAt = modifiedAt)
        deletedPlaylistsCache.remove(id)
        savePlaylists()
        notifyListeners()
        Log.d(TAG, "Created playlist from sync: $name ($id)")
    }

    /**
     * Updates a playlist from cloud sync.
     */
    suspend fun updatePlaylistFromSync(
        id: String,
        name: String,
        tracks: List<SyncPlaylistTrack>,
        modifiedAt: Long
    ) {
        val index = playlistsCache.indexOfFirst { it.id == id }
        if (index != -1) {
            playlistsCache[index] = playlistsCache[index].copy(
                name = name,
                tracks = tracks.map { PlaylistTrackEntry(it.title, it.artist, it.album) }
            )
            playlistMetadata[id]?.modifiedAt = modifiedAt
            savePlaylists()
            notifyListeners()
            Log.d(TAG, "Updated playlist from sync: $name ($id)")
        }
    }

    /**
     * Deletes a playlist from cloud sync.
     */
    suspend fun deletePlaylistFromSync(id: String, deletedTimestamp: Long) {
        val playlistToDelete = playlistsCache.find { it.id == id }
        val removed = playlistsCache.removeAll { it.id == id }
        if (removed && playlistToDelete != null) {
            val metadata = playlistMetadata.remove(id)
            deletedPlaylistsCache[id] = DeletedPlaylist(
                id = id,
                name = playlistToDelete.name,
                createdAt = metadata?.createdAt ?: System.currentTimeMillis(),
                deletedAt = deletedTimestamp
            )
            savePlaylists()
            notifyListeners()
            Log.d(TAG, "Deleted playlist from sync: ${playlistToDelete.name} ($id)")
        }
    }

    /**
     * Clears the deleted playlists cache after successful sync upload.
     */
    suspend fun clearDeletedPlaylistsCache() {
        deletedPlaylistsCache.clear()
        savePlaylists()
        Log.d(TAG, "Cleared deleted playlists cache after sync")
    }
}
