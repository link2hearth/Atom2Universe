package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model for syncing custom EQ presets across devices.
 * Only user-created presets are synced (not system presets).
 */
data class EqPresetsSyncFile(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val presets: List<SyncEqPreset> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("lastModified", lastModified)
            put("presets", JSONArray().apply {
                presets.forEach { preset ->
                    put(preset.toJson())
                }
            })
        }
    }

    companion object {
        const val FILENAME = "eq_presets.json"

        fun fromJson(json: JSONObject): EqPresetsSyncFile {
            val presetsArray = json.optJSONArray("presets") ?: JSONArray()
            val presets = mutableListOf<SyncEqPreset>()

            for (i in 0 until presetsArray.length()) {
                presets.add(SyncEqPreset.fromJson(presetsArray.getJSONObject(i)))
            }

            return EqPresetsSyncFile(
                version = json.optInt("version", 1),
                lastModified = json.optLong("lastModified", 0),
                presets = presets
            )
        }
    }
}

/**
 * A single EQ preset for sync.
 * Uses a unique key based on name to handle merges.
 * Supports soft-delete via deletedAt timestamp for proper cross-device sync.
 */
data class SyncEqPreset(
    val name: String,
    val band32Hz: Int,
    val band64Hz: Int,
    val band125Hz: Int,
    val band250Hz: Int,
    val band500Hz: Int,
    val band1kHz: Int,
    val band2kHz: Int,
    val band4kHz: Int,
    val band8kHz: Int,
    val band16kHz: Int,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null  // Soft-delete timestamp
) {
    /**
     * Returns true if this preset is currently active (not deleted).
     */
    fun isActive(): Boolean {
        return deletedAt == null || updatedAt > deletedAt
    }

    /**
     * Returns the most recent timestamp (updated or deleted).
     */
    fun getLastModifiedTimestamp(): Long {
        return maxOf(updatedAt, deletedAt ?: 0)
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("band32Hz", band32Hz)
            put("band64Hz", band64Hz)
            put("band125Hz", band125Hz)
            put("band250Hz", band250Hz)
            put("band500Hz", band500Hz)
            put("band1kHz", band1kHz)
            put("band2kHz", band2kHz)
            put("band4kHz", band4kHz)
            put("band8kHz", band8kHz)
            put("band16kHz", band16kHz)
            put("bassBoostStrength", bassBoostStrength)
            put("virtualizerStrength", virtualizerStrength)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            deletedAt?.let { put("deletedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncEqPreset {
            return SyncEqPreset(
                name = json.getString("name"),
                band32Hz = json.optInt("band32Hz", 0),
                band64Hz = json.optInt("band64Hz", 0),
                band125Hz = json.optInt("band125Hz", 0),
                band250Hz = json.optInt("band250Hz", 0),
                band500Hz = json.optInt("band500Hz", 0),
                band1kHz = json.optInt("band1kHz", 0),
                band2kHz = json.optInt("band2kHz", 0),
                band4kHz = json.optInt("band4kHz", 0),
                band8kHz = json.optInt("band8kHz", 0),
                band16kHz = json.optInt("band16kHz", 0),
                bassBoostStrength = json.optInt("bassBoostStrength", 0),
                virtualizerStrength = json.optInt("virtualizerStrength", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                deletedAt = if (json.has("deletedAt")) json.optLong("deletedAt") else null
            )
        }
    }
}
