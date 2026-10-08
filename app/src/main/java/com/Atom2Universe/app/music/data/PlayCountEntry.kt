package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour stocker les compteurs d'écoutes.
 *
 * Utilise une clé métadonnées (artiste|titre|album) pour permettre
 * la portabilité entre appareils et la compatibilité avec les fichiers
 * déplacés ou renommés.
 *
 * Cette table est la SOURCE DE VÉRITÉ pour les compteurs d'écoutes.
 * Les tags POPM dans les fichiers MP3 sont synchronisés à partir de ces données.
 */
@Entity(
    tableName = "play_counts",
    indices = [
        Index(value = ["metadataKey"], unique = true),
        Index(value = ["playCount"]),  // For ORDER BY playCount queries
        Index(value = ["earnedPlayCount"])  // For queries on local play counts
    ]
)
data class PlayCountEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Clé unique basée sur les métadonnées: "artiste|titre|album" (lowercase, trimmed)
     * Permet de retrouver le compteur même si le fichier est déplacé
     */
    val metadataKey: String,

    /**
     * Titre du morceau (pour affichage/debug)
     */
    val title: String,

    /**
     * Artiste du morceau (pour affichage/debug)
     */
    val artist: String,

    /**
     * Album du morceau (pour affichage/debug)
     */
    val album: String,

    /**
     * Nombre total d'écoutes (toutes sources confondues: POPM importés + écoutes locales)
     * RÈGLE: Ne JAMAIS décrémenter cette valeur
     */
    val playCount: Long = 0,

    /**
     * Nombre d'écoutes effectuées SUR CET APPAREIL uniquement.
     * N'inclut PAS les imports depuis POPM (WMP, iTunes, etc.) ou d'autres appareils.
     * Utilisé pour la sync cloud: seules ces écoutes sont uploadées comme deltas.
     * Cela évite le doublement quand un MP3 avec POPM est copié sur un nouvel appareil.
     */
    val earnedPlayCount: Long = 0,

    /**
     * Timestamp de la dernière écoute (ms depuis epoch)
     */
    val lastPlayed: Long = 0,

    /**
     * Timestamp de création de l'entrée
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp de dernière modification
     */
    val updatedAt: Long = System.currentTimeMillis()
)
