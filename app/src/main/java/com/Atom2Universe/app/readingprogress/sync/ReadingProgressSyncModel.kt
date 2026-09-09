package com.Atom2Universe.app.readingprogress.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modèle pour la synchronisation de la progression de lecture (livres/BD) avec Google Drive.
 */
data class ReadingProgressSyncFile(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val items: List<SyncReadingProgress>
) {
    companion object {
        fun fromJson(json: String): ReadingProgressSyncFile {
            val obj = JSONObject(json)
            val version = obj.getInt("version")
            val lastModified = obj.getLong("lastModified")

            val itemsArray = obj.getJSONArray("items")
            val items = mutableListOf<SyncReadingProgress>()
            for (i in 0 until itemsArray.length()) {
                items.add(SyncReadingProgress.fromJson(itemsArray.getJSONObject(i)))
            }

            return ReadingProgressSyncFile(version, lastModified, items)
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("lastModified", lastModified)

        val itemsArray = JSONArray()
        items.forEach { itemsArray.put(it.toJson()) }
        obj.put("items", itemsArray)

        return obj.toString()
    }
}

/**
 * Progression de lecture synchronisée d'un titre (livre ou BD).
 */
data class SyncReadingProgress(
    val bookKey: String,
    val deviceId: String,
    val mediaType: String,
    val title: String,
    val progressPercent: Float,
    val lastReadTimestamp: Long
) {
    companion object {
        fun fromJson(obj: JSONObject): SyncReadingProgress {
            return SyncReadingProgress(
                bookKey = obj.getString("bookKey"),
                deviceId = obj.getString("deviceId"),
                mediaType = obj.getString("mediaType"),
                title = obj.getString("title"),
                progressPercent = obj.getDouble("progressPercent").toFloat(),
                lastReadTimestamp = obj.getLong("lastReadTimestamp")
            )
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("bookKey", bookKey)
        obj.put("deviceId", deviceId)
        obj.put("mediaType", mediaType)
        obj.put("title", title)
        obj.put("progressPercent", progressPercent)
        obj.put("lastReadTimestamp", lastReadTimestamp)
        return obj
    }
}
