package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.util.SparseArray
import android.util.SparseIntArray
import com.Atom2Universe.app.R
import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOn
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.meta.TrackName
import java.io.File
import java.io.FileInputStream

/**
 * Gestionnaire pour analyser et suivre les notes MIDI en temps réel
 *
 * Fonctionnalités:
 * - Analyse un fichier MIDI pour lister toutes les pistes (tracks)
 * - Chaque piste est associée à un canal MIDI pour le routage des notes
 * - Suit les notes actives par canal en temps réel
 * - Calcule les octaves nécessaires pour l'affichage
 */
class MidiNoteTracker(private val context: Context) {

    companion object {
        // Constantes MIDI
        const val MIDI_NOTE_MIN = 0
        const val MIDI_NOTE_MAX = 127
        const val NOTES_PER_OCTAVE = 12
        const val TOTAL_CHANNELS = 16
        const val DRUM_CHANNEL = 9  // Canal 10 en notation 1-based

        /**
         * Détecte si un fichier MIDI est un morceau de piano à deux mains
         *
         * Critères de détection:
         * - Exactement 2 canaux non-batterie avec des notes
         * - Les deux canaux ont des instruments piano (program 0-7) ou pas de program change
         * - Un canal a principalement des notes basses, l'autre des notes hautes
         * - Le point de séparation est autour du Do central (MIDI 60)
         */
        fun detectTwoHands(tracks: List<TrackInfo>): TwoHandsInfo {
            // Filtrer les canaux non-batterie avec des notes
            val pianoTracks = tracks.filter { track ->
                !track.isDrumTrack &&
                track.noteCount > 0 &&
                // Programme 0-7 = pianos/claviers, ou -1/0 si pas de program change
                (track.program in 0..7 || track.allPrograms.isEmpty() || track.allPrograms.all { it in 0..7 })
            }

            // Il faut exactement 2 canaux pour deux mains
            if (pianoTracks.size != 2) {
                return TwoHandsInfo(isDetected = false)
            }

            val track1 = pianoTracks[0]
            val track2 = pianoTracks[1]

            // Calculer le centre de chaque plage de notes
            val center1 = (track1.channelNoteRangeMin + track1.channelNoteRangeMax) / 2
            val center2 = (track2.channelNoteRangeMin + track2.channelNoteRangeMax) / 2

            // Vérifier qu'il y a une différence significative (au moins une octave)
            val centerDiff = kotlin.math.abs(center1 - center2)
            if (centerDiff < 8) {
                // Les deux canaux ont des plages trop similaires
                return TwoHandsInfo(isDetected = false)
            }

            // Déterminer quelle main est laquelle
            val (leftHand, rightHand) = if (center1 < center2) {
                Pair(track1, track2)
            } else {
                Pair(track2, track1)
            }

            // Vérifier que la main gauche est bien dans les basses et la droite dans les aigus
            // Le Do central (C4) est MIDI 60
            val middleC = 60
            val leftIsLow = leftHand.channelNoteRangeMax <= middleC + 12  // Peut monter jusqu'à C5
            val rightIsHigh = rightHand.channelNoteRangeMin >= middleC - 12  // Peut descendre jusqu'à C3

            if (!leftIsLow && !rightIsHigh) {
                // Les plages ne correspondent pas au pattern main gauche/droite
                return TwoHandsInfo(isDetected = false)
            }

            return TwoHandsInfo(
                isDetected = true,
                leftHandChannel = leftHand.channel,
                rightHandChannel = rightHand.channel,
                leftHandName = leftHand.trackName.ifEmpty { "Left Hand" },
                rightHandName = rightHand.trackName.ifEmpty { "Right Hand" },
                leftHandNoteRange = Pair(leftHand.channelNoteRangeMin, leftHand.channelNoteRangeMax),
                rightHandNoteRange = Pair(rightHand.channelNoteRangeMin, rightHand.channelNoteRangeMax)
            )
        }
    }

