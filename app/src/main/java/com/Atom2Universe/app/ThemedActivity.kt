package com.Atom2Universe.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.util.followSystemBarsPreference
import com.Atom2Universe.app.util.followWindowFocus

open class ThemedActivity : AppCompatActivity() {

    /**
     * Posé après le thème choisi par l'utilisateur : le style commun de l'appli par défaut,
     * qu'une famille d'écrans peut remplacer par le sien.
     */
    protected open val moduleThemeOverlay: Int = R.style.ThemeOverlay_A2U_App

    /** Some immersive scenes (space) require dark controls regardless of the app palette. */
    protected open val themeBrightness: AppBrightness? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.ensureLocale(this)
        // Active l'affichage de bord à bord (edge-to-edge) pour Android 15+
        // Assure la rétrocompatibilité sur les versions antérieures
        enableEdgeToEdge()

        AppThemeManager.applyTheme(this)
        if (moduleThemeOverlay != 0) theme.applyStyle(moduleThemeOverlay, true)
        AppThemeManager.applyVisualStyle(this,
            brightness = themeBrightness ?: AppThemeManager.getSelectedBrightness(this))
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = AppearanceStyle.isLight(this@ThemedActivity)
            isAppearanceLightNavigationBars = AppearanceStyle.isLight(this@ThemedActivity)
        }
        AppThemeManager.bindEffects(this)
        // AppCompat may have reapplied the configuration supplied by the ROM.
        LocaleHelper.ensureLocale(this)
    }

    override fun onStart() {
        super.onStart()
        window.followSystemBarsPreference(this)
        followWindowFocus(window.decorView) { window.followSystemBarsPreference(this) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.ensureLocale(this)
    }
}
