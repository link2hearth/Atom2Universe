package com.Atom2Universe.app.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Frame
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.AbstractTag
import org.jaudiotagger.tag.id3.framebody.FrameBodyPOPM
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Éditeur de tags ID3 pour les fichiers MP3.
 * Utilise le frame POPM (Popularimeter) pour marquer les favoris.
 *
 * POPM frame format:
 * - Email: identifiant de l'application
 * - Rating: 0-255 (on utilise 255 pour favori, 0 sinon)
 * - Counter: nombre de lectures (non utilisé)
 */
object MusicTagEditor {

    private const val TAG = "MusicTagEditor"
    private const val POPM_EMAIL = "free@app"
    private const val RATING_FAVORITE = 255L
    private const val RATING_NOT_FAVORITE = 0L
    private const val POPM_MAX_REASONABLE = 100_000L  // Refuse si > 100k (probablement corrompu)

    /**
     * Mutex global pour protéger toutes les écritures de tags.
     * CRITIQUE: Empêche MusicPopmSyncManager et LyricsSyncManager
     * d'écrire au même fichier simultanément.
     *
     * PUBLIC: MusicPopmSyncManager a besoin d'y accéder car il écrit
     * directement dans les fichiers sans passer par MusicTagEditor.
     */
    val fileWriteMutex = Mutex()