    /**
     * Résultat de la détection de MIDI deux mains (piano)
     */
    data class TwoHandsInfo(
        val isDetected: Boolean,
        val leftHandChannel: Int = -1,   // Canal main gauche (notes basses)
        val rightHandChannel: Int = -1,  // Canal main droite (notes hautes)
        val leftHandName: String = "",
        val rightHandName: String = "",
        val leftHandNoteRange: Pair<Int, Int> = Pair(0, 0),
        val rightHandNoteRange: Pair<Int, Int> = Pair(0, 0)
    )

    // Plage de notes détectée dans le fichier
    var noteRangeMin: Int = MIDI_NOTE_MAX
        private set
    var noteRangeMax: Int = MIDI_NOTE_MIN
        private set

    // Plage arrondie aux octaves complètes
    var displayRangeMin: Int = 60  // C4 par défaut
        private set
    var displayRangeMax: Int = 72  // C5 par défaut
        private set

    // Notes actives: channel -> Set<note>
    private val activeNotes = SparseArray<MutableSet<Int>>()

    // Vélocité des notes actives: (channel * 128 + note) -> velocity
    private val noteVelocities = SparseIntArray()

    // Callbacks
    var onNoteOn: ((channel: Int, note: Int, velocity: Int) -> Unit)? = null
    var onNoteOff: ((channel: Int, note: Int) -> Unit)? = null
    var onProgramChange: ((channel: Int, program: Int) -> Unit)? = null
    var onAnalysisComplete: ((noteMin: Int, noteMax: Int, tracks: List<TrackInfo>) -> Unit)? = null

    /**
     * Information sur une piste MIDI du fichier
     */
    data class TrackInfo(
        val trackIndex: Int,          // Index de la piste dans le fichier (0, 1, 2, ...)
        val trackName: String,        // Nom de la piste (meta-event ou généré)
        val channel: Int,             // Canal MIDI principal utilisé par cette piste
        val program: Int,             // Programme (instrument) principal (premier détecté)
        val instrumentName: String,   // Nom de l'instrument principal
        val noteCount: Int,           // Nombre de notes dans cette piste
        val isDrumTrack: Boolean,     // True si c'est une piste de batterie (canal 9)
        val programCount: Int = 1,    // Nombre de programmes différents utilisés
        val allPrograms: List<Int> = listOf(),  // Liste de tous les programmes utilisés
        val channelNoteRangeMin: Int = 48,  // Note min pour ce canal spécifique
        val channelNoteRangeMax: Int = 84   // Note max pour ce canal spécifique
    )

    // Pour compatibilité avec l'ancien code
    data class ChannelInfo(
        val channel: Int,
        val program: Int,
        val instrumentName: String,
        val noteCount: Int,
        val isDrumChannel: Boolean = channel == DRUM_CHANNEL
    )

    init {
        // Initialiser les sets pour chaque canal
        for (ch in 0 until TOTAL_CHANNELS) {
            activeNotes.put(ch, mutableSetOf())
        }
    }

