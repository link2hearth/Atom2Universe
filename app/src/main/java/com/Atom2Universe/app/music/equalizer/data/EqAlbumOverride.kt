package com.Atom2Universe.app.music.equalizer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-album equalizer preset override.
 * When an album has an override and the track doesn't have its own override,
 * this setting is used instead of artist/global settings.
 *
 * The albumKey is formatted as "album|albumArtist" to handle albums with the same name
 * by different artists.
 */
@Entity(
    tableName = "eq_album_overrides",
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
data class EqAlbumOverride(
    /**
     * Composite key: "albumName|albumArtist"
     * This ensures unique identification even for albums with identical names.
     */
    @PrimaryKey
    val albumKey: String,

    /** Reference to the EQ preset to apply */
    val presetId: Long,

    /** Timestamp when this override was set */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Create album key from album name and album artist */
        fun createKey(album: String, albumArtist: String?): String {
            return "$album|${albumArtist ?: ""}"
        }
    }
}
