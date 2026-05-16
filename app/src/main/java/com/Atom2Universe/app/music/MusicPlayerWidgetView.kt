package com.Atom2Universe.app.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.lyrics.LrcParser
import com.Atom2Universe.app.music.lyrics.LyricsManager
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.view.AudioVisualizerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.core.content.edit

class MusicPlayerWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), MusicPlaybackHolder.PlayerListener {

    companion object {
        private const val PREFS = "music_widget_prefs"
        private const val KEY_VIZ_MODE = "viz_mode"
        private const val HIDE_DELAY_MS = 9_000L
        private const val FADE_DURATION_MS = 1_000L

        private val VIZ_MODES = listOf(
            AudioVisualizerView.VisualizationMode.BARS              to "Bars",
            AudioVisualizerView.VisualizationMode.WAVE              to "Wave",
            AudioVisualizerView.VisualizationMode.MIRROR            to "Mirror",
            AudioVisualizerView.VisualizationMode.SPECTRUM          to "Spectrum",
            AudioVisualizerView.VisualizationMode.FIRE              to "Fire",
            AudioVisualizerView.VisualizationMode.CIRCLE            to "Circle",
            AudioVisualizerView.VisualizationMode.PARTICLES         to "Particles",
            AudioVisualizerView.VisualizationMode.RADIAL            to "Radial",
            AudioVisualizerView.VisualizationMode.BLOB              to "Blob",
            AudioVisualizerView.VisualizationMode.PARTICLES_MONO    to "Particles B&W",
            AudioVisualizerView.VisualizationMode.KALEIDOSCOPE      to "Kaleidoscope",
            AudioVisualizerView.VisualizationMode.BOIDS             to "Boids",
            AudioVisualizerView.VisualizationMode.JULIA             to "Julia",
            AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM   to "Mandelbrot",
            AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE   to "Julia B&W",
            AudioVisualizerView.VisualizationMode.DELAUNAY_MESH     to "Delaunay",
            AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE to "Delaunay B&W",
            AudioVisualizerView.VisualizationMode.VORONOI           to "Voronoï",
            AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE to "Voronoï B&W",
            AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS   to "Penrose",
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE      to "Penrose True",
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val cardView: MaterialCardView
    private val bgView: View
    private val header: FrameLayout
    private val trackTitle: TextView
    private val trackArtist: TextView
    private val noMusicView: TextView
    private val libBtn: TextView
    private val vizBtn: TextView
    private val lyricsBtn: TextView
    private val controlsView: LinearLayout
    private val btnPrev: ImageButton
    private val btnPlayPause: ImageButton
    private val btnNext: ImageButton
    private val visualizerView: AudioVisualizerView

    // Lyrics views
    private val lyricsOverlay: FrameLayout
    private val lyricsScrollView: ScrollView
    private val lyricsTextView: TextView
    private val lyricsSyncLine: TextView

    private var audioVisualizer: Visualizer? = null
    private var currentVizMode: AudioVisualizerView.VisualizationMode

    // Lyrics state
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lyricsJob: Job? = null
    private var lyricsVisible = false
    private var lyricsLines: List<LrcParser.LyricLine>? = null
    private var isSyncedLyrics = false
    private var lastSyncedLineIdx = -1

    // ── Auto-hide contrôles ───────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private val hideRunnable = Runnable { fadeControls(show = false) }

    // ── Pinch-to-zoom ─────────────────────────────────────────────────────────
    private var currentScale = 1.0f
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale = (currentScale * detector.scaleFactor).coerceIn(0.5f, 3.0f)
                scaleX = currentScale
                scaleY = currentScale
                return true
            }
        })

    // ── Drag via header ───────────────────────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        inflate(context, R.layout.view_music_player_widget, this)

        cardView       = findViewById(R.id.music_widget_card)
        bgView         = findViewById(R.id.music_widget_bg)
        header         = findViewById(R.id.music_widget_header)
        trackTitle     = findViewById(R.id.music_widget_title)
        trackArtist    = findViewById(R.id.music_widget_artist)
        noMusicView    = findViewById(R.id.music_widget_no_music)
        libBtn         = findViewById(R.id.music_widget_lib_btn)
        vizBtn         = findViewById(R.id.music_widget_viz_btn)
        lyricsBtn      = findViewById(R.id.music_widget_lyrics_btn)
        controlsView   = findViewById(R.id.music_widget_controls)
        btnPrev        = findViewById(R.id.music_widget_btn_prev)
        btnPlayPause   = findViewById(R.id.music_widget_btn_play_pause)
        btnNext        = findViewById(R.id.music_widget_btn_next)
        visualizerView = findViewById(R.id.music_widget_visualizer)
        lyricsOverlay  = findViewById(R.id.music_widget_lyrics_overlay)
        lyricsScrollView = findViewById(R.id.music_widget_lyrics_scroll)
        lyricsTextView = findViewById(R.id.music_widget_lyrics_text)
        lyricsSyncLine = findViewById(R.id.music_widget_lyrics_sync_line)

        val savedName = prefs.getString(KEY_VIZ_MODE, AudioVisualizerView.VisualizationMode.CIRCLE.name)
        currentVizMode = runCatching {
            AudioVisualizerView.VisualizationMode.valueOf(savedName ?: "")
        }.getOrDefault(AudioVisualizerView.VisualizationMode.CIRCLE)
        applyVizMode(currentVizMode)

        header.setOnTouchListener { _, event -> handleHeaderTouch(event) }
        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            false
        }

        libBtn.setOnClickListener { openMusicLibrary() }
        vizBtn.setOnClickListener { showVizPicker() }
        lyricsBtn.setOnClickListener { toggleLyrics() }

        btnPrev.setOnClickListener {
            if (MusicPlaybackHolder.getCurrentTrack() == null) MusicPlaybackHolder.restoreQueueOnly(context)
            MusicPlaybackHolder.skipToPrevious(context)
        }
        btnNext.setOnClickListener {
            if (MusicPlaybackHolder.getCurrentTrack() == null) MusicPlaybackHolder.restoreQueueOnly(context)
            MusicPlaybackHolder.skipToNext(context)
        }
        btnPlayPause.setOnClickListener {
            if (MusicPlaybackHolder.isPlaying()) {
                MusicPlaybackHolder.pause(context)
            } else {
                if (MusicPlaybackHolder.getCurrentTrack() == null) {
                    MusicPlaybackHolder.restoreQueueOnly(context)
                }
                MusicPlaybackHolder.resume(context)
            }
        }

        updateTrackUi(MusicPlaybackHolder.getCurrentTrack())
        updatePlayPauseButton(MusicPlaybackHolder.isPlaying())
    }

    // ── Chaque toucher sur le widget remet le timer à zéro ───────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        resetHideTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideRunnable)
        if (!controlsVisible) fadeControls(show = true)
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun fadeControls(show: Boolean) {
        if (show == controlsVisible) return
        controlsVisible = show
        if (show) controlsView.visibility = View.VISIBLE
        controlsView.animate()
            .alpha(if (show) 1f else 0f)
            .setDuration(FADE_DURATION_MS)
            .withEndAction { if (!show) controlsView.visibility = View.INVISIBLE }
            .start()
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    fun applyBackgroundOpacity(opacityPercent: Int) {
        alpha = opacityPercent.coerceIn(0, 100) / 100f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MusicPlaybackHolder.addListener(this)
        if (MusicPlaybackHolder.getCurrentTrack() == null) {
            MusicPlaybackHolder.restoreQueueOnly(context)
        }
        val track = MusicPlaybackHolder.getCurrentTrack()
        updateTrackUi(track)
        updatePlayPauseButton(MusicPlaybackHolder.isPlaying())
        trySetupVisualizer()
        resetHideTimer()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(hideRunnable)
        MusicPlaybackHolder.removeListener(this)
        releaseVisualizer()
        scope.cancel()
        super.onDetachedFromWindow()
    }

    // ── Visualizer ────────────────────────────────────────────────────────────

    private fun trySetupVisualizer() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            showVisualizerFallback(false); return
        }
        val sessionId = MusicPlaybackHolder.getAudioSessionId()
        if (sessionId == 0) { showVisualizerFallback(false); return }
        try {
            releaseVisualizer()
            audioVisualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        waveform?.let { visualizerView.updateWaveform(it) }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        fft?.let { visualizerView.updateFft(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            showVisualizerFallback(true)
        } catch (_: Exception) { showVisualizerFallback(false) }
    }

    private fun showVisualizerFallback(active: Boolean) {
        visualizerView.visibility = if (active) View.VISIBLE else View.GONE
        bgView.visibility = if (active) View.GONE else View.VISIBLE
    }

    private fun releaseVisualizer() {
        try { audioVisualizer?.release() } catch (_: Exception) {}
        audioVisualizer = null
    }

    private fun applyVizMode(mode: AudioVisualizerView.VisualizationMode) {
        currentVizMode = mode
        visualizerView.mode = mode
        visualizerView.isFullscreen = mode.isCentral()
        prefs.edit { putString(KEY_VIZ_MODE, mode.name) }
    }

    private fun AudioVisualizerView.VisualizationMode.isCentral(): Boolean =
        this in setOf(
            AudioVisualizerView.VisualizationMode.FIRE,
            AudioVisualizerView.VisualizationMode.CIRCLE,
            AudioVisualizerView.VisualizationMode.PARTICLES,
            AudioVisualizerView.VisualizationMode.RADIAL,
            AudioVisualizerView.VisualizationMode.BLOB,
            AudioVisualizerView.VisualizationMode.PARTICLES_MONO,
            AudioVisualizerView.VisualizationMode.KALEIDOSCOPE,
            AudioVisualizerView.VisualizationMode.BOIDS,
            AudioVisualizerView.VisualizationMode.JULIA,
            AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM,
            AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE,
            AudioVisualizerView.VisualizationMode.DELAUNAY_MESH,
            AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE,
            AudioVisualizerView.VisualizationMode.VORONOI,
            AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE,
            AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS,
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE,
        )

    // ── Lyrics ────────────────────────────────────────────────────────────────

    private fun toggleLyrics() {
        if (lyricsVisible) {
            hideLyricsOverlay()
        } else {
            val track = MusicPlaybackHolder.getCurrentTrack() ?: return
            loadAndShowLyrics(track)
        }
    }

    private fun loadAndShowLyrics(track: MusicTrack) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            // Afficher un état de chargement
            lyricsOverlay.visibility = View.VISIBLE
            lyricsScrollView.visibility = View.GONE
            lyricsSyncLine.visibility = View.GONE
            lyricsSyncLine.text = "…"
            lyricsSyncLine.visibility = View.VISIBLE
            lyricsVisible = true
            lyricsBtn.setTextColor(0xFF3B82F6.toInt())

            val lyrics = withContext(Dispatchers.IO) {
                runCatching { LyricsManager.getLyrics(track) }.getOrNull()
            }

            if (lyrics == null) {
                // Pas de paroles trouvées
                lyricsSyncLine.text = context.getString(R.string.music_widget_no_lyrics)
                return@launch
            }

            if (LrcParser.isSynchronized(lyrics)) {
                isSyncedLyrics = true
                lyricsLines = LrcParser.parse(lyrics)
                lastSyncedLineIdx = -1
                lyricsSyncLine.visibility = View.VISIBLE
                lyricsScrollView.visibility = View.GONE
                // Afficher la ligne courante immédiatement
                updateSyncedLyrics(MusicPlaybackHolder.getPosition())
            } else {
                isSyncedLyrics = false
                lyricsLines = null
                lyricsTextView.text = lyrics
                lyricsScrollView.visibility = View.VISIBLE
                lyricsSyncLine.visibility = View.GONE
            }
        }
    }

    private fun hideLyricsOverlay() {
        lyricsJob?.cancel()
        lyricsVisible = false
        isSyncedLyrics = false
        lyricsLines = null
        lastSyncedLineIdx = -1
        lyricsOverlay.visibility = View.GONE
        lyricsBtn.setTextColor(0xFF64748B.toInt())
    }

    private fun updateSyncedLyrics(positionMs: Long) {
        val lines = lyricsLines ?: return
        if (lines.isEmpty()) return

        val idx = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        if (idx == lastSyncedLineIdx) return
        lastSyncedLineIdx = idx

        val prev = lines.getOrNull(idx - 1)?.text
        val curr = lines.getOrNull(idx)?.text ?: return
        val next = lines.getOrNull(idx + 1)?.text

        val sb = StringBuilder()
        if (prev != null) sb.append(prev).append('\n')
        sb.append(curr)
        if (next != null) sb.append('\n').append(next)
        lyricsSyncLine.text = sb.toString()
    }

    // ── Viz picker (PopupWindow attaché au bouton) ────────────────────────────

    private fun showVizPicker() {
        val density = context.resources.displayMetrics.density
        val itemHeightPx = (40 * density).roundToInt()
        val visibleItems = 6
        val listHeightPx = itemHeightPx * visibleItems
        val popupWidthPx = (200 * density).roundToInt()

        val popupView = LayoutInflater.from(context)
            .inflate(R.layout.popup_viz_picker, null, false)
        val listView = popupView.findViewById<ListView>(R.id.viz_picker_list)

        val adapter = VizModeAdapter()
        listView.adapter = adapter
        listView.dividerHeight = (1 * density).roundToInt()

        val currentIdx = VIZ_MODES.indexOfFirst { it.first == currentVizMode }.coerceAtLeast(0)
        listView.post { listView.setSelection(currentIdx) }

        val popup = PopupWindow(
            popupView,
            popupWidthPx,
            listHeightPx,
            true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            animationStyle = android.R.style.Animation_Dialog
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            applyVizMode(VIZ_MODES[position].first)
            adapter.notifyDataSetChanged()
        }

        popup.showAsDropDown(vizBtn, 0, 4)
    }

    private inner class VizModeAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)
        override fun getCount() = VIZ_MODES.size
        override fun getItem(position: Int) = VIZ_MODES[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_viz_picker, parent, false)
            val (mode, name) = VIZ_MODES[position]
            view.findViewById<TextView>(R.id.viz_item_number).text =
                "%02d".format(position + 1)
            view.findViewById<TextView>(R.id.viz_item_name).text = name
            val checkView = view.findViewById<TextView>(R.id.viz_item_check)
            checkView.visibility = if (mode == currentVizMode) View.VISIBLE else View.INVISIBLE
            view.setBackgroundColor(
                if (mode == currentVizMode) 0x22_3B_82_F6.toInt() else 0x00_00_00_00
            )
            return view
        }
    }

    // ── PlayerListener ────────────────────────────────────────────────────────

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        updatePlayPauseButton(isPlaying)
        if (isPlaying && audioVisualizer == null) post { trySetupVisualizer() }
    }

    override fun onTrackChanged(track: MusicTrack?) {
        updateTrackUi(track)
        post { trySetupVisualizer() }
        // Recharger les paroles si l'overlay est ouvert
        if (lyricsVisible) {
            if (track != null) loadAndShowLyrics(track) else hideLyricsOverlay()
        }
    }

    override fun onProgressChanged(position: Long, duration: Long) {
        if (lyricsVisible && isSyncedLyrics) {
            updateSyncedLyrics(position)
        }
    }

    override fun onError(error: PlaybackException) {}
    override fun onPlaylistChanged(playlist: List<MusicTrack>) {}
    override fun onPlayCountIncremented(track: MusicTrack, newCount: Long) {}

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun updateTrackUi(track: MusicTrack?) {
        if (track == null) {
            noMusicView.visibility = View.VISIBLE
            trackTitle.text = ""
            trackArtist.text = ""
            return
        }
        noMusicView.visibility = View.GONE
        trackTitle.text = track.title
        trackTitle.isSelected = true
        trackArtist.text = track.artist
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun openMusicLibrary() {
        context.startActivity(
            Intent(context, MusicPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MusicPlayerActivity.EXTRA_FROM_CLICKER_WIDGET, true)
        )
    }

    // ── Header drag ───────────────────────────────────────────────────────────

    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX; dragDownY = event.rawY
                dragLastX = event.rawX; dragLastY = event.rawY
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                if (isDragging || moved > tapThresholdPx) {
                    isDragging = true
                    translationX += event.rawX - dragLastX
                    translationY += event.rawY - dragLastY
                    dragLastX = event.rawX
                    dragLastY = event.rawY
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return false
            }
        }
        return false
    }
}