    init {
        // Désactive les logs verbeux de JAudioTagger
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Définit ou retire le tag favori d'un fichier MP3
     */
    suspend fun setFavoriteTag(context: Context, track: MusicTrack, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(context)
        if (!preferences.writeTagsToFiles) {
            Log.d(TAG, "Tag writing disabled, skipping favorite tag update for: ${track.title}")
            return@withContext true // Retourne success sans écrire
        }

        val filePath = getFilePath(track)
        if (filePath == null) {
            Log.w(TAG, "Cannot get file path for track: ${track.title}")
            return@withContext false
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $filePath")
                return@withContext false
            }

            if (!file.canWrite()) {
                Log.w(TAG, "File is not writable: $filePath")
                return@withContext false
            }

            // Vérifie que c'est un MP3
            if (!filePath.lowercase().endsWith(".mp3")) {
                Log.d(TAG, "Not an MP3 file, skipping tag edit: $filePath")
                return@withContext false
            }

            // MUTEX GLOBAL: Protège contre les écritures concurrentes de POPM et USLT
            fileWriteMutex.withLock {
                val audioFile = AudioFileIO.read(file)
                // Convertir en ID3v2.3 si nécessaire
                // ID3v2.2 utilise des frame IDs de 3 bytes, incompatible avec POPM (4 bytes)
                val id3Tag: AbstractID3v2Tag = when (val tag = audioFile.tagOrCreateAndSetDefault) {
                    is ID3v22Tag -> {
                        // Upgrader ID3v2.2 vers ID3v2.3
                        Log.d(TAG, "Upgrading ID3v2.2 to ID3v2.3 for: $filePath")
                        val newTag = ID3v23Tag(tag as AbstractTag)
                        audioFile.tag = newTag
                        newTag
                    }
                    is AbstractID3v2Tag -> tag
                    else -> {
                        // Créer un nouveau tag ID3v2.3 (pour ID3v1 ou pas de tag)
                        val newTag = ID3v23Tag()
                        audioFile.tag = newTag
                        newTag
                    }
                }

                // Lire le play count existant AVANT de supprimer les frames
                val existingPlayCount = readExistingPlayCount(id3Tag)

                // Supprime les anciens frames POPM
                removeExistingPopmFrames(id3Tag)

                // Crée le nouveau frame avec le rating approprié et le play count préservé
                val newRating = if (isFavorite) RATING_FAVORITE else RATING_NOT_FAVORITE
                val popmBody = FrameBodyPOPM(POPM_EMAIL, newRating, existingPlayCount)
                val popmFrame = ID3v23Frame(ID3v24Frames.FRAME_ID_POPULARIMETER)
                popmFrame.body = popmBody
                id3Tag.setFrame(popmFrame)

                audioFile.commit()
                Log.d(TAG, "Successfully ${if (isFavorite) "added" else "removed"} favorite tag for: ${track.title}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error editing tag for ${track.title}: ${e.message}", e)
            false
        }
    }

    /**
     * Lit le statut favori depuis le tag POPM d'un fichier MP3
     */
    suspend fun getFavoriteTag(track: MusicTrack): Boolean = withContext(Dispatchers.IO) {
        val filePath = getFilePath(track)
        if (filePath == null) {
            return@withContext false
        }

        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            if (!filePath.lowercase().endsWith(".mp3")) {
                return@withContext false
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag as? AbstractID3v2Tag ?: return@withContext false

            // Cherche un frame POPM de notre app
            val frameId = ID3v24Frames.FRAME_ID_POPULARIMETER
            val frame = tag.getFrame(frameId)

            if (frame != null) {
                val body = when (frame) {
                    is AbstractID3v2Frame -> frame.body as? FrameBodyPOPM
                    else -> null
                }

                if (body != null && body.emailToUser == POPM_EMAIL && body.rating >= 1) {
                    return@withContext true
                }
            }

            // Vérifie aussi s'il y a plusieurs frames POPM
            val frames = tag.getFields(frameId)
            for (f in frames) {
                if (f is AbstractID3v2Frame) {
                    val body = f.body as? FrameBodyPOPM
                    if (body != null && body.emailToUser == POPM_EMAIL && body.rating >= 1) {
                        return@withContext true
                    }
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error reading tag: ${e.message}")
            false
        }
    }

    /**
     * Synchronise les favoris du fichier JSON avec les tags POPM des MP3.
     * Utile pour importer les favoris après avoir copié des MP3 depuis un autre appareil.
     */
    @Suppress("unused", "UNUSED_PARAMETER")
    suspend fun syncFavoritesFromTags(context: Context, tracks: List<MusicTrack>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0

        for (track in tracks) {
            try {
                val hasPopmFavorite = getFavoriteTag(track)
                val isInJson = MusicFavoritesManager.isFavorite(track)

                // Si le tag POPM indique favori mais pas dans le JSON, on l'ajoute
                if (hasPopmFavorite && !isInJson) {
                    MusicFavoritesManager.addFavorite(track)
                    importedCount++
                    Log.d(TAG, "Imported favorite from POPM tag: ${track.title}")
                }
            } catch (_: Exception) {
                // Ignore les erreurs individuelles
            }
        }

        if (importedCount > 0) {
            Log.i(TAG, "Imported $importedCount favorites from POPM tags")
        }
        importedCount
    }

    /**
     * Lit le play count existant depuis les frames POPM.
     * Prend la valeur la plus élevée parmi tous les frames POPM trouvés.
     */
    private fun readExistingPlayCount(tag: AbstractID3v2Tag): Long {
        var maxPlayCount = 0L
        try {
            val frameId = ID3v24Frames.FRAME_ID_POPULARIMETER
            val frames = tag.getFields(frameId)
            for (f in frames) {
                if (f is AbstractID3v2Frame) {
                    val body = f.body as? FrameBodyPOPM ?: continue
                    val rawCounter = body.counter

                    // Validation: ignorer les valeurs corrompues
                    if (rawCounter > POPM_MAX_REASONABLE) {
                        Log.w(TAG, "POPM counter suspiciously high ($rawCounter) - ignoring")
                        continue
                    }

                    if (rawCounter > maxPlayCount) {
                        maxPlayCount = rawCounter
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading existing play count: ${e.message}")
        }
        return maxPlayCount
    }

    /**
     * Supprime tous les frames POPM existants
     * Note: On supprime tous les POPM et on réajoute le nôtre si nécessaire
     * car l'API ne permet pas de supprimer un frame spécifique facilement
     */
    private fun removeExistingPopmFrames(tag: AbstractID3v2Tag) {
        try {
            // Supprime tous les frames POPM
            // Note: removeFrame supprime par identifiant, pas par objet
            if (tag.hasFrame(ID3v24Frames.FRAME_ID_POPULARIMETER)) {
                tag.removeFrame(ID3v24Frames.FRAME_ID_POPULARIMETER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing POPM frames: ${e.message}")
        }
    }

    /**
     * Obtient le chemin du fichier à partir du MusicTrack
     */
    private fun getFilePath(track: MusicTrack): String? {
        // Utilise directement le filePath stocké dans MusicTrack
        return track.filePath
    }

    // ==================== ÉDITION DES TAGS ====================

    /**
     * Data class contenant les informations de tags éditables
     */
    data class TagInfo(
        val title: String = "",
        val artist: String = "",
        val albumArtist: String = "",
        val album: String = "",
        val year: String = "",
        val trackNumber: String = "",
        val discNumber: String = ""
    )

    /**
     * Lit les tags d'un fichier audio
     */
    suspend fun readTags(track: MusicTrack): TagInfo? = withContext(Dispatchers.IO) {
        val filePath = getFilePath(track)
        if (filePath == null) {
            Log.w(TAG, "Cannot get file path for track: ${track.title}")
            return@withContext null
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $filePath")
                return@withContext null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return@withContext TagInfo(
                title = track.title,
                artist = track.artist,
                album = track.album
            )

            TagInfo(
                title = tag.getFirst(FieldKey.TITLE) ?: "",
                artist = tag.getFirst(FieldKey.ARTIST) ?: "",
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST) ?: "",
                album = tag.getFirst(FieldKey.ALBUM) ?: "",
                year = tag.getFirst(FieldKey.YEAR) ?: "",
                trackNumber = tag.getFirst(FieldKey.TRACK) ?: "",
                discNumber = tag.getFirst(FieldKey.DISC_NO) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading tags for ${track.title}: ${e.message}", e)
            // Retourne les infos basiques du track
            TagInfo(
                title = track.title,
                artist = track.artist,
                album = track.album
            )
        }
    }

    /**
     * Lit l'année directement depuis les tags ID3 d'un fichier.
     * Utilisé comme fallback quand MediaStore ne retourne pas l'année.
     * Supporte différents formats: "2020", "2020-05-15", etc.
     */
    fun readYearFromFile(filePath: String): Int? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null

            val yearStr = tag.getFirst(FieldKey.YEAR)
            if (yearStr.isNullOrBlank()) return null

            // Gère différents formats: "2020", "2020-05-15", "2020/05/15"
            val yearPart = yearStr.trim().take(4)
            yearPart.toIntOrNull()?.takeIf { it in 1900..2100 }
        } catch (e: Exception) {
            Log.d(TAG, "Could not read year from file $filePath: ${e.message}")
            null
        }
    }

    /**
     * Lit le tag ALBUM_ARTIST depuis un fichier audio.
     * Retourne null si le tag n'existe pas ou est vide.
     */
    fun readAlbumArtistFromFile(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null

            val albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST)
            albumArtist?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "Could not read album artist from file $filePath: ${e.message}")
            null
        }
    }

    /**
     * Lit les années de plusieurs fichiers en batch.
     * Retourne une map filePath -> year
     */
    @Suppress("unused")
    fun readYearsFromFiles(filePaths: List<String>): Map<String, Int> {
        val results = mutableMapOf<String, Int>()
        for (path in filePaths) {
            readYearFromFile(path)?.let { year ->
                results[path] = year
            }
        }
        return results
    }

    /**
     * Écrit les tags dans un fichier audio
     */
    suspend fun writeTags(context: Context, track: MusicTrack, tagInfo: TagInfo): Boolean = withContext(Dispatchers.IO) {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(context)
        if (!preferences.writeTagsToFiles) {
            Log.d(TAG, "Tag writing disabled, skipping tag update for: ${track.title}")
            return@withContext true // Retourne success sans écrire
        }

        val filePath = getFilePath(track)
        if (filePath == null) {
            Log.w(TAG, "Cannot get file path for track: ${track.title}")
            return@withContext false
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $filePath")
                return@withContext false
            }

            if (!file.canWrite()) {
                Log.w(TAG, "File is not writable: $filePath")
                return@withContext false
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            // Mise à jour des champs
            if (tagInfo.title.isNotEmpty()) {
                tag.setField(FieldKey.TITLE, tagInfo.title)
            }
            if (tagInfo.artist.isNotEmpty()) {
                tag.setField(FieldKey.ARTIST, tagInfo.artist)
            }
            if (tagInfo.albumArtist.isNotEmpty()) {
                tag.setField(FieldKey.ALBUM_ARTIST, tagInfo.albumArtist)
            }
            if (tagInfo.album.isNotEmpty()) {
                tag.setField(FieldKey.ALBUM, tagInfo.album)
            }
            if (tagInfo.year.isNotEmpty()) {
                tag.setField(FieldKey.YEAR, tagInfo.year)
            }
            if (tagInfo.trackNumber.isNotEmpty()) {
                tag.setField(FieldKey.TRACK, tagInfo.trackNumber)
            }
            if (tagInfo.discNumber.isNotEmpty()) {
                tag.setField(FieldKey.DISC_NO, tagInfo.discNumber)
            }

            audioFile.commit()
            Log.d(TAG, "Successfully wrote tags for: ${track.title}")

            // Notify MediaStore of the change
            scanFile(context, filePath)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing tags for ${track.title}: ${e.message}", e)
            false
        }
    }

    /**
     * Renomme un fichier audio et met à jour le MediaStore.
     * Retourne le nouveau chemin si succès, sinon null.
     */
    suspend fun renameAudioFile(context: Context, filePath: String, newFileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "File does not exist: $filePath")
            return@withContext null
        }
        if (!file.canWrite()) {
            Log.w(TAG, "File is not writable: $filePath")
            return@withContext null
        }

        val parentDir = file.parentFile ?: return@withContext null
        val targetFile = File(parentDir, newFileName)
        if (targetFile.exists()) {
            Log.w(TAG, "Target file already exists: ${targetFile.absolutePath}")
            return@withContext null
        }

        val renamed = file.renameTo(targetFile)
        if (!renamed) {
            Log.w(TAG, "Failed to rename file: $filePath -> ${targetFile.absolutePath}")
            return@withContext null
        }

        scanFile(context, targetFile.absolutePath)
        targetFile.absolutePath
    }

    /**
     * Notifie le MediaStore qu'un fichier a été modifié.
     * Version synchrone qui attend la fin du scan avec timeout.
     */
    private suspend fun scanFile(context: Context, filePath: String) {
        // Force update file modification time to ensure MediaScanner re-reads metadata
        try {
            val file = File(filePath)
            file.setLastModified(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Could not update file modification time: ${e.message}")
        }

        // Wait for MediaScanner to complete with 10 second timeout
        val result = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf("audio/mpeg")
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner scanned: $path -> $uri")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        if (result == null) {
            Log.w(TAG, "MediaScanner timeout for: $filePath")
        }
    }

    /**
     * Scanne plusieurs fichiers et attend la fin
     */
    private suspend fun scanFiles(context: Context, filePaths: List<String>) {
        if (filePaths.isEmpty()) return

        // Force update file modification times
        for (filePath in filePaths) {
            try {
                val file = File(filePath)
                file.setLastModified(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "Could not update file modification time: ${e.message}")
            }
        }

        var scannedCount = 0
        val totalCount = filePaths.size

        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                filePaths.toTypedArray(),
                filePaths.map { "audio/mpeg" }.toTypedArray()
            ) { path, uri ->
                Log.d(TAG, "MediaScanner scanned: $path -> $uri")
                scannedCount++
                if (scannedCount >= totalCount && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    /**
     * Écrit les tags communs à tous les fichiers d'un album
     * (artiste, artiste album, album, année, numéro de disque)
     */
    suspend fun writeAlbumTags(context: Context, tracks: List<MusicTrack>, tagInfo: TagInfo): Int = withContext(Dispatchers.IO) {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(context)
        if (!preferences.writeTagsToFiles) {
            Log.d(TAG, "Tag writing disabled, skipping album tags update")
            return@withContext tracks.size // Retourne le nombre de tracks comme si tous avaient été mis à jour
        }

        var successCount = 0
        val modifiedPaths = mutableListOf<String>()

        for (track in tracks) {
            val filePath = getFilePath(track)
            if (filePath == null) continue

            try {
                val file = File(filePath)
                if (!file.exists() || !file.canWrite()) continue

                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault

                // Mise à jour des champs communs à l'album (pas titre ni numéro de piste)
                if (tagInfo.artist.isNotEmpty()) {
                    tag.setField(FieldKey.ARTIST, tagInfo.artist)
                }
                if (tagInfo.albumArtist.isNotEmpty()) {
                    tag.setField(FieldKey.ALBUM_ARTIST, tagInfo.albumArtist)
                }
                if (tagInfo.album.isNotEmpty()) {
                    tag.setField(FieldKey.ALBUM, tagInfo.album)
                }
                if (tagInfo.year.isNotEmpty()) {
                    tag.setField(FieldKey.YEAR, tagInfo.year)
                }
                if (tagInfo.discNumber.isNotEmpty()) {
                    tag.setField(FieldKey.DISC_NO, tagInfo.discNumber)
                }

                audioFile.commit()
                modifiedPaths.add(filePath)
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error writing album tags for ${track.title}: ${e.message}")
            }
        }

        // Notify MediaStore of all changes (synchronously)
        if (modifiedPaths.isNotEmpty()) {
            scanFiles(context, modifiedPaths)
        }

        Log.d(TAG, "Updated tags for $successCount/${tracks.size} tracks")
        successCount
    }

    // ==================== ALBUM ART ====================

    /**
     * Embed album artwork in all tracks of an album
     * @param context Context for MediaScanner
     * @param tracks List of tracks to update
     * @param imagePath Path to the image file
     * @return Number of successfully updated tracks
     */
    suspend fun embedAlbumArt(context: Context, tracks: List<MusicTrack>, imagePath: String): Int = withContext(Dispatchers.IO) {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(context)
        if (!preferences.writeTagsToFiles) {
            Log.d(TAG, "Tag writing disabled, skipping album art embedding")
            return@withContext tracks.size // Retourne le nombre de tracks comme si tous avaient été mis à jour
        }

        var successCount = 0
        val modifiedPaths = mutableListOf<String>()

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist: $imagePath")
            return@withContext 0
        }

        // Read and optionally resize image
        val artwork = try {
            ArtworkFactory.createArtworkFromFile(imageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating artwork from file: ${e.message}", e)
            return@withContext 0
        }

        for (track in tracks) {
            val filePath = getFilePath(track)
            if (filePath == null) continue

            try {
                val file = File(filePath)
                if (!file.exists() || !file.canWrite()) continue

                // Only MP3 files supported for now
                if (!filePath.lowercase().endsWith(".mp3")) continue

                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault

                // Remove existing artwork
                tag.deleteArtworkField()

                // Set new artwork
                tag.setField(artwork)

                audioFile.commit()
                modifiedPaths.add(filePath)
                successCount++
                Log.d(TAG, "Added artwork to: ${track.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error embedding artwork for ${track.title}: ${e.message}", e)
            }
        }

        // Notify MediaStore of all changes (synchronously)
        if (modifiedPaths.isNotEmpty()) {
            scanFiles(context, modifiedPaths)
        }

        Log.d(TAG, "Added artwork to $successCount/${tracks.size} tracks")
        successCount
    }

    /**
     * Resize image if too large (max 500x500 for embedded artwork)
     */
    @Suppress("unused") // Utility function for future use when embedding downloaded album art
    fun resizeImageIfNeeded(imagePath: String, maxSize: Int = 500): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            val scale = maxOf(
                options.outWidth / maxSize,
                options.outHeight / maxSize
            )

            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = if (scale > 1) scale else 1
            }

            BitmapFactory.decodeFile(imagePath, finalOptions)
        } catch (_: Exception) {
            Log.e(TAG, "Error resizing image")
            null
        }
    }

