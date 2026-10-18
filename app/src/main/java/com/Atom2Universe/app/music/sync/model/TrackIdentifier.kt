package com.Atom2Universe.app.music.sync.model

import java.io.File

/**
 * Identifies a track across devices using metadata.
 *
 * Two tracks are considered the same if at least 3 fields match among:
 * - artist
 * - title
 * - album
 * - albumArtist
 * - filename (without path)
 *
 * This allows matching even when file paths differ between devices.
 */
data class TrackIdentifier(
    val artist: String,
    val title: String,
    val album: String,
    val albumArtist: String? = null,
    val filename: String? = null
) {
    /**
     * Generates the metadata key used in Room database.
     * Format: "artist|title|album" (lowercase, trimmed)
     */
    fun toMetadataKey(): String {
        val a = artist.trim().lowercase()
        val t = title.trim().lowercase()
        val al = album.trim().lowercase()
        return "$a|$t|$al"
    }

    /**
     * Checks if this identifier matches another with fuzzy logic.
     * Returns true if at least 3 fields match.
     */
    fun matches(other: TrackIdentifier): Boolean {
        var matchCount = 0

        if (normalize(artist) == normalize(other.artist)) matchCount++
        if (normalize(title) == normalize(other.title)) matchCount++
        if (normalize(album) == normalize(other.album)) matchCount++

        if (albumArtist != null && other.albumArtist != null &&
            normalize(albumArtist) == normalize(other.albumArtist)) {
            matchCount++
        }

        if (filename != null && other.filename != null &&
            normalize(filename) == normalize(other.filename)) {
            matchCount++
        }

        return matchCount >= 3
    }

    private fun normalize(s: String): String {
        return s.trim().lowercase()
    }

    companion object {
        /**
         * Creates a TrackIdentifier from a file path and metadata.
         */
        fun fromTrack(
            artist: String,
            title: String,
            album: String,
            albumArtist: String? = null,
            filePath: String? = null
        ): TrackIdentifier {
            val filename = filePath?.let { File(it).name }
            return TrackIdentifier(
                artist = artist,
                title = title,
                album = album,
                albumArtist = albumArtist,
                filename = filename
            )
        }

        /**
         * Creates a TrackIdentifier from a metadata key.
         */
        fun fromMetadataKey(key: String): TrackIdentifier? {
            val parts = key.split("|")
            if (parts.size != 3) return null
            return TrackIdentifier(
                artist = parts[0],
                title = parts[1],
                album = parts[2]
            )
        }
    }
}
