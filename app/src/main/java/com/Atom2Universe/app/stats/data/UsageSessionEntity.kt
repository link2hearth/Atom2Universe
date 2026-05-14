package com.Atom2Universe.app.stats.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room représentant une session d'utilisation d'un module.
 * Utilisée pour tracker le temps passé dans chaque module (musique, MIDI, radio).
 */
@Entity(
    tableName = "usage_sessions",
    indices = [
        Index(value = ["moduleType"]),
        Index(value = ["startTimestamp"]),
        Index(value = ["endTimestamp"]),
        Index(value = ["moduleType", "startTimestamp", "endTimestamp"], unique = true)
    ]
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Type de module: "music", "midi", "radio"
     */
    val moduleType: String,

    /**
     * Timestamp de début de session (ms depuis epoch)
     */
    val startTimestamp: Long,

    /**
     * Timestamp de fin de session (ms depuis epoch)
     */
    val endTimestamp: Long,

    /**
     * Durée de la session en millisecondes
     */
    val durationMs: Long,

    // ===== Métadonnées pour MUSIQUE =====

    /**
     * Titre du morceau (pour musique)
     */
    val trackTitle: String? = null,

    /**
     * Artiste du morceau (pour musique)
     */
    val trackArtist: String? = null,

    /**
     * Album du morceau (pour musique)
     */
    val trackAlbum: String? = null,

    /**
     * Artiste de l'album (pour musique)
     */
    val trackAlbumArtist: String? = null,

    // ===== Métadonnées pour MIDI =====

    /**
     * Nom du fichier MIDI (pour MIDI)
     * Stocke le nom complet du fichier .mid car l'app ne fournit pas de fichiers
     */
    val midiFileName: String? = null,

    /**
     * Score de practice (pour MIDI practice mode)
     * Note: Les sessions de practice ont aussi leur propre table PracticeSessionResult
     */
    val practiceScore: Float? = null,

    // ===== Métadonnées pour RADIO =====

    /**
     * Nom de la station radio (pour radio)
     */
    val radioStationName: String? = null,

    /**
     * Device d'origine de la session (null = créée localement, non-null = importée depuis un autre appareil via sync).
     * Permet d'éviter de réexporter des sessions importées sous un mauvais deviceId.
     */
    val sourceDeviceId: String? = null
)
