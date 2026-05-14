package com.Atom2Universe.app.music

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Activity pour rechercher une pochette d'album sur Google Images.
 * Permet de télécharger une image via long-press.
 */
class AlbumArtSearchActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    companion object {
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM = "extra_album"
        const val RESULT_IMAGE_PATH = "result_image_path"
        private const val TAG = "AlbumArtSearch"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar

    private var isDownloading = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_album_art_search)

        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progress_bar)

        setupToolbar()
        setupWebView()
        setupBackNavigation()

        // Load Google Images search
        val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
        val album = intent.getStringExtra(EXTRA_ALBUM) ?: ""
        searchImages(artist, album)
    }

    private fun setupBackNavigation() {
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

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { navigateBackToHub() }
        toolbar.title = getString(R.string.music_search_cover)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Allow mixed content for image loading
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Enable cookies for Google
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Allow Google domains
                return !url.contains("google.com") && !url.contains("gstatic.com")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = android.view.View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }

        // Handle long press on images
        webView.setOnLongClickListener {
            if (isDownloading) return@setOnLongClickListener false

            val result = webView.hitTestResult
            Log.d(TAG, "HitTest type: ${result.type}, extra: ${result.extra?.take(100)}")

            when (result.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val imageUrl = result.extra
                    if (imageUrl != null) {
                        downloadImage(imageUrl)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun searchImages(artist: String, album: String) {
        val query = "$artist $album album cover"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // Use mobile version for better image access
        val url = "https://www.google.com/search?q=$encodedQuery&tbm=isch"
        Log.d(TAG, "Searching: $url")
        webView.loadUrl(url)
    }

    private fun downloadImage(imageUrl: String) {
        if (isDownloading) return
        isDownloading = true

        Log.d(TAG, "Downloading image URL (first 200 chars): ${imageUrl.take(200)}")
        Toast.makeText(this, R.string.music_downloading_cover, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val filePath = when {
                    imageUrl.startsWith("data:image") -> {
                        // Handle data URL (base64 encoded image)
                        saveDataUrl(imageUrl)
                    }
                    imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                        // Handle regular URL
                        downloadFromUrl(imageUrl)
                    }
                    else -> {
                        Log.e(TAG, "Unsupported URL scheme: ${imageUrl.take(50)}")
                        null
                    }
                }

                // Compress the image for album cover (max 800px, max 200Ko)
                val compressedPath = if (filePath != null) {
                    compressForAlbumCover(filePath)
                } else null

                withContext(Dispatchers.Main) {
                    if (compressedPath != null) {
                        Log.d(TAG, "Download and compression successful: $compressedPath")
                        val resultIntent = Intent()
                        resultIntent.putExtra(RESULT_IMAGE_PATH, compressedPath)
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } else {
                        Toast.makeText(this@AlbumArtSearchActivity, R.string.music_download_error, Toast.LENGTH_SHORT).show()
                        isDownloading = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AlbumArtSearchActivity, R.string.music_download_error, Toast.LENGTH_SHORT).show()
                    isDownloading = false
                }
            }
        }
    }

    private suspend fun saveDataUrl(dataUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // Parse data URL: data:image/jpeg;base64,/9j/4AAQ...
            val commaIndex = dataUrl.indexOf(',')
            if (commaIndex == -1) {
                Log.e(TAG, "Invalid data URL format")
                return@withContext null
            }

            val base64Data = dataUrl.substring(commaIndex + 1)
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

            // Determine extension from mime type
            val mimeType = dataUrl.substring(5, dataUrl.indexOf(';'))
            val extension = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("webp") -> "webp"
                else -> "jpg"
            }

            val outputFile = createOutputFile(extension)
            FileOutputStream(outputFile).use { fos ->
                fos.write(imageBytes)
            }

            Log.d(TAG, "Saved data URL image: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving data URL: ${e.message}", e)
            null
        }
    }

    private suspend fun downloadFromUrl(imageUrl: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(imageUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                setRequestProperty("Accept", "image/*")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: $responseCode")
                return@withContext null
            }

            // Determine extension from content type or URL
            val contentType = connection.contentType ?: ""
            val extension = when {
                contentType.contains("png") -> "png"
                contentType.contains("gif") -> "gif"
                contentType.contains("webp") -> "webp"
                imageUrl.contains(".png") -> "png"
                imageUrl.contains(".gif") -> "gif"
                imageUrl.contains(".webp") -> "webp"
                else -> "jpg"
            }

            val outputFile = createOutputFile(extension)
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Verify file was written
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Downloaded image: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                outputFile.absolutePath
            } else {
                Log.e(TAG, "Downloaded file is empty or doesn't exist")
                outputFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from URL: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun createOutputFile(extension: String): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "album_cover_$timestamp.$extension"
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, fileName)
    }

    /**
     * Compresse l'image telechargee pour une pochette d'album.
     * Max 800px, max 200Ko.
     */
    private suspend fun compressForAlbumCover(inputPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val inputFile = File(inputPath)
            val originalSize = inputFile.length()

            val outputFile = File(cacheDir, "compressed_album_cover_${System.currentTimeMillis()}.jpg")

            val success = ImageCompressor.compressAlbumCover(inputPath, outputFile.absolutePath)

            // Supprimer le fichier original
            inputFile.delete()

            if (success && outputFile.exists()) {
                val newSize = outputFile.length()
                Log.d(TAG, "Album cover compressed: ${originalSize / 1024}Ko -> ${newSize / 1024}Ko")
                outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to compress album cover")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing album cover", e)
            null
        }
    }

}
