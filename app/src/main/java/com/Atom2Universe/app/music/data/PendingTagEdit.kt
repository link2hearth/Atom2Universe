package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente une édition de tags en attente.
 *
 * Quand un morceau est en cours de lecture, on ne peut pas modifier son fichier
 * de manière sûre. L'édition est donc mise en attente et sera appliquée :
 * - Dès que le morceau n'est plus en lecture (changement de piste)
 * - Ou au redémarrage de l'app si elle a été fermée
 */
@Entity(tableName = "pending_tag_edits")
data class PendingTagEdit(
    @PrimaryKey
    val filePath: String,           // Chemin du fichier à éditer (clé unique)
    val title: String?,             // Nouveau titre (null = pas de changement)
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val year: String?,
    val trackNumber: String?,
    val discNumber: String?,
    val coverArtPath: String?,      // Chemin vers l'image de pochette à appliquer
    val createdAt: Long = System.currentTimeMillis()
)
