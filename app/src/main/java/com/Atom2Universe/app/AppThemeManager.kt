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

    fun getAvailableThemes(): List<AppTheme> = AppTheme.entries

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
        activity.setTheme(getSelectedTheme(activity).styleRes)
    }
}
