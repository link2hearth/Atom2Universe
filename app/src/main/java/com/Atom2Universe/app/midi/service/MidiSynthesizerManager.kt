package com.Atom2Universe.app.midi.service

import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.sf2.Sf2Engine

/**
 * Smart synthesizer manager that routes between Sonivox, SF2, FluidSynth, and Hybrid engines
 *
 * Operating modes:
 * - SONIVOX_ONLY: Use Sonivox (lightweight, built-in sounds)
 * - SF2_ONLY: Use Sf2Engine (high-quality SF2 playback, pure Kotlin)
 * - FLUIDSYNTH_ONLY: Use FluidSynth (native library, professional-grade synthesis)
 * - HYBRID: Use both SF2 and Sonivox simultaneously, routing by instrument
 *
 * Routing logic:
 * - No SoundFont (empty path): Use Sonivox
 * - SF2 SoundFont loaded: Use Sf2Engine, FluidSynth, or Hybrid based on settings
 * - DLS SoundFont loaded: Use Sonivox (only supports DLS format)
 */
class MidiSynthesizerManager(private val context: Context) : MidiEngine {

    /**
     * Operating mode for the synthesizer
     */
    enum class OperatingMode {
        SONIVOX_ONLY,      // Only Sonivox, no SF2
        SF2_ONLY,          // Only SF2 engine (pure Kotlin)
        FLUIDSYNTH_ONLY,   // Only FluidSynth (native library)
        HYBRID             // Both engines, routing by instrument
    }

    private val sonivoxEngine = SonivoxEngine(context)
    private var sf2Engine: Sf2Engine? = null
    private var fluidSynthEngine: FluidSynthEngine? = null
    private var hybridEngine: HybridMidiEngine? = null

    private var activeEngine: MidiEngine? = null
    private var currentSoundFontPath: String? = null
    private var operatingMode = OperatingMode.SONIVOX_ONLY

    // Hybrid mode configuration
    private var hybridSf2Programs: Set<Int> = emptySet()
    private var hybridUseSf2ForDrums: Boolean = false

    // Store callbacks to re-apply them when engine changes
    private var storedStateChangeListener: ((MidiEngine.State) -> Unit)? = null
    private var storedCompletionListener: (() -> Unit)? = null
    private var storedErrorListener: ((String) -> Unit)? = null
    private var storedPositionChangedListener: ((Long, Long) -> Unit)? = null

    // Store reverb/chorus presets to re-apply when engine changes
    private var storedReverbPreset: Int = -1  // -1 = Off by default
    private var storedChorusPreset: Int = -1  // -1 = Off by default

    override fun initialize(soundFontPath: String): Boolean {
        currentSoundFontPath = soundFontPath

        try {
            // Select engine based on SoundFont presence and type
            val (engine, engineName) = selectEngine(soundFontPath)
            activeEngine = engine
            android.util.Log.i("MidiSynthesizerManager", "initialize: selected engine=$engineName, mode=$operatingMode, path=$soundFontPath")

            // Initialize the selected engine
            val success = activeEngine?.initialize(soundFontPath) ?: false
            android.util.Log.i("MidiSynthesizerManager", "initialize: engine init success=$success")

            if (success) {
                // BUG FIX: Restaurer TOUS les settings audio depuis les préférences
                // Cela garantit que les paramètres sont appliqués même après un changement de SF2/MIDI
                restoreAudioSettingsFromPreferences()

                // Re-apply stored callbacks to the new engine
                applyStoredCallbacks()
                return true
            } else {
                // Fallback to Sonivox if any engine fails (not just SF2Engine)
                android.util.Log.w("MidiSynthesizerManager", "initialize: $engineName failed, falling back to Sonivox")
                if (activeEngine != sonivoxEngine) {
                    activeEngine = sonivoxEngine
                    val fallbackSuccess = sonivoxEngine.initialize("")
                    if (fallbackSuccess) {
                        applyStoredCallbacks()
                    }
                    return fallbackSuccess
                }
            }

            return success
        } catch (e: Exception) {
            // Always fallback to Sonivox on any exception
            android.util.Log.e("MidiSynthesizerManager", "initialize: exception, falling back to Sonivox", e)
            activeEngine = sonivoxEngine
            val fallbackSuccess = sonivoxEngine.initialize("")
            if (fallbackSuccess) {
                applyStoredCallbacks()
            }
            return fallbackSuccess
        }
    }

