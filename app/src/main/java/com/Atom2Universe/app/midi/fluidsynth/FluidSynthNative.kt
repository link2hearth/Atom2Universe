package com.Atom2Universe.app.midi.fluidsynth

/**
 * JNI wrapper for FluidSynth native library.
 *
 * This class provides low-level access to FluidSynth functions.
 * Use FluidSynthEngine for a higher-level MidiEngine implementation.
 *
 * All handle parameters (settingsHandle, synthHandle, etc.) are native pointers
 * stored as Long values. A value of 0 indicates a null/invalid handle.
 */
@Suppress("unused")
object FluidSynthNative {

    private var isLoaded = false

    /**
     * Load the native library. Call this before using any other methods.
     * This loads both the JNI wrapper (fluidsynth_jni) and the FluidSynth library
     * itself via dlopen.
     * @return true if loaded successfully
     */
    @Synchronized
    fun loadLibrary(): Boolean {
        if (isLoaded) return true

        return try {
            // Pre-load libfluidsynth.so via the Android ClassLoader so it is in the process
            // address space before dlopen is called from native code.
            // Without this, dlopen("libfluidsynth.so") fails on modern Android because the
            // bionic linker does not search inside the APK — it only resolves names that are
            // already loaded or present on LD_LIBRARY_PATH (which points to extracted libs only).
            System.loadLibrary("fluidsynth")
            android.util.Log.i("FluidSynthNative", "Pre-loaded libfluidsynth.so")

            // Load the JNI wrapper that uses dlopen/dlsym to bind FluidSynth symbols
            System.loadLibrary("fluidsynth_jni")
            android.util.Log.i("FluidSynthNative", "Loaded fluidsynth_jni wrapper")

            // dlopen("libfluidsynth.so") inside nativeLoadLibrary() now finds the
            // already-loaded library in the process namespace
            if (!nativeLoadLibrary()) {
                android.util.Log.w("FluidSynthNative", "FluidSynth symbol binding failed")
                return false
            }

            isLoaded = true
            android.util.Log.i("FluidSynthNative", "FluidSynth loaded successfully, version: ${getVersion()}")
            true
        } catch (_: UnsatisfiedLinkError) {
            android.util.Log.d("FluidSynthNative", "FluidSynth native library not available on this device")
            false
        }
    }

    /**
     * Native method to load FluidSynth library via dlopen.
     * Called internally by loadLibrary().
     */
    @JvmStatic
    private external fun nativeLoadLibrary(): Boolean

    fun isLibraryLoaded(): Boolean = isLoaded

    // ==================== Settings ====================

    /**
     * Create new FluidSynth settings with Android-optimized defaults.
     * @return Settings handle, or 0 on failure
     */
    @JvmStatic
    external fun newSettings(): Long

    /**
     * Delete settings and free memory.
     */
    @JvmStatic
    external fun deleteSettings(settingsHandle: Long)

    /**
     * Set a string setting.
     */
    @JvmStatic
    external fun setSettingStr(settingsHandle: Long, name: String, value: String): Boolean

    /**
     * Set an integer setting.
     */
    @JvmStatic
    external fun setSettingInt(settingsHandle: Long, name: String, value: Int): Boolean

    /**
     * Set a numeric (double) setting.
     */
    @JvmStatic
    external fun setSettingNum(settingsHandle: Long, name: String, value: Double): Boolean

    // ==================== Synth ====================

    /**
     * Create a new synthesizer instance.
     * @param settingsHandle Settings to use (from newSettings())
     * @return Synth handle, or 0 on failure
     */
    @JvmStatic
    external fun newSynth(settingsHandle: Long): Long

    /**
     * Delete synth and free memory.
     */
    @JvmStatic
    external fun deleteSynth(synthHandle: Long)

    // ==================== SoundFont ====================

    /**
     * Load a SoundFont file.
     * @param synthHandle Synth to load into
     * @param path Absolute path to SF2/SF3 file
     * @param resetPresets If true, reset presets after loading
     * @return SoundFont ID (>= 0) or -1 on failure
     */
    @JvmStatic
    external fun sfLoad(synthHandle: Long, path: String, resetPresets: Boolean): Int

    /**
     * Unload a SoundFont.
     * @param sfId SoundFont ID from sfLoad()
     */
    @JvmStatic
    external fun sfUnload(synthHandle: Long, sfId: Int, resetPresets: Boolean): Boolean

