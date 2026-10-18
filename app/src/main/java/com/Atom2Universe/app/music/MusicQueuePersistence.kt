package com.Atom2Universe.app.music

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.Atom2Universe.app.music.model.MusicTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gestionnaire de persistance de la file de lecture musique.
 *
 * Permet de sauvegarder et restaurer la queue de lecture pour que le widget
 * puisse reprendre la lecture même après un redémarrage du téléphone ou si l'app est tuée.
 *
 * Version 2: Sauvegarde les données complètes des tracks (pas juste les clés).
 * Ainsi le widget peut restaurer la queue même sans MusicLibrary chargée.
 */
object MusicQueuePersistence {

    private const val TAG = "MusicQueuePersistence"
    private const val PREFS_NAME = "music_queue_prefs"
    private const val KEY_QUEUE_DATA = "queue_data"
    // Bug 5.21: Backup key for migration recovery
    private const val KEY_QUEUE_DATA_BACKUP = "queue_data_backup"
    private const val VERSION = 2

    /**
     * Données d'un track sauvegardé.
     */
    data class PersistedTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String?,
        val duration: Long,
        val uri: String,
        val albumArtUri: String?,
        val filePath: String?
    ) {
        fun toMusicTrack(): MusicTrack {
            // Utiliser le filePath pour créer une URI file:// qui fonctionne après restart
            // Les URIs content:// de MediaStore ne sont pas toujours accessibles après kill de l'app
            val trackUri = if (filePath != null && File(filePath).exists()) {
                File(filePath).toUri()
            } else {
                uri.toUri()
            }

            return MusicTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                duration = duration,
                uri = trackUri,
                albumArtUri = albumArtUri?.toUri(),
                filePath = filePath
            )
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("title", title)
                put("artist", artist)
                put("album", album)
                if (albumArtist != null) put("albumArtist", albumArtist)
                put("duration", duration)
                put("uri", uri)
                if (albumArtUri != null) put("albumArtUri", albumArtUri)
                if (filePath != null) put("filePath", filePath)
            }
        }

        companion object {
            fun fromMusicTrack(track: MusicTrack): PersistedTrack {
                return PersistedTrack(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumArtist = track.albumArtist,
                    duration = track.duration,
                    uri = track.uri.toString(),
                    albumArtUri = track.albumArtUri?.toString(),
                    filePath = track.filePath
                )
            }

            fun fromJson(json: JSONObject): PersistedTrack {
                return PersistedTrack(
                    id = json.optLong("id", 0),
                    title = json.optString("title", ""),
                    artist = json.optString("artist", ""),
                    album = json.optString("album", ""),
                    albumArtist = if (json.has("albumArtist")) json.optString("albumArtist") else null,
                    duration = json.optLong("duration", 0),
                    uri = json.optString("uri", ""),
                    albumArtUri = if (json.has("albumArtUri")) json.optString("albumArtUri") else null,
                    filePath = if (json.has("filePath")) json.optString("filePath") else null
                )
            }
        }
    }

    data class SavedQueue(
        val tracks: List<PersistedTrack>,
        val currentIndex: Int,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: MusicPlaybackHolder.RepeatMode,
        val playlistId: String?,
        val savedAt: Long
    )

    /**
     * Sauvegarde la queue actuelle avec toutes les données des tracks.
     * Bug 5.18: Uses commit() instead of apply() and writes atomically
     * to ensure data integrity even if the app is killed during save.
     */
    fun saveQueue(context: Context, playlist: List<MusicTrack>, currentIndex: Int,
                  positionMs: Long, shuffleEnabled: Boolean, repeatMode: MusicPlaybackHolder.RepeatMode,
                  playlistId: String? = null) {
        if (playlist.isEmpty()) {
            clearQueue(context)
            return
        }

        try {
            val json = JSONObject().apply {
                put("version", VERSION)
                put("currentIndex", currentIndex)
                put("positionMs", positionMs)
                put("shuffleEnabled", shuffleEnabled)
                put("repeatMode", repeatMode.name)
                put("savedAt", System.currentTimeMillis())
                if (playlistId != null) {
                    put("playlistId", playlistId)
                }

                // Sauvegarder les données complètes des tracks
                val tracksArray = JSONArray()
                for (track in playlist) {
                    val persistedTrack = PersistedTrack.fromMusicTrack(track)
                    tracksArray.put(persistedTrack.toJson())
                }
                put("tracks", tracksArray)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Bug 5.18: Use commit() instead of apply() for critical queue data
            // This ensures data is written synchronously before returning.
            // While slightly slower, it guarantees the queue is persisted even if
            // the app is killed immediately after.
            @Suppress("ApplySharedPref")
            val success = prefs.edit().putString(KEY_QUEUE_DATA, json.toString()).commit()

            if (success) {
                Log.d(TAG, "Queue saved: ${playlist.size} tracks, index=$currentIndex, pos=$positionMs, playlistId=$playlistId")
            } else {
                Log.e(TAG, "Failed to commit queue save")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue", e)
        }
    }

    /**
     * Restaure la queue sauvegardée.
     * Retourne null si aucune queue n'est sauvegardée ou si les données sont invalides.
     * Bug 5.21: Creates backup before migration and logs errors properly.
     */
    fun loadQueue(context: Context): SavedQueue? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_QUEUE_DATA, null) ?: return null

            val json = JSONObject(jsonString)
            val version = json.optInt("version", 0)

            // Bug 5.21: Migration with backup
            if (version < VERSION) {
                Log.w(TAG, "Queue version mismatch ($version < $VERSION), attempting migration")

                // Create backup before clearing
                try {
                    prefs.edit {
                        putString(KEY_QUEUE_DATA_BACKUP, jsonString)
                    }
                    Log.d(TAG, "Created backup of old queue data (version $version)")
                } catch (backupError: Exception) {
                    Log.e(TAG, "Failed to create backup before migration", backupError)
                }

                // Try to migrate old format if possible
                val migratedQueue = tryMigrateOldFormat(json, version)
                if (migratedQueue != null) {
                    Log.i(TAG, "Successfully migrated queue from version $version to $VERSION")
                    // Remove backup after successful migration
                    prefs.edit { remove(KEY_QUEUE_DATA_BACKUP) }
                    return migratedQueue
                }

                // Migration failed, clear and log
                Log.w(TAG, "Migration failed, clearing old queue data. Backup preserved in $KEY_QUEUE_DATA_BACKUP")
                clearQueue(context)
                return null
            }

            val tracksArray = json.optJSONArray("tracks") ?: return null
            val tracks = mutableListOf<PersistedTrack>()
            for (i in 0 until tracksArray.length()) {
                try {
                    val trackJson = tracksArray.getJSONObject(i)
                    tracks.add(PersistedTrack.fromJson(trackJson))
                } catch (trackError: Exception) {
                    // Bug 5.21: Log individual track parsing errors but continue
                    Log.e(TAG, "Error parsing track at index $i, skipping", trackError)
                }
            }

            if (tracks.isEmpty()) {
                Log.w(TAG, "Queue loaded but no valid tracks found")
                return null
            }

            val repeatModeName = json.optString("repeatMode", "OFF")
            val repeatMode = try {
                MusicPlaybackHolder.RepeatMode.valueOf(repeatModeName)
            } catch (_: Exception) {
                Log.w(TAG, "Invalid repeat mode '$repeatModeName', defaulting to OFF")
                MusicPlaybackHolder.RepeatMode.OFF
            }

            val playlistId = if (json.has("playlistId")) json.optString("playlistId") else null

            return SavedQueue(
                tracks = tracks,
                currentIndex = json.optInt("currentIndex", 0),
                positionMs = json.optLong("positionMs", 0),
                shuffleEnabled = json.optBoolean("shuffleEnabled", false),
                repeatMode = repeatMode,
                playlistId = playlistId,
                savedAt = json.optLong("savedAt", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading queue", e)
            return null
        }
    }

    /**
     * Bug 5.21: Attempts to migrate old queue format to current version.
     * Returns migrated SavedQueue or null if migration fails.
     */
    private fun tryMigrateOldFormat(json: JSONObject, oldVersion: Int): SavedQueue? {
        try {
            val tracksArray = json.optJSONArray("tracks") ?: run {
                Log.w(TAG, "No tracks array found for version $oldVersion, cannot migrate")
                return null
            }
            val tracks = mutableListOf<PersistedTrack>()
            for (i in 0 until tracksArray.length()) {
                try {
                    val trackJson = tracksArray.getJSONObject(i)
                    tracks.add(PersistedTrack.fromJson(trackJson))
                } catch (trackError: Exception) {
                    Log.e(TAG, "Error parsing track at index $i during migration, skipping", trackError)
                }
            }

            if (tracks.isEmpty()) {
                Log.w(TAG, "No valid tracks found during migration from version $oldVersion")
                return null
            }

            // Version 1 had a different format with track IDs only
            // Version 2 has full track data
            if (oldVersion == 1) {
                // Version 1 stored track IDs in "trackIds" array
                // We can't migrate without MusicLibrary, so return null
                Log.w(TAG, "Cannot migrate version 1 queue (track IDs only) without MusicLibrary")
                return null
            }

            val repeatModeName = json.optString("repeatMode", "OFF")
            val repeatMode = try {
                MusicPlaybackHolder.RepeatMode.valueOf(repeatModeName)
            } catch (_: Exception) {
                Log.w(TAG, "Invalid repeat mode '$repeatModeName' during migration, defaulting to OFF")
                MusicPlaybackHolder.RepeatMode.OFF
            }

            val playlistId = if (json.has("playlistId")) json.optString("playlistId") else null

            return SavedQueue(
                tracks = tracks,
                currentIndex = json.optInt("currentIndex", 0),
                positionMs = json.optLong("positionMs", 0),
                shuffleEnabled = json.optBoolean("shuffleEnabled", false),
                repeatMode = repeatMode,
                playlistId = playlistId,
                savedAt = json.optLong("savedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during queue migration from version $oldVersion", e)
            return null
        }
    }

    /**
     * Convertit les tracks persistés en MusicTrack.
     * Ne nécessite PAS MusicLibrary - fonctionne même si l'app est tuée.
     */
    fun tracksFromSavedQueue(savedQueue: SavedQueue): List<MusicTrack> {
        return savedQueue.tracks.map { it.toMusicTrack() }
    }

    /**
     * Efface la queue sauvegardée.
     */
    fun clearQueue(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_QUEUE_DATA) }
            Log.d(TAG, "Queue cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }

    /**
     * Vérifie si une queue est sauvegardée.
     */
    fun hasQueue(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_QUEUE_DATA)
    }

    /**
     * Met à jour uniquement la position de lecture (appelé périodiquement).
     * Bug fix: Utilise commit() au lieu de apply() pour garantir la persistance
     * en cas de kill de l'app, cohérent avec saveQueue().
     */
    fun updatePosition(context: Context, positionMs: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_QUEUE_DATA, null) ?: return

            val json = JSONObject(jsonString)
            json.put("positionMs", positionMs)
            json.put("savedAt", System.currentTimeMillis())

            prefs.edit(commit = true) { putString(KEY_QUEUE_DATA, json.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating position", e)
        }
    }

    /**
     * Met à jour l'index courant (appelé lors du changement de track).
     * Bug fix: Utilise commit() au lieu de apply() pour garantir la persistance
     * en cas de kill de l'app, cohérent avec saveQueue().
     */
    fun updateCurrentIndex(context: Context, currentIndex: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_QUEUE_DATA, null) ?: return

            val json = JSONObject(jsonString)
            json.put("currentIndex", currentIndex)
            json.put("positionMs", 0L)  // Reset position on track change
            json.put("savedAt", System.currentTimeMillis())

            prefs.edit(commit = true) { putString(KEY_QUEUE_DATA, json.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current index", e)
        }
    }
}
