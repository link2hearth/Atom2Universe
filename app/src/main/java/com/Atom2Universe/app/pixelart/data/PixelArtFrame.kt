package com.Atom2Universe.app.pixelart.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité représentant une frame d'animation d'un projet pixel art
 * Les données de pixels sont stockées en Base64 pour efficacité
 */
@Entity(
    tableName = "pixelart_frames",
    foreignKeys = [
        ForeignKey(
            entity = PixelArtProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class PixelArtFrame(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID du projet parent */
    val projectId: Long,

    /** Index de la frame dans l'animation (0 = première frame) */
    val frameIndex: Int,

    /** Données des pixels encodées en Base64 */
    val pixelDataBase64: String,

    /** Durée de la frame en millisecondes */
    val duration: Int = 100,

    /** Date de dernière modification */
    val dateModified: Long = System.currentTimeMillis()
)
