package com.Atom2Universe.app.music.sync.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Play counts backup file.
 * Contains absolute play count values for all tracks.
 *
 * File: backup/playcounts.json
 */
data class PlayCountsBackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val counts: List<PlayCountBackupEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("exportedAt", exportedAt)
            put("counts", JSONArray().apply {
                counts.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        const val FILENAME = "playcounts.json"

        fun fromJson(json: JSONObject): PlayCountsBackupFile {
            val countsArray = json.optJSONArray("counts") ?: JSONArray()
            val counts = mutableListOf<PlayCountBackupEntry>()
            for (i in 0 until countsArray.length()) {
                counts.add(PlayCountBackupEntry.fromJson(countsArray.getJSONObject(i)))
            }
            return PlayCountsBackupFile(
                version = json.optInt("version", 1),
                exportedAt = json.optLong("exportedAt", 0),
                counts = counts
            )
        }
    }
}

data class PlayCountBackupEntry(
    val key: String,           // "artist|title|album"
    val artist: String,
    val title: String,
    val album: String,
    val playCount: Long,       // Absolute play count
    val lastPlayed: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("key", key)
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("playCount", playCount)
            put("lastPlayed", lastPlayed)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlayCountBackupEntry {
            return PlayCountBackupEntry(
                key = json.optString("key", ""),
                artist = json.optString("artist", ""),
                title = json.optString("title", ""),
                album = json.optString("album", ""),
                playCount = json.optLong("playCount", 0),
                lastPlayed = json.optLong("lastPlayed", 0)
            )
        }
    }
}

/**
 * Album favorites backup file.
 *
 * File: backup/album_favorites.json
 */
data class AlbumFavoritesBackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val favorites: List<AlbumFavoriteBackupEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("exportedAt", exportedAt)
            put("favorites", JSONArray().apply {
                favorites.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        const val FILENAME = "album_favorites.json"

        fun fromJson(json: JSONObject): AlbumFavoritesBackupFile {
            val favoritesArray = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = mutableListOf<AlbumFavoriteBackupEntry>()
            for (i in 0 until favoritesArray.length()) {
                favorites.add(AlbumFavoriteBackupEntry.fromJson(favoritesArray.getJSONObject(i)))
            }
            return AlbumFavoritesBackupFile(
                version = json.optInt("version", 1),
                exportedAt = json.optLong("exportedAt", 0),
                favorites = favorites
            )
        }
    }
}

