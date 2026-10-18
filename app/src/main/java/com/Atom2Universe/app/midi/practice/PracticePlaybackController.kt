package com.Atom2Universe.app.midi.practice

import com.leff.midi.MidiFile
import com.leff.midi.event.MidiEvent
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.Controller
import com.leff.midi.event.meta.Tempo
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * Controleur de lecture pour le mode pratique piano
 *
 * Caracteristiques:
 * - Controle de vitesse (tempo) de 0.25x a 2.0x
 * - Filtrage par canal cible
 * - Callbacks pour visualisation (notes tombantes)
 * - Independant du MidiPlaybackService principal
 */
class PracticePlaybackController {

    companion object {
        private const val TAG = "PracticePlayback"
        private const val DEFAULT_TEMPO_BPM = 120
        private const val MICROSECONDS_PER_MINUTE = 60_000_000L

        // Limites de vitesse
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 2.0f
        const val DEFAULT_SPEED = 1.0f
    }

    // Etat du player
    enum class State {
        IDLE,
        LOADED,
        PLAYING,
        PAUSED,
        STOPPED
    }

    // Evenement MIDI avec timestamp
    private data class TimedEvent(
        val timestampMs: Long,
        val event: MidiEvent,
        val channel: Int,
        val midiBytes: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TimedEvent
            return timestampMs == other.timestampMs &&
                    event == other.event &&
                    channel == other.channel &&
                    midiBytes.contentEquals(other.midiBytes)
        }