    /**
     * Lit les paroles depuis le tag USLT (Unsynchronized Lyrics).
     * Retourne null si le fichier n'a pas de paroles ou n'est pas un MP3.
     */
    suspend fun readLyrics(track: MusicTrack): String? = withContext(Dispatchers.IO) {
        val filePath = getFilePath(track)
        if (filePath == null) {
            Log.w(TAG, "Cannot get file path for track: ${track.title}")
            return@withContext null
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $filePath")
                return@withContext null
            }

            // Uniquement pour les MP3
            if (!filePath.lowercase().endsWith(".mp3")) {
                Log.d(TAG, "Not an MP3 file, skipping lyrics read: $filePath")
                return@withContext null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag as? AbstractID3v2Tag ?: return@withContext null

            // Chercher le frame USLT
            val frameId = ID3v24Frames.FRAME_ID_UNSYNC_LYRICS
            val frames = tag.getFields(frameId)

            for (frame in frames) {
                if (frame is AbstractID3v2Frame) {
                    val body = frame.body as? FrameBodyUSLT
                    if (body != null && body.lyric.isNotBlank()) {
                        Log.d(TAG, "Found lyrics in tag for: ${track.title}")
                        return@withContext body.lyric
                    }
                }
            }

            Log.d(TAG, "No lyrics found in tag for: ${track.title}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading lyrics for ${track.title}: ${e.message}", e)
            null
        }
    }

