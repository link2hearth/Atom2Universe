package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlists sync file stored on Google Drive.
 *
 * File: playlists_sync.json
 *
 * Uses soft-delete with timestamps to track create/modify/delete history.
 * This allows proper merge with last-write-wins conflict resolution.
 */
data class PlaylistsSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val playlists: List<SyncPlaylistEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("lastModified", lastModified)
            put("playlists", JSONArray().apply {
                playlists.forEach { playlist ->
                    put(playlist.toJson())
                }
            })
        }
    }

    /**
     * Returns only the currently active playlists (not deleted).
     */
    @Suppress("unused")
    fun getActivePlaylists(): List<SyncPlaylistEntry> {
        return playlists.filter { it.isActive() }
    }

    companion object {
        const val FILENAME = "playlists_sync.json"

        fun fromJson(json: JSONObject): PlaylistsSyncFile {
            val playlistsArray = json.optJSONArray("playlists") ?: JSONArray()
            val playlists = mutableListOf<SyncPlaylistEntry>()
            for (i in 0 until playlistsArray.length()) {
                playlists.add(SyncPlaylistEntry.fromJson(playlistsArray.getJSONObject(i)))
            }

            return PlaylistsSyncFile(
                version = json.optInt("version", 1),
                lastModified = json.optLong("lastModified", 0),
                playlists = playlists
            )
        }

        fun empty(): PlaylistsSyncFile {
            return PlaylistsSyncFile(
                version = 1,
                lastModified = 0,
                playlists = emptyList()
            )
        }
    }
}

/**
 * A single playlist entry with timestamps and soft-delete support.
 */
data class SyncPlaylistEntry(
    val id: String,
    val name: String,
    val tracks: List<SyncPlaylistTrack>,
    val createdAt: Long,
    val modifiedAt: Long,
    val deletedAt: Long? = null
) {
    /**
     * Returns true if this playlist is currently active (not deleted).
     */
    fun isActive(): Boolean {
        return deletedAt == null || modifiedAt > deletedAt
    }

    /**
     * Returns the most recent timestamp (modified or deleted).
     */
    fun getLastModifiedTimestamp(): Long {
        return maxOf(modifiedAt, deletedAt ?: 0)
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("tracks", JSONArray().apply {
                tracks.forEach { track ->
                    put(track.toJson())
                }
            })
            put("createdAt", createdAt)
            put("modifiedAt", modifiedAt)
            deletedAt?.let { put("deletedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncPlaylistEntry {
            val tracksArray = json.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableListOf<SyncPlaylistTrack>()
            for (i in 0 until tracksArray.length()) {
                tracks.add(SyncPlaylistTrack.fromJson(tracksArray.getJSONObject(i)))
            }

            return SyncPlaylistEntry(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                tracks = tracks,
                createdAt = json.optLong("createdAt", 0),
                modifiedAt = json.optLong("modifiedAt", 0),
                deletedAt = if (json.has("deletedAt")) json.optLong("deletedAt") else null
            )
        }
    }
}

/**
 * A track entry within a playlist (metadata-based).
 */
data class SyncPlaylistTrack(
    val title: String,
    val artist: String,
    val album: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncPlaylistTrack {
            return SyncPlaylistTrack(
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                album = json.optString("album", "")
            )
        }
    }
}
