package com.Atom2Universe.app

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.edit

enum class AppTheme(
    val id: String,
    @StyleRes val styleRes: Int,
    @StringRes val labelRes: Int
) {
    DEFAULT("classic", R.style.Theme_A2U_Classic, R.string.theme_classic),
    GREEN("green", R.style.Theme_A2U_Green, R.string.theme_green),
    TURQUOISE("turquoise", R.style.Theme_A2U_Turquoise, R.string.theme_turquoise),
    ORANGE("orange", R.style.Theme_A2U_Orange, R.string.theme_orange),
    PURPLE("purple", R.style.Theme_A2U_Purple, R.string.theme_purple),
    PINK("pink", R.style.Theme_A2U_Pink, R.string.theme_pink),
    BLUE("blue", R.style.Theme_A2U_Blue, R.string.theme_blue),
    RED("red", R.style.Theme_A2U_Red, R.string.theme_red),
    INDIGO("indigo", R.style.Theme_A2U_Indigo, R.string.theme_indigo)
}

object AppThemeManager {
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_HUE = "spectrum_hue"
    private const val KEY_LEVEL = "spectrum_level"

    /** Valeur de [KEY_THEME] quand la couleur vient de la barre de teintes plutôt que d'un thème fixe. */
    private const val SPECTRUM_ID = "spectrum"

    /** Les intensités de la barre de teintes, dans l'ordre de [SpectrumThemes]. */
    val spectrumLevelLabels = intArrayOf(
        R.string.theme_intensity_vivid, R.string.theme_intensity_soft, R.string.theme_intensity_pastel
    )

    /** Une couleur libre : une teinte de la barre et une intensité. */
    data class Spectrum(val hue: Int, val level: Int)

    fun getAvailableThemes(): List<AppTheme> = AppTheme.entries

    /** Le thème fixe choisi, ou null si la couleur vient de la barre de teintes. */
    fun getSelectedPreset(context: Context): AppTheme? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_THEME, null) == SPECTRUM_ID) return null
        return getSelectedTheme(context)
    }

    /** La couleur libre choisie, ou null si c'est un thème fixe. */
    fun getSelectedSpectrum(context: Context): Spectrum? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_THEME, null) != SPECTRUM_ID) return null
        return Spectrum(prefs.getInt(KEY_HUE, 0), prefs.getInt(KEY_LEVEL, 0))
    }

    fun setSelectedSpectrum(context: Context, spectrum: Spectrum) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_THEME, SPECTRUM_ID)
            putInt(KEY_HUE, spectrum.hue)
            putInt(KEY_LEVEL, spectrum.level)
        }
    }

    /** Le style à poser sur un écran : celui du thème fixe, ou celui de la teinte choisie. */
    @StyleRes
    fun selectedStyleRes(context: Context): Int =
        getSelectedSpectrum(context)?.let { SpectrumThemes.style(it.hue, it.level) }
            ?: getSelectedTheme(context).styleRes

    /** Le nom du thème pour l'accessibilité : le thème fixe, ou l'intensité de la couleur libre. */
    fun selectedLabel(context: Context): String =
        getSelectedSpectrum(context)?.let {
            context.getString(R.string.theme_spectrum_label,
                context.getString(spectrumLevelLabels[it.level.coerceIn(0, spectrumLevelLabels.size - 1)]))
        } ?: context.getString(getSelectedTheme(context).labelRes)

    /**
     * Le thème fixe choisi. Avec une couleur libre, c'est le thème par défaut : il ne sert plus
     * alors que de repli, [selectedStyleRes] donne le vrai style.
     */
    fun getSelectedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(KEY_THEME, AppTheme.DEFAULT.id)
        return AppTheme.entries.firstOrNull { it.id == selectedId } ?: AppTheme.DEFAULT
    }

    fun setSelectedTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Use commit() for theme preference to ensure it's saved before activity recreation
        prefs.edit(commit = true) {
            putString(KEY_THEME, theme.id)
        }
    }

    fun applyTheme(activity: Activity) {
        activity.setTheme(selectedStyleRes(activity))
    }

    /**
     * La couleur choisie, puis le style commun de l'appli (surfaces, arrondis, dialogues,
     * boutons) : ce que fait [ThemedActivity], pour les écrans qui n'en héritent pas.
     * À appeler avant `super.onCreate`.
     */
    fun applyAppStyle(activity: Activity) {
        applyTheme(activity)
        activity.theme.applyStyle(R.style.ThemeOverlay_A2U_App, true)
    }
}
