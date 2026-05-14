package com.Atom2Universe.app.music

import android.content.Context
import androidx.core.net.toUri
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.Artist
import com.Atom2Universe.app.music.model.Folder
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache de la bibliothèque musicale organisée.
 *
 * Stocke la structure complète (Artistes → Albums → Pistes) en JSON
 * pour un chargement instantané sans avoir à réorganiser les 20000 pistes.
 */
object MusicLibraryCache {

    private const val CACHE_FILE_NAME = "music_library_cache.json"
    private const val CACHE_VERSION = 2  // Incremented for folder tree support

    /**
     * Vérifie si un cache existe.
     */
    @Suppress("unused")
    fun hasCache(context: Context): Boolean {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        return cacheFile.exists() && cacheFile.length() > 0
    }

    /**
     * Charge la bibliothèque depuis le cache.
     * Retourne null si le cache n'existe pas ou est invalide.
     */
    suspend fun loadFromCache(context: Context): CachedLibrary? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (!cacheFile.exists()) return@withContext null

            val json = cacheFile.readText()
            val root = JSONObject(json)

            // Vérifier la version du cache
            val version = root.optInt("version", 0)
            if (version != CACHE_VERSION) {
                cacheFile.delete()
                return@withContext null
            }

            // Charger les pistes
            val tracksArray = root.getJSONArray("tracks")
            val allTracks = mutableListOf<MusicTrack>()
            val tracksById = mutableMapOf<Long, MusicTrack>()

            for (i in 0 until tracksArray.length()) {
                val trackJson = tracksArray.getJSONObject(i)
                val track = parseTrack(trackJson)
                allTracks.add(track)
                tracksById[track.id] = track
            }

            // Charger les artistes (par artiste de piste)
            val artistsArray = root.getJSONArray("artists")
            val artists = mutableListOf<Artist>()
            for (i in 0 until artistsArray.length()) {
                val artistJson = artistsArray.getJSONObject(i)
                artists.add(parseArtist(artistJson, tracksById))
            }

            // Charger les album artists
            val albumArtistsArray = root.getJSONArray("albumArtists")
            val albumArtists = mutableListOf<Artist>()
            for (i in 0 until albumArtistsArray.length()) {
                val artistJson = albumArtistsArray.getJSONObject(i)
                albumArtists.add(parseArtist(artistJson, tracksById))
            }

            // Charger tous les albums
            val allAlbumsArray = root.getJSONArray("allAlbums")
            val allAlbums = mutableListOf<Album>()
            for (i in 0 until allAlbumsArray.length()) {
                val albumJson = allAlbumsArray.getJSONObject(i)
                allAlbums.add(parseAlbum(albumJson, tracksById))
            }

            // Charger l'arborescence des dossiers
            val folderTree = if (root.has("folderTree")) {
                parseFolder(root.getJSONObject("folderTree"), tracksById)
            } else {
                null
            }

            // Charger l'ordre de tri
            val sortOrderStr = root.optString("sortOrder", "NAME_ASC")
            val sortOrder = try {
                MusicLibrary.AlbumSortOrder.valueOf(sortOrderStr)
            } catch (_: Exception) {
                MusicLibrary.AlbumSortOrder.NAME_ASC
            }

