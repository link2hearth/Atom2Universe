package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lyrics sync file stored on Google Drive.
 *
 * File: lyrics.json
 *
 * Uses last-write-wins conflict resolution based on modifiedAt timestamp.
 * Only stores lyrics that have been manually edited or fetched by user.
 */
data class LyricsSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val lyrics: List<SyncLyricsEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("lastModified", lastModified)
            put("lyrics", JSONArray().apply {
                lyrics.forEach { entry ->
                    put(entry.toJson())
                }
            })
        }
    }

    companion object {
        const val FILENAME = "lyrics.json"

        fun fromJson(json: JSONObject): LyricsSyncFile {
            val lyricsArray = json.optJSONArray("lyrics") ?: JSONArray()
            val lyrics = mutableListOf<SyncLyricsEntry>()
            for (i in 0 until lyricsArray.length()) {
                lyrics.add(SyncLyricsEntry.fromJson(lyricsArray.getJSONObject(i)))
            }

            return LyricsSyncFile(
                version = json.optInt("version", 1),
                lastModified = json.optLong("lastModified", 0),
                lyrics = lyrics
            )
        }

        fun empty(): LyricsSyncFile {
            return LyricsSyncFile(
                version = 1,
                lastModified = 0,
                lyrics = emptyList()
            )
        }
    }
}

/**
 * A single lyrics entry for sync.
 * Supports soft-delete via deletedAt timestamp for proper cross-device sync.
 */
data class SyncLyricsEntry(
    val key: String,
    val artist: String,
    val title: String,
    val album: String,
    val lyrics: String,
    val source: String,
    val isSynced: Boolean,
    val modifiedAt: Long,
    val deletedAt: Long? = null  // Soft-delete timestamp
) {
    /**
     * Returns true if this lyrics entry is currently active (not deleted).
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
            put("key", key)
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("lyrics", lyrics)
            put("source", source)
            put("isSynced", isSynced)
            put("modifiedAt", modifiedAt)
            deletedAt?.let { put("deletedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncLyricsEntry {
            return SyncLyricsEntry(
                key = json.optString("key", ""),
                artist = json.optString("artist", ""),
                title = json.optString("title", ""),
                album = json.optString("album", ""),
                lyrics = json.optString("lyrics", ""),
                source = json.optString("source", "unknown"),
                isSynced = json.optBoolean("isSynced", false),
                modifiedAt = json.optLong("modifiedAt", 0),
                deletedAt = if (json.has("deletedAt")) json.optLong("deletedAt") else null
            )
        }
    }
}
