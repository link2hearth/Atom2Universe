package com.Atom2Universe.app.midi.service

import com.leff.midi.MidiFile
import com.leff.midi.event.MidiEvent
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.Controller
import com.leff.midi.event.PitchBend
import com.leff.midi.event.ChannelAftertouch
import com.leff.midi.event.meta.Tempo
import com.leff.midi.util.MidiProcessor
import java.io.File
import java.io.FileInputStream

/**
 * Engine de playback MIDI utilisant android-midi-lib
 *
 * Parse les fichiers MIDI Standard (SMF) et dispatch les événements
 * en temps réel avec timing précis
 */
class MidiPlaybackEngine {

    private var midiFile: MidiFile? = null
    private var midiProcessor: MidiProcessor? = null
    private val eventListener = MidiEventDispatcherListener()

    // Callbacks
    var onMidiEvent: ((ByteArray) -> Unit)? = null
    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackStopped: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onEventCount: ((Int) -> Unit)? = null  // Callback pour compter les événements
    var onPositionChanged: ((Long, Long) -> Unit)? = null  // (currentMs, durationMs)

    private var totalEventCount = 0

    // Position tracking
    private var currentPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var isPaused: Boolean = false
    private var pausedAtMs: Long = 0L

    /**
     * Charge un fichier MIDI
     */
    fun loadMidiFile(file: File): Boolean {
        // Vérifier que le fichier existe
        if (!file.exists()) {
            onError?.invoke("File not found")
            return false
        }

        // Vérifier que le fichier n'est pas vide
        if (file.length() == 0L) {
            onError?.invoke("MIDI file is empty")
            return false
        }

        return try {
            // Stopper tout playback en cours
            stop()

            // Libérer l'ancien processor
            midiProcessor = null
            midiFile = null

            // Reset le compteur d'événements
            totalEventCount = 0

            FileInputStream(file).use { inputStream ->
                midiFile = MidiFile(inputStream)

                // Calculer la durée du fichier MIDI
                durationMs = calculateDurationMs(midiFile!!)

                // Reset position tracking
                currentPositionMs = 0L
                pausedAtMs = 0L
                isPaused = false

                // Créer le processor pour dispatch en temps réel
                midiProcessor = MidiProcessor(midiFile)

                // CRITIQUE: Enregistrer le listener pour CHAQUE type d'événement spécifique
                // Sinon les événements ne sont jamais dispatchés!
                midiProcessor?.registerEventListener(eventListener, NoteOn::class.java)
                midiProcessor?.registerEventListener(eventListener, NoteOff::class.java)
                midiProcessor?.registerEventListener(eventListener, ProgramChange::class.java)
                midiProcessor?.registerEventListener(eventListener, Controller::class.java)
                midiProcessor?.registerEventListener(eventListener, PitchBend::class.java)
                midiProcessor?.registerEventListener(eventListener, ChannelAftertouch::class.java)
                midiProcessor?.registerEventListener(eventListener, Tempo::class.java)

                true
            }
        } catch (e: java.io.FileNotFoundException) {
            onError?.invoke("MIDI file not found")
            false
        } catch (e: java.io.IOException) {
            onError?.invoke("Failed to read MIDI file: ${e.message}")
            false
        } catch (e: Exception) {
            onError?.invoke("Invalid MIDI file: ${e.message}")
            false
        }
    }

    /**
     * Démarre le playback
     */
    fun start() {
        if (midiProcessor == null) {
            onError?.invoke("No MIDI file loaded")
            return
        }

        try {
            // Si déjà en cours, ne pas redémarrer
            if (midiProcessor?.isRunning == true) {
                return
            }

            isPaused = false
            currentPositionMs = 0L

            midiProcessor?.start()
            onPlaybackStarted?.invoke()
        } catch (e: Exception) {
            onError?.invoke("Playback start failed: ${e.message}")
        }
    }

    /**
     * Met en pause le playback
     * NOTE: android-midi-lib ne supporte pas le vrai pause/resume
     * On arrête simplement le playback
     */
    fun pause() {
        if (midiProcessor == null || midiProcessor?.isRunning != true) {
            return
        }

        try {
            pausedAtMs = currentPositionMs
            isPaused = true
            midiProcessor?.stop()
        } catch (e: Exception) { }
    }

    /**
     * Reprend le playback
     * NOTE: Redémarre depuis le début car MidiProcessor ne supporte pas le seek
     */
    fun resume() {
        val processor = midiProcessor
        if (processor == null) {
            onError?.invoke("No MIDI file loaded")
            return
        }

        if (!isPaused || processor.isRunning) {
            return
        }

        try {
            // Reset le processor
            processor.reset()
            isPaused = false
            currentPositionMs = 0L

            // Redémarrer
            processor.start()
            onPlaybackStarted?.invoke()
        } catch (e: Exception) {
            onError?.invoke("Playback resume failed: ${e.message}")
        }
    }

    /**
     * Arrête le playback
     */
    fun stop() {
        if (midiProcessor == null) {
            return
        }

        try {
            if (midiProcessor?.isRunning == true) {
                midiProcessor?.stop()
            }
        } catch (e: Exception) { }
    }

    /**
     * Reset le playback au début
     */
    fun reset() {
        try {
            midiProcessor?.reset()
        } catch (e: Exception) { }
    }

