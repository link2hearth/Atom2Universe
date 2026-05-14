package com.Atom2Universe.app.music.lyrics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.appbar.MaterialToolbar

/**
 * Activité avec WebView pour rechercher des paroles en ligne.
 * Ouvre automatiquement une recherche Google avec le titre et l'artiste.
 */
class LyricsWebSearchActivity : ThemedActivity() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"

        fun createIntent(context: Context, title: String, artist: String): Intent {
            return Intent(context, LyricsWebSearchActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_lyrics_web_search)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { navigateBackToHub() }

        val webView = findViewById<WebView>(R.id.webview)

        // Configuration du WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        // Garder la navigation dans l'app
        webView.webViewClient = WebViewClient()

        // Récupérer titre et artiste
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""

        // Construire l'URL de recherche Google avec métadonnées nettoyées
        // Format : "artiste titre lyrics" (avec filtres pour feat., remastered, etc.)
        val searchQuery = LyricsUtils.buildLyricsSearchQuery(title, artist)
        val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
        val searchUrl = "https://www.google.com/search?q=$encodedQuery"

        // Charger la recherche
        webView.loadUrl(searchUrl)

        // Gérer le bouton retour pour naviguer dans l'historique du WebView
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }
}
