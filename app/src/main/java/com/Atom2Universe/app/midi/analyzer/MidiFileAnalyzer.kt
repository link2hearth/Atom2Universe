package com.Atom2Universe.app.midi.analyzer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.Atom2Universe.app.midi.sf2.InstrumentTimeline
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import com.leff.midi.MidiFile
import com.leff.midi.event.Controller
import com.leff.midi.event.NoteOn
import com.leff.midi.event.NoteOff
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.meta.Tempo
import com.leff.midi.event.meta.TimeSignature
import com.leff.midi.event.meta.TrackName
import com.leff.midi.event.meta.CopyrightNotice
import java.util.Locale

/**
 * Analyseur de fichier MIDI - Lit les informations detaillees a la demande
 * Supporte les Content URIs Android (via ContentResolver)
 */
class MidiFileAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "MidiFileAnalyzer"
        const val DRUM_CHANNEL = 9
    }

    /**
     * Resultat de l'analyse d'un fichier MIDI
     */
    data class MidiFileInfo(
        // Infos fichier
        val fileName: String,
        val fileSize: Long,

        // Infos MIDI generales
        val midiType: Int,              // 0, 1 ou 2
        val trackCount: Int,            // Nombre de pistes dans le fichier
        val resolution: Int,            // Ticks per quarter note (PPQ)
        val durationMs: Long,           // Duree en millisecondes

        // Tempo
        val mainBpm: Double,            // BPM principal
        val tempoCount: Int,            // Nombre de tempos differents

        // Time signature
        val timeSignature: String,      // Ex: "4/4"
        val timeSignatureCount: Int,    // Nombre de signatures differentes

        // Canaux et instruments
        val channels: List<ChannelInfo>,
        val totalNoteCount: Int,

        // Metadonnees
        val trackNames: List<String>,
        val copyright: String?,

        // Erreur eventuelle
        val error: String? = null
    ) {
        val formattedDuration: String
            get() {
                if (durationMs <= 0) return "--:--"
                val seconds = durationMs / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                return String.format(Locale.ROOT, "%d:%02d", minutes, secs)
            }

        val formattedFileSize: String
            get() {
                return when {
                    fileSize >= 1024 * 1024 -> String.format(Locale.ROOT, "%.2f Mo", fileSize / (1024.0 * 1024.0))
                    fileSize >= 1024 -> String.format(Locale.ROOT, "%.1f Ko", fileSize / 1024.0)
                    else -> "$fileSize octets"
                }
            }

        val midiTypeDescription: String
            get() = when (midiType) {
                0 -> "Type 0"
                1 -> "Type 1"
                2 -> "Type 2"
                else -> "Type $midiType"
            }

        /** Resume court pour l'affichage dans la liste */
        val summary: String
            get() {
                val parts = mutableListOf<String>()
                parts.add(formattedDuration)
                parts.add("$totalNoteCount notes")
                parts.add("${channels.size} canaux")
                if (mainBpm > 0) parts.add("${mainBpm.toInt()} BPM")
                return parts.joinToString(" . ")
            }

        /** Liste des instruments utilises */
        val instrumentsList: String
            get() {
                return channels
                    .filter { !it.isDrumChannel }
                    .flatMap { it.instrumentNames }
                    .distinct()
                    .take(5)
                    .joinToString(", ")
                    .let { if (channels.any { c -> c.isDrumChannel }) "$it, Batterie" else it }
            }
    }

    data class ChannelInfo(
        val channel: Int,
        val noteCount: Int,
        val programs: List<Int>,
        val instrumentNames: List<String>,
        val isDrumChannel: Boolean = channel == DRUM_CHANNEL
    ) {
        val mainInstrument: String
            get() = if (isDrumChannel) "Batterie" else instrumentNames.firstOrNull() ?: "Inconnu"
    }

    /**
     * Analyse un fichier MIDI a partir de son URI (content:// ou file://)
     */
    fun analyze(uriString: String): MidiFileInfo {
        return try {
            val uri = Uri.parse(uriString)

            // Recuperer les infos du fichier
            val (fileName, fileSize) = getFileInfo(uri)

            // Ouvrir et parser le fichier MIDI
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val midiFile = MidiFile(stream)
                parseMidiFile(fileName, fileSize, midiFile)
            } ?: createErrorInfo(fileName, fileSize, "Impossible d'ouvrir le fichier")

        } catch (e: SecurityException) {
            createErrorInfo("", 0, "Acces refuse")
        } catch (e: Exception) {
            createErrorInfo("", 0, "Fichier introuvable ou corrompu")
        }
    }

    private fun getFileInfo(uri: Uri): Pair<String, Long> {
        var fileName = "Fichier MIDI"
        var fileSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) { }

        return Pair(fileName, fileSize)
    }

    private fun parseMidiFile(fileName: String, fileSize: Long, midiFile: MidiFile): MidiFileInfo {
        // Collecter les informations
        val tempos = mutableListOf<Double>()
        val timeSignatures = mutableListOf<String>()
        val channelData = mutableMapOf<Int, ChannelData>()
        val trackNames = mutableListOf<String>()
        var copyright: String? = null
        var maxTick = 0L

        // Parcourir toutes les pistes
        midiFile.tracks.forEach { track ->
            for (event in track.events) {
                // Mettre a jour la position maximale
                if (event.tick > maxTick) {
                    maxTick = event.tick
                }

                when (event) {
                    is Tempo -> {
                        tempos.add(event.bpm.toDouble())
                    }
                    is TimeSignature -> {
                        timeSignatures.add("${event.numerator}/${event.realDenominator}")
                    }
                    is TrackName -> {
                        val name = event.trackName?.trim()
                        if (!name.isNullOrBlank()) {
                            trackNames.add(name)
                        }
                    }
                    is CopyrightNotice -> {
                        copyright = event.notice
                    }
                    is NoteOn -> {
                        if (event.velocity > 0) {
                            val data = channelData.getOrPut(event.channel) { ChannelData() }
                            data.noteCount++
                        }
                    }
                    is ProgramChange -> {
                        val data = channelData.getOrPut(event.channel) { ChannelData() }
                        data.programs.add(event.programNumber)
                    }
                }
            }
        }

        // Calculer la duree
        val resolution = midiFile.resolution
        val mainBpm = tempos.firstOrNull() ?: 120.0
        val avgBpm = if (tempos.isNotEmpty()) tempos.average() else 120.0

        // Duree = (ticks / resolution) * (60000 / bpm) millisecondes
        val durationMs = if (resolution > 0 && avgBpm > 0) {
            ((maxTick.toDouble() / resolution) * (60000.0 / avgBpm)).toLong()
        } else {
            0L
        }

        // Construire les infos de canaux
        val channels = channelData.map { (channel, data) ->
            val programs = data.programs.toList().distinct().sorted()
            val instrumentNames = if (channel == DRUM_CHANNEL) {
                listOf(context.getString(com.Atom2Universe.app.R.string.gm_drum_channel))
            } else {
                if (programs.isEmpty()) {
                    listOf(GeneralMidiInstruments.getName(context, 0)) // Piano par defaut
                } else {
                    programs.map { GeneralMidiInstruments.getName(context, it) }
                }
            }

            ChannelInfo(
                channel = channel,
                noteCount = data.noteCount,
                programs = programs,
                instrumentNames = instrumentNames
            )
        }.sortedBy { it.channel }

        // Calculer le nombre total de notes
        val totalNoteCount = channels.sumOf { it.noteCount }

        // Time signature par defaut
        val mainTimeSig = timeSignatures.firstOrNull() ?: "4/4"

        return MidiFileInfo(
            fileName = fileName,
            fileSize = fileSize,
            midiType = if (midiFile.trackCount == 1) 0 else 1,
            trackCount = midiFile.trackCount,
            resolution = resolution,
            durationMs = durationMs,
            mainBpm = mainBpm,
            tempoCount = tempos.size,
            timeSignature = mainTimeSig,
            timeSignatureCount = timeSignatures.size,
            channels = channels,
            totalNoteCount = totalNoteCount,
            trackNames = trackNames.distinct(),
            copyright = copyright
        )
    }

    private fun createErrorInfo(fileName: String, fileSize: Long, error: String): MidiFileInfo {
        return MidiFileInfo(
            fileName = fileName,
            fileSize = fileSize,
            midiType = 0,
            trackCount = 0,
            resolution = 0,
            durationMs = 0,
            mainBpm = 0.0,
            tempoCount = 0,
            timeSignature = "",
            timeSignatureCount = 0,
            channels = emptyList(),
            totalNoteCount = 0,
            trackNames = emptyList(),
            copyright = null,
            error = error
        )
    }

    private data class ChannelData(
        var noteCount: Int = 0,
        val programs: MutableSet<Int> = mutableSetOf()
    )

    // ==================== SF2 Streaming Support ====================

    /**
     * Resultat de l'analyse des instruments requis pour le chargement SF2 streaming
     */
    data class RequiredInstruments(
        val presets: Set<Sf2PresetKey>,      // (bank, program) pairs needed
        val hasPercussion: Boolean,           // True if channel 9 is used
        val programChangesByTime: List<TimedProgramChange>  // For look-ahead loading
    ) {
        /** Nombre total de presets requis */
        val presetCount: Int get() = presets.size

        /** Cle de preset SF2 (bank + program) */
        data class Sf2PresetKey(val bank: Int, val program: Int) {
            /** Cle au format utilise par Sf2File.presetMap */
            fun toMapKey(): String = "$bank:$program"
        }

        /** Program Change avec timing pour pre-chargement */
        data class TimedProgramChange(
            val timeMs: Long,
            val channel: Int,
            val bank: Int,
            val program: Int
        )
    }

    /**
     * Analyse un fichier MIDI pour extraire les instruments requis (pour SF2 streaming)
     * Supporte Bank Select MSB (CC#0) et LSB (CC#32)
     *
     * @param uriString URI du fichier MIDI
     * @return RequiredInstruments contenant les presets necessaires
     */
    fun analyzeRequiredInstruments(uriString: String): RequiredInstruments {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val midiFile = MidiFile(stream)
                extractRequiredInstruments(midiFile)
            } ?: RequiredInstruments(emptySet(), false, emptyList())
        } catch (e: Exception) {
            RequiredInstruments(emptySet(), false, emptyList())
        }
    }

    /**
     * Analyse un fichier MIDI depuis un chemin fichier
     */
    fun analyzeRequiredInstrumentsFromPath(filePath: String): RequiredInstruments {
        return try {
            val file = java.io.File(filePath)
            file.inputStream().use { stream ->
                val midiFile = MidiFile(stream)
                extractRequiredInstruments(midiFile)
            }
        } catch (e: Exception) {
            RequiredInstruments(emptySet(), false, emptyList())
        }
    }

    private fun extractRequiredInstruments(midiFile: MidiFile): RequiredInstruments {
        val presets = mutableSetOf<RequiredInstruments.Sf2PresetKey>()
        val timedChanges = mutableListOf<RequiredInstruments.TimedProgramChange>()

        // Track bank select per channel (default bank 0)
        val channelBankMSB = IntArray(16)  // CC#0 Bank Select MSB
        val channelBankLSB = IntArray(16)  // CC#32 Bank Select LSB
        val channelProgram = IntArray(16)  // Current program per channel
        val channelHasNotes = BooleanArray(16) { false }

        // Calculate timing info
        val resolution = midiFile.resolution

        // Tempo changes for accurate timing
        val tempoChanges = mutableListOf<Pair<Long, Double>>()  // tick -> tempo

        // First pass: collect tempo changes
        midiFile.tracks.forEach { track ->
            for (event in track.events) {
                if (event is Tempo) {
                    tempoChanges.add(event.tick to event.mpqn.toDouble())
                }
            }
        }
        tempoChanges.sortBy { it.first }

        // Function to convert tick to milliseconds
        fun tickToMs(tick: Long): Long {
            var ms = 0.0
            var lastTick = 0L
            var tempo = 500000.0  // Default 120 BPM

            for ((changeTick, newTempo) in tempoChanges) {
                if (changeTick > tick) break
                ms += (changeTick - lastTick) * tempo / resolution / 1000.0
                lastTick = changeTick
                tempo = newTempo
            }
            ms += (tick - lastTick) * tempo / resolution / 1000.0
            return ms.toLong()
        }

        // Second pass: collect program changes and note usage
        midiFile.tracks.forEach { track ->
            for (event in track.events) {
                when (event) {
                    is Controller -> {
                        val channel = event.channel
                        when (event.controllerType) {
                            0 -> channelBankMSB[channel] = event.value  // Bank Select MSB
                            32 -> channelBankLSB[channel] = event.value  // Bank Select LSB
                        }
                    }
                    is ProgramChange -> {
                        val channel = event.channel
                        val program = event.programNumber
                        channelProgram[channel] = program

                        // Calculate full bank number (MSB * 128 + LSB)
                        // For GM/GS, usually just MSB is used
                        val bank = if (channel == DRUM_CHANNEL) {
                            128  // Standard drum bank
                        } else {
                            channelBankMSB[channel]
                        }

                        val key = RequiredInstruments.Sf2PresetKey(bank, program)
                        presets.add(key)

                        timedChanges.add(RequiredInstruments.TimedProgramChange(
                            timeMs = tickToMs(event.tick),
                            channel = channel,
                            bank = bank,
                            program = program
                        ))
                    }
                    is NoteOn -> {
                        if (event.velocity > 0) {
                            val channel = event.channel
                            channelHasNotes[channel] = true

                            // If no program change before first note, use default (bank 0, program 0)
                            if (!presets.any { it.bank == channelBankMSB[channel] && it.program == channelProgram[channel] }) {
                                val bank = if (channel == DRUM_CHANNEL) 128 else channelBankMSB[channel]
                                presets.add(RequiredInstruments.Sf2PresetKey(bank, channelProgram[channel]))
                            }
                        }
                    }
                }
            }
        }

        // Check for percussion
        val hasPercussion = channelHasNotes[DRUM_CHANNEL]

        // Add default drum kit if percussion is used but no explicit program change
        if (hasPercussion && presets.none { it.bank == 128 }) {
            presets.add(RequiredInstruments.Sf2PresetKey(128, 0))  // Standard drum kit
        }

        // Sort timed changes by time
        timedChanges.sortBy { it.timeMs }

        return RequiredInstruments(
            presets = presets,
            hasPercussion = hasPercussion,
            programChangesByTime = timedChanges
        )
    }

    // ==================== Phase 3: Instrument Timeline Builder ====================

    /**
     * Result of timeline analysis including both required instruments and timeline
     */
    data class TimelineAnalysisResult(
        val requiredInstruments: RequiredInstruments,
        val instrumentTimeline: InstrumentTimeline
    )

    /**
     * Analyze a MIDI file and build both required instruments and timeline.
     * This is used for Phase 3 intelligent preloading.
     *
     * @param uriString URI of the MIDI file
     * @return TimelineAnalysisResult with instruments and timeline
     */
    fun analyzeWithTimeline(uriString: String): TimelineAnalysisResult? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val midiFile = MidiFile(stream)
                buildTimelineFromMidi(midiFile)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Analyze a MIDI file from path and build both required instruments and timeline.
     */
    fun analyzeWithTimelineFromPath(filePath: String): TimelineAnalysisResult? {
        return try {
            val file = java.io.File(filePath)
            file.inputStream().use { stream ->
                val midiFile = MidiFile(stream)
                buildTimelineFromMidi(midiFile)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build both RequiredInstruments and InstrumentTimeline from a parsed MIDI file.
     */
    private fun buildTimelineFromMidi(midiFile: MidiFile): TimelineAnalysisResult {
        val presets = mutableSetOf<RequiredInstruments.Sf2PresetKey>()
        val timedChanges = mutableListOf<RequiredInstruments.TimedProgramChange>()
        val timeline = InstrumentTimeline()

        // Track bank select per channel (default bank 0)
        val channelBankMSB = IntArray(16)
        val channelBankLSB = IntArray(16)
        val channelProgram = IntArray(16)
        val channelHasNotes = BooleanArray(16) { false }

        // Active notes tracking for duration calculation
        // Key: (channel << 8) | note, Value: start time in ms
        val activeNotes = mutableMapOf<Int, Long>()

        // Calculate timing info
        val resolution = midiFile.resolution

        // Tempo changes for accurate timing
        val tempoChanges = mutableListOf<Pair<Long, Double>>()

        // First pass: collect tempo changes
        midiFile.tracks.forEach { track ->
            for (event in track.events) {
                if (event is Tempo) {
                    tempoChanges.add(event.tick to event.mpqn.toDouble())
                }
            }
        }
        tempoChanges.sortBy { it.first }

        // Track max tick for duration
        var maxTick = 0L

        // Function to convert tick to milliseconds
        fun tickToMs(tick: Long): Long {
            var ms = 0.0
            var lastTick = 0L
            var tempo = 500000.0

            for ((changeTick, newTempo) in tempoChanges) {
                if (changeTick > tick) break
                ms += (changeTick - lastTick) * tempo / resolution / 1000.0
                lastTick = changeTick
                tempo = newTempo
            }
            ms += (tick - lastTick) * tempo / resolution / 1000.0
            return ms.toLong()
        }

        // Collect all events with absolute ticks
        data class AbsoluteEvent(val tick: Long, val event: com.leff.midi.event.MidiEvent)
        val allEvents = mutableListOf<AbsoluteEvent>()

        midiFile.tracks.forEach { track ->
            var absoluteTick = 0L
            for (event in track.events) {
                absoluteTick += event.delta
                allEvents.add(AbsoluteEvent(absoluteTick, event))
                if (absoluteTick > maxTick) maxTick = absoluteTick
            }
        }

        // Sort by tick
        allEvents.sortBy { it.tick }

        // Second pass: collect program changes, notes, and build timeline
        for (absEvent in allEvents) {
            val timeMs = tickToMs(absEvent.tick)

            when (val event = absEvent.event) {
                is Controller -> {
                    val channel = event.channel
                    when (event.controllerType) {
                        0 -> channelBankMSB[channel] = event.value
                        32 -> channelBankLSB[channel] = event.value
                    }
                }
                is ProgramChange -> {
                    val channel = event.channel
                    val program = event.programNumber
                    channelProgram[channel] = program

                    val bank = if (channel == DRUM_CHANNEL) {
                        128
                    } else {
                        channelBankMSB[channel]
                    }

                    val key = RequiredInstruments.Sf2PresetKey(bank, program)
                    presets.add(key)

                    timedChanges.add(RequiredInstruments.TimedProgramChange(
                        timeMs = timeMs,
                        channel = channel,
                        bank = bank,
                        program = program
                    ))

                    // Add to timeline
                    timeline.addProgramChange(timeMs, channel, bank, program)
                }
                is NoteOn -> {
                    val channel = event.channel
                    val note = event.noteValue
                    val velocity = event.velocity

                    if (velocity > 0) {
                        channelHasNotes[channel] = true

                        // Track note start for duration calculation
                        val noteKey = (channel shl 8) or note
                        activeNotes[noteKey] = timeMs

                        // Ensure preset is tracked
                        val bank = if (channel == DRUM_CHANNEL) 128 else channelBankMSB[channel]
                        val key = RequiredInstruments.Sf2PresetKey(bank, channelProgram[channel])
                        if (!presets.contains(key)) {
                            presets.add(key)
                        }
                    } else {
                        // Note On with velocity 0 = Note Off
                        val noteKey = (channel shl 8) or note
                        val startTime = activeNotes.remove(noteKey)
                        if (startTime != null) {
                            val durationMs = timeMs - startTime
                            timeline.addNote(startTime, channel, durationMs)
                        }
                    }
                }
                is NoteOff -> {
                    val channel = event.channel
                    val note = event.noteValue
                    val noteKey = (channel shl 8) or note
                    val startTime = activeNotes.remove(noteKey)
                    if (startTime != null) {
                        val durationMs = timeMs - startTime
                        timeline.addNote(startTime, channel, durationMs)
                    }
                }
            }
        }

        // Handle any notes that weren't properly closed
        val durationMs = tickToMs(maxTick)
        for ((noteKey, startTime) in activeNotes) {
            val channel = noteKey shr 8
            val noteDuration = durationMs - startTime
            timeline.addNote(startTime, channel, noteDuration)
        }

        // Set total duration
        timeline.setDuration(durationMs)

        // Check for percussion
        val hasPercussion = channelHasNotes[DRUM_CHANNEL]

        // Add default drum kit if percussion is used but no explicit program change
        if (hasPercussion && presets.none { it.bank == 128 }) {
            presets.add(RequiredInstruments.Sf2PresetKey(128, 0))
        }

        timedChanges.sortBy { it.timeMs }

        return TimelineAnalysisResult(
            requiredInstruments = RequiredInstruments(
                presets = presets,
                hasPercussion = hasPercussion,
                programChangesByTime = timedChanges
            ),
            instrumentTimeline = timeline
        )
    }
}
