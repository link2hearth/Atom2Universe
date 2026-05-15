package com.Atom2Universe.app.news

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

class NewsWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val ROTATE_INTERVAL_MS = 30_000L
        private const val FETCH_INTERVAL_MS  = 15 * 60_000L
        const val EXTRA_HIGHLIGHT_ID = "news_highlight_id"
        private val BASE_CARD_COLOR = 0xFF0F172A.toInt()
    }

    private val cardView: MaterialCardView
    private val header: FrameLayout
    private val btnUnhide: TextView
    private val btnHide: TextView
    private val btnOpen: TextView
    private val titleView: TextView
    private val metaView: TextView
    private val statusView: TextView

    private val hiddenStack = ArrayDeque<NewsArticle>()

    private val unhideHideRunnable = Runnable {
        btnUnhide.visibility = View.GONE
    }
    private fun scheduleUnhideButtonHide() {
        handler.removeCallbacks(unhideHideRunnable)
        handler.postDelayed(unhideHideRunnable, 15_000L)
    }
    private fun cancelUnhideButtonHide() {
        handler.removeCallbacks(unhideHideRunnable)
    }

    var onOpenUrl: ((String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var fetchJob: Job? = null

    private var visibleArticles: List<NewsArticle> = emptyList()
    private var currentIndex = 0

    private var toggleEnabled = true
    private enum class WidgetState { LOADING, HAS_ARTICLES, EMPTY, ERROR }
    private var widgetState = WidgetState.LOADING

    private fun updateSelfVisibility() {
        visibility = if (toggleEnabled && widgetState != WidgetState.EMPTY) View.VISIBLE else View.GONE
    }

    fun setToggleEnabled(enabled: Boolean) {
        toggleEnabled = enabled
        updateSelfVisibility()
        if (enabled) startPeriodicFetch() else stopPeriodicFetch()
    }

    // ── Zoom (pinch, 2 doigts) ────────────────────────────────────────────────
    private val minScale = 0.6f
    private val maxScale = 2.5f
    private var currentScale = 2.5f

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
        })

    // ── Swipe 1 doigt → navigation articles ──────────────────────────────────
    private val swipeDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            private val minDist = 50f * context.resources.displayMetrics.density
            private val minVel  = 150f * context.resources.displayMetrics.density

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: return false)
                if (abs(dx) < minDist || abs(velocityX) < minVel) return false
                if (dx < 0) showNext(resetTimer = true) else showPrevious(resetTimer = true)
                return true
            }
        })

    // ── Drag (header uniquement) ──────────────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private val tapThresholdPx = 10f * context.resources.displayMetrics.density

    // ── Auto-rotation ─────────────────────────────────────────────────────────
    private val rotateRunnable = object : Runnable {
        override fun run() {
            showNext(resetTimer = false)
            if (visibleArticles.size > 1) {
                handler.postDelayed(this, ROTATE_INTERVAL_MS)
            }
        }
    }

    // ── Re-fetch périodique ───────────────────────────────────────────────────
    private val fetchRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, FETCH_INTERVAL_MS)
        }
    }

    private fun startPeriodicFetch() {
        handler.removeCallbacks(fetchRunnable)
        handler.postDelayed(fetchRunnable, FETCH_INTERVAL_MS)
    }

    private fun stopPeriodicFetch() {
        handler.removeCallbacks(fetchRunnable)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_news_widget, this, true)
        scaleX = currentScale
        scaleY = currentScale

        cardView   = findViewById(R.id.news_widget_card)
        header     = findViewById(R.id.news_widget_header)
        btnUnhide  = findViewById(R.id.news_widget_btn_unhide)
        btnHide    = findViewById(R.id.news_widget_btn_hide)
        btnOpen    = findViewById(R.id.news_widget_btn_open)
        titleView  = findViewById(R.id.news_widget_title)
        metaView   = findViewById(R.id.news_widget_meta)
        statusView = findViewById(R.id.news_widget_status)

        btnUnhide.setOnClickListener { unhideLastArticle() }
        btnHide.setOnClickListener { hideCurrentArticle() }
        btnOpen.setOnClickListener { openCurrentArticle() }
        header.setOnTouchListener { _, event -> handleHeaderTouch(event) }

        showStatus(context.getString(R.string.news_widget_loading))
    }

    // ── Opacité ───────────────────────────────────────────────────────────────

    fun applyBackgroundOpacity(opacityPercent: Int) {
        val alpha = ((opacityPercent.coerceIn(0, 100) / 100f) * 255f).toInt().coerceIn(0, 255)
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(BASE_CARD_COLOR, alpha))
    }

    // ── Public ────────────────────────────────────────────────────────────────

    fun refresh() {
        fetchJob?.cancel()
        hiddenStack.clear()
        cancelUnhideButtonHide()
        btnUnhide.visibility = View.GONE
        fetchJob = scope.launch {
            widgetState = WidgetState.LOADING
            showStatus(context.getString(R.string.news_widget_loading))
            updateSelfVisibility()
            val sources = NewsSource.DEFAULT_SOURCES.filter {
                it.id in NewsPreferences.getEnabledSourceIds(context)
            }
            val banned = NewsPreferences.getBannedWords(context)
            try {
                withContext(Dispatchers.IO) {
                    NewsRepository.refresh(sources, bannedWords = banned)
                }
                // Lu après le fetch pour inclure les hides effectués pendant le réseau.
                val hidden = NewsPreferences.getHiddenIds(context).keys
                visibleArticles = NewsRepository.filterVisible(hidden, banned)
                currentIndex = 0
                if (visibleArticles.isEmpty()) {
                    widgetState = WidgetState.EMPTY
                    updateSelfVisibility()
                    stopRotation()
                } else {
                    showCurrentArticle()
                    startRotation()
                }
            } catch (_: Exception) {
                widgetState = WidgetState.ERROR
                showStatus(context.getString(R.string.news_widget_error))
                updateSelfVisibility()
                stopRotation()
            }
        }
    }

    fun stopRotation() {
        handler.removeCallbacks(rotateRunnable)
    }

    fun startRotation() {
        handler.removeCallbacks(rotateRunnable)
        if (visibleArticles.size > 1) {
            handler.postDelayed(rotateRunnable, ROTATE_INTERVAL_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
        if (toggleEnabled) startPeriodicFetch()
    }

    override fun onDetachedFromWindow() {
        stopRotation()
        stopPeriodicFetch()
        fetchJob?.cancel()
        super.onDetachedFromWindow()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun showNext(resetTimer: Boolean) {
        if (visibleArticles.isEmpty()) return
        currentIndex = (currentIndex + 1) % visibleArticles.size
        showCurrentArticle()
        if (resetTimer) startRotation()
    }

    private fun showPrevious(resetTimer: Boolean) {
        if (visibleArticles.isEmpty()) return
        currentIndex = (currentIndex - 1 + visibleArticles.size) % visibleArticles.size
        showCurrentArticle()
        if (resetTimer) startRotation()
    }

    private fun showCurrentArticle() {
        val article = visibleArticles.getOrNull(currentIndex) ?: return
        titleView.text = article.title
        metaView.text  = buildMeta(article)
        titleView.visibility  = View.VISIBLE
        metaView.visibility   = View.VISIBLE
        statusView.visibility = View.GONE
        widgetState = WidgetState.HAS_ARTICLES
        updateSelfVisibility()
    }

    private fun hideCurrentArticle() {
        val article = visibleArticles.getOrNull(currentIndex) ?: return
        hiddenStack.addLast(article)
        NewsPreferences.addHiddenId(context, article.id)
        val banned = NewsPreferences.getBannedWords(context)
        val hidden = NewsPreferences.getHiddenIds(context).keys
        visibleArticles = NewsRepository.filterVisible(hidden, banned)
        btnUnhide.visibility = View.VISIBLE
        scheduleUnhideButtonHide()
        if (visibleArticles.isEmpty()) {
            widgetState = WidgetState.EMPTY
            updateSelfVisibility()
            stopRotation()
            return
        }
        currentIndex = currentIndex.coerceAtMost(visibleArticles.size - 1)
        showCurrentArticle()
        startRotation()
    }

    private fun unhideLastArticle() {
        val article = hiddenStack.removeLastOrNull() ?: return
        NewsPreferences.removeHiddenId(context, article.id)
        val banned = NewsPreferences.getBannedWords(context)
        val hidden = NewsPreferences.getHiddenIds(context).keys
        visibleArticles = NewsRepository.filterVisible(hidden, banned)
        if (hiddenStack.isNotEmpty()) {
            btnUnhide.visibility = View.VISIBLE
            scheduleUnhideButtonHide()
        } else {
            btnUnhide.visibility = View.GONE
            cancelUnhideButtonHide()
        }
        if (visibleArticles.isEmpty()) {
            widgetState = WidgetState.EMPTY
            updateSelfVisibility()
            stopRotation()
            return
        }
        val idx = visibleArticles.indexOfFirst { it.id == article.id }
        currentIndex = if (idx >= 0) idx else currentIndex.coerceAtMost(visibleArticles.size - 1)
        showCurrentArticle()
        startRotation()
    }

    private fun openCurrentArticle() {
        val article = visibleArticles.getOrNull(currentIndex)
        val url = article?.link
        if (url != null && onOpenUrl != null) {
            onOpenUrl!!.invoke(url)
        } else {
            val intent = Intent(context, NewsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                article?.let { putExtra(EXTRA_HIGHLIGHT_ID, it.id) }
            }
            context.startActivity(intent)
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    private fun showStatus(text: String) {
        titleView.visibility  = View.GONE
        metaView.visibility   = View.GONE
        statusView.text       = text
        statusView.visibility = View.VISIBLE
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    private fun buildMeta(article: NewsArticle): String {
        val parts = mutableListOf<String>()
        if (article.sourceName.isNotBlank()) parts += article.sourceName
        article.pubDateMs?.let { parts += relativeTime(it) }
        return parts.joinToString(" · ")
    }

    private fun relativeTime(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        return when {
            diff < 60_000L     -> "à l'instant"
            diff < 3_600_000L  -> "il y a ${diff / 60_000} min"
            diff < 86_400_000L -> "il y a ${diff / 3_600_000} h"
            else               -> "il y a ${diff / 86_400_000} j"
        }
    }

    // ── Touch : dispatch ─────────────────────────────────────────────────────

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true
        swipeDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    // Sans cet override, si aucun enfant ne consomme ACTION_DOWN (zone titre/méta),
    // le système ne livre plus les MOVE/UP au widget et le GestureDetector ne peut
    // jamais déclencher onFling. En retournant true ici, le widget consomme tous les
    // touches que ses enfants n'ont pas pris, garantissant la séquence complète.
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    // ── Touch : drag via header ───────────────────────────────────────────────

    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX; dragDownY = event.rawY
                dragLastX = event.rawX; dragLastY = event.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
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
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val intent = Intent(context, NewsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return true
    }
}
