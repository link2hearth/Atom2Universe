package com.Atom2Universe.app.music.navidrome

import android.content.Context
import android.net.Uri
import android.util.Log
import com.Atom2Universe.app.A2UApplication
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
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

        private const val PREFS_NAME = "a2u_navidrome_auth"
        private const val KEY_MEDIA_SECRET = "media_salt_secret"

        private val random = SecureRandom()

        private fun randomHex(bytes: Int): String {
            val buffer = ByteArray(bytes)
            random.nextBytes(buffer)
            return buffer.joinToString("") { "%02x".format(it) }
        }

        /**
         * Secret tiré une fois par installation, qui sert à dériver le sel des adresses de
         * lecture et de pochette (voir [mediaSalt]).
         */
        private val mediaSecret: String by lazy {
            val prefs = A2UApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_MEDIA_SECRET, null) ?: randomHex(16).also {
                prefs.edit().putString(KEY_MEDIA_SECRET, it).apply()
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Vrai si ce client a été créé pour ces réglages-là (adresse ET identifiants). */
    fun isSameAccount(url: String, user: String, pass: String): Boolean =
        serverUrl == url && username == user && password == pass

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Authentification Subsonic par jeton : t = md5(mot de passe + sel), le sel envoyé avec la
     * requête. Un sel fixe faisait du jeton l'équivalent du mot de passe.
     */
    private fun authParams(salt: String): String =
        "u=$username&t=${md5(password + salt)}&s=$salt&v=$API_VERSION&c=$CLIENT_NAME&f=json"

    /**
     * Sel des adresses de lecture et de pochette : propre à chaque ressource, mais stable.
     * Ces adresses servent de clé au cache des pochettes et sont gardées dans la file de
     * lecture ; un sel tiré à chaque fois les rendrait toutes différentes. Le serveur accepte
     * n'importe quel sel tant que le jeton correspond.
     */
    private fun mediaSalt(endpoint: String, id: String): String =
        md5("$mediaSecret|$endpoint|$id").take(16)

    private fun normalizedServerUrl(): String {
        val url = serverUrl.trimEnd('/')
        return if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }

    /** Requête d'API : un sel neuf à chaque appel, comme le veut la norme Subsonic. */
    private fun buildEndpointUrl(endpoint: String, extraParams: String = ""): String =
        buildUrl(endpoint, randomHex(8), extraParams)

    private fun buildUrl(endpoint: String, salt: String, extraParams: String): String {
        val base = normalizedServerUrl()
        val params = if (extraParams.isNotBlank()) "&$extraParams" else ""
        return "$base/rest/$endpoint?${authParams(salt)}$params"
    }

    fun buildStreamUrl(trackId: String): String =
        buildUrl("stream", mediaSalt("stream", trackId), "id=$trackId")

    fun buildCoverArtUrl(coverArtId: String): String =
        buildUrl("getCoverArt", mediaSalt("getCoverArt", coverArtId), "id=$coverArtId")

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Pas d'adresse dans le journal : elle porte le jeton d'authentification.
            val request = Request.Builder().url(buildEndpointUrl("ping")).build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "testConnection HTTP ${response.code}")
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val status = json.getJSONObject("subsonic-response").getString("status")
                Log.d(TAG, "testConnection status: $status")
                status == "ok"
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection exception: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Corps de la réponse, ou null si la requête a échoué. La réponse est toujours fermée. */
    private fun fetchBody(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    suspend fun fetchArtists(): List<NavidromeArtist> = withContext(Dispatchers.IO) {
        try {
            val body = fetchBody(buildEndpointUrl("getArtists")) ?: return@withContext emptyList()
            parseArtists(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAlbums(artistId: String): List<NavidromeAlbum> = withContext(Dispatchers.IO) {
        try {
            val body = fetchBody(buildEndpointUrl("getArtist", "id=$artistId")) ?: return@withContext emptyList()
            parseAlbums(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTracks(albumId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        try {
            val body = fetchBody(buildEndpointUrl("getAlbum", "id=$albumId")) ?: return@withContext emptyList()
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
