package com.Atom2Universe.app.sf2creator.data

import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.BASE_LOOP_END
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.BASE_LOOP_START
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.BASE_ORIGINAL_PITCH
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_ENDLOOP_COARSE_OFFSET
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_ENDLOOP_OFFSET
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_INSTRUMENT
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_SAMPLE_ID
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_STARTLOOP_COARSE_OFFSET
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_STARTLOOP_OFFSET
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.LOOP_OFFSET_GENERATORS
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.OFFSET_GENERATORS
import com.Atom2Universe.app.sf2creator.data.db.entities.ModificationFlags
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ModulatorEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2InfoField
import com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedSample
import com.Atom2Universe.app.sf2creator.writer.Sf2Document
import com.Atom2Universe.app.sf2creator.writer.Sf2DocumentWriter

/** Everything stored for a project, as read from the database. */
class Sf2ProjectSnapshot(
    val projectName: String,
    val programs: List<Sf2ProgramEntity>,
    val presetZones: List<Sf2PresetEntity>,
    val instruments: List<Sf2InstrumentEntity>,
    val samples: List<Sf2SampleEntity>,
    val modulators: List<Sf2ModulatorEntity>,
    /** Copy of the imported SF2, whose INFO fields are kept. */
    val sourceFilePath: String? = null,
    /** Project name at import: the source's own name is kept until the project is renamed. */
    val importProjectName: String? = null
)

/**
 * Turns a project into an [Sf2Document].
 *
 * Imported samples whose audio was not edited are copied byte for byte from the imported
 * file, with their original header (loop points, pitch, stereo link). Zones are written
 * as described in [Sf2ZoneGenerators].
 */
object Sf2ProjectExporter {

    private const val SOFTWARE = "A2U SF2 Creator"
    private const val TYPE_MONO = 1

    /** Reads a WAV file of the project: frame count, and the frames themselves. */
    interface WavAccess {
        fun frameCount(path: String): Int
        fun identity(path: String): String
        fun load(path: String): ShortArray?
        /** Loop points to write for a sample recorded in the app, from its audio. */
        fun prepareLoop(data: ShortArray, sample: Sf2SampleEntity): Sf2Document.LoadedPcm =
            Sf2Document.LoadedPcm(data)
    }

