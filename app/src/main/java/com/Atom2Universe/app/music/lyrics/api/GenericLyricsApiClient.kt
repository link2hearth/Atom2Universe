package com.Atom2Universe.app.music.lyrics.api

import android.util.Log
import com.Atom2Universe.app.music.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GenericLyricsApiClient(
    private val config: LyricsApiConfig,
    private val timeoutMs: Long = 10000
) {

    companion object {
        private const val TAG = "GenericLyricsApiClient"
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    suspend fun testConnection(): ApiTestResult = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(
                title = "Yellow",
                artist = "Coldplay",
                album = "Parachutes",
                durationSeconds = 250
            )
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U v1.0 (https://atom2universe.com)")
                .header("Accept", "application/json")
                .applyHeaders()
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> ApiTestResult.Success
                response.code == 404 -> ApiTestResult.Success
                response.code == 429 -> ApiTestResult.RateLimited
                else -> ApiTestResult.HttpError(response.code)
            }
        } catch (e: java.net.UnknownHostException) {
            ApiTestResult.UnknownHost
        } catch (e: java.net.SocketTimeoutException) {
            ApiTestResult.Timeout
        } catch (e: Exception) {
            ApiTestResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,               // Inclus dans l'URL de la requête (filtre côté API)
        durationSeconds: Long?,
        matchDurationSeconds: Long? = null,   // Durée réelle du track, pour sélection côté client uniquement
        scoringAlbum: String? = null,         // Album utilisé uniquement pour le scoring côté client
        scoringAlbumArtist: String? = null    // Album artist utilisé uniquement pour le scoring côté client
    ): LyricsResult = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(title, artist, album, durationSeconds)
            Log.d(TAG, "Requesting lyrics: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U v1.0 (https://atom2universe.com)")
                .header("Accept", "application/json")
                .applyHeaders()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    404 -> LyricsResult.NotFound
                    429 -> LyricsResult.RateLimited
                    else -> LyricsResult.Error("HTTP ${response.code}", config.sourceLabel)
                }
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return@withContext LyricsResult.NotFound
            }

            val json = parseJson(body)
            val effectiveDuration = matchDurationSeconds ?: durationSeconds
            // scoringAlbum prend la priorité sur album pour le scoring (album peut être null
            // sur le try 1 où on ne l'envoie pas dans l'URL, mais on veut quand même scorer)
            val albumForScoring = scoringAlbum ?: album
            val result = when {
                json is JSONArray -> {
                    val sorted = findAllSortedResults(json, effectiveDuration, title, artist, albumForScoring, scoringAlbumArtist)
                    if (sorted.isNotEmpty()) {
                        val best = extractFromJsonObject(sorted.first())
                        val alts = sorted.drop(1).mapNotNull { obj ->
                            (extractFromJsonObject(obj) as? LyricsResult.Success)
                                ?.let { AlternativeLyrics(it.lyrics, it.source, it.isSynced) }
                        }
                        when (best) {
                            is LyricsResult.Success -> best.copy(alternatives = alts)
                            else -> best
                        }
                    } else null
                }
                json != null -> extractFromJson(json)
                else -> extractFromPlain(body)
            }

            result ?: LyricsResult.NotFound
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            LyricsResult.Error(e.message ?: "Unknown error", config.sourceLabel)
        }
    }

    /**
     * Parmi les résultats retournés par l'API, retourne tous les résultats valides
     * (avec paroles) triés du meilleur au moins bon selon :
     * - La durée (critère principal, scoring continu)
     * - Le nom d'album
     * - Le nom d'artiste (ou album artist en fallback)
     * - Explicit (préférence pour les versions non censurées)
     * - Préférence pour les paroles synchronisées
     *
     * Le premier élément est le meilleur résultat, les suivants sont les alternatives.
     */
    private fun findAllSortedResults(
        array: JSONArray,
        durationSeconds: Long?,
        titleName: String,
        artistName: String,
        albumName: String?,
        albumArtistName: String? = null
    ): List<JSONObject> {
        if (array.length() == 0) return emptyList()
        if (array.length() == 1) return listOfNotNull(array.optJSONObject(0))

        val cleanTitle = LyricsUtils.cleanMetadata(titleName)
        val cleanArtist = LyricsUtils.cleanMetadata(artistName)
        val cleanAlbum = albumName?.let { LyricsUtils.cleanMetadata(it) }
        val cleanAlbumArtist = albumArtistName?.let { LyricsUtils.cleanMetadata(it) }

        val scored = mutableListOf<Pair<Int, JSONObject>>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue

            val synced = obj.optString("syncedLyrics")
            val plain = obj.optString("plainLyrics")
            val hasLyrics = LrcLibApiClient.isValidLyrics(synced) || LrcLibApiClient.isValidLyrics(plain)
            if (!hasLyrics) continue

            var score = 0

            // Durée : critère principal, scoring continu sur [0, 5s]
            // - [1s, 5s) : 60..80 pts  (20 pts répartis sur 4s)
            // - [0s, 1s) : 80..100 pts (20 pts à 2pts/0.1s pour la précision sub-seconde)
            // - [5s, 10s) : +30 (palier fixe)
            // - [10s, 30s) : 0
            // - ≥30s : -50
            if (durationSeconds != null) {
                val resultDuration = obj.optDouble("duration", -1.0)
                if (resultDuration > 0) {
                    val diff = Math.abs(resultDuration - durationSeconds)
                    score += when {
                        diff >= 30 -> -50
                        diff >= 10 -> 0
                        diff >= 5  -> 30
                        diff >= 1  -> (60 + ((5.0 - diff) / 4.0 * 20).toInt())
                        else       -> (80 + ((1.0 - diff) * 20).toInt())
                    }
                }
            }

            // Titre
            val trackName = obj.optString("trackName", "")
            if (cleanTitle.isNotBlank()) {
                val resultTitle = LyricsUtils.cleanMetadata(trackName)
                when {
                    resultTitle.equals(cleanTitle, ignoreCase = true)   -> score += 80
                    resultTitle.contains(cleanTitle, ignoreCase = true) -> score += 30
                }
            }

            // Album
            if (!cleanAlbum.isNullOrBlank()) {
                val resultAlbum = LyricsUtils.cleanMetadata(obj.optString("albumName", ""))
                when {
                    resultAlbum.equals(cleanAlbum, ignoreCase = true) -> score += 40
                    resultAlbum.contains(cleanAlbum, ignoreCase = true) -> score += 15
                }
            }

            // Artiste — si pas de match, fallback sur album artist (jamais les deux cumulés)
            if (cleanArtist.isNotBlank()) {
                val resultArtist = LyricsUtils.cleanMetadata(obj.optString("artistName", ""))
                when {
                    resultArtist.equals(cleanArtist, ignoreCase = true) -> score += 20
                    resultArtist.contains(cleanArtist, ignoreCase = true) -> score += 8
                    !cleanAlbumArtist.isNullOrBlank() -> when {
                        resultArtist.equals(cleanAlbumArtist, ignoreCase = true)   -> score += 20
                        resultArtist.contains(cleanAlbumArtist, ignoreCase = true) -> score += 8
                    }
                }
            }

            // Explicit : préférer les versions non censurées
            if (trackName.contains("explicit", ignoreCase = true)) score += 20

            // Paroles synchronisées : priorité haute
            if (LrcLibApiClient.isValidLyrics(synced)) score += 50

            Log.d(TAG, "Result[$i] '$trackName' by '${obj.optString("artistName")}' dur=${obj.optDouble("duration")}s → score=$score")

            scored.add(Pair(score, obj))
        }

        // Si aucun résultat valide (avec paroles), prendre le premier quand même
        if (scored.isEmpty()) return listOfNotNull(array.optJSONObject(0))

        return scored.sortedByDescending { it.first }.map { it.second }
    }

    /**
     * Extrait les paroles depuis un JSONObject (résultat unique déjà sélectionné).
     */
    private fun extractFromJsonObject(obj: JSONObject): LyricsResult? {
        val syncedFromPath = config.syncedLyricsPath?.let { extractJsonValue(obj, it) }
        if (LrcLibApiClient.isValidLyrics(syncedFromPath)) {
            return LyricsResult.Success(syncedFromPath!!.trim(), config.sourceLabel, isSynced = true)
        }

        val lyricsFromPath = config.lyricsPath?.let { extractJsonValue(obj, it) }
        if (LrcLibApiClient.isValidLyrics(lyricsFromPath)) {
            val lyrics = lyricsFromPath!!.trim()
            return LyricsResult.Success(lyrics, config.sourceLabel, isSynced = looksLikeSynced(lyrics))
        }

        for (key in listOf("syncedLyrics", "plainLyrics", "lyrics", "lyrics_body")) {
            val value = obj.optString(key)
            if (LrcLibApiClient.isValidLyrics(value)) {
                return LyricsResult.Success(value.trim(), config.sourceLabel, isSynced = looksLikeSynced(value))
            }
        }

        return null
    }

    private fun Request.Builder.applyHeaders(): Request.Builder {
        config.headers.forEach { (key, value) -> header(key, value) }
        return this
    }

    private fun buildUrl(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): String {
        val cleanTitle = LyricsUtils.cleanMetadata(title)
        val cleanArtist = LyricsUtils.cleanMetadata(artist)
        val cleanAlbum = album?.let { LyricsUtils.cleanMetadata(it) }
        val query = listOf(cleanArtist, cleanTitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val url = config.urlTemplate
            .replace("{title}", encode(cleanTitle))
            .replace("{artist}", encode(cleanArtist))
            .replace("{album}", encode(cleanAlbum ?: ""))
            .replace("{duration}", durationSeconds?.toString() ?: "")
            .replace("{query}", encode(query))

        return stripEmptyParams(url)
    }

    // Supprime les paramètres de query dont la valeur est vide (ex: &album_name=&duration=)
    private fun stripEmptyParams(url: String): String {
        val (base, query) = if ('?' in url) {
            val idx = url.indexOf('?')
            url.substring(0, idx) to url.substring(idx + 1)
        } else {
            return url
        }
        val filteredParams = query.split('&')
            .filter { param -> param.isNotBlank() && !param.endsWith('=') && param.contains('=') }
        return if (filteredParams.isEmpty()) base else "$base?${filteredParams.joinToString("&")}"
    }

    private fun extractFromPlain(body: String): LyricsResult? {
        val trimmed = body.trim()
        return if (LrcLibApiClient.isValidLyrics(trimmed)) {
            LyricsResult.Success(trimmed, config.sourceLabel, isSynced = looksLikeSynced(trimmed))
        } else {
            null
        }
    }

    private fun extractFromJson(json: Any): LyricsResult? {
        val syncedFromPath = config.syncedLyricsPath?.let { extractJsonValue(json, it) }
        if (LrcLibApiClient.isValidLyrics(syncedFromPath)) {
            return LyricsResult.Success(syncedFromPath!!.trim(), config.sourceLabel, isSynced = true)
        }

        val lyricsFromPath = config.lyricsPath?.let { extractJsonValue(json, it) }
        if (LrcLibApiClient.isValidLyrics(lyricsFromPath)) {
            val lyrics = lyricsFromPath!!.trim()
            return LyricsResult.Success(lyrics, config.sourceLabel, isSynced = looksLikeSynced(lyrics))
        }

        val fallbackLyrics = extractWithDefaults(json)
        if (LrcLibApiClient.isValidLyrics(fallbackLyrics)) {
            val lyrics = fallbackLyrics!!.trim()
            return LyricsResult.Success(lyrics, config.sourceLabel, isSynced = looksLikeSynced(lyrics))
        }

        return null
    }

    private fun extractWithDefaults(json: Any): String? {
        val defaultPaths = if (json is JSONArray) {
            listOf(
                "[0].syncedLyrics",
                "[0].plainLyrics",
                "[0].lyrics",
                "[0].lyrics_body"
            )
        } else {
            listOf(
                "syncedLyrics",
                "plainLyrics",
                "lyrics",
                "lyrics_body",
                "message.body.lyrics.lyrics_body"
            )
        }

        for (path in defaultPaths) {
            val value = extractJsonValue(json, path)
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    private fun extractJsonValue(json: Any, path: String): String? {
        val tokens = tokenizePath(path)
        var current: Any? = json

        for (token in tokens) {
            current = when (current) {
                is JSONObject -> token.asKey?.let { current.opt(it) }
                is JSONArray -> token.asIndex?.let { index ->
                    if (index in 0 until current.length()) current.opt(index) else null
                }
                else -> null
            }

            if (current == null) return null
        }

        return when (current) {
            is String -> current
            is JSONObject -> current.toString()
            is JSONArray -> current.toString()
            else -> current?.toString()
        }
    }

    private fun tokenizePath(path: String): List<PathToken> {
        val regex = Regex("""([^\.\[\]]+)|\[(\d+)]""")
        return regex.findAll(path)
            .mapNotNull { match ->
                val key = match.groups[1]?.value
                val index = match.groups[2]?.value?.toIntOrNull()
                when {
                    key != null -> PathToken(key = key)
                    index != null -> PathToken(index = index)
                    else -> null
                }
            }
            .toList()
    }

    private fun parseJson(body: String): Any? {
        val trimmed = body.trim()
        return try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeSynced(lyrics: String): Boolean {
        return Regex("""\[\d{1,2}:\d{2}([.:]\d{1,2})?]""").containsMatchIn(lyrics)
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    private data class PathToken(val key: String? = null, val index: Int? = null) {
        val asKey: String? get() = key
        val asIndex: Int? get() = index
    }
}
