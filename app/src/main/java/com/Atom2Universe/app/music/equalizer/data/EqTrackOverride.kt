package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-track equalizer preset override.
 * When a track has an override, it takes priority over album/artist/global settings.
 */
@Entity(
    tableName = "eq_track_overrides",
    foreignKeys = [
        ForeignKey(
            entity = EqPreset::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("presetId")]
)
data class EqTrackOverride(
    /** The track ID from MediaStore (MusicTrack.id) */
    @PrimaryKey
    val trackId: Long,

    /** Reference to the EQ preset to apply */
    val presetId: Long,

    /** Timestamp when this override was set */
    val createdAt: Long = System.currentTimeMillis()
)
