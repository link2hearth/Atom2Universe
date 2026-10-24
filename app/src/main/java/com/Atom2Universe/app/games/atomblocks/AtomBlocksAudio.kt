package com.Atom2Universe.app.games.atomblocks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.crypto.sound.SharedSonivoxDriver

/**
 * Original, quiet Sonivox score and musical feedback. All public calls belong to the UI thread.
 * Channels 12–15 are separate from the shared clicker/fusion channels (0–2 and 9).
 * Owns a focus request, never the process-wide driver's start/stop or another module's focus.
 */
@MainThread
class AtomBlocksAudio(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val effectsToken = Any()
    private val musicToken = Any()
    private val soundingNotes = mutableMapOf<Int, Long>()
    private var noteSerial = 0L
    private var effectsGeneration = 0
    private var musicGeneration = 0
    private var active = false
    private var released = false
    private var acquired = false
    private var focusRequested = false
    private var hasFocus = false
    private var receiverRegistered = false
    private var musicPlaying = false

    var soundEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) stopEffects()
            updateEnabledState(restart = value)
        }

    var musicEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) stopMusic()
            updateEnabledState(restart = value)
        }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // Notifications pause these very quiet notes instead of silently auto-ducking the driver.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (focusRequested && active && !released) {
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        startPlayback()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        hasFocus = false
                        stopPlayback()
                        abandonFocus()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        hasFocus = false
                        stopPlayback()
                    }
                }
            }
        }, handler)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Do not switch a headphone session to the loudspeaker without a new resume/toggle.
                stopPlayback()
                abandonFocus()
            }
        }
    }

    fun resume() {
        if (released) return
        active = true
        ensureFocus()
    }

    fun pause() {
        active = false
        stopPlayback()
        abandonFocus()
    }

    fun release() {
        if (released) return
        pause()
        released = true
    }

    fun select() = effect {
        note(EFFECTS, 84, 30, 55)
    }

    fun place() = effect {
        note(EFFECTS, 60, 58, 95)
        note(EFFECTS, 67, 40, 100, 30)
    }

    fun clear(lines: Int, combo: Int, allClear: Boolean) = effect {
        stopEffects()
        val lift = (combo - 1).coerceIn(0, 4) * 2
        val pitches = if (allClear) intArrayOf(72, 76, 79, 84, 88)
            else intArrayOf(67, 71, 74, 79).take((lines + 1).coerceIn(2, 4)).toIntArray()
        pitches.forEachIndexed { index, pitch ->
            note(EFFECTS, pitch + lift, 62 + index * 3, if (index == pitches.lastIndex) 370 else 135, index * 80L)
        }
        if (allClear) {
            note(EFFECTS, 72 + lift, 46, 520, 350)
            note(EFFECTS, 79 + lift, 42, 520, 350)
        }
    }

    fun win() = effect {
        stopEffects()
        intArrayOf(60, 64, 67, 72, 76).forEachIndexed { index, pitch ->
            note(EFFECTS, pitch, 60 + index * 3, 160, index * 115L)
        }
        intArrayOf(72, 76, 79, 84).forEach { note(EFFECTS, it, 62, 780, 620) }
    }

    /** A warm resolving phrase; being stuck is an invitation to try again. */
    fun lose() = effect {
        stopEffects()
        note(EFFECTS, 72, 49, 230)
        note(EFFECTS, 67, 44, 270, 210)
        note(EFFECTS, 64, 41, 560, 440)
        note(EFFECTS, 60, 35, 620, 440)
    }

    fun undo() = effect {
        note(EFFECTS, 76, 42, 100)
        note(EFFECTS, 72, 36, 130, 80)
    }

    fun rescue() = effect {
        stopEffects()
        intArrayOf(64, 67, 72, 79).forEachIndexed { index, pitch ->
            note(EFFECTS, pitch, 49 + index * 4, 210, index * 95L)
        }
    }

    private inline fun effect(block: () -> Unit) {
        if (active && acquired && hasFocus && soundEnabled && !released) block()
    }

    private fun updateEnabledState(restart: Boolean) {
        if (released || !active) return
        if (!soundEnabled && !musicEnabled) {
            stopPlayback()
            abandonFocus()
        } else if (restart) ensureFocus()
    }

    private fun ensureFocus() {
        if (released || !active || (!soundEnabled && !musicEnabled)) return
        if (!focusRequested) {
            val manager = audioManager ?: return
            focusRequested = true
            val granted = runCatching { manager.requestAudioFocus(focusRequest) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
            hasFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasFocus) {
                abandonFocus()
                return
            }
        }
        if (hasFocus) startPlayback()
    }

    private fun abandonFocus() {
        val requested = focusRequested
        focusRequested = false
        hasFocus = false
        if (requested) audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private fun startPlayback() {
        if (!active || released || !hasFocus || (!soundEnabled && !musicEnabled)) return
        if (!acquired) {
            SharedSonivoxDriver.acquire {
                acquired = true
                configureChannel(EFFECTS, 12, 88) // Marimba: tactile, rounded feedback.
                configureChannel(MELODY, 11, 48) // Vibraphone.
                configureChannel(HARMONY, 4, 34) // Electric piano.
                configureChannel(BASS, 32, 42) // Acoustic bass.
            }
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        if (musicEnabled && !musicPlaying) {
            musicPlaying = true
            musicStep(0)
        }
    }

    private fun stopPlayback() {
        stopEffects()
        stopMusic()
        if (acquired) {
            acquired = false
            SharedSonivoxDriver.release()
        }
        if (receiverRegistered) {
            appContext.unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
    }

    private fun stopEffects() {
        effectsGeneration++
        handler.removeCallbacksAndMessages(effectsToken)
        silenceChannel(EFFECTS)
    }

    private fun stopMusic() {
        musicGeneration++
        musicPlaying = false
        handler.removeCallbacksAndMessages(musicToken)
        silenceChannel(MELODY)
        silenceChannel(HARMONY)
        silenceChannel(BASS)
    }

    /** Eight original, unhurried measures in C major; no percussion or escalating pressure. */
    private fun musicStep(step: Int) {
        if (!canPlay(MELODY) || !musicPlaying) return
        val bar = (step / 8) % CHORDS.size
        val eighth = step % 8
        val chord = CHORDS[bar]
        if (eighth == 0) {
            chord.forEachIndexed { index, pitch -> note(HARMONY, pitch, 35, 1900, index * 28L) }
            note(BASS, BASS_NOTES[bar], 40, 1200)
        }
        if (eighth == 4) note(BASS, BASS_NOTES[bar] + 7, 31, 950)
        val pitch = MELODIES[bar][eighth]
        if (pitch >= 0) note(MELODY, pitch, if (eighth % 2 == 0) 43 else 34, 450)
        schedule(MELODY, EIGHTH_MS) { musicStep((step + 1) % 64) }
    }

    private fun canPlay(channel: Int): Boolean =
        active && !released && acquired && hasFocus && if (channel == EFFECTS) soundEnabled else musicEnabled

    private fun note(channel: Int, pitch: Int, velocity: Int, durationMs: Int, delayMs: Long = 0) {
        if (!canPlay(channel)) return
        if (delayMs > 0) {
            schedule(channel, delayMs) { note(channel, pitch, velocity, durationMs) }
            return
        }
        val key = channel * 128 + pitch
        if (soundingNotes.containsKey(key)) send(0x80 or channel, pitch, 0)
        val serial = ++noteSerial
        soundingNotes[key] = serial
        send(0x90 or channel, pitch, velocity)
        schedule(channel, durationMs.toLong()) {
            // An old note-off must not cut a more recent repetition of the same pitch.
            if (soundingNotes[key] == serial) {
                soundingNotes.remove(key)
                send(0x80 or channel, pitch, 0)
            }
        }
    }

    private fun schedule(channel: Int, delayMs: Long, block: () -> Unit) {
        val effects = channel == EFFECTS
        val generation = if (effects) effectsGeneration else musicGeneration
        handler.postAtTime({
            val current = if (effects) effectsGeneration else musicGeneration
            if (generation == current && canPlay(channel)) block()
        }, if (effects) effectsToken else musicToken, SystemClock.uptimeMillis() + delayMs)
    }

    private fun silenceChannel(channel: Int) {
        val keys = soundingNotes.keys.filter { it / 128 == channel }
        if (acquired) {
            keys.forEach { send(0x80 or channel, it % 128, 0) }
            send(0xB0 or channel, 64, 0)
            send(0xB0 or channel, 123, 0)
            send(0xB0 or channel, 120, 0)
        }
        keys.forEach(soundingNotes::remove)
    }

    private fun configureChannel(channel: Int, program: Int, volume: Int) {
        SharedSonivoxDriver.queueEvent(byteArrayOf((0xC0 or channel).toByte(), program.toByte()))
        send(0xB0 or channel, 7, volume)
        send(0xB0 or channel, 10, 64)
        send(0xB0 or channel, 64, 0)
        send(0xB0 or channel, 91, 12)
        send(0xB0 or channel, 93, 0)
    }

    private fun send(status: Int, first: Int, second: Int) {
        SharedSonivoxDriver.queueEvent(byteArrayOf(status.toByte(), first.toByte(), second.toByte()))
    }

    private companion object {
        const val EFFECTS = 12
        const val MELODY = 13
        const val HARMONY = 14
        const val BASS = 15
        const val EIGHTH_MS = 320L
        val BASS_NOTES = intArrayOf(36, 33, 41, 43, 38, 40, 41, 43)
        val CHORDS = arrayOf(
            intArrayOf(60, 64, 67), intArrayOf(57, 60, 64),
            intArrayOf(57, 60, 65), intArrayOf(59, 62, 67),
            intArrayOf(57, 62, 65), intArrayOf(55, 59, 64),
            intArrayOf(57, 60, 65), intArrayOf(55, 60, 62),
        )
        val MELODIES = arrayOf(
            intArrayOf(76, -1, 79, 76, 74, -1, 72, -1),
            intArrayOf(72, -1, 76, -1, 79, 76, 72, -1),
            intArrayOf(77, -1, 76, 72, 69, -1, 72, -1),
            intArrayOf(74, -1, 79, -1, 76, 74, 71, -1),
            intArrayOf(74, -1, 77, 76, 74, -1, 69, -1),
            intArrayOf(71, -1, 76, -1, 79, 76, 71, -1),
            intArrayOf(72, -1, 77, 76, 72, -1, 69, -1),
            intArrayOf(74, -1, 72, -1, 67, -1, -1, -1),
        )
    }
}
