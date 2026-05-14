package com.Atom2Universe.app

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

open class ThemedActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Active l'affichage de bord à bord (edge-to-edge) pour Android 15+
        // Assure la rétrocompatibilité sur les versions antérieures
        enableEdgeToEdge()

        AppThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
    }
}
