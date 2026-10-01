package com.Atom2Universe.app.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.Atom2Universe.app.R

class NotesPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 17)
        set(value) = prefs.edit { putInt(KEY_FONT_SIZE, value.coerceIn(MIN_FONT, MAX_FONT)) }

    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, FONT_DEFAULT) ?: FONT_DEFAULT
        set(value) = prefs.edit { putString(KEY_FONT_FAMILY, value) }

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_TTS_SPEECH_RATE, value) }

    var sort: Sort
        get() = Sort.entries.getOrNull(prefs.getInt(KEY_SORT, 0)) ?: Sort.MODIFIED
        set(value) = prefs.edit { putInt(KEY_SORT, value.ordinal) }

    /** L'ordre de la bibliothèque (les épinglées restent toujours en tête). */
    enum class Sort(@StringRes val label: Int) {
        MODIFIED(R.string.notes_sort_modified),
        CREATED(R.string.notes_sort_created),
        TITLE(R.string.notes_sort_title),
    }

    companion object {
        private const val PREFS_NAME = "notes_preferences"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_SORT = "library_sort"

        const val MIN_FONT = 12
        const val MAX_FONT = 32
        const val FONT_DEFAULT = "default"

        /** Les polices proposées, et leur nom à l'écran. */
        val FONTS = listOf(
            FONT_DEFAULT to R.string.notes_font_default,
            "serif" to R.string.notes_font_serif,
            "monospace" to R.string.notes_font_mono,
            "sans-serif-light" to R.string.notes_font_light,
            "sans-serif-condensed" to R.string.notes_font_condensed,
            "casual" to R.string.notes_font_casual,
        )
    }
}
