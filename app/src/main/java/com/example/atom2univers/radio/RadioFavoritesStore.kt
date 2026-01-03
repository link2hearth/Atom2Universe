package com.example.atom2univers.radio

import com.example.atom2univers.SaveCore
import org.json.JSONArray
import org.json.JSONObject

class RadioFavoritesStore(
    private val saveCore: SaveCore,
    private val storageKey: String
) {
    fun load(): LinkedHashMap<String, RadioStation> {
        val map = LinkedHashMap<String, RadioStation>()
        val raw = saveCore.get(storageKey) ?: return map
        return try {
            val payload = JSONArray(raw)
            for (index in 0 until payload.length()) {
                val entry = payload.optJSONObject(index) ?: continue
                val station = normalizeStation(entry) ?: continue
                map[station.id] = station
            }
            map
        } catch (_: Exception) {
            map
        }
    }

    fun save(favorites: Map<String, RadioStation>) {
        val payload = JSONArray()
        favorites.values.forEach { station ->
            payload.put(serializeStation(station))
        }
        saveCore.set(storageKey, payload.toString())
    }

    private fun normalizeStation(raw: JSONObject): RadioStation? {
        val id = raw.optString("id").trim().ifEmpty { raw.optString("stationuuid").trim() }
        val url = raw.optString("url").trim()
        if (id.isEmpty() || url.isEmpty()) {
            return null
        }
        val name = raw.optString("name").trim().ifEmpty { "Station" }
        val country = raw.optString("country").trim()
        val language = raw.optString("language").trim()
        val favicon = raw.optString("favicon").trim()
        val bitrateValue = raw.optInt("bitrate", 0)
        val bitrate = if (bitrateValue > 0) bitrateValue else null
        return RadioStation(
            id = id,
            name = name,
            url = url,
            country = country,
            language = language,
            favicon = favicon,
            bitrate = bitrate
        )
    }

    private fun serializeStation(station: RadioStation): JSONObject {
        return JSONObject().apply {
            put("id", station.id)
            put("stationuuid", station.id)
            put("name", station.name)
            put("url", station.url)
            put("country", station.country)
            put("language", station.language)
            put("favicon", station.favicon)
            station.bitrate?.let { put("bitrate", it) }
        }
    }
}
