package com.Atom2Universe.app.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.Atom2Universe.app.music.data.CachedTrackEntity
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire du cache des pistes musicales.
 *
 * Permet un chargement instantané de la bibliothèque depuis Room au lieu de
 * rescanner MediaStore à chaque lancement. Le scan MediaStore est effectué
 * uniquement sur demande explicite (bouton refresh).
 */
object MusicCacheManager {

    private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

    /**
     * Charge les pistes depuis le cache Room.
     * Retourne null si le cache est vide.
     */
    suspend fun loadFromCache(context: Context): List<MusicTrack>? = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val dao = db.cachedTrackDao()

        if (!dao.hasCache()) {
            return@withContext null
        }

        val cachedTracks = dao.getAllTracks()
        cachedTracks.map { it.toMusicTrack() }
    }

    /**
     * Vérifie si le cache contient des pistes.
     */
    suspend fun hasCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().hasCache()
    }

    /**
     * Retourne le nombre de pistes en cache.
     */
    suspend fun getCacheCount(context: Context): Int = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().getTrackCount()
    }

    /**
     * Sauvegarde les pistes scannées dans le cache Room.
     * Remplace tout le contenu existant.
     */
    suspend fun saveToCache(context: Context, tracks: List<MusicTrack>) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val dao = db.cachedTrackDao()

        val entities = tracks.map { it.toCachedEntity() }
        dao.replaceAllTracks(entities)
    }

    /**
     * Met à jour une piste spécifique dans le cache.
     * Utilisé après modification des tags ID3.
     */
    suspend fun updateTrackInCache(context: Context, track: MusicTrack) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().insertTrack(track.toCachedEntity())
    }

    /**
     * Met à jour plusieurs pistes dans le cache.
     */
    suspend fun updateTracksInCache(context: Context, tracks: List<MusicTrack>) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().insertTracks(tracks.map { it.toCachedEntity() })
    }

    /**
     * Supprime une piste du cache par son chemin de fichier.
     */
    suspend fun removeTrackFromCache(context: Context, filePath: String) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().deleteTrackByFilePath(filePath)
    }

    /**
     * Vide complètement le cache.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.cachedTrackDao().clearCache()
    }

    /**
     * Vérifie si de nouvelles pistes ont été ajoutées dans MediaStore
     * en comparant le nombre de fichiers dans les dossiers configurés.
     *
     * @return Le nombre de nouvelles pistes détectées, ou null en cas d'erreur
     */
    suspend fun checkForNewTracks(context: Context): Int? = withContext(Dispatchers.IO) {
        try {
            val cacheCount = getCacheCount(context)
            if (cacheCount == 0) return@withContext null

            // Compte les pistes dans MediaStore pour les dossiers configurés
            MusicFoldersManager.init(context)
            val folderPaths = MusicFoldersManager.getAllFolderPaths(context)

            var mediaStoreCount = 0
            val seenIds = mutableSetOf<Long>()

            for (folderPath in folderPaths) {
                val count = countTracksInFolder(context, folderPath, seenIds)
                mediaStoreCount += count
            }

            val diff = mediaStoreCount - cacheCount
            if (diff > 0) diff else 0
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compte les pistes dans un dossier MediaStore sans les charger en mémoire.
     */
    private fun countTracksInFolder(
        context: Context,
        folderPath: String,
        seenIds: MutableSet<Long>
    ): Int {
        var count = 0
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                if (!seenIds.contains(id)) {
                    seenIds.add(id)
                    count++
                }
            }
        }
        return count
    }

    /**
     * Convertit une entité cache en MusicTrack.
     */
    private fun CachedTrackEntity.toMusicTrack(): MusicTrack {
        return MusicTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = Uri.parse(uri),
            albumArtUri = albumArtUri?.let { Uri.parse(it) },
            filePath = filePath,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            albumArtist = albumArtist
        )
    }

    /**
     * Convertit un MusicTrack en entité cache.
     */
    private fun MusicTrack.toCachedEntity(): CachedTrackEntity {
        // Extraire l'albumId depuis l'albumArtUri si disponible
        val albumId = albumArtUri?.let {
            try {
                ContentUris.parseId(it)
            } catch (e: Exception) {
                0L
            }
        } ?: 0L

        return CachedTrackEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri.toString(),
            albumArtUri = albumArtUri?.toString(),
            filePath = filePath,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            albumArtist = albumArtist,
            albumId = albumId
        )
    }
}
