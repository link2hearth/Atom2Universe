package com.Atom2Universe.app.midi.fluidsynth

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.Atom2Universe.app.midi.practice.PracticeSynthesizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FluidSynth-based implementation of PracticeSynthesizer.
 *
 * Provides low-latency SF2 synthesis for piano practice mode with USB MIDI keyboards.
 * Uses FluidSynth's built-in Oboe audio driver for optimal performance, with an
 * AudioTrack fallback if Oboe is unavailable (e.g. when another FluidSynth instance
 * already holds the audio driver).
 */
class FluidSynthPracticeSynth(
    private val context: Context,
    private val soundFontPath: String
) : PracticeSynthesizer {

    companion object {
        private const val TAG = "FluidSynthPracticeSynth"
        private const val SAMPLE_RATE = 48000
        private const val FRAMES_PER_BUFFER = 128  // ~2.7ms at 48kHz for low latency
    }

    // FluidSynth handles
    private var settingsHandle: Long = 0
    private var synthHandle: Long = 0
    private var audioDriverHandle: Long = 0
    private var soundFontId: Int = -1

    // Manual AudioTrack rendering (fallback when Oboe driver fails)
    private var useManualRendering = false
    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null
    private val isRendering = AtomicBoolean(false)

    private var isInitialized = false

    override fun initialize(): Boolean {
        Log.d(TAG, "initialize: starting with soundFontPath=$soundFontPath")

        if (soundFontPath.isBlank()) {
            Log.w(TAG, "initialize: soundFontPath is blank")
            return false
        }

        val file = File(soundFontPath)
        if (!file.exists()) {
            Log.w(TAG, "initialize: soundFont file does not exist: $soundFontPath")
            return false
        }

        try {
            // Load native library
            if (!FluidSynthNative.loadLibrary()) {
                Log.e(TAG, "initialize: failed to load native library")
                return false
            }

            // Create settings optimized for low latency
            settingsHandle = FluidSynthNative.newSettings()
            if (settingsHandle == 0L) {
                Log.e(TAG, "initialize: failed to create settings")
                return false
            }

            // Configure for low latency
            FluidSynthNative.setSettingNum(settingsHandle, "synth.sample-rate", SAMPLE_RATE.toDouble())
            FluidSynthNative.setSettingInt(settingsHandle, "synth.polyphony", 64)  // Lower for practice
            FluidSynthNative.setSettingInt(settingsHandle, "audio.periods", 2)
            FluidSynthNative.setSettingInt(settingsHandle, "audio.period-size", FRAMES_PER_BUFFER)

            // Create synth
            synthHandle = FluidSynthNative.newSynth(settingsHandle)
            if (synthHandle == 0L) {
                Log.e(TAG, "initialize: failed to create synth")
                cleanup()
                return false
            }

            // Set gain
            FluidSynthNative.setGain(synthHandle, 0.5f)

            // Enable reverb for better sound
            FluidSynthNative.setReverbOn(synthHandle, true)
            FluidSynthNative.setReverb(synthHandle, 0.4, 0.3, 0.5, 0.4)

            // Load SoundFont
            soundFontId = FluidSynthNative.sfLoad(synthHandle, soundFontPath, true)
            if (soundFontId < 0) {
                Log.e(TAG, "initialize: failed to load SoundFont")
                cleanup()
                return false
            }

            // Create audio driver (Oboe)
            audioDriverHandle = FluidSynthNative.newAudioDriver(settingsHandle, synthHandle)
            if (audioDriverHandle == 0L) {
                Log.w(TAG, "initialize: Oboe audio driver failed, using AudioTrack fallback")
                useManualRendering = true
                if (!initializeAudioTrack()) {
                    Log.e(TAG, "initialize: AudioTrack fallback also failed")
                    cleanup()
                    return false
                }
                startAudioRendering()
            } else {
                useManualRendering = false
            }

            isInitialized = true
            Log.i(TAG, "initialize: SUCCESS, FluidSynth version: ${FluidSynthNative.getVersion()}, manualRendering=$useManualRendering")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "initialize: exception", e)
            cleanup()
            return false
        }
    }

    override fun release() {
        Log.d(TAG, "release: stopping synth")

        stopAudioRendering()
        releaseAudioTrack()
        cleanup()
        isInitialized = false

        Log.d(TAG, "release: complete")
    }

    private fun cleanup() {
        try {
            if (audioDriverHandle != 0L) {
                FluidSynthNative.deleteAudioDriver(audioDriverHandle)
                audioDriverHandle = 0
            }
            if (synthHandle != 0L) {
                // Turn off all sounds first
                for (channel in 0..15) {
                    FluidSynthNative.allSoundOff(synthHandle, channel)
                }
                FluidSynthNative.deleteSynth(synthHandle)
                synthHandle = 0
            }
            if (settingsHandle != 0L) {
                FluidSynthNative.deleteSettings(settingsHandle)
                settingsHandle = 0
            }
            soundFontId = -1
        } catch (e: Exception) {
            Log.e(TAG, "cleanup error", e)
        }
    }

    // ==================== Manual AudioTrack Rendering ====================

    private fun initializeAudioTrack(): Boolean {
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

            Log.d(TAG, "AudioTrack initialized (buffer: $bufferSize)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            audioTrack = null
            return false
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
                        val framesRendered = FluidSynthNative.writeStereoShort(
                            synthHandle, buffer, 0, FRAMES_PER_BUFFER
                        )
                        if (framesRendered > 0) {
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
        }, "FluidSynth-PracticeRender")

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

    // ==================== PracticeSynthesizer Interface ====================

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (!isInitialized || synthHandle == 0L) return
        FluidSynthNative.noteOn(synthHandle, channel, note, velocity)
    }

    override fun noteOff(channel: Int, note: Int) {
        if (!isInitialized || synthHandle == 0L) return
        FluidSynthNative.noteOff(synthHandle, channel, note)
    }

    override fun programChange(channel: Int, program: Int) {
        if (!isInitialized || synthHandle == 0L) return
        FluidSynthNative.programChange(synthHandle, channel, program)
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        if (!isInitialized || synthHandle == 0L) return
        FluidSynthNative.cc(synthHandle, channel, controller, value)
    }

    override fun allNotesOff() {
        if (!isInitialized || synthHandle == 0L) return
        for (channel in 0..15) {
            FluidSynthNative.allNotesOff(synthHandle, channel)
        }
    }

    override fun allSoundOff() {
        if (!isInitialized || synthHandle == 0L) return
        for (channel in 0..15) {
            FluidSynthNative.allSoundOff(synthHandle, channel)
        }
    }

    override fun isReady(): Boolean = isInitialized && synthHandle != 0L

    override fun getName(): String = "FluidSynth"

    /**
     * Get statistics for debugging.
     */
    fun getStats(): String {
        return if (synthHandle != 0L) {
            val voices = FluidSynthNative.getActiveVoiceCount(synthHandle)
            val poly = FluidSynthNative.getPolyphony(synthHandle)
            "voices=$voices/$poly, manualRendering=$useManualRendering"
        } else {
            "not initialized"
        }
    }

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
}
