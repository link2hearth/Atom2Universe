package com.Atom2Universe.app.music.equalizer

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.equalizer.data.EqAlbumOverride
import com.Atom2Universe.app.music.equalizer.data.EqArtistOverride
import com.Atom2Universe.app.music.equalizer.data.EqPreset
import com.Atom2Universe.app.music.equalizer.data.EqSettings
import com.Atom2Universe.app.music.equalizer.data.EqTrackOverride
import com.Atom2Universe.app.music.equalizer.data.OverrideSource
import com.Atom2Universe.app.music.equalizer.data.ResolvedPreset
import com.Atom2Universe.app.music.equalizer.dsp.DspEqualizerEngine
import com.Atom2Universe.app.music.equalizer.dsp.DspEqualizerProcessor
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Singleton manager for the music equalizer.
 * Coordinates between the database, audio engine, and UI.
 * All settings are persisted in Room database.
 */
@SuppressLint("StaticFieldLeak") // Uses applicationContext, not Activity context
object MusicEqualizerManager {

    private const val TAG = "MusicEqualizerManager"
    const val CUSTOM_PRESET_NAME = "Custom"

    private var context: Context? = null
    private var isInitialized = false

    // Components
    private val engine = EqualizerEngine()
    private val dspEngine = DspEqualizerEngine()
    private var resolver: EqPresetResolver? = null
    private var database: MusicDatabase? = null

    // Flag indicating if DSP engine is attached
    private var isDspAttached = false

    // In-memory current band levels (for real-time tracking)
    private val currentBandLevels = IntArray(10)
    private var currentBassBoost = 0
    private var currentVirtualizer = 0

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current state
    private val _currentPreset = MutableStateFlow<ResolvedPreset?>(null)
    val currentPreset: StateFlow<ResolvedPreset?> = _currentPreset.asStateFlow()

    // Default to false until settings are loaded from database
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var currentTrack: MusicTrack? = null

    // Cached settings
    private var cachedSettings: EqSettings? = null

    // Listeners
    private val listeners = mutableSetOf<EqualizerListener>()

    /**
     * Initialize the manager with application context.
     * Call this early in app lifecycle.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        this.context = context.applicationContext
        this.database = MusicDatabase.getInstance(context)
        this.resolver = EqPresetResolver(context)

        // Load settings from database
        scope.launch(Dispatchers.IO) {
            loadSettings()
        }

        isInitialized = true
        Log.d(TAG, "MusicEqualizerManager initialized")
    }

    /**
     * Load settings from Room database.
     */
    private suspend fun loadSettings() {
        // First, ensure system presets exist (may not exist if migration failed)
        ensureSystemPresetsExist()

        val settings = database?.eqSettingsDao()?.getSettings()
        if (settings != null) {
            cachedSettings = settings
            _isEnabled.value = settings.isEnabled
            Log.d(TAG, "Loaded settings: enabled=${settings.isEnabled}, globalPresetId=${settings.globalPresetId}")
        } else {
            // Create default settings if not exist
            val defaultSettings = EqSettings()
            database?.eqSettingsDao()?.saveSettings(defaultSettings)
            cachedSettings = defaultSettings
            _isEnabled.value = defaultSettings.isEnabled
            Log.d(TAG, "Created default settings")
        }
    }

