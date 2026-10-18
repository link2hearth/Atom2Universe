package com.Atom2Universe.app.radio

import android.util.Log
import com.Atom2Universe.app.SaveCore
import org.json.JSONArray
import org.json.JSONObject

class RadioFavoritesStore(
    private val saveCore: SaveCore,
    private val storageKey: String
) {
    companion object {
        private const val TAG = "RadioFavoritesStore"
    }

    /**
     * Résultat du chargement des favoris avec information d'erreur éventuelle
     */
    data class LoadResult(
        val favorites: LinkedHashMap<String, RadioStation>,
        val error: String? = null
    )

    /**
     * Charge les favoris depuis le stockage
     * @return LoadResult contenant les favoris et une erreur éventuelle
     */
    fun loadWithResult(): LoadResult {
        val map = LinkedHashMap<String, RadioStation>()
        val raw = saveCore.get(storageKey)

        if (raw.isNullOrBlank()) {
            return LoadResult(map) // Pas d'erreur, juste vide
        }

        return try {
            val payload = JSONArray(raw)
            var parseErrors = 0
            for (index in 0 until payload.length()) {
                val entry = payload.optJSONObject(index)
                if (entry == null) {
                    parseErrors++
                    continue
                }
                val station = normalizeStation(entry)
                if (station == null) {
                    parseErrors++
                    continue
                }
                map[station.id] = station
            }

            if (parseErrors > 0) {
                Log.w(TAG, "Loaded ${map.size} favorites, skipped $parseErrors invalid entries")
            }

            LoadResult(map)
        } catch (e: Exception) {
            // Bug 4.26: Logger l'exception au lieu de la masquer silencieusement
            Log.e(TAG, "Failed to parse favorites JSON: ${e.message}", e)
            LoadResult(map, "Failed to load favorites: ${e.message}")
        }
    }

    /**
     * Charge les favoris (méthode legacy pour compatibilité)
     * @return Map des favoris, vide en cas d'erreur (l'erreur est loggée)
     */
    fun load(): LinkedHashMap<String, RadioStation> {
        return loadWithResult().favorites
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
