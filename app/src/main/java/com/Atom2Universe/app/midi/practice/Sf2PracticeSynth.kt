package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.Atom2Universe.app.midi.sf2.AudioRenderer
import com.Atom2Universe.app.midi.sf2.AudioRendererListener
import com.Atom2Universe.app.midi.sf2.Sf2File
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import com.Atom2Universe.app.midi.sf2.Sf2Synthesizer
import java.io.File

/**
 * Listener for Sf2PracticeSynth audio events.
 */
interface Sf2PracticeSynthListener {
    /** Called when AudioTrack dies and recovery is attempted */
    fun onAudioRecoveryStarted()
    /** Called when AudioTrack recovery succeeds */
    fun onAudioRecoveryCompleted()
    /** Called when AudioTrack recovery fails */
    fun onAudioRecoveryFailed()
}

/**
 * Implementation de PracticeSynthesizer utilisant le moteur SF2 Kotlin.
 *
 * Charge un fichier SoundFont et synthetise les notes en temps reel.
 */
class Sf2PracticeSynth(
    private val context: Context,
    private val soundFontPath: String
) : PracticeSynthesizer {

    companion object {
        private const val TAG = "Sf2PracticeSynth"
        private const val SAMPLE_RATE = 44100
        // Small buffer for low latency (~3ms at 44100Hz)
        private const val BUFFER_SIZE = 128
    }

    private var sf2File: Sf2File? = null
    private var synthesizer: Sf2Synthesizer? = null
    private var audioRenderer: AudioRenderer? = null
    private var isInitialized = false
    private var synthListener: Sf2PracticeSynthListener? = null
    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun setListener(listener: Sf2PracticeSynthListener?) {
        synthListener = listener
    }

    override fun initialize(): Boolean {
        android.util.Log.d(TAG, "initialize: starting with soundFontPath=$soundFontPath")

        if (soundFontPath.isBlank()) {
            android.util.Log.w(TAG, "initialize: soundFontPath is blank")
            return false
        }

        val file = File(soundFontPath)
        if (!file.exists()) {
            android.util.Log.w(TAG, "initialize: soundFont file does not exist: $soundFontPath")
            return false
        }

        try {
            // For large SF2 files, use memory mapping to avoid OOM
            // For small files, load fully into memory for better performance
            val useMmap = Sf2FileCache.shouldUseMmap(soundFontPath)
            android.util.Log.d(TAG, "initialize: loading SF2 file (useMmap=$useMmap)")

            sf2File = if (useMmap) {
                Sf2FileCache.getMemoryMapped(soundFontPath)
            } else {
                Sf2FileCache.get(soundFontPath)
            }

            if (sf2File == null) {
                android.util.Log.e(TAG, "initialize: failed to load SF2 file")
                return false
            }

            android.util.Log.d(TAG, "initialize: SF2 loaded, name=${sf2File?.name}")

            // Create synthesizer
            synthesizer = Sf2Synthesizer(sf2File!!, SAMPLE_RATE)

            // Create and start audio renderer with low-latency mode for responsive keyboard
            // Force speaker output to prevent audio routing to USB MIDI devices
            audioRenderer = AudioRenderer(
                context = context,
                sampleRate = SAMPLE_RATE,
                bufferSizeFrames = BUFFER_SIZE,
                lowLatency = true,
                forceSpeaker = true
            )

            // Set up listener for audio recovery events
            audioRenderer?.setListener(object : AudioRendererListener {
                override fun onAudioTrackDied(reason: String) {
                    android.util.Log.w(TAG, "AudioRenderer: track died, reason=$reason")
                    synthListener?.onAudioRecoveryStarted()
                }

                override fun onAudioTrackRecovered() {
                    android.util.Log.i(TAG, "AudioRenderer: track recovered successfully")
                    synthListener?.onAudioRecoveryCompleted()
                }

                override fun onOutputDeviceChanged(deviceId: Int, deviceType: Int) {
                    android.util.Log.d(TAG, "AudioRenderer: output device changed to id=$deviceId type=$deviceType")
                }
            })

            audioRenderer?.setRenderCallback { left, right, samples ->
                synthesizer?.render(left, right, samples)
            }

            val started = audioRenderer?.start() == true
            if (!started) {
                android.util.Log.e(TAG, "initialize: failed to start AudioRenderer")
                return false
            }

            isInitialized = true
            android.util.Log.i(TAG, "initialize: SUCCESS, synth is ready")
            return true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "initialize: exception", e)
            return false
        }
    }

    override fun release() {
        android.util.Log.d(TAG, "release: stopping synth, stats=${audioRenderer?.getStats()}")

        audioRenderer?.stop()
        audioRenderer?.release()
        audioRenderer = null

        synthesizer?.allSoundOff()
        synthesizer = null

        // Don't null sf2File - it's managed by Sf2FileCache (shared resource)
        sf2File = null  // Just clear our reference
        isInitialized = false
        synthListener = null

        android.util.Log.d(TAG, "release: complete")
    }

    /**
     * Get statistics about the audio renderer for debugging.
     */
    fun getStats(): String {
        return audioRenderer?.getStats() ?: "AudioRenderer not initialized"
    }

    /**
     * Check if the audio renderer is actively running (not just initialized).
     */
    fun isAudioRunning(): Boolean {
        return audioRenderer?.isRunning() == true
    }

    /**
     * Set an audio tap callback to capture real-time audio output.
     * Pass null to stop capturing.
     */
    fun setAudioTapCallback(callback: AudioRenderer.AudioTapCallback?) {
        audioRenderer?.setAudioTapCallback(callback)
    }

    /**
     * Get the sample rate used by this synth (needed for WAV header).
     */
    fun getSampleRate(): Int = SAMPLE_RATE

    private fun getBuiltInSpeaker(): AudioDeviceInfo? {
        val manager = audioManager ?: return null
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    fun forceOutputToSpeaker(reason: String): Boolean {
        val speaker = getBuiltInSpeaker()
        if (speaker == null) {
            android.util.Log.w(TAG, "forceOutputToSpeaker[$reason]: speaker not found")
            return false
        }
        val result = audioRenderer?.setPreferredOutputDevice(speaker) == true
        android.util.Log.d(TAG, "forceOutputToSpeaker[$reason]: speakerId=${speaker.id} result=$result")
        return result
    }

    fun resetPreferredOutput(reason: String): Boolean {
        val result = audioRenderer?.setPreferredOutputDevice(null) == true
        android.util.Log.d(TAG, "resetPreferredOutput[$reason]: result=$result")
        return result
    }

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (!isInitialized) return
        synthesizer?.noteOn(channel, note, velocity)
    }

    override fun noteOff(channel: Int, note: Int) {
        if (!isInitialized) return
        synthesizer?.noteOff(channel, note)
    }

    override fun programChange(channel: Int, program: Int) {
        if (!isInitialized) return
        synthesizer?.programChange(channel, program)
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        if (!isInitialized) return
        synthesizer?.controlChange(channel, controller, value)
    }

    override fun allNotesOff() {
        if (!isInitialized) return
        synthesizer?.allNotesOff()
    }

    override fun allSoundOff() {
        if (!isInitialized) return
        synthesizer?.allSoundOff()
    }

    override fun isReady(): Boolean = isInitialized && synthesizer != null

    override fun getName(): String = sf2File?.name ?: "SF2"
}
