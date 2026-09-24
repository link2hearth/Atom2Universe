package com.Atom2Universe.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

open class ThemedActivity : AppCompatActivity() {

    /** Surcharge locale pour les familles d'écrans, après le thème choisi par l'utilisateur. */
    protected open val moduleThemeOverlay: Int = 0

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
        super.onCreate(savedInstanceState)
        // AppCompat may have reapplied the configuration supplied by the ROM.
        LocaleHelper.ensureLocale(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.ensureLocale(this)
    }
}
