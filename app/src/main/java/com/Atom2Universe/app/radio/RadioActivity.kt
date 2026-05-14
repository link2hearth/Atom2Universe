package com.Atom2Universe.app.radio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.LocaleHelper
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AudioPlaybackManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.Recorder
import com.Atom2Universe.app.SaveCore
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.AudioHubActivity

class RadioActivity : ThemedActivity(), RadioPlaybackHolder.PlayerListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var config: RadioConfig
    private lateinit var repository: RadioRepository
    private lateinit var favoritesStore: RadioFavoritesStore
    private lateinit var player: ExoPlayer  // Référence au player du holder
    private lateinit var recorder: Recorder
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var statusText: TextView
    private lateinit var playerStatusText: TextView
    private lateinit var stationNameText: TextView
    private lateinit var stationDetailsText: TextView
    private lateinit var nowPlayingText: TextView
    private lateinit var playButton: ImageButton
    private lateinit var pauseButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    private lateinit var recordButton: ImageButton
    private lateinit var recordStopButton: ImageButton
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var recordingDot: ImageView
    private lateinit var favoritesEmptyText: TextView
    private lateinit var resultsEmptyText: TextView
    private lateinit var favoritesList: RecyclerView
    private lateinit var resultsList: RecyclerView
    private lateinit var addFavoriteButton: MaterialButton
    private lateinit var searchButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var queryInput: EditText
    private lateinit var countrySpinner: Spinner
    private lateinit var languageSpinner: Spinner
    private lateinit var loadingSpinner: ProgressBar

    private lateinit var favoritesAdapter: RadioStationAdapter
    private lateinit var resultsAdapter: RadioStationAdapter

    private val favorites = java.util.Collections.synchronizedMap(LinkedHashMap<String, RadioStation>())
    private var currentResults: List<RadioStation> = emptyList()
    private var selectedStation: RadioStation? = null
    @Volatile  // Bug 4.16: Thread-safety pour isRecording
    private var isRecording = false
    @Volatile  // Bug 4.16: Thread-safety pour latestMetadata
    private var latestMetadata: TrackMetadata? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_radio)

        config = RadioConfig.load(this)
        repository = RadioRepository(config, this)
        favoritesStore = RadioFavoritesStore(SaveCore(applicationContext), config.favoritesStorageKey)
        // Utilise le holder singleton pour persister la lecture en background
        player = RadioPlaybackHolder.getOrCreatePlayer(this)
        RadioPlaybackHolder.addListener(this)
        recorder = Recorder(applicationContext, recorderScope)

        // Restaure l'état si une station était en cours de lecture
        RadioPlaybackHolder.getCurrentStation()?.let { station ->
            selectedStation = station
            latestMetadata = TrackMetadata(null, null, station.name)
        }

        // Setup toolbar avec flèche retour
        val toolbar = findViewById<Toolbar>(R.id.radio_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        bindViews()
        setupAdapters()
        setupPlayer()
        loadFavorites()
        setupListeners()
        applyConfigState()
        loadFilters()
        updatePlayerUi()
    }

    override fun onDestroy() {
        // Bug 4.8: Utiliser try-finally pour garantir le nettoyage des listeners même en cas de crash
        // Bug 4.11: S'assurer que recorderScope est cancelled
        try {
            // Ne release PAS le player - il est géré par RadioPlaybackHolder
            // On retire juste le listener pour éviter les fuites mémoire
            RadioPlaybackHolder.removeListener(this)
        } finally {
            try {
                recorder.release()
            } finally {
                recorderScope.cancel()
            }
        }
        super.onDestroy()
    }

    // Bug 4.15: Sauvegarder l'état lors des changements de configuration
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedStationId", selectedStation?.id)
        outState.putBoolean("isRecording", isRecording)
        latestMetadata?.let { meta ->
            outState.putString("metaArtist", meta.artist)
            outState.putString("metaTitle", meta.title)
            outState.putString("metaStation", meta.station)
        }
    }

    // Bug 4.15: Restaurer l'état après changement de configuration
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedStationId = savedInstanceState.getString("selectedStationId")
        val savedIsRecording = savedInstanceState.getBoolean("isRecording", false)

        // Restaurer la station sélectionnée
        if (savedStationId != null) {
            val station = favorites[savedStationId]
                ?: currentResults.find { it.id == savedStationId }
                ?: RadioPlaybackHolder.getCurrentStation()
            if (station != null) {
                selectedStation = station
            }
        }

        // Restaurer les métadonnées
        val metaArtist = savedInstanceState.getString("metaArtist")
        val metaTitle = savedInstanceState.getString("metaTitle")
        val metaStation = savedInstanceState.getString("metaStation")
        if (metaArtist != null || metaTitle != null || metaStation != null) {
            latestMetadata = TrackMetadata(metaArtist, metaTitle, metaStation)
        }

        // Restaurer l'état d'enregistrement
        if (savedIsRecording != isRecording) {
            setRecordingState(savedIsRecording)
        }

        updatePlayerUi()
    }

    // === RadioPlaybackHolder.PlayerListener callbacks ===

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            if (isPlaying) {
                updatePlayerStatus(getString(R.string.radio_player_status_playing))
            }
        }
    }

    override fun onMetadataChanged(artist: String?, title: String?) {
        runOnUiThread {
            latestMetadata = TrackMetadata(
                artist?.takeIf { it.isNotBlank() },
                title?.takeIf { it.isNotBlank() },
                selectedStation?.name
            )
            setNowPlaying(latestMetadata)
        }
    }

    override fun onError(error: PlaybackException) {
        runOnUiThread {
            updatePlayerStatus(getString(R.string.radio_player_status_error))
            setNowPlaying(null)
        }
    }

    // Bug 4.20: Callback avec détails d'erreur pour afficher un message adapté
    override fun onErrorWithDetails(
        error: PlaybackException,
        errorType: RadioPlaybackHolder.PlaybackErrorType,
        willRetry: Boolean
    ) {
        runOnUiThread {
            val statusMessage = when (errorType) {
                RadioPlaybackHolder.PlaybackErrorType.NETWORK -> {
                    if (willRetry) {
                        getString(R.string.radio_player_status_reconnecting)
                    } else {
                        getString(R.string.radio_player_status_network_error)
                    }
                }
                RadioPlaybackHolder.PlaybackErrorType.DECODING -> {
                    getString(R.string.radio_player_status_format_error)
                }
                RadioPlaybackHolder.PlaybackErrorType.UNKNOWN -> {
                    getString(R.string.radio_player_status_error)
                }
            }
            updatePlayerStatus(statusMessage)
            if (!willRetry) {
                setNowPlaying(null)
            }
        }
    }

    // Bug 4.24: Callback quand les permissions de notification manquent
    override fun onNotificationPermissionMissing() {
        runOnUiThread {
            // Afficher un message informatif (la lecture fonctionne mais sans notification)
            Toast.makeText(
                this,
                getString(R.string.radio_notification_permission_missing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Bug 4.25: Callback pour les changements de connectivité
    override fun onNetworkStateChanged(isAvailable: Boolean) {
        runOnUiThread {
            if (!isAvailable) {
                updateStatus(getString(R.string.radio_status_offline))
            } else {
                // Réseau restauré - mettre à jour le statut si on n'est pas en train de charger
                if (!loadingSpinner.isVisible) {
                    updateStatus(getString(R.string.radio_status_idle))
                }
            }
        }
    }

    override fun onRecordingStateChanged(recording: Boolean) {
        runOnUiThread {
            // Sync l'état d'enregistrement avec l'UI
            if (recording != isRecording) {
                if (recording) {
                    // Démarré depuis la notification - lancer l'enregistrement réel
                    val station = selectedStation ?: RadioPlaybackHolder.getCurrentStation()
                    if (station != null) {
                        val started = recorder.startRecording(station.url, latestMetadata) {
                            runOnUiThread {
                                setRecordingState(false)
                                RadioPlaybackHolder.setRecordingState(this, false)
                            }
                        }
                        if (started) {
                            setRecordingState(true)
                            updatePlayerStatus(getString(R.string.radio_player_status_recording))
                        }
                    }
                } else {
                    // Arrêté depuis la notification
                    recorder.stopRecording()
                    setRecordingState(false)
                    updatePlayerStatus(getString(R.string.radio_player_status_recording_stopped))
                }
            }
        }
    }

    private fun bindViews() {
        statusText = findViewById(R.id.radio_status)
        playerStatusText = findViewById(R.id.radio_player_status)
        stationNameText = findViewById(R.id.radio_station_name)
        stationDetailsText = findViewById(R.id.radio_station_details)
        nowPlayingText = findViewById(R.id.radio_now_playing)
        playButton = findViewById(R.id.radio_play_button)
        pauseButton = findViewById(R.id.radio_pause_button)
        stopButton = findViewById(R.id.radio_stop_button)
        reloadButton = findViewById(R.id.radio_reload_button)
        favoriteButton = findViewById(R.id.radio_favorite_button)
        recordButton = findViewById(R.id.radio_record_button)
        recordStopButton = findViewById(R.id.radio_record_stop_button)
        recordingIndicator = findViewById(R.id.radio_recording_indicator)
        recordingDot = findViewById(R.id.radio_recording_dot)
        favoritesEmptyText = findViewById(R.id.radio_favorites_empty)
        resultsEmptyText = findViewById(R.id.radio_results_empty)
        favoritesList = findViewById(R.id.radio_favorites_list)
        resultsList = findViewById(R.id.radio_results_list)
        addFavoriteButton = findViewById(R.id.radio_favorites_add_button)
        searchButton = findViewById(R.id.radio_search_button)
        resetButton = findViewById(R.id.radio_reset_button)
        queryInput = findViewById(R.id.radio_query)
        countrySpinner = findViewById(R.id.radio_country)
        languageSpinner = findViewById(R.id.radio_language)
        loadingSpinner = findViewById(R.id.radio_loading)
    }

    private fun setupAdapters() {
        favoritesAdapter = RadioStationAdapter(
            primaryLabel = getString(R.string.radio_action_listen),
            secondaryLabelProvider = { getString(R.string.radio_action_favorite_remove) },
            onPrimaryAction = { station -> selectStation(station, autoplay = true) },
            onSecondaryAction = { station -> toggleFavorite(station) },
            isFavoriteProvider = { true } // All items in favorites list are favorites
        )
        favoritesList.layoutManager = LinearLayoutManager(this)
        favoritesList.adapter = favoritesAdapter

        resultsAdapter = RadioStationAdapter(
            primaryLabel = getString(R.string.radio_action_listen),
            secondaryLabelProvider = { station ->
                if (favorites.containsKey(station.id)) {
                    getString(R.string.radio_action_favorite_remove)
                } else {
                    getString(R.string.radio_action_favorite)
                }
            },
            onPrimaryAction = { station -> selectStation(station, autoplay = true) },
            onSecondaryAction = { station -> toggleFavorite(station) },
            isFavoriteProvider = { station -> favorites.containsKey(station.id) }
        )
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = resultsAdapter
    }

    private fun setupPlayer() {
        // Les callbacks sont maintenant gérés via RadioPlaybackHolder.PlayerListener
        // (voir onPlaybackStateChanged, onMetadataChanged, onError)

        // Synchronise l'état complet du lecteur avec l'UI
        // Cela gère le cas où le service a été tué après sauvegarde de la station
        syncPlayerStateWithUi()
    }

    /**
     * Synchronise l'état du lecteur avec l'UI.
     * Gère la désynchronisation possible après kill du service.
     */
    private fun syncPlayerStateWithUi() {
        val currentStation = RadioPlaybackHolder.getCurrentStation()
        val isPlaying = RadioPlaybackHolder.isPlaying()
        val isPaused = RadioPlaybackHolder.isPaused()
        val isRecording = RadioPlaybackHolder.isCurrentlyRecording()

        // Synchroniser la station sélectionnée
        if (currentStation != null) {
            selectedStation = currentStation
            latestMetadata = TrackMetadata(
                RadioPlaybackHolder.getCurrentArtist(),
                RadioPlaybackHolder.getCurrentTitle(),
                currentStation.name
            )
            setNowPlaying(latestMetadata)
        }

        // Synchroniser l'état de lecture
        when {
            isPlaying -> updatePlayerStatus(getString(R.string.radio_player_status_playing))
            isPaused -> updatePlayerStatus(getString(R.string.radio_player_status_paused))
            currentStation != null -> updatePlayerStatus(getString(R.string.radio_player_status_stopped))
            else -> updatePlayerStatus(getString(R.string.radio_player_status_idle))
        }

        // Synchroniser l'état d'enregistrement
        if (isRecording != this.isRecording) {
            setRecordingState(isRecording)
            if (isRecording) {
                updatePlayerStatus(getString(R.string.radio_player_status_recording))
            }
        }

        // Mettre à jour l'UI des boutons
        updatePlayerUi()
    }

    private fun loadFavorites() {
        favorites.clear()
        favorites.putAll(favoritesStore.load())
        refreshFavorites()
    }

    private fun setupListeners() {
        playButton.setOnClickListener { playSelectedStation(forceReload = false) }
        pauseButton.setOnClickListener { pausePlayback() }
        stopButton.setOnClickListener { stopPlayback() }
        reloadButton.setOnClickListener { playSelectedStation(forceReload = true) }
        favoriteButton.setOnClickListener { selectedStation?.let { toggleFavorite(it) } }
        recordButton.setOnClickListener { startRecording() }
        recordStopButton.setOnClickListener { stopRecording(showStatus = true) }
        addFavoriteButton.setOnClickListener { showManualFavoriteDialog() }
        searchButton.setOnClickListener { searchStations() }
        resetButton.setOnClickListener { resetSearch() }
    }

    private fun applyConfigState() {
        if (!config.enabled) {
            statusText.text = getString(R.string.radio_status_disabled)
            searchButton.isEnabled = false
            resetButton.isEnabled = false
            addFavoriteButton.isEnabled = false
            playButton.isEnabled = false
            pauseButton.isEnabled = false
            stopButton.isEnabled = false
            reloadButton.isEnabled = false
            favoriteButton.isEnabled = false
            recordButton.isEnabled = false
            recordStopButton.isEnabled = false
        }
    }

    private fun loadFilters() {
        lifecycleScope.launch {
            if (!config.enabled) {
                return@launch
            }
            updateStatus(getString(R.string.radio_status_filters_loading))
            try {
                val filters = repository.fetchFilters()
                val countries = listOf(getString(R.string.radio_filter_any_country)) + filters.countries
                val languages = listOf(getString(R.string.radio_filter_any_language)) + filters.languages
                setupSpinner(countrySpinner, countries)
                setupSpinner(languageSpinner, languages)
                updateStatus(getString(R.string.radio_status_idle))
            } catch (e: Exception) {
                android.util.Log.w("RadioActivity", "Failed to load filters", e)
                setupSpinner(countrySpinner, listOf(getString(R.string.radio_filter_any_country)))
                setupSpinner(languageSpinner, listOf(getString(R.string.radio_filter_any_language)))
                updateStatus(getString(R.string.radio_status_filters_error))
            }
        }
    }

    private fun setupSpinner(spinner: Spinner, entries: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

    }

    private fun searchStations() {
        if (!config.enabled) {
            return
        }
        lifecycleScope.launch {
            setLoading(true)
            updateStatus(getString(R.string.radio_status_loading))
            val params = RadioSearchParams(
                query = queryInput.text.toString(),
                country = selectedFilterValue(countrySpinner, R.string.radio_filter_any_country),
                language = selectedFilterValue(languageSpinner, R.string.radio_filter_any_language)
            )
            val (stations, searchError) = try {
                Pair(repository.searchStations(params), false)
            } catch (e: Exception) {
                android.util.Log.w("RadioActivity", "Search failed", e)
                Pair(emptyList<RadioStation>(), true)
            }
            setLoading(false)
            when {
                searchError -> updateStatus(getString(R.string.radio_status_error))
                stations.isEmpty() -> updateStatus(getString(R.string.radio_status_empty))
                else -> updateStatus(getString(R.string.radio_status_loaded, stations.size))
            }
            updateResults(stations)
        }
    }

    private fun resetSearch() {
        queryInput.setText("")
        countrySpinner.setSelection(0)
        languageSpinner.setSelection(0)
        updateResults(emptyList())
        updateStatus(getString(R.string.radio_status_idle))
    }

    private fun updateResults(stations: List<RadioStation>) {
        currentResults = stations
        resultsAdapter.submitList(stations)
        resultsEmptyText.isVisible = stations.isEmpty()
        if (stations.isNotEmpty()) {
            selectStation(stations.first(), autoplay = false)
        } else {
            selectStation(null, autoplay = false)
        }
    }

    private fun selectStation(station: RadioStation?, autoplay: Boolean) {
        if (station == null) {
            selectedStation = null
            latestMetadata = null
            updatePlayerUi()
            return
        }
        selectedStation = station
        latestMetadata = TrackMetadata(null, null, station.name)
        setNowPlaying(null)
        updatePlayerUi()
        if (autoplay) {
            playSelectedStation(forceReload = false)
        }
    }

    private fun playSelectedStation(forceReload: Boolean) {
        val station = selectedStation
        if (station == null) {
            updatePlayerStatus(getString(R.string.radio_player_status_idle))
            return
        }

        // Vérifie s'il y a un conflit audio (ex: MIDI en cours)
        AudioPlaybackManager.requestPlayback(
            AudioPlaybackManager.AudioSource.RADIO,
            this
        ) {
            // Callback exécuté si la lecture est autorisée
            startRadioPlayback(station, forceReload)
        }
    }

    /**
     * Démarre effectivement la lecture Radio (après vérification des conflits)
     */
    private fun startRadioPlayback(station: RadioStation, forceReload: Boolean) {
        // Enregistre cette source comme active avec callback pour l'arrêter
        AudioPlaybackManager.registerPlayback(AudioPlaybackManager.AudioSource.RADIO) {
            // Callback pour arrêter la lecture Radio si une autre source prend le relais
            RadioPlaybackHolder.stop(this)
            updatePlayerStatus(getString(R.string.radio_player_status_stopped))
        }

        // Utilise le holder pour gérer la lecture (persiste en background)
        RadioPlaybackHolder.play(this, station, forceReload)
        updatePlayerStatus(getString(R.string.radio_player_status_playing))
    }

    private fun pausePlayback() {
        RadioPlaybackHolder.pause(this)
        updatePlayerStatus(getString(R.string.radio_player_status_paused))
    }

    private fun stopPlayback() {
        if (isRecording) {
            stopRecording(showStatus = false)
        }
        RadioPlaybackHolder.stop(this)
        updatePlayerStatus(getString(R.string.radio_player_status_stopped))
        setNowPlaying(null)
        // Désenregistre cette source audio quand la lecture s'arrête
        AudioPlaybackManager.unregisterPlayback(AudioPlaybackManager.AudioSource.RADIO)
    }

    private fun startRecording() {
        val station = selectedStation
        if (station == null) {
            updatePlayerStatus(getString(R.string.radio_player_status_idle))
            return
        }
        val started = recorder.startRecording(station.url, latestMetadata) {
            runOnUiThread {
                setRecordingState(false)
                RadioPlaybackHolder.setRecordingState(this, false)
            }
        }
        if (!started) {
            return
        }
        setRecordingState(true)
        RadioPlaybackHolder.setRecordingState(this, true)
        updatePlayerStatus(getString(R.string.radio_player_status_recording))
        playSelectedStation(forceReload = false)
    }

    private fun stopRecording(showStatus: Boolean) {
        recorder.stopRecording()
        setRecordingState(false)
        RadioPlaybackHolder.setRecordingState(this, false)
        if (showStatus) {
            updatePlayerStatus(getString(R.string.radio_player_status_recording_stopped))
        }
    }

    private fun setRecordingState(recording: Boolean) {
        isRecording = recording
        recordButton.isEnabled = !recording
        recordStopButton.isEnabled = recording

        // Show/hide recording indicator with animation
        if (recording) {
            recordingIndicator.visibility = View.VISIBLE
            val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
            recordingDot.startAnimation(pulseAnim)
        } else {
            recordingDot.clearAnimation()
            recordingIndicator.visibility = View.GONE
        }

        updatePlayerUi()
    }

    private fun toggleFavorite(station: RadioStation) {
        if (favorites.containsKey(station.id)) {
            favorites.remove(station.id)
            Toast.makeText(this, getString(R.string.radio_favorite_removed), Toast.LENGTH_SHORT).show()
        } else {
            favorites[station.id] = station
            Toast.makeText(this, getString(R.string.radio_favorite_added), Toast.LENGTH_SHORT).show()
        }
        favoritesStore.save(favorites)
        refreshFavorites()
        updatePlayerUi()
        refreshResults()
    }

    private fun refreshFavorites() {
        favoritesAdapter.submitList(favorites.values.toList())
        favoritesEmptyText.isVisible = favorites.isEmpty()
    }

    private fun refreshResults() {
        resultsAdapter.submitList(currentResults)
    }

    private fun updatePlayerUi() {
        val station = selectedStation
        if (station == null) {
            stationNameText.text = getString(R.string.radio_player_empty)
            stationDetailsText.text = ""
            favoriteButton.isEnabled = false
            favoriteButton.setImageResource(R.drawable.ic_star_outline)
            playButton.isEnabled = false
            pauseButton.isEnabled = false
            stopButton.isEnabled = false
            reloadButton.isEnabled = false
            recordButton.isEnabled = false
            recordStopButton.isEnabled = false
            return
        }
        stationNameText.text = station.name
        stationDetailsText.text = formatStationMeta(station)
        favoriteButton.isEnabled = true
        // Toggle star icon based on favorite status
        if (favorites.containsKey(station.id)) {
            favoriteButton.setImageResource(R.drawable.ic_star_filled)
        } else {
            favoriteButton.setImageResource(R.drawable.ic_star_outline)
        }
        playButton.isEnabled = true
        pauseButton.isEnabled = true
        stopButton.isEnabled = true
        reloadButton.isEnabled = true
        recordButton.isEnabled = !isRecording
        recordStopButton.isEnabled = isRecording
    }

    private fun formatStationMeta(station: RadioStation): String {
        val parts = mutableListOf<String>()
        if (station.country.isNotBlank() && station.language.isNotBlank()) {
            parts.add(getString(R.string.radio_meta_country_language, station.country, station.language))
        } else if (station.country.isNotBlank()) {
            parts.add(station.country)
        } else if (station.language.isNotBlank()) {
            parts.add(station.language)
        }
        station.bitrate?.let { parts.add(getString(R.string.radio_meta_bitrate, it)) }
        return parts.joinToString(" · ")
    }

    private fun setNowPlaying(metadata: TrackMetadata?) {
        val text = listOfNotNull(metadata?.artist, metadata?.title)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
        if (text.isBlank()) {
            nowPlayingText.isVisible = false
            nowPlayingText.text = ""
        } else {
            nowPlayingText.isVisible = true
            nowPlayingText.text = text
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    private fun updatePlayerStatus(message: String) {
        playerStatusText.text = message
    }

    private fun setLoading(isLoading: Boolean) {
        loadingSpinner.isVisible = isLoading
        searchButton.isEnabled = !isLoading
        resetButton.isEnabled = !isLoading
    }

    private fun selectedFilterValue(spinner: Spinner, placeholderRes: Int): String {
        val placeholder = getString(placeholderRes)
        val value = spinner.selectedItem?.toString().orEmpty()
        return if (value == placeholder) "" else value
    }

    private fun showManualFavoriteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_favorite, null)
        val urlInput = dialogView.findViewById<EditText>(R.id.manual_favorite_url)
        val nameInput = dialogView.findViewById<EditText>(R.id.manual_favorite_name)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.radio_favorite_add_title))
            .setView(dialogView)
            .setPositiveButton(R.string.radio_action_add) { _, _ ->
                val url = urlInput.text.toString().trim()
                val name = nameInput.text.toString().trim()
                handleManualFavorite(url, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleManualFavorite(urlInput: String, nameInput: String) {
        val normalized = normalizeUrl(urlInput)
        if (normalized == null) {
            Toast.makeText(this, getString(R.string.radio_favorite_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }
        val duplicate = favorites.values.any { it.url == normalized }
        if (duplicate) {
            Toast.makeText(this, getString(R.string.radio_favorite_duplicate), Toast.LENGTH_SHORT).show()
            return
        }
        val name = if (nameInput.isNotBlank()) nameInput else normalized
        val station = RadioStation(
            id = "manual-${System.currentTimeMillis()}",
            name = name,
            url = normalized,
            country = "",
            language = "",
            favicon = "",
            bitrate = null
        )
        favorites[station.id] = station
        favoritesStore.save(favorites)
        refreshFavorites()
        updatePlayerUi()
        Toast.makeText(this, getString(R.string.radio_favorite_added), Toast.LENGTH_SHORT).show()
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        // Verifier le schema
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null
        }
        // Valider le format URL
        return try {
            val uri = trimmed.toUri()
            // Verifier que l'URL a un host valide
            if (uri.host.isNullOrBlank()) {
                null
            } else {
                trimmed
            }
        } catch (_: Exception) {
            null
        }
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
}
