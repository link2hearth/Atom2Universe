package com.Atom2Universe.app.music.sync.model

import org.json.JSONObject

/**
 * Master index file stored on Google Drive.
 * Contains metadata about all synced devices and data timestamps.
 *
 * File: sync_manifest.json
 */
data class SyncManifest(
    val version: Int = 1,
    val schemaVersion: String = "1.0",
    val lastSyncTimestamp: Long = 0,
    val devices: Map<String, DeviceInfo> = emptyMap(),
    val playCountsBaseline: PlayCountsBaseline? = null,
    val favoritesLastModified: Long = 0,
    val lyricsLastModified: Long = 0,
    val primaryDeviceId: String? = null,        // Device that uploads full backup
    val backupLastModified: Long = 0            // Timestamp of last backup
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("schemaVersion", schemaVersion)
            put("lastSyncTimestamp", lastSyncTimestamp)

            put("devices", JSONObject().apply {
                devices.forEach { (id, info) ->
                    put(id, info.toJson())
                }
            })

            playCountsBaseline?.let {
                put("playCounts", JSONObject().apply {
                    put("baselineTimestamp", it.baselineTimestamp)
                    put("baselineFile", it.baselineFile)
                })
            }

            put("favorites", JSONObject().apply {
                put("lastModified", favoritesLastModified)
            })

            put("lyrics", JSONObject().apply {
                put("lastModified", lyricsLastModified)
            })

            put("backup", JSONObject().apply {
                primaryDeviceId?.let { put("primaryDeviceId", it) }
                put("lastModified", backupLastModified)
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncManifest {
            val devicesJson = json.optJSONObject("devices") ?: JSONObject()
            val devices = mutableMapOf<String, DeviceInfo>()
            devicesJson.keys().forEach { key ->
                devices[key] = DeviceInfo.fromJson(devicesJson.getJSONObject(key))
            }

            val playCountsJson = json.optJSONObject("playCounts")
            val playCountsBaseline = playCountsJson?.let {
                PlayCountsBaseline(
                    baselineTimestamp = it.optLong("baselineTimestamp", 0),
                    baselineFile = it.optString("baselineFile", "")
                )
            }

            val favoritesJson = json.optJSONObject("favorites")
            val lyricsJson = json.optJSONObject("lyrics")
            val backupJson = json.optJSONObject("backup")

            return SyncManifest(
                version = json.optInt("version", 1),
                schemaVersion = json.optString("schemaVersion", "1.0"),
                lastSyncTimestamp = json.optLong("lastSyncTimestamp", 0),
                devices = devices,
                playCountsBaseline = playCountsBaseline,
                favoritesLastModified = favoritesJson?.optLong("lastModified", 0) ?: 0,
                lyricsLastModified = lyricsJson?.optLong("lastModified", 0) ?: 0,
                primaryDeviceId = backupJson?.optString("primaryDeviceId")?.takeIf { it.isNotBlank() },
                backupLastModified = backupJson?.optLong("lastModified", 0) ?: 0
            )
        }
    }
}

/**
 * Information about a synced device.
 */
data class DeviceInfo(
    val name: String,
    val lastSeen: Long,
    val lastDeltaDate: String?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("lastSeen", lastSeen)
            lastDeltaDate?.let { put("lastDeltaDate", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DeviceInfo {
            return DeviceInfo(
                name = json.optString("name", "Unknown"),
                lastSeen = json.optLong("lastSeen", 0),
                lastDeltaDate = json.optString("lastDeltaDate").takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Reference to the consolidated baseline file.
 */
data class PlayCountsBaseline(
    val baselineTimestamp: Long,
    val baselineFile: String
)
