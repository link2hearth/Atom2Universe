package com.Atom2Universe.app.music.lyrics.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Client pour une API de paroles compatible LRCLib.
 * L'URL de base est configurable par l'utilisateur.
 * Format attendu: GET {baseUrl}/search?track_name=...&artist_name=...
 */
class LrcLibApiClient(
    private val baseUrl: String,
    private val timeoutMs: Long = 10000
) {

    companion object {
        private const val TAG = "LrcLibApiClient"

        // Valeurs invalides que l'API peut renvoyer au lieu de vraies paroles
        private val INVALID_LYRICS = setOf(
            "null", "NULL", "none", "NONE", "n/a", "N/A",
            "undefined", "UNDEFINED", "empty", "EMPTY"
        )

        /**
         * Vérifie si les paroles sont valides (pas vides, pas "NULL", etc.)
         */
        fun isValidLyrics(lyrics: String?): Boolean {
            if (lyrics.isNullOrBlank()) return false
            val trimmed = lyrics.trim()
            // Rejeter si c'est une valeur invalide connue
            if (INVALID_LYRICS.contains(trimmed)) return false
            // Rejeter si c'est trop court (moins de 10 caractères = probablement pas des vraies paroles)
            if (trimmed.length < 10) return false
            // Rejeter les réponses HTML (page web au lieu de l'API)
            if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)) return false
            return true
        }

        /**
         * Normalise l'URL de base en ajoutant /api si nécessaire pour lrclib.net
         */
        private fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')

            // Si c'est lrclib.net et qu'il n'y a pas déjà /api, l'ajouter
            if (trimmed.contains("lrclib.net", ignoreCase = true) &&
                !trimmed.endsWith("/api", ignoreCase = true)) {
                return "$trimmed/api"
            }

            return trimmed
        }
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Test if the API endpoint is reachable and responding.
     * Makes a simple search request to verify the API is working.
     * @return ApiTestResult indicating success or the type of error
     */
    suspend fun testConnection(): ApiTestResult = withContext(Dispatchers.IO) {
        try {
            val normalizedBase = normalizeBaseUrl(baseUrl)
            // Make a simple search request - even if no results, we confirm API responds
            val url = "$normalizedBase/search?track_name=test&artist_name=test"

            Log.d(TAG, "Testing LrcLib API at: $normalizedBase")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U v1.0 (https://atom2universe.com)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    Log.d(TAG, "API test successful")
                    ApiTestResult.Success
                }
                response.code == 429 -> {
                    Log.w(TAG, "API test: rate limited")
                    ApiTestResult.RateLimited
                }
                else -> {
                    Log.e(TAG, "API test failed: HTTP ${response.code}")
                    ApiTestResult.HttpError(response.code)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "API test failed: unknown host", e)
            ApiTestResult.UnknownHost
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "API test failed: timeout", e)
            ApiTestResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "API test failed: ${e.message}", e)
            ApiTestResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        duration: Long? = null  // in seconds
    ): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // Format: {baseUrl}/search?track_name=...&artist_name=...
            val normalizedBase = normalizeBaseUrl(baseUrl)
            val url = buildString {
                append("$normalizedBase/search?")
                append("track_name=${java.net.URLEncoder.encode(title, "UTF-8")}")
                append("&artist_name=${java.net.URLEncoder.encode(artist, "UTF-8")}")
                album?.let {
                    val cleanAlbum = com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(it)
                    if (cleanAlbum.isNotBlank()) {
                        append("&album_name=${java.net.URLEncoder.encode(cleanAlbum, "UTF-8")}")
                    }
                }
                duration?.let { append("&duration=$it") }
            }

            Log.d(TAG, "Searching LRCLib: artist='$artist', title='$title'")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U v1.0 (https://atom2universe.com)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    429 -> {
                        Log.w(TAG, "Rate limited")
                        LyricsResult.RateLimited
                    }
                    404 -> {
                        Log.d(TAG, "Not found")
                        LyricsResult.NotFound
                    }
                    else -> {
                        Log.e(TAG, "HTTP error: ${response.code}")
                        LyricsResult.Error("HTTP ${response.code}", "lrclib")
                    }
                }
            }

            val jsonArray = JSONArray(response.body?.string() ?: "[]")

            if (jsonArray.length() == 0) {
                Log.d(TAG, "No results")
                return@withContext LyricsResult.NotFound
            }

            // Get first result
            val firstResult = jsonArray.getJSONObject(0)

            // Try synced lyrics first (.lrc format with timestamps)
            val syncedLyrics = firstResult.optString("syncedLyrics")
            if (isValidLyrics(syncedLyrics)) {
                Log.d(TAG, "Found synced lyrics (${syncedLyrics.length} chars)")
                return@withContext LyricsResult.Success(
                    syncedLyrics,
                    "lrclib",
                    isSynced = true
                )
            } else if (syncedLyrics.isNotBlank()) {
                Log.w(TAG, "Rejected invalid synced lyrics: '$syncedLyrics'")
            }

            // Fall back to plain lyrics
            val plainLyrics = firstResult.optString("plainLyrics")
            if (isValidLyrics(plainLyrics)) {
                Log.d(TAG, "Found plain lyrics (${plainLyrics.length} chars)")
                return@withContext LyricsResult.Success(
                    plainLyrics,
                    "lrclib",
                    isSynced = false
                )
            } else if (plainLyrics.isNotBlank()) {
                Log.w(TAG, "Rejected invalid plain lyrics: '$plainLyrics'")
            }

            Log.d(TAG, "No valid lyrics in result")
            LyricsResult.NotFound
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            LyricsResult.Error(e.message ?: "Unknown error", "lrclib")
        }
    }
}
