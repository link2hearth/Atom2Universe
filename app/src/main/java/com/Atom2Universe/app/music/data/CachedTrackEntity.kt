package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour mettre en cache les pistes scannées.
 *
 * Cette table permet un chargement instantané de la bibliothèque musicale
 * sans avoir à rescanner MediaStore à chaque lancement.
 *
 * Les URIs sont stockées comme String car Room ne supporte pas directement Uri.
 */
@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey
    val id: Long,                      // MediaStore ID
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,                   // Content URI (as String)
    val albumArtUri: String? = null,   // Album art URI (as String)
    val filePath: String? = null,      // Chemin absolu du fichier
    val trackNumber: Int? = null,      // Numéro de piste
    val discNumber: Int? = null,       // Numéro de disque pour les albums multi-disques
    val year: Int? = null,             // Année de sortie
    val albumArtist: String? = null,   // Artiste de l'album
    val albumId: Long = 0              // Album ID pour reconstruire l'album art URI
)