    // ==================== MIDI Events ====================

    /**
     * Send a Note On event.
     * @param channel MIDI channel (0-15)
     * @param note Note number (0-127)
     * @param velocity Velocity (0-127, 0 = note off)
     */
    @JvmStatic
    external fun noteOn(synthHandle: Long, channel: Int, note: Int, velocity: Int): Boolean

    /**
     * Send a Note Off event.
     */
    @JvmStatic
    external fun noteOff(synthHandle: Long, channel: Int, note: Int): Boolean

    /**
     * Send a Program Change event.
     * @param program Program/instrument number (0-127)
     */
    @JvmStatic
    external fun programChange(synthHandle: Long, channel: Int, program: Int): Boolean

    /**
     * Send a Bank Select event.
     */
    @JvmStatic
    external fun bankSelect(synthHandle: Long, channel: Int, bank: Int): Boolean

    /**
     * Send a Control Change event.
     * @param controller CC number (0-127)
     * @param value CC value (0-127)
     */
    @JvmStatic
    external fun cc(synthHandle: Long, channel: Int, controller: Int, value: Int): Boolean

    /**
     * Send a Pitch Bend event.
     * @param value Pitch bend value (0-16383, center = 8192)
     */
    @JvmStatic
    external fun pitchBend(synthHandle: Long, channel: Int, value: Int): Boolean

    /**
     * Turn off all notes on a channel (with release).
     * @param channel Channel number, or -1 for all channels
     */
    @JvmStatic
    external fun allNotesOff(synthHandle: Long, channel: Int): Boolean

    /**
     * Immediately stop all sounds on a channel (no release).
     * @param channel Channel number, or -1 for all channels
     */
    @JvmStatic
    external fun allSoundOff(synthHandle: Long, channel: Int): Boolean

    /**
     * Reset the synthesizer to initial state.
     */
    @JvmStatic
    external fun systemReset(synthHandle: Long)

    // ==================== Synth Parameters ====================

    /**
     * Set master gain (volume).
     * @param gain Gain value (0.0 to 10.0, default 0.2)
     */
    @JvmStatic
    external fun setGain(synthHandle: Long, gain: Float)

    /**
     * Get current master gain.
     */
    @JvmStatic
    external fun getGain(synthHandle: Long): Float

    /**
     * Get maximum polyphony setting.
     */
    @JvmStatic
    external fun getPolyphony(synthHandle: Long): Int

    /**
     * Get number of currently active voices.
     */
    @JvmStatic
    external fun getActiveVoiceCount(synthHandle: Long): Int

    // ==================== Effects ====================

    /**
     * Enable or disable reverb.
     */
    @JvmStatic
    external fun setReverbOn(synthHandle: Long, on: Boolean)

    /**
     * Set reverb parameters.
     * @param roomSize Room size (0.0 - 1.0)
     * @param damping Damping (0.0 - 1.0)
     * @param width Width (0.0 - 100.0)
     * @param level Output level (0.0 - 1.0)
     */
    @JvmStatic
    external fun setReverb(synthHandle: Long, roomSize: Double, damping: Double, width: Double, level: Double)

    /**
     * Enable or disable chorus.
     */
    @JvmStatic
    external fun setChorusOn(synthHandle: Long, on: Boolean)

    // ==================== Audio Rendering ====================

    /**
     * Render audio to interleaved stereo short buffer.
     * @param buffer Output buffer (interleaved L/R samples)
     * @param offset Start offset in buffer
     * @param frames Number of frames to render
     * @return Number of frames rendered, or -1 on error
     */
    @JvmStatic
    external fun writeStereoShort(synthHandle: Long, buffer: ShortArray, offset: Int, frames: Int): Int

    /**
     * Render audio to separate float buffers.
     * @param leftBuffer Left channel output
     * @param rightBuffer Right channel output
     * @param frames Number of frames to render
     * @return Number of frames rendered, or -1 on error
     */
    @JvmStatic
    external fun writeStereoFloat(synthHandle: Long, leftBuffer: FloatArray, rightBuffer: FloatArray, frames: Int): Int

    // ==================== Audio Driver ====================