data class AlbumFavoriteBackupEntry(
    val artistName: String,
    val albumName: String,
    val addedAt: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("artistName", artistName)
            put("albumName", albumName)
            addedAt?.let { put("addedAt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AlbumFavoriteBackupEntry {
            return AlbumFavoriteBackupEntry(
                artistName = json.optString("artistName", ""),
                albumName = json.optString("albumName", ""),
                addedAt = json.optString("addedAt").takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Artist customizations backup file.
 * Includes favorites, colors, and image references.
 *
 * File: backup/artist_customizations.json
 */
data class ArtistCustomizationsBackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val customizations: List<ArtistCustomizationBackupEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("exportedAt", exportedAt)
            put("customizations", JSONArray().apply {
                customizations.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        const val FILENAME = "artist_customizations.json"

        fun fromJson(json: JSONObject): ArtistCustomizationsBackupFile {
            val customArray = json.optJSONArray("customizations") ?: JSONArray()
            val customizations = mutableListOf<ArtistCustomizationBackupEntry>()
            for (i in 0 until customArray.length()) {
                customizations.add(ArtistCustomizationBackupEntry.fromJson(customArray.getJSONObject(i)))
            }
            return ArtistCustomizationsBackupFile(
                version = json.optInt("version", 1),
                exportedAt = json.optLong("exportedAt", 0),
                customizations = customizations
            )
        }
    }
}

data class ArtistCustomizationBackupEntry(
    val artistName: String,
    val isFavorite: Boolean = false,
    val addedToFavoritesAt: String? = null,
    val color: String? = null,           // Hex color like "#FF5722"
    val imageKey: String? = null         // Reference to backup/artist_images/{key}.jpg
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("artistName", artistName)
            put("isFavorite", isFavorite)
            addedToFavoritesAt?.let { put("addedToFavoritesAt", it) }
            color?.let { put("color", it) }
            imageKey?.let { put("imageKey", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ArtistCustomizationBackupEntry {
            return ArtistCustomizationBackupEntry(
                artistName = json.optString("artistName", ""),
                isFavorite = json.optBoolean("isFavorite", false),
                addedToFavoritesAt = json.optString("addedToFavoritesAt").takeIf { it.isNotBlank() },
                color = json.optString("color").takeIf { it.isNotBlank() },
                imageKey = json.optString("imageKey").takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Playlists backup file.
 *
 * File: backup/playlists.json
 */
data class PlaylistsBackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val playlists: List<PlaylistBackupEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("exportedAt", exportedAt)
            put("playlists", JSONArray().apply {
                playlists.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        const val FILENAME = "playlists.json"

        fun fromJson(json: JSONObject): PlaylistsBackupFile {
            val playlistsArray = json.optJSONArray("playlists") ?: JSONArray()
            val playlists = mutableListOf<PlaylistBackupEntry>()
            for (i in 0 until playlistsArray.length()) {
                playlists.add(PlaylistBackupEntry.fromJson(playlistsArray.getJSONObject(i)))
            }
            return PlaylistsBackupFile(
                version = json.optInt("version", 1),
                exportedAt = json.optLong("exportedAt", 0),
                playlists = playlists
            )
        }
    }
}

data class PlaylistBackupEntry(
    val id: String,
    val name: String,
    val createdAt: String,
    val tracks: List<PlaylistTrackBackupEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("createdAt", createdAt)
            put("tracks", JSONArray().apply {
                tracks.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlaylistBackupEntry {
            val tracksArray = json.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableListOf<PlaylistTrackBackupEntry>()
            for (i in 0 until tracksArray.length()) {
                tracks.add(PlaylistTrackBackupEntry.fromJson(tracksArray.getJSONObject(i)))
            }
            return PlaylistBackupEntry(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                createdAt = json.optString("createdAt", ""),
                tracks = tracks
            )
        }
    }
}

data class PlaylistTrackBackupEntry(
    val title: String,
    val artist: String,
    val album: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PlaylistTrackBackupEntry {
            return PlaylistTrackBackupEntry(
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                album = json.optString("album", "")
            )
        }
    }
}

/**
 * User preferences backup file.
 *
 * File: backup/preferences.json
 */
data class PreferencesBackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val preferences: Map<String, Any>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("exportedAt", exportedAt)
            put("preferences", JSONObject(preferences))
        }
    }

    companion object {
        const val FILENAME = "preferences.json"

        fun fromJson(json: JSONObject): PreferencesBackupFile {
            val prefsJson = json.optJSONObject("preferences") ?: JSONObject()
            val preferences = mutableMapOf<String, Any>()
            prefsJson.keys().forEach { key ->
                preferences[key] = prefsJson.get(key)
            }
            return PreferencesBackupFile(
                version = json.optInt("version", 1),
                exportedAt = json.optLong("exportedAt", 0),
                preferences = preferences
            )
        }
    }
}

/**
 * Index of artist images stored in backup.
 *
 * File: backup/artist_images_index.json
 */
data class ArtistImagesIndex(
    val version: Int = 1,
    val images: List<ArtistImageEntry>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("images", JSONArray().apply {
                images.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        const val FILENAME = "artist_images_index.json"

        fun fromJson(json: JSONObject): ArtistImagesIndex {
            val imagesArray = json.optJSONArray("images") ?: JSONArray()
            val images = mutableListOf<ArtistImageEntry>()
            for (i in 0 until imagesArray.length()) {
                images.add(ArtistImageEntry.fromJson(imagesArray.getJSONObject(i)))
            }
            return ArtistImagesIndex(
                version = json.optInt("version", 1),
                images = images
            )
        }
    }
}

data class ArtistImageEntry(
    val artistKey: String,          // Lowercase artist name used as key
    val artistName: String,         // Original artist name
    val filename: String,           // Filename on Drive: "artist_img_{hash}.jpg"
    val uploadedAt: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("artistKey", artistKey)
            put("artistName", artistName)
            put("filename", filename)
            put("uploadedAt", uploadedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ArtistImageEntry {
            return ArtistImageEntry(
                artistKey = json.optString("artistKey", ""),
                artistName = json.optString("artistName", ""),
                filename = json.optString("filename", ""),
                uploadedAt = json.optLong("uploadedAt", 0)
            )
        }
    }
}
