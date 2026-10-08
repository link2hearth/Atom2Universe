package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Album favorites sync file stored on Google Drive.
 *
 * File: album_favorites_sync.json
 *
 * Uses soft-delete with timestamps to track add/remove history.
 * This allows proper merge with last-write-wins conflict resolution.
 */
data class AlbumFavoritesSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val favorites: List<SyncAlbumFavoriteEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("lastModified", lastModified)
            put("favorites", JSONArray().apply {
                favorites.forEach { fav ->
                    put(fav.toJson())
                }
            })
        }
    }

    /**
     * Returns only the currently active favorites (not removed).
     */
    @Suppress("unused")
    fun getActiveFavorites(): List<SyncAlbumFavoriteEntry> {
        return favorites.filter { it.isActive() }
    }

    companion object {
        const val FILENAME = "album_favorites_sync.json"

        fun fromJson(json: JSONObject): AlbumFavoritesSyncFile {
            val favsArray = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = mutableListOf<SyncAlbumFavoriteEntry>()
            for (i in 0 until favsArray.length()) {
                favorites.add(SyncAlbumFavoriteEntry.fromJson(favsArray.getJSONObject(i)))
            }

            return AlbumFavoritesSyncFile(
                version = json.optInt("version", 1),
                lastModified = json.optLong("lastModified", 0),
                favorites = favorites
            )
        }

        fun empty(): AlbumFavoritesSyncFile {
            return AlbumFavoritesSyncFile(
                version = 1,
                lastModified = 0,
                favorites = emptyList()
            )
        }
    }
}

/**
 * A single album favorite entry with timestamps.
 */
data class SyncAlbumFavoriteEntry(
    val key: String,           // "artistName|albumName" (lowercased)
    val artistName: String,
    val albumName: String,
    val addedAt: Long,
    val removedAt: Long? = null
) {
    /**
     * Returns true if this favorite is currently active (not removed).
     */
    fun isActive(): Boolean {
        return removedAt == null || addedAt > removedAt
    }

    /**
     * Returns the most recent timestamp (added or removed).
     */
    fun getLastModifiedTimestamp(): Long {
        return maxOf(addedAt, removedAt ?: 0)
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("key", key)
            put("artistName", artistName)
            put("albumName", albumName)
            put("addedAt", addedAt)
            removedAt?.let { put("removedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncAlbumFavoriteEntry {
            return SyncAlbumFavoriteEntry(
                key = json.optString("key", ""),
                artistName = json.optString("artistName", ""),
                albumName = json.optString("albumName", ""),
                addedAt = json.optLong("addedAt", 0),
                removedAt = if (json.has("removedAt")) json.optLong("removedAt") else null
            )
        }

        /**
         * Creates a new active album favorite entry.
         */
        @Suppress("unused")
        fun createActive(artistName: String, albumName: String): SyncAlbumFavoriteEntry {
            val key = "${artistName.trim().lowercase()}|${albumName.trim().lowercase()}"
            return SyncAlbumFavoriteEntry(
                key = key,
                artistName = artistName,
                albumName = albumName,
                addedAt = System.currentTimeMillis(),
                removedAt = null
            )
        }

        /**
         * Generates a unique key for an album favorite.
         */
        @Suppress("unused")
        fun generateKey(artistName: String, albumName: String): String {
            return "${artistName.trim().lowercase()}|${albumName.trim().lowercase()}"
        }
    }
}
