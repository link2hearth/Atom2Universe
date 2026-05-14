package com.Atom2Universe.app.music.lyrics.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client pour une API de paroles de type simple.
 * L'URL de base est configurable par l'utilisateur.
 * Format attendu: GET {baseUrl}/{artist}/{title}
 */
class LyricsOvhApiClient(
    private val baseUrl: String,
    private val timeoutMs: Long = 10000
) {

    companion object {
        private const val TAG = "LyricsOvhApiClient"
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
            val normalizedBase = baseUrl.trimEnd('/')
            // Make a simple search request with a well-known song
            val url = "$normalizedBase/Coldplay/Yellow"

            Log.d(TAG, "Testing Lyrics.ovh API at: $normalizedBase")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U/1.0")
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    Log.d(TAG, "API test successful")
                    ApiTestResult.Success
                }
                response.code == 404 -> {
                    // 404 means the API is working but song not found - that's OK
                    Log.d(TAG, "API test successful (404 = API working)")
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

    suspend fun searchLyrics(title: String, artist: String): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // Clean metadata for better matching
            val cleanArtist = com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(artist)
            val cleanTitle = com.Atom2Universe.app.music.lyrics.LyricsUtils.cleanMetadata(title)

            // Format: {baseUrl}/{artist}/{title}
            val normalizedBase = baseUrl.trimEnd('/')
            val url = "$normalizedBase/" +
                "${java.net.URLEncoder.encode(cleanArtist, "UTF-8")}/" +
                java.net.URLEncoder.encode(cleanTitle, "UTF-8")

            Log.d(TAG, "Searching Lyrics.ovh: artist='$cleanArtist', title='$cleanTitle'")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "A2U/1.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    404 -> {
                        Log.d(TAG, "Not found (404)")
                        LyricsResult.NotFound
                    }
                    429 -> {
                        Log.w(TAG, "Rate limited")
                        LyricsResult.RateLimited
                    }
                    else -> {
                        Log.e(TAG, "HTTP error: ${response.code}")
                        LyricsResult.Error("HTTP ${response.code}", "lyrics.ovh")
                    }
                }
            }

            val jsonString = response.body?.string() ?: "{}"
            val json = JSONObject(jsonString)

            val lyrics = json.optString("lyrics", "").trim()

            if (LrcLibApiClient.isValidLyrics(lyrics)) {
                Log.d(TAG, "Found lyrics (${lyrics.length} chars)")
                LyricsResult.Success(lyrics, "lyrics.ovh", isSynced = false)
            } else {
                if (lyrics.isNotBlank()) {
                    Log.w(TAG, "Rejected invalid lyrics: '$lyrics'")
                }
                Log.d(TAG, "No valid lyrics in response")
                LyricsResult.NotFound
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            LyricsResult.Error(e.message ?: "Unknown error", "lyrics.ovh")
        }
    }
}
