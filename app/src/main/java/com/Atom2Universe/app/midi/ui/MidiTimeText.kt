package com.Atom2Universe.app.midi.ui

import android.content.Context
import com.Atom2Universe.app.R

/**
 * Durée en "m:ss" ou "h:mm:ss" (formats dans les ressources).
 */
internal fun formatMidiTime(context: Context, ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        context.getString(R.string.midi_ui_time_hms, hours, minutes, seconds)
    } else {
        context.getString(R.string.midi_ui_time_ms, minutes, seconds)
    }
}