        override fun hashCode(): Int {
            var result = timestampMs.hashCode()
            result = 31 * result + event.hashCode()
            result = 31 * result + channel
            result = 31 * result + (midiBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    // Liste des evenements tries
    private var allEvents: List<TimedEvent> = emptyList()
    private var targetChannelNotes: List<ScheduledNote> = emptyList()
    private var durationMs: Long = 0L

    // Configuration
    private var targetChannel: Int = 0
    private var secondTargetChannel: Int = -1  // Pour le mode deux mains
    private var isTwoHandsMode: Boolean = false
    private var leftHandChannel: Int = -1
    private var rightHandChannel: Int = -1
    private var speedMultiplier: Float = DEFAULT_SPEED

    // Horloge partagée pour synchronisation audio/visuel
    val sharedClock = PlaybackClock()

    // Position de lecture (dépréciée, utiliser sharedClock.getCurrentPositionMs())
    private val currentPositionMs = AtomicLong(0L)
    private var currentEventIndex = 0

    // Thread de lecture
    private var playbackThread: Thread? = null
    private val isPlaying = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // Etat
    private var state = State.IDLE

    // Synthesizer pour le son (Sonivox ou SF2)
    private var synthesizer: PracticeSynthesizer? = null

    // Options
    private var _playAccompaniment: Boolean = true
    var playAccompaniment: Boolean
        get() = _playAccompaniment
        set(value) {
            val wasEnabled = _playAccompaniment
            _playAccompaniment = value
            android.util.Log.i(TAG, "playAccompaniment changed: $wasEnabled -> $value")
            // Si on désactive l'accompagnement, couper toutes les notes d'accompagnement en cours
            if (wasEnabled && !value) {
                android.util.Log.d(TAG, "playAccompaniment: stopping accompaniment notes")
                stopAccompanimentNotes()
            }
        }
    private var _muteTargetChannel: Boolean = false
    var muteTargetChannel: Boolean  // Mode "Je joue" - mute le canal cible, l'utilisateur joue a la place
        get() = _muteTargetChannel
        set(value) {
            val wasMuted = _muteTargetChannel
            _muteTargetChannel = value
            android.util.Log.i(TAG, "muteTargetChannel changed: $wasMuted -> $value (targetChannel=$targetChannel)")
            // Si on active le mute, couper toutes les notes du canal cible en cours
            if (!wasMuted && value) {
                android.util.Log.d(TAG, "muteTargetChannel: stopping target channel notes")
                stopTargetChannelNotes()
            }
        }

    // Two-hands mode: per-hand channel muting
    private var _muteLeftHandChannel: Boolean = false
    var muteLeftHandChannel: Boolean
        get() = _muteLeftHandChannel
        set(value) {
            val wasMuted = _muteLeftHandChannel
            _muteLeftHandChannel = value
            android.util.Log.i(TAG, "muteLeftHandChannel changed: $wasMuted -> $value (leftHandChannel=$leftHandChannel)")
            if (!wasMuted && value && isTwoHandsMode) {
                stopChannelNotes(leftHandChannel)
            }
        }

    private var _muteRightHandChannel: Boolean = false
    var muteRightHandChannel: Boolean
        get() = _muteRightHandChannel
        set(value) {
            val wasMuted = _muteRightHandChannel
            _muteRightHandChannel = value
            android.util.Log.i(TAG, "muteRightHandChannel changed: $wasMuted -> $value (rightHandChannel=$rightHandChannel)")
            if (!wasMuted && value && isTwoHandsMode) {
                stopChannelNotes(rightHandChannel)
            }
        }

    // Callbacks
    var onPositionChanged: ((currentMs: Long, durationMs: Long) -> Unit)? = null
    var onNoteOn: ((channel: Int, note: Int, velocity: Int) -> Unit)? = null
    var onNoteOff: ((channel: Int, note: Int) -> Unit)? = null
    var onTargetNoteOn: ((note: Int, velocity: Int, expectedTimeMs: Long) -> Unit)? = null
    var onTargetNoteOff: ((note: Int) -> Unit)? = null
    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackCompleted: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onAllNotesOff: (() -> Unit)? = null  // Callback quand toutes les notes doivent s'eteindre

    /**
     * Initialise avec un PracticeSynthesizer (Sonivox ou SF2)
     */
    fun initialize(synth: PracticeSynthesizer) {
        synthesizer = synth
    }

    /**
     * Charge un fichier MIDI depuis un File et filtre pour le canal cible
     */
    fun loadFile(file: File, channel: Int): Boolean {
        if (!file.exists()) {
            onError?.invoke("File not found")
            return false
        }

        return try {
            FileInputStream(file).use { inputStream ->
                loadFromInputStream(inputStream, channel)
            }
        } catch (e: Exception) {
            onError?.invoke("Failed to load: ${e.message}")
            state = State.IDLE
            false
        }
    }

    /**
     * Charge un fichier MIDI depuis un InputStream (pour les Content URIs)
     */
    fun loadFromInputStream(inputStream: java.io.InputStream, channel: Int): Boolean {
        try {
            stop()
            targetChannel = channel
            isTwoHandsMode = false
            secondTargetChannel = -1

            val midiFile = MidiFile(inputStream)

            // Convertir tous les evenements
            allEvents = convertToTimedEvents(midiFile)
            durationMs = if (allEvents.isNotEmpty()) allEvents.last().timestampMs else 0L

            // Extraire les notes du canal cible pour le falling notes
            targetChannelNotes = extractTargetChannelNotes()

            currentPositionMs.set(0)
            currentEventIndex = 0
            state = State.LOADED

            return true
        } catch (e: Exception) {
            onError?.invoke("Failed to load: ${e.message}")
            state = State.IDLE
            return false
        }
    }

    /**
     * Charge un fichier MIDI pour le mode deux mains (deux canaux cibles)
     */
    fun loadFromInputStreamTwoHands(
        inputStream: java.io.InputStream,
        leftChannel: Int,
        rightChannel: Int
    ): Boolean {
        try {
            stop()
            isTwoHandsMode = true
            leftHandChannel = leftChannel
            rightHandChannel = rightChannel
            targetChannel = rightChannel  // Canal principal pour compatibilité
            secondTargetChannel = leftChannel

            val midiFile = MidiFile(inputStream)

            // Convertir tous les evenements
            allEvents = convertToTimedEvents(midiFile)
            durationMs = if (allEvents.isNotEmpty()) allEvents.last().timestampMs else 0L

            // Extraire les notes des deux canaux pour le falling notes
            targetChannelNotes = extractTwoHandsNotes()

            currentPositionMs.set(0)
            currentEventIndex = 0
            state = State.LOADED

            return true
        } catch (e: Exception) {
            onError?.invoke("Failed to load: ${e.message}")
            state = State.IDLE
            return false
        }
    }

    /**
     * Charge un fichier MIDI pour le mode deux mains depuis un File
     */
    fun loadFileTwoHands(file: File, leftChannel: Int, rightChannel: Int): Boolean {
        if (!file.exists()) {
            onError?.invoke("File not found")
            return false
        }

        return try {
            FileInputStream(file).use { inputStream ->
                loadFromInputStreamTwoHands(inputStream, leftChannel, rightChannel)
            }
        } catch (e: Exception) {
            onError?.invoke("Failed to load: ${e.message}")
            state = State.IDLE
            false
        }
    }

    /**
     * Extrait les notes du canal cible sous forme de ScheduledNote
     */
    private fun extractTargetChannelNotes(): List<ScheduledNote> {
        val notes = mutableListOf<ScheduledNote>()
        val activeNotes = mutableMapOf<Int, Pair<Long, Int>>() // note -> (startTime, velocity)

        for (event in allEvents) {
            if (event.channel != targetChannel) continue

            when (val midiEvent = event.event) {
                is NoteOn -> {
                    if (midiEvent.velocity > 0) {
                        activeNotes[midiEvent.noteValue] = Pair(event.timestampMs, midiEvent.velocity)
                    } else {
                        // Note On avec velocity 0 = Note Off
                        activeNotes.remove(midiEvent.noteValue)?.let { (startTime, velocity) ->
                            notes.add(ScheduledNote(
                                note = midiEvent.noteValue,
                                startTimeMs = startTime,
                                durationMs = event.timestampMs - startTime,
                                velocity = velocity,
                                channel = targetChannel,
                                isLeftHand = false
                            ))
                        }
                    }
                }
                is NoteOff -> {
                    activeNotes.remove(midiEvent.noteValue)?.let { (startTime, velocity) ->
                        notes.add(ScheduledNote(
                            note = midiEvent.noteValue,
                            startTimeMs = startTime,
                            durationMs = event.timestampMs - startTime,
                            velocity = velocity,
                            channel = targetChannel,
                            isLeftHand = false
                        ))
                    }
                }
            }
        }

        return notes.sortedBy { it.startTimeMs }
    }

    /**
     * Extrait les notes des deux canaux (main gauche + main droite)
     */
    private fun extractTwoHandsNotes(): List<ScheduledNote> {
        val notes = mutableListOf<ScheduledNote>()
        // Map: (channel, note) -> (startTime, velocity)
        val activeNotes = mutableMapOf<Pair<Int, Int>, Pair<Long, Int>>()

        for (event in allEvents) {
            val isLeftHand = event.channel == leftHandChannel
            val isRightHand = event.channel == rightHandChannel
            if (!isLeftHand && !isRightHand) continue

            when (val midiEvent = event.event) {
                is NoteOn -> {
                    val key = Pair(event.channel, midiEvent.noteValue)
                    if (midiEvent.velocity > 0) {
                        activeNotes[key] = Pair(event.timestampMs, midiEvent.velocity)
                    } else {
                        // Note On avec velocity 0 = Note Off
                        activeNotes.remove(key)?.let { (startTime, velocity) ->
                            notes.add(ScheduledNote(
                                note = midiEvent.noteValue,
                                startTimeMs = startTime,
                                durationMs = event.timestampMs - startTime,
                                velocity = velocity,
                                channel = event.channel,
                                isLeftHand = isLeftHand
                            ))
                        }
                    }
                }
                is NoteOff -> {
                    val key = Pair(event.channel, midiEvent.noteValue)
                    activeNotes.remove(key)?.let { (startTime, velocity) ->
                        notes.add(ScheduledNote(
                            note = midiEvent.noteValue,
                            startTimeMs = startTime,
                            durationMs = event.timestampMs - startTime,
                            velocity = velocity,
                            channel = event.channel,
                            isLeftHand = isLeftHand
                        ))
                    }
                }
            }
        }

        return notes.sortedBy { it.startTimeMs }
    }

    /**
     * Vérifie si c'est le mode deux mains
     */
    @Suppress("unused")
    fun isTwoHandsMode(): Boolean = isTwoHandsMode

    /**
     * Retourne les canaux main gauche/droite
     */
    @Suppress("unused")
    fun getLeftHandChannel(): Int = leftHandChannel
    @Suppress("unused")
    fun getRightHandChannel(): Int = rightHandChannel

    /**
     * Retourne les notes programmees du canal cible
     */
    fun getTargetChannelNotes(): List<ScheduledNote> = targetChannelNotes

    /**
     * Calcule et retourne la plage de notes reelle du canal cible (arrondie aux octaves)
     * @return Pair(noteRangeMin, noteRangeMax) ou null si aucune note
     */
    fun getActualNoteRange(): Pair<Int, Int>? {
        if (targetChannelNotes.isEmpty()) return null

        val actualMin = targetChannelNotes.minOf { it.note }
        val actualMax = targetChannelNotes.maxOf { it.note }

        // Arrondir aux octaves completes
        val displayMin = ((actualMin / 12) * 12).coerceIn(0, 120)
        val displayMax = (((actualMax / 12) + 1) * 12).coerceIn(12, 127)

        return Pair(displayMin, displayMax)
    }

    /**
     * Convertit les evenements MIDI en liste triee avec timestamps en ms
     */
    private fun convertToTimedEvents(midiFile: MidiFile): List<TimedEvent> {
        val resolution = midiFile.resolution.toLong()
        val events = mutableListOf<TimedEvent>()

        // Collecter tous les evenements
        val rawEvents = mutableListOf<Pair<Long, MidiEvent>>()
        for (track in midiFile.tracks) {
            for (event in track.events) {
                rawEvents.add(event.tick to event)
            }
        }
        rawEvents.sortBy { it.first }

        // Convertir ticks en ms
        var microsecondsPerBeat = MICROSECONDS_PER_MINUTE / DEFAULT_TEMPO_BPM
        var lastTick = 0L
        var currentTimeUs = 0L

        for ((tick, event) in rawEvents) {
            val deltaTicks = tick - lastTick
            if (deltaTicks > 0 && resolution > 0) {
                currentTimeUs += (deltaTicks * microsecondsPerBeat) / resolution
            }
            lastTick = tick

            if (event is Tempo) {
                // BUG FIX 3.26: Valider les valeurs de tempo pour éviter les valeurs aberrantes
                // Tempo valide: 20 BPM (3_000_000 µs/beat) à 300 BPM (200_000 µs/beat)
                val newTempo = event.mpqn.toLong()
                if (newTempo in 200_000L..3_000_000L) {
                    microsecondsPerBeat = newTempo
                }
                // Sinon, garder le tempo précédent
            }

            val channel = getEventChannel(event)
            val midiBytes = convertEventToBytes(event)

            if (midiBytes != null || event is Tempo) {
                events.add(TimedEvent(
                    timestampMs = currentTimeUs / 1000,
                    event = event,
                    channel = channel,
                    midiBytes = midiBytes
                ))
            }
        }

        return events
    }

    /**
     * Recupere le canal d'un evenement MIDI
     */
    private fun getEventChannel(event: MidiEvent): Int {
        return when (event) {
            is NoteOn -> event.channel
            is NoteOff -> event.channel
            is ProgramChange -> event.channel
            is Controller -> event.channel
            else -> -1
        }
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
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Definit la vitesse de lecture (0.25 a 2.0)
     */
    fun setSpeed(speed: Float) {
        speedMultiplier = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        // Synchroniser l'horloge partagée
        sharedClock.setSpeed(speedMultiplier)
    }

    /**
     * Recupere la vitesse actuelle
     */
    fun getSpeed(): Float = speedMultiplier

    /**
     * Demarre la lecture
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

        // Démarrer l'horloge partagée
        val startPos = currentPositionMs.get()
        sharedClock.start(startPos, speedMultiplier)

        playbackThread = Thread(PlaybackRunnable()).apply {
            name = "PracticePlayback-Thread"
            start()
        }

        onPlaybackStarted?.invoke()
    }

    /**
     * Met en pause
     */
    fun pause() {
        if (!isPlaying.get()) return

        isPaused.set(true)
        isPlaying.set(false)
        state = State.PAUSED

        // Mettre en pause l'horloge partagée
        sharedClock.pause()

        // Interrompre le thread et attendre qu'il termine pour éviter les threads zombies
        val thread = playbackThread
        thread?.interrupt()
        if (thread != null && thread.isAlive) {
            try {
                thread.join(500)  // Attendre max 500ms
            } catch (_: InterruptedException) {
                // Restaurer le flag d'interruption
                Thread.currentThread().interrupt()
            }
        }
        playbackThread = null

        // Eteindre toutes les notes (audio + callback pour UI/LEDs)
        sendAllNotesOff()
        onAllNotesOff?.invoke()
    }

    /**
     * Reprend la lecture
     */
    fun resume() {
        if (state != State.PAUSED) {
            play()
            return
        }
        play()
    }

    /**
     * Arrete et remet au debut
     */
    fun stop() {
        isPlaying.set(false)
        isPaused.set(false)

        // Arrêter l'horloge partagée
        sharedClock.stop()

        playbackThread?.interrupt()
        try {
            playbackThread?.join(500)
        } catch (_: InterruptedException) {
            // OK
        }
        playbackThread = null

        // Eteindre toutes les notes (audio + callback pour UI/LEDs)
        sendAllNotesOff()
        onAllNotesOff?.invoke()

        currentPositionMs.set(0)
        currentEventIndex = 0
        state = if (allEvents.isNotEmpty()) State.STOPPED else State.IDLE
    }

    /**
     * Retourne au debut sans arreter
     */
    fun restart() {
        val wasPlaying = isPlaying.get()
        stop()
        if (wasPlaying) {
            play()
        }
    }

    /**
     * Seek a une position
     */
    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceIn(0, durationMs)

        val wasPlaying = isPlaying.get()

        if (wasPlaying) {
            isPlaying.set(false)
            playbackThread?.interrupt()
            try {
                playbackThread?.join(200)
            } catch (_: InterruptedException) {
                // OK
            }
            playbackThread = null
            state = State.PAUSED
        }

        // Eteindre les notes (audio + callback pour UI/LEDs)
        sendAllNotesOff()
        onAllNotesOff?.invoke()

        // Trouver le bon index
        currentEventIndex = findEventIndexAtPosition(targetMs)
        currentPositionMs.set(targetMs)

        // Mettre à jour l'horloge partagée
        sharedClock.seekTo(targetMs)

        // Envoyer les Program Changes jusqu'a cette position
        sendProgramChangesUpToPosition(targetMs)

        onPositionChanged?.invoke(targetMs, durationMs)

        if (wasPlaying) {
            play()
        } else {
            state = State.PAUSED
        }
    }

    private fun findEventIndexAtPosition(positionMs: Long): Int {
        var low = 0
        var high = allEvents.size - 1

        while (low < high) {
            val mid = (low + high) / 2
            if (allEvents[mid].timestampMs < positionMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        return low.coerceIn(0, allEvents.size - 1)
    }

    private fun sendProgramChangesUpToPosition(positionMs: Long) {
        val programsByChannel = mutableMapOf<Int, ByteArray>()

        for (event in allEvents) {
            if (event.timestampMs > positionMs) break

            if (event.event is ProgramChange && event.midiBytes != null) {
                programsByChannel[event.channel] = event.midiBytes
            }
        }

        programsByChannel.forEach { (channel, bytes) ->
            // Extract program number from bytes (0xC0 | channel, program)
            if (bytes.size >= 2) {
                val program = bytes[1].toInt() and 0x7F
                synthesizer?.programChange(channel, program)
            }
        }
    }

    private fun sendAllNotesOff() {
        val synth = synthesizer ?: return

        // Couper tous les sons immédiatement
        synth.allSoundOff()

        // Reset tous les contrôleurs sur tous les canaux (sustain, modulation, etc.)
        for (channel in 0..15) {
            // CC121 = Reset All Controllers (sustain, modulation, etc.)
            synth.controlChange(channel, 121, 0)
            // CC64 = Sustain Pedal Off (explicite, au cas où CC121 ne suffit pas)
            synth.controlChange(channel, 64, 0)
        }
    }

    /**
     * Coupe toutes les notes d'accompagnement (tous les canaux sauf le(s) canal(aux) cible(s))
     * Appelé quand on désactive l'accompagnement pour éviter les notes bloquées
     */
    private fun stopAccompanimentNotes() {
        val synth = synthesizer ?: return

        // En mode deux mains, exclure les deux canaux cibles
        val targetChannels = if (isTwoHandsMode) {
            setOf(leftHandChannel, rightHandChannel)
        } else {
            setOf(targetChannel)
        }

        for (channel in 0..15) {
            if (channel !in targetChannels) {
                // CC120 = All Sound Off - coupe immédiatement les sons
                synth.controlChange(channel, 120, 0)
                // CC121 = Reset All Controllers - reset sustain, modulation, etc.
                synth.controlChange(channel, 121, 0)
                // CC64 = Sustain Pedal Off (explicite)
                synth.controlChange(channel, 64, 0)
            }
        }
    }

    /**
     * Coupe toutes les notes du canal cible uniquement
     * Appelé quand on active le mute du canal cible pour éviter les notes bloquées
     */
    private fun stopTargetChannelNotes() {
        val synth = synthesizer ?: return

        // En mode deux mains, couper les deux canaux
        val channels = if (isTwoHandsMode) {
            listOf(leftHandChannel, rightHandChannel)
        } else {
            listOf(targetChannel)
        }

        for (channel in channels) {
            // CC120 = All Sound Off - coupe immédiatement les sons
            synth.controlChange(channel, 120, 0)
            // CC121 = Reset All Controllers - reset sustain, modulation, etc.
            synth.controlChange(channel, 121, 0)
            // CC64 = Sustain Pedal Off (explicite)
            synth.controlChange(channel, 64, 0)
        }
    }

    /**
     * Coupe toutes les notes d'un canal spécifique
     * Utilisé pour le mute per-hand en mode deux mains
     */
    private fun stopChannelNotes(channel: Int) {
        val synth = synthesizer ?: return
        if (channel < 0) return

        // CC120 = All Sound Off - coupe immédiatement les sons
        synth.controlChange(channel, 120, 0)
        // CC121 = Reset All Controllers - reset sustain, modulation, etc.
        synth.controlChange(channel, 121, 0)
        // CC64 = Sustain Pedal Off (explicite)
        synth.controlChange(channel, 64, 0)
    }

    /**
     * Envoie des bytes MIDI au synthetiseur en les parsant.
     */
    private fun sendMidiBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        val synth = synthesizer ?: return
        val status = bytes[0].toInt() and 0xFF
        val type = status and 0xF0
        val channel = status and 0x0F

        when (type) {
            0x90 -> { // Note On
                if (bytes.size >= 3) {
                    val note = bytes[1].toInt() and 0x7F
                    val velocity = bytes[2].toInt() and 0x7F
                    if (velocity > 0) {
                        synth.noteOn(channel, note, velocity)
                    } else {
                        synth.noteOff(channel, note)
                    }
                }
            }
            0x80 -> { // Note Off
                if (bytes.size >= 3) {
                    val note = bytes[1].toInt() and 0x7F
                    synth.noteOff(channel, note)
                }
            }
            0xC0 -> { // Program Change
                if (bytes.size >= 2) {
                    val program = bytes[1].toInt() and 0x7F
                    synth.programChange(channel, program)
                }
            }
            0xB0 -> { // Control Change
                if (bytes.size >= 3) {
                    val controller = bytes[1].toInt() and 0x7F
                    val value = bytes[2].toInt() and 0x7F
                    synth.controlChange(channel, controller, value)
                }
            }
        }
    }

    // Getters
    fun getCurrentPositionMs(): Long = currentPositionMs.get()
    fun getDurationMs(): Long = durationMs
    fun isPlaying(): Boolean = isPlaying.get()
    fun isPaused(): Boolean = isPaused.get()
    @Suppress("unused")
    fun getState(): State = state

    /**
     * Calcule la position ajustee par le tempo
     * Pour synchroniser les falling notes
     */
    @Suppress("unused")
    fun getAdjustedPositionMs(): Long {
        return currentPositionMs.get()
    }

    /**
     * Libere les ressources
     */
    fun release() {
        stop()
        allEvents = emptyList()
        targetChannelNotes = emptyList()
        synthesizer = null
        onPositionChanged = null
        onNoteOn = null
        onNoteOff = null
        onTargetNoteOn = null
        onTargetNoteOff = null
        onPlaybackStarted = null
        onPlaybackCompleted = null
        onError = null
        state = State.IDLE
    }

    /**
     * Runnable de lecture avec support du tempo variable
     * Utilise l'horloge partagée pour la synchronisation audio/visuel
     */
    private inner class PlaybackRunnable : Runnable {
        override fun run() {
            var lastPositionUpdateMs = sharedClock.getCurrentPositionMs()

            try {
                while (isPlaying.get() && currentEventIndex < allEvents.size) {
                    val event = allEvents[currentEventIndex]

                    // Utiliser l'horloge partagée pour obtenir la position courante
                    val currentTimeMs = sharedClock.getCurrentPositionMs()

                    // Synchroniser l'ancien AtomicLong pour compatibilité
                    currentPositionMs.set(currentTimeMs)

                    // Attendre si necessaire
                    val waitAdjustedMs = event.timestampMs - currentTimeMs
                    if (waitAdjustedMs > 0) {
                        // Convertir en temps reel selon la vitesse de l'horloge partagée
                        val currentSpeed = sharedClock.getSpeed()
                        val waitRealMs = (waitAdjustedMs / currentSpeed).roundToLong()
                        try {
                            Thread.sleep(waitRealMs.coerceAtMost(100))
                            continue
                        } catch (_: InterruptedException) {
                            if (!isPlaying.get()) break
                        }
                    }

                    // Determiner si on doit jouer l'audio et/ou notifier les callbacks
                    // En mode deux mains, les deux canaux sont des canaux cibles
                    val isTargetChannel = if (isTwoHandsMode) {
                        event.channel == leftHandChannel || event.channel == rightHandChannel
                    } else {
                        event.channel == targetChannel
                    }
                    val isAccompaniment = !isTargetChannel && event.channel >= 0

                    // Jouer l'audio selon les options
                    val shouldPlayAudio = when {
                        // Mode "Je joue" : muter le canal cible (single hand mode)
                        isTargetChannel && muteTargetChannel -> false
                        // Two-hands mode: check per-hand muting
                        isTwoHandsMode && event.channel == leftHandChannel && muteLeftHandChannel -> false
                        isTwoHandsMode && event.channel == rightHandChannel && muteRightHandChannel -> false
                        isTargetChannel -> true
                        isAccompaniment && playAccompaniment -> true
                        else -> false
                    }

                    if (shouldPlayAudio && event.midiBytes != null) {
                        sendMidiBytes(event.midiBytes)
                    }

                    // Log note events for debugging (only log occasionally to avoid spam)
                    val midiEvent = event.event
                    if (midiEvent is NoteOn && midiEvent.velocity > 0) {
                        // Log every C note for debugging
                        if (midiEvent.noteValue % 12 == 0) {
                            android.util.Log.d(TAG, "NoteOn: ch=${event.channel} note=${midiEvent.noteValue} " +
                                "isTarget=$isTargetChannel shouldPlay=$shouldPlayAudio " +
                                "muteTarget=$muteTargetChannel playAccomp=$playAccompaniment")
                        }
                    }

                    // Toujours notifier les callbacks pour le canal cible (pour la visualisation)
                    // meme si l'audio est mute
                    if (isTargetChannel || (isAccompaniment && playAccompaniment)) {
                        when (midiEvent) {
                            is NoteOn -> {
                                if (midiEvent.velocity > 0) {
                                    onNoteOn?.invoke(event.channel, midiEvent.noteValue, midiEvent.velocity)
                                    if (isTargetChannel) {
                                        onTargetNoteOn?.invoke(midiEvent.noteValue, midiEvent.velocity, event.timestampMs)
                                    }
                                } else {
                                    onNoteOff?.invoke(event.channel, midiEvent.noteValue)
                                    if (isTargetChannel) {
                                        onTargetNoteOff?.invoke(midiEvent.noteValue)
                                    }
                                }
                            }
                            is NoteOff -> {
                                onNoteOff?.invoke(event.channel, midiEvent.noteValue)
                                if (isTargetChannel) {
                                    onTargetNoteOff?.invoke(midiEvent.noteValue)
                                }
                            }
                        }
                    }

                    currentEventIndex++

                    // Notifier la position (~4 fois par seconde)
                    if (currentTimeMs - lastPositionUpdateMs >= 250) {
                        lastPositionUpdateMs = currentTimeMs
                        onPositionChanged?.invoke(currentTimeMs, durationMs)
                    }
                }

                // Fin de lecture
                if (isPlaying.get() && currentEventIndex >= allEvents.size) {
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
}
