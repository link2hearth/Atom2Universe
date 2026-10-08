package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.billthefarmer.mididriver.MidiDriver

/**
 * Implementation de PracticeSynthesizer utilisant Sonivox (MidiDriver).
 *
 * Utilise le synthetiseur integre Android pour les sons General MIDI.
 */
class SonivoxPracticeSynth(private val context: Context) : PracticeSynthesizer {

    companion object {
        private const val TAG = "SonivoxPracticeSynth"
    }

    private var midiDriver: MidiDriver? = null
    private var isInitialized = false
    private var isForcedToSpeaker = false

    override fun initialize(): Boolean {
        try {
            midiDriver = MidiDriver.getInstance()
            if (midiDriver == null) {
                android.util.Log.e(TAG, "initialize: MidiDriver.getInstance() returned null")
                return false
            }

            // Always stop and restart to ensure clean audio state
            // This fixes the issue where audio stops working after navigating back
            // and choosing a new track to practice
            val config = midiDriver?.config()
            if (config != null) {
                android.util.Log.d(TAG, "initialize: MidiDriver already running, restarting for clean state")
                try {
                    midiDriver?.stop()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "initialize: stop() failed, continuing anyway", e)
                }
            }

            midiDriver?.start()
            android.util.Log.d(TAG, "initialize: MidiDriver started")

            isInitialized = true
            return true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "initialize: exception", e)
            return false
        }
    }

    override fun release() {
        // Ne PAS stopper le MidiDriver - il est partagé avec le service principal
        // On envoie juste All Sound Off pour couper les notes en cours
        if (isInitialized) {
            allSoundOff()
        }
        isInitialized = false
        // Ne pas mettre midiDriver = null, c'est un singleton partagé
    }

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (!isInitialized) return
        midiDriver?.write(byteArrayOf(
            (0x90 or (channel and 0x0F)).toByte(),
            (note and 0x7F).toByte(),
            (velocity and 0x7F).toByte()
        ))
    }

    override fun noteOff(channel: Int, note: Int) {
        if (!isInitialized) return
        midiDriver?.write(byteArrayOf(
            (0x80 or (channel and 0x0F)).toByte(),
            (note and 0x7F).toByte(),
            0.toByte()
        ))
    }

    override fun programChange(channel: Int, program: Int) {
        if (!isInitialized) return
        midiDriver?.write(byteArrayOf(
            (0xC0 or (channel and 0x0F)).toByte(),
            (program and 0x7F).toByte()
        ))
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        if (!isInitialized) return
        midiDriver?.write(byteArrayOf(
            (0xB0 or (channel and 0x0F)).toByte(),
            (controller and 0x7F).toByte(),
            (value and 0x7F).toByte()
        ))
    }

    override fun allNotesOff() {
        if (!isInitialized) return
        for (channel in 0..15) {
            // CC 123: All Notes Off
            controlChange(channel, 123, 0)
            // CC 64: Sustain Pedal Off
            controlChange(channel, 64, 0)
        }
    }

    override fun allSoundOff() {
        if (!isInitialized) return
        for (channel in 0..15) {
            // CC 120: All Sound Off - coupe immédiatement tous les sons
            controlChange(channel, 120, 0)
            // CC 121: Reset All Controllers - reset sustain, modulation, etc.
            controlChange(channel, 121, 0)
            // CC 64: Sustain Pedal Off - explicite
            controlChange(channel, 64, 0)
            // CC 123: All Notes Off
            controlChange(channel, 123, 0)
        }
    }

    override fun isReady(): Boolean = isInitialized && midiDriver != null

    override fun getName(): String = "Sonivox"

    // ==================== Audio Device Routing ====================

    /**
     * Forces audio output to the built-in speaker.
     * Call this when a USB MIDI device is connected and may be hijacking audio output.
     * @return true if successfully routed to speaker
     */
    fun forceOutputToSpeaker(): Boolean {
        val speakerId = getBuiltInSpeakerId()
        if (speakerId != -1) {
            try {
                val result = midiDriver?.setOutputDevice(speakerId) ?: false
                if (result) {
                    isForcedToSpeaker = true
                }
                android.util.Log.d(TAG, "forceOutputToSpeaker: speakerId=$speakerId result=$result")
                return result
            } catch (e: UnsatisfiedLinkError) {
                // Native function not available in this build - need clean rebuild
                android.util.Log.w(TAG, "setOutputDevice not available in native library - please clean rebuild")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "forceOutputToSpeaker failed", e)
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
                isForcedToSpeaker = false
            }
            android.util.Log.d(TAG, "resetOutputDevice: result=$result")
            return result
        } catch (e: UnsatisfiedLinkError) {
            // Native function not available in this build - need clean rebuild
            android.util.Log.w(TAG, "setOutputDevice not available in native library - please clean rebuild")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "resetOutputDevice failed", e)
        }
        return false
    }

    /**
     * Returns whether audio is currently forced to the speaker.
     */
    fun isOutputForcedToSpeaker(): Boolean = isForcedToSpeaker

    /**
     * Gets the device ID of the built-in speaker.
     * @return device ID or -1 if not found
     */
    private fun getBuiltInSpeakerId(): Int {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return -1

            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return device.id
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "getBuiltInSpeakerId failed", e)
        }
        return -1
    }
}
