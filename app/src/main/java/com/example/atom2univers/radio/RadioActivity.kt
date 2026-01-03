package com.example.atom2univers.radio

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.atom2univers.R
import com.example.atom2univers.Recorder
import com.example.atom2univers.SaveCore
import com.example.atom2univers.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class RadioActivity : AppCompatActivity() {

    private lateinit var config: RadioConfig
    private lateinit var repository: RadioRepository
    private lateinit var favoritesStore: RadioFavoritesStore
    private lateinit var player: ExoPlayer
    private lateinit var recorder: Recorder
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var statusText: TextView
    private lateinit var playerStatusText: TextView
    private lateinit var stationNameText: TextView
    private lateinit var stationDetailsText: TextView
    private lateinit var nowPlayingText: TextView
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var reloadButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var recordButton: Button
    private lateinit var recordStopButton: Button
    private lateinit var favoritesEmptyText: TextView
    private lateinit var resultsEmptyText: TextView
    private lateinit var favoritesList: RecyclerView
    private lateinit var resultsList: RecyclerView
    private lateinit var addFavoriteButton: Button
    private lateinit var searchButton: Button
    private lateinit var resetButton: Button
    private lateinit var queryInput: EditText
    private lateinit var countrySpinner: Spinner
    private lateinit var languageSpinner: Spinner
    private lateinit var loadingSpinner: ProgressBar

    private lateinit var favoritesAdapter: RadioStationAdapter
    private lateinit var resultsAdapter: RadioStationAdapter

    private val favorites = LinkedHashMap<String, RadioStation>()
    private var currentResults: List<RadioStation> = emptyList()
    private var selectedStation: RadioStation? = null
    private var isRecording = false
    private var latestMetadata: TrackMetadata? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio)

        config = RadioConfig.load(this)
        repository = RadioRepository(config)
        favoritesStore = RadioFavoritesStore(SaveCore(applicationContext), config.favoritesStorageKey)
        player = ExoPlayer.Builder(this).build()
        recorder = Recorder(applicationContext, recorderScope)

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
        super.onDestroy()
        player.release()
        recorder.release()
        recorderScope.cancel()
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
            onSecondaryAction = { station -> toggleFavorite(station) }
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
            onSecondaryAction = { station -> toggleFavorite(station) }
        )
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = resultsAdapter
    }

    private fun setupPlayer() {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                updatePlayerStatus(getString(R.string.radio_player_status_error))
                setNowPlaying(null)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val artist = mediaMetadata.artist?.toString().orEmpty()
                val title = mediaMetadata.title?.toString().orEmpty()
                latestMetadata = TrackMetadata(artist.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() }, selectedStation?.name)
                setNowPlaying(latestMetadata)
            }
        })
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
            } catch (_: Exception) {
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
            val stations = try {
                repository.searchStations(params)
            } catch (_: Exception) {
                emptyList()
            }
            setLoading(false)
            if (stations.isEmpty()) {
                updateStatus(getString(R.string.radio_status_empty))
            } else {
                updateStatus(getString(R.string.radio_status_loaded, stations.size))
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
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        val shouldReload = forceReload || currentUri != station.url
        if (shouldReload) {
            val mediaItem = MediaItem.fromUri(Uri.parse(station.url))
            player.setMediaItem(mediaItem)
            player.prepare()
        }
        player.playWhenReady = true
        player.play()
        updatePlayerStatus(getString(R.string.radio_player_status_playing))
    }

    private fun pausePlayback() {
        player.pause()
        updatePlayerStatus(getString(R.string.radio_player_status_paused))
    }

    private fun stopPlayback() {
        if (isRecording) {
            stopRecording(showStatus = false)
        }
        player.stop()
        player.clearMediaItems()
        updatePlayerStatus(getString(R.string.radio_player_status_stopped))
        setNowPlaying(null)
    }

    private fun startRecording() {
        val station = selectedStation
        if (station == null) {
            updatePlayerStatus(getString(R.string.radio_player_status_idle))
            return
        }
        val started = recorder.startRecording(station.url, latestMetadata) {
            runOnUiThread { setRecordingState(false) }
        }
        if (!started) {
            return
        }
        setRecordingState(true)
        updatePlayerStatus(getString(R.string.radio_player_status_recording))
        playSelectedStation(forceReload = false)
    }

    private fun stopRecording(showStatus: Boolean) {
        recorder.stopRecording()
        setRecordingState(false)
        if (showStatus) {
            updatePlayerStatus(getString(R.string.radio_player_status_recording_stopped))
        }
    }

    private fun setRecordingState(recording: Boolean) {
        isRecording = recording
        recordButton.isEnabled = !recording
        recordStopButton.isEnabled = recording
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
        favoriteButton.text = if (favorites.containsKey(station.id)) {
            getString(R.string.radio_action_favorite_remove)
        } else {
            getString(R.string.radio_action_favorite)
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
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            null
        }
    }
}
