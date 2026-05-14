package com.Atom2Universe.app.midi.fluidsynth

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.util.Log
import com.Atom2Universe.app.midi.service.MidiEngine
import com.Atom2Universe.app.midi.sf2.MidiEqualizerEngine
import com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher
import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.meta.Tempo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * FluidSynth Engine - High-quality SF2 synthesis via FluidSynth native library.
 *
 * This engine provides professional-grade SF2 SoundFont synthesis using the
 * FluidSynth library (via JNI). It implements the MidiEngine interface for
 * seamless integration with the existing MIDI playback system.
 *
 * Features:
 * - High-quality SF2/SF3 synthesis
 * - Built-in reverb and chorus effects
 * - Low-latency audio output via Oboe
 * - MIDI file playback via FluidSynth's player
 */
class FluidSynthEngine(private val context: Context) : MidiEngine, MidiEventDispatcher.ChannelControlListener {

    companion object {
        private const val TAG = "FluidSynthEngine"
        private const val POSITION_UPDATE_INTERVAL_MS = 100L
        private const val DEFAULT_GAIN = 0.5f  // FluidSynth default is 0.2, we use slightly higher

        // Audio rendering constants
        private const val SAMPLE_RATE = 48000
        private const val FRAMES_PER_BUFFER = 512  // ~10ms at 48kHz

        /**
         * Check if FluidSynth is available on this device.
         */
        fun isSupported(): Boolean {
            return try {
                FluidSynthNative.loadLibrary()
            } catch (e: Exception) {
                Log.w(TAG, "FluidSynth not supported", e)
                false
            }
        }
    }

    // State
    private var state: MidiEngine.State = MidiEngine.State.UNINITIALIZED
    private val stateLock = Any()

    // Operation lock to prevent concurrent operations
    private val operationLock = ReentrantLock()
    private val isReleased = AtomicBoolean(false)

    // FluidSynth handles (native pointers stored as Long)
    private var settingsHandle: Long = 0
    private var synthHandle: Long = 0
    private var audioDriverHandle: Long = 0
    private var playerHandle: Long = 0
    private var soundFontId: Int = -1

    // Playback state
    private var currentMidiPath: String? = null
    private var durationMs: Long = 0
    private var totalTicks: Long = 0
    private var currentPositionMs = AtomicLong(0)

    // Executor for position updates
    private var executor: ScheduledExecutorService? = null
    private var positionUpdateFuture: ScheduledFuture<*>? = null

    // Volume
    private var volume: Float = 1f

