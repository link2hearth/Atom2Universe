package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room marquant un album déjà traité par la proposition d'écriture
 * automatique des numéros de piste (pour ne plus le reproposer).
 *
 * Remplace l'ancien StringSet `albums_track_number_checked` des SharedPreferences,
 * qui était réécrit en entier à chaque album marqué et grossissait avec la
 * taille de la bibliothèque.
 */
@Entity(tableName = "album_track_number_checks")
data class AlbumTrackCheckEntity(
    @PrimaryKey
    val albumKey: String   // "$artistName|$albumName"
)
