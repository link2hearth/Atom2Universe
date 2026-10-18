package com.Atom2Universe.app.midi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity pour stocker les paramètres de l'application (key-value store)
 * Utilisé pour: SoundFont path, dossier MIDI URI, préférences utilisateur
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val key: String,

    /** Valeur du paramètre (stockée comme String) */
    val value: String
)