    /**
     * @param readSource parses an SF2 file (structure only), or returns null
     * @return the document, or null when the project has no playable sample
     */
    fun build(
        snapshot: Sf2ProjectSnapshot,
        readSource: (String) -> Sf2ParseResult?,
        wav: WavAccess
    ): Sf2Document? {
        val sources = HashMap<String, SourceIndex?>()
        fun source(path: String): SourceIndex? = sources.getOrPut(path) { readSource(path)?.let { SourceIndex(path, it) } }

        val zonesByInstrument = snapshot.samples.groupBy { it.instrumentId }.mapValues { (_, list) -> list.sortedBy { it.id } }
        val instrumentsById = snapshot.instruments.associateBy { it.id }
        val modsBySample = snapshot.modulators.filter { it.sampleId != null }.groupBy { it.sampleId!! }
        val modsByPreset = snapshot.modulators.filter { it.presetId != null }.groupBy { it.presetId!! }
        val modsByProgram = snapshot.modulators.filter { it.programId != null }.groupBy { it.programId!! }
        val modsByInstrument = snapshot.modulators
            .filter { it.instrumentId != null && it.sampleId == null }
            .groupBy { it.instrumentId!! }

        val samples = mutableListOf<Sf2Document.Sample>()
        val sampleIndex = HashMap<String, Int>()
        // For stereo links: output index -> (source, other channel's header index in the source)
        val pendingLinks = HashMap<Int, Pair<SourceIndex, Int>>()
        val instruments = mutableListOf<Sf2Document.Instrument>()
        val instrumentIndex = HashMap<Long, Int>()

        fun instrumentZone(s: Sf2SampleEntity): Sf2Document.Zone? {
            val generators = Sf2ZoneGenerators.sampleZoneGenerators(s)
            val baseline = Sf2ZoneGenerators.decode(s.importBaseline)
            val audioEdited = (s.modificationFlags and ModificationFlags.MOD_FLAG_AUDIO) != 0

            val src = if (!s.isExtracted && s.sourceFilePath != null) source(s.sourceFilePath) else null
            val header = src?.headerFor(s)
            val index: Int
            if (header != null && src != null) {
                // Original audio and header; an edited loop becomes a loop offset of the zone
                index = sampleIndex.getOrPut(src.key(header)) {
                    samples.add(sourceSample(src, header, s))
                    if ((header.sampleType and 0x7FFF) != TYPE_MONO) pendingLinks[samples.size - 1] = src to header.sampleLink
                    samples.size - 1
                }
                val frames = (header.end - header.start).toInt().coerceAtLeast(1)
                val importedStart = baseline?.get(BASE_LOOP_START) ?: (header.loopStart - header.start).toInt().coerceAtLeast(0)
                val importedEnd = baseline?.get(BASE_LOOP_END) ?: (header.loopEnd - header.start - 1).toInt().coerceIn(0, frames - 1)
                if (s.loopStart != importedStart || s.loopEnd != importedEnd) {
                    generators.keys.removeAll(LOOP_OFFSET_GENERATORS)
                    Sf2ZoneGenerators.setOffset(generators, GEN_STARTLOOP_OFFSET, GEN_STARTLOOP_COARSE_OFFSET,
                        s.loopStart - (header.loopStart - header.start))
                    Sf2ZoneGenerators.setOffset(generators, GEN_ENDLOOP_OFFSET, GEN_ENDLOOP_COARSE_OFFSET,
                        s.loopEnd + 1L - (header.loopEnd - header.start))
                }
            } else {
                // Audio from a WAV file (recorded, edited, or imported as WAV): the header
                // carries the zone's own loop
                val audio = audioOf(s, wav) ?: return null
                val key = "wav:${s.audioFilePath}:${s.loopStart}:${s.loopEnd}:${s.hasLoop}:${s.rootNote}:${s.sampleRate}:${s.pitchCorrection}:${s.name}"
                index = sampleIndex.getOrPut(key) {
                    samples.add(wavSample(s, audio, baseline))
                    samples.size - 1
                }
                val loopEdited = baseline == null ||
                    baseline[BASE_LOOP_START] != s.loopStart || baseline[BASE_LOOP_END] != s.loopEnd
                if (loopEdited) generators.keys.removeAll(LOOP_OFFSET_GENERATORS)
            }
            if (audioEdited) generators.keys.removeAll(OFFSET_GENERATORS)

            val mods = modsBySample[s.id].orEmpty().sortedBy { it.id }.map(::modulator)
            return Sf2Document.Zone(Sf2ZoneGenerators.ordered(generators, GEN_SAMPLE_ID to index), mods)
        }

        fun instrumentFor(instrument: Sf2InstrumentEntity): Int? {
            instrumentIndex[instrument.id]?.let { return it }
            val zones = zonesByInstrument[instrument.id].orEmpty().mapNotNull { instrumentZone(it) }
            if (zones.isEmpty()) return null
            val global = Sf2ZoneGenerators.instrumentGlobalGenerators(instrument)
            val globalMods = modsByInstrument[instrument.id].orEmpty().sortedBy { it.id }.map(::modulator)
            val all = if (global.isNotEmpty() || globalMods.isNotEmpty()) {
                listOf(Sf2Document.Zone(Sf2ZoneGenerators.ordered(global), globalMods)) + zones
            } else {
                zones
            }
            instruments.add(Sf2Document.Instrument(instrument.name, all))
            return (instruments.size - 1).also { instrumentIndex[instrument.id] = it }
        }

        fun presetZone(zone: Sf2PresetEntity): Sf2Document.Zone? {
            val instrument = instrumentsById[zone.instrumentId] ?: return null
            val index = instrumentFor(instrument) ?: return null
            val generators = Sf2ZoneGenerators.presetZoneGenerators(zone)
            val mods = modsByPreset[zone.id].orEmpty().sortedBy { it.id }.map(::modulator)
            return Sf2Document.Zone(Sf2ZoneGenerators.ordered(generators, GEN_INSTRUMENT to index), mods)
        }

        val presets = mutableListOf<Sf2Document.Preset>()
        for (program in snapshot.programs) {
            val zones = snapshot.presetZones.filter { it.programId == program.id }.sortedBy { it.id }.mapNotNull { presetZone(it) }
            if (zones.isEmpty()) continue
            val global = Sf2ZoneGenerators.programGlobalGenerators(program)
            val globalMods = modsByProgram[program.id].orEmpty().sortedBy { it.id }.map(::modulator)
            val all = if (global.isNotEmpty() || globalMods.isNotEmpty()) {
                listOf(Sf2Document.Zone(Sf2ZoneGenerators.ordered(global), globalMods)) + zones
            } else {
                zones
            }
            presets.add(Sf2Document.Preset(program.name, program.programNumber, program.bankNumber, all))
        }
        if (presets.isEmpty()) {
            // Projects from before programs existed: each preset zone is a preset
            for (zone in snapshot.presetZones.sortedBy { it.id }) {
                val exported = presetZone(zone) ?: continue
                presets.add(Sf2Document.Preset(zone.name, zone.programNumber, zone.bankNumber, listOf(exported)))
            }
        }
        if (samples.isEmpty()) return null

        resolveLinks(samples, sampleIndex, pendingLinks)

        return Sf2Document(info(snapshot, snapshot.sourceFilePath?.let { source(it) }), presets, instruments, samples)
    }

