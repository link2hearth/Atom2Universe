package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Artist favorites sync file stored on Google Drive.
 *
 * File: artist_favorites_sync.json
 *
 * Uses soft-delete with timestamps to track add/remove history.
 * This allows proper merge with last-write-wins conflict resolution.
 *
 * Note: This syncs BOTH regular artists AND album artists since they use
 * the same favorite system in ArtistCustomizationManager.
 */
data class ArtistFavoritesSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val favorites: List<SyncArtistFavoriteEntry>
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
    fun getActiveFavorites(): List<SyncArtistFavoriteEntry> {
        return favorites.filter { it.isActive() }
    }

    companion object {
        const val FILENAME = "artist_favorites_sync.json"

        fun fromJson(json: JSONObject): ArtistFavoritesSyncFile {
            val favsArray = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = mutableListOf<SyncArtistFavoriteEntry>()
            for (i in 0 until favsArray.length()) {
                favorites.add(SyncArtistFavoriteEntry.fromJson(favsArray.getJSONObject(i)))
            }

            return ArtistFavoritesSyncFile(
                version = json.optInt("version", 1),
                lastModified = json.optLong("lastModified", 0),
                favorites = favorites
            )
        }

        fun empty(): ArtistFavoritesSyncFile {
            return ArtistFavoritesSyncFile(
                version = 1,
                lastModified = 0,
                favorites = emptyList()
            )
        }
    }
}

/**
 * A single artist favorite entry with timestamps.
 */
data class SyncArtistFavoriteEntry(
    val key: String,           // artistName (lowercased, trimmed)
    val artistName: String,
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
            put("addedAt", addedAt)
            removedAt?.let { put("removedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncArtistFavoriteEntry {
            return SyncArtistFavoriteEntry(
                key = json.optString("key", ""),
                artistName = json.optString("artistName", ""),
                addedAt = json.optLong("addedAt", 0),
                removedAt = if (json.has("removedAt")) json.optLong("removedAt") else null
            )
        }

        /**
         * Creates a new active artist favorite entry.
         */
        @Suppress("unused")
        fun createActive(artistName: String): SyncArtistFavoriteEntry {
            val key = artistName.trim().lowercase()
            return SyncArtistFavoriteEntry(
                key = key,
                artistName = artistName,
                addedAt = System.currentTimeMillis(),
                removedAt = null
            )
        }

        /**
         * Generates a unique key for an artist favorite.
         */
        @Suppress("unused")
        fun generateKey(artistName: String): String {
            return artistName.trim().lowercase()
        }
    }
}