    /**
     * Écrit les paroles dans le tag USLT (Unsynchronized Lyrics).
     * Retourne true si l'opération a réussi.
     */
    suspend fun writeLyrics(
        context: Context,
        track: MusicTrack,
        lyrics: String,
        language: String = "eng",
        description: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        // Vérifier si l'écriture des tags est activée
        val preferences = MusicPreferences.getInstance(context)
        if (!preferences.writeTagsToFiles) {
            Log.d(TAG, "Tag writing disabled, skipping lyrics write for: ${track.title}")
            return@withContext true // Retourne success sans écrire
        }

        val filePath = getFilePath(track)
        if (filePath == null) {
            Log.w(TAG, "Cannot get file path for track: ${track.title}")
            return@withContext false
        }

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $filePath")
                return@withContext false
            }

            if (!file.canWrite()) {
                Log.w(TAG, "File is not writable: $filePath")
                return@withContext false
            }

            // Uniquement pour les MP3
            if (!filePath.lowercase().endsWith(".mp3")) {
                Log.d(TAG, "Not an MP3 file, skipping lyrics write: $filePath")
                return@withContext false
            }

            // MUTEX GLOBAL: Protège contre les écritures concurrentes de POPM et USLT
            fileWriteMutex.withLock {
                val audioFile = AudioFileIO.read(file)
                // Convertir en ID3v2.3 si nécessaire (même pattern que POPM)
                val id3Tag: AbstractID3v2Tag = when (val tag = audioFile.tagOrCreateAndSetDefault) {
                    is ID3v22Tag -> {
                        Log.d(TAG, "Upgrading ID3v2.2 to ID3v2.3 for: $filePath")
                        val newTag = ID3v23Tag(tag as AbstractTag)
                        audioFile.tag = newTag
                        newTag
                    }
                    is AbstractID3v2Tag -> tag
                    else -> {
                        val newTag = ID3v23Tag()
                        audioFile.tag = newTag
                        newTag
                    }
                }

                // Supprimer les anciens frames USLT
                val frameId = ID3v24Frames.FRAME_ID_UNSYNC_LYRICS
                if (id3Tag.hasFrame(frameId)) {
                    id3Tag.removeFrame(frameId)
                }

                // Créer et ajouter le nouveau frame USLT
                val usltBody = FrameBodyUSLT()
                usltBody.language = language
                usltBody.description = description
                usltBody.lyric = lyrics

                val usltFrame = ID3v23Frame(frameId)
                usltFrame.body = usltBody
                id3Tag.setFrame(usltFrame)

                // Écrire dans le fichier
                audioFile.commit()
                Log.d(TAG, "Successfully wrote lyrics for: ${track.title}")

                // Notifier MediaStore
                scanFile(context, filePath)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing lyrics for ${track.title}: ${e.message}", e)
            false
        }
    }
}