    /**
     * Applies all stored callbacks and settings to the active engine.
     * Called after engine initialization or switching.
     */
    private fun applyStoredCallbacks() {
        val engine = activeEngine ?: return

        storedStateChangeListener?.let { engine.setOnStateChangeListener(it) }
        storedCompletionListener?.let { engine.setOnCompletionListener(it) }
        storedErrorListener?.let { engine.setOnErrorListener(it) }
        storedPositionChangedListener?.let { engine.setOnPositionChangedListener(it) }

        // Re-apply reverb and chorus presets to the new engine
        engine.setReverb(storedReverbPreset)
        engine.setChorus(storedChorusPreset)
    }

    /**
     * Restaure TOUS les paramètres audio depuis les préférences.
     * Garantit que les paramètres choisis par l'utilisateur sont appliqués même après
     * un changement de SF2, de MIDI, ou un redémarrage de l'app.
     */
    private fun restoreAudioSettingsFromPreferences() {
        try {
            val settingsRepository = com.Atom2Universe.app.midi.repository.SettingsRepository(context)
            kotlinx.coroutines.runBlocking {
                // 1. Restaurer le preset de normalisation du mixer
                val presetIndex = settingsRepository.getMixerPreset()
                val preset = MidiAudioMixer.NormalizationPreset.entries.getOrNull(presetIndex)
                    ?: MidiAudioMixer.NormalizationPreset.MEDIUM
                MidiAudioMixer.setPreset(preset)
                android.util.Log.d("MidiSynthesizerManager", "Restored mixer preset: $preset")

                // 2. Restaurer le master gain
                val gain = settingsRepository.getMasterGain()
                MidiAudioMixer.setMasterGain(gain)
                android.util.Log.d("MidiSynthesizerManager", "Restored master gain: $gain")

                // 3. Restaurer la courbe de vélocité (SF2)
                val curveIndex = settingsRepository.getSf2VelocityCurve()
                val curve = when (curveIndex) {
                    0 -> com.Atom2Universe.app.midi.sf2.VelocityCurve.LINEAR
                    1 -> com.Atom2Universe.app.midi.sf2.VelocityCurve.CONCAVE
                    2 -> com.Atom2Universe.app.midi.sf2.VelocityCurve.SOFT
                    3 -> com.Atom2Universe.app.midi.sf2.VelocityCurve.HARD
                    else -> com.Atom2Universe.app.midi.sf2.VelocityCurve.CONCAVE
                }
                com.Atom2Universe.app.midi.sf2.Sf2Voice.velocityCurve = curve
                android.util.Log.d("MidiSynthesizerManager", "Restored velocity curve: $curve")

                // 4. Restaurer l'EQ (enabled + 10 bandes)
                val eqEnabled = settingsRepository.getMidiEqEnabled()
                (activeEngine as? Sf2Engine)?.setEqEnabled(eqEnabled)
                for (band in 0 until com.Atom2Universe.app.midi.sf2.MidiEqualizerEngine.BAND_COUNT) {
                    val mb = settingsRepository.getMidiEqBandLevel(band)
                    (activeEngine as? Sf2Engine)?.setEqBandLevel(band, mb)
                }
                android.util.Log.d("MidiSynthesizerManager", "Restored EQ: enabled=$eqEnabled")
            }
        } catch (e: Exception) {
            android.util.Log.w("MidiSynthesizerManager", "Failed to restore audio settings", e)
        }
    }

    /**
     * Detaches callbacks from the specified engine to prevent stale updates
     * when switching engines or stopping playback.
     */
    private fun detachCallbacks(engine: MidiEngine?) {
        engine?.setOnStateChangeListener { }
        engine?.setOnCompletionListener { }
        engine?.setOnErrorListener { }
        engine?.setOnPositionChangedListener { _, _ -> }
    }

