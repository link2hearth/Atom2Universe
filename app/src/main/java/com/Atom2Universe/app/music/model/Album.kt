package com.Atom2Universe.app.music.model

import android.net.Uri
import java.util.Locale

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val albumArtUri: Uri? = null,
    val tracks: MutableList<MusicTrack> = mutableListOf(),
    var year: Int? = null,
    private val trackCountOverride: Int? = null
) {
    val trackCount: Int
        get() = trackCountOverride ?: tracks.size

    val totalDuration: Long
        get() = tracks.sumOf { it.duration }

    val totalDurationFormatted: String
        get() {
            val totalSeconds = totalDuration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d h %02d min", hours, minutes)
            } else {
                String.format(Locale.ROOT, "%d min", minutes)
            }
        }
}
