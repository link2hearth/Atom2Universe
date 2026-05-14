package com.Atom2Universe.app.stats.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modèle pour la synchronisation des sessions d'utilisation avec Google Drive.
 * Format JSON pour stocker toutes les sessions de tous les appareils.
 */
data class UsageSessionsSyncFile(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val sessions: List<SyncUsageSession>
) {
    companion object {
        fun fromJson(json: String): UsageSessionsSyncFile {
            val obj = JSONObject(json)
            val version = obj.getInt("version")
            val lastModified = obj.getLong("lastModified")

            val sessionsArray = obj.getJSONArray("sessions")
            val sessions = mutableListOf<SyncUsageSession>()

            for (i in 0 until sessionsArray.length()) {
                sessions.add(SyncUsageSession.fromJson(sessionsArray.getJSONObject(i)))
            }

            return UsageSessionsSyncFile(version, lastModified, sessions)
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("lastModified", lastModified)

        val sessionsArray = JSONArray()
        sessions.forEach { session ->
            sessionsArray.put(session.toJson())
        }
        obj.put("sessions", sessionsArray)

        return obj.toString()
    }
}

/**
 * Session d'utilisation synchronisée.
 * Contient toutes les données nécessaires pour merger les sessions entre appareils.
 */
data class SyncUsageSession(
    val id: String,  // UUID unique pour éviter les doublons
    val deviceId: String,  // ID de l'appareil qui a créé cette session
    val moduleType: String,  // "music", "midi", "radio"
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,

    // Métadonnées pour musique
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackAlbum: String? = null,
    val trackAlbumArtist: String? = null,

    // Métadonnées pour MIDI
    val midiFileName: String? = null,
    val practiceScore: Float? = null,

    // Métadonnées pour radio
    val radioStationName: String? = null
) {
    companion object {
        fun fromJson(obj: JSONObject): SyncUsageSession {
            return SyncUsageSession(
                id = obj.getString("id"),
                deviceId = obj.getString("deviceId"),
                moduleType = obj.getString("moduleType"),
                startTimestamp = obj.getLong("startTimestamp"),
                endTimestamp = obj.getLong("endTimestamp"),
                durationMs = obj.getLong("durationMs"),
                trackTitle = obj.optString("trackTitle").takeIf { it.isNotEmpty() },
                trackArtist = obj.optString("trackArtist").takeIf { it.isNotEmpty() },
                trackAlbum = obj.optString("trackAlbum").takeIf { it.isNotEmpty() },
                trackAlbumArtist = obj.optString("trackAlbumArtist").takeIf { it.isNotEmpty() },
                midiFileName = obj.optString("midiFileName").takeIf { it.isNotEmpty() },
                practiceScore = if (obj.has("practiceScore")) obj.getDouble("practiceScore").toFloat() else null,
                radioStationName = obj.optString("radioStationName").takeIf { it.isNotEmpty() }
            )
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("deviceId", deviceId)
        obj.put("moduleType", moduleType)
        obj.put("startTimestamp", startTimestamp)
        obj.put("endTimestamp", endTimestamp)
        obj.put("durationMs", durationMs)

        trackTitle?.let { obj.put("trackTitle", it) }
        trackArtist?.let { obj.put("trackArtist", it) }
        trackAlbum?.let { obj.put("trackAlbum", it) }
        trackAlbumArtist?.let { obj.put("trackAlbumArtist", it) }
        midiFileName?.let { obj.put("midiFileName", it) }
        practiceScore?.let { obj.put("practiceScore", it) }
        radioStationName?.let { obj.put("radioStationName", it) }

        return obj
    }
}