    /**
     * Selects the appropriate engine based on operating mode and SoundFont availability.
     * The operatingMode should be set via setOperatingMode() before calling initialize().
     * @return Pair of (engine, engine name)
     */
    private fun selectEngine(soundFontPath: String): Pair<MidiEngine, String> {
        // First, check if we need SF2 and if it's available
        val sf2Available = soundFontPath.isNotBlank() &&
            java.io.File(soundFontPath).exists() &&
            soundFontPath.substringAfterLast('.', "").lowercase() in listOf("sf2", "sf3")

        // Select engine based on operating mode
        return when (operatingMode) {
            OperatingMode.SONIVOX_ONLY -> {
                Pair(sonivoxEngine, "Sonivox")
            }

            OperatingMode.HYBRID -> {
                if (sf2Available && hybridSf2Programs.isNotEmpty()) {
                    try {
                        createHybridEngine()
                    } catch (_: Exception) {
                        operatingMode = OperatingMode.SONIVOX_ONLY
                        Pair(sonivoxEngine, "Sonivox")
                    }
                } else {
                    operatingMode = OperatingMode.SONIVOX_ONLY
                    Pair(sonivoxEngine, "Sonivox")
                }
            }

            OperatingMode.SF2_ONLY -> {
                if (sf2Available) {
                    try {
                        // Release previous SF2 engine if exists
                        sf2Engine?.release()
                        // Create new SF2 engine instance for this SoundFont
                        sf2Engine = Sf2Engine(context)
                        Pair(sf2Engine!!, "SF2Engine")
                    } catch (_: Exception) {
                        operatingMode = OperatingMode.SONIVOX_ONLY
                        Pair(sonivoxEngine, "Sonivox")
                    }
                } else {
                    operatingMode = OperatingMode.SONIVOX_ONLY
                    Pair(sonivoxEngine, "Sonivox")
                }
            }

            OperatingMode.FLUIDSYNTH_ONLY -> {
                if (sf2Available && FluidSynthEngine.isSupported()) {
                    try {
                        // Release previous FluidSynth engine if exists
                        fluidSynthEngine?.release()
                        // Create new FluidSynth engine instance
                        fluidSynthEngine = FluidSynthEngine(context)
                        Pair(fluidSynthEngine!!, "FluidSynth")
                    } catch (e: Exception) {
                        android.util.Log.e("MidiSynthesizerManager", "FluidSynth init failed, falling back to SF2Engine", e)
                        // Fallback to SF2Engine if FluidSynth fails
                        operatingMode = OperatingMode.SF2_ONLY
                        sf2Engine?.release()
                        sf2Engine = Sf2Engine(context)
                        Pair(sf2Engine!!, "SF2Engine")
                    }
                } else if (sf2Available) {
                    // FluidSynth not supported, fallback to SF2Engine
                    android.util.Log.w("MidiSynthesizerManager", "FluidSynth not supported, using SF2Engine")
                    operatingMode = OperatingMode.SF2_ONLY
                    sf2Engine?.release()
                    sf2Engine = Sf2Engine(context)
                    Pair(sf2Engine!!, "SF2Engine")
                } else {
                    operatingMode = OperatingMode.SONIVOX_ONLY
                    Pair(sonivoxEngine, "Sonivox")
                }
            }
        }
    }

    /**
     * Creates and configures a HybridMidiEngine.
     */
    private fun createHybridEngine(): Pair<MidiEngine, String> {
        try {
            // Release previous engines
            hybridEngine?.release()
            hybridEngine = null
            sf2Engine?.release()
            sf2Engine = null

            // Create fresh SF2 engine for hybrid use
            val newSf2Engine = Sf2Engine(context)
            sf2Engine = newSf2Engine

            // Create hybrid engine
            val newHybridEngine = HybridMidiEngine(context, newSf2Engine, sonivoxEngine)
            hybridEngine = newHybridEngine

            // Configure hybrid routing BEFORE initialization (so SF2 engine is created with right config)
            newHybridEngine.configureSf2Programs(hybridSf2Programs, hybridUseSf2ForDrums)

            return Pair(newHybridEngine, "HybridEngine")
        } catch (_: Exception) {
            operatingMode = OperatingMode.SF2_ONLY
            sf2Engine?.release()
            sf2Engine = Sf2Engine(context)
            return Pair(sf2Engine!!, "SF2Engine")
        }
    }