    /**
     * Create an audio driver (uses Oboe on Android).
     * This handles audio output automatically - no need to call writeStereo*.
     * @return Driver handle, or 0 on failure
     */
    @JvmStatic
    external fun newAudioDriver(settingsHandle: Long, synthHandle: Long): Long

    /**
     * Delete audio driver.
     */
    @JvmStatic
    external fun deleteAudioDriver(driverHandle: Long)

    // ==================== MIDI Player ====================

    /**
     * Create a MIDI file player.
     * @return Player handle, or 0 on failure
     */
    @JvmStatic
    external fun newPlayer(synthHandle: Long): Long

    /**
     * Delete MIDI player.
     */
    @JvmStatic
    external fun deletePlayer(playerHandle: Long)

    /**
     * Add a MIDI file to the player queue.
     */
    @JvmStatic
    external fun playerAdd(playerHandle: Long, midiPath: String): Boolean

    /**
     * Start playback.
     */
    @JvmStatic
    external fun playerPlay(playerHandle: Long): Boolean

    /**
     * Stop playback.
     */
    @JvmStatic
    external fun playerStop(playerHandle: Long)

    /**
     * Get player status.
     * @return 0 = READY, 1 = PLAYING, 2 = STOPPING, 3 = DONE
     */
    @JvmStatic
    external fun playerGetStatus(playerHandle: Long): Int

    /**
     * Seek to a position in ticks.
     */
    @JvmStatic
    external fun playerSeek(playerHandle: Long, ticks: Int): Int

    /**
     * Get current playback position in ticks.
     */
    @JvmStatic
    external fun playerGetCurrentTick(playerHandle: Long): Int

    /**
     * Get total duration in ticks.
     */
    @JvmStatic
    external fun playerGetTotalTicks(playerHandle: Long): Int

    /**
     * Set loop count.
     * @param loops Number of loops (-1 = infinite, 0 = play once)
     */
    @JvmStatic
    external fun playerSetLoop(playerHandle: Long, loops: Int)

    /**
     * Set playback tempo.
     * @param tempoType 0 = FLUID_PLAYER_TEMPO_INTERNAL, 1 = FLUID_PLAYER_TEMPO_EXTERNAL_BPM, 2 = FLUID_PLAYER_TEMPO_EXTERNAL_MIDI
     * @param tempo Tempo value
     */
    @JvmStatic
    external fun playerSetTempo(playerHandle: Long, tempoType: Int, tempo: Double)

    // ==================== Utility ====================

    /**
     * Get FluidSynth library version.
     */
    @JvmStatic
    external fun getVersion(): String

    // ==================== Player Status Constants ====================

    const val PLAYER_READY = 0
    const val PLAYER_PLAYING = 1
    const val PLAYER_STOPPING = 2
    const val PLAYER_DONE = 3

    // ==================== Tempo Type Constants ====================

    const val TEMPO_INTERNAL = 0
    const val TEMPO_EXTERNAL_BPM = 1
    const val TEMPO_EXTERNAL_MIDI = 2

    // ==================== Common CC Numbers ====================

    const val CC_BANK_SELECT_MSB = 0
    const val CC_MODULATION = 1
    const val CC_BREATH = 2
    const val CC_FOOT = 4
    const val CC_PORTAMENTO_TIME = 5
    const val CC_DATA_ENTRY_MSB = 6
    const val CC_VOLUME = 7
    const val CC_BALANCE = 8
    const val CC_PAN = 10
    const val CC_EXPRESSION = 11
    const val CC_BANK_SELECT_LSB = 32
    const val CC_SUSTAIN = 64
    const val CC_PORTAMENTO = 65
    const val CC_SOSTENUTO = 66
    const val CC_SOFT_PEDAL = 67
    const val CC_LEGATO = 68
    const val CC_HOLD2 = 69
    const val CC_SOUND_VARIATION = 70
    const val CC_RESONANCE = 71
    const val CC_RELEASE_TIME = 72
    const val CC_ATTACK_TIME = 73
    const val CC_CUTOFF = 74
    const val CC_REVERB_SEND = 91
    const val CC_TREMOLO = 92
    const val CC_CHORUS_SEND = 93
    const val CC_DETUNE = 94
    const val CC_PHASER = 95
    const val CC_ALL_SOUND_OFF = 120
    const val CC_RESET_ALL = 121
    const val CC_ALL_NOTES_OFF = 123
}
