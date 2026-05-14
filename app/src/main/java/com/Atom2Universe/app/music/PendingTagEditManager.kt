package com.Atom2Universe.app.music

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PendingTagEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire des éditions de tags en attente.
 *
 * Quand un fichier est en cours de lecture, on ne peut pas le modifier de manière sûre.
 * Les éditions sont mises en attente et appliquées :
 * - Dès que le morceau n'est plus en lecture
 * - Au redémarrage de l'app
 */
object PendingTagEditManager {

    private const val TAG = "PendingTagEditManager"

    /**
     * Vérifie si un fichier est actuellement en cours de lecture.
     */
    fun isFileCurrentlyPlaying(filePath: String): Boolean {
        val currentTrack = MusicPlaybackHolder.getCurrentTrack()
        return currentTrack?.filePath == filePath
    }

    /**
     * Ajoute une édition de tags en attente.
     */
    suspend fun addPendingEdit(
        context: Context,
        filePath: String,
        title: String? = null,
        artist: String? = null,
        albumArtist: String? = null,
        album: String? = null,
        year: String? = null,
        trackNumber: String? = null,
        discNumber: String? = null,
        coverArtPath: String? = null
    ) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val edit = PendingTagEdit(
            filePath = filePath,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            album = album,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            coverArtPath = coverArtPath
        )
        db.pendingTagEditDao().insertPendingEdit(edit)
        Log.d(TAG, "Édition en attente ajoutée pour: $filePath")
    }

    /**
     * Vérifie s'il y a des éditions en attente.
     */
    suspend fun hasPendingEdits(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.pendingTagEditDao().hasPendingEdits()
    }

    /**
     * Retourne le nombre d'éditions en attente.
     */
    suspend fun getPendingEditCount(context: Context): Int = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.pendingTagEditDao().getPendingEditCount()
    }

    /**
     * Applique toutes les éditions en attente qui ne sont plus en lecture.
     * Appelé au changement de piste et au démarrage de l'app.
     *
     * @return Le nombre d'éditions appliquées avec succès
     */
    suspend fun applyPendingEdits(context: Context): Int = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        val pendingEdits = db.pendingTagEditDao().getAllPendingEdits()

        if (pendingEdits.isEmpty()) {
            return@withContext 0
        }

        Log.d(TAG, "Application de ${pendingEdits.size} édition(s) en attente...")

        var appliedCount = 0
        val currentTrackPath = MusicPlaybackHolder.getCurrentTrack()?.filePath

        for (edit in pendingEdits) {
            // Ne pas appliquer si le fichier est toujours en lecture
            if (edit.filePath == currentTrackPath) {
                Log.d(TAG, "Fichier toujours en lecture, édition reportée: ${edit.filePath}")
                continue
            }

            try {
                val success = applyEdit(context, edit)
                if (success) {
                    // Supprimer l'édition de la liste d'attente
                    db.pendingTagEditDao().deletePendingEdit(edit.filePath)
                    appliedCount++
                    Log.d(TAG, "Édition appliquée avec succès: ${edit.filePath}")
                } else {
                    Log.w(TAG, "Échec de l'application de l'édition: ${edit.filePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'application de l'édition: ${edit.filePath}", e)
            }
        }

        Log.d(TAG, "$appliedCount édition(s) appliquée(s) sur ${pendingEdits.size}")
        appliedCount
    }

    /**
     * Applique une édition spécifique.
     */
    private suspend fun applyEdit(context: Context, edit: PendingTagEdit): Boolean {
        // Trouver la piste correspondante
        val track = MusicLibrary.getTrackByFilePath(edit.filePath)
            ?: return false // Piste non trouvée dans la bibliothèque

        // Construire le TagInfo avec les modifications
        val tagInfo = MusicTagEditor.TagInfo(
            title = edit.title ?: track.title,
            artist = edit.artist ?: track.artist,
            albumArtist = edit.albumArtist ?: track.albumArtist ?: "",
            album = edit.album ?: track.album,
            year = edit.year ?: (track.year?.toString() ?: ""),
            trackNumber = edit.trackNumber ?: (track.trackNumber?.toString() ?: ""),
            discNumber = edit.discNumber ?: ""
        )

        // Appliquer les tags texte
        val tagsSuccess = MusicTagEditor.writeTags(context, track, tagInfo)

        // Appliquer la pochette si spécifiée
        var coverSuccess = true
        if (edit.coverArtPath != null) {
            val count = MusicTagEditor.embedAlbumArt(context, listOf(track), edit.coverArtPath)
            coverSuccess = count > 0
        }

        return tagsSuccess && coverSuccess
    }

    /**
     * Vérifie si un fichier a une édition en attente.
     */
    suspend fun hasPendingEditForFile(context: Context, filePath: String): Boolean = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.pendingTagEditDao().hasPendingEditForFile(filePath)
    }

    /**
     * Supprime toutes les éditions en attente.
     */
    suspend fun clearAllPendingEdits(context: Context) = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getInstance(context)
        db.pendingTagEditDao().deleteAllPendingEdits()
        Log.d(TAG, "Toutes les éditions en attente ont été supprimées")
    }
}