    /**
     * Vérifie si le playback est en cours
     */
    fun isPlaying(): Boolean {
        return midiProcessor?.isRunning ?: false
    }

    /**
     * Libère les ressources
     */
    fun release() {
        try {
            stop()
        } catch (e: Exception) { }

        try {
            midiProcessor = null
            midiFile = null
            onMidiEvent = null
            onPlaybackStarted = null
            onPlaybackStopped = null
            onError = null
            onPositionChanged = null
        } catch (e: Exception) { }
    }

    /**
     * Récupère la position actuelle en millisecondes
     */
    fun getCurrentPositionMs(): Long = currentPositionMs

    /**
     * Récupère la durée totale en millisecondes
     */
    fun getDurationMs(): Long = durationMs

    /**
     * Vérifie si le playback est en pause
     */
    fun isPaused(): Boolean = isPaused

    /**
     * Calcule la durée totale du fichier MIDI en millisecondes
     */
    private fun calculateDurationMs(midi: MidiFile): Long {
        try {
            // Parcourir toutes les tracks pour trouver l'événement le plus tardif
            var maxTick: Long = 0
            var lastTempo: Long = 500000  // Default tempo: 120 BPM = 500000 µs/beat

            for (track in midi.tracks) {
                for (event in track.events) {
                    if (event.tick > maxTick) {
                        maxTick = event.tick
                    }
                    // Capturer le dernier tempo (simplifié - ne gère pas les changements multiples)
                    if (event is Tempo) {
                        lastTempo = event.mpqn.toLong()
                    }
                }
            }

            // Convertir ticks en millisecondes
            // Formula: ms = (ticks / resolution) * (tempo_µs / 1000)
            val resolution = midi.resolution.toLong()
            if (resolution <= 0) {
                return maxTick / 2  // Estimation grossière
            }

            // Calcul simplifié (assume tempo constant)
            // ms = ticks * (µs_per_beat / 1000) / ticks_per_beat
            val durationMs = (maxTick * lastTempo) / (resolution * 1000)

            return durationMs
        } catch (e: Exception) {
            return 0L
        }
    }

    /**
     * Listener qui reçoit les événements MIDI et les convertit en bytes
     */
    private inner class MidiEventDispatcherListener : com.leff.midi.util.MidiEventListener {

        private var lastPositionUpdateMs: Long = 0

        override fun onStart(fromBeginning: Boolean) {
            lastPositionUpdateMs = 0
        }

        override fun onEvent(event: MidiEvent, ms: Long) {
            totalEventCount++

            // Mettre à jour la position
            currentPositionMs = ms

            // Notifier après 10 événements
            if (totalEventCount == 10) {
                onEventCount?.invoke(totalEventCount)
            }

            // Notifier la position (throttled à ~2 fois par seconde pour réduire l'overhead)
            if (ms - lastPositionUpdateMs >= 500 || ms < lastPositionUpdateMs) {
                lastPositionUpdateMs = ms
                onPositionChanged?.invoke(ms, durationMs)
            }

            // Convertir l'événement MIDI en bytes et l'envoyer
            val midiBytes = convertEventToBytes(event)
            if (midiBytes != null && midiBytes.isNotEmpty()) {
                onMidiEvent?.invoke(midiBytes)
            }
        }

        override fun onStop(finished: Boolean) {
            onPlaybackStopped?.invoke(finished)
        }

        /**
         * Convertit un MidiEvent en byte array pour MidiDriver
         */
        private fun convertEventToBytes(event: MidiEvent): ByteArray? {
            return try {
                when (event) {
                    is NoteOn -> {
                        // Note On: 0x90 + channel, note, velocity
                        byteArrayOf(
                            (0x90 or event.channel).toByte(),
                            event.noteValue.toByte(),
                            event.velocity.toByte()
                        )
                    }

                    is NoteOff -> {
                        // Note Off: 0x80 + channel, note, velocity
                        byteArrayOf(
                            (0x80 or event.channel).toByte(),
                            event.noteValue.toByte(),
                            event.velocity.toByte()
                        )
                    }

                    is ProgramChange -> {
                        // Program Change: 0xC0 + channel, program
                        byteArrayOf(
                            (0xC0 or event.channel).toByte(),
                            event.programNumber.toByte()
                        )
                    }

                    is Controller -> {
                        // Control Change: 0xB0 + channel, controller, value
                        byteArrayOf(
                            (0xB0 or event.channel).toByte(),
                            event.controllerType.toByte(),
                            event.value.toByte()
                        )
                    }

                    is PitchBend -> {
                        // Pitch Bend: 0xE0 + channel, LSB, MSB
                        val bendValue = event.bendAmount
                        byteArrayOf(
                            (0xE0 or event.channel).toByte(),
                            (bendValue and 0x7F).toByte(),
                            ((bendValue shr 7) and 0x7F).toByte()
                        )
                    }

                    is ChannelAftertouch -> {
                        // Channel Aftertouch: 0xD0 + channel, pressure
                        byteArrayOf(
                            (0xD0 or event.channel).toByte(),
                            event.amount.toByte()
                        )
                    }

                    is Tempo -> {
                        // Tempo est un meta-event, ne génère pas de bytes MIDI
                        // Le MidiProcessor gère automatiquement le tempo
                        null
                    }

                    else -> {
                        // Autres événements (meta-events, SysEx, etc.)
                        // On les ignore pour l'instant
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MidiPlaybackEngine"
    }
}
