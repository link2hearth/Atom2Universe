package com.Atom2Universe.app.midi.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher
import org.billthefarmer.mididriver.MidiDriver
import java.io.File
import java.io.FileOutputStream

/**
 * Sonivox EAS MIDI synthesis engine (lightweight, built-in sounds)
 *
 * Uses org.billthefarmer:mididriver (Sonivox EAS) for MIDI synthesis
 * - No external SoundFont files needed (uses built-in General MIDI sounds)
 * - Lightweight memory footprint
 * - Low latency
 * - Supports DLS (DownLoadable Sounds) format only (not SF2)
 *
 * This is the default engine when no SoundFont is loaded.
 */
class SonivoxEngine(private val context: Context) : MidiEngine, MidiEventDispatcher.ChannelControlListener {

    private var currentState = MidiEngine.State.UNINITIALIZED
    private var currentMidiFile: File? = null
    private var currentTempFile: File? = null
    private var midiDriver: MidiDriver? = null
    private var midiPlayer: MidiFilePlayer? = null
    private var audioSessionId: Int = 0
    private var isMidiDriverStarted = false
    private var pendingSoundFontPath: String? = null
    private var pendingReverbPreset: Int? = null

    // Mécanisme de restart périodique pour éviter la perte de son
    private var tracksPlayedSinceRestart = 0
    private var lastDriverRestartTime = System.currentTimeMillis()

    // Track if audio was forced to speaker (to re-apply after driver restart)
    private var wasOutputForcedToSpeaker = false

    companion object {
        private const val TAG = "SonivoxEngine"

        // Seuils pour le restart périodique du driver
        private const val MAX_TRACKS_BEFORE_RESTART = 10
        private const val MAX_TIME_BEFORE_RESTART_MS = 10 * 60 * 1000L

        @Suppress("unused")
        fun isSupported(): Boolean {
            return try {
                MidiDriver.getInstance() != null
            } catch (_: Exception) {
                false
            }
        }
    }

