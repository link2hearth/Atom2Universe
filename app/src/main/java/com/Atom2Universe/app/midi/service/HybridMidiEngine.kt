package com.Atom2Universe.app.midi.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.Atom2Universe.app.midi.sf2.MidiEventType
import com.Atom2Universe.app.midi.sf2.ScheduledMidiEvent
import com.Atom2Universe.app.midi.sf2.Sf2Engine
import com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher
import com.leff.midi.MidiFile
import com.leff.midi.event.*
import com.leff.midi.event.meta.Tempo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Hybrid MIDI Engine - Routes MIDI events to SF2 or Sonivox based on program selection.
 *
 * Architecture:
 * - Both Sf2Engine (AudioTrack) and SonivoxEngine (OpenSL ES) run simultaneously
 * - MIDI events are routed based on sf2Programs set (which programs use SF2)
 * - Program Change and Control Change are sent to BOTH engines for state coherence
 * - Note On/Off are routed to the engine handling that channel's current program
 * - Audio streams are mixed automatically by Android AudioFlinger
 *
 * Channel 9 (drums) has special handling via useSf2ForDrums flag.
 */
class HybridMidiEngine(
    private val context: Context,
    private val sf2Engine: Sf2Engine,
    private val sonivoxEngine: SonivoxEngine
) : MidiEngine {

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 100L
        private const val PERCUSSION_CHANNEL = 9
        // BUG FIX 3.17: Limite max d'evenements MIDI pour eviter les fichiers corrompus/infinis
        private const val MAX_MIDI_EVENTS = 500_000
    }

    // State
    private var state: MidiEngine.State = MidiEngine.State.UNINITIALIZED
    private val stateLock = Any()

    // Configuration
    private var sf2Programs: Set<Int> = emptySet()  // Programs (0-127) that use SF2
    private var useSf2ForDrums: Boolean = false     // Whether channel 9 uses SF2

    // Channel state
    private val channelProgram = IntArray(16) { 0 }  // Current program for each channel
    private val channelRouting = Array(16) { SynthTarget.SONIVOX }  // Current routing

    // MIDI playback
    private var midiFile: MidiFile? = null
    // Lock for synchronizing access to midiTimeline and currentEventIndex
    private val timelineLock = Any()
    @Volatile
    private var midiTimeline: List<ScheduledMidiEvent> = emptyList()
    private var durationMs: Long = 0
    private var currentPositionMs = AtomicLong(0)
    private var playbackStartTimeNs: Long = 0
    private var playbackStartPositionMs: Long = 0
    // Thread-safe event index - accessed from playback thread and modified in seekTo
    private var currentEventIndex = AtomicInteger(0)

    // Playback executor
    private var playbackExecutor: ScheduledExecutorService? = null
    private var playbackFuture: ScheduledFuture<*>? = null
    private var positionUpdateFuture: ScheduledFuture<*>? = null

    // Temp file for content URI
    private var currentTempFile: File? = null

    // Listeners
    private var onStateChangeListener: ((MidiEngine.State) -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPositionChangedListener: ((Long, Long) -> Unit)? = null

    // Volume
    private var volume: Float = 1f

    /**
     * Target synthesizer for routing
     */
    enum class SynthTarget {
        SF2,
        SONIVOX
    }

    /**
     * Configures which programs use SF2 synthesis.
     * Can be called at any time - will update routing for all channels immediately.
     * Notes currently playing will continue on their current engine until note-off.
     */
    fun configureSf2Programs(programs: Set<Int>, useSf2ForDrums: Boolean) {
        this.sf2Programs = programs.filter { it in 0..127 }.toSet()
        this.useSf2ForDrums = useSf2ForDrums

        // Update routing for all channels based on new configuration
        for (channel in 0 until 16) {
            updateChannelRouting(channel, channelProgram[channel])
        }
    }

    /**
     * Updates the routing for a channel based on its current program.
     */
    private fun updateChannelRouting(channel: Int, program: Int) {
        val target = when {
            channel == PERCUSSION_CHANNEL -> if (useSf2ForDrums) SynthTarget.SF2 else SynthTarget.SONIVOX
            sf2Programs.contains(program) -> SynthTarget.SF2
            else -> SynthTarget.SONIVOX
        }
        channelRouting[channel] = target
    }

    // ==================== MidiEngine Interface ====================

    override fun initialize(soundFontPath: String): Boolean {
        try {
            // Initialize Sonivox (always needed for fallback)
            val sonivoxInit = sonivoxEngine.initialize("")
            if (!sonivoxInit) {
                notifyError("Sonivox initialization failed")
                return false
            }

            // Initialize SF2 engine with SoundFont
            if (soundFontPath.isNotBlank()) {
                sf2Engine.initialize(soundFontPath)
            }

            // Initialize channel routing
            for (channel in 0 until 16) {
                channelProgram[channel] = 0
                channelRouting[channel] = if (channel == PERCUSSION_CHANNEL && useSf2ForDrums) {
                    SynthTarget.SF2
                } else if (sf2Programs.contains(0)) {
                    SynthTarget.SF2
                } else {
                    SynthTarget.SONIVOX
                }
            }

            // Register for mute/volume control events
            MidiEventDispatcher.addChannelControlListener(channelControlListener)

            updateState(MidiEngine.State.INITIALIZED)
            return true

        } catch (e: Exception) {
            notifyError("Initialization failed: ${e.message}")
            updateState(MidiEngine.State.ERROR)
            return false
        }
    }

    override fun loadMidiFile(filePath: String): Boolean {
        if (state != MidiEngine.State.INITIALIZED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.MIDI_LOADED) {
            return false
        }

        try {
            // Clean up previous temp file
            cleanupTempFile()

            // Resolve file path (handle content URIs)
            val file = resolveMidiFile(filePath)
            if (file == null || !file.exists()) {
                notifyError("MIDI file not found")
                return false
            }

            // Prepare visualizer dispatcher for new file
            MidiEventDispatcher.prepareForNewFile()

            // Parse MIDI file
            midiFile = MidiFile(file)

            // Build timeline
            buildMidiTimeline()

            // IMPORTANT: Ensure SF2 engine is ready for the new file
            // This fixes issues where SF2 state is invalid after practice mode
            // or when switching between tracks with different instrument requirements
            ensureSf2EngineReady()

            // Analyze for visualization
            analyzeLoadedMidiFile(file)

            currentPositionMs.set(0)
            currentEventIndex.set(0)
            updateState(MidiEngine.State.MIDI_LOADED)

            return true

        } catch (e: Exception) {
            notifyError("Failed to load MIDI: ${e.message}")
            return false
        }
    }

    // Lock for SF2 engine state changes (prevents race condition during reload)
    private val sf2StateLock = Any()

    /**
     * Ensures the SF2 engine is properly initialized for the new MIDI file.
     * This handles cases where the SF2 cache was invalidated (e.g., after practice mode)
     * or when streaming mode needs to load different instruments.
     *
     * Thread-safe: synchronized to prevent race condition between isReadyForDirectMidi()
     * check and reloadSoundFont() call.
     */
    private fun ensureSf2EngineReady() {
        synchronized(sf2StateLock) {
            try {
                // Check if SF2 engine is still valid
                if (!sf2Engine.isReadyForDirectMidi()) {
                    // SF2 engine lost its state - force it to reload from cache
                    val soundFontPath = sf2Engine.getCurrentSoundFontPath()
                    if (soundFontPath.isNotBlank()) {
                        sf2Engine.reloadSoundFont(soundFontPath)
                    }
                }
            } catch (_: Exception) {
                // Silently handle - Sonivox will still work as fallback
            }
        }
    }

    override fun start(): Boolean {
        if (state != MidiEngine.State.MIDI_LOADED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.PAUSED) {
            return false
        }

        try {
            // Ensure SF2 engine is ready (might have been invalidated after practice mode)
            // Use synchronized block to prevent race condition
            synchronized(sf2StateLock) {
                if (!sf2Engine.isReadyForDirectMidi()) {
                    val soundFontPath = sf2Engine.getCurrentSoundFontPath()
                    if (soundFontPath.isNotBlank()) {
                        sf2Engine.reloadSoundFont(soundFontPath)
                    }
                }
            }

            // Start both engines' audio systems
            val sf2Started = ensureSf2AudioStarted()

            val sonivoxAlreadyReady = sonivoxEngine.isReadyForDirectMidi()

            val sonivoxStarted = if (!sonivoxAlreadyReady) sonivoxEngine.startDriver() else true

            val sonivoxReady = sonivoxAlreadyReady || sonivoxStarted

            if (!sonivoxReady) {
                notifyError("Failed to start Sonivox")
                return false
            }

            if (!sf2Started && sf2Programs.isNotEmpty()) {
                // SF2 failed to start; fallback to Sonivox routing for this session
                for (channel in 0 until 16) {
                    channelRouting[channel] = SynthTarget.SONIVOX
                }
            }

            // Start playback executor
            playbackExecutor = Executors.newScheduledThreadPool(2)

            // Reset position if starting from stopped
            if (state == MidiEngine.State.STOPPED || state == MidiEngine.State.MIDI_LOADED) {
                currentPositionMs.set(0)
                currentEventIndex.set(0)
                resetChannelState()
            }

            // Start MIDI event scheduling
            startMidiPlayback()

            // Start position updates
            startPositionUpdates()

            updateState(MidiEngine.State.PLAYING)
            return true

        } catch (e: Exception) {
            // Nettoyer l'executor en cas d'erreur pour éviter fuite de ressources
            try {
                playbackExecutor?.shutdownNow()
                playbackExecutor = null
            } catch (_: Exception) {
            }
            notifyError("Playback failed: ${e.message}")
            return false
        }
    }

    private fun ensureSf2AudioStarted(): Boolean {
        try {
            if (!sf2Engine.isReadyForDirectMidi()) {
                val soundFontPath = sf2Engine.getCurrentSoundFontPath()
                if (soundFontPath.isNotBlank()) {
                    sf2Engine.reloadSoundFont(soundFontPath)
                }
            }

            if (sf2Engine.isReadyForDirectMidi()) {
                if (sf2Engine.startAudioRenderer()) {
                    return true
                }

                val soundFontPath = sf2Engine.getCurrentSoundFontPath()
                if (soundFontPath.isNotBlank() && sf2Engine.reloadSoundFont(soundFontPath)) {
                    return sf2Engine.startAudioRenderer()
                }
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    override fun pause() {
        if (state != MidiEngine.State.PLAYING) return

        stopMidiPlayback()

        // Send all notes off to both engines
        sf2Engine.sendAllNotesOff()
        sonivoxEngine.sendAllSoundOffDirect()

        // Pause audio systems
        sf2Engine.pauseAudioRenderer()

        // Clear keyboard visualization
        MidiEventDispatcher.dispatchAllNotesOff()

        updateState(MidiEngine.State.PAUSED)
    }

    override fun resume() {
        if (state != MidiEngine.State.PAUSED) return

        // Resume audio systems
        sf2Engine.resumeAudioRenderer()

        startMidiPlayback()
        startPositionUpdates()
        updateState(MidiEngine.State.PLAYING)
    }

    override fun stop() {
        stopForTransition(stopAudioEngines = true)
    }

    /**
     * Stops playback, optionally keeping audio engines running for track transitions.
     * When transitioning between tracks, we should NOT stop the underlying audio
     * engines (Oboe streams) to avoid race conditions and SIGBUS crashes.
     *
     * @param stopAudioEngines If true, stops Sf2Engine's AudioRenderer and Sonivox driver.
     *                         If false, only stops MIDI playback but keeps audio ready.
     */
    fun stopForTransition(stopAudioEngines: Boolean = true) {
        stopMidiPlayback()
        stopPositionUpdates()

        // Send all notes off to both engines
        sf2Engine.sendAllNotesOff()
        sonivoxEngine.sendAllSoundOffDirect()

        if (stopAudioEngines) {
            // Full stop - stop audio systems
            sf2Engine.stopAudioRenderer()
            sonivoxEngine.stopDriver()
        }
        // If !stopAudioEngines, keep audio streams running for next track

        // Clear keyboard visualization
        MidiEventDispatcher.dispatchAllNotesOff()

        playbackExecutor?.let { executor ->
            executor.shutdownNow()
            try {
                // Attendre que les tâches terminent pour éviter les race conditions
                executor.awaitTermination(1, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        playbackExecutor = null

        currentPositionMs.set(0)
        currentEventIndex.set(0)
        resetChannelState()

        updateState(MidiEngine.State.STOPPED)
    }

    override fun release() {
        // IMPORTANT: Garantir sendAllNotesOff avant shutdown pour éviter les notes bloquées
        try {
            sf2Engine.sendAllNotesOff()
            sonivoxEngine.sendAllSoundOffDirect()
        } catch (_: Exception) {
            // Ignorer les erreurs lors du cleanup
        }

        stop()

        // Unregister from channel control events
        MidiEventDispatcher.removeChannelControlListener(channelControlListener)

        // BUG FIX 1.15: Reset le synthesizer SF2 pour liberer les voix actives
        // qui pourraient detenir des references aux samples MappedByteBuffer.
        // Note: On ne release pas sf2Engine/sonivoxEngine car ils sont owned by MidiSynthesizerManager,
        // mais on s'assure que leurs ressources internes (voix, buffers) sont liberees.
        try {
            sf2Engine.resetSynthesizer()
        } catch (_: Exception) {
            // Ignorer les erreurs lors du cleanup
        }

        midiFile = null
        synchronized(timelineLock) {
            midiTimeline = emptyList()
        }
        cleanupTempFile()

        // Nettoyer les callbacks pour éviter les fuites mémoire
        onStateChangeListener = null
        onCompletionListener = null
        onErrorListener = null
        onPositionChangedListener = null

        updateState(MidiEngine.State.UNINITIALIZED)
    }

    override fun seekTo(positionMs: Long) {
        val clampedPos = positionMs.coerceIn(0, durationMs)

        val wasPlaying = state == MidiEngine.State.PLAYING

        if (wasPlaying) {
            stopMidiPlayback()
        }

        // Stop all current sounds on both engines
        sf2Engine.sendAllSoundOff()
        sonivoxEngine.sendAllSoundOffDirect()

        // Reset both synthesizers to clear all internal state
        // This is crucial to prevent lingering notes/effects
        sf2Engine.resetSynthesizer()
        sonivoxEngine.resetAllControllers()

        // Flush the SF2 audio buffer to immediately cut off any pending audio
        // This prevents ghost notes that would continue playing from the buffer
        sf2Engine.flushAudioBuffer()

        // Clear keyboard visualization
        MidiEventDispatcher.dispatchAllNotesOff()

        // Check if SF2 needs to load instruments for this position
        if (sf2Engine.hasActivePreloader()) {
            val isReady = sf2Engine.prepareForSeek(clampedPos) { _ ->
                // Callback when SF2 is ready - complete the seek
                completeSeek(clampedPos, wasPlaying)
            }

            if (isReady) {
                // SF2 already has all instruments, complete seek immediately
                completeSeek(clampedPos, wasPlaying)
            }
            // else: wait for callback, playback stays paused
        } else {
            // No preloader active, complete seek immediately
            completeSeek(clampedPos, wasPlaying)
        }
    }

    /**
     * Complete the seek operation after SF2 is ready.
     * Called either immediately or from prepareForSeek callback.
     */
    private fun completeSeek(clampedPos: Long, wasPlaying: Boolean) {
        // Synchronize access to midiTimeline to prevent race condition
        synchronized(timelineLock) {
            // Find the event index for this position
            val timeline = midiTimeline
            var newIndex = timeline.indexOfFirst { it.timeMs >= clampedPos }
            if (newIndex < 0) newIndex = timeline.size
            currentEventIndex.set(newIndex)

            // Replay program changes and control changes up to this point
            replayStateEvents(clampedPos)
        }

        currentPositionMs.set(clampedPos)

        // Restart playback from new position if was playing
        if (wasPlaying) {
            startMidiPlayback()
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        sf2Engine.setVolume(this.volume)
        sonivoxEngine.setVolume(this.volume)
    }

    override fun setReverb(preset: Int) {
        sf2Engine.setReverb(preset)
        sonivoxEngine.setReverb(preset)
    }

    override fun setChorus(preset: Int) {
        sf2Engine.setChorus(preset)
        sonivoxEngine.setChorus(preset)
    }

    override fun getCurrentPosition(): Long = currentPositionMs.get()

    override fun getDuration(): Long = durationMs

    override fun isPlaying(): Boolean = state == MidiEngine.State.PLAYING

    override fun getState(): MidiEngine.State = state

    override fun getAudioSessionId(): Int = sf2Engine.getAudioSessionId()

    /**
     * Synchronizes the visualizer (keyboards) with the current playback position.
     * This scans the MIDI timeline to determine which notes should be currently active
     * and sends them to the MidiEventDispatcher.
     *
     * Use this for refresh functionality to restore the correct visual state.
     */
    fun syncVisualizerToCurrentPosition() {
        val position = currentPositionMs.get()
        syncVisualizerToPosition(position)
    }

    /**
     * Synchronizes the visualizer (keyboards) with a specific position.
     * Calculates which notes should be active at that position based on the MIDI timeline.
     */
    fun syncVisualizerToPosition(positionMs: Long) {
        if (midiTimeline.isEmpty()) return

        // First, clear the tracker's active notes (but keep analysis cache!)
        MidiEventDispatcher.clearTrackerActiveNotes()

        // Track note states: key = (channel * 128 + note), value = velocity (0 = off)
        val activeNotes = mutableMapOf<Int, Int>()

        // Scan through timeline up to current position
        for (event in midiTimeline) {
            if (event.timeMs > positionMs) break

            when (event.type) {
                MidiEventType.NOTE_ON -> {
                    val key = event.channel * 128 + event.data1
                    if (event.data2 > 0) {
                        activeNotes[key] = event.data2
                    } else {
                        // Note On with velocity 0 = Note Off
                        activeNotes.remove(key)
                    }
                }
                MidiEventType.NOTE_OFF -> {
                    val key = event.channel * 128 + event.data1
                    activeNotes.remove(key)
                }
                MidiEventType.PROGRAM_CHANGE -> {
                    // Also replay program changes for correct instrument display
                    val midiBytes = byteArrayOf(
                        (0xC0 or event.channel).toByte(),
                        event.data1.toByte()
                    )
                    MidiEventDispatcher.processMidiBytes(midiBytes)
                }
                else -> {}
            }
        }

        // Now send all active notes to the visualizer
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
    }

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
        // Stop playback
        stop()

        // BUG FIX 1.15: S'assurer que l'ancien MappedByteBuffer est libere avant de recharger.
        // sf2Engine.reloadSoundFont() appelle close() sur l'ancien Sf2File en interne,
        // mais nous ajoutons un reset du synthesizer pour liberer toutes les voix actives
        // qui pourraient detenir des references aux samples.
        sf2Engine.resetSynthesizer()

        // Reload SF2 engine (ferme l'ancien Sf2File et son MappedByteBuffer en interne)
        val success = sf2Engine.reloadSoundFont(soundFontPath)

        if (success && midiTimeline.isNotEmpty()) {
            updateState(MidiEngine.State.MIDI_LOADED)
        }

        return success
    }

    override fun forceDriverRestart() {
        sf2Engine.forceDriverRestart()
        sonivoxEngine.forceDriverRestart()
    }

    override fun getDriverStats(): String {
        val sf2Stats = sf2Engine.getDriverStats()
        val sonivoxStats = sonivoxEngine.getDriverStats()
        val sf2Count = sf2Programs.size
        return "Hybrid: SF2($sf2Count progs) | $sf2Stats | Sonivox: $sonivoxStats"
    }

    // ==================== MIDI Timeline Building ====================

    private fun buildMidiTimeline() {
        val midi = midiFile ?: return
        val events = mutableListOf<ScheduledMidiEvent>()

        var microsecondsPerBeat = 500000L // Default 120 BPM
        val ticksPerBeat = midi.resolution.toLong()

        // Collect all events from all tracks with their tick times
        data class TickEvent(val tick: Long, val event: MidiEvent)
        val allEvents = mutableListOf<TickEvent>()

        for (track in midi.tracks) {
            var absoluteTick = 0L
            for (event in track.events) {
                absoluteTick += event.delta
                allEvents.add(TickEvent(absoluteTick, event))
                // BUG FIX 3.17: Limite max d'evenements pour eviter les fichiers corrompus
                if (allEvents.size >= MAX_MIDI_EVENTS) break
            }
            if (allEvents.size >= MAX_MIDI_EVENTS) break
        }

        // Sort by tick time
        allEvents.sortBy { it.tick }

        // Convert to timeline with proper tempo handling
        var currentTick = 0L
        var currentTimeUs = 0L

        for (tickEvent in allEvents) {
            val deltaTicks = tickEvent.tick - currentTick
            // BUG FIX 3.16: Valider que deltaTicks >= 0 pour éviter les timestamps négatifs
            if (deltaTicks >= 0 && ticksPerBeat > 0) {
                val deltaTimeUs = (deltaTicks * microsecondsPerBeat) / ticksPerBeat
                currentTimeUs += deltaTimeUs
            }
            currentTick = tickEvent.tick

            // Valider que timeMs >= 0
            val timeMs = (currentTimeUs / 1000).coerceAtLeast(0)

            when (val event = tickEvent.event) {
                is Tempo -> {
                    // BUG FIX 3.26: Valider les valeurs de tempo pour éviter les valeurs aberrantes
                    // Tempo valide: 20 BPM (3_000_000 µs/beat) à 300 BPM (200_000 µs/beat)
                    val newTempo = event.mpqn.toLong()
                    if (newTempo in 200_000L..3_000_000L) {
                        microsecondsPerBeat = newTempo
                    }
                    // Sinon, garder le tempo précédent
                }
                is NoteOn -> {
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = MidiEventType.NOTE_ON,
                        channel = event.channel,
                        data1 = event.noteValue,
                        data2 = event.velocity
                    ))
                }
                is NoteOff -> {
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = MidiEventType.NOTE_OFF,
                        channel = event.channel,
                        data1 = event.noteValue,
                        data2 = event.velocity
                    ))
                }
                is ProgramChange -> {
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = MidiEventType.PROGRAM_CHANGE,
                        channel = event.channel,
                        data1 = event.programNumber,
                        data2 = 0
                    ))
                }
                is Controller -> {
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = MidiEventType.CONTROL_CHANGE,
                        channel = event.channel,
                        data1 = event.controllerType,
                        data2 = event.value
                    ))
                }
                is PitchBend -> {
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = MidiEventType.PITCH_BEND,
                        channel = event.channel,
                        data1 = event.bendAmount and 0x7F,
                        data2 = (event.bendAmount shr 7) and 0x7F
                    ))
                }
            }
        }

        // Synchronize timeline replacement to prevent race condition with processMidiEvents
        synchronized(timelineLock) {
            midiTimeline = events.sortedBy { it.timeMs }
            durationMs = midiTimeline.lastOrNull()?.timeMs ?: 0
        }
    }

    // ==================== MIDI Playback ====================

    private fun startMidiPlayback() {
        playbackStartTimeNs = System.nanoTime()
        playbackStartPositionMs = currentPositionMs.get()

        playbackFuture = playbackExecutor?.scheduleWithFixedDelay(
            { processMidiEvents() },
            0,
            5,  // Process every 5ms for tight timing
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopMidiPlayback() {
        playbackFuture?.cancel(false)
        playbackFuture = null
    }

    private fun processMidiEvents() {
        if (state != MidiEngine.State.PLAYING) return

        // Calculate current position
        val elapsedNs = System.nanoTime() - playbackStartTimeNs
        val elapsedMs = elapsedNs / 1_000_000
        val position = playbackStartPositionMs + elapsedMs
        currentPositionMs.set(position)

        // Process events up to current position (synchronized for thread-safety)
        // Synchronize access to midiTimeline to prevent race condition during replacement
        synchronized(timelineLock) {
            val timeline = midiTimeline
            val timelineSize = timeline.size
            while (true) {
                val index = currentEventIndex.get()
                if (index >= timelineSize) break

                val event = timeline[index]
                if (event.timeMs > position) break

                dispatchMidiEvent(event)
                // Use compareAndSet to safely increment (handles concurrent seekTo)
                currentEventIndex.compareAndSet(index, index + 1)
            }

            // Check for completion
            if (currentEventIndex.get() >= timelineSize && position >= durationMs) {
                onPlaybackComplete()
            }
        }
    }

    private fun dispatchMidiEvent(event: ScheduledMidiEvent) {
        val channel = event.channel

        // Check if channel is muted
        val isMuted = MidiEventDispatcher.isChannelMuted(channel)

        when (event.type) {
            MidiEventType.PROGRAM_CHANGE -> {
                // Update channel program and routing
                channelProgram[channel] = event.data1
                updateChannelRouting(channel, event.data1)

                // Send to BOTH engines for state coherence
                sf2Engine.sendProgramChange(channel, event.data1)
                sonivoxEngine.sendProgramChange(channel, event.data1)

                // Apply instrument boost
                MidiAudioMixer.applyInstrumentBoost(channel, event.data1)

                // Dispatch for visualization
                dispatchMidiToVisualizer(event)
            }

            MidiEventType.NOTE_ON -> {
                if (!isMuted) {
                    // Apply velocity normalization
                    val normalizedVelocity = MidiAudioMixer.calculateAdjustedVelocity(
                        channel, event.data2, applyChannelVolume = false
                    )

                    // Route to the appropriate engine based on channel's program
                    val target = channelRouting[channel]

                    when (target) {
                        SynthTarget.SF2 -> sf2Engine.sendNoteOn(channel, event.data1, normalizedVelocity)
                        SynthTarget.SONIVOX -> sonivoxEngine.sendNoteOn(channel, event.data1, normalizedVelocity)
                    }
                }
                // Always dispatch for visualization
                dispatchMidiToVisualizer(event)
            }

            MidiEventType.NOTE_OFF -> {
                if (!isMuted) {
                    // IMPORTANT: Send Note Off to BOTH engines!
                    // If routing changed between Note On and Note Off (due to Program Change),
                    // the note would be stuck on the old engine. Sending to both ensures
                    // the note is properly released regardless of routing changes.
                    sf2Engine.sendNoteOff(channel, event.data1)
                    sonivoxEngine.sendNoteOff(channel, event.data1)
                }
                dispatchMidiToVisualizer(event)
            }

            MidiEventType.CONTROL_CHANGE -> {
                // Send to BOTH engines for state coherence
                sf2Engine.sendControlChange(channel, event.data1, event.data2)
                sonivoxEngine.sendControlChange(channel, event.data1, event.data2)
            }

            MidiEventType.PITCH_BEND -> {
                val value = event.data1 or (event.data2 shl 7)
                // Send to BOTH engines for state coherence
                sf2Engine.sendPitchBend(channel, value)
                sonivoxEngine.sendPitchBend(channel, value)
            }
        }
    }

    private fun dispatchMidiToVisualizer(event: ScheduledMidiEvent) {
        val midiBytes = when (event.type) {
            MidiEventType.NOTE_ON -> byteArrayOf(
                (0x90 or event.channel).toByte(),
                event.data1.toByte(),
                event.data2.toByte()
            )
            MidiEventType.NOTE_OFF -> byteArrayOf(
                (0x80 or event.channel).toByte(),
                event.data1.toByte(),
                event.data2.toByte()
            )
            MidiEventType.PROGRAM_CHANGE -> byteArrayOf(
                (0xC0 or event.channel).toByte(),
                event.data1.toByte()
            )
            else -> null
        }

        midiBytes?.let { MidiEventDispatcher.processMidiBytes(it) }
    }

    private fun replayStateEvents(upToMs: Long) {
        // Instead of replaying ALL events, calculate the FINAL state at the target position
        // This avoids sending hundreds of intermediate events which can cause issues

        // Track final state for each channel
        val finalProgram = IntArray(16) { 0 }
        val finalPitchBend = IntArray(16) { 8192 }  // Center = 8192
        val finalVolume = IntArray(16) { 100 }      // CC7 default
        val finalExpression = IntArray(16) { 127 }  // CC11 default
        val finalModulation = IntArray(16) { 0 }    // CC1 default
        val finalPan = IntArray(16) { 64 }          // CC10 center
        val finalSustain = BooleanArray(16) { false } // CC64

        // Scan through events to find final state at target position
        for (event in midiTimeline) {
            if (event.timeMs > upToMs) break

            when (event.type) {
                MidiEventType.PROGRAM_CHANGE -> {
                    finalProgram[event.channel] = event.data1
                }
                MidiEventType.CONTROL_CHANGE -> {
                    when (event.data1) {
                        1 -> finalModulation[event.channel] = event.data2   // Modulation
                        7 -> finalVolume[event.channel] = event.data2       // Volume
                        10 -> finalPan[event.channel] = event.data2         // Pan
                        11 -> finalExpression[event.channel] = event.data2  // Expression
                        64 -> finalSustain[event.channel] = event.data2 >= 64 // Sustain
                    }
                }
                MidiEventType.PITCH_BEND -> {
                    finalPitchBend[event.channel] = event.data1 or (event.data2 shl 7)
                }
                else -> {}
            }
        }

        // Now apply the final state to both engines
        for (channel in 0 until 16) {
            // Update internal routing
            channelProgram[channel] = finalProgram[channel]
            updateChannelRouting(channel, finalProgram[channel])

            // Send program change
            sf2Engine.sendProgramChange(channel, finalProgram[channel])
            sonivoxEngine.sendProgramChange(channel, finalProgram[channel])

            // Send control changes
            sf2Engine.sendControlChange(channel, 1, finalModulation[channel])
            sonivoxEngine.sendControlChange(channel, 1, finalModulation[channel])

            sf2Engine.sendControlChange(channel, 7, finalVolume[channel])
            sonivoxEngine.sendControlChange(channel, 7, finalVolume[channel])

            sf2Engine.sendControlChange(channel, 10, finalPan[channel])
            sonivoxEngine.sendControlChange(channel, 10, finalPan[channel])

            sf2Engine.sendControlChange(channel, 11, finalExpression[channel])
            sonivoxEngine.sendControlChange(channel, 11, finalExpression[channel])

            sf2Engine.sendControlChange(channel, 64, if (finalSustain[channel]) 127 else 0)
            sonivoxEngine.sendControlChange(channel, 64, if (finalSustain[channel]) 127 else 0)

            // Send pitch bend
            sf2Engine.sendPitchBend(channel, finalPitchBend[channel])
            sonivoxEngine.sendPitchBend(channel, finalPitchBend[channel])
        }
    }

    /**
     * Resets channel state to defaults.
     * Used when starting from stopped state or stopping playback.
     */
    private fun resetChannelState() {
        // Reset internal channel tracking
        for (channel in 0 until 16) {
            channelProgram[channel] = 0
            updateChannelRouting(channel, 0)
        }

        // Reset SF2 synthesizer (resets pitch bend, modulation, etc.)
        sf2Engine.resetSynthesizer()

        // Reset Sonivox controllers (pitch bend, modulation, etc.)
        sonivoxEngine.resetAllControllers()
    }

    private fun onPlaybackComplete() {
        // Clear keyboard visualization
        MidiEventDispatcher.dispatchAllNotesOff()

        // Let notes ring out briefly, then stop
        playbackExecutor?.schedule({
            if (state == MidiEngine.State.PLAYING) {
                stopForTransition(stopAudioEngines = false)
                onCompletionListener?.invoke()
            }
        }, 500, TimeUnit.MILLISECONDS)
    }

    // ==================== Position Updates ====================

    private fun startPositionUpdates() {
        positionUpdateFuture = playbackExecutor?.scheduleWithFixedDelay(
            {
                onPositionChangedListener?.invoke(currentPositionMs.get(), durationMs)
            },
            0,
            POSITION_UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopPositionUpdates() {
        positionUpdateFuture?.cancel(false)
        positionUpdateFuture = null
    }

    // ==================== File Handling ====================

    private fun resolveMidiFile(path: String): File? {
        return try {
            when {
                path.startsWith("content://") -> {
                    copyContentUriToCache(path)
                }
                else -> {
                    File(path)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyContentUriToCache(uriString: String): File? {
        try {
            val uri = uriString.toUri()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            val fileName = extractFileNameFromUri(uriString)
            val tempFile = File(context.cacheDir, "hybrid_midi_temp_$fileName")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            currentTempFile = tempFile
            return tempFile
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractFileNameFromUri(uriString: String): String {
        return try {
            val lastSegment = uriString.substringAfterLast("/")
            val decoded = Uri.decode(lastSegment)
            if (decoded.contains("/")) decoded.substringAfterLast("/") else decoded
        } catch (_: Exception) {
            "temp_${System.currentTimeMillis()}.mid"
        }
    }

    private fun cleanupTempFile() {
        currentTempFile?.let { tempFile ->
            try {
                if (tempFile.exists()) tempFile.delete()
                currentTempFile = null
            } catch (_: Exception) {
            }
        }
    }

    private fun analyzeLoadedMidiFile(midiFile: File) {
        try {
            val tracker = MidiEventDispatcher.initializeTracker(context)
            tracker.analyzeFile(midiFile)
        } catch (_: Exception) {
        }
    }

    // ==================== State Management ====================

    private fun updateState(newState: MidiEngine.State) {
        synchronized(stateLock) {
            if (state != newState) {
                state = newState
                onStateChangeListener?.invoke(newState)
            }
        }
    }

    private fun notifyError(message: String) {
        onErrorListener?.invoke(message)
    }

    // ==================== Channel Control Listener ====================

    private val channelControlListener = object : MidiEventDispatcher.ChannelControlListener {
        override fun onChannelMuteChanged(channel: Int, isMuted: Boolean) {
            if (isMuted) {
                // Stop all notes on this channel for BOTH engines
                // We must send to both because notes might have been started
                // when routing was different (before a Program Change)
                sf2Engine.sendControlChange(channel, 123, 0) // All Notes Off
                sonivoxEngine.sendControlChange(channel, 123, 0)
            }
        }

        override fun onChannelVolumeChanged(channel: Int, volume: Float) {
            val midiVolume = (volume * 127).toInt().coerceIn(0, 127)
            // Send to both engines for consistent state
            sf2Engine.sendControlChange(channel, 7, midiVolume) // CC7 = Volume
            sonivoxEngine.sendControlChange(channel, 7, midiVolume)
        }
    }
}