    private fun modulator(m: Sf2ModulatorEntity) =
        Sf2Document.Modulator(m.srcOper, m.destOper, m.amount, m.amtSrcOper, m.transOper)

    private fun sourceSample(src: SourceIndex, header: Sf2ParsedSample, zone: Sf2SampleEntity): Sf2Document.Sample {
        val frames = (header.end - header.start).toInt()
        val sm24 = if (src.result.sm24DataOffset >= 0) src.result.sm24DataOffset + header.start else -1L
        return Sf2Document.Sample(
            name = zone.name,
            audio = Sf2Document.Audio.FromFile(src.path, src.byteOffset(header), frames, sm24),
            loopStart = header.loopStart - header.start,
            loopEnd = header.loopEnd - header.start,
            sampleRate = zone.sampleRate,
            originalPitch = header.originalPitch,
            pitchCorrection = zone.pitchCorrection,
            link = 0,
            type = header.sampleType
        )
    }

    private fun audioOf(s: Sf2SampleEntity, wav: Sf2ProjectExporter.WavAccess): Sf2Document.Audio.Pcm? {
        if (s.audioFilePath.isEmpty()) return null
        val frames = wav.frameCount(s.audioFilePath)
        if (frames <= 0) return null
        val recordedInApp = s.importBaseline == null && s.sourceFilePath == null
        return Sf2Document.Audio.Pcm(frames, wav.identity(s.audioFilePath)) {
            val data = wav.load(s.audioFilePath) ?: ShortArray(frames)
            if (recordedInApp && s.hasLoop && s.loopEnd > s.loopStart) wav.prepareLoop(data, s) else Sf2Document.LoadedPcm(data)
        }
    }

    private fun wavSample(s: Sf2SampleEntity, audio: Sf2Document.Audio.Pcm, baseline: Map<Int, Int>?): Sf2Document.Sample {
        val looped = s.hasLoop || s.loopEnd > s.loopStart
        return Sf2Document.Sample(
            name = s.name,
            audio = audio,
            loopStart = if (looped) s.loopStart.toLong() else 0L,
            loopEnd = if (looped) (s.loopEnd + 1L).coerceAtMost(audio.frames.toLong()) else audio.frames.toLong(),
            sampleRate = s.sampleRate,
            // An imported zone that relied on the header's pitch keeps it; gen 58 does the rest
            originalPitch = baseline?.get(BASE_ORIGINAL_PITCH) ?: s.rootNote,
            pitchCorrection = s.pitchCorrection,
            link = 0,
            type = TYPE_MONO
        )
    }

