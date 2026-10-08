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
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lecteur de fichiers MIDI avec support complet pour:
 * - Play / Pause / Resume
 * - Seek (navigation à n'importe quelle position)
 * - Récupération de la position et durée
 *
 * Remplace MidiPlaybackEngine/MidiProcessor qui ne supportait pas le seek.
 */
class MidiFilePlayer {

    companion object {
        private const val DEFAULT_TEMPO_BPM = 120
        private const val MICROSECONDS_PER_MINUTE = 60_000_000L

        // Rate limiting pour éviter surcharge (RUSH E et autres fichiers intenses)
        private const val RATE_LIMIT_WINDOW_MS = 100L      // Fenêtre de 100ms
        private const val MAX_EVENTS_PER_WINDOW = 80       // Max 80 événements par 100ms (800/sec)
        private const val MAX_NOTES_PER_WINDOW = 50        // Max 50 notes par 100ms (500/sec)
    }

    // État du player
    enum class State {
        IDLE,
        LOADED,
        PLAYING,
        PAUSED,
        STOPPED
    }

    // Événement MIDI avec timestamp en millisecondes
    data class TimedMidiEvent(
        val timestampMs: Long,
        val event: MidiEvent,
        val midiBytes: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TimedMidiEvent
            if (timestampMs != other.timestampMs) return false
            if (event != other.event) return false
            if (midiBytes != null) {
                if (other.midiBytes == null) return false
                if (!midiBytes.contentEquals(other.midiBytes)) return false
            } else if (other.midiBytes != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = timestampMs.hashCode()
            result = 31 * result + event.hashCode()
            result = 31 * result + (midiBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    // Liste des événements triés par timestamp
    @Volatile
    private var events: List<TimedMidiEvent> = emptyList()
    private var durationMs: Long = 0L

    // Position de lecture
    private val currentPositionMs = AtomicLong(0L)
    @Volatile
    private var currentEventIndex = 0

    // Lock pour synchroniser l'accès à currentEventIndex et events entre threads
    private val eventLock = Any()

    // Thread de lecture
    private var playbackThread: Thread? = null
    private val isPlaying = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // État
    private var state = State.IDLE

    // Callbacks (volatile pour visibilité entre threads)
    @Volatile var onMidiEvent: ((ByteArray) -> Unit)? = null
    @Volatile var onPositionChanged: ((Long, Long) -> Unit)? = null  // (currentMs, durationMs)
    @Volatile var onPlaybackStarted: (() -> Unit)? = null
    @Volatile var onPlaybackCompleted: (() -> Unit)? = null
    @Volatile var onError: ((String) -> Unit)? = null

    /**
     * Charge un fichier MIDI et prépare tous les événements
     */
    fun loadFile(file: File): Boolean {
        if (!file.exists()) {
            onError?.invoke("File not found")
            return false
        }

        try {
            stop()

            FileInputStream(file).use { inputStream ->
                val midiFile = MidiFile(inputStream)

                // Convertir tous les événements en liste triée avec timestamps en ms
                val newEvents = convertToTimedEvents(midiFile)
                val newDuration = if (newEvents.isNotEmpty()) newEvents.last().timestampMs else 0L

                synchronized(eventLock) {
                    events = newEvents
                    durationMs = newDuration
                    currentPositionMs.set(0)
                    currentEventIndex = 0
                }
                state = State.LOADED

                return true
            }
        } catch (e: Exception) {
            onError?.invoke("Failed to load: ${e.message}")
            state = State.IDLE
            return false
        }
    }

    /**
     * Convertit les événements MIDI en liste triée avec timestamps en millisecondes
     */
    private fun convertToTimedEvents(midiFile: MidiFile): List<TimedMidiEvent> {
        val resolution = midiFile.resolution.toLong()
        val allEvents = mutableListOf<TimedMidiEvent>()

        // Collecter tous les événements de toutes les tracks
        val rawEvents = mutableListOf<Pair<Long, MidiEvent>>() // tick, event

        for (track in midiFile.tracks) {
            for (event in track.events) {
                rawEvents.add(event.tick to event)
            }
        }

        // Trier par tick
        rawEvents.sortBy { it.first }

        // Convertir ticks en millisecondes
        // On doit tracker les changements de tempo
        var microsecondsPerBeat = MICROSECONDS_PER_MINUTE / DEFAULT_TEMPO_BPM // Default 120 BPM
        var lastTick = 0L
        var currentTimeUs = 0L // Temps en microsecondes

        for ((tick, event) in rawEvents) {
            // Calculer le temps écoulé depuis le dernier événement
            val deltaTicks = tick - lastTick
            if (deltaTicks > 0 && resolution > 0) {
                currentTimeUs += (deltaTicks * microsecondsPerBeat) / resolution
            }
            lastTick = tick

            // Mettre à jour le tempo si c'est un événement Tempo
            if (event is Tempo) {
                // BUG FIX 3.26: Valider les valeurs de tempo pour éviter les valeurs aberrantes
                // Tempo valide: 20 BPM (3_000_000 µs/beat) à 300 BPM (200_000 µs/beat)
                val newTempo = event.mpqn.toLong()
                if (newTempo in 200_000L..3_000_000L) {
                    microsecondsPerBeat = newTempo
                }
                // Sinon, garder le tempo précédent
            }

            // Convertir en bytes MIDI si c'est un événement jouable
            val midiBytes = convertEventToBytes(event)

            val timestampMs = currentTimeUs / 1000

            allEvents.add(TimedMidiEvent(
                timestampMs = timestampMs,
                event = event,
                midiBytes = midiBytes
            ))
        }

        return allEvents
    }

    /**
     * Convertit un MidiEvent en bytes pour MidiDriver
     */
    private fun convertEventToBytes(event: MidiEvent): ByteArray? {
        return try {
            when (event) {
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
                is Controller -> byteArrayOf(
                    (0xB0 or event.channel).toByte(),
                    event.controllerType.toByte(),
                    event.value.toByte()
                )
                is PitchBend -> {
                    val bendValue = event.bendAmount
                    byteArrayOf(
                        (0xE0 or event.channel).toByte(),
                        (bendValue and 0x7F).toByte(),
                        ((bendValue shr 7) and 0x7F).toByte()
                    )
                }
                is ChannelAftertouch -> byteArrayOf(
                    (0xD0 or event.channel).toByte(),
                    event.amount.toByte()
                )
                else -> null // Meta-events, etc.
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Démarre la lecture
     */
    fun play() {
        if (state != State.LOADED && state != State.PAUSED && state != State.STOPPED) {
            return
        }

        if (isPlaying.get()) {
            return
        }

        isPlaying.set(true)
        isPaused.set(false)
        state = State.PLAYING

        playbackThread = Thread(PlaybackRunnable()).apply {
            name = "MidiFilePlayer-Playback"
            start()
        }

        onPlaybackStarted?.invoke()
    }

    /**
     * Met en pause la lecture (garde la position)
     */
    fun pause() {
        if (!isPlaying.get()) {
            return
        }

        isPaused.set(true)
        isPlaying.set(false)
        state = State.PAUSED

        // Le thread va s'arrêter naturellement en vérifiant isPlaying
        playbackThread?.interrupt()
        playbackThread = null
    }

    /**
     * Reprend la lecture depuis la position actuelle
     */
    fun resume() {
        if (state != State.PAUSED) {
            play()
            return
        }

        play()
    }

    /**
     * Arrête la lecture et remet à zéro
     */
    fun stop() {
        isPlaying.set(false)
        isPaused.set(false)

        playbackThread?.interrupt()
        try {
            playbackThread?.join(500)
        } catch (_: InterruptedException) {
            // OK
        }
        playbackThread = null

        currentPositionMs.set(0)
        synchronized(eventLock) {
            currentEventIndex = 0
        }
        state = if (events.isNotEmpty()) State.STOPPED else State.IDLE
    }

    /**
     * Seek à une position donnée (en millisecondes)
     */
    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceIn(0, durationMs)

        val wasPlaying = isPlaying.get()

        // Pause si en cours de lecture
        if (wasPlaying) {
            isPlaying.set(false)
            // BUG FIX 3.11: Thread-safe join avec capture locale pour éviter les null-checks fragiles
            val thread = playbackThread
            thread?.interrupt()
            if (thread != null && thread.isAlive) {
                try {
                    thread.join(200)
                } catch (_: InterruptedException) {
                    // Restaurer le flag d'interruption
                    Thread.currentThread().interrupt()
                }
            }
            playbackThread = null
            // IMPORTANT: Mettre l'état en PAUSED pour que play() fonctionne
            state = State.PAUSED
        }

        // Trouver l'index de l'événement le plus proche (synchronisé)
        synchronized(eventLock) {
            currentEventIndex = findEventIndexAtPosition(targetMs)
        }
        currentPositionMs.set(targetMs)

        // Notifier la nouvelle position
        onPositionChanged?.invoke(targetMs, durationMs)

        // Reprendre si on jouait
        if (wasPlaying) {
            play()
        } else {
            state = State.PAUSED
        }
    }

    /**
     * Trouve l'index du premier événement à ou après la position donnée
     */
    private fun findEventIndexAtPosition(positionMs: Long): Int {
        // Recherche binaire pour trouver le bon index
        var low = 0
        var high = events.size - 1

        while (low < high) {
            val mid = (low + high) / 2
            if (events[mid].timestampMs < positionMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        return low.coerceIn(0, events.size - 1)
    }

    /**
     * Récupère la position actuelle en millisecondes
     */
    fun getCurrentPositionMs(): Long = currentPositionMs.get()

    /**
     * Récupère la durée totale en millisecondes
     */
    fun getDurationMs(): Long = durationMs

    /**
     * Vérifie si la lecture est en cours
     */
    fun isPlaying(): Boolean = isPlaying.get()

    /**
     * Vérifie si en pause
     */
    fun isPaused(): Boolean = isPaused.get()

    /**
     * Récupère l'état actuel
     */
    @Suppress("unused")
    fun getState(): State = state

    /**
     * Libère les ressources
     */
    fun release() {
        stop()
        events = emptyList()
        onMidiEvent = null
        onPositionChanged = null
        onPlaybackStarted = null
        onPlaybackCompleted = null
        onError = null
        state = State.IDLE
    }

    /**
     * Runnable de lecture qui dispatch les événements MIDI en temps réel
     * Inclut un rate limiting pour éviter la surcharge avec des fichiers intenses (RUSH E, etc.)
     */
    private inner class PlaybackRunnable : Runnable {
        override fun run() {
            val startTimeNs = System.nanoTime()
            val startPositionMs = currentPositionMs.get()
            var lastPositionUpdateMs = startPositionMs

            // Rate limiting
            var windowStartMs = startPositionMs
            var eventsInWindow = 0
            var notesInWindow = 0
            var skippedEvents = 0

            try {
                while (isPlaying.get()) {
                    // Lecture synchronisée de l'index et de l'événement
                    val (_, event) = synchronized(eventLock) {
                        val idx = currentEventIndex
                        val evts = events
                        if (idx >= evts.size) return@synchronized Pair(-1, null)
                        Pair(idx, evts[idx])
                    }
                    if (event == null) break

                    // Calculer le temps écoulé depuis le début de cette session de lecture
                    val elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000
                    val currentTimeMs = startPositionMs + elapsedMs

                    // Mettre à jour la position
                    currentPositionMs.set(currentTimeMs)

                    // Reset rate limiting window si nécessaire
                    if (currentTimeMs - windowStartMs >= RATE_LIMIT_WINDOW_MS) {
                        windowStartMs = currentTimeMs
                        eventsInWindow = 0
                        notesInWindow = 0
                        skippedEvents = 0
                    }

                    // Attendre si l'événement est dans le futur
                    val waitMs = event.timestampMs - currentTimeMs
                    if (waitMs > 0) {
                        try {
                            Thread.sleep(waitMs.coerceAtMost(100)) // Max 100ms pour rester réactif
                            continue // Re-vérifier après le sleep
                        } catch (_: InterruptedException) {
                            if (!isPlaying.get()) break
                        }
                    }

                    // Rate limiting: vérifier si on doit envoyer cet événement
                    val isNoteEvent = event.event is NoteOn || event.event is NoteOff
                    val shouldSend = when {
                        // Toujours envoyer les événements de contrôle (Program Change, Controller, etc.)
                        !isNoteEvent -> true
                        // Limiter les notes par fenêtre
                        notesInWindow >= MAX_NOTES_PER_WINDOW -> false
                        // Limiter les événements totaux par fenêtre
                        eventsInWindow >= MAX_EVENTS_PER_WINDOW -> false
                        else -> true
                    }

                    if (shouldSend) {
                        // Envoyer l'événement MIDI
                        event.midiBytes?.let { bytes ->
                            onMidiEvent?.invoke(bytes)
                        }
                        eventsInWindow++
                        if (isNoteEvent) notesInWindow++
                    } else {
                        skippedEvents++
                    }

                    // Incrémentation synchronisée
                    synchronized(eventLock) {
                        currentEventIndex++
                    }

                    // Notifier la position (~2 fois par seconde)
                    if (currentTimeMs - lastPositionUpdateMs >= 500) {
                        lastPositionUpdateMs = currentTimeMs
                        onPositionChanged?.invoke(currentTimeMs, durationMs)
                    }
                }

                // Fin de lecture
                val isAtEnd = synchronized(eventLock) {
                    currentEventIndex >= events.size
                }
                if (isPlaying.get() && isAtEnd) {
                    currentPositionMs.set(durationMs)
                    isPlaying.set(false)
                    state = State.STOPPED
                    onPlaybackCompleted?.invoke()
                }

            } catch (e: Exception) {
                onError?.invoke("Playback error: ${e.message}")
            }
        }
    }

    /**
     * Envoie les ProgramChange pour restaurer l'état des instruments à une position
     * Utile après un seek pour avoir les bons sons
     */
    fun sendProgramChangesUpToPosition(positionMs: Long) {
        val programsByChannel = mutableMapOf<Int, ByteArray>()

        // Parcourir les événements jusqu'à la position pour collecter les derniers ProgramChange
        for (event in events) {
            if (event.timestampMs > positionMs) break

            if (event.event is ProgramChange && event.midiBytes != null) {
                val channel = (event.midiBytes[0].toInt() and 0x0F)
                programsByChannel[channel] = event.midiBytes
            }
        }

        // Envoyer les ProgramChange
        programsByChannel.values.forEach { bytes ->
            onMidiEvent?.invoke(bytes)
        }
    }
}
