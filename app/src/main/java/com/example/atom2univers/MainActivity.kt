package com.example.atom2univers

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: GameWebView
    private var webViewSaveScript: String? = null
    private var cssRecoveryAttempted = false
    private var pendingBackupUri: Uri? = null

    private val openBackupFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                notifyBackupLoadFailed("cancelled")
                return@registerForActivityResult
            }
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    deliverBackupPayloadToJs(base64)
                } ?: notifyBackupLoadFailed("error")
            } catch (error: Exception) {
                Log.e(TAG, "Unable to read manual backup file", error)
                notifyBackupLoadFailed("error")
            }
        }

    private val createBackupFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            if (uri == null) {
                pendingBackupUri = null
                notifyBackupSaved(false, "cancelled")
                return@registerForActivityResult
            }
            pendingBackupUri = uri
            requestBackupDataFromJs()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!::webView.isInitialized) {
                    dispatchSystemBack(this)
                    return
                }

                val callback = this
                try {
                    webView.evaluateJavascript(OVERLAY_BACK_PRESS_SCRIPT) { value ->
                        val handled = value?.trim()?.trim('"') == "handled"
                        if (!handled) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    dispatchSystemBack(callback)
                                }
                            }
                        }
                    }
                } catch (error: Throwable) {
                    dispatchSystemBack(this)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

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
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val urlStr = url.toString()

                if (urlStr.startsWith(ASSET_URL_PREFIX)) {
                    return false
                }

                val intent = Intent(Intent.ACTION_VIEW, url)
                startActivity(intent)
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, urlStr: String): Boolean {
                if (urlStr.startsWith(ASSET_URL_PREFIX)) {
                    return false
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                startActivity(intent)
                return true
            }

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

        webView.addJavascriptInterface(
            AndroidSystemBridge(this, webView),
            "AndroidSystemBridge"
        )

        webView.addJavascriptInterface(
            AntiCheatBridge(this),
            "AndroidAntiCheat"
        )

        webView.addJavascriptInterface(
            WebAppBridge(this),
            "AndroidBridge"
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

    fun startOpenBackup() {
        openBackupFileLauncher.launch(arrayOf("*/*"))
    }

    fun startCreateBackup() {
        createBackupFileLauncher.launch(BACKUP_FILE_NAME)
    }

    fun writeBackupFromJs(base64Data: String?) {
        val destination = pendingBackupUri
        if (destination == null) {
            notifyBackupSaved(false, "missingDestination")
            return
        }
        if (base64Data.isNullOrEmpty()) {
            pendingBackupUri = null
            notifyBackupSaved(false, "invalidData")
            return
        }
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            contentResolver.openOutputStream(destination)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw IOException("Unable to open output stream for $destination")
            notifyBackupSaved(true, null)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to write manual backup data", error)
            notifyBackupSaved(false, "error")
        } finally {
            pendingBackupUri = null
        }
    }

    private fun requestBackupDataFromJs() {
        if (!::webView.isInitialized) {
            pendingBackupUri = null
            notifyBackupSaved(false, "webviewUnavailable")
            return
        }
        webView.post {
            webView.evaluateJavascript("window.getBackupData && window.getBackupData();", null)
        }
    }

    private fun deliverBackupPayloadToJs(base64Data: String) {
        val script = "window.onBackupLoaded && window.onBackupLoaded(${JSONObject.quote(base64Data)});"
        postJavascript(script)
    }

    private fun notifyBackupLoadFailed(reason: String) {
        val safeReason = if (reason.isNotBlank()) reason else "error"
        val script = "window.onBackupLoadFailed && window.onBackupLoadFailed(${JSONObject.quote(safeReason)});"
        postJavascript(script)
    }

    private fun notifyBackupSaved(success: Boolean, reason: String?) {
        val reasonArg = reason?.takeIf { it.isNotBlank() }?.let { JSONObject.quote(it) } ?: "null"
        val script = "window.onBackupSaved && window.onBackupSaved(${if (success) "true" else "false"}, $reasonArg);"
        postJavascript(script)
    }

    internal fun postJavascript(script: String) {
        if (!::webView.isInitialized) {
            return
        }
        webView.post {
            webView.evaluateJavascript(script, null)
        }
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

    private fun dispatchSystemBack(callback: OnBackPressedCallback) {
        callback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        if (!isFinishing && !isDestroyed) {
            callback.isEnabled = true
        }
    }

    private companion object {
        private const val TAG = "Atom2Univers"
        private const val ASSET_URL_PREFIX = "https://appassets.androidplatform.net/assets/"
        private const val BACKUP_FILE_NAME = "atom2univers-backup.json"
        private const val CSS_PRESENCE_CHECK = """
            (function() {
              try {
                return document.styleSheets.length;
              } catch (error) {
                return -1;
              }
            })();
        """
        private val OVERLAY_BACK_PRESS_SCRIPT = """
            (function() {
              try {
                var overlay = document.getElementById('gachaCardOverlay');
                if (!overlay) {
                  return 'ignored';
                }
                if (overlay.hidden) {
                  return 'ignored';
                }
                var hiddenAttr = overlay.getAttribute('aria-hidden');
                if (hiddenAttr === 'true') {
                  return 'ignored';
                }
                var overlayType = overlay.dataset ? overlay.dataset.overlayType : null;
                if (overlayType && overlayType !== 'video') {
                  return 'ignored';
                }
                var closer = typeof closeSpecialCardOverlay === 'function'
                  ? closeSpecialCardOverlay
                  : (typeof window !== 'undefined' && typeof window.closeSpecialCardOverlay === 'function'
                    ? window.closeSpecialCardOverlay
                    : null);
                if (closer) {
                  closer();
                  return 'handled';
                }
              } catch (error) {
              }
              return 'ignored';
            })();
        """.trimIndent()
    }
}

