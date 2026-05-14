package com.Atom2Universe.app.notes

import android.content.Context
import android.content.SharedPreferences

class NotesPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSize: Int
        get() = prefs.getInt(KEY_FONT_SIZE, 16)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value.coerceIn(14, 48)).apply()

    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, FONT_DEFAULT) ?: FONT_DEFAULT
        set(value) = prefs.edit().putString(KEY_FONT_FAMILY, value).apply()

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, value).apply()

    var ttsVoiceName: String?
        get() = prefs.getString(KEY_TTS_VOICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_TTS_VOICE_NAME, value).apply()

    companion object {
        private const val PREFS_NAME = "notes_preferences"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_VOICE_NAME = "tts_voice_name"

        const val FONT_DEFAULT = "default"
        val AVAILABLE_FONTS = listOf(
            "default",
            "serif",
            "monospace",
            "sans-serif-light",
            "sans-serif-medium",
            "sans-serif-condensed",
            "cursive",
            "casual",
            "serif-monospace",
            "sans-serif-smallcaps"
        )
    }
}
