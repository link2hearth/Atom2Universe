package com.Atom2Universe.app.pixelart.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant un projet de toile infinie (Canvas Editor)
 * Les objets du canvas sont stockés en JSON sérialisé
 */
@Entity(tableName = "canvas_projects")
data class CanvasProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nom du projet */
    val name: String = "Canvas",

    /** Position X du viewport */
    val viewportX: Float = 0f,

    /** Position Y du viewport */
    val viewportY: Float = 0f,

    /** Niveau de zoom du viewport */
    val viewportZoom: Float = 1f,

    /** Objets du canvas sérialisés en JSON (tableau d'objets) */
    val objectsJson: String = "[]",

    /** Date de création */
    val dateCreated: Long = System.currentTimeMillis(),

    /** Date de dernière modification */
    val dateModified: Long = System.currentTimeMillis(),

    /** Version du format de sérialisation */
    val version: Int = 1
)
