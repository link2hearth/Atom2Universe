package com.Atom2Universe.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
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
    val SUPPORTED_LANGUAGES = listOf("in", "de", "el", "en", "es", "fr", "it", "nl", "pl", "pt", "ro", "tr", "ru", "uk")
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
    fun setLanguage(context: Context, language: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LANGUAGE, language) }
        return syncApplicationLocale(context)
    }

    /**
     * Inform Android 13+ of the saved choice, including choices saved by older app versions.
     * A localized Activity context alone does not set the system's per-app language.
     * Returns true when Android will dispatch a locale configuration change itself.
     */
    fun syncApplicationLocale(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        val locales = LocaleList.forLanguageTags(getLanguage(context))
        if (manager.applicationLocales == locales) return false
        manager.applicationLocales = locales
        return true
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
     * Some vendor ROMs replace the locale after attachBaseContext, even when the
     * system per-app preference is correct. Repair the actual resources only when
     * that happens, preserving density, orientation, night mode and font scale.
     */
    @Suppress("DEPRECATION")
    fun ensureLocale(context: Context) {
        val locale = Locale.forLanguageTag(getLanguage(context))
        val resources = context.resources
        val before = resources.configuration.locales
        if (!before.isEmpty && before[0] == locale) return

        val configuration = Configuration(resources.configuration)
        setConfigurationLocale(configuration, locale)
        Locale.setDefault(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        Log.i("AppLocale", "${context.javaClass.simpleName}: requested=$locale, " +
            "before=${before.toLanguageTags()}, after=${resources.configuration.locales.toLanguageTags()}")
    }

    /**
     * Update configuration with the new locale.
     */
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        setConfigurationLocale(config, locale)

        return context.createConfigurationContext(config).also { ensureLocale(it) }
    }

    /**
     * Lenovo's PRC Android 15 framework rewrites setLocales() to zh_CN through
     * ZuiAntiCrossSell. Its public legacy locale field and getLocales() still
     * synchronize normally (Configuration.fixUpLocaleList). Use that path only
     * if the standard setter did not retain the requested locale.
     */
    @Suppress("DEPRECATION")
    private fun setConfigurationLocale(configuration: Configuration, locale: Locale) {
        configuration.setLocale(locale)
        if (configuration.locales.isEmpty || configuration.locales[0] != locale) {
            configuration.locale = locale
            // Reading the list synchronizes it with the public legacy field.
            val actual = configuration.locales
            Log.i("AppLocale", "Configuration fallback: requested=$locale, actual=${actual.toLanguageTags()}")
        }
        configuration.setLayoutDirection(locale)
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
            "ro" -> "Română"
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
            "ro" -> "RO"
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
