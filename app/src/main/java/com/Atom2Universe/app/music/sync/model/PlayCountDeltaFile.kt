package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Daily play count delta file for a specific device.
 *
 * File format: playcounts_device_{deviceId}_{date}.json
 * Example: playcounts_device_abc123_2026-01-15.json
 *
 * Contains incremental play counts since the last sync.
 */
data class PlayCountDeltaFile(
    val deviceId: String,
    val date: String,
    val createdAt: Long,
    val deltas: List<PlayCountDelta>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("date", date)
            put("createdAt", createdAt)
            put("deltas", JSONArray().apply {
                deltas.forEach { delta ->
                    put(delta.toJson())
                }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlayCountDeltaFile {
            val deltasArray = json.optJSONArray("deltas") ?: JSONArray()
            val deltas = mutableListOf<PlayCountDelta>()
            for (i in 0 until deltasArray.length()) {
                deltas.add(PlayCountDelta.fromJson(deltasArray.getJSONObject(i)))
            }

            return PlayCountDeltaFile(
                deviceId = json.optString("deviceId", ""),
                date = json.optString("date", ""),
                createdAt = json.optLong("createdAt", 0),
                deltas = deltas
            )
        }

        /**
         * Generates the filename for a delta file.
         */
        fun generateFilename(deviceId: String, date: String): String {
            return "playcounts_device_${deviceId}_${date}.json"
        }
    }
}

/**
 * A single track's play count delta.
 */
data class PlayCountDelta(
    val key: String,
    val artist: String,
    val title: String,
    val album: String,
    val delta: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("key", key)
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("delta", delta)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlayCountDelta {
            return PlayCountDelta(
                key = json.optString("key", ""),
                artist = json.optString("artist", ""),
                title = json.optString("title", ""),
                album = json.optString("album", ""),
                delta = json.optLong("delta", 0)
            )
        }
    }
}

/**
 * Consolidated baseline file with all-time play counts.
 *
 * File: baseline_playcounts_{date}.json
 */
data class PlayCountBaselineFile(
    val createdAt: Long,
    val tracks: Map<String, PlayCountBaseline>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("createdAt", createdAt)
            put("tracks", JSONObject().apply {
                tracks.forEach { (key, baseline) ->
                    put(key, baseline.toJson())
                }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlayCountBaselineFile {
            val tracksJson = json.optJSONObject("tracks") ?: JSONObject()
            val tracks = mutableMapOf<String, PlayCountBaseline>()
            tracksJson.keys().forEach { key ->
                tracks[key] = PlayCountBaseline.fromJson(tracksJson.getJSONObject(key))
            }

            return PlayCountBaselineFile(
                createdAt = json.optLong("createdAt", 0),
                tracks = tracks
            )
        }
    }
}

/**
 * Baseline play count for a single track.
 */
data class PlayCountBaseline(
    val artist: String,
    val title: String,
    val album: String,
    val totalPlayCount: Long,
    val lastPlayed: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("totalPlayCount", totalPlayCount)
            put("lastPlayed", lastPlayed)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlayCountBaseline {
            return PlayCountBaseline(
                artist = json.optString("artist", ""),
                title = json.optString("title", ""),
                album = json.optString("album", ""),
                totalPlayCount = json.optLong("totalPlayCount", 0),
                lastPlayed = json.optLong("lastPlayed", 0)
            )
        }
    }
}
