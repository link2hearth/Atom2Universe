package com.Atom2Universe.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.audio.AudioSubHubActivity
import com.Atom2Universe.app.games.GamesActivity
import com.Atom2Universe.app.creative.CreativeHubActivity
import com.Atom2Universe.app.reading.ReadingHubActivity
import com.Atom2Universe.app.hub.HubTile
import com.Atom2Universe.app.hub.HubTilesAdapter
import com.Atom2Universe.app.hub.HubTileTouchCallback
import com.Atom2Universe.app.hub.QuickAccessItem
import com.Atom2Universe.app.music.lyrics.LyricsManager
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.CacheCleanerManager
import com.Atom2Universe.app.music.sync.peer.A2USyncService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioHubActivity : ThemedActivity(), AudioHubPlaybackController.Listener, SleepTimerManager.Listener {

    companion object {
        private const val PREFS_NAME = "audio_hub_prefs"
        private const val KEY_TILE_ORDER = "tile_order"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_TILE_COLORS = "tile_colors"
        private const val KEY_QUICK_ACCESS = "quick_access"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
        private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        private const val VIEW_MODE_LIST = "list"
        private const val VIEW_MODE_GRID = "grid"

        // SavedInstanceState keys for locale change preservation
        private const val STATE_IS_GRID_MODE = "state_is_grid_mode"
        private const val STATE_IS_EDIT_MODE = "state_is_edit_mode"

        // Tile IDs
        const val TILE_AUDIO = "audio"
        const val TILE_MUSIC = "music"
        const val TILE_MIDI = "midi"
        const val TILE_RADIO = "radio"
        const val TILE_EDITOR = "editor"
        const val TILE_SF2_CREATOR = "sf2_creator"
        const val TILE_STATS = "stats"  // kept for prefs migration
        const val TILE_GAMES = "games"
        const val TILE_CLICKER = "clicker"
        const val TILE_CREATIVE = "creative"
        const val TILE_NOTES = "notes"
        const val TILE_READING = "reading"
        // Default order: Audio (sub-hub), Games, Creative, Reading
        val DEFAULT_ORDER = listOf(TILE_AUDIO, TILE_GAMES, TILE_CREATIVE, TILE_READING)

        // Extra to indicate we're returning from a module (don't redirect)
        const val EXTRA_FROM_MODULE = "from_module"
    }

    // Permission request launcher (audio)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionDeniedDialog()
        }
        // Enchaîne la demande de notification après la permission audio
        checkAndRequestNotificationPermission()
    }

    // Permission request launcher (notifications)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Pas d'action spécifique selon le résultat : le toast dans RadioPlaybackHolder
        // prévient l'utilisateur si la permission est manquante lors de l'utilisation
    }

    private lateinit var playbackController: AudioHubPlaybackController
    private lateinit var tilesAdapter: HubTilesAdapter
    private lateinit var prefs: SharedPreferences

    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var stopButton: FloatingActionButton
    private lateinit var nowPlayingText: TextView
    private lateinit var sourceLabel: TextView
    private lateinit var tilesRecyclerView: RecyclerView
    private lateinit var viewToggleButton: ImageButton
    private lateinit var confirmEditFab: FloatingActionButton
    private lateinit var languageButton: Button
    private lateinit var sleepTimerButton: ImageButton
    private lateinit var settingsButton: ImageButton

    private var isGridMode = true
    private var isEditMode = false
    private var pendingRestoreEditMode = false

    // CoroutineScope pour les opérations en arrière-plan
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // All available tiles using generalized HubTile
    private val defaultTiles = listOf(
        HubTile(
            id = TILE_AUDIO,
            titleRes = R.string.hub_audio_title,
            descriptionRes = R.string.hub_audio_desc,
            iconRes = R.drawable.ic_audio_globe_animated,
            defaultColorRes = R.color.audio_hub_tile_audio,
            activityClass = AudioSubHubActivity::class.java
        ),
        HubTile(
            id = TILE_GAMES,
            titleRes = R.string.hub_games_title,
            descriptionRes = R.string.hub_games_desc,
            iconRes = R.drawable.ic_games,
            defaultColorRes = R.color.audio_hub_tile_games,
            activityClass = GamesActivity::class.java
        ),
        HubTile(
            id = TILE_CREATIVE,
            titleRes = R.string.hub_creative_title,
            descriptionRes = R.string.hub_creative_desc,
            iconRes = R.drawable.ic_creative,
            defaultColorRes = R.color.audio_hub_tile_creative,
            activityClass = CreativeHubActivity::class.java
        ),
        HubTile(
            id = TILE_READING,
            titleRes = R.string.hub_reading_title,
            descriptionRes = R.string.hub_reading_desc,
            iconRes = R.drawable.ic_hub_books,
            defaultColorRes = R.color.audio_hub_tile_books,
            activityClass = ReadingHubActivity::class.java
        )
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_audio_hub)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Restore state after locale change recreation
        if (savedInstanceState != null) {
            isGridMode = savedInstanceState.getBoolean(STATE_IS_GRID_MODE, true)
            pendingRestoreEditMode = savedInstanceState.getBoolean(STATE_IS_EDIT_MODE, false)
        }

        // Initialize lyrics manager
        LyricsManager.init(this)

        // Lancer le nettoyage des fichiers orphelins en arrière-plan
        launchCacheCleanup()

        // Démarrer la synchronisation P2P LAN
        A2USyncService.startLanSync(this)

        // Initialize views
        playPauseButton = findViewById(R.id.play_pause_button)
        stopButton = findViewById(R.id.stop_button)
        nowPlayingText = findViewById(R.id.now_playing_text)
        sourceLabel = findViewById(R.id.source_label)
        tilesRecyclerView = findViewById(R.id.tiles_recycler_view)
        viewToggleButton = findViewById(R.id.view_toggle_button)
        confirmEditFab = findViewById(R.id.confirm_edit_fab)
        languageButton = findViewById(R.id.language_button)
        sleepTimerButton = findViewById(R.id.sleep_timer_button)
        settingsButton = findViewById(R.id.settings_button)

        // Initialize playback controller
        playbackController = AudioHubPlaybackController(this)

        setupPlaybackControls()
        setupViewToggle()
        setupConfirmEditFab()
        setupEditModeBackPress()
        setupLanguageButton()
        updateLanguageButtonLabel()
        setupSleepTimerButton()
        setupSettingsButton()
        setupModuleTiles()

        // Restore saved view mode (only if not restored from savedInstanceState)
        if (savedInstanceState == null) {
            isGridMode = prefs.getString(KEY_VIEW_MODE, VIEW_MODE_GRID) == VIEW_MODE_GRID
        }
        updateViewMode()

        // Restore edit mode after recreation if needed
        if (pendingRestoreEditMode) {
            pendingRestoreEditMode = false
            tilesRecyclerView.post { enterEditMode() }
        }

        // Check and request permissions on first launch
        checkAndRequestPermissions()
    }

    /**
     * Checks if required permissions are granted and requests them if needed.
     * Shows an explanation dialog before requesting permissions.
     */
    private fun checkAndRequestPermissions() {
        val permission = getRequiredAudioPermission()

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Permission audio déjà accordée : enchaîne sur la notification
                checkAndRequestNotificationPermission()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // User denied before, show rationale
                showPermissionRationaleDialog()
            }
            !prefs.getBoolean(KEY_PERMISSION_ASKED, false) -> {
                // First time - show explanation then request
                showFirstTimePermissionDialog()
            }
            else -> {
                // Permission was denied and "Don't ask again" was checked
                // Don't bother the user again, they'll be prompted when entering Music/MIDI
                checkAndRequestNotificationPermission()
            }
        }
    }

    /**
     * Demande la permission POST_NOTIFICATIONS au premier démarrage sur Android 13+.
     * Appelée après la gestion de la permission audio pour éviter d'afficher deux dialogs simultanément.
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Déjà accordée, rien à faire
            }
            !prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false) -> {
                prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true) }
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_notification_title)
                    .setMessage(R.string.permission_notification_message)
                    .setPositiveButton(R.string.permission_dialog_grant) { _, _ ->
                        requestNotificationPermissionLauncher.launch(permission)
                    }
                    .setNegativeButton(R.string.permission_dialog_later, null)
                    .show()
            }
            // Déjà refusée et "Ne plus demander" coché : on ne redemande pas
        }
    }

    /**
     * Returns the required permission based on Android version.
     */
    private fun getRequiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Shows explanation dialog on first launch before requesting permission.
     */
    private fun showFirstTimePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.permission_dialog_message)
            .setPositiveButton(R.string.permission_dialog_grant) { _, _ ->
                prefs.edit { putBoolean(KEY_PERMISSION_ASKED, true) }
                requestPermissionLauncher.launch(getRequiredAudioPermission())
            }
            .setNegativeButton(R.string.permission_dialog_later) { _, _ ->
                prefs.edit { putBoolean(KEY_PERMISSION_ASKED, true) }
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows rationale dialog when user previously denied permission.
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.permission_rationale_message)
            .setPositiveButton(R.string.permission_dialog_grant) { _, _ ->
                requestPermissionLauncher.launch(getRequiredAudioPermission())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Shows dialog when permission was denied.
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_denied_title)
            .setMessage(R.string.permission_denied_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        playbackController.addListener(this)
        playbackController.start()
        SleepTimerManager.addListener(this)
        updateSleepTimerButtonState()
    }

    override fun onStop() {
        super.onStop()
        playbackController.removeListener(this)
        playbackController.stop()
        SleepTimerManager.removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when returning to activity
        playbackController.refreshState()
        updateLanguageButtonLabel()
        // Reload tiles to pick up any new quick-access shortcuts set from sub-hubs
        if (::tilesAdapter.isInitialized) {
            loadTiles()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save state for locale change recreation
        outState.putBoolean(STATE_IS_GRID_MODE, isGridMode)
        outState.putBoolean(STATE_IS_EDIT_MODE, isEditMode)
    }

    // AudioHubPlaybackController.Listener implementation
    override fun onPlaybackStateChanged(state: AudioHubPlaybackController.PlaybackState) {
        updateUI(state)
    }

    private fun updateUI(state: AudioHubPlaybackController.PlaybackState) {
        // Update play/pause button
        if (state.isPlaying) {
            playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play)
        }

        // Update now playing text
        when (state.source) {
            AudioHubPlaybackController.ActiveSource.NONE -> {
                nowPlayingText.text = getString(R.string.audio_hub_no_playback)
                sourceLabel.visibility = View.GONE
            }
            else -> {
                nowPlayingText.text = state.title ?: getString(R.string.audio_hub_no_playback)

                // Show source label with subtitle if available
                val sourceName = when (state.source) {
                    AudioHubPlaybackController.ActiveSource.MUSIC -> getString(R.string.audio_hub_music_title)
                    AudioHubPlaybackController.ActiveSource.RADIO -> getString(R.string.audio_hub_radio_title)
                    AudioHubPlaybackController.ActiveSource.MIDI -> getString(R.string.audio_hub_midi_title)
                    else -> ""
                }

                if (state.subtitle != null) {
                    sourceLabel.text = getString(
                        R.string.audio_hub_source_with_subtitle,
                        state.subtitle,
                        sourceName
                    )
                } else {
                    sourceLabel.text = sourceName
                }
                sourceLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun setupPlaybackControls() {
        playPauseButton.setOnClickListener {
            playbackController.togglePlayPause()
        }

        stopButton.setOnClickListener {
            playbackController.stopPlayback()
        }
    }

    private fun setupViewToggle() {
        viewToggleButton.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit {
                putString(KEY_VIEW_MODE, if (isGridMode) VIEW_MODE_GRID else VIEW_MODE_LIST)
            }
            updateViewMode()
        }
    }

    private fun setupConfirmEditFab() {
        confirmEditFab.setOnClickListener {
            exitEditMode()
        }
    }

    private fun setupEditModeBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditMode) {
                    exitEditMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun enterEditMode() {
        if (isEditMode) return
        isEditMode = true
        tilesAdapter.setEditMode(true)
        confirmEditFab.visibility = View.VISIBLE
        confirmEditFab.alpha = 0f
        confirmEditFab.animate().alpha(1f).setDuration(200).start()
    }

    private fun exitEditMode() {
        if (!isEditMode) return
        isEditMode = false
        tilesAdapter.setEditMode(false)
        confirmEditFab.animate().alpha(0f).setDuration(200).withEndAction {
            confirmEditFab.visibility = View.GONE
        }.start()
    }

    private fun setupLanguageButton() {
        languageButton.setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun setupSleepTimerButton() {
        sleepTimerButton.setOnClickListener {
            showSleepTimerDialog()
        }
    }

    private fun showSleepTimerDialog() {
        SleepTimerDialog(this) {
            updateSleepTimerButtonState()
        }.show()
    }

    private fun updateSleepTimerButtonState() {
        if (SleepTimerManager.isTimerRunning) {
            // Timer active - theme accent color
            sleepTimerButton.clearColorFilter()
        } else {
            // Timer inactive - grey
            sleepTimerButton.setColorFilter("#9E9E9E".toColorInt())
        }
    }

    // SleepTimerManager.Listener implementation
    override fun onTimerTick(remainingMillis: Long) {
        updateSleepTimerButtonState()
    }

    override fun onTimerFinished() {
        updateSleepTimerButtonState()
        // Optionally show a toast
        android.widget.Toast.makeText(
            this,
            R.string.sleep_timer_finished,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    override fun onTimerCancelled() {
        updateSleepTimerButtonState()
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, HubSettingsActivity::class.java))
        }
    }

    private fun showLanguageDialog() {
        val languages = LocaleHelper.SUPPORTED_LANGUAGES
        val currentLanguage = LocaleHelper.getLanguage(this)
        val displayNames = languages.map { LocaleHelper.getLanguageDisplayName(it) }.toTypedArray()
        val currentIndex = languages.indexOf(currentLanguage)

        AlertDialog.Builder(this)
            .setTitle(R.string.hub_select_language)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                if (selectedLanguage != currentLanguage) {
                    LocaleHelper.setLanguage(this, selectedLanguage)
                    // Recreate activity to apply new language
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateLanguageButtonLabel() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        languageButton.text = LocaleHelper.getLanguageShortLabel(currentLanguage)
    }

    private fun updateViewMode() {
        if (isGridMode) {
            viewToggleButton.setImageResource(R.drawable.ic_view_list)
            tilesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        } else {
            viewToggleButton.setImageResource(R.drawable.ic_view_grid)
            tilesRecyclerView.layoutManager = LinearLayoutManager(this)
        }
        tilesAdapter.setGridMode(isGridMode)

        // Recalculate heights after layout change
        tilesRecyclerView.post {
            val height = tilesRecyclerView.height
            if (height > 0) {
                tilesAdapter.setRecyclerViewHeight(height)
            }
        }
    }

    private fun setupModuleTiles() {
        // Create adapter with generalized HubTilesAdapter
        tilesAdapter = HubTilesAdapter(
            context = this,
            onTileClick = { tile -> onTileClicked(tile) },
            onOrderChanged = { order -> saveTileOrder(order) },
            onEditTile = { tile -> showTileEditDialog(tile) },
            onLongPressTile = { _ -> enterEditMode() },
            onQuickAccessClick = { _, item ->
                try {
                    startActivity(Intent(this, Class.forName(item.activityClassName)))
                } catch (e: Exception) {
                    // classe invalide, on ignore
                }
            }
        )
        tilesAdapter.setShowQuickAccessButtons(true)

        // Setup RecyclerView with GridLayoutManager (2 columns)
        tilesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        tilesRecyclerView.adapter = tilesAdapter

        // Setup ItemTouchHelper for drag & drop
        val touchCallback = HubTileTouchCallback(tilesAdapter)
        val itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(tilesRecyclerView)
        tilesAdapter.attachItemTouchHelper(itemTouchHelper)

        // Load tiles with customization
        loadTiles()

        // Notify adapter of RecyclerView height after layout (once)
        tilesRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val height = tilesRecyclerView.height
                if (height > 0) {
                    tilesAdapter.setRecyclerViewHeight(height)
                    // Remove listener after first successful call to avoid repeated invocations
                    tilesRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    private fun loadTiles() {
        val savedOrder = loadTileOrder()
        val savedColors = loadTileColors()
        val savedQuickAccess = loadQuickAccess()

        // Apply customization to tiles
        val tilesWithCustomization = defaultTiles.map { tile ->
            val colorData = savedColors[tile.id]
            tile.copy(
                customColorHex = colorData?.first,
                textColorMode = colorData?.second ?: "auto",
                quickAccessItems = savedQuickAccess[tile.id] ?: emptyList()
            )
        }

        // Reorder according to saved order, appending any new tiles not yet in the saved order
        val orderedTiles = if (savedOrder.isNotEmpty()) {
            val reordered = savedOrder.mapNotNull { id -> tilesWithCustomization.find { it.id == id } }
            val newTiles = tilesWithCustomization.filter { tile -> tile.id !in savedOrder }
            reordered + newTiles
        } else {
            tilesWithCustomization
        }

        tilesAdapter.setTiles(orderedTiles)
    }

    private fun onTileClicked(tile: HubTile) {
        tile.activityClass?.let {
            startActivity(Intent(this, it))
        }
    }

    private fun showTileEditDialog(tile: HubTile) {
        // Only one option (color picker), so open it directly
        showColorPicker(tile)
    }

    private fun showColorPicker(tile: HubTile) {
        SimpleColorPickerDialog(
            context = this,
            currentColorHex = tile.customColorHex,
            currentTextColorMode = tile.textColorMode
        ) { colorHex, textColorMode ->
            // Update tile
            tilesAdapter.updateTileColor(tile.id, colorHex, textColorMode)

            // Save
            val currentColors = loadTileColors().toMutableMap()
            currentColors[tile.id] = Pair(colorHex, textColorMode)
            saveTileColors(currentColors)
        }.show()
    }

    private fun loadQuickAccess(): Map<String, List<QuickAccessItem>> {
        val saved = prefs.getString(KEY_QUICK_ACCESS, null) ?: return emptyMap()
        val result = mutableMapOf<String, MutableList<QuickAccessItem>>()
        saved.split(";").filter { it.isNotEmpty() }.forEach { entry ->
            val parts = entry.split(":", limit = 4)
            if (parts.size >= 3) {
                val parentTileId = parts[0]
                val label = parts[1]
                val className = parts[2]
                val colorHex = parts.getOrNull(3)
                if (className.isNotEmpty()) {
                    result.getOrPut(parentTileId) { mutableListOf() }
                        .add(QuickAccessItem(label = label, activityClassName = className, colorHex = colorHex))
                }
            }
        }
        return result
    }

    private fun loadTileOrder(): List<String> {
        val savedOrder = prefs.getString(KEY_TILE_ORDER, null)
        return if (savedOrder != null) {
            savedOrder.split(",").filter { it.isNotEmpty() }
        } else {
            DEFAULT_ORDER
        }
    }

    private fun saveTileOrder(order: List<String>) {
        prefs.edit { putString(KEY_TILE_ORDER, order.joinToString(",")) }
    }

    // Format: "tileId:colorHex:textMode;tileId:colorHex:textMode;..."
    private fun loadTileColors(): Map<String, Pair<String, String>> {
        val saved = prefs.getString(KEY_TILE_COLORS, null) ?: return emptyMap()
        return saved.split(";")
            .filter { it.contains(":") }
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size >= 3) {
                    parts[0] to Pair(parts[1], parts[2])
                } else if (parts.size == 2) {
                    parts[0] to Pair(parts[1], "auto")
                } else null
            }
            .toMap()
    }

    private fun saveTileColors(colors: Map<String, Pair<String, String>>) {
        val encoded = colors.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }
        prefs.edit { putString(KEY_TILE_COLORS, encoded) }
    }

    /**
     * Lance le nettoyage des fichiers orphelins en arrière-plan.
     * Ce nettoyage supprime les fichiers non référencés dans les bases de données
     * pour libérer de l'espace de stockage.
     */
    private fun launchCacheCleanup() {
        activityScope.launch {
            try {
                val cleanupEnabled = prefs.getBoolean("auto_cleanup_enabled", false)

                if (!cleanupEnabled) {
                    Log.d("AudioHubActivity", "🧹 Nettoyage automatique désactivé")
                    return@launch
                }

                // Vérifier la dernière exécution pour éviter de nettoyer trop souvent
                val lastCleanup = prefs.getLong("last_cleanup_timestamp", 0)
                val now = System.currentTimeMillis()
                val dayInMillis = 24 * 60 * 60 * 1000L

                if (now - lastCleanup < dayInMillis) {
                    Log.d("AudioHubActivity", "🧹 Nettoyage déjà effectué aujourd'hui, skip")
                    return@launch
                }

                Log.i("AudioHubActivity", "🧹 Lancement du nettoyage des fichiers orphelins...")

                val cleanerManager = CacheCleanerManager(this@AudioHubActivity)
                val report = cleanerManager.cleanOrphanedFiles(dryRun = false)

                // Sauvegarder le timestamp
                prefs.edit {
                    putLong("last_cleanup_timestamp", now)
                }

                // Afficher un message si des fichiers ont été supprimés
                if (report.deletedFiles > 0) {
                    Log.i("AudioHubActivity", "✅ Nettoyage terminé: ${report.deletedFiles} fichiers supprimés, " +
                            "${String.format("%.2f", report.freedSpaceMB)} MB libérés")

                    // Optionnel : afficher une Snackbar à l'utilisateur
                    if (report.freedSpaceMB > 10.0) {
                        runOnUiThread {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                getString(R.string.settings_cleanup_snackbar,
                                    report.deletedFiles,
                                    String.format("%.1f", report.freedSpaceMB)),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Log.d("AudioHubActivity", "✅ Aucun fichier orphelin trouvé")
                }

            } catch (e: Exception) {
                Log.e("AudioHubActivity", "❌ Erreur lors du nettoyage", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
