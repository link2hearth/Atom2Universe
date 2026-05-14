package com.Atom2Universe.app.midi.practice.themes

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Gestionnaire des thèmes de practice mode.
 */
object PracticeThemeManager {

    private const val PREFS_NAME = "practice_themes"
    private const val KEY_SELECTED_THEME = "selected_theme"
    private const val KEY_CUSTOM_BASE_THEME = "custom_base_theme"
    private const val KEY_CUSTOM_OPACITY = "custom_opacity"

    // Thèmes disponibles
    private val themes = mutableMapOf<String, PracticeTheme>()
    private var currentTheme: PracticeTheme? = null
    private var prefs: SharedPreferences? = null
    // Internal pour que les thèmes puissent accéder au contexte (ex: CyberpunkTheme pour charger l'image)
    internal var appContext: Context? = null
        private set

    // Thème personnalisé (géré séparément car nécessite le context)
    private var customBackgroundTheme: CustomBackgroundTheme? = null

    // Listeners pour les changements de thème
    private val themeChangeListeners = mutableListOf<(PracticeTheme) -> Unit>()

    /**
     * Initialise le gestionnaire avec le contexte de l'application.
     * Doit être appelé au démarrage de l'app.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        registerBuiltInThemes()
        initCustomTheme(context)
        loadSelectedTheme()
    }

    /**
     * Initialise le thème personnalisé
     */
    private fun initCustomTheme(context: Context) {
        // Récupérer les préférences du thème personnalisé
        val baseThemeId = prefs?.getString(KEY_CUSTOM_BASE_THEME, "classic") ?: "classic"
        val opacity = prefs?.getFloat(KEY_CUSTOM_OPACITY, 0.4f) ?: 0.4f
        val baseTheme = themes[baseThemeId] ?: ClassicTheme()

        customBackgroundTheme = CustomBackgroundTheme(
            context = context,
            baseTheme = baseTheme,
            imageOpacity = opacity
        )

        // Enregistrer comme thème disponible
        themes["custom_background"] = customBackgroundTheme!!
    }

    private fun registerBuiltInThemes() {
        registerTheme(ClassicTheme())
        registerTheme(RainbowTheme())
        registerTheme(NeonTheme())
        registerTheme(OceanTheme())
        registerTheme(FireTheme())
        registerTheme(OceanDeepTheme())
        registerTheme(GalaxyTheme())
        registerTheme(CyberpunkTheme())
        registerTheme(MinimalTheme())
        registerTheme(MatrixTheme())
    }

    /**
     * Enregistre un thème
     */
    fun registerTheme(theme: PracticeTheme) {
        themes[theme.id] = theme
    }

    /**
     * Retourne tous les thèmes disponibles
     */
    fun getAllThemes(): List<PracticeTheme> = themes.values.toList()

    /**
     * Retourne le thème actuellement sélectionné
     */
    fun getCurrentTheme(): PracticeTheme {
        return currentTheme ?: themes["classic"] ?: ClassicTheme()
    }

    /**
     * Sélectionne un thème par son ID
     * @return true si le thème a été sélectionné, false si non disponible
     */
    fun selectTheme(themeId: String): Boolean {
        val theme = themes[themeId] ?: return false

        currentTheme?.onDeactivate()
        currentTheme = theme
        theme.onActivate()

        // Sauvegarder la sélection
        prefs?.edit()?.putString(KEY_SELECTED_THEME, themeId)?.apply()

        // Notifier les listeners
        themeChangeListeners.forEach { it(theme) }

        return true
    }

    /**
     * Charge le thème sauvegardé
     */
    private fun loadSelectedTheme() {
        val savedThemeId = prefs?.getString(KEY_SELECTED_THEME, "classic") ?: "classic"
        val theme = themes[savedThemeId]

        currentTheme = theme ?: themes["classic"]

        currentTheme?.onActivate()
    }

    /**
     * Ajoute un listener pour les changements de thème
     */
    fun addThemeChangeListener(listener: (PracticeTheme) -> Unit) {
        themeChangeListeners.add(listener)
    }

    /**
     * Retire un listener
     */
    fun removeThemeChangeListener(listener: (PracticeTheme) -> Unit) {
        themeChangeListeners.remove(listener)
    }

    /**
     * Retourne un thème par son ID
     */
    fun getTheme(id: String): PracticeTheme? = themes[id]

    /**
     * Vérifie si un thème est disponible
     */
    fun isThemeAvailable(themeId: String): Boolean = themes.containsKey(themeId)

    // ========== GESTION DU THÈME PERSONNALISÉ ==========

    /**
     * Retourne le thème personnalisé
     */
    fun getCustomTheme(): CustomBackgroundTheme? = customBackgroundTheme

    /**
     * Définit le thème de base pour le thème personnalisé
     */
    fun setCustomBaseTheme(baseThemeId: String) {
        val baseTheme = themes[baseThemeId]
        if (baseTheme != null && baseTheme !is CustomBackgroundTheme) {
            customBackgroundTheme?.setBaseTheme(baseTheme)
            prefs?.edit()?.putString(KEY_CUSTOM_BASE_THEME, baseThemeId)?.apply()

            // Notifier si le thème personnalisé est actif
            if (currentTheme?.id == "custom_background") {
                themeChangeListeners.forEach { it(customBackgroundTheme!!) }
            }
        }
    }

    /**
     * Définit l'opacité de l'image du thème personnalisé
     */
    fun setCustomImageOpacity(opacity: Float) {
        val clampedOpacity = opacity.coerceIn(0.1f, 0.8f)
        customBackgroundTheme?.setImageOpacity(clampedOpacity)
        prefs?.edit()?.putFloat(KEY_CUSTOM_OPACITY, clampedOpacity)?.apply()

        // Notifier si le thème personnalisé est actif
        if (currentTheme?.id == "custom_background") {
            themeChangeListeners.forEach { it(customBackgroundTheme!!) }
        }
    }

    /**
     * Retourne l'opacité actuelle de l'image personnalisée
     */
    fun getCustomImageOpacity(): Float {
        return prefs?.getFloat(KEY_CUSTOM_OPACITY, 0.4f) ?: 0.4f
    }

    /**
     * Retourne l'ID du thème de base actuellement utilisé pour le thème personnalisé
     */
    fun getCustomBaseThemeId(): String {
        return prefs?.getString(KEY_CUSTOM_BASE_THEME, "classic") ?: "classic"
    }

    /**
     * Sauvegarde une image comme fond personnalisé
     * @return true si succès
     */
    fun saveCustomBackgroundImage(uri: Uri): Boolean {
        val context = appContext ?: return false
        val success = CustomBackgroundTheme.saveCustomBackground(context, uri)
        if (success) {
            customBackgroundTheme?.reloadImage()
        }
        return success
    }

    /**
     * Vérifie si un fond personnalisé existe
     */
    fun hasCustomBackground(): Boolean {
        val context = appContext ?: return false
        return CustomBackgroundTheme.hasCustomBackground(context)
    }

    /**
     * Supprime le fond personnalisé
     */
    fun deleteCustomBackground(): Boolean {
        val context = appContext ?: return false
        val success = CustomBackgroundTheme.deleteCustomBackground(context)
        if (success) {
            customBackgroundTheme?.reloadImage()
        }
        return success
    }

    /**
     * Retourne tous les thèmes sauf le thème personnalisé
     * (utilisé pour sélectionner le thème de base)
     */
    fun getThemesForBaseSelection(): List<PracticeTheme> {
        return themes.values.filter { it.id != "custom_background" }
    }
}
