package com.Atom2Universe.app.crypto

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

class FloatingWebWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val BLANK_URL = "about:blank"
    }

    private val card: MaterialCardView
    private val header: FrameLayout
    private val titleView: TextView
    private val closeBtn: TextView
    private val expandBtn: TextView
    private val backBtn: TextView
    private val reloadBtn: TextView
    private val urlBar: EditText
    private val progressBar: ProgressBar
    private val webContainer: FrameLayout

    // Créée au premier article ouvert, pas à chaque ouverture du clicker
    private var webView: WebView? = null
    // Fermé puis rouvert : l'historique de l'article précédent est effacé au premier chargement
    private var clearHistoryOnLoad = false
    // Plantages du moteur de rendu depuis la dernière page demandée (au-delà d'un, on ferme)
    private var renderCrashes = 0

    // pauseTimers() fige le JavaScript de TOUTES les WebView du processus : relâché dès
    // qu'une autre activité (paroles, pochettes…) passe au premier plan
    private var timersPaused = false
    private val resumeTimersOnOtherActivity = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { resumeTimersIfPaused() }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    // ── Plein écran ───────────────────────────────────────────────────────────
    private var isExpanded = false
    private val cardOriginalWidth  = (340 * context.resources.displayMetrics.density).toInt()
    private val cardOriginalHeight = (480 * context.resources.displayMetrics.density).toInt()

    // ── Zoom (pinch sur le header) ────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f
    private var isTwoFingerGesture = false
    private var skipNextDragFrame = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next
                    scaleX = currentScale
                    scaleY = currentScale
                }
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (!isExpanded) keepFloatingWidgetOnScreen()
            }
        })

    // ── Drag 1 doigt via header ───────────────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private val tapThresholdPx = 10f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_floating_web_widget, this, true)

        card        = findViewById(R.id.floating_web_card)
        header      = findViewById(R.id.floating_web_header)
        titleView   = findViewById(R.id.floating_web_title)
        closeBtn    = findViewById(R.id.floating_web_close)
        expandBtn   = findViewById(R.id.floating_web_expand)
        backBtn     = findViewById(R.id.floating_web_back)
        reloadBtn   = findViewById(R.id.floating_web_reload)
        urlBar      = findViewById(R.id.floating_web_url_bar)
        progressBar = findViewById(R.id.floating_web_progress)
        webContainer = findViewById(R.id.floating_web_container)

        // Coins arrondis pour la barre d'URL
        urlBar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * context.resources.displayMetrics.density
            setColor(0xFF1E293B.toInt())
        }

        closeBtn.setOnClickListener { dismiss() }
        expandBtn.setOnClickListener { toggleExpand() }
        header.setOnTouchListener { _, event -> handleHeaderTouch(event) }

        backBtn.setOnClickListener {
            webView?.let { if (it.canGoBack()) it.goBack() }
        }
        reloadBtn.setOnClickListener {
            webView?.reload()
        }
        urlBar.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                    && event.action == KeyEvent.ACTION_DOWN
            if (isGo || isEnter) {
                navigateTo(urlBar.text.toString().trim())
                true
            } else false
        }

        keepFloatingWidgetOnScreenOnResize()
    }

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val view = WebView(context)
        webContainer.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        with(view.settings) {
            javaScriptEnabled    = true
            setSupportZoom(true)
            builtInZoomControls  = true
            displayZoomControls  = false
            useWideViewPort      = true
            loadWithOverviewMode = true
            domStorageEnabled    = true
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url == BLANK_URL) return
                if (!urlBar.isFocused) urlBar.setText(url ?: "")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == BLANK_URL) return
                if (clearHistoryOnLoad) {
                    clearHistoryOnLoad = false
                    view?.clearHistory()
                }
                titleView.text = view?.title?.takeIf { it.isNotBlank() } ?: extractDomain(url)
                urlBar.setText(url ?: "")
                backBtn.alpha = if (view?.canGoBack() == true) 1f else 0.4f
            }
            // Moteur de rendu tué (mémoire) ou planté : sans ce retour à true, l'appli entière plante
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (view == null || view !== webView) return true
                val url = view.url?.takeIf { it != BLANK_URL }
                discardWebView()
                renderCrashes++
                if (visibility == VISIBLE && url != null && renderCrashes <= 1) {
                    ensureWebView().loadUrl(url)
                } else if (visibility == VISIBLE) {
                    dismiss()
                }
                return true
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.progress = newProgress
                    progressBar.visibility = VISIBLE
                } else {
                    progressBar.visibility = GONE
                }
            }
        }

        setupImageLongPress(view)
        webView = view
        return view
    }

    private fun discardWebView() {
        val view = webView ?: return
        resumeTimersIfPaused()
        webView = null
        webContainer.removeView(view)
        view.destroy()
    }

    // ── Public ────────────────────────────────────────────────────────────────

    fun load(url: String) {
        val view = ensureWebView()
        if (visibility != VISIBLE) clearHistoryOnLoad = true
        renderCrashes = 0
        titleView.text = extractDomain(url)
        progressBar.progress = 0
        urlBar.setText(url)
        backBtn.alpha = 0.4f
        view.onResume()
        view.loadUrl(url)
        visibility = VISIBLE
        bringToFront()
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        if (isExpanded) {
            // Passer en plein écran
            val widgetLp = layoutParams as LayoutParams
            widgetLp.width   = LayoutParams.MATCH_PARENT
            widgetLp.height  = LayoutParams.MATCH_PARENT
            widgetLp.gravity = Gravity.NO_GRAVITY
            layoutParams = widgetLp

            card.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            currentScale = 1f
            expandBtn.text = "⤡"
        } else {
            // Restaurer la taille originale
            val widgetLp = layoutParams as LayoutParams
            widgetLp.width   = LayoutParams.WRAP_CONTENT
            widgetLp.height  = LayoutParams.WRAP_CONTENT
            widgetLp.gravity = Gravity.CENTER
            layoutParams = widgetLp

            card.layoutParams = LayoutParams(cardOriginalWidth, cardOriginalHeight)
            expandBtn.text = "⤢"
        }
    }

    private fun navigateTo(input: String) {
        if (input.isBlank()) return
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
        ensureWebView().loadUrl(url)
        urlBar.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    fun dismiss() {
        if (isExpanded) {
            isExpanded = false
            val widgetLp = layoutParams as LayoutParams
            widgetLp.width   = LayoutParams.WRAP_CONTENT
            widgetLp.height  = LayoutParams.WRAP_CONTENT
            widgetLp.gravity = Gravity.CENTER
            layoutParams = widgetLp
            card.layoutParams = LayoutParams(cardOriginalWidth, cardOriginalHeight)
            expandBtn.text = "⤢"
        }
        // Fermé : la page est vidée (plus de JS, de pubs ni de vidéo) et la WebView mise en pause
        webView?.let {
            it.stopLoading()
            it.loadUrl(BLANK_URL)
            it.onPause()
        }
        visibility = GONE
    }

    fun onPause() {
        val view = webView ?: return
        view.onPause()
        if (!timersPaused) {
            view.pauseTimers()
            timersPaused = true
            (context.applicationContext as? Application)
                ?.registerActivityLifecycleCallbacks(resumeTimersOnOtherActivity)
        }
    }

    fun onResume() {
        val view = webView ?: return
        resumeTimersIfPaused()
        if (visibility == VISIBLE) view.onResume()
    }

    fun onDestroy() {
        discardWebView()
        resumeTimersIfPaused()
    }

    private fun resumeTimersIfPaused() {
        if (!timersPaused) return
        timersPaused = false
        (context.applicationContext as? Application)
            ?.unregisterActivityLifecycleCallbacks(resumeTimersOnOtherActivity)
        // Méthode d'instance mais effet global ; pauseTimers() n'a lieu qu'avec une WebView,
        // et discardWebView() relance les minuteurs avant de la détruire
        webView?.resumeTimers()
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true
    fun goBack() { webView?.goBack() }

    // ── Touch header : drag 1 doigt + pinch 2 doigts ─────────────────────────

    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        // Toujours alimenter le scaleDetector en premier
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownX = event.rawX; dragDownY = event.rawY
                dragLastX = event.rawX; dragLastY = event.rawY
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isTwoFingerGesture = true
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Quand un doigt se lève après pinch, ignorer le prochain MOVE
                // pour éviter un saut de position
                skipNextDragFrame = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isExpanded && !isTwoFingerGesture && event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val dx = event.rawX - dragLastX
                        val dy = event.rawY - dragLastY
                        val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += dx
                            translationY += dy
                        }
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
                if (!isExpanded) keepFloatingWidgetOnScreen()
            }
        }
        return true
    }

    // ── Long press sur image → téléchargement ────────────────────────────────

    private fun setupImageLongPress(view: WebView) {
        view.setOnLongClickListener {
            val result = view.hitTestResult
            val isImage = result.type == WebView.HitTestResult.IMAGE_TYPE ||
                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val imageUrl = result.extra
            if (isImage && !imageUrl.isNullOrBlank()) {
                downloadWebImage(view, imageUrl)
                true // consomme l'event : pas de menu contextuel par défaut
            } else {
                false
            }
        }
    }

    private fun downloadWebImage(view: WebView, url: String) {
        // Android 9 et avant : écrire dans Pictures demande la permission ; on la demande, l'appui
        // long suivant enregistrera l'image
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            findActivity()?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            }
            return
        }
        // Capturer les infos WebView sur le main thread avant de basculer en background
        val userAgent = view.settings.userAgentString
        val referer   = view.url
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout    = 15_000
                connection.setRequestProperty("User-Agent", userAgent)
                if (!referer.isNullOrBlank()) connection.setRequestProperty("Referer", referer)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) connection.setRequestProperty("Cookie", cookies)

                if (connection.responseCode !in 200..299) {
                    postToast(context.getString(R.string.floating_web_image_error_download))
                    return@Thread
                }
                // Le dossier des images n'accepte que des types image/* (les CDN renvoient
                // parfois application/octet-stream) : sinon, déduit de l'URL
                val rawMime = connection.contentType
                    ?.substringBefore(';')?.trim()
                    ?.takeIf { it.startsWith("image/") }
                val mimeType = rawMime ?: guessMimeTypeFromUrl(url)

                val filename = buildSmartFilename(url, mimeType)
                // Copié par morceaux vers le fichier : une grosse image ne passe jamais en mémoire
                val saved = connection.inputStream.use { saveToAtom2Universe(it, filename, mimeType) }
                if (saved) showDownloadToast(filename)
                else postToast(context.getString(R.string.floating_web_image_error_save))

            } catch (_: Exception) {
                postToast(context.getString(R.string.floating_web_image_error_download))
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun findActivity(): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** Écrit l'image dans Pictures/Atom2Universe ; false (et rien de laissé derrière) si vide ou en échec. */
    private fun saveToAtom2Universe(input: InputStream, filename: String, mimeType: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Atom2Universe")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: return false
            val written = try {
                resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: 0L
            } catch (_: Exception) { 0L }
            if (written <= 0L) {
                runCatching { resolver.delete(uri, null, null) }
                return false
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val dir = File(picturesDir, "Atom2Universe").also { it.mkdirs() }
            val file = File(dir, filename)
            val written = try {
                file.outputStream().use { input.copyTo(it) }
            } catch (_: Exception) { 0L }
            if (written <= 0L) {
                file.delete()
                return false
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            true
        }
    }

    // ── Nom de fichier intelligent ────────────────────────────────────────────

    private fun buildSmartFilename(url: String, mimeType: String): String {
        val ext = extFromMime(mimeType) ?: extFromUrl(url) ?: "jpg"
        val rawName = try {
            Uri.parse(url).lastPathSegment
                ?.substringBeforeLast('.')
                ?.trim()
                ?: ""
        } catch (_: Exception) { "" }

        return if (!isGenericName(rawName)) {
            sanitizeFilename(rawName).take(60) + ".$ext"
        } else {
            val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val site = extractShortSiteName(url)
            if (site.isNotEmpty()) "${date}_${site}.$ext" else "${date}.$ext"
        }
    }

    private val genericNames = setOf(
        "image", "images", "img", "photo", "photos", "pic", "thumbnail",
        "thumb", "avatar", "logo", "banner", "background", "bg", "default",
        "placeholder", "icon", "sprite", "cover", "poster", "header",
        "figure", "media", "content", "file", "upload", "download", "asset"
    )

    private fun isGenericName(name: String): Boolean {
        if (name.isBlank() || name.length < 3) return true
        val lower = name.lowercase()
        if (lower in genericNames) return true
        if (genericNames.any { lower.startsWith(it) && lower.length <= it.length + 5 }) return true
        // Purement numérique / tirets / underscores
        if (lower.all { it.isDigit() || it == '-' || it == '_' }) return true
        // UUID
        if (lower.matches(Regex("[0-9a-f]{8}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{4}[-_][0-9a-f]{12}"))) return true
        // Hash hex CDN (≥16 chars tout en hexa)
        if (lower.length >= 16 && lower.all { it.isDigit() || it in 'a'..'f' }) return true
        return false
    }

    private fun extractShortSiteName(url: String): String {
        val host = try { Uri.parse(url).host ?: "" } catch (_: Exception) { "" }
        val domain = host.removePrefix("www.").substringBefore('.')
        return if (domain.length in 2..15) sanitizeFilename(domain) else ""
    }

    private fun extFromMime(mime: String): String? =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.isNotBlank() }

    private fun extFromUrl(url: String): String? {
        val path = try { Uri.parse(url).path ?: "" } catch (_: Exception) { "" }
        val ext = path.substringAfterLast('.', "")
            .substringBefore('?').substringBefore('#')
            .lowercase().take(5)
        return ext.takeIf { it.isNotBlank() && ext.all { c -> c.isLetter() } }
    }

    private fun guessMimeTypeFromUrl(url: String): String {
        val ext = extFromUrl(url) ?: return "image/jpeg"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")

    private fun showDownloadToast(filename: String) {
        val msg = context.getString(R.string.floating_web_image_saved, filename)
        // ~5 secondes : LENGTH_LONG (~3.5s) + LENGTH_SHORT (~2s) décalé
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }, 2_500)
    }

    private fun postToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Domaine ───────────────────────────────────────────────────────────────

    private fun extractDomain(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val host = java.net.URI(url).host ?: url
            host.removePrefix("www.")
        } catch (_: Exception) { url }
    }
}
