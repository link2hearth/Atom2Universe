package com.Atom2Universe.app.pixelart.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant un projet de pixel art
 * Stocke les métadonnées du projet (dimensions, couleurs, etc.)
 */
@Entity(tableName = "pixelart_projects")
data class PixelArtProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nom du projet */
    val name: String = "Projet",

    /** Largeur du canvas en pixels */
    val canvasWidth: Int,

    /** Hauteur du canvas en pixels */
    val canvasHeight: Int,

    /** Images par seconde pour l'animation */
    val fps: Int = 10,

    /** Couleur primaire (ARGB) */
    val primaryColor: Int,

    /** Couleur secondaire (ARGB) */
    val secondaryColor: Int,

    /** Index de la frame actuellement sélectionnée */
    val currentFrameIndex: Int = 0,

    /** Date de création */
    val dateCreated: Long = System.currentTimeMillis(),

    /** Date de dernière modification */
    val dateModified: Long = System.currentTimeMillis(),

    /** Version du format de sérialisation */
    val version: Int = 1
)
