package com.Atom2Universe.app.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.PlaybackException
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.music.adapter.QueueItemTouchHelper
import com.Atom2Universe.app.music.adapter.QueueTrackAdapter
import com.Atom2Universe.app.music.adapter.VizReorderAdapter
import com.Atom2Universe.app.music.lyrics.LyricsBottomSheet
import com.Atom2Universe.app.music.lyrics.LrcParser
import com.Atom2Universe.app.music.lyrics.LyricsAdapter
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.view.AudioVisualizerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.applySystemBarsVisibility
import com.Atom2Universe.app.music.equalizer.MusicEqualizerManager
import com.Atom2Universe.app.music.equalizer.ui.EqualizerFragment
import java.util.Locale

class FullPlayerActivity : ThemedActivity(), MusicPlaybackHolder.PlayerListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var albumArtContainer: CardView
    private lateinit var albumArt: ImageView
    private lateinit var visualizer: AudioVisualizerView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var trackAlbum: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var playCountText: TextView
    private lateinit var btnVizBars: TextView
    private lateinit var btnVizWave: TextView
    private lateinit var btnVizCircle: TextView
    private lateinit var btnVizMirror: TextView
    private lateinit var btnVizSpectrum: TextView
    private lateinit var btnVizParticles: TextView
    private lateinit var btnVizRadial: TextView
    private lateinit var btnVizBlob: TextView
    private lateinit var btnVizParticlesMono: TextView
    private var btnVizKaleidoscope: TextView? = null
    private var btnVizBoids: TextView? = null
    private var btnVizJulia: TextView? = null
    private var btnVizMandelbrot: TextView? = null
    private var btnVizJuliaGrayscale: TextView? = null
    private var btnVizDelaunay: TextView? = null
    private var btnVizDelaunayGrayscale: TextView? = null
    private var btnVizVoronoi: TextView? = null
    private var btnVizVoronoiGrayscale: TextView? = null
    private var btnVizPenrose: TextView? = null
    private var btnVizPenroseTrue: TextView? = null
    private lateinit var btnVizFire: TextView

    // Queue views
    private lateinit var queueList: RecyclerView
    private lateinit var queueTitle: TextView
    private lateinit var queueCount: TextView
    private lateinit var queueAdapter: QueueTrackAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var btnSaveQueueAsPlaylist: ImageButton
    private lateinit var btnEditPlaylistMode: ImageButton

    private var audioVisualizer: Visualizer? = null
    private var isUserSeeking = false
    private var currentTrackId: Long = -1

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
        private const val PREFS_NAME = "music_player_prefs"
        private const val PREF_VISUALIZER_MODE = "visualizer_mode"
        private const val PREF_SHOW_ALBUM_ART = "show_album_art"
        private const val FULLSCREEN_CONTROLS_HIDE_DELAY = 5000L // 5 secondes
    }

    private lateinit var prefs: SharedPreferences
    private var currentVisualizerMode: AudioVisualizerView.VisualizationMode = AudioVisualizerView.VisualizationMode.BARS
    private var isAlbumArtVisible: Boolean = true
    private lateinit var btnToggleAlbumArt: ImageButton
    private lateinit var btnLyrics: ImageButton
    private lateinit var visualizerModes: View
    private lateinit var btnEqualizer: ImageButton

    // Lyrics overlay
    private lateinit var lyricsOverlayContainer: View
    private lateinit var lyricsRecycler: RecyclerView
    private lateinit var checkboxAutoScroll: CheckBox
    private lateinit var lyricsLoading: ProgressBar
    // Lyrics overlay pour mode fullscreen (modes 6-12)
    private lateinit var lyricsOverlayFullscreen: View
    private lateinit var lyricsRecyclerFullscreen: RecyclerView
    private lateinit var checkboxAutoScrollFullscreen: CheckBox
    private lateinit var lyricsLoadingFullscreen: ProgressBar
    private var lyricsAdapter: LyricsAdapter? = null
    private var lyricsLines: List<LrcParser.LyricLine>? = null
    private var currentLyrics: String? = null
    private var isSyncedLyrics: Boolean = false
    private var lyricsEnabledInFullscreen: Boolean = false  // Persiste au changement de piste

    // True fullscreen mode (modes 6-12 only)
    private lateinit var btnFullscreen: ImageButton
    private lateinit var trueFullscreenContainer: View
    private lateinit var visualizerFullscreen: AudioVisualizerView
    private lateinit var btnExitFullscreen: ImageButton
    private lateinit var btnLyricsFullscreen: ImageButton
    private lateinit var lyricsOverlayTrueFullscreen: View
    private lateinit var lyricsRecyclerTrueFullscreen: RecyclerView
    private lateinit var checkboxAutoScrollTrueFullscreen: CheckBox
    private lateinit var lyricsLoadingTrueFullscreen: ProgressBar
    private lateinit var btnVizFireFs: TextView
    private lateinit var btnVizCircleFs: TextView
    private lateinit var btnVizParticlesFs: TextView
    private lateinit var btnVizRadialFs: TextView
    private lateinit var btnVizBlobFs: TextView
    private lateinit var btnVizParticlesMonoFs: TextView
    private lateinit var btnVizKaleidoscopeFs: TextView
    private lateinit var btnVizBoidsFs: TextView
    private lateinit var btnVizJuliaFs: TextView
    private lateinit var btnVizMandelbrotFs: TextView
    private var btnVizJuliaGrayscaleFs: TextView? = null
    private var btnVizDelaunayFs: TextView? = null
    private var btnVizDelaunayGrayscaleFs: TextView? = null
    private var btnVizVoronoiFs: TextView? = null
    private var btnVizVoronoiGrayscaleFs: TextView? = null
    private var btnVizPenroseFs: TextView? = null
    private var btnVizPenroseTrueFs: TextView? = null
    private var isTrueFullscreen: Boolean = false

    // Fullscreen controls
    private lateinit var fullscreenTopBar: View
    private lateinit var fullscreenBottomBar: View
    private lateinit var fullscreenTrackTitle: TextView
    private lateinit var fullscreenBtnPrev: ImageButton
    private lateinit var fullscreenBtnPlayPause: ImageButton
    private lateinit var fullscreenBtnNext: ImageButton
    private lateinit var fullscreenSeekBar: SeekBar
    private lateinit var fullscreenCurrentTime: TextView
    private lateinit var fullscreenTotalTime: TextView
    private var areFullscreenControlsVisible: Boolean = true

    private val hideControlsRunnable = Runnable {
        hideFullscreenControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_full_player)

        // Initialiser les SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        setupToolbar()
        setupControls()
        setupVisualizerModes()
        applyVisualizerOrder()
        setupSeekBar()
        setupQueue()
        setupBackPress()
        setupSwipeToClose()
        setupLyricsResultListener()

        // Observer l'état de l'égaliseur pour mettre à jour le bouton
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MusicEqualizerManager.isEnabled.collect { _ ->
                    updateEqualizerButton()
                }
            }
        }

        // Charger le mode visualiseur sauvegardé
        loadVisualizerMode()

        // Charger la visibilité de la pochette
        loadAlbumArtVisibility()

        // Restaurer la queue si nécessaire (après kill de l'app)
        if (MusicPlaybackHolder.getCurrentTrack() == null) {
            MusicPlaybackHolder.restoreQueueOnly(this)
        }

        refreshPlaybackState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Rafraîchir l'état quand singleTask ramène l'activité au premier plan
        // (ex. nouvelle piste chargée depuis un fichier externe ou le widget)
        refreshPlaybackState()
    }

    override fun onStart() {
        super.onStart()
        MusicPlaybackHolder.addListener(this)
        refreshPlaybackState()
        handler.post(updateProgressRunnable)
        checkAudioPermissionAndSetupVisualizer()
    }

    override fun onStop() {
        super.onStop()
        MusicPlaybackHolder.removeListener(this)
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        releaseVisualizer()
    }

    override fun onDestroy() {
        // S'assurer que le handler runnable est bien retiré même si onStop n'a pas été appelé
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        // Bug 5.13: Release visualizer also in onDestroy to ensure cleanup
        // even if onStop wasn't called (e.g., configuration change)
        releaseVisualizer()
        // Nettoyer le flag KEEP_SCREEN_ON
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Rafraîchir les favoris en cas de sync cloud
        refreshFavoritesAfterSync()
        // Mettre à jour le bouton d'édition de playlist
        updateEditModeButton()

        // Bug 5.23: Re-vérifier la permission RECORD_AUDIO au retour de l'activité.
        // Si l'utilisateur a accordé la permission dans les Settings après un refus initial,
        // on peut maintenant activer le visualizer.
        if (audioVisualizer == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupAudioVisualizer()
        }
    }

    /**
     * Rafraîchit l'affichage des favoris après une potentielle sync cloud.
     */
    private fun refreshFavoritesAfterSync() {
        if (!::queueAdapter.isInitialized) return

        lifecycleScope.launch {
            // Recharger le cache des favoris depuis le fichier
            MusicFavoritesManager.invalidateCache()
            MusicFavoritesManager.loadFavorites(this@FullPlayerActivity)

            // Mettre à jour les favoris dans la queue
            val playlist = MusicPlaybackHolder.getPlaylist()
            val favoriteIds = playlist.filter { MusicFavoritesManager.isFavorite(it) }
                .map { it.id }
                .toSet()
            queueAdapter.setFavorites(favoriteIds)

            // Mettre à jour aussi le bouton favori et le play count du track actuel
            val currentTrack = MusicPlaybackHolder.getCurrentTrack()
            if (currentTrack != null) {
                updateFavoriteButton(currentTrack)
                updatePlayCount(currentTrack)
            }

            // Rafraîchir les play counts dans la queue (peuvent avoir changé pendant que
            // l'écran était éteint ou l'activité en arrière-plan)
            if (queueAdapter.itemCount > 0) {
                queueAdapter.notifyItemRangeChanged(0, queueAdapter.itemCount)
            }
        }
    }

    private fun refreshPlaybackState() {
        MusicPlaybackHolder.getCurrentTrack()?.let { updateTrackInfo(it) }
        updatePlayPauseButton(MusicPlaybackHolder.isPlaying())
        updateShuffleButton()
        updateRepeatButton()
        updateEqualizerButton()
        updateQueue()
        updateEditModeButton()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        albumArtContainer = findViewById(R.id.album_art_container)
        albumArt = findViewById(R.id.album_art)
        visualizer = findViewById(R.id.visualizer)
        trackTitle = findViewById(R.id.track_title)
        trackArtist = findViewById(R.id.track_artist)
        trackAlbum = findViewById(R.id.track_album)
        trackTitle.isSelected = true
        trackArtist.isSelected = true
        trackAlbum.isSelected = true
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnRepeat = findViewById(R.id.btn_repeat)
        btnFavorite = findViewById(R.id.btn_favorite)
        playCountText = findViewById(R.id.play_count)
        btnVizBars = findViewById(R.id.btn_viz_bars)
        btnVizWave = findViewById(R.id.btn_viz_wave)
        btnVizCircle = findViewById(R.id.btn_viz_circle)
        btnVizMirror = findViewById(R.id.btn_viz_mirror)
        btnVizSpectrum = findViewById(R.id.btn_viz_spectrum)
        btnVizParticles = findViewById(R.id.btn_viz_particles)
        btnVizRadial = findViewById(R.id.btn_viz_radial)
        btnVizBlob = findViewById(R.id.btn_viz_blob)
        btnVizParticlesMono = findViewById(R.id.btn_viz_particles_mono)
        btnVizKaleidoscope = findViewById(R.id.btn_viz_kaleidoscope) // Peut être null si layout pas rebuild
        btnVizBoids = findViewById(R.id.btn_viz_boids) // Peut être null si layout pas rebuild
        btnVizJulia = findViewById(R.id.btn_viz_julia) // Peut être null si layout pas rebuild
        btnVizMandelbrot = findViewById(R.id.btn_viz_mandelbrot) // Peut être null si layout pas rebuild
        btnVizJuliaGrayscale = findViewById(R.id.btn_viz_julia_grayscale) // Peut être null si layout pas rebuild
        btnVizDelaunay = findViewById(R.id.btn_viz_delaunay)
        btnVizDelaunayGrayscale = findViewById(R.id.btn_viz_delaunay_grayscale)
        btnVizVoronoi = findViewById(R.id.btn_viz_voronoi)
        btnVizVoronoiGrayscale = findViewById(R.id.btn_viz_voronoi_grayscale)
        btnVizPenrose = findViewById(R.id.btn_viz_penrose)
        btnVizPenroseTrue = findViewById(R.id.btn_viz_penrose_true)
        btnVizFire = findViewById(R.id.btn_viz_fire)
        queueList = findViewById(R.id.queue_list)
        queueTitle = findViewById(R.id.queue_title)
        queueCount = findViewById(R.id.queue_count)
        btnSaveQueueAsPlaylist = findViewById(R.id.btn_save_queue_as_playlist)
        btnEditPlaylistMode = findViewById(R.id.btn_edit_playlist_mode)
        btnToggleAlbumArt = findViewById(R.id.btn_toggle_album_art)
        btnLyrics = findViewById(R.id.btn_lyrics)
        visualizerModes = findViewById(R.id.visualizer_modes)
        btnEqualizer = findViewById(R.id.btn_equalizer)

        // Lyrics overlay (normal - sur pochette)
        lyricsOverlayContainer = findViewById(R.id.lyrics_overlay_container)
        lyricsRecycler = findViewById(R.id.lyrics_recycler)
        lyricsRecycler.layoutManager = LinearLayoutManager(this)
        checkboxAutoScroll = findViewById(R.id.checkbox_auto_scroll)
        lyricsLoading = findViewById(R.id.lyrics_loading)

        // Lyrics overlay fullscreen (modes 6-12 en fullscreen)
        lyricsOverlayFullscreen = findViewById(R.id.lyrics_overlay_fullscreen)
        lyricsRecyclerFullscreen = findViewById(R.id.lyrics_recycler_fullscreen)
        lyricsRecyclerFullscreen.layoutManager = LinearLayoutManager(this)
        checkboxAutoScrollFullscreen = findViewById(R.id.checkbox_auto_scroll_fullscreen)
        lyricsLoadingFullscreen = findViewById(R.id.lyrics_loading_fullscreen)

        // Auto-scroll checkbox listener (normal)
        checkboxAutoScroll.setOnCheckedChangeListener { _, isChecked ->
            lyricsAdapter?.setAutoScrollEnabled(isChecked)
            MusicPreferences.getInstance(this).lyricsAutoScroll = isChecked
        }

        // Auto-scroll checkbox listener (fullscreen)
        checkboxAutoScrollFullscreen.setOnCheckedChangeListener { _, isChecked ->
            lyricsAdapter?.setAutoScrollEnabled(isChecked)
            MusicPreferences.getInstance(this).lyricsAutoScroll = isChecked
        }

        // True fullscreen mode views
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        trueFullscreenContainer = findViewById(R.id.true_fullscreen_container)
        visualizerFullscreen = findViewById(R.id.visualizer_fullscreen)
        btnExitFullscreen = findViewById(R.id.btn_exit_fullscreen)
        btnLyricsFullscreen = findViewById(R.id.btn_lyrics_fullscreen)
        lyricsOverlayTrueFullscreen = findViewById(R.id.lyrics_overlay_true_fullscreen)
        lyricsRecyclerTrueFullscreen = findViewById(R.id.lyrics_recycler_true_fullscreen)
        lyricsRecyclerTrueFullscreen.layoutManager = LinearLayoutManager(this)
        checkboxAutoScrollTrueFullscreen = findViewById(R.id.checkbox_auto_scroll_true_fullscreen)
        lyricsLoadingTrueFullscreen = findViewById(R.id.lyrics_loading_true_fullscreen)
        btnVizFireFs = findViewById(R.id.btn_viz_fire_fs)
        btnVizCircleFs = findViewById(R.id.btn_viz_circle_fs)
        btnVizParticlesFs = findViewById(R.id.btn_viz_particles_fs)
        btnVizRadialFs = findViewById(R.id.btn_viz_radial_fs)
        btnVizBlobFs = findViewById(R.id.btn_viz_blob_fs)
        btnVizParticlesMonoFs = findViewById(R.id.btn_viz_particles_mono_fs)
        btnVizKaleidoscopeFs = findViewById(R.id.btn_viz_kaleidoscope_fs)
        btnVizBoidsFs = findViewById(R.id.btn_viz_boids_fs)
        btnVizJuliaFs = findViewById(R.id.btn_viz_julia_fs)
        btnVizMandelbrotFs = findViewById(R.id.btn_viz_mandelbrot_fs)
        btnVizJuliaGrayscaleFs = findViewById(R.id.btn_viz_julia_grayscale_fs)
        btnVizDelaunayFs = findViewById(R.id.btn_viz_delaunay_fs)
        btnVizDelaunayGrayscaleFs = findViewById(R.id.btn_viz_delaunay_grayscale_fs)
        btnVizVoronoiFs = findViewById(R.id.btn_viz_voronoi_fs)
        btnVizVoronoiGrayscaleFs = findViewById(R.id.btn_viz_voronoi_grayscale_fs)
        btnVizPenroseFs = findViewById(R.id.btn_viz_penrose_fs)
        btnVizPenroseTrueFs = findViewById(R.id.btn_viz_penrose_true_fs)

        // Fullscreen controls
        fullscreenTopBar = findViewById(R.id.fullscreen_top_bar)
        fullscreenBottomBar = findViewById(R.id.fullscreen_bottom_bar)
        fullscreenTrackTitle = findViewById(R.id.fullscreen_track_title)
        fullscreenBtnPrev = findViewById(R.id.fullscreen_btn_prev)
        fullscreenBtnPlayPause = findViewById(R.id.fullscreen_btn_play_pause)
        fullscreenBtnNext = findViewById(R.id.fullscreen_btn_next)
        fullscreenSeekBar = findViewById(R.id.fullscreen_seek_bar)
        fullscreenCurrentTime = findViewById(R.id.fullscreen_current_time)
        fullscreenTotalTime = findViewById(R.id.fullscreen_total_time)

        // Auto-scroll checkbox listener (true fullscreen)
        checkboxAutoScrollTrueFullscreen.setOnCheckedChangeListener { _, isChecked ->
            lyricsAdapter?.setAutoScrollEnabled(isChecked)
            MusicPreferences.getInstance(this).lyricsAutoScroll = isChecked
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { navigateBackToHub() }
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            MusicPlaybackHolder.togglePlayPause(this)
        }

        btnPrev.setOnClickListener {
            MusicPlaybackHolder.skipToPrevious(this)
        }

        btnNext.setOnClickListener {
            MusicPlaybackHolder.skipToNext(this)
        }

        btnShuffle.setOnClickListener {
            MusicPlaybackHolder.toggleShuffle()
            updateShuffleButton()
        }

        btnRepeat.setOnClickListener {
            MusicPlaybackHolder.toggleRepeat()
            updateRepeatButton()
        }

        btnFavorite.setOnClickListener {
            MusicPlaybackHolder.getCurrentTrack()?.let { track ->
                toggleFavorite(track)
            }
        }

        btnToggleAlbumArt.setOnClickListener {
            toggleAlbumArtVisibility()
        }

        btnLyrics.setOnClickListener {
            MusicPlaybackHolder.getCurrentTrack()?.let { track ->
                // Toggle lyrics overlay visibility (tous les containers)
                if (lyricsOverlayContainer.isVisible || lyricsOverlayFullscreen.isVisible || lyricsOverlayTrueFullscreen.isVisible) {
                    lyricsEnabledInFullscreen = false
                    hideLyricsOverlay()
                } else {
                    loadAndShowLyrics(track)
                }
            }
        }

        // Long press to always open bottom sheet for editing
        btnLyrics.setOnLongClickListener {
            MusicPlaybackHolder.getCurrentTrack()?.let { track ->
                hideLyricsOverlay()
                val bottomSheet = LyricsBottomSheet.newInstance(track)
                bottomSheet.show(supportFragmentManager, "lyrics_bottom_sheet")
            }
            true
        }

        btnSaveQueueAsPlaylist.setOnClickListener {
            showSaveQueueAsPlaylistDialog()
        }

        btnEditPlaylistMode.setOnClickListener {
            togglePlaylistEditMode()
        }

        // Equalizer button
        btnEqualizer.setOnClickListener {
            showEqualizerBottomSheet()
        }

        // Fullscreen button
        btnFullscreen.setOnClickListener {
            enterTrueFullscreen()
        }

        // Exit fullscreen button
        btnExitFullscreen.setOnClickListener {
            exitTrueFullscreen()
        }

        // Lyrics button in fullscreen mode
        btnLyricsFullscreen.setOnClickListener {
            MusicPlaybackHolder.getCurrentTrack()?.let { track ->
                if (lyricsOverlayTrueFullscreen.isVisible) {
                    hideLyricsOverlay()
                } else {
                    loadAndShowLyrics(track)
                }
            }
        }

        // Fullscreen bottom controls
        fullscreenBtnPlayPause.setOnClickListener {
            MusicPlaybackHolder.togglePlayPause(this)
            resetFullscreenControlsHideTimer()
        }

        fullscreenBtnPrev.setOnClickListener {
            MusicPlaybackHolder.skipToPrevious(this)
            resetFullscreenControlsHideTimer()
        }

        fullscreenBtnNext.setOnClickListener {
            MusicPlaybackHolder.skipToNext(this)
            resetFullscreenControlsHideTimer()
        }
    }

    private fun showEqualizerBottomSheet() {
        val equalizerFragment = EqualizerFragment.newInstance()
        equalizerFragment.show(supportFragmentManager, EqualizerFragment.TAG)

        // Update button color when the bottom sheet is dismissed
        supportFragmentManager.setFragmentResultListener(
            EqualizerFragment.TAG,
            this
        ) { _, _ ->
            updateEqualizerButton()
        }
    }

    private fun setupLyricsResultListener() {
        supportFragmentManager.setFragmentResultListener(
            LyricsBottomSheet.RESULT_KEY,
            this
        ) { _, bundle ->
            val updatedTrackId = bundle.getLong(LyricsBottomSheet.RESULT_TRACK_ID, -1L)
            val lyrics = bundle.getString(LyricsBottomSheet.RESULT_LYRICS)
            val currentTrack = MusicPlaybackHolder.getCurrentTrack() ?: return@setFragmentResultListener

            if (currentTrack.id != updatedTrackId) return@setFragmentResultListener

            // Afficher directement les paroles sauvegardées sans recharger depuis le cache
            if (isAlbumArtVisible && lyrics != null && lyrics.isNotBlank()) {
                displayLyricsInOverlay(lyrics)
            }
        }
    }

    private fun togglePlaylistEditMode() {
        val newState = MusicPlaybackHolder.togglePlaylistEditMode()
        updateEditModeButton()

        val messageRes = if (newState) {
            R.string.music_edit_mode_enabled
        } else {
            R.string.music_edit_mode_disabled
        }
        Snackbar.make(queueList, messageRes, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateEditModeButton() {
        // Show button only if we're playing from a custom playlist
        val playlistId = MusicPlaybackHolder.currentPlaylistId
        val hasPlaylist = playlistId != null

        btnEditPlaylistMode.visibility = if (hasPlaylist) View.VISIBLE else View.GONE

        // Update button color based on state
        val tintColor = if (MusicPlaybackHolder.isPlaylistEditModeEnabled) {
            ContextCompat.getColor(this, R.color.music_accent)
        } else {
            ContextCompat.getColor(this, R.color.music_text_secondary)
        }
        btnEditPlaylistMode.imageTintList = ColorStateList.valueOf(tintColor)
    }

    /**
     * Synchronise les modifications de la queue vers la playlist si le mode édition est activé
     */
    private fun syncQueueToPlaylistIfNeeded() {
        if (!MusicPlaybackHolder.isPlaylistEditModeEnabled) return

        val playlistId = MusicPlaybackHolder.currentPlaylistId ?: return
        val currentTracks = queueAdapter.getTracks()

        lifecycleScope.launch {
            MusicPlaylistManager.updatePlaylistTracks(playlistId, currentTracks)
        }
    }

    private fun showSaveQueueAsPlaylistDialog() {
        val queue = MusicPlaybackHolder.getPlaylist()
        if (queue.isEmpty()) {
            Snackbar.make(queueList, R.string.music_playlist_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_name, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_save_queue_as_playlist)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        // Create the playlist
                        val playlistId = MusicPlaylistManager.createPlaylist(name)

                        // Add all tracks from the queue
                        queue.forEach { track ->
                            MusicPlaylistManager.addTrackToPlaylist(playlistId, track)
                        }

                        // Set as current playlist so edit mode can work
                        MusicPlaybackHolder.setCurrentPlaylistId(playlistId)
                        updateEditModeButton()
                        updateQueueCount()

                        Snackbar.make(
                            queueList,
                            getString(R.string.music_queue_saved_as_playlist, name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun toggleAlbumArtVisibility() {
        isAlbumArtVisible = !isAlbumArtVisible
        saveAlbumArtVisibility()
        updateAlbumArtVisibility()
    }

    private fun loadAlbumArtVisibility() {
        isAlbumArtVisible = prefs.getBoolean(PREF_SHOW_ALBUM_ART, true)
        updateAlbumArtVisibility()
    }

    private fun saveAlbumArtVisibility() {
        prefs.edit {
            putBoolean(PREF_SHOW_ALBUM_ART, isAlbumArtVisible)
        }
    }

    private fun updateAlbumArtVisibility() {
        // Update album art container visibility
        albumArtContainer.visibility = if (isAlbumArtVisible) View.VISIBLE else View.GONE

        // Update toggle button icon
        val iconRes = if (isAlbumArtVisible) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        btnToggleAlbumArt.setImageResource(iconRes)

        // Update visualizer visibility based on mode
        updateVisualizerVisibility()
    }

    private fun updateVisualizerVisibility() {
        // Visualizer is visible only if it has a mode other than NONE
        val isVisualizerEnabled = currentVisualizerMode != AudioVisualizerView.VisualizationMode.NONE
        visualizer.visibility = if (isVisualizerEnabled) View.VISIBLE else View.GONE

        // Check if we should use fullscreen mode (central visualizer + album art hidden)
        val isCentralMode = visualizer.isCentralMode()
        val shouldBeFullscreen = !isAlbumArtVisible && isCentralMode && isVisualizerEnabled

        // Update fullscreen state
        visualizer.isFullscreen = shouldBeFullscreen

        // Update constraints based on fullscreen mode
        val parent = visualizer.parent as? ConstraintLayout ?: return
        val constraintSet = ConstraintSet()
        constraintSet.clone(parent)

        if (shouldBeFullscreen) {
            // Fullscreen mode: visualizer prend toute la largeur, hauteur = celle de la pochette
            // Connect to toolbar bottom instead of album_art_container bottom
            constraintSet.connect(
                R.id.visualizer,
                ConstraintSet.TOP,
                R.id.toolbar,
                ConstraintSet.BOTTOM,
                (8 * resources.displayMetrics.density).toInt() // 8dp margin
            )
            // Largeur complète (start et end au parent avec petite marge)
            constraintSet.connect(
                R.id.visualizer,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                (16 * resources.displayMetrics.density).toInt()
            )
            constraintSet.connect(
                R.id.visualizer,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                (16 * resources.displayMetrics.density).toInt()
            )
            // Hauteur fixe comme la pochette (350dp max)
            constraintSet.constrainHeight(R.id.visualizer, (350 * resources.displayMetrics.density).toInt())
            // Pas de ratio, pas de max width
            constraintSet.setDimensionRatio(R.id.visualizer, null)
            constraintSet.constrainMaxWidth(R.id.visualizer, 0)
            constraintSet.constrainMaxHeight(R.id.visualizer, 0)
        } else {
            // Normal mode: visualizer below album art container with fixed height
            constraintSet.connect(
                R.id.visualizer,
                ConstraintSet.TOP,
                R.id.album_art_container,
                ConstraintSet.BOTTOM,
                (16 * resources.displayMetrics.density).toInt() // 16dp margin
            )
            // Fixed height of 80dp
            constraintSet.constrainHeight(R.id.visualizer, (80 * resources.displayMetrics.density).toInt())
            constraintSet.setDimensionRatio(R.id.visualizer, null)
            constraintSet.constrainMaxWidth(R.id.visualizer, ConstraintSet.WRAP_CONTENT)
            constraintSet.constrainMaxHeight(R.id.visualizer, ConstraintSet.WRAP_CONTENT)
        }

        constraintSet.applyTo(parent)

        // Update fullscreen button visibility
        // Show only when in pseudo-fullscreen (modes 6-12, album art hidden)
        // and NOT in true fullscreen mode
        val shouldShowFullscreenButton = shouldBeFullscreen && !isTrueFullscreen
        btnFullscreen.visibility = if (shouldShowFullscreenButton) View.VISIBLE else View.GONE

        // Rafraîchir les lyrics pour basculer entre normal/fullscreen
        refreshLyricsIfVisible()
    }

    private fun toggleFavorite(track: MusicTrack) {
        lifecycleScope.launch {
            val newState = MusicFavoritesManager.toggleFavorite(track)
            updateFavoriteButton(track, newState)
            // Also update the queue list
            queueAdapter.updateFavorite(track.id, newState)

            val messageRes = if (newState) {
                R.string.music_added_to_favorites
            } else {
                R.string.music_removed_from_favorites
            }
            Snackbar.make(queueList, messageRes, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun toggleQueueTrackFavorite(track: MusicTrack) {
        lifecycleScope.launch {
            val newState = MusicFavoritesManager.toggleFavorite(track)
            queueAdapter.updateFavorite(track.id, newState)

            // Also update the main favorite button if it's the current track
            val currentTrack = MusicPlaybackHolder.getCurrentTrack()
            if (currentTrack?.id == track.id) {
                updateFavoriteButton(track, newState)
            }

            val messageRes = if (newState) {
                R.string.music_added_to_favorites
            } else {
                R.string.music_removed_from_favorites
            }
            Snackbar.make(queueList, messageRes, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupQueue() {
        queueAdapter = QueueTrackAdapter(
            onTrackClick = { _, position ->
                MusicPlaybackHolder.playAtIndex(this, position)
            },
            onDragStarted = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onFavoriteClick = { track ->
                toggleQueueTrackFavorite(track)
            }
        )
        val preferences = MusicPreferences.getInstance(this)
        queueAdapter.setShowPlayCount(preferences.showPlayCount)

        queueList.apply {
            layoutManager = LinearLayoutManager(this@FullPlayerActivity)
            adapter = queueAdapter
        }

        val touchCallback = QueueItemTouchHelper(
            adapter = queueAdapter,
            onItemMoved = {
                // Update playlist order in MusicPlaybackHolder when reorder is complete
                // Use updatePlaylistOrder to preserve the currently playing track
                MusicPlaybackHolder.updatePlaylistOrder(queueAdapter.getTracks())
                updateQueueCount()
                // Sync to playlist if edit mode is enabled
                syncQueueToPlaylistIfNeeded()
            },
            onItemRemoved = { position ->
                val removed = queueAdapter.removeItem(position)
                if (removed != null) {
                    // Update playlist in MusicPlaybackHolder
                    if (!MusicPlaybackHolder.removeTrackFromQueue(this, removed)) {
                        MusicPlaybackHolder.updatePlaylistOrder(queueAdapter.getTracks())
                    }
                    updateQueueCount()
                    // Sync to playlist if edit mode is enabled
                    syncQueueToPlaylistIfNeeded()
                    // Show appropriate message
                    val messageRes = if (MusicPlaybackHolder.isPlaylistEditModeEnabled && MusicPlaybackHolder.currentPlaylistId != null) {
                        R.string.music_track_removed_from_queue_and_playlist
                    } else {
                        R.string.music_track_removed_from_queue
                    }
                    Snackbar.make(queueList, messageRes, Snackbar.LENGTH_SHORT).show()
                }
            }
        )
        itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(queueList)
    }

    private fun updateQueue() {
        if (queueList.isComputingLayout) {
            queueList.post { updateQueue() }
            return
        }

        val playlist = MusicPlaybackHolder.getPlaylist()
        val currentIndex = MusicPlaybackHolder.getCurrentIndex()

        queueAdapter.setTracks(playlist)
        queueAdapter.setCurrentlyPlayingIndex(currentIndex)

        // Set favorites for all tracks in the queue
        val favoriteIds = playlist.filter { MusicFavoritesManager.isFavorite(it) }
            .map { it.id }
            .toSet()
        queueAdapter.setFavorites(favoriteIds)

        updateQueueCount()

        // Scroll to current track
        if (currentIndex >= 0) {
            queueList.post { queueList.scrollToPosition(currentIndex) }
        }
    }

    private fun updateQueueCount() {
        val count = queueAdapter.itemCount
        queueCount.text = getString(R.string.music_queue_count, count)

        // Update title based on whether we're playing from a playlist
        val playlistId = MusicPlaybackHolder.currentPlaylistId
        if (playlistId != null) {
            val playlist = MusicPlaylistManager.getPlaylist(playlistId)
            queueTitle.text = playlist?.name ?: getString(R.string.music_queue)
        } else {
            queueTitle.text = getString(R.string.music_queue)
        }
    }

    private fun setupVisualizerModes() {
        // Modes gratuits (1-4)
        btnVizBars.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.BARS)
        }

        btnVizWave.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.WAVE)
        }

        btnVizMirror.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.MIRROR)
        }

        btnVizSpectrum.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.SPECTRUM)
        }

        btnVizFire.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.FIRE)
        }

        btnVizCircle.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.CIRCLE)
        }

        btnVizParticles.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PARTICLES)
        }

        btnVizRadial.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.RADIAL)
        }

        btnVizBlob.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.BLOB)
        }

        btnVizParticlesMono.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PARTICLES_MONO)
        }

        btnVizKaleidoscope?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.KALEIDOSCOPE)
        }

        btnVizBoids?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.BOIDS)
        }

        btnVizJulia?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.JULIA)
        }

        btnVizMandelbrot?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM)
        }

        btnVizJuliaGrayscale?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE)
        }

        btnVizDelaunay?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.DELAUNAY_MESH)
        }

        btnVizDelaunayGrayscale?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE)
        }

        btnVizVoronoi?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.VORONOI)
        }

        btnVizVoronoiGrayscale?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE)
        }

        btnVizPenrose?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS)
        }

        btnVizPenroseTrue?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PENROSE_TRUE)
        }

        // Fullscreen mode buttons (5-19)
        btnVizFireFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.FIRE)
        }

        btnVizCircleFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.CIRCLE)
        }

        btnVizParticlesFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PARTICLES)
        }

        btnVizRadialFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.RADIAL)
        }

        btnVizBlobFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.BLOB)
        }

        btnVizParticlesMonoFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PARTICLES_MONO)
        }

        btnVizKaleidoscopeFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.KALEIDOSCOPE)
        }

        btnVizBoidsFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.BOIDS)
        }

        btnVizJuliaFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.JULIA)
        }

        btnVizMandelbrotFs.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM)
        }

        btnVizJuliaGrayscaleFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE)
        }

        btnVizDelaunayFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.DELAUNAY_MESH)
        }

        btnVizDelaunayGrayscaleFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE)
        }

        btnVizVoronoiFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.VORONOI)
        }

        btnVizVoronoiGrayscaleFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE)
        }

        btnVizPenroseFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS)
        }

        btnVizPenroseTrueFs?.setOnClickListener {
            toggleVisualizerMode(AudioVisualizerView.VisualizationMode.PENROSE_TRUE)
        }

        findViewById<ImageView>(R.id.btn_reorder_visuals)?.setOnClickListener {
            showVizReorderDialog()
        }
        findViewById<ImageView>(R.id.btn_reorder_visuals_fs)?.setOnClickListener {
            showVizReorderDialog()
        }
    }

    private fun toggleVisualizerMode(mode: AudioVisualizerView.VisualizationMode) {
        // Si on clique sur le mode actuellement actif, on désactive le visualiseur
        val newMode = if (currentVisualizerMode == mode) {
            AudioVisualizerView.VisualizationMode.NONE
        } else {
            mode
        }
        setVisualizerMode(newMode)
    }

    private fun setVisualizerMode(mode: AudioVisualizerView.VisualizationMode) {
        currentVisualizerMode = mode
        visualizer.mode = mode
        saveVisualizerMode(mode)
        updateVisualizerButtons()
        updateVisualizerVisibility()

        // Si on est en mode vrai fullscreen, synchroniser aussi le visualiseur fullscreen
        if (isTrueFullscreen) {
            visualizerFullscreen.mode = mode
            updateFullscreenVisualizerButtons()
        }

        // Rafraîchir les lyrics si affichées (pour basculer entre normal/fullscreen)
        refreshLyricsIfVisible()
    }

    private fun updateVisualizerButtons() {
        val accentColor = MaterialColors.getColor(
            this,
            R.attr.a2uMusicAccent,
            ContextCompat.getColor(this, R.color.music_accent)
        )
        val secondaryColor = ContextCompat.getColor(this, R.color.music_text_secondary)

        // Liste de tous les boutons avec leurs modes
        val buttons = buildList {
            add(btnVizBars to AudioVisualizerView.VisualizationMode.BARS)       // 1
            add(btnVizWave to AudioVisualizerView.VisualizationMode.WAVE)       // 2
            add(btnVizMirror to AudioVisualizerView.VisualizationMode.MIRROR)   // 3
            add(btnVizSpectrum to AudioVisualizerView.VisualizationMode.SPECTRUM) // 4
            add(btnVizFire to AudioVisualizerView.VisualizationMode.FIRE)       // 5
            add(btnVizCircle to AudioVisualizerView.VisualizationMode.CIRCLE)   // 6
            add(btnVizParticles to AudioVisualizerView.VisualizationMode.PARTICLES) // 7
            add(btnVizRadial to AudioVisualizerView.VisualizationMode.RADIAL)   // 8
            add(btnVizBlob to AudioVisualizerView.VisualizationMode.BLOB)       // 9
            add(btnVizParticlesMono to AudioVisualizerView.VisualizationMode.PARTICLES_MONO) // 10
            // Ajouter boutons 11-16 seulement s'ils existent (pour compatibilité avant rebuild)
            btnVizKaleidoscope?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.KALEIDOSCOPE) // 11
            }
            btnVizBoids?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.BOIDS) // 12
            }
            btnVizJulia?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.JULIA) // 13
            }
            btnVizMandelbrot?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM) // 14
            }
            btnVizJuliaGrayscale?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE) // 15
            }
            btnVizDelaunay?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.DELAUNAY_MESH) // 16
            }
            btnVizDelaunayGrayscale?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE) // 17
            }
            btnVizVoronoi?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.VORONOI) // 18
            }
            btnVizVoronoiGrayscale?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE) // 19
            }
            btnVizPenrose?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS) // 20
            }
            btnVizPenroseTrue?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.PENROSE_TRUE) // 21
            }
        }

        buttons.forEach { (button, mode) ->
            val isActive = currentVisualizerMode == mode
            button.setTextColor(if (isActive) accentColor else secondaryColor)
            button.setBackgroundResource(
                if (isActive) R.drawable.bg_viz_button_active else R.drawable.bg_viz_button_inactive
            )
            button.alpha = 1f
        }
    }

    private fun loadVisualizerMode() {
        val modeName = prefs.getString(PREF_VISUALIZER_MODE, AudioVisualizerView.VisualizationMode.BARS.name)
        currentVisualizerMode = try {
            AudioVisualizerView.VisualizationMode.valueOf(modeName ?: AudioVisualizerView.VisualizationMode.BARS.name)
        } catch (_: IllegalArgumentException) {
            AudioVisualizerView.VisualizationMode.BARS
        }
        visualizer.mode = currentVisualizerMode
        updateVisualizerButtons()
    }

    private fun saveVisualizerMode(mode: AudioVisualizerView.VisualizationMode) {
        prefs.edit {
            putString(PREF_VISUALIZER_MODE, mode.name)
        }
    }

    // ======================== Visualizer reorder ========================

    private val defaultVizOrder = listOf(
        AudioVisualizerView.VisualizationMode.BARS,
        AudioVisualizerView.VisualizationMode.WAVE,
        AudioVisualizerView.VisualizationMode.MIRROR,
        AudioVisualizerView.VisualizationMode.SPECTRUM,
        AudioVisualizerView.VisualizationMode.FIRE,
        AudioVisualizerView.VisualizationMode.CIRCLE,
        AudioVisualizerView.VisualizationMode.PARTICLES,
        AudioVisualizerView.VisualizationMode.RADIAL,
        AudioVisualizerView.VisualizationMode.BLOB,
        AudioVisualizerView.VisualizationMode.PARTICLES_MONO,
        AudioVisualizerView.VisualizationMode.KALEIDOSCOPE,
        AudioVisualizerView.VisualizationMode.BOIDS,
        AudioVisualizerView.VisualizationMode.JULIA,
        AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE,
        AudioVisualizerView.VisualizationMode.DELAUNAY_MESH,
        AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE,
        AudioVisualizerView.VisualizationMode.VORONOI,
        AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE,
        AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM,
        AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS,
        AudioVisualizerView.VisualizationMode.PENROSE_TRUE
    )

    private fun resolveVizOrder(): List<AudioVisualizerView.VisualizationMode> {
        val saved = MusicPreferences.getInstance(this).visualizerModesOrder
        if (saved.isEmpty()) return defaultVizOrder
        val result = saved.mapNotNull { name ->
            try { AudioVisualizerView.VisualizationMode.valueOf(name) } catch (_: IllegalArgumentException) { null }
        }.toMutableList()
        defaultVizOrder.forEach { mode -> if (mode !in result) result.add(mode) }
        return result
    }

    private fun applyVisualizerOrder() {
        val orderedModes = resolveVizOrder()
        val marginPx = (4 * resources.displayMetrics.density).toInt()

        val modeToBtn: Map<AudioVisualizerView.VisualizationMode, View?> = mapOf(
            AudioVisualizerView.VisualizationMode.BARS to btnVizBars,
            AudioVisualizerView.VisualizationMode.WAVE to btnVizWave,
            AudioVisualizerView.VisualizationMode.MIRROR to btnVizMirror,
            AudioVisualizerView.VisualizationMode.SPECTRUM to btnVizSpectrum,
            AudioVisualizerView.VisualizationMode.FIRE to btnVizFire,
            AudioVisualizerView.VisualizationMode.CIRCLE to btnVizCircle,
            AudioVisualizerView.VisualizationMode.PARTICLES to btnVizParticles,
            AudioVisualizerView.VisualizationMode.RADIAL to btnVizRadial,
            AudioVisualizerView.VisualizationMode.BLOB to btnVizBlob,
            AudioVisualizerView.VisualizationMode.PARTICLES_MONO to btnVizParticlesMono,
            AudioVisualizerView.VisualizationMode.KALEIDOSCOPE to btnVizKaleidoscope,
            AudioVisualizerView.VisualizationMode.BOIDS to btnVizBoids,
            AudioVisualizerView.VisualizationMode.JULIA to btnVizJulia,
            AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM to btnVizMandelbrot,
            AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE to btnVizJuliaGrayscale,
            AudioVisualizerView.VisualizationMode.DELAUNAY_MESH to btnVizDelaunay,
            AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE to btnVizDelaunayGrayscale,
            AudioVisualizerView.VisualizationMode.VORONOI to btnVizVoronoi,
            AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE to btnVizVoronoiGrayscale,
            AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS to btnVizPenrose,
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE to btnVizPenroseTrue
        )

        val modeToBtnFs: Map<AudioVisualizerView.VisualizationMode, View?> = mapOf(
            AudioVisualizerView.VisualizationMode.FIRE to btnVizFireFs,
            AudioVisualizerView.VisualizationMode.CIRCLE to btnVizCircleFs,
            AudioVisualizerView.VisualizationMode.PARTICLES to btnVizParticlesFs,
            AudioVisualizerView.VisualizationMode.RADIAL to btnVizRadialFs,
            AudioVisualizerView.VisualizationMode.BLOB to btnVizBlobFs,
            AudioVisualizerView.VisualizationMode.PARTICLES_MONO to btnVizParticlesMonoFs,
            AudioVisualizerView.VisualizationMode.KALEIDOSCOPE to btnVizKaleidoscopeFs,
            AudioVisualizerView.VisualizationMode.BOIDS to btnVizBoidsFs,
            AudioVisualizerView.VisualizationMode.JULIA to btnVizJuliaFs,
            AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM to btnVizMandelbrotFs,
            AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE to btnVizJuliaGrayscaleFs,
            AudioVisualizerView.VisualizationMode.DELAUNAY_MESH to btnVizDelaunayFs,
            AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE to btnVizDelaunayGrayscaleFs,
            AudioVisualizerView.VisualizationMode.VORONOI to btnVizVoronoiFs,
            AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE to btnVizVoronoiGrayscaleFs,
            AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS to btnVizPenroseFs,
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE to btnVizPenroseTrueFs
        )

        // Reorder normal buttons
        val normalContainer = findViewById<android.widget.LinearLayout>(R.id.visualizer_modes)
        // Sauvegarder le séparateur et le bouton de réorganisation avant de vider
        val reorderSeparator = findViewById<View>(R.id.viz_reorder_separator)
        val reorderBtn = findViewById<View>(R.id.btn_reorder_visuals)
        normalContainer.removeAllViews()
        var normalPos = 1
        orderedModes.forEach { mode ->
            val btn = modeToBtn[mode] as? TextView ?: return@forEach
            btn.text = normalPos.toString()
            val params = btn.layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(btn.layoutParams)
            params.marginStart = if (normalPos == 1) 0 else marginPx
            btn.layoutParams = params
            normalContainer.addView(btn)
            normalPos++
        }
        // Remettre le séparateur et le bouton de réorganisation à la fin
        reorderSeparator?.let { normalContainer.addView(it) }
        reorderBtn?.let { normalContainer.addView(it) }

        // Reorder fullscreen buttons (only modes that have a fs button)
        // Le numéro affiché = position dans orderedModes (identique au bouton normal)
        val fsContainer = findFsContainer() ?: return
        val reorderSeparatorFs = findViewById<View>(R.id.viz_reorder_separator_fs)
        val reorderBtnFs = findViewById<View>(R.id.btn_reorder_visuals_fs)
        fsContainer.removeAllViews()
        var isFirstFs = true
        orderedModes.forEachIndexed { index, mode ->
            val btn = modeToBtnFs[mode] ?: return@forEachIndexed
            if (btn is TextView) btn.text = (index + 1).toString()
            val params = btn.layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(btn.layoutParams)
            params.marginStart = if (isFirstFs) 0 else marginPx
            btn.layoutParams = params
            fsContainer.addView(btn)
            isFirstFs = false
        }
        reorderSeparatorFs?.let { fsContainer.addView(it) }
        reorderBtnFs?.let { fsContainer.addView(it) }
    }

    private fun findFsContainer(): android.widget.LinearLayout? =
        findViewById(R.id.visualizer_modes_fullscreen)

    private fun showVizReorderDialog() {
        val musicPrefs = MusicPreferences.getInstance(this)
        val allLabels = mapOf(
            AudioVisualizerView.VisualizationMode.BARS to getString(R.string.music_viz_bars),
            AudioVisualizerView.VisualizationMode.WAVE to getString(R.string.music_viz_wave),
            AudioVisualizerView.VisualizationMode.MIRROR to getString(R.string.music_viz_mirror),
            AudioVisualizerView.VisualizationMode.SPECTRUM to getString(R.string.music_viz_spectrum),
            AudioVisualizerView.VisualizationMode.FIRE to getString(R.string.music_viz_fire),
            AudioVisualizerView.VisualizationMode.CIRCLE to getString(R.string.music_viz_circle),
            AudioVisualizerView.VisualizationMode.PARTICLES to getString(R.string.music_viz_particles),
            AudioVisualizerView.VisualizationMode.RADIAL to getString(R.string.music_viz_radial),
            AudioVisualizerView.VisualizationMode.BLOB to getString(R.string.music_viz_blob),
            AudioVisualizerView.VisualizationMode.PARTICLES_MONO to getString(R.string.music_viz_particles_mono),
            AudioVisualizerView.VisualizationMode.KALEIDOSCOPE to getString(R.string.music_viz_kaleidoscope),
            AudioVisualizerView.VisualizationMode.BOIDS to getString(R.string.music_viz_boids),
            AudioVisualizerView.VisualizationMode.JULIA to getString(R.string.music_viz_julia),
            AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM to getString(R.string.music_viz_mandelbrot),
            AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE to getString(R.string.music_viz_julia_grayscale),
            AudioVisualizerView.VisualizationMode.DELAUNAY_MESH to getString(R.string.music_viz_delaunay),
            AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE to getString(R.string.music_viz_delaunay_grayscale),
            AudioVisualizerView.VisualizationMode.VORONOI to getString(R.string.music_viz_voronoi),
            AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE to getString(R.string.music_viz_voronoi_grayscale),
            AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS to getString(R.string.music_viz_penrose),
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE to getString(R.string.music_viz_penrose_true)
        )

        val orderedItems: MutableList<Pair<AudioVisualizerView.VisualizationMode, String>> =
            resolveVizOrder().mapNotNull { mode ->
                allLabels[mode]?.let { label -> mode to label }
            }.toMutableList()

        val adapter = VizReorderAdapter(orderedItems)
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@FullPlayerActivity)
            this.adapter = adapter
        }

        val touchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }
        val touchHelper = ItemTouchHelper(touchCallback)
        touchHelper.attachToRecyclerView(recycler)
        adapter.touchHelper = touchHelper

        AlertDialog.Builder(this)
            .setTitle(R.string.music_reorder_visuals)
            .setView(recycler)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                musicPrefs.visualizerModesOrder = orderedItems.map { it.first.name }
                applyVisualizerOrder()
                updateVisualizerButtons()
                if (isTrueFullscreen) updateFullscreenVisualizerButtons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.music_reorder_reset) { _, _ ->
                musicPrefs.visualizerModesOrder = emptyList()
                applyVisualizerOrder()
                updateVisualizerButtons()
                if (isTrueFullscreen) updateFullscreenVisualizerButtons()
            }
            .show()
    }

    // ====================================================================

    private fun setupSeekBar() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = MusicPlaybackHolder.getDuration()
                    val position = (progress / 100f * duration).toLong()
                    val formatted = formatDuration(position)
                    currentTime.text = formatted
                    fullscreenCurrentTime.text = formatted
                    // Sync l'autre seekbar
                    if (seekBar?.id == R.id.seek_bar) fullscreenSeekBar.progress = progress
                    else this@FullPlayerActivity.seekBar.progress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val duration = MusicPlaybackHolder.getDuration()
                    val position = (it.progress / 100f * duration).toLong()
                    MusicPlaybackHolder.seekTo(position)
                }
                isUserSeeking = false
            }
        }
        seekBar.setOnSeekBarChangeListener(listener)
        fullscreenSeekBar.setOnSeekBarChangeListener(listener)
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Si on est en mode vrai fullscreen, sortir du mode fullscreen au lieu de quitter
                if (isTrueFullscreen) {
                    exitTrueFullscreen()
                } else {
                    navigateBackToHub()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToClose() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                // Swipe must be top-to-bottom (positive diffY)
                if (diffY <= 0) return false

                // Must be roughly vertical: vertical movement > horizontal movement
                val isRoughlyVertical = kotlin.math.abs(diffY) > kotlin.math.abs(diffX)

                // Minimum swipe distance (100dp)
                val minSwipeDistance = 100 * resources.displayMetrics.density

                if (diffY >= minSwipeDistance && isRoughlyVertical) {
                    navigateBackToHub()
                    return true
                }
                return false
            }

            override fun onDown(e: MotionEvent): Boolean {
                // Must return true to receive subsequent events
                return true
            }
        })

        albumArtContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun checkAudioPermissionAndSetupVisualizer() {
        // Always try to initialize equalizer (doesn't need RECORD_AUDIO permission)
        initializeEqualizerIfNeeded()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupAudioVisualizer()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun initializeEqualizerIfNeeded() {
        MusicEqualizerManager.initialize(this)

        // Attach DSP processor first (primary - works on Samsung)
        MusicPlaybackHolder.equalizerProcessor?.let { processor ->
            MusicEqualizerManager.attachToProcessor(processor)
        }

        // Also attach to audio session (for virtualizer and fallback)
        val audioSessionId = MusicPlaybackHolder.getAudioSessionId()
        if (audioSessionId != 0) {
            MusicEqualizerManager.attachToAudioSession(audioSessionId)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupAudioVisualizer()
            }
            // Si refusé, le visualiseur fonctionnera en mode animation idle
        }
    }

    private fun setupAudioVisualizer() {
        try {
            val audioSessionId = MusicPlaybackHolder.getAudioSessionId()
            if (audioSessionId == 0) return

            releaseVisualizer()

            // Initialize equalizer with the audio session
            MusicEqualizerManager.initialize(this)
            MusicEqualizerManager.attachToAudioSession(audioSessionId)

            audioVisualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let {
                                this@FullPlayerActivity.visualizer.updateWaveform(it)
                                // Mettre à jour aussi le visualiseur fullscreen si actif
                                if (isTrueFullscreen) {
                                    this@FullPlayerActivity.visualizerFullscreen.updateWaveform(it)
                                }
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft?.let {
                                this@FullPlayerActivity.visualizer.updateFft(it)
                                // Mettre à jour aussi le visualiseur fullscreen si actif
                                if (isTrueFullscreen) {
                                    this@FullPlayerActivity.visualizerFullscreen.updateFft(it)
                                }
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            // Visualizer not available, animation mode will be used
            android.util.Log.w("FullPlayerActivity", "Visualizer not available: ${e.message}")
        }
    }

    private fun releaseVisualizer() {
        audioVisualizer?.let {
            it.enabled = false
            it.release()
        }
        audioVisualizer = null
    }

    private fun updateTrackInfo(track: MusicTrack) {
        currentTrackId = track.id
        trackTitle.text = track.title
        trackArtist.text = track.artist
        trackAlbum.text = track.year?.let { year ->
            getString(R.string.music_album_with_year, track.album, year)
        } ?: track.album
        totalTime.text = track.durationFormatted
        fullscreenTotalTime.text = track.durationFormatted

        // Load album art
        track.albumArtUri?.let { uri ->
            try {
                albumArt.setImageURI(null)
                albumArt.setImageURI(uri)
                if (albumArt.drawable == null) {
                    albumArt.setImageResource(R.drawable.ic_music_album_placeholder)
                }
            } catch (_: Exception) {
                albumArt.setImageResource(R.drawable.ic_music_album_placeholder)
            }
        } ?: run {
            albumArt.setImageResource(R.drawable.ic_music_album_placeholder)
        }

        // Reset seek bar
        seekBar.progress = 0
        fullscreenSeekBar.progress = 0
        currentTime.text = formatDuration(0)
        fullscreenCurrentTime.text = formatDuration(0)

        // Update favorite button
        updateFavoriteButton(track)

        // Update play count
        updatePlayCount(track)

        // Update queue indicator
        queueAdapter.setCurrentlyPlayingIndex(MusicPlaybackHolder.getCurrentIndex())

        // Lyrics overlay : afficher automatiquement si l'option est activée
        // En mode fullscreen, garder les paroles actives si l'utilisateur les a ouvertes
        val preferences = MusicPreferences.getInstance(this)
        val lyricsDisplayWanted = isAlbumArtVisible || lyricsEnabledInFullscreen
        if (preferences.autoFetchLyrics && lyricsDisplayWanted) {
            // Recherche + affichage
            loadAndShowLyrics(track, showBottomSheetIfNotFound = false, searchOnline = true)
        } else {
            hideLyricsOverlay()
            // Même si l'affichage n'est pas voulu, lancer une recherche silencieuse
            // en arrière-plan pour pré-remplir le cache. La recherche est supprimée
            // automatiquement si un marqueur "no_lyrics" existe déjà pour ce titre.
            if (preferences.autoFetchLyrics) {
                com.Atom2Universe.app.music.lyrics.LyricsAutoFetchManager.onTrackChanged(track)
            }
        }

        // Re-setup visualizer for new track
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            handler.postDelayed({ setupAudioVisualizer() }, 500)
        }
    }

    private fun updateFavoriteButton(track: MusicTrack, isFavorite: Boolean? = null) {
        val favorite = isFavorite ?: MusicFavoritesManager.isFavorite(track)
        val iconRes = if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        val tintColor = if (favorite) {
            ContextCompat.getColor(this, R.color.music_favorite_active)
        } else {
            ContextCompat.getColor(this, R.color.music_text_secondary)
        }
        btnFavorite.setImageResource(iconRes)
        btnFavorite.imageTintList = ColorStateList.valueOf(tintColor)
    }

    private fun updatePlayCount(track: MusicTrack) {
        val count = MusicStatsManager.getPlayCount(track)
        playCountText.text = when {
            count == 0L -> ""
            count == 1L -> getString(R.string.music_play_count_single)
            else -> getString(R.string.music_play_count_format, count)
        }
    }

    private fun updateProgress() {
        if (!isUserSeeking) {
            val position = MusicPlaybackHolder.getPosition()
            val duration = MusicPlaybackHolder.getDuration()

            if (duration > 0) {
                val progress = ((position.toFloat() / duration) * 100).toInt()
                val formatted = formatDuration(position)
                seekBar.progress = progress
                fullscreenSeekBar.progress = progress
                currentTime.text = formatted
                fullscreenCurrentTime.text = formatted

                // Update synchronized lyrics if visible
                updateCurrentLyricLine(position)
            }
        }
    }

    private fun loadAndShowLyrics(track: MusicTrack, showBottomSheetIfNotFound: Boolean = true, searchOnline: Boolean = false) {
        val requestedTrackId = track.id
        lifecycleScope.launch {
            // Vider immédiatement les paroles de l'ancien titre avant tout appel suspend :
            // lyricsLines = null stoppe le défilement dans updateCurrentLyricLine()
            lyricsLines = null
            isSyncedLyrics = false
            lyricsAdapter = null
            lyricsRecycler.adapter = null
            lyricsRecyclerFullscreen.adapter = null
            lyricsRecyclerTrueFullscreen.adapter = null

            // Si une recherche en ligne va avoir lieu, afficher le fond gris + spinner
            // immédiatement (même si le container était caché pour le titre précédent)
            if (searchOnline) showSearchingState()

            // Vérifier d'abord le cache/fichier
            val lyrics = com.Atom2Universe.app.music.lyrics.LyricsManager.getLyrics(track)

            // Si le track a changé pendant la lecture du cache, abandonner
            if (MusicPlaybackHolder.getCurrentTrack()?.id != requestedTrackId) {
                hideAllSpinners()
                return@launch
            }

            if (lyrics != null && lyrics.isNotBlank()) {
                hideAllSpinners()
                displayLyricsInOverlay(lyrics)
            } else if (searchOnline) {
                // Spinner déjà affiché par showSearchingState()
                // Pas de paroles en cache, lancer une recherche en ligne
                val result = com.Atom2Universe.app.music.lyrics.LyricsManager.fetchLyricsOnline(track)

                // Cacher l'indicateur de chargement dans tous les cas
                hideAllSpinners()

                // Si le track a changé pendant la recherche réseau, abandonner
                if (MusicPlaybackHolder.getCurrentTrack()?.id != requestedTrackId) return@launch

                when (result) {
                    is com.Atom2Universe.app.music.lyrics.api.LyricsResult.Success -> {
                        // Afficher les paroles trouvées
                        displayLyricsInOverlay(result.lyrics)
                    }
                    else -> {
                        // Pas de paroles trouvées — masquer le contenu
                        // mais garder le système prêt en mode fullscreen
                        currentLyrics = null
                        lyricsLines = null
                        if (lyricsEnabledInFullscreen) {
                            // Cacher les containers visuellement mais garder le flag
                            lyricsOverlayContainer.isVisible = false
                            lyricsOverlayFullscreen.isVisible = false
                            lyricsOverlayTrueFullscreen.isVisible = false
                            lyricsAdapter = null
                            isSyncedLyrics = false
                        } else {
                            hideLyricsOverlay()
                        }

                        if (showBottomSheetIfNotFound) {
                            val bottomSheet = LyricsBottomSheet.newInstance(track)
                            bottomSheet.show(supportFragmentManager, "lyrics_bottom_sheet")
                        }
                    }
                }
            } else {
                // Pas de recherche en ligne demandée et pas de paroles
                currentLyrics = null
                lyricsLines = null
                if (lyricsEnabledInFullscreen) {
                    lyricsOverlayContainer.isVisible = false
                    lyricsOverlayFullscreen.isVisible = false
                    lyricsOverlayTrueFullscreen.isVisible = false
                    lyricsAdapter = null
                    isSyncedLyrics = false
                } else {
                    hideLyricsOverlay()
                }

                if (showBottomSheetIfNotFound) {
                    val bottomSheet = LyricsBottomSheet.newInstance(track)
                    bottomSheet.show(supportFragmentManager, "lyrics_bottom_sheet")
                }
            }
        }
    }

    private fun displayLyricsInOverlay(lyrics: String) {
        currentLyrics = lyrics

        // Check if lyrics are synchronized (LRC format)
        if (lyrics.contains("[") && lyrics.contains("]")) {
            // Parse LRC format
            val parsed = LrcParser.parse(lyrics)
            if (parsed.isNotEmpty()) {
                lyricsLines = parsed
                isSyncedLyrics = true
                showLyricsOverlay(parsed, synced = true)
                return
            }
        }

        // If not LRC or parsing failed, show unsynchronized lyrics in overlay
        // Split plain text by lines for proper display
        val plainLines = lyrics.split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                LrcParser.LyricLine(
                    timeMs = 0L,
                    text = line.trim()
                )
            }
        lyricsLines = plainLines
        isSyncedLyrics = false
        showLyricsOverlay(plainLines, synced = false)
    }

    // Détermine si on doit utiliser le container fullscreen (modes 6-12 en fullscreen)
    private fun shouldUseLyricsFullscreen(): Boolean {
        // Si on est en vrai mode fullscreen, utiliser le container dédié
        if (isTrueFullscreen) {
            return false // On utilisera lyricsOverlayTrueFullscreen à la place
        }

        val mode = visualizer.mode
        val isFullscreen = visualizer.isFullscreen

        // Modes centraux (5-21) qui supportent fullscreen
        val fullscreenModes = setOf(
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
            AudioVisualizerView.VisualizationMode.PENROSE_TRUE
        )

        return isFullscreen && mode in fullscreenModes
    }

    private fun showLyricsOverlay(
        lines: List<LrcParser.LyricLine>,
        synced: Boolean = false
    ) {
        // Si on est en mode vrai fullscreen, utiliser le container dédié
        if (isTrueFullscreen) {
            lyricsEnabledInFullscreen = true
            lyricsOverlayContainer.isVisible = false
            lyricsOverlayFullscreen.isVisible = false
            lyricsOverlayTrueFullscreen.isVisible = true

            lyricsAdapter = LyricsAdapter(lines, isSynced = synced)
            lyricsRecyclerTrueFullscreen.adapter = lyricsAdapter

            checkboxAutoScrollTrueFullscreen.isVisible = synced
            val autoScrollEnabled = MusicPreferences.getInstance(this).lyricsAutoScroll
            checkboxAutoScrollTrueFullscreen.isChecked = autoScrollEnabled
            lyricsAdapter?.setAutoScrollEnabled(autoScrollEnabled)

            if (synced) {
                lyricsRecyclerTrueFullscreen.post {
                    updateCurrentLyricLine(MusicPlaybackHolder.getPosition())
                }
            }
            return
        }

        val useFullscreen = shouldUseLyricsFullscreen()
        if (useFullscreen) lyricsEnabledInFullscreen = true

        // Choisir le bon container selon le mode
        val container = if (useFullscreen) lyricsOverlayFullscreen else lyricsOverlayContainer
        val recycler = if (useFullscreen) lyricsRecyclerFullscreen else lyricsRecycler
        val checkbox = if (useFullscreen) checkboxAutoScrollFullscreen else checkboxAutoScroll

        // Masquer les autres containers
        lyricsOverlayContainer.isVisible = !useFullscreen
        lyricsOverlayFullscreen.isVisible = useFullscreen
        lyricsOverlayTrueFullscreen.isVisible = false

        lyricsAdapter = LyricsAdapter(lines, isSynced = synced)
        recycler.adapter = lyricsAdapter
        container.isVisible = true

        // Show auto-scroll checkbox only for synced lyrics
        checkbox.isVisible = synced
        val autoScrollEnabled = MusicPreferences.getInstance(this).lyricsAutoScroll
        checkbox.isChecked = autoScrollEnabled
        lyricsAdapter?.setAutoScrollEnabled(autoScrollEnabled)

        // Retarder la mise à jour de la ligne pour éviter le scroll automatique
        // Le RecyclerView a besoin d'être layouté avant qu'on puisse scroller
        if (synced) {
            recycler.post {
                updateCurrentLyricLine(MusicPlaybackHolder.getPosition())
            }
        }
    }

    // Affiche le bon container de paroles avec uniquement le spinner (fond gris visible,
    // RecyclerView vide) — utilisé pendant une recherche en ligne en cours.
    private fun showSearchingState() {
        when {
            isTrueFullscreen -> {
                lyricsOverlayContainer.isVisible = false
                lyricsOverlayFullscreen.isVisible = false
                lyricsOverlayTrueFullscreen.isVisible = true
                lyricsLoadingTrueFullscreen.visibility = View.VISIBLE
            }
            shouldUseLyricsFullscreen() -> {
                lyricsOverlayContainer.isVisible = false
                lyricsOverlayFullscreen.isVisible = true
                lyricsOverlayTrueFullscreen.isVisible = false
                lyricsLoadingFullscreen.visibility = View.VISIBLE
            }
            else -> {
                lyricsOverlayContainer.isVisible = true
                lyricsOverlayFullscreen.isVisible = false
                lyricsOverlayTrueFullscreen.isVisible = false
                lyricsLoading.visibility = View.VISIBLE
            }
        }
    }

    private fun hideAllSpinners() {
        lyricsLoading.visibility = View.GONE
        lyricsLoadingFullscreen.visibility = View.GONE
        lyricsLoadingTrueFullscreen.visibility = View.GONE
    }

    private fun hideLyricsOverlay() {
        lyricsOverlayContainer.isVisible = false
        lyricsOverlayFullscreen.isVisible = false
        lyricsOverlayTrueFullscreen.isVisible = false
        checkboxAutoScroll.isVisible = false
        checkboxAutoScrollFullscreen.isVisible = false
        checkboxAutoScrollTrueFullscreen.isVisible = false
        lyricsAdapter = null
        lyricsLines = null
        isSyncedLyrics = false
    }

    // Rafraîchir l'affichage des lyrics (pour basculer entre normal/fullscreen)
    private fun refreshLyricsIfVisible() {
        val lines = lyricsLines
        if (lines != null && (lyricsOverlayContainer.isVisible || lyricsOverlayFullscreen.isVisible || lyricsOverlayTrueFullscreen.isVisible)) {
            // Pour les paroles non-synchronisées, sauvegarder la position de scroll
            // manuelle avant la recréation de l'adapter (le cas synced est géré
            // automatiquement par updateCurrentLyricLine via LyricsAdapter.setCurrentLine)
            var savedScrollPos = RecyclerView.NO_POSITION
            if (!isSyncedLyrics) {
                val currentRecycler = when {
                    lyricsOverlayTrueFullscreen.isVisible -> lyricsRecyclerTrueFullscreen
                    lyricsOverlayFullscreen.isVisible -> lyricsRecyclerFullscreen
                    else -> lyricsRecycler
                }
                savedScrollPos = (currentRecycler.layoutManager as? LinearLayoutManager)
                    ?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            }

            // Réafficher les lyrics dans le bon container
            showLyricsOverlay(lines, isSyncedLyrics)

            // Restaurer la position pour les paroles non-synchronisées
            if (!isSyncedLyrics && savedScrollPos != RecyclerView.NO_POSITION && savedScrollPos > 0) {
                val newRecycler = when {
                    isTrueFullscreen -> lyricsRecyclerTrueFullscreen
                    shouldUseLyricsFullscreen() -> lyricsRecyclerFullscreen
                    else -> lyricsRecycler
                }
                newRecycler.post {
                    newRecycler.scrollToPosition(savedScrollPos)
                }
            }
        }
    }

    private fun updateCurrentLyricLine(positionMs: Long) {
        // Only update for synced lyrics
        if (!isSyncedLyrics) return

        val lines = lyricsLines ?: return
        val adapter = lyricsAdapter ?: return

        // Vérifier quel container est visible
        val isNormalVisible = lyricsOverlayContainer.isVisible
        val isFullscreenVisible = lyricsOverlayFullscreen.isVisible
        val isTrueFullscreenVisible = lyricsOverlayTrueFullscreen.isVisible

        if (!isNormalVisible && !isFullscreenVisible && !isTrueFullscreenVisible) return

        // Utiliser le bon recycler selon le container actif
        val recycler = when {
            isTrueFullscreenVisible -> lyricsRecyclerTrueFullscreen
            isFullscreenVisible -> lyricsRecyclerFullscreen
            else -> lyricsRecycler
        }

        val currentIndex = LrcParser.getCurrentLineIndex(lines, positionMs)
        adapter.setCurrentLine(currentIndex, recycler)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        btnPlayPause.setImageResource(icon)
    }

    private fun updateShuffleButton() {
        val color = if (MusicPlaybackHolder.shuffleEnabled) {
            ContextCompat.getColor(this, R.color.music_accent)
        } else {
            ContextCompat.getColor(this, R.color.music_text_secondary)
        }
        btnShuffle.imageTintList = ColorStateList.valueOf(color)
    }

    private fun updateRepeatButton() {
        val repeatMode = MusicPlaybackHolder.repeatMode

        // Changer l'icône selon le mode
        val iconRes = when (repeatMode) {
            MusicPlaybackHolder.RepeatMode.ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }
        btnRepeat.setImageResource(iconRes)

        // Changer la couleur (accent si actif, secondary sinon)
        val color = if (repeatMode != MusicPlaybackHolder.RepeatMode.OFF) {
            ContextCompat.getColor(this, R.color.music_accent)
        } else {
            ContextCompat.getColor(this, R.color.music_text_secondary)
        }
        btnRepeat.imageTintList = ColorStateList.valueOf(color)
    }

    private fun updateEqualizerButton() {
        val color = if (MusicEqualizerManager.isEnabled.value) {
            ContextCompat.getColor(this, R.color.music_accent)
        } else {
            ContextCompat.getColor(this, android.R.color.white)
        }
        btnEqualizer.imageTintList = ColorStateList.valueOf(color)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
    }

    // MusicPlaybackHolder.PlayerListener implementation

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            updatePlayPauseButton(isPlaying)
            // Mettre à jour aussi le bouton fullscreen si on est en mode fullscreen
            if (isTrueFullscreen) {
                updateFullscreenPlayPauseButton(isPlaying)
            }
        }
    }

    override fun onTrackChanged(track: MusicTrack?) {
        runOnUiThread {
            track?.let {
                updateTrackInfo(it)
                // Mettre à jour aussi le titre fullscreen si on est en mode fullscreen
                if (isTrueFullscreen) {
                    fullscreenTrackTitle.text = it.title
                }
            }
        }
        // Update equalizer preset for the new track
        MusicEqualizerManager.onTrackChanged(track)
    }

    override fun onProgressChanged(position: Long, duration: Long) {
        // Handled by our own progress updater
    }

    override fun onError(error: PlaybackException) {
        // Could show error message
    }

    override fun onPlaylistChanged(playlist: List<MusicTrack>) {
        runOnUiThread {
            updateQueue()
            updateEditModeButton()
        }
    }

    override fun onPlayCountIncremented(track: MusicTrack, newCount: Long) {
        runOnUiThread {
            // Met à jour l'affichage du compteur si c'est le track actuel
            val currentTrack = MusicPlaybackHolder.getCurrentTrack()
            if (currentTrack?.id == track.id) {
                updatePlayCount(track)
            }
            // Met à jour le compteur dans la queue
            queueAdapter.updatePlayCount(track.id)
        }
    }

    // ========== True Fullscreen Mode (modes 6-12 only) ==========

    @SuppressLint("ClickableViewAccessibility")
    private fun enterTrueFullscreen() {
        isTrueFullscreen = true

        // Empêcher la mise en veille de l'écran
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Afficher le container fullscreen
        trueFullscreenContainer.visibility = View.VISIBLE

        // Synchroniser le mode de visualisation
        visualizerFullscreen.mode = currentVisualizerMode
        visualizerFullscreen.isFullscreen = true

        // Update fullscreen visualizer buttons
        updateFullscreenVisualizerButtons()

        // Mettre à jour le titre de la chanson
        MusicPlaybackHolder.getCurrentTrack()?.let { track ->
            fullscreenTrackTitle.text = track.title
        }

        // Mettre à jour l'état du bouton play/pause
        updateFullscreenPlayPauseButton(MusicPlaybackHolder.isPlaying())

        // Hide system bars for true immersive experience
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)

        // Masquer le bouton fullscreen (on est déjà en fullscreen)
        btnFullscreen.visibility = View.GONE

        // Setup touch listener pour afficher/masquer les contrôles
        visualizerFullscreen.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                toggleFullscreenControls()
            }
            true
        }

        // Afficher les contrôles au début, puis les masquer après délai
        showFullscreenControls()
        resetFullscreenControlsHideTimer()

        // Si les lyrics sont affichées, les réafficher dans le bon container
        refreshLyricsIfVisible()
    }

    private fun exitTrueFullscreen() {
        isTrueFullscreen = false

        // Annuler le timer de masquage des contrôles
        handler.removeCallbacks(hideControlsRunnable)

        // Réactiver la mise en veille de l'écran
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Masquer le container fullscreen
        trueFullscreenContainer.visibility = View.GONE

        // Restore system bars via l'API moderne (cohérent avec onCreate/onResume)
        enableImmersiveMode()

        // Retirer le touch listener
        visualizerFullscreen.setOnTouchListener(null)

        // Réinitialiser l'état fullscreen du visualiseur dédié
        visualizerFullscreen.isFullscreen = false

        // Synchroniser le mode de visualisation avec le visualiseur principal
        visualizer.mode = currentVisualizerMode

        // Mettre à jour la visibilité du bouton fullscreen
        updateVisualizerVisibility()

        // Si les lyrics sont affichées, les réafficher dans le bon container
        refreshLyricsIfVisible()
    }

    private fun showFullscreenControls() {
        areFullscreenControlsVisible = true
        fullscreenTopBar.visibility = View.VISIBLE
        fullscreenBottomBar.visibility = View.VISIBLE
    }

    private fun hideFullscreenControls() {
        areFullscreenControlsVisible = false
        fullscreenTopBar.visibility = View.GONE
        fullscreenBottomBar.visibility = View.GONE
    }

    private fun toggleFullscreenControls() {
        if (areFullscreenControlsVisible) {
            hideFullscreenControls()
            // Annuler le timer car on vient de masquer manuellement
            handler.removeCallbacks(hideControlsRunnable)
        } else {
            showFullscreenControls()
            resetFullscreenControlsHideTimer()
        }
    }

    private fun resetFullscreenControlsHideTimer() {
        // Annuler le timer précédent
        handler.removeCallbacks(hideControlsRunnable)
        // Relancer le timer pour masquer les contrôles après 3 secondes
        handler.postDelayed(hideControlsRunnable, FULLSCREEN_CONTROLS_HIDE_DELAY)
    }

    private fun updateFullscreenPlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        fullscreenBtnPlayPause.setImageResource(icon)
    }

    private fun updateFullscreenVisualizerButtons() {
        val accentColor = MaterialColors.getColor(
            this,
            R.attr.a2uMusicAccent,
            ContextCompat.getColor(this, R.color.music_accent)
        )
        val secondaryColor = ContextCompat.getColor(this, R.color.music_text_secondary)

        // Liste des boutons fullscreen avec leurs modes
        val buttons = buildList {
            add(btnVizFireFs to AudioVisualizerView.VisualizationMode.FIRE)
            add(btnVizCircleFs to AudioVisualizerView.VisualizationMode.CIRCLE)
            add(btnVizParticlesFs to AudioVisualizerView.VisualizationMode.PARTICLES)
            add(btnVizRadialFs to AudioVisualizerView.VisualizationMode.RADIAL)
            add(btnVizBlobFs to AudioVisualizerView.VisualizationMode.BLOB)
            add(btnVizParticlesMonoFs to AudioVisualizerView.VisualizationMode.PARTICLES_MONO)
            add(btnVizKaleidoscopeFs to AudioVisualizerView.VisualizationMode.KALEIDOSCOPE)
            add(btnVizBoidsFs to AudioVisualizerView.VisualizationMode.BOIDS)
            add(btnVizJuliaFs to AudioVisualizerView.VisualizationMode.JULIA)
            add(btnVizMandelbrotFs to AudioVisualizerView.VisualizationMode.MANDELBROT_ZOOM)
            btnVizJuliaGrayscaleFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.JULIA_GRAYSCALE)
            }
            btnVizDelaunayFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.DELAUNAY_MESH)
            }
            btnVizDelaunayGrayscaleFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.DELAUNAY_GRAYSCALE)
            }
            btnVizVoronoiFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.VORONOI)
            }
            btnVizVoronoiGrayscaleFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.VORONOI_GRAYSCALE)
            }
            btnVizPenroseFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.PENROSE_RHOMBUS)
            }
            btnVizPenroseTrueFs?.let { btn ->
                add(btn to AudioVisualizerView.VisualizationMode.PENROSE_TRUE)
            }
        }

        buttons.forEach { (button, mode) ->
            val isActive = currentVisualizerMode == mode
            button.setTextColor(if (isActive) accentColor else secondaryColor)
            button.setBackgroundResource(
                if (isActive) R.drawable.bg_viz_button_active else R.drawable.bg_viz_button_inactive
            )
            button.alpha = 1f
        }
    }
}