    /**
     * Ensure system presets exist in the database.
     * This is a fallback in case the migration didn't run properly.
     */
    private suspend fun ensureSystemPresetsExist() {
        val db = database ?: return
        val existingPresets = db.eqPresetDao().getSystemPresets()

        if (existingPresets.isNotEmpty()) {
            Log.d(TAG, "System presets already exist: ${existingPresets.size} presets")
            return
        }

        Log.w(TAG, "No system presets found! Creating them now...")

        val systemPresets = listOf(
            EqPreset(name = "Flat", isSystemPreset = true),
            EqPreset(name = "Rock", isSystemPreset = true,
                band32Hz = 400, band64Hz = 300, band125Hz = 200, band250Hz = 0, band500Hz = -100,
                band1kHz = 0, band2kHz = 200, band4kHz = 400, band8kHz = 500, band16kHz = 400),
            EqPreset(name = "Pop", isSystemPreset = true,
                band32Hz = 200, band64Hz = 300, band125Hz = 200, band250Hz = 100, band500Hz = 0,
                band1kHz = 100, band2kHz = 200, band4kHz = 300, band8kHz = 200, band16kHz = 100),
            EqPreset(name = "Jazz", isSystemPreset = true,
                band32Hz = 300, band64Hz = 200, band125Hz = 100, band250Hz = 200, band500Hz = 0,
                band1kHz = 100, band2kHz = 0, band4kHz = 100, band8kHz = 200, band16kHz = 300),
            EqPreset(name = "Classical", isSystemPreset = true,
                band32Hz = 0, band64Hz = 0, band125Hz = 0, band250Hz = 0, band500Hz = 0,
                band1kHz = 0, band2kHz = -100, band4kHz = 100, band8kHz = 200, band16kHz = 300),
            EqPreset(name = "Electronic", isSystemPreset = true,
                band32Hz = 600, band64Hz = 500, band125Hz = 400, band250Hz = 200, band500Hz = 0,
                band1kHz = -100, band2kHz = 0, band4kHz = 200, band8kHz = 400, band16kHz = 500),
            EqPreset(name = "Bass Boost", isSystemPreset = true,
                band32Hz = 800, band64Hz = 600, band125Hz = 400, band250Hz = 200, band500Hz = 0,
                band1kHz = 0, band2kHz = 0, band4kHz = 0, band8kHz = 0, band16kHz = 0,
                bassBoostStrength = 500),
            EqPreset(name = "Treble Boost", isSystemPreset = true,
                band32Hz = 0, band64Hz = 0, band125Hz = 0, band250Hz = 0, band500Hz = 0,
                band1kHz = 100, band2kHz = 200, band4kHz = 400, band8kHz = 600, band16kHz = 700),
            EqPreset(name = "Vocal", isSystemPreset = true,
                band32Hz = -200, band64Hz = -100, band125Hz = 0, band250Hz = 200, band500Hz = 400,
                band1kHz = 400, band2kHz = 300, band4kHz = 200, band8kHz = 0, band16kHz = -100),
            EqPreset(name = "Late Night", isSystemPreset = true,
                band32Hz = -300, band64Hz = -100, band125Hz = 100, band250Hz = 200, band500Hz = 300,
                band1kHz = 300, band2kHz = 200, band4kHz = 100, band8kHz = -100, band16kHz = -300)
        )

        db.eqPresetDao().insertPresets(systemPresets)
        Log.d(TAG, "Created ${systemPresets.size} system presets")
    }

    /**
     * Ensure settings are loaded (blocking for synchronous access).
     */
    private fun ensureSettingsLoaded() {
        if (cachedSettings == null) {
            runBlocking(Dispatchers.IO) {
                loadSettings()
            }
        }
    }

    /**
     * Get the global preset ID from cached settings.
     */
    fun getGlobalPresetId(): Long {
        ensureSettingsLoaded()
        return cachedSettings?.globalPresetId ?: 1L
    }

