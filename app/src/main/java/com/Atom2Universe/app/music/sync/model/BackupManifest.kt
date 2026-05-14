package com.Atom2Universe.app.music.sync.model

import org.json.JSONObject

/**
 * Manifest file for the backup folder on Google Drive.
 * Contains metadata about the backup and summary of contents.
 *
 * File: backup/backup_manifest.json
 */
data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String,
    val deviceName: String,
    val contents: BackupContents
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("createdAt", createdAt)
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("contents", contents.toJson())
        }
    }

    companion object {
        const val FILENAME = "backup_manifest.json"

        fun fromJson(json: JSONObject): BackupManifest {
            val contentsJson = json.optJSONObject("contents") ?: JSONObject()
            return BackupManifest(
                version = json.optInt("version", 1),
                createdAt = json.optLong("createdAt", 0),
                deviceId = json.optString("deviceId", ""),
                deviceName = json.optString("deviceName", "Unknown"),
                contents = BackupContents.fromJson(contentsJson)
            )
        }
    }
}

/**
 * Summary of backup contents for user display.
 */
data class BackupContents(
    val playCountsCount: Int = 0,
    val trackFavoritesCount: Int = 0,
    val albumFavoritesCount: Int = 0,
    val artistCustomizationsCount: Int = 0,
    val playlistsCount: Int = 0,
    val artistImagesCount: Int = 0,
    val lyricsCount: Int = 0,
    val hasPreferences: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("playCountsCount", playCountsCount)
            put("trackFavoritesCount", trackFavoritesCount)
            put("albumFavoritesCount", albumFavoritesCount)
            put("artistCustomizationsCount", artistCustomizationsCount)
            put("playlistsCount", playlistsCount)
            put("artistImagesCount", artistImagesCount)
            put("lyricsCount", lyricsCount)
            put("hasPreferences", hasPreferences)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): BackupContents {
            return BackupContents(
                playCountsCount = json.optInt("playCountsCount", 0),
                trackFavoritesCount = json.optInt("trackFavoritesCount", 0),
                albumFavoritesCount = json.optInt("albumFavoritesCount", 0),
                artistCustomizationsCount = json.optInt("artistCustomizationsCount", 0),
                playlistsCount = json.optInt("playlistsCount", 0),
                artistImagesCount = json.optInt("artistImagesCount", 0),
                lyricsCount = json.optInt("lyricsCount", 0),
                hasPreferences = json.optBoolean("hasPreferences", false)
            )
        }
    }
}