    /**
     * Sets the operating mode for the synthesizer.
     * This configures how MIDI events are routed.
     *
     * NOTE: This only sets the mode configuration. The caller must call
     * initialize() or reloadSoundFont() to apply the new mode.
     *
     * @param mode The operating mode (SONIVOX_ONLY, SF2_ONLY, or HYBRID)
     * @param sf2Programs For HYBRID mode: programs (0-127) that use SF2 synthesis
     * @param useSf2ForDrums For HYBRID mode: whether channel 9 uses SF2
     */
    fun setOperatingMode(
        mode: OperatingMode,
        sf2Programs: Set<Int> = emptySet(),
        useSf2ForDrums: Boolean = false
    ) {
        operatingMode = mode
        hybridSf2Programs = sf2Programs
        hybridUseSf2ForDrums = useSf2ForDrums

        // NOTE: We don't auto-reconfigure here anymore.
        // The caller (handleReloadSoundFont) will call reloadSoundFont() explicitly.
        // This prevents issues where reconfigureForMode() uses old/empty soundFontPath.
    }

    /**
     * Gets the current operating mode.
     */
    @Suppress("unused")
    fun getOperatingMode(): OperatingMode = operatingMode

    /**
     * Gets the current hybrid SF2 programs configuration.
     */
    @Suppress("unused")
    fun getHybridSf2Programs(): Set<Int> = hybridSf2Programs

    /**
     * Gets whether hybrid mode uses SF2 for drums.
     */
    @Suppress("unused")
    fun isHybridUsingSf2ForDrums(): Boolean = hybridUseSf2ForDrums

    /**
     * Updates the hybrid mode configuration on the fly without restarting the engine.
     * Only works when already in HYBRID mode with a HybridMidiEngine active.
     *
     * @param sf2Programs Programs (0-127) that should use SF2 synthesis
     * @param useSf2ForDrums Whether channel 9 (drums) should use SF2
     * @return true if configuration was updated, false if not in hybrid mode
     */
    @Suppress("unused")
    fun updateHybridConfiguration(sf2Programs: Set<Int>, useSf2ForDrums: Boolean): Boolean {
        if (operatingMode != OperatingMode.HYBRID || hybridEngine == null) {
            return false
        }

        // Update stored configuration
        hybridSf2Programs = sf2Programs
        hybridUseSf2ForDrums = useSf2ForDrums

        // Update the running engine
        hybridEngine?.configureSf2Programs(sf2Programs, useSf2ForDrums)

        return true
    }

    /**
     * Reconfigures the engines for the current operating mode.
     * Called when the mode changes after initialization.
     */
    @Suppress("unused")
    private fun reconfigureForMode() {
        val soundFontPath = currentSoundFontPath ?: ""

        // Stop current playback
        try {
            detachCallbacks(activeEngine)
            activeEngine?.stop()
        } catch (_: Exception) { }

        // Release hybrid engine if switching away from hybrid
        if (operatingMode != OperatingMode.HYBRID && hybridEngine != null) {
            try {
                hybridEngine?.release()
                hybridEngine = null
            } catch (_: Exception) { }
        }

        // Reinitialize with current SoundFont
        initialize(soundFontPath)
    }

    /**
     * Switches to a different SoundFont (or removes it)
     * This will stop playback, switch engines if needed, and reinitialize
     */
    fun switchSoundFont(newSoundFontPath: String?): Boolean {
        // Stop current playback
        try {
            detachCallbacks(activeEngine)
            activeEngine?.stop()
        } catch (_: Exception) { }

        // Release current engine
        try {
            activeEngine?.release()
        } catch (_: Exception) { }

        // Reinitialize with new SoundFont
        return initialize(newSoundFontPath ?: "")
    }

    /**
     * Gets the name of the currently active engine
     */
    fun getActiveEngineName(): String {
        return when (activeEngine) {
            sonivoxEngine -> "Sonivox"
            sf2Engine -> "SF2Engine"
            fluidSynthEngine -> "FluidSynth"
            hybridEngine -> "HybridEngine"
            else -> "None"
        }
    }

    /**
     * Checks if a SoundFont is currently loaded
     */
    @Suppress("unused")
    fun hasSoundFontLoaded(): Boolean {
        return !currentSoundFontPath.isNullOrBlank()
    }

    /**
     * Gets the current SoundFont path
     */
    @Suppress("unused")
    fun getCurrentSoundFontPath(): String? {
        return currentSoundFontPath
    }

