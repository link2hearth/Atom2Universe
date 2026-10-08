package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites sync file stored on Google Drive.
 *
 * File: favorites.json
 *
 * Uses soft-delete with timestamps to track add/remove history.
 * This allows proper merge with last-write-wins conflict resolution.
 */
data class FavoritesSyncFile(
    val version: Int = 2,
    val lastModified: Long,
    val favorites: List<SyncFavoriteEntry>
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
    fun getActiveFavorites(): List<SyncFavoriteEntry> {
        return favorites.filter { it.isActive() }
    }

    companion object {
        const val FILENAME = "favorites.json"

        fun fromJson(json: JSONObject): FavoritesSyncFile {
            val favsArray = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = mutableListOf<SyncFavoriteEntry>()
            for (i in 0 until favsArray.length()) {
                favorites.add(SyncFavoriteEntry.fromJson(favsArray.getJSONObject(i)))
            }

            return FavoritesSyncFile(
                version = json.optInt("version", 2),
                lastModified = json.optLong("lastModified", 0),
                favorites = favorites
            )
        }

        fun empty(): FavoritesSyncFile {
            return FavoritesSyncFile(
                version = 2,
                lastModified = 0,
                favorites = emptyList()
            )
        }
    }
}

/**
 * A single favorite entry with timestamps.
 */
data class SyncFavoriteEntry(
    val key: String,
    val artist: String,
    val title: String,
    val album: String,
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
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("addedAt", addedAt)
            removedAt?.let { put("removedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncFavoriteEntry {
            return SyncFavoriteEntry(
                key = json.optString("key", ""),
                artist = json.optString("artist", ""),
                title = json.optString("title", ""),
                album = json.optString("album", ""),
                addedAt = json.optLong("addedAt", 0),
                removedAt = if (json.has("removedAt")) json.optLong("removedAt") else null
            )
        }

        /**
         * Creates a new active favorite entry.
         */
        fun createActive(
            artist: String,
            title: String,
            album: String
        ): SyncFavoriteEntry {
            val key = "${artist.trim().lowercase()}|${title.trim().lowercase()}|${album.trim().lowercase()}"
            return SyncFavoriteEntry(
                key = key,
                artist = artist,
                title = title,
                album = album,
                addedAt = System.currentTimeMillis(),
                removedAt = null
            )
        }
    }
}
