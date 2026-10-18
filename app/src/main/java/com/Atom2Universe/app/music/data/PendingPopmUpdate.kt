package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour stocker les modifications POPM en attente.
 * Ces modifications seront appliquées au fichier MP3 dès que possible
 * (quand le fichier n'est plus en cours de lecture).
 */
@Entity(tableName = "pending_popm_updates")
data class PendingPopmUpdate(
    @PrimaryKey
    val filePath: String,           // Chemin absolu du fichier MP3

    val trackId: Long,              // ID du track dans MediaStore

    val playCountDelta: Int = 0,    // Nombre d'écoutes à ajouter (+1, +2, etc.)

    val newRating: Long? = null,    // Nouvelle note (0-255), null = pas de changement

    val createdAt: Long = System.currentTimeMillis(),  // Timestamp de création

    val lastAttempt: Long = 0,      // Timestamp de la dernière tentative d'écriture

    val attemptCount: Int = 0       // Nombre de tentatives échouées
)