            CachedLibrary(
                allTracks = allTracks,
                artists = artists,
                albumArtists = albumArtists,
                allAlbums = allAlbums,
                folderTree = folderTree,
                sortOrder = sortOrder
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Cache invalide, le supprimer
            File(context.filesDir, CACHE_FILE_NAME).delete()
            null
        }
    }

    /**
     * Sauvegarde la bibliothèque dans le cache.
     */
    suspend fun saveToCache(
        context: Context,
        allTracks: List<MusicTrack>,
        artists: List<Artist>,
        albumArtists: List<Artist>,
        allAlbums: List<Album>,
        folderTree: Folder?,
        sortOrder: MusicLibrary.AlbumSortOrder
    ) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("version", CACHE_VERSION)
            root.put("sortOrder", sortOrder.name)

            // Sauvegarder les pistes
            val tracksArray = JSONArray()
            for (track in allTracks) {
                tracksArray.put(trackToJson(track))
            }
            root.put("tracks", tracksArray)

            // Sauvegarder les artistes
            val artistsArray = JSONArray()
            for (artist in artists) {
                artistsArray.put(artistToJson(artist))
            }
            root.put("artists", artistsArray)

            // Sauvegarder les album artists
            val albumArtistsArray = JSONArray()
            for (artist in albumArtists) {
                albumArtistsArray.put(artistToJson(artist))
            }
            root.put("albumArtists", albumArtistsArray)

            // Sauvegarder tous les albums
            val allAlbumsArray = JSONArray()
            for (album in allAlbums) {
                allAlbumsArray.put(albumToJson(album))
            }
            root.put("allAlbums", allAlbumsArray)

            // Sauvegarder l'arborescence des dossiers
            if (folderTree != null) {
                root.put("folderTree", folderToJson(folderTree))
            }

            // Écrire dans le fichier
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            cacheFile.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Supprime le cache.
     */
    @Suppress("unused")
    fun clearCache(context: Context) {
        File(context.filesDir, CACHE_FILE_NAME).delete()
    }

    // === Sérialisation ===

    private fun trackToJson(track: MusicTrack): JSONObject {
        return JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration", track.duration)
            put("uri", track.uri.toString())
            put("albumArtUri", track.albumArtUri?.toString() ?: JSONObject.NULL)
            put("filePath", track.filePath ?: JSONObject.NULL)
            put("trackNumber", track.trackNumber ?: JSONObject.NULL)
            put("discNumber", track.discNumber ?: JSONObject.NULL)
            put("year", track.year ?: JSONObject.NULL)
            put("albumArtist", track.albumArtist ?: JSONObject.NULL)
        }
    }

    private fun albumToJson(album: Album): JSONObject {
        return JSONObject().apply {
            put("id", album.id)
            put("name", album.name)
            put("artist", album.artist)
            put("albumArtUri", album.albumArtUri?.toString() ?: JSONObject.NULL)
            put("year", album.year ?: JSONObject.NULL)
            // Stocker uniquement les IDs des pistes pour réduire la taille
            val trackIds = JSONArray()
            for (track in album.tracks) {
                trackIds.put(track.id)
            }
            put("trackIds", trackIds)
        }
    }

    private fun artistToJson(artist: Artist): JSONObject {
        return JSONObject().apply {
            put("name", artist.name)
            put("trackCount", artist.trackCount)
            val albumsArray = JSONArray()
            for (album in artist.albums) {
                albumsArray.put(albumToJson(album))
            }
            put("albums", albumsArray)
        }
    }

    private fun folderToJson(folder: Folder): JSONObject {
        return JSONObject().apply {
            put("path", folder.path)
            put("name", folder.name)
            // Store track IDs instead of full track objects
            val trackIds = JSONArray()
            for (track in folder.tracks) {
                trackIds.put(track.id)
            }
            put("trackIds", trackIds)
            // Recursively serialize subfolders
            val subfoldersArray = JSONArray()
            for (subfolder in folder.subfolders) {
                subfoldersArray.put(folderToJson(subfolder))
            }
            put("subfolders", subfoldersArray)
        }
    }

    // === Désérialisation ===

    private fun parseTrack(json: JSONObject): MusicTrack {
        return MusicTrack(
            id = json.getLong("id"),
            title = json.getString("title"),
            artist = json.getString("artist"),
            album = json.getString("album"),
            duration = json.getLong("duration"),
            uri = json.getString("uri").toUri(),
            albumArtUri = json.optString("albumArtUri").takeIf { it.isNotBlank() && it != "null" }?.toUri(),
            filePath = json.optString("filePath").takeIf { it.isNotBlank() && it != "null" },
            trackNumber = if (json.isNull("trackNumber")) null else json.optInt("trackNumber"),
            discNumber = if (json.isNull("discNumber")) null else json.optInt("discNumber"),
            year = if (json.isNull("year")) null else json.optInt("year"),
            albumArtist = json.optString("albumArtist").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseAlbum(json: JSONObject, tracksById: Map<Long, MusicTrack>): Album {
        val album = Album(
            id = json.getLong("id"),
            name = json.getString("name"),
            artist = json.getString("artist"),
            albumArtUri = json.optString("albumArtUri").takeIf { it.isNotBlank() && it != "null" }?.toUri(),
            year = if (json.isNull("year")) null else json.optInt("year")
        )

        // Reconstruire la liste des pistes à partir des IDs
        val trackIds = json.getJSONArray("trackIds")
        for (i in 0 until trackIds.length()) {
            val trackId = trackIds.getLong(i)
            tracksById[trackId]?.let { album.tracks.add(it) }
        }

        return album
    }

    private fun parseArtist(json: JSONObject, tracksById: Map<Long, MusicTrack>): Artist {
        val artist = Artist(
            name = json.getString("name"),
            trackCount = json.getInt("trackCount")
        )

        val albumsArray = json.getJSONArray("albums")
        for (i in 0 until albumsArray.length()) {
            val albumJson = albumsArray.getJSONObject(i)
            artist.albums.add(parseAlbum(albumJson, tracksById))
        }

        return artist
    }

    private fun parseFolder(json: JSONObject, tracksById: Map<Long, MusicTrack>): Folder {
        val folder = Folder(
            path = json.getString("path"),
            name = json.getString("name")
        )

        // Restore tracks from IDs
        val trackIds = json.getJSONArray("trackIds")
        for (i in 0 until trackIds.length()) {
            val trackId = trackIds.getLong(i)
            tracksById[trackId]?.let { folder.tracks.add(it) }
        }

        // Recursively parse subfolders
        val subfoldersArray = json.getJSONArray("subfolders")
        for (i in 0 until subfoldersArray.length()) {
            val subfolderJson = subfoldersArray.getJSONObject(i)
            folder.subfolders.add(parseFolder(subfolderJson, tracksById))
        }

        return folder
    }

    /**
     * Données de la bibliothèque en cache.
     */
    data class CachedLibrary(
        val allTracks: List<MusicTrack>,
        val artists: List<Artist>,
        val albumArtists: List<Artist>,
        val allAlbums: List<Album>,
        val folderTree: Folder?,
        val sortOrder: MusicLibrary.AlbumSortOrder
    )
}