    // Callbacks
    private var onStateChangeCallback: ((MidiEngine.State) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onPositionChangedCallback: ((Long, Long) -> Unit)? = null

    override fun initialize(soundFontPath: String): Boolean {
        try {
            midiDriver = MidiDriver.getInstance()

            if (midiDriver == null) {
                setState(MidiEngine.State.ERROR)
                onErrorCallback?.invoke("mididriver_instance_failed")
                return false
            }

            midiPlayer = MidiFilePlayer()
            setupPlaybackCallbacks()

            tracksPlayedSinceRestart = 0
            lastDriverRestartTime = System.currentTimeMillis()

            // Store SoundFont path for loading on first start()
            if (soundFontPath.isNotBlank()) {
                pendingSoundFontPath = soundFontPath
            } else {
                pendingSoundFontPath = null
            }

            // Register for channel control events (volume/mute)
            MidiEventDispatcher.addChannelControlListener(this)

            setState(MidiEngine.State.INITIALIZED)
            return true

        } catch (e: Exception) {
            setState(MidiEngine.State.ERROR)
            onErrorCallback?.invoke("initialization_exception: ${e.message}")
            return false
        }
    }

    private fun setupPlaybackCallbacks() {
        midiPlayer?.onMidiEvent = { midiBytes ->
            try {
                val status = midiBytes[0].toInt() and 0xFF
                val type = status and 0xF0
                val channel = status and 0x0F

                val isChannelEvent = type in 0x80..0xEF
                val isMuted = isChannelEvent && MidiEventDispatcher.isChannelMuted(channel)

                if (!isMuted) {
                    val processedBytes = MidiAudioMixer.processMidiEvent(midiBytes)
                    if (processedBytes != null) {
                        midiDriver?.write(processedBytes)
                    }
                }

                MidiEventDispatcher.processMidiBytes(midiBytes)
            } catch (_: Exception) { }
        }

        midiPlayer?.onPlaybackStarted = { }

        midiPlayer?.onPlaybackCompleted = {
            MidiEventDispatcher.reset()
            setState(MidiEngine.State.STOPPED)
            onCompletionCallback?.invoke()
        }

        midiPlayer?.onError = { error ->
            setState(MidiEngine.State.ERROR)
            onErrorCallback?.invoke(error)
        }

        midiPlayer?.onPositionChanged = { currentMs, durationMs ->
            onPositionChangedCallback?.invoke(currentMs, durationMs)
        }
    }

    override fun loadMidiFile(filePath: String): Boolean {
        if (currentState == MidiEngine.State.UNINITIALIZED) {
            onErrorCallback?.invoke("engine_not_initialized")
            return false
        }

        // Prepare visualizer dispatcher for new file (clears all state including cache)
        MidiEventDispatcher.prepareForNewFile()
        cleanupTempFile()

        val midiFile = resolveMidiFile(filePath)
        if (midiFile == null || !midiFile.exists()) {
            setState(MidiEngine.State.ERROR)
            onErrorCallback?.invoke("midi_file_not_found")
            return false
        }

        if (filePath.startsWith("content://")) {
            currentTempFile = midiFile
        }

        val loaded = midiPlayer?.loadFile(midiFile) ?: false

        if (loaded) {
            currentMidiFile = midiFile
            setState(MidiEngine.State.MIDI_LOADED)
            analyzeLoadedMidiFile(midiFile)
            return true
        } else {
            setState(MidiEngine.State.ERROR)
            onErrorCallback?.invoke("midi_load_failed")
            return false
        }
    }

    private fun analyzeLoadedMidiFile(midiFile: File) {
        try {
            val tracker = MidiEventDispatcher.initializeTracker(context)
            tracker.analyzeFile(midiFile)
        } catch (_: Exception) { }
    }

    override fun start(): Boolean {
        if (currentState != MidiEngine.State.MIDI_LOADED &&
            currentState != MidiEngine.State.PAUSED &&
            currentState != MidiEngine.State.STOPPED) {
            return false
        }

        try {
            onTrackStarting()

            // Vérifier si le driver est réellement démarré (peut avoir été stoppé par practice mode)
            // config() retourne null si le driver n'est pas démarré
            val driverActuallyRunning = try {
                midiDriver?.config() != null
            } catch (_: Exception) {
                false
            }

            val needsDriverInit = !isMidiDriverStarted || !driverActuallyRunning
            if (needsDriverInit) {
                // S'assurer que le driver est stoppé avant de le redémarrer
                if (!driverActuallyRunning) {
                    try {
                        midiDriver?.stop()
                    } catch (_: Exception) { }
                }

                midiDriver?.start()
                isMidiDriverStarted = true

                // Apply pending reverb now that driver is started
                pendingReverbPreset?.let { preset ->
                    try {
                        midiDriver?.setReverb(preset)
                    } catch (_: Exception) { }
                }

                pendingSoundFontPath?.let { sfPath ->
                    Thread.sleep(300)
                    val loaded = loadSoundFont(sfPath)
                    if (loaded) {
                        pendingSoundFontPath = null
                    }
                }
            }

            val delayMs = if (needsDriverInit) 200L else 50L

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    midiPlayer?.play()
                } catch (e: Exception) {
                    setState(MidiEngine.State.ERROR)
                    onErrorCallback?.invoke("playback_start_failed: ${e.message}")
                }
            }, delayMs)

