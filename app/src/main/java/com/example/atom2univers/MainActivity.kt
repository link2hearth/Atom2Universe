package com.example.atom2univers

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: GameWebView
    private var webViewSaveScript: String? = null
    private var cssRecoveryAttempted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())


        webView = findViewById(R.id.webview)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(Uri.parse(url))
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!url.startsWith(ASSET_URL_PREFIX)) {
                    return
                }
                ensureStylesheetsLoaded(view, url)
            }

            private fun ensureStylesheetsLoaded(view: WebView, url: String) {
                view.evaluateJavascript(CSS_PRESENCE_CHECK) { rawResult ->
                    val trimmed = rawResult?.trim()?.trim('"')
                    val stylesheetCount = trimmed?.toIntOrNull()
                    if (stylesheetCount == null) {
                        Log.w(TAG, "Unable to parse stylesheet count from WebView: $rawResult")
                        return@evaluateJavascript
                    }

                    if (stylesheetCount < 0) {
                        Log.w(TAG, "Unable to inspect stylesheets due to WebView restrictions")
                        if (cssRecoveryAttempted) {
                            cssRecoveryAttempted = false
                        }
                        return@evaluateJavascript
                    }

                    if (stylesheetCount > 0) {
                        if (cssRecoveryAttempted) {
                            cssRecoveryAttempted = false
                        }
                        return@evaluateJavascript
                    }

                    if (!cssRecoveryAttempted) {
                        cssRecoveryAttempted = true
                        Log.w(TAG, "Stylesheets missing, clearing cache and reloading $url")
                        view.post {
                            view.clearCache(true)
                            view.loadUrl(url)
                        }
                    } else {
                        Log.e(TAG, "Stylesheets still unavailable after recovery attempt")
                    }
                }
            }
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setOnTouchListener { view, motionEvent ->
            if (motionEvent?.action == MotionEvent.ACTION_DOWN || motionEvent?.action == MotionEvent.ACTION_UP) {
                if (!view.hasFocus()) {
                    view.requestFocus()
                }
            }
            false
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
        }

        webView.addJavascriptInterface(
            AndroidSaveBridge(applicationContext),
            "AndroidSaveBridge"
        )

        webViewSaveScript = """
            (function() {
              try {
                var saveFn = window.atom2universSaveGame;
                if (typeof saveFn === 'function') {
                  saveFn();
                  return 'saved';
                }
                return 'no-save';
              } catch (error) {
                return 'error';
              }
            })();
        """.trimIndent()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    override fun onPause() {
        requestWebViewSave()
        if (::webView.isInitialized) {
            webView.pauseTimers()
            webView.onPause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onStop() {
        requestWebViewSave()
        super.onStop()
    }

    private fun requestWebViewSave() {
        if (!::webView.isInitialized) {
            return
        }
        val script = webViewSaveScript ?: return
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private companion object {
        private const val TAG = "Atom2Univers"
        private const val ASSET_URL_PREFIX = "https://appassets.androidplatform.net/assets/"
        private const val CSS_PRESENCE_CHECK = """
            (function() {
              try {
                return document.styleSheets.length;
              } catch (error) {
                return -1;
              }
            })();
        """
    }
}