    /**
     * Analyse un fichier MIDI pour extraire les informations par CANAL
     * (pas par piste du fichier, car Type 0 = une seule piste avec plusieurs canaux)
     */
    fun analyzeFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        return try {
            reset()

            var detectedNoteMin = MIDI_NOTE_MAX
            var detectedNoteMax = MIDI_NOTE_MIN

            // Collecter les infos par CANAL (pas par piste du fichier)
            data class ChannelData(
                var noteCount: Int = 0,
                var firstProgram: Int = 0,           // Premier programme détecté
                var trackName: String? = null,
                val programs: MutableSet<Int> = mutableSetOf(),  // Tous les programmes utilisés
                var noteMin: Int = MIDI_NOTE_MAX,    // Note la plus basse pour ce canal
                var noteMax: Int = MIDI_NOTE_MIN     // Note la plus haute pour ce canal
            )
            val channelDataMap = mutableMapOf<Int, ChannelData>()

            FileInputStream(file).use { stream ->
                val midiFile = MidiFile(stream)

                // Parcourir toutes les pistes pour collecter les infos par canal
                midiFile.tracks.forEachIndexed { trackIndex, track ->
                    var currentTrackName: String? = null

                    for (event in track.events) {
                        when (event) {
                            is TrackName -> {
                                currentTrackName = event.trackName
                            }
                            is NoteOn -> {
                                if (event.velocity > 0) {
                                    val note = event.noteValue
                                    val channel = event.channel

                                    // Mettre à jour la plage globale (ignorer batterie pour la plage piano)
                                    if (channel != DRUM_CHANNEL) {
                                        if (note < detectedNoteMin) detectedNoteMin = note
                                        if (note > detectedNoteMax) detectedNoteMax = note
                                    }

                                    // Collecter par canal
                                    val data = channelDataMap.getOrPut(channel) { ChannelData() }
                                    data.noteCount++

                                    // Tracker la plage de notes pour CE canal
                                    if (note < data.noteMin) data.noteMin = note
                                    if (note > data.noteMax) data.noteMax = note

                                    // Garder le premier nom de piste trouvé pour ce canal
                                    if (data.trackName == null && currentTrackName != null) {
                                        data.trackName = currentTrackName
                                    }
                                }
                            }
                            is ProgramChange -> {
                                val data = channelDataMap.getOrPut(event.channel) { ChannelData() }
                                // Garder le premier programme comme principal
                                if (data.programs.isEmpty()) {
                                    data.firstProgram = event.programNumber
                                }
                                // Ajouter à la liste de tous les programmes
                                data.programs.add(event.programNumber)
                            }
                        }
                    }
                }
            }

            // Créer les TrackInfo à partir des canaux utilisés
            val trackInfos = channelDataMap
                .filter { it.value.noteCount > 0 }
                .map { (channel, data) ->
                    val isDrum = channel == DRUM_CHANNEL
                    val programToUse = if (data.programs.isNotEmpty()) data.firstProgram else 0
                    val allProgramsList = data.programs.toList().sorted()

                    val instrumentName = if (isDrum) {
                        context.getString(R.string.gm_drum_channel)
                    } else {
                        GeneralMidiInstruments.getName(context, programToUse)
                    }

                    // Utiliser le nom de piste si disponible, sinon l'instrument
                    val displayName = data.trackName?.takeIf { it.isNotBlank() }
                        ?: instrumentName

                    // Calculer la plage d'affichage pour CE canal (octaves complètes)
                    val channelDisplayMin = if (data.noteMin <= data.noteMax) {
                        ((data.noteMin / NOTES_PER_OCTAVE) * NOTES_PER_OCTAVE).coerceIn(0, 120)
                    } else 48
                    val channelDisplayMax = if (data.noteMin <= data.noteMax) {
                        (((data.noteMax / NOTES_PER_OCTAVE) + 1) * NOTES_PER_OCTAVE).coerceIn(12, 127)
                    } else 84

                    TrackInfo(
                        trackIndex = channel,  // Utiliser le canal comme index unique
                        trackName = displayName,
                        channel = channel,
                        program = programToUse,
                        instrumentName = instrumentName,
                        noteCount = data.noteCount,
                        isDrumTrack = isDrum,
                        programCount = allProgramsList.size.coerceAtLeast(1),
                        allPrograms = allProgramsList,
                        channelNoteRangeMin = channelDisplayMin,
                        channelNoteRangeMax = channelDisplayMax
                    )
                }
                .sortedBy { it.channel }  // Trier par numéro de canal

            // Stocker la plage détectée
            if (detectedNoteMin <= detectedNoteMax) {
                noteRangeMin = detectedNoteMin
                noteRangeMax = detectedNoteMax
            } else {
                // Aucune note trouvée (hors batterie), utiliser des valeurs par défaut
                noteRangeMin = 48  // C3
                noteRangeMax = 84  // C6
            }

            // Calculer la plage d'affichage (octaves complètes)
            calculateDisplayRange()

            onAnalysisComplete?.invoke(noteRangeMin, noteRangeMax, trackInfos)
            true

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calcule la plage d'affichage arrondie aux octaves complètes
     */
    private fun calculateDisplayRange() {
        // Arrondir noteRangeMin vers le bas à l'octave la plus proche
        displayRangeMin = (noteRangeMin / NOTES_PER_OCTAVE) * NOTES_PER_OCTAVE

        // Arrondir noteRangeMax vers le haut à l'octave suivante
        displayRangeMax = ((noteRangeMax / NOTES_PER_OCTAVE) + 1) * NOTES_PER_OCTAVE

        // Limiter aux valeurs MIDI valides
        displayRangeMin = displayRangeMin.coerceIn(0, 120)
        displayRangeMax = displayRangeMax.coerceIn(12, 127)

        // Assurer au moins 2 octaves
        if (displayRangeMax - displayRangeMin < 24) {
            val center = (displayRangeMin + displayRangeMax) / 2
            displayRangeMin = (center - 12).coerceAtLeast(0)
            displayRangeMax = (center + 12).coerceAtMost(127)
        }
    }

    /**
     * Traite un événement MIDI brut (bytes)
     */
    fun processMidiBytes(midiBytes: ByteArray) {
        if (midiBytes.isEmpty()) return

        val status = midiBytes[0].toInt() and 0xFF
        val type = status and 0xF0
        val channel = status and 0x0F

        when (type) {
            0x90 -> { // Note On
                if (midiBytes.size >= 3) {
                    val note = midiBytes[1].toInt() and 0x7F
                    val velocity = midiBytes[2].toInt() and 0x7F

                    if (velocity > 0) {
                        handleNoteOn(channel, note, velocity)
                    } else {
                        // Note On avec velocity 0 = Note Off
                        handleNoteOff(channel, note)
                    }
                }
            }
            0x80 -> { // Note Off
                if (midiBytes.size >= 3) {
                    val note = midiBytes[1].toInt() and 0x7F
                    handleNoteOff(channel, note)
                }
            }
            0xC0 -> { // Program Change
                if (midiBytes.size >= 2) {
                    val program = midiBytes[1].toInt() and 0x7F
                    handleProgramChange(channel, program)
                }
            }
        }
    }

    private fun handleNoteOn(channel: Int, note: Int, velocity: Int) {
        activeNotes.get(channel)?.add(note)
        noteVelocities.put(channel * 128 + note, velocity)
        onNoteOn?.invoke(channel, note, velocity)
    }

    private fun handleNoteOff(channel: Int, note: Int) {
        activeNotes.get(channel)?.remove(note)
        noteVelocities.delete(channel * 128 + note)
        onNoteOff?.invoke(channel, note)
    }

    private fun handleProgramChange(channel: Int, program: Int) {
        onProgramChange?.invoke(channel, program)
    }

    /**
     * Retourne les notes actuellement actives pour un canal
     */
    fun getActiveNotes(channel: Int): Set<Int> {
        return activeNotes.get(channel)?.toSet() ?: emptySet()
    }

    /**
     * Retourne la vélocité d'une note active
     */
    fun getNoteVelocity(channel: Int, note: Int): Int {
        return noteVelocities.get(channel * 128 + note, 0)
    }

    /**
     * Réinitialise l'état du tracker
     */
    fun reset() {
        for (ch in 0 until TOTAL_CHANNELS) {
            activeNotes.get(ch)?.clear()
        }
        noteVelocities.clear()

        noteRangeMin = MIDI_NOTE_MAX
        noteRangeMax = MIDI_NOTE_MIN
        displayRangeMin = 60
        displayRangeMax = 72
    }

    /**
     * Efface toutes les notes actives sans réinitialiser l'analyse.
     * Utilisé pour le refresh des claviers.
     */
    fun clearActiveNotes() {
        for (ch in 0 until TOTAL_CHANNELS) {
            activeNotes.get(ch)?.clear()
        }
        noteVelocities.clear()
    }

    /**
     * Convertit un numéro de note MIDI en nom (ex: 60 -> "C4")
     */
    fun noteToName(note: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (note / 12) - 1  // MIDI note 60 = C4
        val noteName = noteNames[note % 12]
        return "$noteName$octave"
    }
}
