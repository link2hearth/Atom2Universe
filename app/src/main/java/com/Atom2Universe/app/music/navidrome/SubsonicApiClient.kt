package com.Atom2Universe.app.music.navidrome

import android.net.Uri
import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SubsonicApiClient(
    val serverUrl: String,
    private val username: String,
    private val password: String
) {

    companion object {
        private const val TAG = "SubsonicApiClient"
        private const val API_VERSION = "1.16.1"
        private const val CLIENT_NAME = "A2U"
        private const val SALT = "A2USalt42"
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val token: String by lazy {
        md5(password + SALT)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun authParams(): String =
        "u=$username&t=$token&s=$SALT&v=$API_VERSION&c=$CLIENT_NAME&f=json"

    private fun normalizedServerUrl(): String {
        val url = serverUrl.trimEnd('/')
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "http://$url"
    }

    private fun buildEndpointUrl(endpoint: String, extraParams: String = ""): String {
        val base = normalizedServerUrl()
        val params = if (extraParams.isNotBlank()) "&$extraParams" else ""
        return "$base/rest/$endpoint?${authParams()}$params"
    }

    fun buildStreamUrl(trackId: String): String =
        buildEndpointUrl("stream", "id=$trackId")

    fun buildCoverArtUrl(coverArtId: String): String =
        buildEndpointUrl("getCoverArt", "id=$coverArtId")

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildEndpointUrl("ping")
            Log.d(TAG, "testConnection URL: $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d(TAG, "testConnection HTTP ${response.code}: $body")
            if (!response.isSuccessful) return@withContext false
            val json = JSONObject(body)
            val status = json.getJSONObject("subsonic-response").getString("status")
            Log.d(TAG, "testConnection status: $status")
            status == "ok"
        } catch (e: Exception) {
            Log.e(TAG, "testConnection exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    suspend fun fetchArtists(): List<NavidromeArtist> = withContext(Dispatchers.IO) {
        try {
            val url = buildEndpointUrl("getArtists")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseArtists(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAlbums(artistId: String): List<NavidromeAlbum> = withContext(Dispatchers.IO) {
        try {
            val url = buildEndpointUrl("getArtist", "id=$artistId")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseAlbums(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTracks(albumId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val url = buildEndpointUrl("getAlbum", "id=$albumId")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseTracks(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseArtists(json: String): List<NavidromeArtist> {
        val result = mutableListOf<NavidromeArtist>()
        try {
            val root = JSONObject(json).getJSONObject("subsonic-response")
            if (root.getString("status") != "ok") return emptyList()
            val artists = root.getJSONObject("artists")
            val indices = artists.getJSONArray("index")
            for (i in 0 until indices.length()) {
                val index = indices.getJSONObject(i)
                if (!index.has("artist")) continue
                val artistArray = index.getJSONArray("artist")
                for (j in 0 until artistArray.length()) {
                    val a = artistArray.getJSONObject(j)
                    result.add(
                        NavidromeArtist(
                            id = a.getString("id"),
                            name = a.getString("name"),
                            albumCount = a.optInt("albumCount", 0),
                            coverArtId = a.optString("coverArt").ifBlank { null }
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parseAlbums(json: String): List<NavidromeAlbum> {
        val result = mutableListOf<NavidromeAlbum>()
        try {
            val root = JSONObject(json).getJSONObject("subsonic-response")
            if (root.getString("status") != "ok") return emptyList()
            val artistObj = root.getJSONObject("artist")
            if (!artistObj.has("album")) return emptyList()
            val albums = artistObj.getJSONArray("album")
            for (i in 0 until albums.length()) {
                val a = albums.getJSONObject(i)
                result.add(
                    NavidromeAlbum(
                        id = a.getString("id"),
                        name = a.getString("name"),
                        artist = a.optString("artist", ""),
                        artistId = a.optString("artistId", ""),
                        songCount = a.optInt("songCount", 0),
                        year = if (a.has("year")) a.getInt("year") else null,
                        coverArtId = a.optString("coverArt").ifBlank { null }
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    private fun parseTracks(json: String): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        try {
            val root = JSONObject(json).getJSONObject("subsonic-response")
            if (root.getString("status") != "ok") return emptyList()
            val album = root.getJSONObject("album")
            val albumName = album.optString("name", "")
            if (!album.has("song")) return emptyList()
            val songs = album.getJSONArray("song")
            for (i in 0 until songs.length()) {
                val s = songs.getJSONObject(i)
                val id = s.getString("id")
                val coverArtId = s.optString("coverArt").ifBlank { null }
                    ?: album.optString("coverArt").ifBlank { null }
                val streamUrl = buildStreamUrl(id)
                val artUrl = coverArtId?.let { buildCoverArtUrl(it) }
                result.add(
                    MusicTrack(
                        id = id.hashCode().toLong(),
                        title = s.optString("title", ""),
                        artist = s.optString("artist", ""),
                        album = albumName,
                        duration = s.optLong("duration", 0) * 1000,
                        uri = Uri.parse(streamUrl),
                        albumArtUri = artUrl?.let { Uri.parse(it) },
                        trackNumber = if (s.has("track")) s.getInt("track") else null,
                        discNumber = if (s.has("discNumber")) s.getInt("discNumber") else null,
                        year = if (s.has("year")) s.getInt("year") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }
}
