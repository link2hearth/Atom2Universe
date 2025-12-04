package com.example.atom2univers

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: GameWebView
    private var webViewSaveScript: String? = null
    private var cssRecoveryAttempted = false
    private var pendingBackupUri: Uri? = null

    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

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

    private val openBackgroundBankLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) {
                notifyBackgroundBankError("cancelled")
                return@registerForActivityResult
            }
            handleBackgroundBankSelection(uri)
        }

    private val openSoundFontLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                notifySoundFontError("cancelled")
                return@registerForActivityResult
            }
            handleSoundFontSelection(uri)
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
            .addPathHandler("/soundfonts/", SoundFontPathHandler())
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
                        resetWebViewStyles(view, url)
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

    internal fun startBackgroundBankPicker() {
        openBackgroundBankLauncher.launch(null)
    }

    internal fun loadPersistedBackgroundBank() {
        val storedUri = preferences.getString(KEY_BACKGROUND_TREE_URI, null) ?: return
        val treeUri = Uri.parse(storedUri)
        if (!hasPersistedPermission(treeUri)) {
            notifyBackgroundBankError("permission-denied")
            return
        }
        loadBackgroundBank(treeUri, false)
    }

    private fun handleBackgroundBankSelection(treeUri: Uri) {
        val relativePath = extractRelativePath(treeUri)
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to persist tree permission", error)
        }
        persistBackgroundBank(treeUri, relativePath)
        loadBackgroundBank(treeUri, true)
    }

    private fun loadBackgroundBank(treeUri: Uri, notifyWhenEmpty: Boolean) {
        Thread {
            try {
                val images = queryImagesForTree(treeUri)
                val label = preferences.getString(KEY_BACKGROUND_RELATIVE_PATH, extractRelativePath(treeUri)) ?: ""
                if (images.isEmpty()) {
                    notifyBackgroundBankReady(emptyList(), label)
                    if (notifyWhenEmpty) {
                        notifyBackgroundBankError("empty")
                    }
                    return@Thread
                }
                notifyBackgroundBankReady(images, label)
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to load background bank", error)
                notifyBackgroundBankError("permission-denied")
            } catch (error: Exception) {
                Log.w(TAG, "Unable to load background bank", error)
                notifyBackgroundBankError("error")
            }
        }.start()
    }

    private fun queryImagesForTree(treeUri: Uri, limit: Int = 250): List<String> {
        val documentUris = queryImagesWithDocumentFile(treeUri, limit)
        if (documentUris.isNotEmpty()) {
            return documentUris
        }

        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val relativePath = extractRelativePath(treeUri)
        val volumeName = if (documentId.startsWith("primary", true)) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        } else {
            MediaStore.VOLUME_EXTERNAL
        }
        val collection = MediaStore.Images.Media.getContentUri(volumeName)
        val results = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePath.isNotBlank()) {
            val normalized = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("$normalized%")
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idIndex)
                    results.add(ContentUris.withAppendedId(collection, id).toString())
                    count += 1
                }
            }
        }

        if (results.isNotEmpty()) {
            return results
        }

        return results
    }

    private fun queryImagesWithDocumentFile(treeUri: Uri, limit: Int): List<String> {
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return emptyList()
        val uris = mutableListOf<String>()
        fun collectFiles(folder: DocumentFile) {
            if (uris.size >= limit) {
                return
            }
            folder.listFiles().forEach { file ->
                if (uris.size >= limit) {
                    return
                }
                if (file.isDirectory) {
                    collectFiles(file)
                } else if (file.type?.startsWith("image/") == true) {
                    uris.add(file.uri.toString())
                }
            }
        }
        collectFiles(root)
        return uris
    }

    private fun extractRelativePath(treeUri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return documentId.substringAfter(':', "")
    }

    private fun persistBackgroundBank(treeUri: Uri, relativePath: String) {
        preferences.edit {
            putString(KEY_BACKGROUND_TREE_URI, treeUri.toString())
            putString(KEY_BACKGROUND_RELATIVE_PATH, relativePath)
        }
    }

    fun startSoundFontPicker() {
        openSoundFontLauncher.launch(arrayOf("audio/x-soundfont", "application/octet-stream"))
    }

    fun loadCachedSoundFont() {
        val cacheFile = File(filesDir, SOUND_FONT_CACHE_NAME)
        if (!cacheFile.exists()) {
            notifySoundFontError("missing")
            return
        }

        val cacheSize = cacheFile.length()
        if (cacheSize <= 0) {
            cacheFile.delete()
            clearSoundFontLabel()
            notifySoundFontError("empty")
            return
        }
        if (cacheSize > SOUND_FONT_MAX_BYTES) {
            cacheFile.delete()
            clearSoundFontLabel()
            notifySoundFontError("too_large")
            return
        }

        val label = preferences.getString(KEY_SOUND_FONT_LABEL, getString(R.string.soundfont_default_label))
        notifySoundFontReady(cacheFile, label)
    }

    private fun handleSoundFontSelection(uri: Uri) {
        val declaredSize = DocumentFile.fromSingleUri(this, uri)?.length() ?: -1
        if (declaredSize > SOUND_FONT_MAX_BYTES) {
            clearSoundFontLabel()
            notifySoundFontError("too_large")
            return
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        val target = File(filesDir, SOUND_FONT_CACHE_NAME)
        if (target.exists()) {
            target.delete()
        }
        Thread {
            importSoundFont(uri, target)
        }.start()
    }

    private fun importSoundFont(uri: Uri, target: File) {
        var errorReason: String? = null
        val displayName = DocumentFile.fromSingleUri(this, uri)?.name
            ?: getString(R.string.soundfont_default_label)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                copySoundFontStream(input, target)
            } ?: run {
                errorReason = "error"
                return
            }

            val importedSize = target.length()
            if (importedSize <= 0) {
                errorReason = "empty"
                target.delete()
                return
            }

            preferences.edit {
                putString(KEY_SOUND_FONT_LABEL, displayName)
            }
            runOnUiThread {
                notifySoundFontReady(target, displayName)
            }
        } catch (error: SoundFontTooLargeException) {
            target.delete()
            errorReason = "too_large"
        } catch (error: Exception) {
            Log.e(TAG, "Unable to import SoundFont", error)
            target.delete()
            errorReason = "error"
        } finally {
            if (errorReason != null) {
                runOnUiThread {
                    clearSoundFontLabel()
                    notifySoundFontError(errorReason ?: "error")
                }
            }
        }
    }

    private fun copySoundFontStream(input: InputStream, target: File) {
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            var total = 0L
            while (true) {
                read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                total += read
                if (total > SOUND_FONT_MAX_BYTES) {
                    throw SoundFontTooLargeException()
                }
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    private fun notifySoundFontReady(file: File, label: String?) {
        val url = "$SOUND_FONT_URL_PREFIX/$SOUND_FONT_CACHE_NAME"
        if (!file.exists() || file.length() <= 0) {
            notifySoundFontError("empty")
            return
        }
        try {
            val payload = JSONObject().apply {
                put("id", DEFAULT_SOUNDFONT_ID)
                put(
                    "name",
                    label?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.soundfont_default_label)
                )
                put("url", url)
                put("mimeType", "audio/x-soundfont")
            }
            val script = "window.onAndroidSoundFontReady && window.onAndroidSoundFontReady(${payload});"
            postJavascript(script)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to prepare SoundFont for JS", error)
            notifySoundFontError("error")
        }
    }

    private fun notifySoundFontError(reason: String) {
        val safeReason = if (reason.isNotBlank()) reason else "error"
        val message = when (safeReason) {
            "missing" -> getString(R.string.soundfont_error_missing)
            "empty" -> getString(R.string.soundfont_error_empty)
            "too_large" -> getString(R.string.soundfont_error_too_large, SOUND_FONT_MAX_BYTES / (1024 * 1024))
            else -> getString(R.string.soundfont_error_generic)
        }
        val script = "window.onAndroidSoundFontError && window.onAndroidSoundFontError(${JSONObject.quote(message)});"
        postJavascript(script)
    }

    private class SoundFontTooLargeException : Exception()

    private inner class SoundFontPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            if (path != SOUND_FONT_CACHE_NAME) {
                return null
            }

            val file = File(filesDir, SOUND_FONT_CACHE_NAME)
            if (!file.exists() || file.length() <= 0) {
                return null
            }

            return try {
                val stream = file.inputStream()
                WebResourceResponse(SOUND_FONT_MIME_TYPE, null, stream).apply {
                    responseHeaders = mapOf("Content-Length" to file.length().toString())
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to serve SoundFont", error)
                null
            }
        }
    }

    private fun clearSoundFontLabel() {
        preferences.edit {
            remove(KEY_SOUND_FONT_LABEL)
        }
    }

    private fun hasPersistedPermission(treeUri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
    }

    private fun notifyBackgroundBankReady(uris: List<String>, label: String) {
        val payload = JSONObject().apply {
            put("uris", JSONArray(uris))
            put("label", label)
        }
        val script = "window.onBackgroundImageBankLoaded && window.onBackgroundImageBankLoaded(${payload});"
        postJavascript(script)
    }

    private fun notifyBackgroundBankError(reason: String) {
        val safeReason = if (reason.isNotBlank()) reason else "error"
        val script = "window.onBackgroundImageBankError && window.onBackgroundImageBankError(${JSONObject.quote(safeReason)});"
        postJavascript(script)
    }

    private fun resetWebViewStyles(view: WebView, url: String) {
        view.post {
            view.clearCache(true)
            view.clearHistory()
            view.clearFormData()
            view.clearFocus()
            view.requestFocus()
            view.loadUrl(url)
        }
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
        private const val PREFERENCES_NAME = "atom2univers_prefs"
        private const val KEY_BACKGROUND_TREE_URI = "background.tree.uri"
        private const val KEY_BACKGROUND_RELATIVE_PATH = "background.relative.path"
        private const val KEY_SOUND_FONT_LABEL = "soundfont.label"
        private const val SOUND_FONT_CACHE_NAME = "user_soundfont.sf2"
        private const val DEFAULT_SOUNDFONT_ID = "user-soundfont"
        private const val SOUND_FONT_MIME_TYPE = "audio/x-soundfont"
        private const val SOUND_FONT_URL_PREFIX = "https://appassets.androidplatform.net/soundfonts"
        private const val SOUND_FONT_MAX_BYTES = 1L * 1024 * 1024 * 1024
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

