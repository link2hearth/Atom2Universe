package com.Atom2Universe.app.midi.sf2

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer
import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer.RequiredInstruments
import com.Atom2Universe.app.midi.service.MidiAudioMixer
import com.Atom2Universe.app.midi.service.MidiEngine
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * SF2 Engine - Pure Kotlin implementation of SF2/MIDI playback.
 *
 * This engine provides high-quality SF2 SoundFont synthesis
 * implemented entirely in Kotlin without native libraries.
 */
class Sf2Engine(private val context: Context) : MidiEngine, MidiEventDispatcher.ChannelControlListener {

    companion object {
        // Use 48000Hz to match most Android DACs and avoid Samsung SoundAlive resampling
        private const val SAMPLE_RATE = 48000
        private const val BUFFER_SIZE = 512  // 10,67ms @ 48kHz — taille de rendu (timing MIDI précis)
        private const val POSITION_UPDATE_INTERVAL_MS = 100L
        private const val SEEK_DEBOUNCE_MS = 50L
        private const val OPERATION_TIMEOUT_MS = 2000L
        // BUG FIX 3.17: Limite max d'evenements MIDI pour eviter les fichiers corrompus/infinis
        private const val MAX_MIDI_EVENTS = 500_000

        @Suppress("unused")
        fun isSupported(): Boolean = true
    }

    // State
    private var state: MidiEngine.State = MidiEngine.State.UNINITIALIZED
    private val stateLock = Any()

