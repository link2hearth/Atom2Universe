package com.Atom2Universe.app.midi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity représentant un fichier MIDI dans la bibliothèque
 * Indices ajoutés pour optimiser les requêtes fréquentes sur filePath, artist, album
 */
@Entity(
    tableName = "midi_tracks",
    indices = [
        Index(value = ["filePath"], unique = true),  // Unicité du chemin + recherche rapide
        Index(value = ["artist"]),                   // Groupement par artiste
        Index(value = ["album"]),                    // Groupement par album
        Index(value = ["title"])                     // Recherche par titre
    ]
)
data class MidiTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Titre du morceau */
    val title: String,

    /** Artiste */
    val artist: String,

    /** Album */
    val album: String,

    /** Chemin du fichier (Content URI) */
    val filePath: String,

    /** Durée en millisecondes */
    val duration: Long,

    /** Date d'ajout (timestamp) */
    val dateAdded: Long,

    /** Taille du fichier en octets */
    val fileSize: Long
)
