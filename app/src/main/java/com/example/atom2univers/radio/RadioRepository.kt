package com.example.atom2univers.radio

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RadioRepository(private val config: RadioConfig) {

    private val client = OkHttpClient.Builder()
        .callTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    suspend fun fetchFilters(): RadioFilters = withContext(Dispatchers.IO) {
        val countries = fetchDirectory("json/countries")
        val languages = fetchDirectory("json/languages")
        RadioFilters(countries, languages)
    }

    suspend fun searchStations(params: RadioSearchParams): List<RadioStation> = withContext(Dispatchers.IO) {
        val servers = config.servers
        if (servers.isEmpty()) {
            return@withContext emptyList()
        }
        val query = params.query.trim()
        val country = params.country.trim()
        val language = params.language.trim()
        val results = mutableListOf<RadioStation>()
        for (server in servers) {
            val url = Uri.parse(server)
                .buildUpon()
                .appendEncodedPath("json/stations/search")
                .apply {
                    if (query.isNotEmpty()) {
                        appendQueryParameter("name", query)
                    }
                    if (country.isNotEmpty()) {
                        appendQueryParameter("country", country)
                    }
                    if (language.isNotEmpty()) {
                        appendQueryParameter("language", language)
                    }
                    if (config.hideBroken) {
                        appendQueryParameter("hidebroken", "true")
                    }
                    appendQueryParameter("limit", config.maxResults.toString())
                }
                .build()
                .toString()
            try {
                val payload = fetchJsonArray(url)
                val normalized = normalizeStations(payload)
                if (normalized.isNotEmpty()) {
                    results.addAll(normalized)
                    break
                }
            } catch (_: Exception) {
                // try next server
            }
        }
        results.take(config.maxResults)
    }

    private suspend fun fetchDirectory(path: String): List<String> {
        val servers = config.servers
        if (servers.isEmpty()) {
            return emptyList()
        }
        for (server in servers) {
            val url = Uri.parse(server).buildUpon().appendEncodedPath(path).build().toString()
            try {
                val payload = fetchJsonArray(url)
                return normalizeDirectory(payload)
            } catch (_: Exception) {
                // try next server
            }
        }
        return emptyList()
    }

    private fun normalizeDirectory(payload: JSONArray): List<String> {
        val entries = mutableListOf<String>()
        for (index in 0 until payload.length()) {
            val item = payload.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotEmpty()) {
                entries.add(name)
            }
        }
        return entries.distinct().sorted()
    }

    private fun normalizeStations(payload: JSONArray): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        for (index in 0 until payload.length()) {
            val entry = payload.optJSONObject(index) ?: continue
            normalizeStation(entry)?.let { stations.add(it) }
        }
        return stations
    }

    private fun normalizeStation(raw: JSONObject): RadioStation? {
        val id = raw.optString("stationuuid").trim().ifEmpty { raw.optString("id").trim() }
        val url = raw.optString("url").trim().ifEmpty { raw.optString("url_resolved").trim() }
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

    private suspend fun fetchJsonArray(url: String): JSONArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", config.userAgent)
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                JSONArray(raw)
            }
        }
    }
}
