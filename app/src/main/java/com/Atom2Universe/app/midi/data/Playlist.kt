package com.Atom2Universe.app.midi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity représentant une playlist créée par l'utilisateur
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nom de la playlist */
    val name: String,

    /** Date de création (timestamp) */
    val dateCreated: Long,

    /** Date de dernière modification (timestamp) */
    val dateModified: Long
)
