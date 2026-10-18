package com.Atom2Universe.app.music.lyrics.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache des paroles récupérées depuis les APIs ou les fichiers.
 * Permet un accès rapide aux paroles sans requête réseau.
 */
@Entity(tableName = "lyrics_cache")
data class LyricsEntity(
    @PrimaryKey
    val metadataKey: String,        // Format: "title-artist-album" (lowercase)

    val trackId: Long,              // MediaStore track ID
    val lyrics: String,             // Texte complet des paroles
    val source: String,             // "lrclib", "lyrics.ovh", "manual", "file"
    val language: String? = null,   // Code ISO 639-2 (ex: "eng", "fra")
    val isSynced: Boolean = false,  // True si format LRC avec timestamps

    val fetchedAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),

    // État de synchronisation avec le fichier
    val isSyncedToFile: Boolean = false,  // True si écrit dans le tag USLT

    // Marqueur interne : les APIs n'ont retourné aucun résultat pour ce titre.
    // Permet d'éviter des appels API répétés inutiles. Effacé si l'utilisateur
    // sauvegarde des paroles manuellement.
    val noLyricsFound: Boolean = false
)