    /**
     * Stereo pairs stay linked when both channels are exported from their source; a channel
     * whose partner is missing becomes mono.
     */
    private fun resolveLinks(
        samples: MutableList<Sf2Document.Sample>,
        sampleIndex: Map<String, Int>,
        pending: Map<Int, Pair<SourceIndex, Int>>
    ) {
        for ((index, link) in pending) {
            val (src, partnerHeaderIndex) = link
            val partner = src.result.samples.getOrNull(partnerHeaderIndex)
            val partnerIndex = partner?.let { sampleIndex[src.key(it)] }
            val s = samples[index]
            samples[index] = Sf2Document.Sample(
                s.name, s.audio, s.loopStart, s.loopEnd, s.sampleRate, s.originalPitch, s.pitchCorrection,
                link = partnerIndex ?: 0,
                type = if (partnerIndex != null) s.type else (s.type and 0x8000) or TYPE_MONO
            )
        }
    }

    /**
     * INFO of the imported file (author, copyright, comments...), with the project name
     * once the project has been renamed, and this app added as the editing software.
     */
    private fun info(snapshot: Sf2ProjectSnapshot, source: SourceIndex?): List<Sf2InfoField> {
        val original = source?.result?.info?.fields.orEmpty()
        if (original.isEmpty()) {
            return listOf(
                Sf2DocumentWriter.textField("isng", "EMU8000"),
                Sf2DocumentWriter.textField("INAM", snapshot.projectName),
                Sf2DocumentWriter.textField("ISFT", SOFTWARE)
            )
        }
        val renamed = snapshot.importProjectName == null || snapshot.importProjectName != snapshot.projectName
        val fields = original.map { field ->
            when (field.id) {
                "INAM" -> if (renamed) Sf2DocumentWriter.textField("INAM", snapshot.projectName) else field
                "ISFT" -> Sf2DocumentWriter.textField("ISFT", editedBy(text(field)))
                else -> field
            }
        }.toMutableList()
        if (fields.none { it.id == "ISFT" }) fields.add(Sf2DocumentWriter.textField("ISFT", SOFTWARE))
        return fields
    }

    private fun text(field: Sf2InfoField): String {
        val end = field.data.indexOf(0.toByte()).takeIf { it >= 0 } ?: field.data.size
        return String(field.data, 0, end, Charsets.ISO_8859_1)
    }

    /** ISFT is "creating:modifying" software: keeps the tool that created the file. */
    private fun editedBy(software: String): String {
        val creator = software.substringBefore(':').trim()
        return if (creator.isEmpty() || creator == SOFTWARE) SOFTWARE else "$creator:$SOFTWARE"
    }

    private class SourceIndex(val path: String, val result: Sf2ParseResult) {
        private val byOffset = result.samples.groupBy { byteOffset(it) }

        fun byteOffset(sample: Sf2ParsedSample): Long = result.smplDataOffset + sample.start * 2
        fun key(sample: Sf2ParsedSample) = "src:$path:#${sample.index}"

        /**
         * Header a zone was imported from. Several headers can share the same audio (with
         * other loop points or names): the closest match wins.
         */
        fun headerFor(zone: Sf2SampleEntity): Sf2ParsedSample? {
            val candidates = byOffset[zone.sourceSmplOffset] ?: return null
            if (candidates.size == 1) return candidates[0]
            return candidates.maxByOrNull { h ->
                var score = 0
                if ((h.end - h.start) * 2 == zone.sourceSampleSize) score += 4
                if (h.name.take(20) == zone.name) score += 2
                if ((h.loopStart - h.start).toInt() == zone.loopStart) score += 1
                score
            }
        }
    }
}