    // Listeners
    private var onStateChangeListener: ((MidiEngine.State) -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPositionChangedListener: ((Long, Long) -> Unit)? = null

    // Current SoundFont path
    private var currentSoundFontPath: String = ""

    // Temp file for content URI
    private var currentTempFile: File? = null

    // Manual audio rendering (fallback when Oboe driver fails, or when EQ is enabled)
    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null
    private val isRendering = AtomicBoolean(false)
    private var useManualRendering = false

    // EQ 10 bandes (nécessite rendu manuel pour accéder au PCM)
    private val eq = MidiEqualizerEngine(SAMPLE_RATE)

    // Tempo map pour conversion précise tick↔ms (au lieu du mapping linéaire)
    // Chaque entrée marque un changement de tempo avec le temps accumulé à ce tick
    private data class TempoMapEntry(
        val tick: Long,
        val timeUs: Double,            // temps accumulé en microsecondes jusqu'à ce tick
        val microsecondsPerBeat: Double // tempo à partir de ce point
    )
    private var tempoMap: List<TempoMapEntry> = emptyList()
    private var midiResolution: Long = 480 // PPQ, mis à jour au chargement

    // Visualization timeline - shadow dispatch of MIDI events for keyboard display
    // Uses ticks (not ms) to stay synchronized with FluidSynth's native player
    private class VisualizationEvent(val tick: Long, val midiBytes: ByteArray)
    private var visualizationTimeline: List<VisualizationEvent> = emptyList()
    @Volatile private var lastDispatchIndex: Int = 0

    // ==================== MidiEngine Interface ====================

    override fun initialize(soundFontPath: String): Boolean {
        if (isReleased.get()) return false

        if (soundFontPath.isBlank()) {
            notifyError("No SoundFont file specified")
            return false
        }

        val file = File(soundFontPath)
        if (!file.exists()) {
            notifyError("SoundFont file not found: $soundFontPath")
            return false
        }

        try {
            // Load native library
            if (!FluidSynthNative.loadLibrary()) {
                notifyError("Failed to load FluidSynth native library")
                return false
            }

            // Create settings
            settingsHandle = FluidSynthNative.newSettings()
            if (settingsHandle == 0L) {
                notifyError("Failed to create FluidSynth settings")
                return false
            }

            // Configure settings for Android
            FluidSynthNative.setSettingNum(settingsHandle, "synth.sample-rate", 48000.0)
            FluidSynthNative.setSettingInt(settingsHandle, "synth.polyphony", 128)
            FluidSynthNative.setSettingInt(settingsHandle, "audio.periods", 2)
            FluidSynthNative.setSettingInt(settingsHandle, "audio.period-size", 256)

            // Create synth
            synthHandle = FluidSynthNative.newSynth(settingsHandle)
            if (synthHandle == 0L) {
                notifyError("Failed to create FluidSynth synth")
                cleanup()
                return false
            }

            // Set initial gain
            FluidSynthNative.setGain(synthHandle, DEFAULT_GAIN * volume)

            // Enable reverb by default
            FluidSynthNative.setReverbOn(synthHandle, true)

            // Load SoundFont
            soundFontId = FluidSynthNative.sfLoad(synthHandle, soundFontPath, true)
            if (soundFontId < 0) {
                notifyError("Failed to load SoundFont: $soundFontPath")
                cleanup()
                return false
            }

            // Create audio driver (starts audio output)
            audioDriverHandle = FluidSynthNative.newAudioDriver(settingsHandle, synthHandle)
            if (audioDriverHandle == 0L) {
                Log.w(TAG, "Failed to create audio driver - will use manual rendering")
                useManualRendering = true
                initializeAudioTrack()
            } else {
                useManualRendering = false
            }

            currentSoundFontPath = soundFontPath

            // Register for channel control events (volume/mute)
            MidiEventDispatcher.addChannelControlListener(this)

            Log.i(TAG, "Initialized FluidSynth with SF2: $soundFontPath (version: ${FluidSynthNative.getVersion()}, manualRendering: $useManualRendering)")
            updateState(MidiEngine.State.INITIALIZED)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            notifyError("Initialization failed: ${e.message}")
            cleanup()
            return false
        }
    }

    override fun loadMidiFile(filePath: String): Boolean {
        if (isReleased.get()) return false

        if (state == MidiEngine.State.PLAYING || state == MidiEngine.State.PAUSED) {
            stop()
        }

        if (state != MidiEngine.State.INITIALIZED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.MIDI_LOADED) {
            Log.w(TAG, "Cannot load MIDI in state: $state")
            return false
        }

        try {
            // Prepare visualizer dispatcher for new file (clears all state including cache)
            MidiEventDispatcher.prepareForNewFile()
            cleanupTempFile()

            val resolvedPath = resolveMidiPath(filePath)
            if (resolvedPath == null || !File(resolvedPath).exists()) {
                notifyError("MIDI file not found: $filePath")
                return false
            }

            // Clean up existing player
            if (playerHandle != 0L) {
                FluidSynthNative.deletePlayer(playerHandle)
                playerHandle = 0
            }

            // Create new player
            playerHandle = FluidSynthNative.newPlayer(synthHandle)
            if (playerHandle == 0L) {
                notifyError("Failed to create MIDI player")
                return false
            }

            // Add MIDI file to player
            if (!FluidSynthNative.playerAdd(playerHandle, resolvedPath)) {
                notifyError("Failed to add MIDI file to player")
                FluidSynthNative.deletePlayer(playerHandle)
                playerHandle = 0
                return false
            }

            currentMidiPath = resolvedPath

            // Get accurate duration by parsing the MIDI file with android-midi-lib
            durationMs = computeMidiDuration(resolvedPath)

            // Get total ticks from FluidSynth player (may be 0 before playback starts)
            totalTicks = FluidSynthNative.playerGetTotalTicks(playerHandle).toLong()

            currentPositionMs.set(0)

            Log.i(TAG, "Loaded MIDI file: $resolvedPath (duration: ${durationMs}ms, ticks: $totalTicks)")
            updateState(MidiEngine.State.MIDI_LOADED)
            analyzeLoadedMidiFile(File(resolvedPath))
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MIDI", e)
            notifyError("Failed to load MIDI: ${e.message}")
            return false
        }
    }

    override fun start(): Boolean {
        if (isReleased.get()) return false

        if (state != MidiEngine.State.MIDI_LOADED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.PAUSED) {
            Log.w(TAG, "Cannot start in state: $state")
            return false
        }

        try {
            if (playerHandle == 0L) {
                notifyError("No MIDI file loaded")
                return false
            }

            // Start playback
            if (!FluidSynthNative.playerPlay(playerHandle)) {
                notifyError("Failed to start playback")
                return false
            }

            // Start audio rendering (for manual mode)
            if (useManualRendering) {
                startAudioRendering()
            }

            // Start position updates
            startPositionUpdates()

            Log.d(TAG, "Started playback (manualRendering: $useManualRendering)")
            updateState(MidiEngine.State.PLAYING)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Start failed", e)
            notifyError("Start failed: ${e.message}")
            return false
        }
    }

    override fun pause() {
        if (isReleased.get()) return
        if (state != MidiEngine.State.PLAYING) return

        try {
            FluidSynthNative.playerStop(playerHandle)
            stopPositionUpdates()

            // Stop audio rendering (for manual mode)
            if (useManualRendering) {
                stopAudioRendering()
            }

            // Turn off all notes
            for (channel in 0..15) {
                FluidSynthNative.allNotesOff(synthHandle, channel)
            }

            MidiEventDispatcher.dispatchAllNotesOff()

            Log.d(TAG, "Paused playback")
            updateState(MidiEngine.State.PAUSED)

        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
        }
    }

    override fun resume() {
        if (isReleased.get()) return
        if (state != MidiEngine.State.PAUSED) return

        try {
            // Seek to current position and play
            val currentTicks = msToTick(currentPositionMs.get()).toInt()
            FluidSynthNative.playerSeek(playerHandle, currentTicks)
            FluidSynthNative.playerPlay(playerHandle)

            // Start audio rendering (for manual mode)
            if (useManualRendering) {
                startAudioRendering()
            }

            startPositionUpdates()

            Log.d(TAG, "Resumed playback")
            updateState(MidiEngine.State.PLAYING)

        } catch (e: Exception) {
            Log.e(TAG, "Resume failed", e)
        }
    }

    override fun stop() {
        if (isReleased.get()) return

        try {
            stopPositionUpdates()

            // Stop audio rendering (for manual mode)
            if (useManualRendering) {
                stopAudioRendering()
            }

            if (playerHandle != 0L) {
                FluidSynthNative.playerStop(playerHandle)
            }

            // Stop all sounds
            for (channel in 0..15) {
                FluidSynthNative.allSoundOff(synthHandle, channel)
            }

            MidiEventDispatcher.dispatchAllNotesOff()

            currentPositionMs.set(0)
            lastDispatchIndex = 0

            Log.d(TAG, "Stopped playback")
            updateState(MidiEngine.State.STOPPED)

        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
    }

    override fun release() {
        if (isReleased.getAndSet(true)) return

        Log.d(TAG, "Releasing FluidSynthEngine")

        try {
            stopPositionUpdates()
            executor?.shutdownNow()
            executor = null

            // Unregister from channel control events
            MidiEventDispatcher.removeChannelControlListener(this)

            // Release AudioTrack (for manual mode)
            releaseAudioTrack()

            cleanup()
            cleanupTempFile()

            updateState(MidiEngine.State.UNINITIALIZED)

        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        }
    }

    private fun cleanup() {
        try {
            if (playerHandle != 0L) {
                FluidSynthNative.deletePlayer(playerHandle)
                playerHandle = 0
            }
            if (audioDriverHandle != 0L) {
                FluidSynthNative.deleteAudioDriver(audioDriverHandle)
                audioDriverHandle = 0
            }
            if (synthHandle != 0L) {
                FluidSynthNative.deleteSynth(synthHandle)
                synthHandle = 0
            }
            if (settingsHandle != 0L) {
                FluidSynthNative.deleteSettings(settingsHandle)
                settingsHandle = 0
            }
            soundFontId = -1
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (isReleased.get()) return
        if (playerHandle == 0L) return

        val clampedPos = positionMs.coerceIn(0, durationMs)
        val targetTicks = msToTick(clampedPos).toInt()

        try {
            val wasPlaying = state == MidiEngine.State.PLAYING

            // Stop all sounds before seeking
            for (channel in 0..15) {
                FluidSynthNative.allSoundOff(synthHandle, channel)
            }

            // Seek to position
            FluidSynthNative.playerSeek(playerHandle, targetTicks)
            currentPositionMs.set(clampedPos)

            MidiEventDispatcher.dispatchAllNotesOff()

            // Update visualization timeline index and re-dispatch program changes (using ticks for accuracy)
            lastDispatchIndex = findTimelineIndexByTick(targetTicks.toLong())
            dispatchProgramChangesUpTo(targetTicks.toLong())

            if (wasPlaying) {
                FluidSynthNative.playerPlay(playerHandle)
            }

            Log.d(TAG, "Seeked to ${clampedPos}ms (ticks: $targetTicks)")

        } catch (e: Exception) {
            Log.e(TAG, "Seek failed", e)
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        if (synthHandle != 0L) {
            FluidSynthNative.setGain(synthHandle, DEFAULT_GAIN * this.volume)
        }
    }

    override fun getCurrentPosition(): Long = currentPositionMs.get()

    override fun getDuration(): Long = durationMs

    override fun isPlaying(): Boolean = state == MidiEngine.State.PLAYING

    override fun getState(): MidiEngine.State = state

    override fun getAudioSessionId(): Int = 0  // FluidSynth manages its own audio

    override fun setOnStateChangeListener(listener: (MidiEngine.State) -> Unit) {
        onStateChangeListener = listener
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    override fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    override fun setOnPositionChangedListener(listener: (Long, Long) -> Unit) {
        onPositionChangedListener = listener
    }

    override fun reloadSoundFont(soundFontPath: String): Boolean {
        if (isReleased.get()) return false

        val wasPlaying = state == MidiEngine.State.PLAYING
        val position = currentPositionMs.get()
        val hadMidiLoaded = currentMidiPath != null

        stop()

        try {
            // Unload current SoundFont
            if (soundFontId >= 0 && synthHandle != 0L) {
                FluidSynthNative.sfUnload(synthHandle, soundFontId, true)
                soundFontId = -1
            }

            // Load new SoundFont
            soundFontId = FluidSynthNative.sfLoad(synthHandle, soundFontPath, true)
            if (soundFontId < 0) {
                notifyError("Failed to reload SoundFont: $soundFontPath")
                return false
            }

            currentSoundFontPath = soundFontPath
            Log.i(TAG, "Reloaded SoundFont: $soundFontPath")

            // Restore state
            if (hadMidiLoaded && currentMidiPath != null) {
                updateState(MidiEngine.State.MIDI_LOADED)
                if (wasPlaying) {
                    seekTo(position)
                    start()
                }
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Reload SoundFont failed", e)
            notifyError("Reload failed: ${e.message}")
            return false
        }
    }

    override fun setReverb(preset: Int) {
        if (synthHandle == 0L) return

        when (preset) {
            -1 -> FluidSynthNative.setReverbOn(synthHandle, false)
            0 -> {  // Large Hall
                FluidSynthNative.setReverbOn(synthHandle, true)
                FluidSynthNative.setReverb(synthHandle, 0.9, 0.2, 0.5, 0.9)
            }
            1 -> {  // Hall
                FluidSynthNative.setReverbOn(synthHandle, true)
                FluidSynthNative.setReverb(synthHandle, 0.7, 0.3, 0.5, 0.7)
            }
            2 -> {  // Chamber
                FluidSynthNative.setReverbOn(synthHandle, true)
                FluidSynthNative.setReverb(synthHandle, 0.5, 0.4, 0.5, 0.5)
            }
            3 -> {  // Room
                FluidSynthNative.setReverbOn(synthHandle, true)
                FluidSynthNative.setReverb(synthHandle, 0.3, 0.5, 0.5, 0.3)
            }
            else -> FluidSynthNative.setReverbOn(synthHandle, true)
        }
    }

    override fun setChorus(preset: Int) {
        if (synthHandle == 0L) return
        // FluidSynth chorus: only on/off toggle exposed via native API
        FluidSynthNative.setChorusOn(synthHandle, preset >= 0)
    }

    override fun forceDriverRestart() {
        if (synthHandle == 0L || settingsHandle == 0L) return

        try {
            // Delete and recreate audio driver
            if (audioDriverHandle != 0L) {
                FluidSynthNative.deleteAudioDriver(audioDriverHandle)
            }
            audioDriverHandle = FluidSynthNative.newAudioDriver(settingsHandle, synthHandle)
            Log.d(TAG, "Audio driver restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Driver restart failed", e)
        }
    }

    override fun getDriverStats(): String {
        return if (synthHandle != 0L) {
            val voices = FluidSynthNative.getActiveVoiceCount(synthHandle)
            val poly = FluidSynthNative.getPolyphony(synthHandle)
            val gain = FluidSynthNative.getGain(synthHandle)
            "FluidSynth: voices=$voices/$poly, gain=${"%.2f".format(gain)}"
        } else {
            "FluidSynth: not initialized"
        }
    }

    // ==================== Visualizer Sync ====================

    /**
     * Synchronizes the keyboard visualizer with the current playback position.
     * Scans the visualization timeline to rebuild active note state, same logic as Sf2Engine.
     * Called by MidiSynthesizerManager when the refresh button is pressed.
     */
    fun syncVisualizerToCurrentPosition() {
        val timeline = visualizationTimeline
        if (timeline.isEmpty()) return

        val currentTick = if (playerHandle != 0L)
            FluidSynthNative.playerGetCurrentTick(playerHandle).toLong() else 0L

        // Clear tracker's active notes (but keep analysis cache!)
        MidiEventDispatcher.clearTrackerActiveNotes()

        // Track note states: key = (channel * 128 + note), value = velocity
        val activeNotes = mutableMapOf<Int, Int>()

        // Scan through timeline up to current tick
        for (event in timeline) {
            if (event.tick > currentTick) break

            val status = event.midiBytes[0].toInt() and 0xFF
            val type = status and 0xF0
            val channel = status and 0x0F

            when (type) {
                0x90 -> { // Note On
                    val note = event.midiBytes[1].toInt() and 0x7F
                    val velocity = event.midiBytes[2].toInt() and 0x7F
                    val key = channel * 128 + note
                    if (velocity > 0) {
                        activeNotes[key] = velocity
                    } else {
                        activeNotes.remove(key)
                    }
                }
                0x80 -> { // Note Off
                    val note = event.midiBytes[1].toInt() and 0x7F
                    val key = channel * 128 + note
                    activeNotes.remove(key)
                }
                0xC0 -> { // Program Change
                    MidiEventDispatcher.processMidiBytes(event.midiBytes)
                }
            }
        }

        // Re-dispatch all currently active notes to the visualizer
        for ((key, velocity) in activeNotes) {
            val channel = key / 128
            val note = key % 128
            val midiBytes = byteArrayOf(
                (0x90 or channel).toByte(),
                note.toByte(),
                velocity.toByte()
            )
            MidiEventDispatcher.processMidiBytes(midiBytes)
        }

        // Also sync the dispatch index so subsequent updates continue correctly
        lastDispatchIndex = findTimelineIndexByTick(currentTick)
    }

    // ==================== Direct MIDI Methods (for Hybrid Mode) ====================

    /**
     * Start the audio renderer for direct MIDI mode (not file playback).
     * Call this after initialize() when using sendNoteOn/sendNoteOff directly.
     * For Oboe mode, audio starts automatically. For manual rendering mode, this starts the render thread.
     */
    fun startAudioRenderer(): Boolean {
        if (synthHandle == 0L) return false

        if (useManualRendering) {
            startAudioRendering()
        }
        // Oboe driver starts automatically in initialize()
        return true
    }

    /**
     * Stop the audio renderer for direct MIDI mode.
     */
    fun stopAudioRenderer() {
        if (useManualRendering) {
            stopAudioRendering()
        }
        // Stop all sounds
        sendAllSoundOff()
    }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        if (synthHandle != 0L) {
            FluidSynthNative.noteOn(synthHandle, channel, note, velocity)
        }
    }

    fun sendNoteOff(channel: Int, note: Int) {
        if (synthHandle != 0L) {
            FluidSynthNative.noteOff(synthHandle, channel, note)
        }
    }

    fun sendProgramChange(channel: Int, program: Int) {
        if (synthHandle != 0L) {
            FluidSynthNative.programChange(synthHandle, channel, program)
        }
    }

    fun sendControlChange(channel: Int, controller: Int, value: Int) {
        if (synthHandle != 0L) {
            FluidSynthNative.cc(synthHandle, channel, controller, value)
        }
    }

    fun sendPitchBend(channel: Int, value: Int) {
        if (synthHandle != 0L) {
            FluidSynthNative.pitchBend(synthHandle, channel, value)
        }
    }

    fun sendAllSoundOff() {
        if (synthHandle != 0L) {
            for (channel in 0..15) {
                FluidSynthNative.allSoundOff(synthHandle, channel)
            }
        }
    }

    fun sendAllNotesOff() {
        if (synthHandle != 0L) {
            for (channel in 0..15) {
                FluidSynthNative.allNotesOff(synthHandle, channel)
            }
        }
    }

    fun resetSynthesizer() {
        if (synthHandle != 0L) {
            FluidSynthNative.systemReset(synthHandle)
        }
    }

    fun isReadyForDirectMidi(): Boolean {
        return state != MidiEngine.State.UNINITIALIZED &&
               state != MidiEngine.State.ERROR &&
               synthHandle != 0L
    }

    fun getCurrentSoundFontPath(): String = currentSoundFontPath

    // ==================== Audio Device Routing ====================

    /**
     * Force audio output to speaker (for USB MIDI device scenarios).
     * Note: FluidSynth's Oboe driver handles this internally in most cases.
     */
    fun forceOutputToSpeaker(reason: String): Boolean {
        Log.d(TAG, "forceOutputToSpeaker[$reason]: FluidSynth uses Oboe which handles routing")
        // FluidSynth's Oboe audio driver typically handles device routing automatically
        // If needed, we could recreate the audio driver with specific settings
        return true
    }

    /**
     * Reset audio output to default.
     */
    fun resetPreferredOutput(reason: String): Boolean {
        Log.d(TAG, "resetPreferredOutput[$reason]")
        return true
    }

    // ==================== Private Helpers ====================

    /**
     * Analyze the loaded MIDI file to detect channels, instruments, and note ranges.
     * This populates the keyboard display in NowPlayingFragment.
     */
    private fun analyzeLoadedMidiFile(midiFile: File) {
        try {
            val tracker = MidiEventDispatcher.initializeTracker(context)
            tracker.analyzeFile(midiFile)
        } catch (e: Exception) {
            Log.e(TAG, "MIDI analysis failed", e)
        }
    }

    /**
     * Parse the MIDI file with android-midi-lib to compute an accurate duration in ms
     * and build a visualization timeline for keyboard display.
     * Walks all events, tracking tempo changes, same approach as MidiFilePlayer.convertToTimedEvents.
     */
    private fun computeMidiDuration(filePath: String): Long {
        return try {
            val midiFile = MidiFile(FileInputStream(File(filePath)))
            val resolution = midiFile.resolution.toLong()
            if (resolution <= 0) return 0L

            // Store resolution for tick↔ms conversions
            midiResolution = resolution

            // Collect all events from all tracks as (tick, event)
            val rawEvents = mutableListOf<Pair<Long, com.leff.midi.event.MidiEvent>>()
            for (track in midiFile.tracks) {
                for (event in track.events) {
                    rawEvents.add(event.tick to event)
                }
            }
            rawEvents.sortBy { it.first }

            // Use Double arithmetic to avoid integer truncation accumulation
            var microsecondsPerBeat = 500000.0  // Default 120 BPM
            val ticksPerBeat = resolution.toDouble()
            var lastTick = 0L
            var currentTimeUs = 0.0
            val timeline = mutableListOf<VisualizationEvent>()

            // Build tempo map: first entry is the default tempo at tick 0
            val tempoEntries = mutableListOf(
                TempoMapEntry(tick = 0, timeUs = 0.0, microsecondsPerBeat = microsecondsPerBeat)
            )

            for ((tick, event) in rawEvents) {
                val deltaTicks = tick - lastTick
                if (deltaTicks > 0) {
                    currentTimeUs += deltaTicks.toDouble() * microsecondsPerBeat / ticksPerBeat
                }
                lastTick = tick

                if (event is Tempo) {
                    microsecondsPerBeat = event.mpqn.toDouble()
                    tempoEntries.add(
                        TempoMapEntry(tick = tick, timeUs = currentTimeUs, microsecondsPerBeat = microsecondsPerBeat)
                    )
                }

                // Build visualization bytes for keyboard-relevant events
                val vizBytes = when (event) {
                    is NoteOn -> byteArrayOf(
                        (0x90 or event.channel).toByte(),
                        event.noteValue.toByte(),
                        event.velocity.toByte()
                    )
                    is NoteOff -> byteArrayOf(
                        (0x80 or event.channel).toByte(),
                        event.noteValue.toByte(),
                        event.velocity.toByte()
                    )
                    is ProgramChange -> byteArrayOf(
                        (0xC0 or event.channel).toByte(),
                        event.programNumber.toByte()
                    )
                    else -> null
                }
                if (vizBytes != null) {
                    timeline.add(VisualizationEvent(tick, vizBytes))
                }
            }

            tempoMap = tempoEntries
            visualizationTimeline = timeline
            lastDispatchIndex = 0

            val durationMs = (currentTimeUs / 1000.0).toLong()
            Log.d(TAG, "computeMidiDuration: ${durationMs}ms (resolution=$resolution, tempoChanges=${tempoEntries.size}, vizEvents=${timeline.size})")
            durationMs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute MIDI duration", e)
            tempoMap = emptyList()
            visualizationTimeline = emptyList()
            lastDispatchIndex = 0
            0L
        }
    }

    /**
     * Convert a MIDI tick position to milliseconds using the tempo map.
     * Uses binary search to find the applicable tempo segment,
     * then linearly interpolates within that segment.
     */
    private fun tickToMs(tick: Long): Long {
        val map = tempoMap
        if (map.isEmpty()) {
            // Fallback to linear if no tempo map
            return if (totalTicks > 0) (tick * durationMs) / totalTicks else 0L
        }

        // Binary search for the last tempo entry at or before this tick
        var low = 0
        var high = map.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (map[mid].tick <= tick) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        val entry = map[low]
        val deltaTicks = tick - entry.tick
        val deltaUs = deltaTicks.toDouble() * entry.microsecondsPerBeat / midiResolution.toDouble()
        return ((entry.timeUs + deltaUs) / 1000.0).toLong()
    }

    /**
     * Convert a millisecond position to MIDI ticks using the tempo map.
     * Uses binary search to find the applicable tempo segment,
     * then linearly interpolates within that segment.
     */
    private fun msToTick(ms: Long): Long {
        val map = tempoMap
        if (map.isEmpty()) {
            // Fallback to linear if no tempo map
            return if (durationMs > 0) (ms * totalTicks) / durationMs else 0L
        }

        val targetUs = ms * 1000.0

        // Binary search for the last tempo entry at or before this time
        var low = 0
        var high = map.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (map[mid].timeUs <= targetUs) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        val entry = map[low]
        val deltaUs = targetUs - entry.timeUs
        val deltaTicks = if (entry.microsecondsPerBeat > 0) {
            (deltaUs * midiResolution.toDouble() / entry.microsecondsPerBeat).toLong()
        } else {
            0L
        }
        return entry.tick + deltaTicks
    }

    /**
     * Dispatch all visualization events up to the given tick position to MidiEventDispatcher.
     * Uses ticks (not ms) to stay perfectly synchronized with FluidSynth's native player,
     * even when the MIDI file has tempo changes.
     */
    private fun dispatchVisualizationEvents(currentTick: Long) {
        val timeline = visualizationTimeline
        if (timeline.isEmpty()) return

        while (lastDispatchIndex < timeline.size) {
            val event = timeline[lastDispatchIndex]
            if (event.tick <= currentTick) {
                MidiEventDispatcher.processMidiBytes(event.midiBytes)
                lastDispatchIndex++
            } else {
                break
            }
        }
    }

    /**
     * Binary search for the first timeline event at or after the given tick position.
     */
    private fun findTimelineIndexByTick(targetTick: Long): Int {
        val timeline = visualizationTimeline
        if (timeline.isEmpty()) return 0

        var low = 0
        var high = timeline.size
        while (low < high) {
            val mid = (low + high) / 2
            if (timeline[mid].tick < targetTick) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    /**
     * After a seek, re-dispatch the latest ProgramChange per channel so instrument labels stay correct.
     */
    private fun dispatchProgramChangesUpTo(targetTick: Long) {
        val timeline = visualizationTimeline
        val latestProgramChange = arrayOfNulls<ByteArray>(16)

        for (event in timeline) {
            if (event.tick > targetTick) break
            val status = event.midiBytes[0].toInt() and 0xF0
            if (status == 0xC0) {
                val channel = event.midiBytes[0].toInt() and 0x0F
                latestProgramChange[channel] = event.midiBytes
            }
        }

        for (bytes in latestProgramChange) {
            if (bytes != null) {
                MidiEventDispatcher.processMidiBytes(bytes)
            }
        }
    }

    private fun resolveMidiPath(path: String): String? {
        return try {
            when {
                path.startsWith("content://") -> copyContentUriToCache(path)
                else -> path
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve MIDI path", e)
            null
        }
    }

    private fun copyContentUriToCache(uriString: String): String? {
        try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            val fileName = extractFileNameFromUri(uriString)
            val tempFile = File(context.cacheDir, "fluidsynth_midi_temp_$fileName")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            currentTempFile = tempFile
            return tempFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy content URI", e)
            return null
        }
    }

    private fun extractFileNameFromUri(uriString: String): String {
        return try {
            val lastSegment = uriString.substringAfterLast("/")
            val decoded = Uri.decode(lastSegment)
            if (decoded.contains("/")) decoded.substringAfterLast("/") else decoded
        } catch (e: Exception) {
            "temp_${System.currentTimeMillis()}.mid"
        }
    }

    private fun cleanupTempFile() {
        currentTempFile?.let { tempFile ->
            try {
                if (tempFile.exists()) tempFile.delete()
                currentTempFile = null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup temp file", e)
            }
        }
    }

    private fun startPositionUpdates() {
        if (executor == null || executor?.isShutdown == true) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }

        positionUpdateFuture = executor?.scheduleAtFixedRate({
            try {
                if (state == MidiEngine.State.PLAYING && playerHandle != 0L) {
                    // Re-query totalTicks if it was 0 at load time (some players need playback to start first)
                    if (totalTicks <= 0) {
                        val ticks = FluidSynthNative.playerGetTotalTicks(playerHandle).toLong()
                        if (ticks > 0) {
                            totalTicks = ticks
                            Log.d(TAG, "Deferred totalTicks update: $totalTicks")
                        }
                    }

                    val currentTick = FluidSynthNative.playerGetCurrentTick(playerHandle).toLong()
                    val posMs = tickToMs(currentTick)
                    currentPositionMs.set(posMs)

                    // Dispatch visualization events using raw ticks for accurate sync
                    dispatchVisualizationEvents(currentTick)

                    onPositionChangedListener?.invoke(posMs, durationMs)

                    // Check for completion
                    val status = FluidSynthNative.playerGetStatus(playerHandle)
                    if (status == FluidSynthNative.PLAYER_DONE) {
                        onPlaybackComplete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Position update error", e)
            }
        }, 0, POSITION_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun stopPositionUpdates() {
        positionUpdateFuture?.cancel(false)
        positionUpdateFuture = null
    }

    private fun onPlaybackComplete() {
        MidiEventDispatcher.dispatchAllNotesOff()

        executor?.schedule({
            if (state == MidiEngine.State.PLAYING) {
                stop()
                onCompletionListener?.invoke()
            }
        }, 200, TimeUnit.MILLISECONDS)
    }

    private fun updateState(newState: MidiEngine.State) {
        synchronized(stateLock) {
            if (state != newState) {
                Log.d(TAG, "State: $state -> $newState")
                state = newState
                onStateChangeListener?.invoke(newState)
            }
        }
    }

    private fun notifyError(message: String) {
        Log.e(TAG, message)
        onErrorListener?.invoke(message)
    }

    // ==================== Manual Audio Rendering ====================

    private fun initializeAudioTrack() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(FRAMES_PER_BUFFER * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            Log.d(TAG, "AudioTrack initialized for manual rendering (buffer: $bufferSize)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            audioTrack = null
        }
    }

    private fun startAudioRendering() {
        if (!useManualRendering || audioTrack == null || synthHandle == 0L) return
        if (isRendering.get()) return

        isRendering.set(true)
        audioTrack?.play()

        renderThread = Thread({
            Log.d(TAG, "Audio rendering thread started")
            val buffer = ShortArray(FRAMES_PER_BUFFER * 2)  // Stereo = 2 samples per frame

            while (isRendering.get() && !Thread.currentThread().isInterrupted) {
                try {
                    if (synthHandle != 0L) {
                        // Render audio from FluidSynth
                        val framesRendered = FluidSynthNative.writeStereoShort(
                            synthHandle, buffer, 0, FRAMES_PER_BUFFER
                        )

                        if (framesRendered > 0) {
                            eq.processInterleaved(buffer, framesRendered)
                            audioTrack?.write(buffer, 0, framesRendered * 2)
                        }
                    }
                } catch (e: Exception) {
                    if (isRendering.get()) {
                        Log.w(TAG, "Audio rendering error", e)
                    }
                    break
                }
            }

            Log.d(TAG, "Audio rendering thread stopped")
        }, "FluidSynth-AudioRender")

        renderThread?.priority = Thread.MAX_PRIORITY
        renderThread?.start()
    }

    private fun stopAudioRendering() {
        isRendering.set(false)
        renderThread?.interrupt()
        try {
            renderThread?.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        renderThread = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
    }

    private fun releaseAudioTrack() {
        stopAudioRendering()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    // ==================== EQ 10 bandes ====================

    /**
     * Active ou désactive l'EQ.
     * Si l'EQ est activé en mode Oboe, bascule automatiquement vers le rendu manuel
     * (Oboe ne donne pas accès au PCM pour post-traitement).
     */
    fun setEqEnabled(enabled: Boolean) {
        eq.enabled = enabled
        if (enabled && !useManualRendering && synthHandle != 0L) {
            switchToManualRendering()
        }
    }

    fun setEqBandLevel(band: Int, millibels: Int) {
        eq.setBandLevel(band, millibels)
    }

    fun getEqBandLevel(band: Int): Int = eq.getBandLevel(band)

    /**
     * Bascule de Oboe vers le rendu manuel (AudioTrack + thread Kotlin).
     * Requis pour pouvoir appliquer l'EQ sur le PCM rendu par FluidSynth.
     */
    private fun switchToManualRendering() {
        val wasPlaying = state == MidiEngine.State.PLAYING

        if (audioDriverHandle != 0L) {
            FluidSynthNative.deleteAudioDriver(audioDriverHandle)
            audioDriverHandle = 0
        }

        useManualRendering = true
        initializeAudioTrack()

        if (wasPlaying) {
            startAudioRendering()
        }

        Log.d(TAG, "Switched to manual rendering for EQ support")
    }

    // ==================== ChannelControlListener Implementation ====================

    override fun onChannelMuteChanged(channel: Int, isMuted: Boolean) {
        if (isMuted && synthHandle != 0L) {
            // Send All Notes Off (CC#123) to mute the channel
            FluidSynthNative.cc(synthHandle, channel, 123, 0)
        }
    }

    override fun onChannelVolumeChanged(channel: Int, volume: Float) {
        if (synthHandle != 0L) {
            val midiVolume = (volume * 127).toInt().coerceIn(0, 127)
            // Send Volume (CC#7) to adjust channel volume
            FluidSynthNative.cc(synthHandle, channel, 7, midiVolume)
        }
    }
}
