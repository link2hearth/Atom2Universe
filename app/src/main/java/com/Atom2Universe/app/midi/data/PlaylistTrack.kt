package com.Atom2Universe.app.midi.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entity de jonction (junction table) pour la relation many-to-many
 * entre Playlists et MidiTracks
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MidiTrack::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class PlaylistTrack(
    /** ID de la playlist */
    val playlistId: Long,

    /** ID du track MIDI */
    val trackId: Long,

    /** Position du track dans la playlist (pour l'ordre) */
    val position: Int
)