    // === MidiEngine interface delegation ===
    // All methods delegate to the active engine

    override fun loadMidiFile(filePath: String): Boolean {
        return activeEngine?.loadMidiFile(filePath) ?: false
    }

    override fun start(): Boolean {
        return activeEngine?.start() ?: false
    }

    override fun pause() {
        activeEngine?.pause()
    }

    override fun resume() {
        activeEngine?.resume()
    }

    override fun stop() {
        activeEngine?.stop()
    }

    /**
     * Stops playback without stopping the underlying audio engines.
     * Use this when transitioning between tracks to avoid race conditions
     * with Oboe's audio callback threads (prevents SIGBUS crashes).
     *
     * This only affects HybridMidiEngine - other engines use regular stop().
     */
    fun stopForTrackTransition() {
        when (val engine = activeEngine) {
            is HybridMidiEngine -> engine.stopForTransition(stopAudioEngines = false)
            else -> engine?.stop()
        }
    }

    override fun release() {
        try {
            hybridEngine?.release()
            hybridEngine = null
        } catch (_: Exception) { }

        try {
            sonivoxEngine.release()
        } catch (_: Exception) { }

        try {
            sf2Engine?.release()
            sf2Engine = null
        } catch (_: Exception) { }

        try {
            fluidSynthEngine?.release()
            fluidSynthEngine = null
        } catch (_: Exception) { }

        activeEngine = null
        currentSoundFontPath = null
        operatingMode = OperatingMode.SONIVOX_ONLY
    }

    /**
     * Returns true if the engine has been released and needs re-initialization
     * before playback can resume.
     */
    fun needsReinitialization(): Boolean = activeEngine == null

    override fun seekTo(positionMs: Long) {
        activeEngine?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        activeEngine?.setVolume(volume)
    }

    /**
     * Signale le changement d'état de l'écran au SF2Engine.
     * Quand isOff=true, force le rendu séquentiel pour éviter les craquements
     * causés par le throttling CPU du gouverneur Android en background.
     */
    fun setScreenOff(isOff: Boolean) {
        sf2Engine?.setScreenOff(isOff)
    }

    override fun setReverb(preset: Int) {
        // Store the preset so it persists across engine changes
        storedReverbPreset = preset
        activeEngine?.setReverb(preset)
    }

    override fun setChorus(preset: Int) {
        storedChorusPreset = preset
        activeEngine?.setChorus(preset)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        (activeEngine as? Sf2Engine)?.setEqEnabled(enabled)
    }

    fun setEqualizerBandLevel(band: Int, millibels: Int) {
        (activeEngine as? Sf2Engine)?.setEqBandLevel(band, millibels)
    }

    fun getEqualizerBandLevel(band: Int): Int =
        (activeEngine as? Sf2Engine)?.getEqBandLevel(band) ?: 0

    override fun getCurrentPosition(): Long {
        return activeEngine?.getCurrentPosition() ?: 0L
    }

    override fun getDuration(): Long {
        return activeEngine?.getDuration() ?: 0L
    }

    override fun isPlaying(): Boolean {
        return activeEngine?.isPlaying() ?: false
    }

    override fun getState(): MidiEngine.State {
        return activeEngine?.getState() ?: MidiEngine.State.UNINITIALIZED
    }

    override fun getAudioSessionId(): Int {
        return activeEngine?.getAudioSessionId() ?: 0
    }

    override fun setOnStateChangeListener(listener: (MidiEngine.State) -> Unit) {
        storedStateChangeListener = listener
        activeEngine?.setOnStateChangeListener(listener)
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        storedCompletionListener = listener
        activeEngine?.setOnCompletionListener(listener)
    }

    override fun setOnErrorListener(listener: (String) -> Unit) {
        storedErrorListener = listener
        activeEngine?.setOnErrorListener(listener)
    }

    override fun setOnPositionChangedListener(listener: (Long, Long) -> Unit) {
        storedPositionChangedListener = listener
        activeEngine?.setOnPositionChangedListener(listener)
    }

    override fun reloadSoundFont(soundFontPath: String): Boolean {
        return switchSoundFont(soundFontPath)
    }

    override fun forceDriverRestart() {
        activeEngine?.forceDriverRestart()
    }

