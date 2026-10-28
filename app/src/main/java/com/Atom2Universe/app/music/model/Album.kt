package com.Atom2Universe.app.music.model

import android.net.Uri
import com.Atom2Universe.app.A2UApplication
import com.Atom2Universe.app.R

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

    // Formats en ressource (EN + FR) ; le contexte de l'appli suit la langue choisie
    val totalDurationFormatted: String
        get() {
            val totalSeconds = totalDuration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val context = A2UApplication.appContext
            return if (hours > 0) {
                context.getString(R.string.music_duration_hours_minutes, hours, minutes)
            } else {
                context.getString(R.string.music_duration_minutes, minutes)
            }
        }
}
