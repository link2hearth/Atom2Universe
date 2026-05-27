package com.Atom2Universe.app

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/**
 * Helper class to manage app language/locale settings.
 * Persists user language choice and applies it to Activities.
 */
object LocaleHelper {

    private const val PREFS_NAME = "atom2univers_prefs"
    private const val KEY_LANGUAGE = "app_language"

    // Supported languages (alphabetically sorted by display name)
    val SUPPORTED_LANGUAGES = listOf("in", "de", "el", "en", "es", "fr", "it", "nl", "pl", "pt", "tr", "ru", "uk")
    const val DEFAULT_LANGUAGE = "en"

    /**
     * Get the stored language preference, or default if not set.
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Save the language preference.
     * Uses apply() to persist the preference asynchronously.
     */
    fun setLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LANGUAGE, language) }
    }

    /**
     * Apply the stored language to a context and return the localized context.
     * Call this in attachBaseContext() of each Activity.
     */
    fun applyLocale(context: Context): Context {
        val language = getLanguage(context)
        return updateResources(context, language)
    }

    /**
     * Apply a specific language to a context.
     */
    fun applyLocale(context: Context, language: String): Context {
        setLanguage(context, language)
        return updateResources(context, language)
    }

    /**
     * Update configuration with the new locale.
     */
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Get the display name for a language code.
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "fr" -> "Français"
            "it" -> "Italiano"
            "pt" -> "Português"
            "es" -> "Español"
            "de" -> "Deutsch"
            "el" -> "Ελληνικά"
            "pl" -> "Polski"
            "ru" -> "Русский"
            "uk" -> "Українська"
            "nl" -> "Nederlands"
            "tr" -> "Türkçe"
            "in" -> "Bahasa Indonesia"
            else -> languageCode
        }
    }

    /**
     * Get the short label for a language code.
     */
    fun getLanguageShortLabel(languageCode: String): String {
        return when (languageCode) {
            "en" -> "EN"
            "fr" -> "FR"
            "it" -> "IT"
            "pt" -> "PT"
            "es" -> "ES"
            "de" -> "DE"
            "el" -> "EL"
            "pl" -> "PL"
            "ru" -> "RU"
            "uk" -> "UK"
            "nl" -> "NL"
            "tr" -> "TR"
            "in" -> "ID"
            else -> languageCode.uppercase(Locale.getDefault())
        }
    }

    /**
     * Get the next language in the cycle (for toggle button).
     */
    @Suppress("unused")
    fun getNextLanguage(currentLanguage: String): String {
        val currentIndex = SUPPORTED_LANGUAGES.indexOf(currentLanguage)
        val nextIndex = (currentIndex + 1) % SUPPORTED_LANGUAGES.size
        return SUPPORTED_LANGUAGES[nextIndex]
    }
}