    override fun getDriverStats(): String {
        val engineName = getActiveEngineName()
        val engineStats = activeEngine?.getDriverStats() ?: "N/A"
        return "Active: $engineName | $engineStats"
    }

    // ==================== Audio Device Routing ====================

    /**
     * Forces audio output to the built-in speaker.
     * Call this when a USB MIDI device is connected and may be hijacking audio output.
     * Routes the active engine (Sonivox, SF2, FluidSynth, or Hybrid) to the speaker.
     * @return true if successfully routed to speaker
     */
    fun forceOutputToSpeaker(): Boolean {
        // Route the active engine to speaker
        val result = when (val engine = activeEngine) {
            is Sf2Engine -> engine.forceOutputToSpeaker()
            is FluidSynthEngine -> engine.forceOutputToSpeaker("MidiSynthesizerManager")
            is HybridMidiEngine -> {
                // Hybrid uses both engines, route both
                val sf2Result = sf2Engine?.forceOutputToSpeaker() == true
                val sonivoxResult = sonivoxEngine.forceOutputToSpeaker()
                sf2Result || sonivoxResult
            }
            else -> sonivoxEngine.forceOutputToSpeaker()
        }
        android.util.Log.d("MidiSynthesizerManager", "forceOutputToSpeaker: activeEngine=${getActiveEngineName()} result=$result")
        return result
    }

    /**
     * Resets audio output to default device routing.
     * @return true if successfully reset
     */
    @Suppress("unused")
    fun resetOutputDevice(): Boolean {
        // Reset the active engine's output device
        return when (val engine = activeEngine) {
            is Sf2Engine -> engine.resetOutputDevice()
            is FluidSynthEngine -> engine.resetPreferredOutput("MidiSynthesizerManager")
            is HybridMidiEngine -> {
                val sf2Result = sf2Engine?.resetOutputDevice() == true
                val sonivoxResult = sonivoxEngine.resetOutputDevice()
                sf2Result || sonivoxResult
            }
            else -> sonivoxEngine.resetOutputDevice()
        }
    }

    /**
     * Checks if any USB MIDI devices are connected.
     * @return true if at least one USB MIDI device is connected
     */
    fun hasConnectedUsbMidiDevice(): Boolean {
        return try {
            val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
            @Suppress("DEPRECATION")
            val devices = midiManager?.devices ?: return false
            devices.any { it.type == MidiDeviceInfo.TYPE_USB }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks for USB MIDI devices and forces audio to speaker if any are connected.
     * Call this after initializing the synthesizer to prevent USB MIDI devices
     * from hijacking audio output.
     * @return true if speaker routing was applied, false if no USB MIDI devices found
     */
    fun autoConfigureAudioOutput(): Boolean {
        if (hasConnectedUsbMidiDevice()) {
            android.util.Log.d("MidiSynthesizerManager", "USB MIDI device detected, forcing audio to speaker")
            return forceOutputToSpeaker()
        }
        return false
    }

    /**
     * Synchronizes the visualizer (keyboards) with the current playback position.
     * This rescans the MIDI timeline to determine which notes should be currently active
     * and sends them to the MidiEventDispatcher.
     *
     * This is useful for the refresh button to restore the correct visual state.
     */
    fun syncVisualizerToCurrentPosition() {
        // Delegate to the active engine if it supports this operation
        when (val engine = activeEngine) {
            is HybridMidiEngine -> engine.syncVisualizerToCurrentPosition()
            is Sf2Engine -> engine.syncVisualizerToCurrentPosition()
            is FluidSynthEngine -> engine.syncVisualizerToCurrentPosition()
            // SonivoxEngine doesn't have its own timeline, so we fall back to
            // the MidiEventDispatcher's tracker state (without resending cached analysis
            // which would cause the viewHolderMap to be cleared)
            else -> {
                // Clear tracker notes first, then resend active notes
                // DO NOT call resendCachedAnalysis() - it triggers setTracks() which
                // clears viewHolderMap and DiffUtil skips rebinding identical items
                com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher.clearTrackerActiveNotes()
                com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher.resendActiveNotes()
            }
        }
    }

    /**
     * Checks if FluidSynth native library is available on this device.
     */
    @Suppress("unused")
    fun isFluidSynthSupported(): Boolean = FluidSynthEngine.isSupported()
}
