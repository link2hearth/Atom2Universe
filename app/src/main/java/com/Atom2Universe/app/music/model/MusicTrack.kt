package com.Atom2Universe.app.music.model

import android.net.Uri
import com.Atom2Universe.app.A2UApplication
import com.Atom2Universe.app.R
import java.io.File

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri? = null,
    val filePath: String? = null,  // Chemin absolu du fichier pour l'édition de tags
    val trackNumber: Int? = null,  // Numéro de piste pour le tri
    val discNumber: Int? = null,   // Numéro de disque pour les albums multi-disques
    val year: Int? = null,         // Année de sortie
    val albumArtist: String? = null // Artiste de l'album (différent de l'artiste de la piste)
) {
    // Format en ressource (EN + FR) ; le contexte de l'appli suit la langue choisie
    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return A2UApplication.appContext.getString(R.string.music_duration_minutes_seconds, minutes, seconds)
        }

    /**
     * URI stable pour la lecture audio.
     * Utilise le chemin du fichier (qui ne change jamais) au lieu de l'URI MediaStore
     * (qui change quand les tags ID3 sont modifiés car l'ID est réassigné).
     */
    val playbackUri: Uri
        get() = filePath?.let { Uri.fromFile(File(it)) } ?: uri
}