    // Operation lock to prevent concurrent operations
    private val operationLock = ReentrantLock()
    private val isOperationInProgress = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)

    // Seek debouncing (synchronized via seekLock for thread safety)
    private val seekLock = Any()
    @Volatile private var lastSeekTimeMs = 0L
    @Volatile private var pendingSeekPosition: Long? = null
    private var seekDebounceJob: ScheduledFuture<*>? = null

    // État de l'écran — mémorisé pour être réappliqué si le synthesizer est recréé
    // (streaming SF2 : loadWithMemoryMapping / loadWithBasicStreamingInternal recrée un Sf2Synthesizer)
    @Volatile private var isScreenOff: Boolean = false

    // SF2 components
    private var sf2File: Sf2File? = null
    private var synthesizer: Sf2Synthesizer? = null
    private var audioRenderer: AudioRenderer? = null

    // MIDI playback
    private var midiFile: MidiFile? = null
    private var midiTimeline: List<ScheduledMidiEvent> = emptyList()
    private var durationMs: Long = 0
    private var currentPositionMs = AtomicLong(0)
    private var playbackStartPositionMs: Long = 0
    private var playbackStartFrame: Long = 0

    // Playback executor
    private var playbackExecutor: ScheduledExecutorService? = null
    private var positionUpdateFuture: ScheduledFuture<*>? = null
    private var currentEventIndex: Int = 0

    // BUG FIX 1.8: Verrou dedie pour synchroniser l'acces a currentEventIndex
    // entre le thread audio (processMidiEventsForBuffer) et le main thread (seekToInternal)
    private val playbackStateLock = Any()

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

    // ==================== MidiEngine Interface ====================

    override fun initialize(soundFontPath: String): Boolean {
        if (soundFontPath.isBlank()) {
            notifyError("No SoundFont file specified")
            return false
        }

        // BUG FIX 1.4: Utiliser try-finally pour garantir la libération des ressources
        // même en cas d'exception après fermeture de l'ancien fichier
        val oldSf2File = sf2File
        var newSf2File: Sf2File? = null
        var newSynthesizer: Sf2Synthesizer? = null
        var newAudioRenderer: AudioRenderer? = null

        try {
            val file = File(soundFontPath)
            if (!file.exists()) {
                notifyError("SoundFont file not found")
                return false
            }

            newSf2File = if (Sf2FileCache.shouldUseMmap(soundFontPath)) {
                Sf2FileCache.getMemoryMapped(soundFontPath)
            } else {
                Sf2FileCache.get(soundFontPath)
            }
            if (newSf2File == null) {
                notifyError("Failed to load SoundFont")
                return false
            }

            newSynthesizer = Sf2Synthesizer(newSf2File, SAMPLE_RATE)
            newSynthesizer.masterVolume = volume

            newAudioRenderer = AudioRenderer(
                context = context,
                sampleRate = SAMPLE_RATE,
                bufferSizeFrames = BUFFER_SIZE
            )

            // Tout est prêt, maintenant on peut faire le swap atomique
            // Fermer l'ancien SF2 et libérer les anciennes ressources
            oldSf2File?.close()
            synthesizer?.release()
            audioRenderer?.release()

            // Assigner les nouvelles ressources
            sf2File = newSf2File
            synthesizer = newSynthesizer
            audioRenderer = newAudioRenderer
            configureRenderCallback()

            currentSoundFontPath = soundFontPath

            MidiEventDispatcher.addChannelControlListener(this)

            updateState(MidiEngine.State.INITIALIZED)
            return true

        } catch (e: Sf2ParseException) {
            // Nettoyer les ressources partiellement créées en cas d'erreur
            try { newAudioRenderer?.release() } catch (_: Exception) {}
            try { newSynthesizer?.release() } catch (_: Exception) {}
            try { newSf2File?.close() } catch (_: Exception) {}
            notifyError("Invalid SoundFont file: ${e.message}")
            updateState(MidiEngine.State.ERROR)
            return false
        } catch (e: Exception) {
            // Nettoyer les ressources partiellement créées en cas d'erreur
            try { newAudioRenderer?.release() } catch (_: Exception) {}
            try { newSynthesizer?.release() } catch (_: Exception) {}
            try { newSf2File?.close() } catch (_: Exception) {}
            notifyError("Initialization failed: ${e.message}")
            updateState(MidiEngine.State.ERROR)
            return false
        }
    }

    private inline fun <T> safeOperation(
        @Suppress("UNUSED_PARAMETER") operationName: String,
        default: T,
        allowWhenReleased: Boolean = false,
        block: () -> T
    ): T {
        if (isReleased.get() && !allowWhenReleased) {
            return default
        }

        val acquired = try {
            operationLock.tryLock(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            return default
        }

        if (!acquired) {
            return default
        }

        return try {
            isOperationInProgress.set(true)
            block()
        } catch (_: Exception) {
            default
        } finally {
            isOperationInProgress.set(false)
            operationLock.unlock()
        }
    }

    override fun loadMidiFile(filePath: String): Boolean = safeOperation("loadMidiFile", false) {
        if (state == MidiEngine.State.PLAYING || state == MidiEngine.State.PAUSED) {
            stopInternal()
        }

        if (state != MidiEngine.State.INITIALIZED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.MIDI_LOADED) {
            return@safeOperation false
        }

        try {
            cleanupTempFile()

            val file = resolveMidiFile(filePath)
            if (file == null || !file.exists()) {
                notifyError("MIDI file not found")
                return@safeOperation false
            }

            MidiEventDispatcher.prepareForNewFile()

            midiFile = MidiFile(file)

            buildMidiTimeline()

            if (currentSoundFontPath.isNotEmpty() && Sf2FileCache.shouldUseStreaming(currentSoundFontPath)) {
                reloadSf2WithStreamingForMidi(file.absolutePath)
            }

            currentPositionMs.set(0)
            currentEventIndex = 0
            updateState(MidiEngine.State.MIDI_LOADED)

            analyzeLoadedMidiFile(file)

            return@safeOperation true

        } catch (e: Exception) {
            notifyError("Failed to load MIDI: ${e.message}")
            return@safeOperation false
        }
    }

    private fun reloadSf2WithStreamingForMidi(midiFilePath: String) {
        if (currentSoundFontPath.isBlank()) return

        try {
            // For small SF2 files, use memory mapping (fast, loads everything)
            if (Sf2FileCache.shouldUseMmap(currentSoundFontPath)) {
                loadWithMemoryMapping()
                return
            }

            // For large SF2 files, load only required instruments upfront
            // This is simpler and more reliable than dynamic loading
            loadWithBasicStreaming(midiFilePath)

        } catch (_: Exception) { }
    }

    private fun loadWithMemoryMapping() {
        val mmapSf2 = Sf2FileCache.getMemoryMapped(currentSoundFontPath) ?: return

        sf2File = mmapSf2
        synthesizer?.release()
        synthesizer = Sf2Synthesizer(mmapSf2, SAMPLE_RATE)
        synthesizer?.masterVolume = volume
        synthesizer?.setForceSequentialRendering(isScreenOff)

        configureRenderCallback()
    }

    private fun loadWithBasicStreaming(midiFilePath: String) {
        val analyzer = MidiFileAnalyzer(context)
        val requiredInstruments = analyzer.analyzeRequiredInstrumentsFromPath(midiFilePath)
        loadWithBasicStreamingInternal(requiredInstruments)
    }

    private fun loadWithBasicStreamingInternal(requiredInstruments: RequiredInstruments) {
        val streamedSf2 = Sf2FileCache.getStreaming(currentSoundFontPath, requiredInstruments) ?: return

        sf2File = streamedSf2
        synthesizer?.release()
        synthesizer = Sf2Synthesizer(streamedSf2, SAMPLE_RATE)
        synthesizer?.masterVolume = volume
        synthesizer?.setForceSequentialRendering(isScreenOff)

        configureRenderCallback()
    }

    private fun configureRenderCallback() {
        audioRenderer?.setRenderCallback { left, right, samples ->
            processMidiEventsForBuffer(samples)
            synthesizer?.render(left, right, samples)
        }
    }

    private fun analyzeLoadedMidiFile(midiFile: File) {
        try {
            val tracker = MidiEventDispatcher.initializeTracker(context)
            tracker.analyzeFile(midiFile)
        } catch (_: Exception) { }
    }

    private fun resolveMidiFile(path: String): File? {
        return try {
            when {
                path.startsWith("content://") -> copyContentUriToCache(path)
                else -> File(path)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyContentUriToCache(uriString: String): File? {
        // BUG FIX 1.11: Utiliser try-finally pour garantir la suppression du fichier temporaire
        // en cas d'exception apres sa creation mais avant l'assignation a currentTempFile
        var tempFile: File? = null
        try {
            val uri = uriString.toUri()

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            val fileName = extractFileNameFromUri(uriString)
            tempFile = File(context.cacheDir, "sf2_midi_temp_$fileName")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            currentTempFile = tempFile
            return tempFile

        } catch (_: Exception) {
            // BUG FIX 1.11: Supprimer le fichier temporaire si une exception se produit
            // apres sa creation pour eviter les fuites de fichiers
            tempFile?.let {
                try {
                    if (it.exists()) it.delete()
                } catch (_: Exception) { }
            }
            return null
        }
    }

    private fun extractFileNameFromUri(uriString: String): String {
        return try {
            val lastSegment = uriString.substringAfterLast("/")
            val decoded = Uri.decode(lastSegment)
            if (decoded.contains("/")) {
                decoded.substringAfterLast("/")
            } else {
                decoded
            }
        } catch (_: Exception) {
            "temp_${System.currentTimeMillis()}.mid"
        }
    }

    private fun cleanupTempFile() {
        currentTempFile?.let { tempFile ->
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                currentTempFile = null
            } catch (_: Exception) { }
        }
    }

    override fun start(): Boolean = safeOperation("start", false) {
        if (state != MidiEngine.State.MIDI_LOADED && state != MidiEngine.State.STOPPED &&
            state != MidiEngine.State.PAUSED) {
            return@safeOperation false
        }

        try {
            if (audioRenderer?.start() != true) {
                notifyError("Failed to start audio")
                return@safeOperation false
            }

            if (playbackExecutor == null || playbackExecutor?.isShutdown == true) {
                playbackExecutor = Executors.newScheduledThreadPool(2)
            }

            if (state == MidiEngine.State.STOPPED || state == MidiEngine.State.MIDI_LOADED) {
                currentPositionMs.set(0)
                currentEventIndex = 0
                synthesizer?.reset()
            }

            startMidiPlayback()
            startPositionUpdates()

            updateState(MidiEngine.State.PLAYING)
            return@safeOperation true

        } catch (e: Exception) {
            // Nettoyer l'executor en cas d'erreur pour éviter fuite de ressources
            try {
                playbackExecutor?.shutdownNow()
                playbackExecutor = null
            } catch (_: Exception) { }
            notifyError("Playback failed: ${e.message}")
            return@safeOperation false
        }
    }

    override fun pause(): Unit = safeOperation("pause", Unit) {
        if (state != MidiEngine.State.PLAYING) return@safeOperation

        stopMidiPlayback()
        audioRenderer?.pause()

        MidiEventDispatcher.dispatchAllNotesOff()

        updateState(MidiEngine.State.PAUSED)
    }

    override fun resume(): Unit = safeOperation("resume", Unit) {
        if (state != MidiEngine.State.PAUSED) return@safeOperation

        audioRenderer?.resume()
        startMidiPlayback()
        startPositionUpdates()
        updateState(MidiEngine.State.PLAYING)
    }

    private fun stopInternal() {
        stopMidiPlayback()
        stopPositionUpdates()

        synchronized(seekLock) {
            seekDebounceJob?.cancel(false)
            seekDebounceJob = null
            pendingSeekPosition = null
        }

        audioRenderer?.stop()
        synthesizer?.allSoundOff()

        MidiEventDispatcher.dispatchAllNotesOff()

        playbackExecutor?.shutdownNow()
        playbackExecutor = null

        currentPositionMs.set(0)
        currentEventIndex = 0
        updateState(MidiEngine.State.STOPPED)
    }

    override fun stop(): Unit = safeOperation("stop", Unit) {
        stopInternal()
    }

    override fun release() {
        isReleased.set(true)

        safeOperation("release", Unit, allowWhenReleased = true) {
            stopInternal()

            MidiEventDispatcher.removeChannelControlListener(this)

            audioRenderer?.release()
            audioRenderer = null

            synthesizer?.release()
            synthesizer = null

            // Fermer le fichier SF2 pour libérer les ressources memory-mapped
            sf2File?.close()
            sf2File = null

            midiFile = null
            midiTimeline = emptyList()

            // Nettoyer les callbacks pour éviter les fuites mémoire
            onStateChangeListener = null
            onCompletionListener = null
            onErrorListener = null
            onPositionChangedListener = null

            cleanupTempFile()

            updateState(MidiEngine.State.UNINITIALIZED)
        }
    }

    override fun seekTo(positionMs: Long) {
        if (isReleased.get()) return

        val clampedPos = positionMs.coerceIn(0, durationMs)
        val now = System.currentTimeMillis()

        synchronized(seekLock) {
            val executor = playbackExecutor
            if (now - lastSeekTimeMs < SEEK_DEBOUNCE_MS && executor != null) {
                pendingSeekPosition = clampedPos

                seekDebounceJob?.cancel(false)

                seekDebounceJob = executor.schedule({
                    synchronized(seekLock) {
                        pendingSeekPosition?.let { pos ->
                            pendingSeekPosition = null
                            seekToInternal(pos)
                        }
                    }
                }, SEEK_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                return
            }

            lastSeekTimeMs = now
        }
        seekToInternal(clampedPos)
    }

    private fun seekToInternal(clampedPos: Long): Unit = safeOperation("seekTo", Unit) {
        val wasPlaying = state == MidiEngine.State.PLAYING

        // Step 1: CRITICAL - Stop playback thread BEFORE modifying state
        // Without this, the playback thread continues reading/modifying currentEventIndex
        if (wasPlaying) {
            stopMidiPlayback()
        }

        // Step 2: Stop all sounds immediately
        synthesizer?.allSoundOff()

        // Step 3: Reset synthesizer state (pitch bend, sustain, modulation, etc.)
        synthesizer?.reset()

        // Step 4: Flush audio buffer to cut off any pending audio samples
        audioRenderer?.flushAndResume()

        // Step 5: Notify visualizer
        MidiEventDispatcher.dispatchAllNotesOff()

        // Step 6: Complete seek (replay state, resume playback)
        completeSeekInternal(clampedPos, wasPlaying)
    }

    private fun completeSeekInternal(clampedPos: Long, wasPlaying: Boolean) {
        // BUG FIX 1.8: Synchroniser la modification de currentEventIndex avec playbackStateLock
        // pour eviter une race condition avec processMidiEventsForBuffer
        synchronized(playbackStateLock) {
            currentEventIndex = midiTimeline.indexOfFirst { it.timeMs >= clampedPos }
            if (currentEventIndex < 0) currentEventIndex = midiTimeline.size

            currentPositionMs.set(clampedPos)

            // Reinitialiser playbackStartPositionMs et playbackStartFrame pour le nouveau point de depart
            playbackStartPositionMs = clampedPos
            playbackStartFrame = 0L
        }

        replayStateEvents(clampedPos)

        if (wasPlaying) {
            startMidiPlayback()
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        synthesizer?.masterVolume = this.volume
    }

    override fun setReverb(preset: Int) {
        synthesizer?.setReverbPreset(preset)
    }

    override fun setChorus(preset: Int) {
        synthesizer?.setChorusPreset(preset)
    }

    fun setEqEnabled(enabled: Boolean) {
        synthesizer?.setEqEnabled(enabled)
    }

    fun setEqBandLevel(band: Int, millibels: Int) {
        synthesizer?.setEqBandLevel(band, millibels)
    }

    fun getEqBandLevel(band: Int): Int = synthesizer?.getEqBandLevel(band) ?: 0

    /**
     * Sets the global gain (engine output level) for the SF2 synthesizer.
     * Useful for adjusting volume per-SF2: quiet SF2s benefit from higher gain,
     * loud SF2s may need lower gain.
     * @param gain Gain value (0.05 to 1.0, default 0.25)
     */
    @Suppress("unused")
    fun setGlobalGain(gain: Float) {
        synthesizer?.globalGain = gain
    }

    /**
     * Gets the current global gain.
     */
    @Suppress("unused")
    fun getGlobalGain(): Float = synthesizer?.globalGain ?: 0.25f

    override fun getCurrentPosition(): Long = currentPositionMs.get()

    override fun getDuration(): Long = durationMs

    override fun isPlaying(): Boolean = state == MidiEngine.State.PLAYING

    override fun getState(): MidiEngine.State = state

    override fun getAudioSessionId(): Int = audioRenderer?.audioSessionId ?: 0

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
        val hadMidiLoaded = midiFile != null

        MidiEventDispatcher.prepareForSoundFontChange()

        val success = safeOperation("reloadSoundFont", false) {
            stopInternal()

            audioRenderer?.release()
            audioRenderer = null
            synthesizer?.release()
            synthesizer = null

            // BUG FIX 3.4 & 3.8: Fermer l'ancien sf2File AVANT d'en charger un nouveau
            // pour éviter les fuites de file descriptors (memory-mapped files)
            val oldSf2File = sf2File
            sf2File = null

            if (soundFontPath.isBlank()) {
                // Fermer l'ancien fichier même en cas d'erreur
                oldSf2File?.close()
                notifyError("No SoundFont file specified")
                return@safeOperation false
            }

            val file = File(soundFontPath)
            if (!file.exists()) {
                // Fermer l'ancien fichier même en cas d'erreur
                oldSf2File?.close()
                notifyError("SoundFont file not found")
                return@safeOperation false
            }

            try {
                // Fermer l'ancien fichier avant de charger le nouveau
                oldSf2File?.close()

                sf2File = if (Sf2FileCache.shouldUseMmap(soundFontPath)) {
                    Sf2FileCache.getMemoryMapped(soundFontPath)
                } else {
                    Sf2FileCache.get(soundFontPath)
                }
                if (sf2File == null) {
                    notifyError("Failed to load SoundFont")
                    return@safeOperation false
                }

                synthesizer = Sf2Synthesizer(sf2File!!, SAMPLE_RATE)
                synthesizer?.masterVolume = volume

                audioRenderer = AudioRenderer(
                    context = context,
                    sampleRate = SAMPLE_RATE,
                    bufferSizeFrames = BUFFER_SIZE
                )
                configureRenderCallback()

                currentSoundFontPath = soundFontPath
                updateState(MidiEngine.State.INITIALIZED)

                if (hadMidiLoaded && midiTimeline.isNotEmpty()) {
                    updateState(MidiEngine.State.MIDI_LOADED)

                    MidiEventDispatcher.resendCachedAnalysis()
                }

                return@safeOperation true

            } catch (e: Exception) {
                // BUG FIX 3.8: En cas d'exception, fermer l'ancien fichier s'il n'a pas été fermé
                // et nettoyer le nouveau s'il a été partiellement chargé
                try {
                    oldSf2File?.close()
                } catch (_: Exception) { }
                try {
                    sf2File?.close()
                    sf2File = null
                } catch (_: Exception) { }
                notifyError("Failed to reload SoundFont: ${e.message}")
                updateState(MidiEngine.State.ERROR)
                return@safeOperation false
            }
        }

        return success
    }

    override fun forceDriverRestart() {
        audioRenderer?.stop()
        audioRenderer?.start()
    }

    /**
     * Signale que l'écran vient de s'éteindre (isOff=true) ou de s'allumer (isOff=false).
     *
     * Quand l'écran est éteint, Android throttle le CPU (gouverneur powersave/ondemand),
     * ce qui ralentit les threads workers du VoicePool (priorité THREAD_PRIORITY_AUDIO).
     * En forçant le rendu séquentiel, seul le thread audio principal (THREAD_PRIORITY_URGENT_AUDIO)
     * est utilisé — beaucoup plus résistant au throttling, élimine les craquements en background.
     */
    fun setScreenOff(isOff: Boolean) {
        isScreenOff = isOff
        synthesizer?.setForceSequentialRendering(isOff)
    }

    override fun getDriverStats(): String {
        val synthStats = synthesizer?.getStats() ?: "No synthesizer"
        val audioStats = audioRenderer?.getStats() ?: "No audio"
        return "$synthStats | $audioStats"
    }

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

    // ==================== Direct MIDI Methods (for Hybrid Mode) ====================

    fun startAudioRenderer(): Boolean {
        if (state == MidiEngine.State.UNINITIALIZED || state == MidiEngine.State.ERROR) {
            return false
        }

        try {
            if (audioRenderer?.start() != true) {
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun stopAudioRenderer() {
        try {
            audioRenderer?.stop()
            synthesizer?.allSoundOff()
        } catch (_: Exception) { }
    }

    fun pauseAudioRenderer() {
        try {
            audioRenderer?.pause()
        } catch (_: Exception) { }
    }

    fun resumeAudioRenderer() {
        try {
            audioRenderer?.resume()
        } catch (_: Exception) { }
    }

    /**
     * Forces audio output to the built-in speaker.
     * Call this when a USB MIDI device is connected to prevent audio routing to the USB device.
     * @return true if successfully routed to speaker
     */
    fun forceOutputToSpeaker(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val speaker = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            if (speaker != null) {
                val result = audioRenderer?.setPreferredOutputDevice(speaker) == true
                android.util.Log.d("Sf2Engine", "forceOutputToSpeaker: speakerId=${speaker.id} result=$result")
                result
            } else {
                android.util.Log.w("Sf2Engine", "forceOutputToSpeaker: speaker not found")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("Sf2Engine", "forceOutputToSpeaker failed", e)
            false
        }
    }

    /**
     * Resets audio output to default device routing.
     * @return true if successfully reset
     */
    fun resetOutputDevice(): Boolean {
        return try {
            audioRenderer?.setPreferredOutputDevice(null) == true
        } catch (_: Exception) {
            false
        }
    }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        synthesizer?.noteOn(channel, note, velocity)
    }

    fun sendNoteOff(channel: Int, note: Int) {
        synthesizer?.noteOff(channel, note)
    }

    fun sendProgramChange(channel: Int, program: Int) {
        synthesizer?.programChange(channel, program)
    }

    fun sendControlChange(channel: Int, controller: Int, value: Int) {
        synthesizer?.controlChange(channel, controller, value)
    }

    fun sendPitchBend(channel: Int, value: Int) {
        synthesizer?.pitchBend(channel, value)
    }

    fun sendAllSoundOff() {
        synthesizer?.allSoundOff()
    }

    fun sendAllNotesOff() {
        synthesizer?.allNotesOff()
    }

    /**
     * Flushes the audio buffer to immediately stop any pending audio.
     * Call this after sendAllSoundOff() during seek to prevent ghost notes.
     */
    fun flushAudioBuffer() {
        audioRenderer?.flushAndResume()
    }

    fun resetSynthesizer() {
        synthesizer?.reset()
    }

    fun isReadyForDirectMidi(): Boolean {
        return state != MidiEngine.State.UNINITIALIZED && state != MidiEngine.State.ERROR && synthesizer != null
    }

    /**
     * Returns the current SoundFont path (for HybridMidiEngine use).
     */
    fun getCurrentSoundFontPath(): String = currentSoundFontPath

    /**
     * Prepare for seek to a specific position (for HybridMidiEngine use).
     * Always returns true since we load all instruments upfront (no dynamic loading).
     */
    @Suppress("UNUSED_PARAMETER")
    fun prepareForSeek(targetPositionMs: Long, onReady: (Sf2File?) -> Unit): Boolean {
        // Always ready - all instruments are loaded at the start
        onReady(sf2File)
        return true
    }

    /**
     * Check if preloader is active (DYNAMIC loading mode).
     * Always returns false since we no longer use dynamic loading.
     */
    fun hasActivePreloader(): Boolean {
        return false
    }

    // ==================== MIDI Timeline Building ====================

    private fun buildMidiTimeline() {
        val midi = midiFile ?: return
        val events = mutableListOf<ScheduledMidiEvent>()

        // Double precision pour éviter l'accumulation d'erreurs de troncature
        // sur les morceaux longs (Integer division perdait ~2-5s sur 49 min)
        var microsecondsPerBeat = 500000.0
        val ticksPerBeat = midi.resolution.toDouble()

        data class TickEvent(val tick: Long, val event: MidiEvent, val order: Int)
        val allEvents = mutableListOf<TickEvent>()
        var eventOrder = 0

        for (track in midi.tracks) {
            var absoluteTick = 0L
            for (event in track.events) {
                absoluteTick += event.delta
                allEvents.add(TickEvent(absoluteTick, event, eventOrder++))
                // BUG FIX 3.17: Limite max d'evenements pour eviter les fichiers corrompus
                if (allEvents.size >= MAX_MIDI_EVENTS) break
            }
            if (allEvents.size >= MAX_MIDI_EVENTS) break
        }

        fun eventPriority(event: MidiEvent): Int {
            return when (event) {
                is Tempo -> 0
                is ProgramChange -> 1
                is Controller -> 2
                is PitchBend -> 3
                is NoteOff -> 4
                is NoteOn -> if (event.velocity == 0) 4 else 5
                else -> 6
            }
        }

        allEvents.sortWith(compareBy<TickEvent>({ it.tick }, { eventPriority(it.event) }, { it.order }))

        var currentTick = 0L
        var currentTimeUs = 0.0

        for (tickEvent in allEvents) {
            val deltaTicks = tickEvent.tick - currentTick
            // BUG FIX 3.16: Valider que deltaTicks >= 0 et ticksPerBeat > 0
            if (deltaTicks >= 0 && ticksPerBeat > 0) {
                currentTimeUs += deltaTicks.toDouble() * microsecondsPerBeat / ticksPerBeat
            }
            currentTick = tickEvent.tick

            // Valider que timeMs >= 0
            val timeMs = (currentTimeUs / 1000.0).toLong().coerceAtLeast(0)

            when (val event = tickEvent.event) {
                is Tempo -> {
                    microsecondsPerBeat = event.mpqn.toDouble()
                }
                is NoteOn -> {
                    val isNoteOff = event.velocity == 0
                    events.add(ScheduledMidiEvent(
                        timeMs = timeMs,
                        type = if (isNoteOff) MidiEventType.NOTE_OFF else MidiEventType.NOTE_ON,
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

        midiTimeline = events.sortedBy { it.timeMs }
        // Durée = temps du dernier tick (tous événements confondus, y compris EndOfTrack)
        // au lieu du dernier événement schedulé, pour correspondre à la durée réelle du fichier MIDI
        durationMs = (currentTimeUs / 1000.0).toLong()
    }

    // ==================== MIDI Playback ====================

    private fun startMidiPlayback() {
        // BUG FIX 1.8: Synchroniser avec playbackStateLock
        synchronized(playbackStateLock) {
            playbackStartPositionMs = currentPositionMs.get()
            playbackStartFrame = 0L
        }
    }

    private fun stopMidiPlayback() {
        // BUG FIX 1.8: Synchroniser avec playbackStateLock
        synchronized(playbackStateLock) {
            playbackStartFrame = 0L
        }
    }

    private fun processMidiEventsForBuffer(numSamples: Int) {
        // BUG FIX 1.8: Utiliser playbackStateLock pour synchroniser avec seekToInternal
        // Cela evite une race condition ou le thread audio lit/modifie currentEventIndex
        // pendant que seekToInternal le modifie aussi
        if (state != MidiEngine.State.PLAYING) return

        // Capturer les valeurs locales sous verrou pour eviter les modifications pendant le traitement
        val localTimeline: List<ScheduledMidiEvent>
        val localPlaybackStartPositionMs: Long
        var localEventIndex: Int
        val bufferEndMs: Long

        synchronized(playbackStateLock) {
            // Double-check: si une operation est en cours (seek), ne pas traiter
            if (isOperationInProgress.get()) return

            localTimeline = midiTimeline
            localPlaybackStartPositionMs = playbackStartPositionMs
            localEventIndex = currentEventIndex

            val bufferStartFrame = playbackStartFrame
            val bufferEndFrame = bufferStartFrame + numSamples
            playbackStartFrame = bufferEndFrame

            bufferEndMs = localPlaybackStartPositionMs + (bufferEndFrame * 1000L / SAMPLE_RATE)
            currentPositionMs.set(bufferEndMs)
        }

        // Traitement des evenements hors du verrou (dispatchMidiEvent peut etre lent)
        while (localEventIndex < localTimeline.size) {
            val event = localTimeline[localEventIndex]
            if (event.timeMs > bufferEndMs) break

            dispatchMidiEvent(event)
            localEventIndex++
        }

        // Mettre a jour currentEventIndex sous verrou
        synchronized(playbackStateLock) {
            // Seulement mettre a jour si aucune operation n'a modifie l'index entre-temps
            if (!isOperationInProgress.get()) {
                currentEventIndex = localEventIndex
            }
        }

        if (localEventIndex >= localTimeline.size && bufferEndMs >= durationMs) {
            onPlaybackComplete()
        }
    }

    private fun dispatchMidiEvent(event: ScheduledMidiEvent) {
        val synth = synthesizer ?: return

        val isMuted = MidiEventDispatcher.isChannelMuted(event.channel)

        when (event.type) {
            MidiEventType.NOTE_ON -> {
                if (!isMuted) {
                    val normalizedVelocity = MidiAudioMixer.calculateAdjustedVelocity(
                        event.channel,
                        event.data2,
                        applyChannelVolume = false
                    )
                    synth.noteOn(event.channel, event.data1, normalizedVelocity)
                }
                dispatchMidiToVisualizer(event)
            }
            MidiEventType.NOTE_OFF -> {
                if (!isMuted) {
                    synth.noteOff(event.channel, event.data1)
                }
                dispatchMidiToVisualizer(event)
            }
            MidiEventType.PROGRAM_CHANGE -> {
                synth.programChange(event.channel, event.data1)
                MidiAudioMixer.applyInstrumentBoost(event.channel, event.data1)
                dispatchMidiToVisualizer(event)
            }
            MidiEventType.CONTROL_CHANGE -> {
                synth.controlChange(event.channel, event.data1, event.data2)
            }
            MidiEventType.PITCH_BEND -> {
                val value = event.data1 or (event.data2 shl 7)
                synth.pitchBend(event.channel, value)
            }
        }
    }

    private fun dispatchMidiToVisualizer(event: ScheduledMidiEvent) {
        // Pas de listeners visuels actifs (ex: écran éteint en arrière-plan) :
        // court-circuiter pour éviter les allocations ByteArray sur le thread audio.
        if (!MidiEventDispatcher.hasMidiEventListeners()) return

        val midiBytes = when (event.type) {
            MidiEventType.NOTE_ON -> {
                byteArrayOf(
                    (0x90 or event.channel).toByte(),
                    event.data1.toByte(),
                    event.data2.toByte()
                )
            }
            MidiEventType.NOTE_OFF -> {
                byteArrayOf(
                    (0x80 or event.channel).toByte(),
                    event.data1.toByte(),
                    event.data2.toByte()
                )
            }
            MidiEventType.PROGRAM_CHANGE -> {
                byteArrayOf(
                    (0xC0 or event.channel).toByte(),
                    event.data1.toByte()
                )
            }
            else -> null
        }

        midiBytes?.let {
            MidiEventDispatcher.processMidiBytes(it)
        }
    }

    private fun replayStateEvents(upToMs: Long) {
        val synth = synthesizer ?: return

        // Track final state for each channel
        val finalProgram = IntArray(16) { 0 }
        val finalPitchBend = IntArray(16) { 8192 }  // Center value (no bend)
        val finalVolume = IntArray(16) { 100 }      // Default volume
        val finalPan = IntArray(16) { 64 }          // Center pan
        val finalExpression = IntArray(16) { 127 }  // Full expression
        val finalModulation = IntArray(16) { 0 }    // No modulation
        val finalSustain = BooleanArray(16) { false }

        // Scan timeline to find final state at seek position
        for (event in midiTimeline) {
            if (event.timeMs > upToMs) break

            when (event.type) {
                MidiEventType.PROGRAM_CHANGE -> {
                    finalProgram[event.channel] = event.data1
                }
                MidiEventType.PITCH_BEND -> {
                    finalPitchBend[event.channel] = event.data1 or (event.data2 shl 7)
                }
                MidiEventType.CONTROL_CHANGE -> {
                    when (event.data1) {
                        7 -> finalVolume[event.channel] = event.data2      // Volume
                        10 -> finalPan[event.channel] = event.data2        // Pan
                        11 -> finalExpression[event.channel] = event.data2 // Expression
                        1 -> finalModulation[event.channel] = event.data2  // Modulation
                        64 -> finalSustain[event.channel] = event.data2 >= 64 // Sustain pedal
                    }
                }
                else -> {}
            }
        }

        // Apply final state to all channels
        for (channel in 0 until 16) {
            synth.programChange(channel, finalProgram[channel])
            synth.pitchBend(channel, finalPitchBend[channel])
            synth.controlChange(channel, 7, finalVolume[channel])
            synth.controlChange(channel, 10, finalPan[channel])
            synth.controlChange(channel, 11, finalExpression[channel])
            synth.controlChange(channel, 1, finalModulation[channel])
            synth.controlChange(channel, 64, if (finalSustain[channel]) 127 else 0)
        }
    }

    private fun onPlaybackComplete() {
        MidiEventDispatcher.dispatchAllNotesOff()

        playbackExecutor?.schedule({
            if (state == MidiEngine.State.PLAYING) {
                stop()
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

    // ==================== ChannelControlListener Implementation ====================

    override fun onChannelMuteChanged(channel: Int, isMuted: Boolean) {
        if (isMuted) {
            synthesizer?.controlChange(channel, Sf2Synthesizer.CC_ALL_NOTES_OFF, 0)
        }
    }

    override fun onChannelVolumeChanged(channel: Int, volume: Float) {
        val midiVolume = (volume * 127).toInt().coerceIn(0, 127)
        synthesizer?.controlChange(channel, Sf2Synthesizer.CC_VOLUME, midiVolume)
    }
}

/**
 * Scheduled MIDI event for the playback timeline.
 */
data class ScheduledMidiEvent(
    val timeMs: Long,
    val type: MidiEventType,
    val channel: Int,
    val data1: Int,
    val data2: Int
)

/**
 * MIDI event types for the timeline.
 */
enum class MidiEventType {
    NOTE_ON,
    NOTE_OFF,
    PROGRAM_CHANGE,
    CONTROL_CHANGE,
    PITCH_BEND
}
