package com.Atom2Universe.app.music

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.Atom2Universe.app.music.model.MusicTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Gestionnaire de persistance de la file de lecture musique.
 *
 * Permet de sauvegarder et restaurer la queue de lecture pour que le widget
 * puisse reprendre la lecture même après un redémarrage du téléphone ou si l'app est tuée.
 *
 * La liste des pistes (lourde) vit dans un fichier à part, réécrit seulement quand elle
 * change, sur un fil dédié (écritures dans l'ordre, la dernière gagne). L'index, la position
 * et les modes sont de petites clés SharedPreferences écrites avec apply().
 */
object MusicQueuePersistence {

    private const val TAG = "MusicQueuePersistence"
    private const val PREFS_NAME = "music_queue_prefs"
    private const val TRACKS_FILENAME = "music_queue_tracks.json"
    private const val KEY_INDEX = "current_index"
    private const val KEY_POSITION = "position_ms"
    private const val KEY_SHUFFLE = "shuffle_enabled"
    private const val KEY_REPEAT = "repeat_mode"
    private const val KEY_PLAYLIST_ID = "playlist_id"
    private const val KEY_SAVED_AT = "saved_at"
    // Ancien format (toute la file en JSON dans une clé) : effacé, jamais converti
    private const val LEGACY_KEY_QUEUE_DATA = "queue_data"
    private const val LEGACY_KEY_QUEUE_DATA_BACKUP = "queue_data_backup"

    private val tracksWriter = Executors.newSingleThreadExecutor()
    // Liste à écrire ; une liste vide signifie « supprimer le fichier »
    private val pendingTracks = AtomicReference<List<PersistedTrack>?>(null)
    // Dernière liste connue (écrite ou lue) : évite de relire le fichier
    @Volatile
    private var knownTracks: List<PersistedTrack>? = null
    private var lastSavedPlaylist: List<MusicTrack>? = null
    @Volatile
    private var legacyChecked = false

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

    private fun prefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!legacyChecked) {
            legacyChecked = true
            if (prefs.contains(LEGACY_KEY_QUEUE_DATA) || prefs.contains(LEGACY_KEY_QUEUE_DATA_BACKUP)) {
                prefs.edit {
                    remove(LEGACY_KEY_QUEUE_DATA)
                    remove(LEGACY_KEY_QUEUE_DATA_BACKUP)
                }
            }
        }
        return prefs
    }

    private fun tracksFile(context: Context) = File(context.applicationContext.filesDir, TRACKS_FILENAME)

    /**
     * Sauvegarde la queue actuelle. La liste des pistes n'est réécrite que si elle a changé.
     */
    @Synchronized
    fun saveQueue(context: Context, playlist: List<MusicTrack>, currentIndex: Int,
                  positionMs: Long, shuffleEnabled: Boolean, repeatMode: MusicPlaybackHolder.RepeatMode,
                  playlistId: String? = null) {
        if (playlist.isEmpty()) {
            clearQueue(context)
            return
        }

        try {
            if (playlist !== lastSavedPlaylist && playlist != lastSavedPlaylist) {
                val tracks = playlist.map { PersistedTrack.fromMusicTrack(it) }
                lastSavedPlaylist = playlist
                knownTracks = tracks
                scheduleTracksWrite(context, tracks)
            }

            prefs(context).edit {
                putInt(KEY_INDEX, currentIndex)
                putLong(KEY_POSITION, positionMs)
                putBoolean(KEY_SHUFFLE, shuffleEnabled)
                putString(KEY_REPEAT, repeatMode.name)
                if (playlistId != null) putString(KEY_PLAYLIST_ID, playlistId) else remove(KEY_PLAYLIST_ID)
                putLong(KEY_SAVED_AT, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue", e)
        }
    }

    private fun scheduleTracksWrite(context: Context, tracks: List<PersistedTrack>) {
        val file = tracksFile(context)
        pendingTracks.set(tracks)
        tracksWriter.execute {
            val latest = pendingTracks.getAndSet(null) ?: return@execute
            try {
                if (latest.isEmpty()) {
                    file.delete()
                } else {
                    val array = JSONArray()
                    latest.forEach { array.put(it.toJson()) }
                    MusicFileWriter.writeTextAtomically(file, array.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing queue tracks", e)
            }
        }
    }

    private fun readTracksFile(context: Context): List<PersistedTrack> {
        val file = tracksFile(context)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        val tracks = ArrayList<PersistedTrack>(array.length())
        for (i in 0 until array.length()) {
            try {
                tracks.add(PersistedTrack.fromJson(array.getJSONObject(i)))
            } catch (trackError: Exception) {
                Log.e(TAG, "Error parsing track at index $i, skipping", trackError)
            }
        }
        return tracks
    }

    /**
     * Restaure la queue sauvegardée.
     * Retourne null si aucune queue n'est sauvegardée ou si les données sont invalides.
     */
    fun loadQueue(context: Context): SavedQueue? {
        try {
            val prefs = prefs(context)
            if (!prefs.contains(KEY_INDEX)) return null

            val tracks = knownTracks ?: readTracksFile(context).also { knownTracks = it }
            if (tracks.isEmpty()) {
                Log.w(TAG, "Queue loaded but no valid tracks found")
                return null
            }

            val repeatModeName = prefs.getString(KEY_REPEAT, null) ?: MusicPlaybackHolder.RepeatMode.OFF.name
            val repeatMode = try {
                MusicPlaybackHolder.RepeatMode.valueOf(repeatModeName)
            } catch (_: Exception) {
                Log.w(TAG, "Invalid repeat mode '$repeatModeName', defaulting to OFF")
                MusicPlaybackHolder.RepeatMode.OFF
            }

            return SavedQueue(
                tracks = tracks,
                currentIndex = prefs.getInt(KEY_INDEX, 0),
                positionMs = prefs.getLong(KEY_POSITION, 0),
                shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false),
                repeatMode = repeatMode,
                playlistId = prefs.getString(KEY_PLAYLIST_ID, null),
                savedAt = prefs.getLong(KEY_SAVED_AT, 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading queue", e)
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
    @Synchronized
    fun clearQueue(context: Context) {
        try {
            lastSavedPlaylist = null
            if (knownTracks?.isEmpty() != true) {
                knownTracks = emptyList()
                scheduleTracksWrite(context, emptyList())
            }
            prefs(context).edit {
                remove(KEY_INDEX)
                remove(KEY_POSITION)
                remove(KEY_SHUFFLE)
                remove(KEY_REPEAT)
                remove(KEY_PLAYLIST_ID)
                remove(KEY_SAVED_AT)
            }
            Log.d(TAG, "Queue cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }

    /**
     * Vérifie si une queue est sauvegardée.
     */
    fun hasQueue(context: Context): Boolean {
        return prefs(context).contains(KEY_INDEX)
    }

    /**
     * Met à jour uniquement la position de lecture (appelé périodiquement).
     */
    fun updatePosition(context: Context, positionMs: Long) {
        try {
            val prefs = prefs(context)
            if (!prefs.contains(KEY_INDEX)) return
            prefs.edit {
                putLong(KEY_POSITION, positionMs)
                putLong(KEY_SAVED_AT, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating position", e)
        }
    }

    /**
     * Met à jour l'index courant (appelé lors du changement de track).
     */
    fun updateCurrentIndex(context: Context, currentIndex: Int) {
        try {
            val prefs = prefs(context)
            if (!prefs.contains(KEY_INDEX)) return
            prefs.edit {
                putInt(KEY_INDEX, currentIndex)
                putLong(KEY_POSITION, 0L)  // Reset position on track change
                putLong(KEY_SAVED_AT, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current index", e)
        }
    }
}