    /**
     * Attach the DSP equalizer processor.
     * Call this after the player is created to enable DSP-based EQ.
     * This is the preferred method for Samsung and other devices where
     * the Android AudioEffect API is intercepted.
     */
    fun attachToProcessor(processor: DspEqualizerProcessor) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot attach DSP processor: manager not initialized")
            return
        }

        // First, ensure settings are loaded BEFORE initializing DSP engine
        // This guarantees we have the preset ready to apply
        ensureSettingsLoaded()

        // Load preset synchronously if not yet loaded
        if (_currentPreset.value == null) {
            loadPresetSync()
        }

        // Now initialize DSP engine - it will apply default (zero) values initially
        dspEngine.initialize(processor)
        isDspAttached = true

        // Always apply the current preset to DSP engine
        // This ensures the correct EQ is applied even if dspEngine.initialize() reset values
        val resolved = _currentPreset.value
        if (resolved != null) {
            dspEngine.applyPreset(resolved.preset, _isEnabled.value)
            // Update in-memory tracking
            resolved.preset.getBandLevels().forEachIndexed { index, level ->
                if (index < 10) currentBandLevels[index] = level
            }
            currentBassBoost = resolved.preset.bassBoostStrength
            currentVirtualizer = resolved.preset.virtualizerStrength
            Log.d(TAG, "Applied preset '${resolved.preset.name}' to DSP (enabled=${_isEnabled.value})")
        } else {
            // Fallback: create and apply a default flat preset in memory
            // This should never happen after loadPresetSync() fix, but safety first
            Log.w(TAG, "No preset available after loadPresetSync, creating emergency flat preset")
            val emergencyFlatPreset = EqPreset(
                id = 0,
                name = "Flat",
                isSystemPreset = true
            )
            _currentPreset.value = ResolvedPreset(emergencyFlatPreset, OverrideSource.GLOBAL)
            dspEngine.applyPreset(emergencyFlatPreset, _isEnabled.value)
        }

        Log.d(TAG, "Attached to DSP Equalizer Processor")
    }

    /**
     * Load the global preset synchronously.
     * Used when we need the preset immediately (e.g., when attaching processor).
     * ALWAYS sets _currentPreset.value to a valid preset (creates in-memory fallback if needed).
     */
    private fun loadPresetSync() {
        try {
            val presetId = cachedSettings?.globalPresetId ?: 1L
            val preset = runBlocking(Dispatchers.IO) {
                database?.eqPresetDao()?.getPresetById(presetId)
            }
            if (preset != null) {
                _currentPreset.value = ResolvedPreset(preset, OverrideSource.GLOBAL)
                // Also update in-memory band levels to match the preset
                preset.getBandLevels().forEachIndexed { index, level ->
                    if (index < 10) currentBandLevels[index] = level
                }
                currentBassBoost = preset.bassBoostStrength
                currentVirtualizer = preset.virtualizerStrength
                Log.d(TAG, "Loaded preset synchronously: ${preset.name}")
            } else {
                // Fallback to Flat preset (id=1)
                val flatPreset = runBlocking(Dispatchers.IO) {
                    database?.eqPresetDao()?.getPresetById(1L)
                }
                if (flatPreset != null) {
                    _currentPreset.value = ResolvedPreset(flatPreset, OverrideSource.GLOBAL)
                    Log.d(TAG, "Loaded fallback Flat preset")
                } else {
                    // Last resort: create an in-memory Flat preset
                    // This ensures _currentPreset is NEVER null after loadPresetSync()
                    Log.w(TAG, "No presets found in database, creating in-memory Flat preset")
                    val memoryFlatPreset = EqPreset(
                        id = 0,
                        name = "Flat",
                        isSystemPreset = true
                    )
                    _currentPreset.value = ResolvedPreset(memoryFlatPreset, OverrideSource.GLOBAL)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preset sync: ${e.message}")
            // Even on exception, ensure we have a valid preset
            if (_currentPreset.value == null) {
                val memoryFlatPreset = EqPreset(
                    id = 0,
                    name = "Flat",
                    isSystemPreset = true
                )
                _currentPreset.value = ResolvedPreset(memoryFlatPreset, OverrideSource.GLOBAL)
                Log.d(TAG, "Created fallback in-memory Flat preset after exception")
            }
        }
    }

    /**
     * Attach to an audio session (from ExoPlayer).
     * Call this when playback starts or audio session changes.
     * This initializes the standard Android AudioEffect API (fallback for virtualizer).
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot attach: manager not initialized")
            return
        }

        if (audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID: 0")
            return
        }

        val success = engine.initialize(audioSessionId)
        if (success) {
            Log.d(TAG, "Attached to audio session $audioSessionId (standard API)")

            // Re-apply current preset if we have one
            // Note: Band levels and bass boost now go through DSP engine,
            // but we still apply to the standard engine as fallback
            // and for virtualizer support
            _currentPreset.value?.let { resolved ->
                engine.applyPreset(resolved.preset, _isEnabled.value)
            }
        }
    }

    /**
     * Called when track changes. Resolves and applies appropriate preset.
     */
    fun onTrackChanged(track: MusicTrack?) {
        currentTrack = track

        if (track == null) {
            Log.d(TAG, "Track cleared")
            return
        }

        Log.d(TAG, "onTrackChanged: trackId=${track.id}, title='${track.title}', album='${track.album}', artist='${track.artist}', albumArtist='${track.albumArtist}'")

        scope.launch(Dispatchers.IO) {
            try {
                val globalPresetId = getGlobalPresetId()
                Log.d(TAG, "onTrackChanged: resolving preset, globalPresetId=$globalPresetId")

                val resolved = resolver?.resolvePresetForTrack(track, globalPresetId)

                if (resolved != null) {
                    Log.d(TAG, "onTrackChanged: resolved preset='${resolved.preset.name}' (id=${resolved.preset.id}), source=${resolved.source}")
                    _currentPreset.value = resolved

                    // Sync in-memory band levels so the EQ Fragment shows correct values
                    // when opened after a track change (avoids stale values from previous track)
                    resolved.preset.getBandLevels().forEachIndexed { index, level ->
                        if (index < 10) currentBandLevels[index] = level
                    }
                    currentBassBoost = resolved.preset.bassBoostStrength
                    currentVirtualizer = resolved.preset.virtualizerStrength

                    // Apply on main thread
                    launch(Dispatchers.Main) {
                        // Apply to DSP engine (primary - for band EQ and bass boost)
                        if (isDspAttached) {
                            dspEngine.applyPreset(resolved.preset, _isEnabled.value)
                        }
                        // Apply to standard engine (for virtualizer and fallback)
                        engine.applyPreset(resolved.preset, _isEnabled.value)
                        notifyPresetChanged(resolved)
                    }

                    Log.d(TAG, "Applied '${resolved.preset.name}' (${resolved.source}) for track: ${track.title}")
                } else {
                    Log.w(TAG, "onTrackChanged: resolver returned null for track ${track.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving preset for track: ${e.message}", e)
            }
        }
    }

    /**
     * Enable or disable the equalizer.
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled

        // Enable/disable DSP engine (primary)
        if (isDspAttached) {
            dspEngine.setEnabled(enabled)
        }

        // Enable/disable standard engine (for virtualizer)
        engine.setEnabled(enabled)

        // Save to database
        scope.launch(Dispatchers.IO) {
            database?.eqSettingsDao()?.setEnabled(enabled)
            cachedSettings = cachedSettings?.copy(isEnabled = enabled)
        }

        notifyEnabledChanged(enabled)
        Log.d(TAG, "Equalizer enabled: $enabled")
    }

    /**
     * Set the global preset and save to database.
     */
    fun setGlobalPreset(presetId: Long) {
        scope.launch(Dispatchers.IO) {
            // Save to database
            database?.eqSettingsDao()?.setGlobalPresetId(presetId)
            cachedSettings = cachedSettings?.copy(globalPresetId = presetId)
            Log.d(TAG, "Global preset set to: $presetId")

            // If current track doesn't have an override, apply the new global preset
            val track = currentTrack
            if (track != null) {
                val hasOverride = resolver?.let { r ->
                    r.hasTrackOverride(track.id) ||
                    r.hasAlbumOverride(track.album, track.albumArtist) ||
                    r.hasArtistOverride(track.artist, track.albumArtist)
                } ?: false

                if (!hasOverride) {
                    // Refresh with new global preset
                    onTrackChanged(track)
                }
            }
        }
    }

    /**
     * Set a track-specific override.
     */
    suspend fun setTrackOverride(trackId: Long, presetId: Long) {
        Log.d(TAG, "setTrackOverride: Saving override trackId=$trackId, presetId=$presetId")
        database?.eqOverrideDao()?.setTrackOverride(
            EqTrackOverride(trackId, presetId)
        )
        Log.d(TAG, "setTrackOverride: Override saved to database")

        // Verify save by reading it back
        val verification = database?.eqOverrideDao()?.getTrackOverride(trackId)
        Log.d(TAG, "setTrackOverride: Verification read - override=${verification != null}, presetId=${verification?.presetId}")

        // Refresh if this is the current track
        currentTrack?.let { track ->
            if (track.id == trackId) {
                Log.d(TAG, "setTrackOverride: Triggering onTrackChanged for current track")
                onTrackChanged(track)
            }
        }
    }

    /**
     * Set an album-specific override.
     */
    suspend fun setAlbumOverride(album: String, albumArtist: String?, presetId: Long) {
        val albumKey = EqAlbumOverride.createKey(album, albumArtist)
        database?.eqOverrideDao()?.setAlbumOverride(
            EqAlbumOverride(albumKey, presetId)
        )
        Log.d(TAG, "Set album override: albumKey=$albumKey, presetId=$presetId")

        // Refresh if current track is from this album
        currentTrack?.let { track ->
            if (track.album == album && track.albumArtist == albumArtist) {
                onTrackChanged(track)
            }
        }
    }

    /**
     * Set an artist-specific override.
     */
    suspend fun setArtistOverride(artist: String, albumArtist: String?, presetId: Long) {
        val artistKey = EqArtistOverride.createKey(artist, albumArtist)
        database?.eqOverrideDao()?.setArtistOverride(
            EqArtistOverride(artistKey, presetId)
        )
        Log.d(TAG, "Set artist override: artistKey=$artistKey, presetId=$presetId")

        // Refresh if current track is from this artist
        currentTrack?.let { track ->
            val trackArtistKey = EqArtistOverride.createKey(track.artist, track.albumArtist)
            if (trackArtistKey == artistKey) {
                onTrackChanged(track)
            }
        }
    }

    /**
     * Clear track override.
     */
    suspend fun clearTrackOverride(trackId: Long) {
        database?.eqOverrideDao()?.clearTrackOverride(trackId)
        Log.d(TAG, "Cleared track override: trackId=$trackId")

        currentTrack?.let { track ->
            if (track.id == trackId) {
                onTrackChanged(track)
            }
        }
    }

    /**
     * Clear album override.
     */
    suspend fun clearAlbumOverride(album: String, albumArtist: String?) {
        val albumKey = EqAlbumOverride.createKey(album, albumArtist)
        database?.eqOverrideDao()?.clearAlbumOverride(albumKey)
        Log.d(TAG, "Cleared album override: albumKey=$albumKey")

        currentTrack?.let { track ->
            if (track.album == album && track.albumArtist == albumArtist) {
                onTrackChanged(track)
            }
        }
    }

    /**
     * Clear artist override.
     */
    suspend fun clearArtistOverride(artist: String, albumArtist: String?) {
        val artistKey = EqArtistOverride.createKey(artist, albumArtist)
        database?.eqOverrideDao()?.clearArtistOverride(artistKey)
        Log.d(TAG, "Cleared artist override: artistKey=$artistKey")

        currentTrack?.let { track ->
            val trackArtistKey = EqArtistOverride.createKey(track.artist, track.albumArtist)
            if (trackArtistKey == artistKey) {
                onTrackChanged(track)
            }
        }
    }

    /**
     * Apply a preset directly (for preview/testing).
     */
    fun applyPresetDirectly(preset: EqPreset) {
        // Apply to DSP engine (primary)
        if (isDspAttached) {
            dspEngine.applyPreset(preset, _isEnabled.value)
        }
        // Apply to standard engine (for virtualizer)
        engine.applyPreset(preset, _isEnabled.value)
        _currentPreset.value = ResolvedPreset(preset, OverrideSource.GLOBAL)

        // Update in-memory tracking
        preset.getBandLevels().forEachIndexed { index, level ->
            if (index < 10) currentBandLevels[index] = level
        }
        currentBassBoost = preset.bassBoostStrength
        currentVirtualizer = preset.virtualizerStrength
    }

    /**
     * Set a single band level (for real-time adjustment).
     * This updates the DSP immediately and saves to a "Custom" preset.
     */
    @Suppress("unused")
    fun setBandLevel(bandIndex: Int, level: Int) {
        // Apply to DSP engine (primary - works on Samsung)
        if (isDspAttached) {
            dspEngine.setBandLevel(bandIndex, level)
        }
        // Apply to standard engine (fallback)
        engine.setBandLevel(bandIndex, level)

        // Update in-memory tracking
        if (bandIndex in 0 until 10) {
            currentBandLevels[bandIndex] = level
        }

        // Save to Custom preset in Room (debounced)
        saveCurrentAsCustomPreset()
    }

    /**
     * Set a single band level without saving (for real-time slider feedback).
     * Use this when the user is actively dragging a slider.
     * Call the save function manually when user releases the slider.
     */
    fun setBandLevelWithoutSave(bandIndex: Int, level: Int) {
        // Apply to DSP engine (primary - works on Samsung)
        if (isDspAttached) {
            dspEngine.setBandLevel(bandIndex, level)
        }
        // Apply to standard engine (fallback)
        engine.setBandLevel(bandIndex, level)

        // Update in-memory tracking
        if (bandIndex in 0 until 10) {
            currentBandLevels[bandIndex] = level
        }
    }

    /**
     * Get current band level.
     */
    @Suppress("unused")
    fun getBandLevel(bandIndex: Int): Int {
        return if (bandIndex in 0 until 10) {
            currentBandLevels[bandIndex]
        } else {
            0
        }
    }

    /**
     * Get all current band levels.
     */
    fun getAllBandLevels(): List<Int> = currentBandLevels.toList()

    /**
     * Set bass boost strength (for real-time adjustment).
     */
    @Suppress("unused")
    fun setBassBoost(strength: Int) {
        // Apply to DSP engine (primary - works on Samsung)
        if (isDspAttached) {
            dspEngine.setBassBoost(strength)
        }
        // Apply to standard engine (fallback)
        engine.setBassBoost(strength)

        // Update in-memory tracking
        currentBassBoost = strength

        // Save to Custom preset in Room
        saveCurrentAsCustomPreset()
    }

    /**
     * Set bass boost strength without saving (for real-time slider feedback).
     */
    fun setBassBoostWithoutSave(strength: Int) {
        // Apply to DSP engine (primary - works on Samsung)
        if (isDspAttached) {
            dspEngine.setBassBoost(strength)
        }
        // Apply to standard engine (fallback)
        engine.setBassBoost(strength)

        // Update in-memory tracking
        currentBassBoost = strength
    }

    /**
     * Get current bass boost strength.
     */
    fun getBassBoost(): Int = currentBassBoost

    /**
     * Set virtualizer strength (for real-time adjustment).
     */
    @Suppress("unused")
    fun setVirtualizer(strength: Int) {
        // Apply to DSP engine
        if (isDspAttached) {
            dspEngine.setVirtualizer(strength)
        }
        // Apply to standard engine (usually works on Samsung)
        engine.setVirtualizer(strength)

        // Update in-memory tracking
        currentVirtualizer = strength

        // Save to Custom preset in Room
        saveCurrentAsCustomPreset()
    }

    /**
     * Set virtualizer strength without saving (for real-time slider feedback).
     */
    fun setVirtualizerWithoutSave(strength: Int) {
        // Apply to DSP engine
        if (isDspAttached) {
            dspEngine.setVirtualizer(strength)
        }
        // Apply to standard engine (usually works on Samsung)
        engine.setVirtualizer(strength)

        // Update in-memory tracking
        currentVirtualizer = strength
    }

    /**
     * Get current virtualizer strength.
     */
    fun getVirtualizer(): Int = currentVirtualizer

    /**
     * Create or update a custom preset with the given values and return its ID.
     * Used when saving slider adjustments to a specific target (Track/Album/Artist/Global).
     *
     * @param presetName The name of the preset to create/update. Each target (track/album/artist)
     *   should use a unique name to avoid overwriting another target's custom preset.
     *   Use [CUSTOM_PRESET_NAME] for the global custom slot.
     */
    suspend fun createOrUpdateCustomPreset(bandLevels: List<Int>, bassBoost: Int, virtualizer: Int, presetName: String = CUSTOM_PRESET_NAME): Long {
        val db = database ?: return -1

        // Find or create the preset for this specific name
        var customPreset = db.eqPresetDao().getPresetByName(presetName)

        if (customPreset == null) {
            // Create new preset for this target
            customPreset = EqPreset(
                name = presetName,
                isSystemPreset = false,
                band32Hz = bandLevels.getOrElse(0) { 0 },
                band64Hz = bandLevels.getOrElse(1) { 0 },
                band125Hz = bandLevels.getOrElse(2) { 0 },
                band250Hz = bandLevels.getOrElse(3) { 0 },
                band500Hz = bandLevels.getOrElse(4) { 0 },
                band1kHz = bandLevels.getOrElse(5) { 0 },
                band2kHz = bandLevels.getOrElse(6) { 0 },
                band4kHz = bandLevels.getOrElse(7) { 0 },
                band8kHz = bandLevels.getOrElse(8) { 0 },
                band16kHz = bandLevels.getOrElse(9) { 0 },
                bassBoostStrength = bassBoost,
                virtualizerStrength = virtualizer
            )
            val newId = db.eqPresetDao().insertPreset(customPreset)
            Log.d(TAG, "createOrUpdateCustomPreset: Created preset '$presetName' with id=$newId")
            return newId
        } else {
            // Update existing preset for this target
            customPreset = customPreset.copy(
                band32Hz = bandLevels.getOrElse(0) { 0 },
                band64Hz = bandLevels.getOrElse(1) { 0 },
                band125Hz = bandLevels.getOrElse(2) { 0 },
                band250Hz = bandLevels.getOrElse(3) { 0 },
                band500Hz = bandLevels.getOrElse(4) { 0 },
                band1kHz = bandLevels.getOrElse(5) { 0 },
                band2kHz = bandLevels.getOrElse(6) { 0 },
                band4kHz = bandLevels.getOrElse(7) { 0 },
                band8kHz = bandLevels.getOrElse(8) { 0 },
                band16kHz = bandLevels.getOrElse(9) { 0 },
                bassBoostStrength = bassBoost,
                virtualizerStrength = virtualizer
            )
            db.eqPresetDao().updatePreset(customPreset)
            Log.d(TAG, "createOrUpdateCustomPreset: Updated preset '$presetName' id=${customPreset.id}")
            return customPreset.id
        }
    }

    /**
     * Save current EQ settings to a "Custom" preset in Room.
     * This is called when user manually adjusts sliders.
     */
    private fun saveCurrentAsCustomPreset() {
        scope.launch(Dispatchers.IO) {
            try {
                val db = database ?: return@launch

                // Find or create "Custom" preset
                var customPreset = db.eqPresetDao().getPresetByName(CUSTOM_PRESET_NAME)

                if (customPreset == null) {
                    // Create new Custom preset
                    customPreset = EqPreset(
                        name = CUSTOM_PRESET_NAME,
                        isSystemPreset = false,
                        band32Hz = currentBandLevels[0],
                        band64Hz = currentBandLevels[1],
                        band125Hz = currentBandLevels[2],
                        band250Hz = currentBandLevels[3],
                        band500Hz = currentBandLevels[4],
                        band1kHz = currentBandLevels[5],
                        band2kHz = currentBandLevels[6],
                        band4kHz = currentBandLevels[7],
                        band8kHz = currentBandLevels[8],
                        band16kHz = currentBandLevels[9],
                        bassBoostStrength = currentBassBoost,
                        virtualizerStrength = currentVirtualizer
                    )
                    val newId = db.eqPresetDao().insertPreset(customPreset)
                    customPreset = customPreset.copy(id = newId)
                    Log.d(TAG, "Created Custom preset with id=$newId")
                } else {
                    // Update existing Custom preset
                    customPreset = customPreset.copy(
                        band32Hz = currentBandLevels[0],
                        band64Hz = currentBandLevels[1],
                        band125Hz = currentBandLevels[2],
                        band250Hz = currentBandLevels[3],
                        band500Hz = currentBandLevels[4],
                        band1kHz = currentBandLevels[5],
                        band2kHz = currentBandLevels[6],
                        band4kHz = currentBandLevels[7],
                        band8kHz = currentBandLevels[8],
                        band16kHz = currentBandLevels[9],
                        bassBoostStrength = currentBassBoost,
                        virtualizerStrength = currentVirtualizer
                    )
                    db.eqPresetDao().updatePreset(customPreset)
                    Log.d(TAG, "Updated Custom preset")
                }

                // Set Custom as global preset so it's used on next launch
                db.eqSettingsDao().setGlobalPresetId(customPreset.id)
                cachedSettings = cachedSettings?.copy(globalPresetId = customPreset.id)

                // Update current preset state
                _currentPreset.value = ResolvedPreset(customPreset, OverrideSource.GLOBAL)

            } catch (e: Exception) {
                Log.e(TAG, "Error saving custom preset: ${e.message}")
            }
        }
    }

    /**
     * Check if bass boost is supported.
     * DSP mode always supports bass boost.
     */
    @Suppress("unused")
    fun isBassBoostSupported(): Boolean {
        return isDspAttached || engine.isBassBoostSupported()
    }

    /**
     * Check if virtualizer is supported.
     * DSP mode always supports virtualizer.
     */
    @Suppress("unused")
    fun isVirtualizerSupported(): Boolean {
        return isDspAttached || engine.isVirtualizerSupported()
    }

    /**
     * Get all presets as a Flow.
     */
    @Suppress("unused")
    fun getAllPresetsFlow(): Flow<List<EqPreset>>? {
        return database?.eqPresetDao()?.getAllPresetsFlow()
    }

    /**
     * Get all presets (suspend).
     * Ensures system presets exist before returning (handles race condition with initialize).
     */
    suspend fun getAllPresets(): List<EqPreset> {
        // Ensure system presets exist (handles race condition with async initialize)
        ensureSystemPresetsExist()
        return database?.eqPresetDao()?.getAllPresets() ?: emptyList()
    }

    /**
     * Get a preset by ID.
     */
    @Suppress("unused")
    suspend fun getPresetById(id: Long): EqPreset? {
        return database?.eqPresetDao()?.getPresetById(id)
    }

    /**
     * Create a new user preset.
     */
    suspend fun createPreset(name: String, bandLevels: List<Int>, bassBoost: Int, virtualizer: Int): Long {
        val preset = EqPreset(
            name = name,
            isSystemPreset = false,
            band32Hz = bandLevels.getOrElse(0) { 0 },
            band64Hz = bandLevels.getOrElse(1) { 0 },
            band125Hz = bandLevels.getOrElse(2) { 0 },
            band250Hz = bandLevels.getOrElse(3) { 0 },
            band500Hz = bandLevels.getOrElse(4) { 0 },
            band1kHz = bandLevels.getOrElse(5) { 0 },
            band2kHz = bandLevels.getOrElse(6) { 0 },
            band4kHz = bandLevels.getOrElse(7) { 0 },
            band8kHz = bandLevels.getOrElse(8) { 0 },
            band16kHz = bandLevels.getOrElse(9) { 0 },
            bassBoostStrength = bassBoost,
            virtualizerStrength = virtualizer
        )
        val id = database?.eqPresetDao()?.insertPreset(preset) ?: -1
        if (id > 0) {
            CloudSyncManager.triggerDebouncedSync()
        }
        return id
    }

    /**
     * Update an existing preset.
     */
    @Suppress("unused")
    suspend fun updatePreset(preset: EqPreset) {
        if (!preset.isSystemPreset) {
            database?.eqPresetDao()?.updatePreset(preset)
            CloudSyncManager.triggerDebouncedSync()
        }
    }

    /**
     * Delete a user preset.
     * Tracks the deletion for cloud sync.
     */
    suspend fun deletePreset(presetId: Long) {
        val dao = database?.eqPresetDao() ?: return

        // Get preset before deleting for cloud sync tracking
        val preset = dao.getPresetById(presetId)

        dao.deletePresetById(presetId)

        // Track deletion for cloud sync (soft-delete)
        if (preset != null && !preset.isSystemPreset) {
            try {
                CloudSyncManager.trackEqPresetDeletion(preset)
                CloudSyncManager.triggerDebouncedSync()
            } catch (e: Exception) {
                Log.w(TAG, "Could not track EQ preset deletion for sync", e)
            }
        }
    }

    /**
     * Get override info for current track.
     */
    @Suppress("unused")
    suspend fun getCurrentTrackOverrideInfo(): OverrideInfo? {
        return currentTrack?.let { resolver?.getOverrideInfo(it) }
    }

    /**
     * Add a listener for equalizer events.
     */
    fun addListener(listener: EqualizerListener) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: EqualizerListener) {
        listeners.remove(listener)
    }

    private fun notifyPresetChanged(resolved: ResolvedPreset) {
        listeners.forEach { it.onPresetChanged(resolved) }
    }

    private fun notifyEnabledChanged(enabled: Boolean) {
        listeners.forEach { it.onEnabledChanged(enabled) }
    }

    /**
     * Release the equalizer engines.
     * Call this when playback stops.
     */
    fun release() {
        dspEngine.release()
        engine.release()
        isDspAttached = false
        Log.d(TAG, "Equalizer engines released")
    }

    /**
     * Listener interface for equalizer events.
     */
    interface EqualizerListener {
        fun onPresetChanged(resolved: ResolvedPreset)
        fun onEnabledChanged(enabled: Boolean)
    }
}