            setState(MidiEngine.State.PLAYING)
            return true

        } catch (_: Exception) {
            setState(MidiEngine.State.ERROR)
            return false
        }
    }

    override fun pause() {
        if (currentState != MidiEngine.State.PLAYING) {
            return
        }

        try {
            midiPlayer?.pause()
            sendAllNotesOff()
            MidiEventDispatcher.dispatchAllNotesOff()
            setState(MidiEngine.State.PAUSED)
        } catch (_: Exception) { }
    }

    override fun resume() {
        if (currentState != MidiEngine.State.PAUSED) {
            return
        }

        try {
            if (!isMidiDriverStarted) {
                midiDriver?.start()
                isMidiDriverStarted = true
            }

            midiPlayer?.resume()
            setState(MidiEngine.State.PLAYING)
        } catch (_: Exception) { }
    }

    override fun stop() {
        try {
            midiPlayer?.stop()
            sendAllNotesOff()
            setState(MidiEngine.State.STOPPED)
            currentMidiFile = null

            Handler(Looper.getMainLooper()).postDelayed({
                cleanupTempFile()
            }, 200)
        } catch (_: Exception) { }
    }

    override fun release() {
        try {
            stop()
        } catch (_: Exception) { }

        try {
            midiPlayer?.release()
            midiPlayer = null
        } catch (_: Exception) { }

        try {
            if (midiDriver != null && isMidiDriverStarted) {
                midiDriver?.stop()
                isMidiDriverStarted = false
            }
        } catch (_: Exception) { }

        // Unregister from channel control events
        try {
            MidiEventDispatcher.removeChannelControlListener(this)
        } catch (_: Exception) { }

        try {
            setState(MidiEngine.State.UNINITIALIZED)
            currentMidiFile = null
            onStateChangeCallback = null
            onErrorCallback = null
            onCompletionCallback = null
            onPositionChangedCallback = null
        } catch (_: Exception) { }

        try {
            cleanupTempFile()
        } catch (_: Exception) { }
    }

    override fun seekTo(positionMs: Long) {
        if (currentState == MidiEngine.State.UNINITIALIZED || currentState == MidiEngine.State.ERROR) {
            return
        }

        try {
            sendAllNotesOff()
            MidiEventDispatcher.dispatchAllNotesOff()
            midiPlayer?.seekTo(positionMs)
            midiPlayer?.sendProgramChangesUpToPosition(positionMs)
        } catch (_: Exception) { }
    }

    override fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        try {
            val volumeInt = (clampedVolume * 100).toInt()
            midiDriver?.setVolume(volumeInt)
        } catch (_: Exception) { }
    }

    override fun setReverb(preset: Int) {
        // Store the reverb preset - we'll apply it when the driver is started
        // MidiDriver.setReverb() crashes if called before start()
        if (!isMidiDriverStarted) {
            pendingReverbPreset = preset
            return
        }

        try {
            midiDriver?.setReverb(preset)
            pendingReverbPreset = preset
        } catch (_: Exception) { }
    }

    override fun setChorus(preset: Int) {
        // Sonivox does not support chorus effect - no-op
    }

    override fun getCurrentPosition(): Long {
        return midiPlayer?.getCurrentPositionMs() ?: 0L
    }

    override fun getDuration(): Long {
        return midiPlayer?.getDurationMs() ?: 0L
    }

    override fun isPlaying(): Boolean {
        return currentState == MidiEngine.State.PLAYING
    }

    override fun getState(): MidiEngine.State {
        return currentState
    }

    override fun getAudioSessionId(): Int {
        return audioSessionId
    }

    override fun setOnStateChangeListener(listener: (MidiEngine.State) -> Unit) {
        onStateChangeCallback = listener
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionCallback = listener
    }

    override fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorCallback = listener
    }

    override fun setOnPositionChangedListener(listener: (Long, Long) -> Unit) {
        onPositionChangedCallback = listener
    }

    override fun reloadSoundFont(soundFontPath: String): Boolean {
        try {
            val wasPlaying = isPlaying()
            val currentMidi = currentMidiFile

            if (wasPlaying) {
                stop()
            }

            if (midiDriver != null && isMidiDriverStarted) {
                midiDriver?.stop()
                isMidiDriverStarted = false
            }

            Thread.sleep(100)

            val initSuccess = initialize(soundFontPath)

            if (initSuccess) {
                if (currentMidi != null && currentMidi.exists()) {
                    loadMidiFile(currentMidi.absolutePath)
                }
                return true
            } else {
                return false
            }

        } catch (_: Exception) {
            return false
        }
    }

    override fun forceDriverRestart() {
        restartMidiDriver()
    }

    override fun getDriverStats(): String {
        val uptimeMs = System.currentTimeMillis() - lastDriverRestartTime
        return "tracks=$tracksPlayedSinceRestart, uptime=${uptimeMs / 1000}s, started=$isMidiDriverStarted"
    }

    // === Private helper methods ===

    private fun setState(newState: MidiEngine.State) {
        if (currentState != newState) {
            currentState = newState
            onStateChangeCallback?.invoke(newState)
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

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }

            val fileName = extractFileNameFromUri(uriString)
            val tempFile = File(context.cacheDir, "midi_temp_$fileName")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            return tempFile

        } catch (_: Exception) {
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

    private fun sendAllNotesOff() {
        try {
            for (channel in 0..15) {
                // CC 120: All Sound Off - coupe immédiatement tous les sons
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    120.toByte(),
                    0.toByte()
                ))
                // CC 121: Reset All Controllers - reset sustain, modulation, etc.
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    121.toByte(),
                    0.toByte()
                ))
                // CC 64: Sustain Pedal Off - explicite au cas où CC121 ne suffit pas
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    64.toByte(),
                    0.toByte()
                ))
                // CC 123: All Notes Off
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    123.toByte(),
                    0.toByte()
                ))
            }
        } catch (_: Exception) { }
    }

    private fun shouldRestartDriver(): Boolean {
        val timeSinceRestart = System.currentTimeMillis() - lastDriverRestartTime
        return tracksPlayedSinceRestart >= MAX_TRACKS_BEFORE_RESTART ||
                timeSinceRestart >= MAX_TIME_BEFORE_RESTART_MS
    }

    private fun restartMidiDriver() {
        try {
            sendAllNotesOff()

            if (isMidiDriverStarted) {
                midiDriver?.stop()
                isMidiDriverStarted = false
                Thread.sleep(100)
            }

            midiDriver?.start()
            isMidiDriverStarted = true

            // Re-apply reverb after restart
            pendingReverbPreset?.let { preset ->
                try {
                    midiDriver?.setReverb(preset)
                } catch (_: Exception) { }
            }

            tracksPlayedSinceRestart = 0
            lastDriverRestartTime = System.currentTimeMillis()

        } catch (_: Exception) {
            isMidiDriverStarted = false
        }
    }

    private fun onTrackStarting() {
        tracksPlayedSinceRestart++

        if (shouldRestartDriver()) {
            restartMidiDriver()
        }
    }

    // ==================== Direct MIDI Methods (for Hybrid Mode) ====================

    /**
     * Starts the MidiDriver without loading a MIDI file.
     * Used by HybridMidiEngine to enable Sonivox synthesis in hybrid mode.
     * @return true if driver started successfully
     */
    private var sonivoxNoteLogCount = 0

    fun startDriver(): Boolean {
        try {
            if (!isMidiDriverStarted) {
                if (midiDriver == null) {
                    return false
                }
                midiDriver?.start()
                isMidiDriverStarted = true
                sonivoxNoteLogCount = 0

                // Apply pending reverb
                pendingReverbPreset?.let { preset ->
                    try {
                        midiDriver?.setReverb(preset)
                    } catch (_: Exception) { }
                }
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Stops the MidiDriver.
     * Used by HybridMidiEngine when stopping playback.
     */
    fun stopDriver() {
        try {
            sendAllNotesOff()
            if (isMidiDriverStarted) {
                midiDriver?.stop()
                isMidiDriverStarted = false
            }
        } catch (_: Exception) { }
    }

    /**
     * Sends a Note On event directly to the MidiDriver.
     * @param channel MIDI channel (0-15)
     * @param note Note number (0-127)
     * @param velocity Velocity (0-127)
     */
    fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        if (!isMidiDriverStarted) {
            return
        }
        try {
            val bytes = byteArrayOf(
                (0x90 + (channel and 0x0F)).toByte(),
                (note and 0x7F).toByte(),
                (velocity and 0x7F).toByte()
            )
            midiDriver?.write(bytes)
        } catch (_: Exception) { }
    }

    /**
     * Sends a Note Off event directly to the MidiDriver.
     * @param channel MIDI channel (0-15)
     * @param note Note number (0-127)
     */
    fun sendNoteOff(channel: Int, note: Int) {
        if (!isMidiDriverStarted) return
        try {
            midiDriver?.write(byteArrayOf(
                (0x80 + (channel and 0x0F)).toByte(),
                (note and 0x7F).toByte(),
                0.toByte()
            ))
        } catch (_: Exception) { }
    }

    /**
     * Sends a Program Change event directly to the MidiDriver.
     * @param channel MIDI channel (0-15)
     * @param program Program number (0-127)
     */
    fun sendProgramChange(channel: Int, program: Int) {
        if (!isMidiDriverStarted) return
        try {
            midiDriver?.write(byteArrayOf(
                (0xC0 + (channel and 0x0F)).toByte(),
                (program and 0x7F).toByte()
            ))
        } catch (_: Exception) { }
    }

    /**
     * Sends a Control Change event directly to the MidiDriver.
     * @param channel MIDI channel (0-15)
     * @param controller Controller number (0-127)
     * @param value Controller value (0-127)
     */
    fun sendControlChange(channel: Int, controller: Int, value: Int) {
        if (!isMidiDriverStarted) return
        try {
            midiDriver?.write(byteArrayOf(
                (0xB0 + (channel and 0x0F)).toByte(),
                (controller and 0x7F).toByte(),
                (value and 0x7F).toByte()
            ))
        } catch (_: Exception) { }
    }

    /**
     * Sends a Pitch Bend event directly to the MidiDriver.
     * @param channel MIDI channel (0-15)
     * @param value Pitch bend value (0-16383, center = 8192)
     */
    fun sendPitchBend(channel: Int, value: Int) {
        if (!isMidiDriverStarted) return
        try {
            val lsb = value and 0x7F
            val msb = (value shr 7) and 0x7F
            midiDriver?.write(byteArrayOf(
                (0xE0 + (channel and 0x0F)).toByte(),
                lsb.toByte(),
                msb.toByte()
            ))
        } catch (_: Exception) { }
    }

    /**
     * Sends All Sound Off on all channels.
     */
    fun sendAllSoundOffDirect() {
        sendAllNotesOff()
    }

    /**
     * Resets all controllers on all channels to default values.
     * This includes pitch bend (center), modulation (0), expression (127), etc.
     * Call this during seek to prevent lingering vibrato/pitch effects.
     */
    fun resetAllControllers() {
        try {
            for (channel in 0..15) {
                // CC 121: Reset All Controllers
                // This resets modulation, expression, sustain pedal, etc. to defaults
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    121.toByte(),
                    0.toByte()
                ))

                // Reset Pitch Bend to center (8192 = 0x2000)
                // Pitch bend message: 0xE0 + channel, LSB (0), MSB (64)
                midiDriver?.write(byteArrayOf(
                    (0xE0 + channel).toByte(),
                    0.toByte(),    // LSB = 0
                    64.toByte()    // MSB = 64 (64 << 7 = 8192 = center)
                ))

                // Explicitly reset modulation to 0 (in case CC121 doesn't do it)
                midiDriver?.write(byteArrayOf(
                    (0xB0 + channel).toByte(),
                    1.toByte(),   // CC 1 = Modulation
                    0.toByte()
                ))
            }
        } catch (_: Exception) { }
    }

    /**
     * Checks if the Sonivox driver is started and ready for direct MIDI.
     */
    fun isReadyForDirectMidi(): Boolean {
        return isMidiDriverStarted && midiDriver != null
    }

    // ==================== Audio Device Routing ====================

    /**
     * Forces audio output to the built-in speaker.
     * Call this when a USB MIDI device is connected and is hijacking audio output.
     * @return true if successfully routed to speaker
     */
    fun forceOutputToSpeaker(): Boolean {
        val speakerId = getBuiltInSpeakerId()
        if (speakerId != -1) {
            try {
                val result = midiDriver?.setOutputDevice(speakerId) ?: false
                if (result) {
                    wasOutputForcedToSpeaker = true
                }
                android.util.Log.d("SonivoxEngine", "forceOutputToSpeaker: speakerId=$speakerId result=$result")
                return result
            } catch (_: UnsatisfiedLinkError) {
                // Native function not available in this build - need clean rebuild
                android.util.Log.w("SonivoxEngine", "setOutputDevice not available in native library - please clean rebuild")
            } catch (_: Exception) {
                android.util.Log.e("SonivoxEngine", "forceOutputToSpeaker failed")
            }
        }
        return false
    }

    /**
     * Resets audio output to default device routing.
     * @return true if successfully reset
     */
    fun resetOutputDevice(): Boolean {
        try {
            val result = midiDriver?.setOutputDevice(-1) ?: false
            if (result) {
                wasOutputForcedToSpeaker = false
            }
            android.util.Log.d("SonivoxEngine", "resetOutputDevice: result=$result")
            return result
        } catch (_: UnsatisfiedLinkError) {
            // Native function not available in this build - need clean rebuild
            android.util.Log.w("SonivoxEngine", "setOutputDevice not available in native library - please clean rebuild")
        } catch (_: Exception) {
            android.util.Log.e("SonivoxEngine", "resetOutputDevice failed")
        }
        return false
    }

    /**
     * Checks if any USB MIDI devices are connected.
     * @return true if at least one USB MIDI device is connected
     */
    @Suppress("unused")
    private fun hasConnectedUsbMidiDevice(): Boolean {
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
     * Gets the device ID of the built-in speaker.
     * @return device ID or -1 if not found
     */
    private fun getBuiltInSpeakerId(): Int {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                return -1
            }

            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return device.id
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SonivoxEngine", "getBuiltInSpeakerId failed", e)
        }
        return -1
    }

    // ==================== SoundFont Loading ====================

    private fun loadSoundFont(soundFontPath: String): Boolean {
        try {
            val soundFontFile = File(soundFontPath)

            if (!soundFontFile.exists()) {
                android.util.Log.w(TAG, "loadSoundFont: file not found: $soundFontPath")
                return false
            }

            if (!soundFontFile.canRead()) {
                android.util.Log.w(TAG, "loadSoundFont: file not readable: $soundFontPath")
                return false
            }

            val soundFontBytes = soundFontFile.readBytes()

            val config = midiDriver?.config()
            if (config == null) {
                android.util.Log.w(TAG, "loadSoundFont: midiDriver not configured")
                return false
            }

            val loaded = midiDriver?.loadDLS(soundFontBytes) ?: false
            if (!loaded) {
                android.util.Log.w(TAG, "loadSoundFont: loadDLS returned false (not a valid DLS file?)")
            }
            return loaded

        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "loadSoundFont: OutOfMemoryError (file too large: $soundFontPath)", e)
            return false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "loadSoundFont: failed to load $soundFontPath", e)
            return false
        }
    }

    // ==================== ChannelControlListener Implementation ====================

    override fun onChannelMuteChanged(channel: Int, isMuted: Boolean) {
        if (isMuted && isMidiDriverStarted) {
            // Send All Notes Off (CC#123) to mute the channel
            try {
                midiDriver?.write(byteArrayOf(
                    (0xB0 + (channel and 0x0F)).toByte(),
                    123.toByte(),
                    0.toByte()
                ))
            } catch (_: Exception) { }
        }
    }

    override fun onChannelVolumeChanged(channel: Int, volume: Float) {
        if (isMidiDriverStarted) {
            try {
                val midiVolume = (volume * 127).toInt().coerceIn(0, 127)
                // Send Volume (CC#7) to adjust channel volume
                midiDriver?.write(byteArrayOf(
                    (0xB0 + (channel and 0x0F)).toByte(),
                    7.toByte(),
                    midiVolume.toByte()
                ))
            } catch (_: Exception) { }
        }
    }
}
