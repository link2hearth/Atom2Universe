package com.Atom2Universe.app.midi.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import com.Atom2Universe.app.LocaleHelper
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import com.Atom2Universe.app.AudioPlaybackManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.google.android.material.color.MaterialColors
import com.Atom2Universe.app.midi.data.MidiDatabase
import com.Atom2Universe.app.midi.repository.MidiRepository
import com.Atom2Universe.app.midi.repository.PlaylistRepository
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.scanner.MidiLibraryScanner
import com.Atom2Universe.app.midi.scanner.SoundFontManager
import com.Atom2Universe.app.midi.service.MidiAudioMixer
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModel
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModelFactory
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.Atom2Universe.app.midi.practice.PracticeArgs
import com.Atom2Universe.app.midi.practice.PianoPracticeFragment
import com.Atom2Universe.app.midi.service.PlaybackQueueManager
import kotlinx.coroutines.launch
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.AudioHubActivity
import java.util.Locale

/**
 * Activity principale du lecteur MIDI
 *
 * Architecture:
 * - ViewPager2 avec 3 fragments (Library, Playlists, Now Playing)
 * - BottomNavigationView pour navigation
 * - MediaBrowser connection au MidiPlaybackService
 * - Mini player en bas (collapsed state)
 */
class MidiPlayerActivity : ThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLibrary: TextView
    private lateinit var tabPlaylists: TextView
    private lateinit var tabNowPlaying: TextView
    private lateinit var tabPractice: TextView
    private lateinit var practiceContainer: FrameLayout
    private lateinit var viewModel: MidiPlayerViewModel

    // Playback bar elements
    private lateinit var playbackBarContainer: LinearLayout
    private lateinit var playbackBarTitle: TextView
    private lateinit var playbackBarArtist: TextView
    private lateinit var playbackBarSeek: SeekBar
    private lateinit var playbackBarTimeCurrent: TextView
    private lateinit var playbackBarTimeTotal: TextView
    private lateinit var playbackBarPlayPause: ImageButton
    private lateinit var playbackBarStop: ImageButton
    private lateinit var playbackBarPrevious: ImageButton
    private lateinit var playbackBarNext: ImageButton
    private lateinit var playbackBarShuffle: ImageButton
    private lateinit var playbackBarRepeat: ImageButton
    private lateinit var playbackBarTrackInfo: LinearLayout

    // Playback bar state
    private var playbackBarDurationMs: Long = 0L
    private var playbackBarShuffleEnabled = false
    private var playbackBarRepeatMode = PlaybackQueueManager.RepeatMode.NONE
    private var colorActive = 0
    private var colorInactive = 0

    // MediaBrowser pour connexion au service
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null
    private var lastPlaybackState: Int = PlaybackStateCompat.STATE_NONE
    private var isControllerCallbackRegistered = false  // Track si callback est enregistré

    // BUG FIX 3.32: Timeout pour la connexion MediaBrowser
    private val connectionTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null
    private val connectionTimeoutMs = 5000L  // 5 secondes de timeout

    // URI en attente pour fichier externe (ouvert depuis explorateur de fichiers)
    private var pendingExternalUri: Uri? = null

    // Repositories
    private lateinit var midiRepository: MidiRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var midiScanner: MidiLibraryScanner
    private lateinit var soundFontManager: SoundFontManager

    // File pickers
    private val soundFontPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSoundFontSelected(it) }
    }

    private val midiFolderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleMidiFolderSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_midi_player)

        // Initialisation repositories
        MidiDatabase.getInstance(this)
        midiRepository = MidiRepository(this)
        playlistRepository = PlaylistRepository(this)
        settingsRepository = SettingsRepository(this)
        midiScanner = MidiLibraryScanner(this, midiRepository)
        soundFontManager = SoundFontManager(this, settingsRepository)

        // Initialisation ViewModel
        val factory = MidiPlayerViewModelFactory(
            midiRepository,
            playlistRepository,
            settingsRepository
        )
        viewModel = ViewModelProvider(this, factory)[MidiPlayerViewModel::class.java]

        // Setup UI
        setupViews()
        setupMediaBrowser()
        setupObservers()

        // Vérifie si SoundFont est configuré
        checkSoundFontConfiguration()

        // Charger les paramètres audio
        loadAudioSettings()

        // Gérer l'ouverture via fichier externe
        handleExternalFileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalFileIntent(intent)
    }

    /**
     * Gère l'ouverture d'un fichier MIDI externe (depuis explorateur de fichiers)
     */
    private fun handleExternalFileIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val uri = intent.data ?: return

        // Stocker l'URI pour le jouer une fois connecté au service
        pendingExternalUri = uri

        // Si déjà connecté, jouer immédiatement
        if (mediaController != null) {
            playExternalFile(uri)
            pendingExternalUri = null
        }

        // Clear l'intent pour ne pas rejouer si l'activité est recréée
        setIntent(Intent())
    }

    /**
     * Joue un fichier MIDI externe
     */
    private fun playExternalFile(uri: Uri) {
        lifecycleScope.launch {
            val track = createMidiTrackFromUri(uri)
            if (track != null) {
                // Aller à l'onglet Now Playing
                selectTab(2)
                // Jouer le fichier avec le mode synthétiseur sélectionné par l'utilisateur
                playTracks(listOf(track), 0)
            }
        }
    }

    /**
     * Crée un MidiTrack temporaire à partir d'une URI externe
     */
    private suspend fun createMidiTrackFromUri(uri: Uri): com.Atom2Universe.app.midi.data.MidiTrack? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = getFileNameFromUri(uri) ?: "Unknown MIDI"

                com.Atom2Universe.app.midi.data.MidiTrack(
                    id = uri.hashCode().toLong(),
                    title = fileName,
                    artist = "",
                    album = "",
                    filePath = uri.toString(),  // Le service peut jouer depuis une URI content://
                    duration = 0L,  // Unknown for external files
                    dateAdded = System.currentTimeMillis(),
                    fileSize = 0L  // Unknown for external files
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Extrait le nom de fichier depuis une URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) cursor.getString(nameIndex)?.substringBeforeLast('.') else null
                        } else null
                    }
                }
                "file" -> uri.lastPathSegment?.substringBeforeLast('.')
                else -> uri.lastPathSegment?.substringBeforeLast('.')
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setupViews() {
        // Setup toolbar avec flèche retour
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        viewPager = findViewById(R.id.view_pager)
        tabLibrary = findViewById(R.id.tab_library)
        tabPlaylists = findViewById(R.id.tab_playlists)
        tabNowPlaying = findViewById(R.id.tab_now_playing)
        tabPractice = findViewById(R.id.tab_practice)
        practiceContainer = findViewById(R.id.practice_container)

        // Setup ViewPager adapter avec 3 fragments
        val adapter = MidiPlayerPagerAdapter(this)
        viewPager.adapter = adapter

        // Disable swipe (navigation uniquement via tabs)
        viewPager.isUserInputEnabled = false

        // Setup tab clicks
        tabLibrary.setOnClickListener { selectTab(0) }
        tabPlaylists.setOnClickListener { selectTab(1) }
        tabNowPlaying.setOnClickListener { selectTab(2) }
        tabPractice.setOnClickListener { openPracticeMode() }

        // Sync ViewPager changes avec tabs
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabStyles(position)
                updatePlaybackBarVisibility()
            }
        })

        // Setup playback bar
        setupPlaybackBar()
    }

    /**
     * Configure la barre de lecture persistante
     */
    private fun setupPlaybackBar() {
        // Initialize colors
        colorActive = MaterialColors.getColor(
            this,
            R.attr.a2uMidiAccent,
            ContextCompat.getColor(this, R.color.midi_accent)
        )
        colorInactive = ContextCompat.getColor(this, R.color.midi_text_secondary)

        // Bind playback bar views
        playbackBarContainer = findViewById(R.id.playback_bar_container)
        playbackBarTitle = findViewById(R.id.playback_bar_title)
        playbackBarArtist = findViewById(R.id.playback_bar_artist)
        playbackBarSeek = findViewById(R.id.playback_bar_seek)
        playbackBarTimeCurrent = findViewById(R.id.playback_bar_time_current)
        playbackBarTimeTotal = findViewById(R.id.playback_bar_time_total)
        playbackBarPlayPause = findViewById(R.id.playback_bar_play_pause)
        playbackBarStop = findViewById(R.id.playback_bar_stop)
        playbackBarPrevious = findViewById(R.id.playback_bar_previous)
        playbackBarNext = findViewById(R.id.playback_bar_next)
        playbackBarShuffle = findViewById(R.id.playback_bar_shuffle)
        playbackBarRepeat = findViewById(R.id.playback_bar_repeat)
        playbackBarTrackInfo = findViewById(R.id.playback_bar_track_info)

        // Setup control buttons
        playbackBarPlayPause.setOnClickListener {
            val state = mediaController?.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
            when (state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController?.transportControls?.pause()
                else -> mediaController?.transportControls?.play()
            }
        }

        playbackBarStop.setOnClickListener {
            mediaController?.transportControls?.stop()
        }

        playbackBarPrevious.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }

        playbackBarNext.setOnClickListener {
            mediaController?.transportControls?.skipToNext()
        }

        playbackBarShuffle.setOnClickListener {
            mediaController?.sendCommand(MidiPlaybackService.COMMAND_TOGGLE_SHUFFLE, null, null)
        }

        playbackBarRepeat.setOnClickListener {
            mediaController?.sendCommand(MidiPlaybackService.COMMAND_CYCLE_REPEAT, null, null)
        }

        // Track info click goes to Now Playing tab
        playbackBarTrackInfo.setOnClickListener {
            selectTab(2) // Now Playing
        }

        // Setup seek bar
        playbackBarSeek.max = 1000
        playbackBarSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && playbackBarDurationMs > 0) {
                    val positionMs = (progress.toLong() * playbackBarDurationMs) / 1000
                    playbackBarTimeCurrent.text = formatTime(positionMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let { progress ->
                    if (playbackBarDurationMs > 0) {
                        val positionMs = (progress.toLong() * playbackBarDurationMs) / 1000
                        mediaController?.transportControls?.seekTo(positionMs)
                    }
                }
            }
        })
    }

    /**
     * Met à jour la visibilité de la barre de lecture selon l'onglet actif
     * La barre est visible sur Library (0) et Playlists (1), cachée sur Now Playing (2)
     * La barre est aussi cachée quand le mode Practice est actif
     */
    private fun updatePlaybackBarVisibility() {
        val isPlaying = lastPlaybackState == PlaybackStateCompat.STATE_PLAYING ||
                        lastPlaybackState == PlaybackStateCompat.STATE_PAUSED
        val currentTab = viewPager.currentItem
        val isPracticeVisible = practiceContainer.visibility == View.VISIBLE

        // Show playback bar on Library and Playlists tabs if there's playback
        // Hide on Now Playing tab (controls are already there)
        // Hide when practice mode is active (not useful there)
        playbackBarContainer.visibility = if (isPlaying && currentTab != 2 && !isPracticeVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * Met à jour l'UI de la barre de lecture avec l'état du playback
     */
    private fun updatePlaybackBarUI(state: PlaybackStateCompat) {
        // Update play/pause button
        val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
        playbackBarPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )

        // Extract shuffle/repeat and position/duration from extras
        state.extras?.let { extras ->
            playbackBarShuffleEnabled = extras.getBoolean(MidiPlaybackService.EXTRA_SHUFFLE_ENABLED, false)
            val repeatModeOrdinal = extras.getInt(MidiPlaybackService.EXTRA_REPEAT_MODE, 0)
            playbackBarRepeatMode = PlaybackQueueManager.RepeatMode.entries.getOrElse(repeatModeOrdinal) {
                PlaybackQueueManager.RepeatMode.NONE
            }

            updatePlaybackBarShuffleButton()
            updatePlaybackBarRepeatButton()

            // Position and duration
            val positionMs = extras.getLong(MidiPlaybackService.EXTRA_POSITION_MS, 0L)
            val durationMs = extras.getLong(MidiPlaybackService.EXTRA_DURATION_MS, 0L)
            playbackBarDurationMs = durationMs

            // Update seek bar and time labels
            playbackBarTimeCurrent.text = formatTime(positionMs)
            playbackBarTimeTotal.text = formatTime(durationMs)
            if (durationMs > 0) {
                val progress = ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000)
                playbackBarSeek.progress = progress
            } else {
                playbackBarSeek.progress = 0
            }
        }
    }

    /**
     * Met à jour les métadonnées de la barre de lecture (titre, artiste)
     */
    private fun updatePlaybackBarMetadata(metadata: android.support.v4.media.MediaMetadataCompat) {
        val title = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE) ?: getString(R.string.midi_no_track_playing)
        val artist = metadata.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST)

        playbackBarTitle.text = title
        if (!artist.isNullOrEmpty()) {
            playbackBarArtist.text = artist
            playbackBarArtist.visibility = View.VISIBLE
        } else {
            playbackBarArtist.visibility = View.GONE
        }
    }

    private fun updatePlaybackBarShuffleButton() {
        playbackBarShuffle.imageTintList = ColorStateList.valueOf(
            if (playbackBarShuffleEnabled) colorActive else colorInactive
        )
    }

    private fun updatePlaybackBarRepeatButton() {
        val tintColor = when (playbackBarRepeatMode) {
            PlaybackQueueManager.RepeatMode.NONE -> colorInactive
            else -> colorActive
        }
        playbackBarRepeat.imageTintList = ColorStateList.valueOf(tintColor)
        // Visual indicator for ONE mode (slight rotation)
        playbackBarRepeat.rotation = if (playbackBarRepeatMode == PlaybackQueueManager.RepeatMode.ONE) 15f else 0f
    }

    /**
     * Formate un temps en millisecondes en "m:ss" ou "h:mm:ss"
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun selectTab(position: Int) {
        viewPager.currentItem = position
        updateTabStyles(position)
        updatePlaybackBarVisibility()
    }

    private fun updateTabStyles(selectedPosition: Int) {
        val tabs = listOf(tabLibrary, tabPlaylists, tabNowPlaying)
        val activeColor = MaterialColors.getColor(
            this,
            R.attr.a2uMidiAccent,
            getColor(R.color.midi_accent)
        )
        tabs.forEachIndexed { index, tab ->
            if (index == selectedPosition) {
                tab.setTextColor(activeColor)
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tab.setTextColor(getColor(R.color.midi_text_secondary))
                tab.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    /**
     * Setup MediaBrowser pour connexion au service
     */
    private fun setupMediaBrowser() {
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MidiPlaybackService::class.java),
            connectionCallback,
            null
        )
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // BUG FIX 3.32: Annuler le timeout car la connexion a réussi
            cancelConnectionTimeout()

            val token = mediaBrowser.sessionToken
            mediaController = MediaControllerCompat(this@MidiPlayerActivity, token)
            MediaControllerCompat.setMediaController(this@MidiPlayerActivity, mediaController)

            // Setup transport controls callback (vérifie si pas déjà enregistré)
            if (!isControllerCallbackRegistered) {
                mediaController?.registerCallback(controllerCallback)
                isControllerCallbackRegistered = true
            }

            // Update UI avec l'état actuel
            updatePlaybackState(mediaController?.playbackState)

            // Initialize playback bar with current state and metadata
            mediaController?.playbackState?.let { updatePlaybackBarUI(it) }
            mediaController?.metadata?.let { updatePlaybackBarMetadata(it) }

            // Jouer le fichier externe en attente s'il y en a un
            pendingExternalUri?.let { uri ->
                pendingExternalUri = null
                playExternalFile(uri)
            }
        }

        override fun onConnectionFailed() {
            // BUG FIX 3.32: Annuler le timeout
            cancelConnectionTimeout()
            Toast.makeText(this@MidiPlayerActivity, R.string.midi_service_not_connected, Toast.LENGTH_SHORT).show()
        }

        override fun onConnectionSuspended() {
            // BUG FIX 3.32: Annuler le timeout
            cancelConnectionTimeout()
        }
    }

    /**
     * BUG FIX 3.32: Annule le timeout de connexion
     */
    private fun cancelConnectionTimeout() {
        connectionTimeoutRunnable?.let { connectionTimeoutHandler.removeCallbacks(it) }
        connectionTimeoutRunnable = null
    }

    /**
     * BUG FIX 3.32: Démarre le timeout de connexion
     */
    private fun startConnectionTimeout() {
        cancelConnectionTimeout()
        connectionTimeoutRunnable = Runnable {
            if (!mediaBrowser.isConnected) {
                Toast.makeText(this, R.string.midi_service_not_connected, Toast.LENGTH_SHORT).show()
            }
        }
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable!!, connectionTimeoutMs)
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlaybackState(state)
            // Update playback bar UI
            state?.let { updatePlaybackBarUI(it) }
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            viewModel.setPlaying(isPlaying)
        }

        override fun onMetadataChanged(metadata: android.support.v4.media.MediaMetadataCompat?) {
            // Update current track UI
            // Update playback bar metadata
            metadata?.let { updatePlaybackBarMetadata(it) }
            syncCurrentTrackFromMetadata(metadata)
        }
    }

    private fun syncCurrentTrackFromMetadata(metadata: android.support.v4.media.MediaMetadataCompat?) {
        val mediaIdStr = metadata?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val mediaId = mediaIdStr?.toLongOrNull()

        if (mediaId == null) {
            viewModel.setCurrentTrack(null)
            return
        }

        if (mediaId != viewModel.currentTrack.value?.id) {
            lifecycleScope.launch {
                val track = midiRepository.getTrackById(mediaId)
                if (track != null) {
                    viewModel.setCurrentTrack(track)
                }
            }
        }
    }

    private fun setupObservers() {
        // Observe scan progress
        lifecycleScope.launch {
            midiScanner.scanProgress.collect { progress ->
                if (progress.isScanning) {
                    // TODO: Show progress dialog
                    return@collect
                }
            }
        }
    }

    /**
     * Vérifie si un SoundFont est configuré, sinon demande à l'utilisateur
     */
    private fun checkSoundFontConfiguration() {
        lifecycleScope.launch {
            val isConfigured = soundFontManager.isSoundFontConfigured()
            viewModel.setSoundFontConfigured(isConfigured)

            if (!isConfigured) {
                Toast.makeText(
                    this@MidiPlayerActivity,
                    "Using built-in Sonivox synthesizer. You can load a custom SoundFont (.sf2) from Settings for better sound quality.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Ouvre le picker pour sélectionner un SoundFont
     */
    fun selectSoundFont() {
        soundFontPicker.launch(arrayOf("*/*"))
    }

    /**
     * Ouvre le picker pour sélectionner un dossier MIDI
     */
    fun selectMidiFolder() {
        midiFolderPicker.launch(null)
    }

    /**
     * Expose le scanProgress pour les fragments
     */
    fun getScanProgress() = midiScanner.scanProgress

    /**
     * Gère la sélection d'un SoundFont
     */
    private fun handleSoundFontSelected(uri: Uri) {
        lifecycleScope.launch {
            // Prend permission persistante
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            // Import le SoundFont
            Toast.makeText(this@MidiPlayerActivity, R.string.midi_importing_soundfont, Toast.LENGTH_SHORT).show()

            when (val result = soundFontManager.importSoundFont(uri)) {
                is SoundFontManager.ImportResult.Success -> {
                    val sizeMB = result.fileSize / 1024 / 1024
                    Toast.makeText(
                        this@MidiPlayerActivity,
                        "SoundFont imported: ${result.fileName} (${sizeMB} MB)",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Afficher un avertissement si problèmes détectés
                    if (result.warning != null) {
                        showSf2WarningDialog(result.fileName, result.warning)
                    }

                    viewModel.setSoundFontConfigured(true)

                    // Recharger le SoundFont dans le service
                    val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                        action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                        putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, result.filePath)
                    }
                    startService(intent)

                    Toast.makeText(
                        this@MidiPlayerActivity,
                        "Loading SoundFont into MIDI engine...",
                        Toast.LENGTH_LONG
                    ).show()

                    // Rafraîchir le dialogue de sélection s'il est ouvert
                    val dialog = supportFragmentManager.findFragmentByTag(SynthesizerSelectionDialog.TAG)
                            as? SynthesizerSelectionDialog
                    dialog?.refreshState()
                }
                is SoundFontManager.ImportResult.Error -> {
                    Toast.makeText(
                        this@MidiPlayerActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Gère la sélection d'un dossier MIDI
     */
    private fun handleMidiFolderSelected(uri: Uri) {
        lifecycleScope.launch {
            // Prend permission persistante
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            // Lance le scan
            Toast.makeText(this@MidiPlayerActivity, R.string.midi_scanning_folder, Toast.LENGTH_SHORT).show()

            val count = midiScanner.scanMidiFolder(uri)

            Toast.makeText(
                this@MidiPlayerActivity,
                "Scan completed: $count MIDI files found",
                Toast.LENGTH_LONG
            ).show()

            // Sauvegarde l'URI du dossier
            settingsRepository.saveMidiFolderUri(uri.toString())
        }
    }

    /**
     * Met à jour l'UI selon l'état de lecture
     */
    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        val newState = state?.state ?: PlaybackStateCompat.STATE_NONE

        // Ne logger que si l'état a changé
        if (newState != lastPlaybackState) {
            lastPlaybackState = newState

            // Update playback bar visibility when state changes
            updatePlaybackBarVisibility()
        }

        // Actions toujours exécutées
        if (newState == PlaybackStateCompat.STATE_STOPPED) {
            AudioPlaybackManager.unregisterPlayback(AudioPlaybackManager.AudioSource.MIDI)
        }
    }

    override fun onStart() {
        super.onStart()
        // Ne connecte que si pas déjà connecté (cas du retour après background)
        if (!mediaBrowser.isConnected) {
            // BUG FIX 3.32: Démarrer le timeout avant la connexion
            startConnectionTimeout()
            mediaBrowser.connect()
        } else if (!isControllerCallbackRegistered) {
            // Déjà connecté, re-enregistre le callback seulement si pas déjà enregistré
            mediaController?.registerCallback(controllerCallback)
            isControllerCallbackRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (isControllerCallbackRegistered) {
            mediaController?.unregisterCallback(controllerCallback)
            isControllerCallbackRegistered = false
        }

        // Ne déconnecte pas le MediaBrowser si la lecture est en cours
        // Le service continuera en foreground avec la notification
        val isPlaying = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING ||
                        mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PAUSED

        if (!isPlaying) {
            mediaBrowser.disconnect()
        }
    }

    override fun onDestroy() {
        // IMPORTANT: Appeler super.onDestroy() en premier pour garantir le nettoyage parent
        super.onDestroy()

        // Nettoyer le BackStackListener pour éviter les fuites mémoire
        practiceBackStackListener?.let {
            supportFragmentManager.removeOnBackStackChangedListener(it)
            practiceBackStackListener = null
        }

        // Ne déconnecte que si la musique ne joue pas
        // Si elle joue, le service continue en foreground indépendamment
        val isPlaying = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING ||
                        mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PAUSED

        if (!isPlaying && mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
        // Note: Si isPlaying, on laisse le MediaBrowser se déconnecter naturellement
        // Le service reste en vie grâce à startService() + startForeground()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_midi_player, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_audio_settings -> {
                showAudioSettingsDialog()
                true
            }
            R.id.action_color_settings -> {
                showColorSettingsDialog()
                true
            }
            R.id.action_select_soundfont -> {
                showSynthesizerSelectionDialog()
                true
            }
            R.id.action_select_midi_folder -> {
                selectMidiFolder()
                true
            }
            R.id.action_refresh_library -> {
                refreshLibrary()
                true
            }
            R.id.action_clear_library -> {
                showClearLibraryConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Affiche le dialog de personnalisation des couleurs
     */
    private fun showColorSettingsDialog() {
        val dialog = ColorSettingsDialog.newInstance()
        dialog.onColorsChanged = {
            // Refresh the practice fragment if it's visible
            val practiceFragment = supportFragmentManager.findFragmentByTag("practice")
            if (practiceFragment is PianoPracticeFragment) {
                practiceFragment.refreshColors()
            }
        }
        dialog.show(supportFragmentManager, ColorSettingsDialog.TAG)
    }

    /**
     * Rafraîchit la bibliothèque (rescanne le dossier)
     */
    private fun refreshLibrary() {
        lifecycleScope.launch {
            // Récupère l'URI du dossier MIDI depuis les settings
            val folderUri = settingsRepository.getMidiFolderUri()

            if (folderUri == null) {
                Toast.makeText(
                    this@MidiPlayerActivity,
                    "Aucun dossier MIDI configuré. Sélectionnez un dossier d'abord.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            Toast.makeText(
                this@MidiPlayerActivity,
                R.string.midi_library_refreshing,
                Toast.LENGTH_SHORT
            ).show()

            // Rescanne avec refresh=true pour vider et re-scanner
            val count = midiScanner.scanMidiFolder(folderUri.toUri(), refresh = true)

            Toast.makeText(
                this@MidiPlayerActivity,
                getString(R.string.midi_folder_scan_complete, count),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Affiche la confirmation avant de vider la bibliothèque
     */
    private fun showClearLibraryConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.midi_clear_library)
            .setMessage(R.string.midi_clear_library_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                clearLibrary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Vide complètement la bibliothèque
     */
    private fun clearLibrary() {
        lifecycleScope.launch {
            midiRepository.clearAllTracks()
            Toast.makeText(
                this@MidiPlayerActivity,
                R.string.midi_library_cleared,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Vide une playlist (retire tous les tracks)
     */
    fun clearPlaylist(playlistId: Long) {
        lifecycleScope.launch {
            playlistRepository.clearPlaylist(playlistId)
        }
    }

    /**
     * Play a single track via MediaController (legacy method)
     * Appelé par les fragments quand l'utilisateur clique sur un track
     */
    @Suppress("unused")
    fun playTrack(track: com.Atom2Universe.app.midi.data.MidiTrack) {
        playTracks(listOf(track), 0)
    }

    /**
     * Play a list of tracks starting at the given index
     * Appelé par les fragments pour jouer une liste complète (ex: album, dossier)
     */
    fun playTracks(tracks: List<com.Atom2Universe.app.midi.data.MidiTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            return
        }

        if (mediaController == null) {
            Toast.makeText(this, R.string.midi_service_not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Vérifie s'il y a un conflit audio (ex: radio en cours)
        AudioPlaybackManager.requestPlayback(
            AudioPlaybackManager.AudioSource.MIDI,
            this
        ) {
            // Callback exécuté si la lecture est autorisée
            startMidiPlayback(tracks, startIndex)
        }
    }

    /**
     * Démarre effectivement la lecture MIDI (après vérification des conflits)
     */
    private fun startMidiPlayback(tracks: List<com.Atom2Universe.app.midi.data.MidiTrack>, startIndex: Int) {
        // Démarre le service explicitement pour qu'il reste en vie en background
        val serviceIntent = Intent(this, MidiPlaybackService::class.java)
        startService(serviceIntent)

        // Enregistre cette source comme active avec callback pour l'arrêter
        AudioPlaybackManager.registerPlayback(AudioPlaybackManager.AudioSource.MIDI) {
            // Callback pour arrêter la lecture MIDI si une autre source prend le relais
            mediaController?.transportControls?.stop()
        }

        // Sérialiser la liste des tracks dans le bundle
        val bundle = Bundle().apply {
            putInt("queue_size", tracks.size)
            putInt("start_index", startIndex)

            // Sérialiser chaque track
            tracks.forEachIndexed { index, track ->
                putLong("track_${index}_id", track.id)
                putString("track_${index}_path", track.filePath)
                putString("track_${index}_title", track.title)
                putString("track_${index}_artist", track.artist)
                putString("track_${index}_album", track.album)
            }
        }

        // Arrête le playback actuel si nécessaire
        if (mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            mediaController?.transportControls?.stop()
        }

        // Lance la queue
        val startTrack = tracks[startIndex]
        mediaController?.transportControls?.playFromUri("midi://queue/${startTrack.id}".toUri(), bundle)
    }

    /**
     * Play a list of tracks with shuffle enabled from a specific index
     * Appelé par NowPlayingFragment lors du long-press shuffle avec scope
     */
    fun playTracksWithShuffle(tracks: List<com.Atom2Universe.app.midi.data.MidiTrack>, startIndex: Int) {
        if (tracks.isEmpty()) {
            return
        }

        if (mediaController == null) {
            Toast.makeText(this, R.string.midi_service_not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Vérifie s'il y a un conflit audio (ex: radio en cours)
        AudioPlaybackManager.requestPlayback(
            AudioPlaybackManager.AudioSource.MIDI,
            this
        ) {
            // Callback exécuté si la lecture est autorisée
            startMidiPlaybackWithShuffle(tracks, startIndex)
        }
    }

    /**
     * Démarre effectivement la lecture MIDI avec shuffle activé
     */
    private fun startMidiPlaybackWithShuffle(tracks: List<com.Atom2Universe.app.midi.data.MidiTrack>, startIndex: Int) {
        // Démarre le service explicitement pour qu'il reste en vie en background
        val serviceIntent = Intent(this, MidiPlaybackService::class.java)
        startService(serviceIntent)

        // Enregistre cette source comme active avec callback pour l'arrêter
        AudioPlaybackManager.registerPlayback(AudioPlaybackManager.AudioSource.MIDI) {
            // Callback pour arrêter la lecture MIDI si une autre source prend le relais
            mediaController?.transportControls?.stop()
        }

        // Sérialiser la liste des tracks dans le bundle
        val bundle = Bundle().apply {
            putInt("queue_size", tracks.size)
            putInt("start_index", startIndex)
            putBoolean("enable_shuffle", true)  // Flag pour activer le shuffle immédiatement

            // Sérialiser chaque track
            tracks.forEachIndexed { index, track ->
                putLong("track_${index}_id", track.id)
                putString("track_${index}_path", track.filePath)
                putString("track_${index}_title", track.title)
                putString("track_${index}_artist", track.artist)
                putString("track_${index}_album", track.album)
            }
        }

        // Arrête le playback actuel si nécessaire
        if (mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            mediaController?.transportControls?.stop()
        }

        // Lance la queue avec shuffle
        val startTrack = tracks[startIndex]
        mediaController?.transportControls?.playFromUri("midi://queue/${startTrack.id}".toUri(), bundle)
    }

    override fun onSupportNavigateUp(): Boolean {
        navigateBackToHub()
        return true
    }

    /**
     * Navigue vers le Hub si l'activité est la racine de la tâche (lancée depuis widget/raccourci),
     * sinon termine simplement l'activité pour revenir à l'écran précédent.
     */
    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    /**
     * Charge les paramètres audio sauvegardés et les applique au mixer
     */
    private fun loadAudioSettings() {
        lifecycleScope.launch {
            try {
                // Charger le preset de normalisation
                val presetIndex = settingsRepository.getMixerPreset()
                val preset = MidiAudioMixer.NormalizationPreset.entries.getOrElse(presetIndex) {
                    MidiAudioMixer.NormalizationPreset.MEDIUM
                }
                MidiAudioMixer.setPreset(preset)

                // Charger le master gain
                val gain = settingsRepository.getMasterGain()
                MidiAudioMixer.setMasterGain(gain)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Affiche le dialog des paramètres audio
     */
    fun showAudioSettingsDialog() {
        // Show SF2 settings section only when SF2 engine is active
        val isSf2Active = viewModel.soundFontConfigured.value == true
        val dialog = AudioSettingsDialog.newInstance(showSf2Settings = isSf2Active)

        // Callback pour appliquer le reverb en temps réel
        dialog.onReverbChanged = { reverbPreset ->
            val intent = Intent(this, MidiPlaybackService::class.java).apply {
                action = MidiPlaybackService.ACTION_SET_REVERB
                putExtra(MidiPlaybackService.EXTRA_REVERB_PRESET, reverbPreset)
            }
            startService(intent)
        }

        // Callbacks EQ en temps réel
        dialog.onEqEnabled = { enabled ->
            val intent = Intent(this, MidiPlaybackService::class.java).apply {
                action = MidiPlaybackService.ACTION_SET_EQ_ENABLED
                putExtra(MidiPlaybackService.EXTRA_EQ_ENABLED, enabled)
            }
            startService(intent)
        }
        dialog.onEqBandChanged = { band, millibels ->
            val intent = Intent(this, MidiPlaybackService::class.java).apply {
                action = MidiPlaybackService.ACTION_SET_EQ_BAND
                putExtra(MidiPlaybackService.EXTRA_EQ_BAND, band)
                putExtra(MidiPlaybackService.EXTRA_EQ_MILLIBELS, millibels)
            }
            startService(intent)
        }

        dialog.show(supportFragmentManager, AudioSettingsDialog.TAG)
    }

    /**
     * Affiche le dialog de sélection du synthétiseur
     * @param onSettingsChanged Callback optionnel appelé quand les paramètres du synthétiseur changent
     *                          Paramètres: (synthMode: String, sf2Path: String?)
     */
    fun showSynthesizerSelectionDialog(onSettingsChanged: ((String, String?) -> Unit)? = null) {
        val dialog = SynthesizerSelectionDialog.newInstance()

        // Callback: Sonivox sélectionné
        dialog.onSonivoxSelected = {
            lifecycleScope.launch {
                Toast.makeText(
                    this@MidiPlayerActivity,
                    R.string.midi_synth_switched_sonivox,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setSoundFontConfigured(false)

                // Recharger avec Sonivox (chemin vide)
                val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                    action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                    putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, "")
                }
                startService(intent)

                // Notify caller that settings changed
                onSettingsChanged?.invoke(SettingsRepository.SYNTH_MODE_SONIVOX, null)
            }
        }

        // Callback: Demande d'import SF2
        dialog.onImportSf2Requested = {
            selectSoundFont()
        }

        // Callback: SF2 sélectionné (déjà importé)
        dialog.onSf2Selected = { sf2Path ->
            lifecycleScope.launch {
                val sf2Label = settingsRepository.getSoundFontLabel() ?: "SoundFont"
                Toast.makeText(
                    this@MidiPlayerActivity,
                    getString(R.string.midi_synth_switched_sf2, sf2Label),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setSoundFontConfigured(true)

                // Recharger avec le SF2
                val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                    action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                    putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, sf2Path)
                }
                startService(intent)

                // Notify caller that settings changed
                onSettingsChanged?.invoke(SettingsRepository.SYNTH_MODE_SF2, sf2Path)
            }
        }

        // Callback: SF2 supprimé
        dialog.onSf2Removed = {
            lifecycleScope.launch {
                Toast.makeText(
                    this@MidiPlayerActivity,
                    R.string.midi_synth_sf2_removed,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setSoundFontConfigured(false)

                // Recharger avec Sonivox
                val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                    action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                    putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, "")
                }
                startService(intent)

                // Notify caller that settings changed
                onSettingsChanged?.invoke(SettingsRepository.SYNTH_MODE_SONIVOX, null)
            }
        }

        // Callback: Mode hybride sélectionné
        dialog.onHybridSelected = { sf2Path, programs, useSf2ForDrums ->
            lifecycleScope.launch {
                Toast.makeText(
                    this@MidiPlayerActivity,
                    getString(R.string.midi_synth_switched_hybrid, programs.size),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setSoundFontConfigured(true)

                // Recharger en mode hybride
                val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                    action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                    putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, sf2Path)
                    putExtra(MidiPlaybackService.EXTRA_HYBRID_MODE, true)
                    putIntegerArrayListExtra(MidiPlaybackService.EXTRA_HYBRID_PROGRAMS, ArrayList(programs))
                    putExtra(MidiPlaybackService.EXTRA_HYBRID_DRUMS_SF2, useSf2ForDrums)
                }
                startService(intent)

                // Notify caller that settings changed
                onSettingsChanged?.invoke(SettingsRepository.SYNTH_MODE_HYBRID, sf2Path)
            }
        }

        // Callback: FluidSynth sélectionné
        dialog.onFluidSynthSelected = { sf2Path ->
            lifecycleScope.launch {
                val sf2Label = settingsRepository.getSoundFontLabel() ?: "SoundFont"
                Toast.makeText(
                    this@MidiPlayerActivity,
                    getString(R.string.midi_synth_switched_fluidsynth, sf2Label),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setSoundFontConfigured(true)

                // Recharger avec FluidSynth
                val intent = Intent(this@MidiPlayerActivity, MidiPlaybackService::class.java).apply {
                    action = MidiPlaybackService.ACTION_RELOAD_SOUNDFONT
                    putExtra(MidiPlaybackService.EXTRA_SOUNDFONT_PATH, sf2Path)
                    putExtra(MidiPlaybackService.EXTRA_FLUIDSYNTH_MODE, true)
                }
                startService(intent)

                // Notify caller that settings changed
                onSettingsChanged?.invoke(SettingsRepository.SYNTH_MODE_FLUIDSYNTH, sf2Path)
            }
        }

        dialog.show(supportFragmentManager, SynthesizerSelectionDialog.TAG)
    }

    /**
     * Open practice mode from the toolbar button
     * Always opens FREE PLAY mode (just the keyboard, no MIDI file)
     * To practice a specific track/channel, user should use the channel training buttons
     */
    private fun openPracticeMode() {
        // STOP any current playback (not pause) before entering practice mode
        mediaController?.transportControls?.stop()

        // Always open free play mode from the Practice tab
        // For track-specific practice, use the channel buttons in NowPlayingFragment
        navigateToPractice(
            trackFilePath = "",  // Empty path = free play mode
            channelNumber = 0,
            noteRangeMin = 36,
            noteRangeMax = 96,
            instrumentName = getString(R.string.midi_practice_mode),
            trackTitle = getString(R.string.midi_practice_mode),
            trackIndex = 0
        )
    }

    // Listener for practice fragment backstack changes (reused to avoid duplicates)
    private var practiceBackStackListener: androidx.fragment.app.FragmentManager.OnBackStackChangedListener? = null

    /**
     * Navigate to practice mode for a specific channel
     */
    fun navigateToPractice(
        trackFilePath: String,
        channelNumber: Int,
        noteRangeMin: Int,
        noteRangeMax: Int,
        instrumentName: String,
        trackTitle: String,
        trackIndex: Int,
        programNumber: Int = 0
    ) {
        // IMPORTANT: Arrêter complètement le playback principal avant d'entrer en mode practice
        // Cela évite que le callback onCompletion du synthétiseur ne déclenche skipToNext
        // et ne charge le morceau suivant, corrompant la session de practice
        mediaController?.transportControls?.stop()

        val args = PracticeArgs(
            trackFilePath = trackFilePath,
            channelNumber = channelNumber,
            noteRangeMin = noteRangeMin,
            noteRangeMax = noteRangeMax,
            instrumentName = instrumentName,
            trackTitle = trackTitle,
            trackIndex = trackIndex,
            programNumber = programNumber
        )

        android.util.Log.d(TAG, "navigateToPractice: file=$trackFilePath, channel=$channelNumber, title=$trackTitle")

        val fragment = PianoPracticeFragment.newInstance(args)

        // Show practice container and hide playback bar
        practiceContainer.visibility = View.VISIBLE
        updatePlaybackBarVisibility()

        // ALWAYS pop any existing practice entry from backstack to ensure clean state
        // This handles both cases: fragment visible in container, or just an entry in backstack
        // popBackStackImmediate returns false if "practice" is not found, which is fine
        val popped = supportFragmentManager.popBackStackImmediate("practice", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        android.util.Log.d(TAG, "navigateToPractice: popBackStack result=$popped, backStackCount=${supportFragmentManager.backStackEntryCount}")

        // Add new fragment to container with fresh state
        supportFragmentManager.beginTransaction()
            .replace(R.id.practice_container, fragment, "practice")
            .addToBackStack("practice")
            .commit()
        android.util.Log.d(TAG, "navigateToPractice: fragment transaction committed")

        // Listen for backstack changes to hide container when fragment is popped
        // Only add listener if not already registered
        if (practiceBackStackListener == null) {
            practiceBackStackListener = object : androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    val practiceFragment = supportFragmentManager.findFragmentByTag("practice")
                    if (practiceFragment == null) {
                        practiceContainer.visibility = View.GONE
                        updatePlaybackBarVisibility()
                        refreshPlaybackAfterPractice()
                    }
                }
            }
            supportFragmentManager.addOnBackStackChangedListener(practiceBackStackListener!!)
        }
    }

    /**
     * Navigate to two-hands practice mode (piano with left and right hand channels)
     */
    fun navigateToTwoHandsPractice(
        trackFilePath: String,
        trackTitle: String,
        leftHandChannel: Int,
        rightHandChannel: Int,
        leftHandName: String,
        rightHandName: String,
        leftHandNoteRange: Pair<Int, Int>,
        rightHandNoteRange: Pair<Int, Int>
    ) {
        // Stop main playback before entering practice mode
        mediaController?.transportControls?.stop()

        // Calculate combined note range
        val noteRangeMin = minOf(leftHandNoteRange.first, rightHandNoteRange.first)
        val noteRangeMax = maxOf(leftHandNoteRange.second, rightHandNoteRange.second)

        val args = PracticeArgs(
            trackFilePath = trackFilePath,
            channelNumber = rightHandChannel,  // Default to right hand for single-channel operations
            noteRangeMin = noteRangeMin,
            noteRangeMax = noteRangeMax,
            instrumentName = "$leftHandName + $rightHandName",
            trackTitle = trackTitle,
            trackIndex = 0,
            programNumber = 0,
            // Two-hands specific
            isTwoHandsMode = true,
            leftHandChannel = leftHandChannel,
            rightHandChannel = rightHandChannel,
            leftHandName = leftHandName,
            rightHandName = rightHandName,
            leftHandNoteRangeMin = leftHandNoteRange.first,
            leftHandNoteRangeMax = leftHandNoteRange.second,
            rightHandNoteRangeMin = rightHandNoteRange.first,
            rightHandNoteRangeMax = rightHandNoteRange.second
        )

        android.util.Log.d(TAG, "navigateToTwoHandsPractice: file=$trackFilePath, LH=$leftHandChannel, RH=$rightHandChannel, title=$trackTitle")

        val fragment = PianoPracticeFragment.newInstance(args)

        // Show practice container and hide playback bar
        practiceContainer.visibility = View.VISIBLE
        updatePlaybackBarVisibility()

        // ALWAYS pop any existing practice entry from backstack to ensure clean state
        // This handles both cases: fragment visible in container, or just an entry in backstack
        // popBackStackImmediate returns false if "practice" is not found, which is fine
        val popped = supportFragmentManager.popBackStackImmediate("practice", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        android.util.Log.d(TAG, "navigateToTwoHandsPractice: popBackStack result=$popped, backStackCount=${supportFragmentManager.backStackEntryCount}")

        // Add new fragment to container with fresh state
        supportFragmentManager.beginTransaction()
            .replace(R.id.practice_container, fragment, "practice")
            .addToBackStack("practice")
            .commit()
        android.util.Log.d(TAG, "navigateToTwoHandsPractice: fragment transaction committed")

        // Listen for backstack changes (reuse same listener)
        if (practiceBackStackListener == null) {
            practiceBackStackListener = object : androidx.fragment.app.FragmentManager.OnBackStackChangedListener {
                override fun onBackStackChanged() {
                    val practiceFragment = supportFragmentManager.findFragmentByTag("practice")
                    if (practiceFragment == null) {
                        practiceContainer.visibility = View.GONE
                        updatePlaybackBarVisibility()
                        refreshPlaybackAfterPractice()
                    }
                }
            }
            supportFragmentManager.addOnBackStackChangedListener(practiceBackStackListener!!)
        }
    }

    private fun refreshPlaybackAfterPractice() {
        val intent = Intent(this, MidiPlaybackService::class.java).apply {
            action = MidiPlaybackService.ACTION_REFRESH_AFTER_PRACTICE
        }
        startService(intent)
    }

    /**
     * Affiche un dialog d'avertissement pour les problèmes détectés dans un fichier SF2.
     * Permet à l'utilisateur de savoir que le fichier pourrait avoir des problèmes audio.
     */
    private fun showSf2WarningDialog(fileName: String, warning: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Avertissement SoundFont")
            .setMessage("$fileName\n\n$warning\n\nLe fichier a été importé mais certains instruments pourraient ne pas fonctionner correctement.")
            .setPositiveButton("Compris") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Voir détails") { _, _ ->
                // Affiche le rapport détaillé
                lifecycleScope.launch {
                    val path = soundFontManager.getCurrentSoundFontPath()
                    if (path != null) {
                        val report = com.Atom2Universe.app.midi.sf2.Sf2FileCache.getValidationReport(path)
                        AlertDialog.Builder(this@MidiPlayerActivity)
                            .setTitle("Rapport de validation SF2")
                            .setMessage(report)
                            .setPositiveButton("Fermer", null)
                            .show()
                    }
                }
            }
            .setCancelable(true)
            .show()
    }

    companion object {
        private const val TAG = "MidiPlayerActivity"
    }
}
