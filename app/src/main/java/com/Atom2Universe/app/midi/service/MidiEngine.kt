package com.Atom2Universe.app.midi.service

/**
 * Common interface for MIDI synthesis engines
 * Allows switching between Sonivox (lightweight, no SF2) and Sf2Engine (pure Kotlin SF2 support)
 */
interface MidiEngine {

    /**
     * Engine state
     */
    enum class State {
        UNINITIALIZED,
        INITIALIZED,
        MIDI_LOADED,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR
    }

    /**
     * Initializes the engine with optional SoundFont
     * @param soundFontPath Path to SF2/DLS file, or empty string for default sounds
     * @return true if initialization successful, false otherwise
     */
    fun initialize(soundFontPath: String): Boolean

    /**
     * Loads a MIDI file for playback
     * @param filePath Absolute path to MIDI file
     * @return true if loading successful, false otherwise
     */
    fun loadMidiFile(filePath: String): Boolean

    /**
     * Starts playback
     * @return true if playback started successfully, false otherwise
     */
    fun start(): Boolean

    /**
     * Pauses playback (keeps position)
     */
    fun pause()

    /**
     * Resumes playback from paused position
     */
    fun resume()

    /**
     * Stops playback completely
     */
    fun stop()

    /**
     * Releases all resources
     */
    fun release()

    /**
     * Seeks to a specific position in milliseconds
     * @param positionMs Target position in milliseconds
     */
    fun seekTo(positionMs: Long)

    /**
     * Sets the volume (0.0 to 1.0)
     * @param volume Volume level between 0.0 (silent) and 1.0 (max)
     */
    fun setVolume(volume: Float)

    /**
     * Gets the current playback position in milliseconds
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long

    /**
     * Gets the total duration of the loaded MIDI file in milliseconds
     * @return Duration in milliseconds
     */
    fun getDuration(): Long

    /**
     * Checks if playback is currently active
     * @return true if playing, false otherwise
     */
    fun isPlaying(): Boolean

    /**
     * Gets the current engine state
     * @return Current State enum value
     */
    fun getState(): State

    /**
     * Sets a callback for state changes
     * @param listener Lambda called when state changes
     */
    fun setOnStateChangeListener(listener: (State) -> Unit)

    /**
     * Sets a callback for playback completion
     * @param listener Lambda called when playback completes
     */
    fun setOnCompletionListener(listener: () -> Unit)

    /**
     * Sets a callback for errors
     * @param listener Lambda called on errors with error message
     */
    fun setOnErrorListener(listener: (String) -> Unit)

    /**
     * Sets a callback for position updates
     * @param listener Lambda called periodically with (currentMs, durationMs)
     */
    fun setOnPositionChangedListener(listener: (Long, Long) -> Unit)

    /**
     * Reloads with a new SoundFont
     * @param soundFontPath Path to new SF2/DLS file, or empty string for default
     * @return true if reload successful, false otherwise
     */
    fun reloadSoundFont(soundFontPath: String): Boolean

    /**
     * Sets reverb preset
     * @param preset Reverb preset: -1 (off), 0 (large hall), 1 (hall), 2 (chamber), 3 (room)
     */
    fun setReverb(preset: Int)

    /**
     * Sets chorus preset
     * @param preset Chorus preset: -1 (off), 0 (light), 1 (default), 2 (rich)
     */
    fun setChorus(preset: Int)

    /**
     * Gets audio session ID for visualizers
     * @return Audio session ID, or 0 if not available
     */
    fun getAudioSessionId(): Int

    /**
     * Forces a driver restart (for maintenance/recovery)
     */
    fun forceDriverRestart()

    /**
     * Gets driver statistics for debugging
     * @return String with driver stats
     */
    fun getDriverStats(): String
}
