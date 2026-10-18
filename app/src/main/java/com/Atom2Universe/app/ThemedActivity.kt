package com.Atom2Universe.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.util.followSystemBarsPreference
import com.Atom2Universe.app.util.followWindowFocus

open class ThemedActivity : AppCompatActivity() {
    private data class ThemeSelection(
        val color: Int,
        val appearance: AppAppearance,
        val brightness: AppBrightness,
        val tinted: Boolean
    )

    private var appliedSelection: ThemeSelection? = null

    private fun selectedAppearance() = ThemeSelection(
        AppThemeManager.selectedStyleRes(this), AppThemeManager.getSelectedAppearance(this),
        AppThemeManager.getSelectedBrightness(this), AppThemeManager.hasTintedSurfaces(this)
    )

    internal fun rememberThemeSelection() {
        appliedSelection = selectedAppearance()
    }

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
        AppThemeManager.applyTheme(this)
        if (moduleThemeOverlay != 0) theme.applyStyle(moduleThemeOverlay, true)
        AppThemeManager.applyVisualStyle(this,
            brightness = themeBrightness ?: AppThemeManager.getSelectedBrightness(this))
        rememberThemeSelection()
        // Les couleurs des barres suivent le choix de l'app, même si Android utilise l'autre
        // mode clair/sombre. Sur Android 8/9, le fond de navigation est encore une vraie couleur.
        val barSurface = AppearanceStyle.color(this, R.attr.a2uSurfaceColor)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) {
                !AppearanceStyle.isLight(this)
            },
            navigationBarStyle = SystemBarStyle.auto(barSurface, barSurface) { !AppearanceStyle.isLight(this) }
        )
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
        // Un écran resté derrière les réglages garde sinon ses vues et couleurs mises en cache.
        if (appliedSelection != selectedAppearance()) {
            recreate()
            return
        }
        window.followSystemBarsPreference(this)
        followWindowFocus(window.decorView) { window.followSystemBarsPreference(this) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.ensureLocale(this)
    }
}
