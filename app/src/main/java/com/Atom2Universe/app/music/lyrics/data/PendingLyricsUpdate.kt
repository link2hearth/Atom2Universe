package com.Atom2Universe.app.music.lyrics.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * File d'attente pour l'écriture asynchrone des paroles dans les tags USLT.
 * Utilisé par LyricsSyncManager pour éviter de modifier un fichier en cours de lecture.
 */
@Entity(tableName = "pending_lyrics_updates")
data class PendingLyricsUpdate(
    @PrimaryKey
    val filePath: String,           // Chemin absolu du fichier MP3

    val trackId: Long,              // MediaStore ID
    val lyrics: String,             // Paroles à écrire
    val language: String = "eng",   // Code ISO 639-2 (3 lettres)
    val description: String = "",   // Description USLT (généralement vide)

    val createdAt: Long = System.currentTimeMillis(),
    val lastAttempt: Long = 0,      // Timestamp de la dernière tentative
    val attemptCount: Int = 0       // Nombre de tentatives (max 3)
)
