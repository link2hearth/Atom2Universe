package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-artist equalizer preset override.
 * When an artist has an override and neither the track nor album have their own overrides,
 * this setting is used instead of global settings.
 *
 * Uses albumArtist if available, otherwise falls back to track artist.
 */
@Entity(
    tableName = "eq_artist_overrides",
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
data class EqArtistOverride(
    /**
     * Artist name (preferably albumArtist for consistency).
     * Normalized to handle case variations.
     */
    @PrimaryKey
    val artistKey: String,

    /** Reference to the EQ preset to apply */
    val presetId: Long,

    /** Timestamp when this override was set */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Create artist key, preferring albumArtist over artist */
        fun createKey(artist: String, albumArtist: String?): String {
            return (albumArtist ?: artist).trim().lowercase()
        }
    }
}
