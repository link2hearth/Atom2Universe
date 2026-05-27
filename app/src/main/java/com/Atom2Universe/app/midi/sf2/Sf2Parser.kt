package com.Atom2Universe.app.midi.sf2

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Parser for SoundFont 2 (.sf2) files.
 * Parses the RIFF/sfbk structure and builds presets with regions.
 */
class Sf2Parser {

    companion object {
        // RIFF chunk IDs
        private const val RIFF = "RIFF"
        private const val SFBK = "sfbk"
        private const val LIST = "LIST"

        // LIST types
        private const val INFO = "INFO"
        private const val SDTA = "sdta"
        private const val PDTA = "pdta"

        // INFO sub-chunks
        private const val INAM = "INAM"

        // SDTA sub-chunks
        private const val SMPL = "smpl"

        // PDTA sub-chunks
        private const val PHDR = "phdr"
        private const val PBAG = "pbag"
        private const val PGEN = "pgen"
        private const val PMOD = "pmod"
        private const val INST = "inst"
        private const val IBAG = "ibag"
        private const val IGEN = "igen"
        private const val IMOD = "imod"
        private const val SHDR = "shdr"
    }

    // Parsed data
    private var name: String = ""
    private var sampleData: ShortArray = ShortArray(0)
    private val sampleHeaders = mutableListOf<Sf2SampleHeader>()
    private val phdr = mutableListOf<Sf2PresetHeader>()
    private val pbag = mutableListOf<Sf2PresetBag>()
    private val pgen = mutableListOf<Sf2GeneratorEntry>()
    private val inst = mutableListOf<Sf2InstrumentHeader>()
    private val ibag = mutableListOf<Sf2InstrumentBag>()
    private val igen = mutableListOf<Sf2GeneratorEntry>()

    // Built data
    private val instrumentZones = mutableListOf<List<Sf2ZoneData>>()
    private val instrumentGlobals = mutableListOf<Sf2ZoneData?>()

    /**
     * Parses an SF2 file from a file path.
     */
    fun parse(filePath: String): Sf2File {
        return parse(File(filePath))
    }

    /**
     * Parses an SF2 file from a File object.
     */
    fun parse(file: File): Sf2File {
        FileInputStream(file).channel.use { channel ->
            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            return parse(mapped)
        }
    }

    /**
     * Parses an SF2 file from an InputStream.
     */
    fun parse(inputStream: InputStream): Sf2File {
        val bytes = inputStream.use { it.readBytes() }
        return parse(ByteBuffer.wrap(bytes))
    }

    /**
     * Parses an SF2 file from a ByteArray.
     */
    fun parse(data: ByteArray): Sf2File {
        return parse(ByteBuffer.wrap(data))
    }

    /**
     * Parses an SF2 file from a ByteBuffer.
     */
    fun parse(buffer: ByteBuffer): Sf2File {
        // Reset state
        reset()

        val leBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Verify RIFF header
        val riffId = readFourCC(leBuffer)
        if (riffId != RIFF) {
            throw Sf2ParseException("Invalid SF2 header: expected RIFF, got $riffId")
        }

        val riffSize = leBuffer.int
        val sfbkId = readFourCC(leBuffer)
        if (sfbkId != SFBK) {
            throw Sf2ParseException("Invalid SF2 format: expected sfbk, got $sfbkId")
        }

        // Parse chunks
        val limit = min(leBuffer.capacity(), riffSize + 8)
        while (leBuffer.position() + 8 <= limit) {
            val chunkId = readFourCC(leBuffer)
            val chunkSize = leBuffer.int
            val chunkStart = leBuffer.position()

            when (chunkId) {
                LIST -> {
                    val listType = readFourCC(leBuffer)
                    val listStart = leBuffer.position()
                    val listSize = chunkSize - 4

                    when (listType) {
                        INFO -> parseInfo(leBuffer, listStart, listSize)
                        SDTA -> parseSdta(leBuffer, listStart, listSize)
                        PDTA -> parsePdta(leBuffer, listStart, listSize)
                    }
                }
            }

            // Move to next chunk (aligned to word boundary)
            val nextPos = chunkStart + chunkSize + (chunkSize % 2)
            leBuffer.position(max(leBuffer.position(), nextPos))
        }

        // Build instrument zones
        buildInstrumentZones()

        // Build preset regions
        val presets = buildPresetRegions()

        return Sf2File(
            name = name,
            sampleData = sampleData,
            presetMap = presets,
            sampleHeaders = sampleHeaders.toList()
        )
    }

    private fun reset() {
        name = ""
        sampleData = ShortArray(0)
        sampleHeaders.clear()
        phdr.clear()
        pbag.clear()
        pgen.clear()
        inst.clear()
        ibag.clear()
        igen.clear()
        instrumentZones.clear()
        instrumentGlobals.clear()
    }

    private fun readFourCC(buffer: ByteBuffer): String {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readString(buffer: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        // Find null terminator
        val end = bytes.indexOf(0)
        val actualLength = if (end >= 0) end else length
        return String(bytes, 0, actualLength, Charsets.US_ASCII).trim()
    }

    // ==================== INFO Parsing ====================

    private fun parseInfo(buffer: ByteBuffer, start: Int, size: Int) {
        val end = min(buffer.capacity(), start + size)
        buffer.position(start)

        while (buffer.position() + 8 <= end) {
            val id = readFourCC(buffer)
            val chunkSize = buffer.int
            val chunkStart = buffer.position()

            if (id == INAM) {
                name = readString(buffer, chunkSize)
            }

            val nextPos = chunkStart + chunkSize + (chunkSize % 2)
            buffer.position(max(buffer.position(), nextPos))
        }
    }

    // ==================== SDTA Parsing ====================

    private fun parseSdta(buffer: ByteBuffer, start: Int, size: Int) {
        val end = min(buffer.capacity(), start + size)
        buffer.position(start)

        while (buffer.position() + 8 <= end) {
            val id = readFourCC(buffer)
            val chunkSize = buffer.int
            val chunkStart = buffer.position()

            if (id == SMPL) {
                // Sample data is 16-bit signed PCM
                val numSamples = chunkSize / 2
                sampleData = ShortArray(numSamples)

                // Read samples directly
                for (i in 0 until numSamples) {
                    sampleData[i] = buffer.short
                }
            }

            val nextPos = chunkStart + chunkSize + (chunkSize % 2)
            buffer.position(max(buffer.position(), nextPos))
        }
    }

    // ==================== PDTA Parsing ====================

    private fun parsePdta(buffer: ByteBuffer, start: Int, size: Int) {
        val end = min(buffer.capacity(), start + size)
        buffer.position(start)

        while (buffer.position() + 8 <= end) {
            val id = readFourCC(buffer)
            val chunkSize = buffer.int
            val chunkStart = buffer.position()

            when (id) {
                PHDR -> parsePhdr(buffer, chunkStart, chunkSize)
                PBAG -> parsePbag(buffer, chunkStart, chunkSize)
                PGEN -> parsePgen(buffer, chunkStart, chunkSize)
                INST -> parseInst(buffer, chunkStart, chunkSize)
                IBAG -> parseIbag(buffer, chunkStart, chunkSize)
                IGEN -> parseIgen(buffer, chunkStart, chunkSize)
                SHDR -> parseShdr(buffer, chunkStart, chunkSize)
            }

            val nextPos = chunkStart + chunkSize + (chunkSize % 2)
            buffer.position(max(buffer.position(), nextPos))
        }
    }

    private fun parsePhdr(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 38

        for (i in 0 until count) {
            val name = readString(buffer, 20)
            val preset = buffer.short.toInt() and 0xFFFF
            val bank = buffer.short.toInt() and 0xFFFF
            val bagIndex = buffer.short.toInt() and 0xFFFF
            buffer.int  // library (unused)
            buffer.int  // genre (unused)
            buffer.int  // morphology (unused)

            phdr.add(Sf2PresetHeader(name, preset, bank, bagIndex))
        }
    }

    private fun parsePbag(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 4

        for (i in 0 until count) {
            val genIndex = buffer.short.toInt() and 0xFFFF
            val modIndex = buffer.short.toInt() and 0xFFFF
            pbag.add(Sf2PresetBag(genIndex, modIndex))
        }
    }

    private fun parsePgen(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 4

        for (i in 0 until count) {
            val operator = buffer.short.toInt() and 0xFFFF
            val amount = buffer.short.toInt()
            pgen.add(Sf2GeneratorEntry(operator, amount))
        }
    }

    private fun parseInst(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 22

        for (i in 0 until count) {
            val name = readString(buffer, 20)
            val bagIndex = buffer.short.toInt() and 0xFFFF
            inst.add(Sf2InstrumentHeader(name, bagIndex))
        }
    }

    private fun parseIbag(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 4

        for (i in 0 until count) {
            val genIndex = buffer.short.toInt() and 0xFFFF
            val modIndex = buffer.short.toInt() and 0xFFFF
            ibag.add(Sf2InstrumentBag(genIndex, modIndex))
        }
    }

    private fun parseIgen(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 4

        for (i in 0 until count) {
            val operator = buffer.short.toInt() and 0xFFFF
            val amount = buffer.short.toInt()
            igen.add(Sf2GeneratorEntry(operator, amount))
        }
    }

    private fun parseShdr(buffer: ByteBuffer, start: Int, size: Int) {
        buffer.position(start)
        val count = size / 46

        for (i in 0 until count) {
            val name = readString(buffer, 20)
            val sampleStart = buffer.int.toLong() and 0xFFFFFFFFL
            val sampleEnd = buffer.int.toLong() and 0xFFFFFFFFL
            val startLoop = buffer.int.toLong() and 0xFFFFFFFFL
            val endLoop = buffer.int.toLong() and 0xFFFFFFFFL
            val sampleRate = buffer.int
            val originalPitch = buffer.get().toInt() and 0xFF
            val pitchCorrection = buffer.get().toInt()
            val sampleLink = buffer.short.toInt() and 0xFFFF
            val sampleType = buffer.short.toInt() and 0xFFFF

            sampleHeaders.add(
                Sf2SampleHeader(
                    name, sampleStart, sampleEnd, startLoop, endLoop,
                    sampleRate, originalPitch, pitchCorrection, sampleLink, sampleType
                )
            )
        }
    }

    // ==================== Zone Building ====================

    private fun buildInstrumentZones() {
        instrumentZones.clear()
        instrumentGlobals.clear()

        val count = max(0, inst.size - 1)  // Last entry is terminal

        for (i in 0 until count) {
            val instrument = inst[i]
            val nextInstrument = inst.getOrNull(i + 1)
            val zoneStart = instrument.bagIndex
            val zoneEnd = nextInstrument?.bagIndex ?: ibag.size

            var globalZone: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val zones = mutableListOf<Sf2ZoneData>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = ibag.getOrNull(zoneIndex) ?: continue
                val nextBag = ibag.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: igen.size

                val zoneData = collectGeneratorValues(igen, genStart, genEnd)

                // If no sampleID, this is a global zone
                if (zoneData.sampleId == null) {
                    globalZone = mergeZoneData(globalZone, zoneData)
                } else {
                    val merged = mergeZoneData(globalZone, zoneData)
                    val withHeader = merged.copy(
                        sampleHeader = sampleHeaders.getOrNull(merged.sampleId ?: 0)
                    )
                    zones.add(withHeader)
                }
            }

            instrumentGlobals.add(globalZone)
            instrumentZones.add(zones)
        }
    }

    private fun buildPresetRegions(): Map<String, Sf2Preset> {
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val count = max(0, phdr.size - 1)  // Last entry is terminal (EOP)

        for (i in 0 until count) {
            val preset = phdr[i]
            val nextPreset = phdr.getOrNull(i + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: pbag.size

            var presetGlobal: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val regions = mutableListOf<Sf2Region>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = pbag.getOrNull(zoneIndex) ?: continue
                val nextBag = pbag.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: pgen.size

                val zoneData = collectGeneratorValues(pgen, genStart, genEnd)

                // If no instrument reference, this is a global zone
                if (zoneData.instrumentIndex == null) {
                    presetGlobal = mergeZoneData(presetGlobal, zoneData)
                    continue
                }

                val instrumentIndex = zoneData.instrumentIndex
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()
                instrumentGlobals.getOrNull(instrumentIndex)
                    ?: Sf2ZoneData.createDefaults()

                // Diagnostic: warn if instrument not found or has no zones
                if (instZones.isEmpty()) {
                    android.util.Log.w("Sf2Parser", "  ⚠️ Preset '${preset.name}' zone $zoneIndex: instrument index $instrumentIndex has NO zones (total instruments: ${instrumentZones.size})")
                }

                val presetApplied = mergeZoneData(presetGlobal, zoneData)

                for (instZone in instZones) {
                    // instZone already has instrument global merged in (from buildInstrumentZones),
                    // so we merge directly with presetApplied to avoid double-applying
                    // additive generators (attenuation, tuning, offsets)
                    val combined = mergeZoneData(presetApplied, instZone)
                    val region = Sf2Region.fromZoneData(combined, sampleData, sampleHeaders)
                    if (region != null) {
                        regions.add(region)
                    }
                }
            }

            val key = preset.getKey()

            // Diagnostic: warn if preset has no regions
            if (regions.isEmpty()) {
                android.util.Log.w("Sf2Parser", "⚠️ Preset '${preset.name}' (bank:${preset.bank}, program:${preset.preset}) has 0 regions!")
                android.util.Log.w("Sf2Parser", "  Zone range: $zoneStart until $zoneEnd (${zoneEnd - zoneStart} zones)")
            } else {
                android.util.Log.d("Sf2Parser", "✓ Preset '${preset.name}' loaded with ${regions.size} regions")
            }

            presetMap[key] = Sf2Preset(
                name = preset.name,
                bank = preset.bank,
                program = preset.preset,
                regions = regions
            )
        }

        return presetMap
    }

    // ==================== Generator Collection ====================

    private fun collectGeneratorValues(
        generators: List<Sf2GeneratorEntry>,
        start: Int,
        end: Int
    ): Sf2ZoneData {
        var keyRange: IntRange = 0..127
        var velRange: IntRange = 0..127
        var startOffset = 0
        var endOffset = 0
        var startLoopOffset = 0
        var endLoopOffset = 0
        var coarseTune = 0
        var fineTune = 0
        var scaleTuning: Int? = null
        var attenuation = 0
        var pan: Float? = null
        var sampleModes = 0
        var rootKey: Int? = null
        var exclusiveClass = 0
        var reverbSend: Float? = null
        var chorusSend: Float? = null
        var sampleId: Int? = null
        var instrumentIndex: Int? = null
        var forcedKeyNum: Int? = null
        var forcedVelocity: Int? = null

        // Filter parameters
        var filterFc: Int? = null
        var filterQ: Int? = null

        // Vibrato LFO parameters
        var vibLfoDelay: Int? = null
        var vibLfoFreq: Int? = null
        var vibLfoToPitch: Int? = null

        // Modulation LFO parameters
        var modLfoDelay: Int? = null
        var modLfoFreq: Int? = null
        var modLfoToPitch: Int? = null
        var modLfoToFilterFc: Int? = null
        var modLfoToVolume: Int? = null

        // Volume Envelope data
        var envDelay: Int? = null
        var envAttack: Int? = null
        var envHold: Int? = null
        var envDecay: Int? = null
        var envKeynumToHold: Int? = null
        var envKeynumToDecay: Int? = null

        // Modulation Envelope data
        var modEnvDelay: Int? = null
        var modEnvAttack: Int? = null
        var modEnvHold: Int? = null
        var modEnvDecay: Int? = null
        var modEnvSustain: Int? = null
        var modEnvRelease: Int? = null
        var modEnvKeynumToHold: Int? = null
        var modEnvKeynumToDecay: Int? = null
        var modEnvToPitch: Int? = null
        var modEnvToFilterFc: Int? = null
        var envSustain: Int? = null
        var envRelease: Int? = null

        val limit = min(generators.size, end)
        for (index in max(0, start) until limit) {
            val entry = generators[index]
            val amount = entry.amount
            // Convert to unsigned for generators that require it
            val unsignedAmount = amount and 0xFFFF

            when (entry.operator) {
                Sf2Generator.START_ADDRS_OFFSET.id -> startOffset += amount
                Sf2Generator.END_ADDRS_OFFSET.id -> endOffset += amount
                Sf2Generator.STARTLOOP_ADDRS_OFFSET.id -> startLoopOffset += amount
                Sf2Generator.ENDLOOP_ADDRS_OFFSET.id -> endLoopOffset += amount
                Sf2Generator.START_ADDRS_COARSE_OFFSET.id -> startOffset += amount * 32768
                Sf2Generator.END_ADDRS_COARSE_OFFSET.id -> endOffset += amount * 32768
                Sf2Generator.STARTLOOP_ADDRS_COARSE_OFFSET.id -> startLoopOffset += amount * 32768
                Sf2Generator.ENDLOOP_ADDRS_COARSE_OFFSET.id -> endLoopOffset += amount * 32768

                Sf2Generator.INITIAL_FILTER_FC.id -> filterFc = unsignedAmount  // Unsigned: 0-13500+ cents
                Sf2Generator.INITIAL_FILTER_Q.id -> filterQ = amount  // Signed: -960 to +960 cB

                // LFO generators
                Sf2Generator.MOD_LFO_TO_PITCH.id -> modLfoToPitch = amount
                Sf2Generator.VIB_LFO_TO_PITCH.id -> vibLfoToPitch = amount
                Sf2Generator.MOD_LFO_TO_FILTER_FC.id -> modLfoToFilterFc = amount
                Sf2Generator.MOD_LFO_TO_VOLUME.id -> modLfoToVolume = amount
                Sf2Generator.DELAY_MOD_LFO.id -> modLfoDelay = amount
                Sf2Generator.FREQ_MOD_LFO.id -> modLfoFreq = amount
                Sf2Generator.DELAY_VIB_LFO.id -> vibLfoDelay = amount
                Sf2Generator.FREQ_VIB_LFO.id -> vibLfoFreq = amount

                Sf2Generator.CHORUS_EFFECTS_SEND.id -> chorusSend = amount / 1000f
                Sf2Generator.REVERB_EFFECTS_SEND.id -> reverbSend = amount / 1000f
                Sf2Generator.PAN.id -> pan = amount / 500f  // -500 to +500 -> -1.0 to +1.0

                // Modulation Envelope generators
                Sf2Generator.MOD_ENV_TO_PITCH.id -> modEnvToPitch = amount
                Sf2Generator.MOD_ENV_TO_FILTER_FC.id -> modEnvToFilterFc = amount
                Sf2Generator.DELAY_MOD_ENV.id -> modEnvDelay = amount
                Sf2Generator.ATTACK_MOD_ENV.id -> modEnvAttack = amount
                Sf2Generator.HOLD_MOD_ENV.id -> modEnvHold = amount
                Sf2Generator.DECAY_MOD_ENV.id -> modEnvDecay = amount
                Sf2Generator.SUSTAIN_MOD_ENV.id -> modEnvSustain = amount
                Sf2Generator.RELEASE_MOD_ENV.id -> modEnvRelease = amount
                Sf2Generator.KEYNUM_TO_MOD_ENV_HOLD.id -> modEnvKeynumToHold = amount
                Sf2Generator.KEYNUM_TO_MOD_ENV_DECAY.id -> modEnvKeynumToDecay = amount

                // Volume Envelope generators
                Sf2Generator.DELAY_VOL_ENV.id -> envDelay = amount
                Sf2Generator.ATTACK_VOL_ENV.id -> envAttack = amount
                Sf2Generator.HOLD_VOL_ENV.id -> envHold = amount
                Sf2Generator.DECAY_VOL_ENV.id -> envDecay = amount
                Sf2Generator.SUSTAIN_VOL_ENV.id -> envSustain = amount
                Sf2Generator.RELEASE_VOL_ENV.id -> envRelease = amount
                Sf2Generator.KEYNUM_TO_VOL_ENV_HOLD.id -> envKeynumToHold = amount
                Sf2Generator.KEYNUM_TO_VOL_ENV_DECAY.id -> envKeynumToDecay = amount

                Sf2Generator.INSTRUMENT.id -> instrumentIndex = amount and 0xFFFF  // Unsigned word
                Sf2Generator.KEY_RANGE.id -> keyRange = entry.decodeRange()
                Sf2Generator.VEL_RANGE.id -> velRange = entry.decodeRange()
                Sf2Generator.INITIAL_ATTENUATION.id -> attenuation += amount
                Sf2Generator.COARSE_TUNE.id -> coarseTune += amount
                Sf2Generator.FINE_TUNE.id -> fineTune += amount
                Sf2Generator.SAMPLE_ID.id -> sampleId = amount and 0xFFFF  // Unsigned word
                Sf2Generator.SAMPLE_MODES.id -> sampleModes = amount
                Sf2Generator.SCALE_TUNING.id -> scaleTuning = amount
                Sf2Generator.EXCLUSIVE_CLASS.id -> exclusiveClass = amount
                Sf2Generator.OVERRIDING_ROOT_KEY.id -> rootKey = amount
                Sf2Generator.KEYNUM.id -> forcedKeyNum = amount
                Sf2Generator.VELOCITY.id -> forcedVelocity = amount
            }
        }

        return Sf2ZoneData(
            keyRange = keyRange,
            velRange = velRange,
            startOffset = startOffset,
            endOffset = endOffset,
            startLoopOffset = startLoopOffset,
            endLoopOffset = endLoopOffset,
            coarseTune = coarseTune,
            fineTune = fineTune,
            scaleTuning = scaleTuning ?: 100,
            attenuation = attenuation,
            pan = pan,
            sampleModes = sampleModes,
            rootKey = rootKey,
            exclusiveClass = exclusiveClass,
            reverbSend = reverbSend,
            chorusSend = chorusSend,
            volumeEnvelope = Sf2EnvelopeData(
                delay = envDelay,
                attack = envAttack,
                hold = envHold,
                decay = envDecay,
                sustain = envSustain,
                release = envRelease,
                keynumToHold = envKeynumToHold,
                keynumToDecay = envKeynumToDecay
            ),
            sampleId = sampleId,
            instrumentIndex = instrumentIndex,
            filterFc = filterFc,
            filterQ = filterQ,
            vibLfoDelay = vibLfoDelay,
            vibLfoFreq = vibLfoFreq,
            vibLfoToPitch = vibLfoToPitch,
            modLfoDelay = modLfoDelay,
            modLfoFreq = modLfoFreq,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilterFc = modLfoToFilterFc,
            modLfoToVolume = modLfoToVolume,
            modEnvelope = Sf2EnvelopeData(
                delay = modEnvDelay,
                attack = modEnvAttack,
                hold = modEnvHold,
                decay = modEnvDecay,
                sustain = modEnvSustain,
                release = modEnvRelease,
                keynumToHold = modEnvKeynumToHold,
                keynumToDecay = modEnvKeynumToDecay
            ),
            modEnvToPitch = modEnvToPitch,
            modEnvToFilterFc = modEnvToFilterFc,
            forcedKeyNum = forcedKeyNum,
            forcedVelocity = forcedVelocity
        )
    }

    private fun mergeZoneData(base: Sf2ZoneData, source: Sf2ZoneData): Sf2ZoneData {
        // Key and velocity ranges are intersected
        val keyLo = max(base.keyRange.first, source.keyRange.first)
        val keyHi = min(base.keyRange.last, source.keyRange.last)
        val velLo = max(base.velRange.first, source.velRange.first)
        val velHi = min(base.velRange.last, source.velRange.last)

        // Limite l'atténuation totale à 120 cB (12 dB) pour éviter les presets muets
        // Certains SF2 ont des zones globales avec des atténuations excessives qui s'additionnent
        // 120 cB → gain 25% → avec globalGain 0.35 = 9% volume final (audible)
        val totalAttenuation = (base.attenuation + source.attenuation).coerceAtMost(120)

        return base.copy(
            keyRange = keyLo..max(keyLo, keyHi),
            velRange = velLo..max(velLo, velHi),
            startOffset = base.startOffset + source.startOffset,
            endOffset = base.endOffset + source.endOffset,
            startLoopOffset = base.startLoopOffset + source.startLoopOffset,
            endLoopOffset = base.endLoopOffset + source.endLoopOffset,
            coarseTune = base.coarseTune + source.coarseTune,
            fineTune = base.fineTune + source.fineTune,
            scaleTuning = source.scaleTuning,
            attenuation = totalAttenuation,
            pan = source.pan ?: base.pan,
            sampleModes = source.sampleModes.takeIf { it != 0 } ?: base.sampleModes,
            rootKey = source.rootKey ?: base.rootKey,
            exclusiveClass = source.exclusiveClass.takeIf { it != 0 } ?: base.exclusiveClass,
            reverbSend = source.reverbSend ?: base.reverbSend,
            chorusSend = source.chorusSend ?: base.chorusSend,
            volumeEnvelope = mergeEnvelope(base.volumeEnvelope, source.volumeEnvelope),
            sampleId = source.sampleId ?: base.sampleId,
            instrumentIndex = source.instrumentIndex ?: base.instrumentIndex,
            sampleHeader = source.sampleHeader ?: base.sampleHeader,
            filterFc = source.filterFc ?: base.filterFc,
            filterQ = source.filterQ ?: base.filterQ,
            // Vibrato LFO - source overrides base
            vibLfoDelay = source.vibLfoDelay ?: base.vibLfoDelay,
            vibLfoFreq = source.vibLfoFreq ?: base.vibLfoFreq,
            vibLfoToPitch = source.vibLfoToPitch ?: base.vibLfoToPitch,
            // Modulation LFO - source overrides base
            modLfoDelay = source.modLfoDelay ?: base.modLfoDelay,
            modLfoFreq = source.modLfoFreq ?: base.modLfoFreq,
            modLfoToPitch = source.modLfoToPitch ?: base.modLfoToPitch,
            modLfoToFilterFc = source.modLfoToFilterFc ?: base.modLfoToFilterFc,
            modLfoToVolume = source.modLfoToVolume ?: base.modLfoToVolume,
            // Modulation Envelope
            modEnvelope = mergeEnvelope(base.modEnvelope, source.modEnvelope),
            modEnvToPitch = source.modEnvToPitch ?: base.modEnvToPitch,
            modEnvToFilterFc = source.modEnvToFilterFc ?: base.modEnvToFilterFc,
            // Force key/velocity generators (override)
            forcedKeyNum = source.forcedKeyNum ?: base.forcedKeyNum,
            forcedVelocity = source.forcedVelocity ?: base.forcedVelocity
        )
    }

    private fun mergeEnvelope(base: Sf2EnvelopeData, source: Sf2EnvelopeData): Sf2EnvelopeData {
        return Sf2EnvelopeData(
            delay = source.delay ?: base.delay,
            attack = source.attack ?: base.attack,
            hold = source.hold ?: base.hold,
            decay = source.decay ?: base.decay,
            sustain = source.sustain ?: base.sustain,
            release = source.release ?: base.release,
            keynumToHold = source.keynumToHold ?: base.keynumToHold,
            keynumToDecay = source.keynumToDecay ?: base.keynumToDecay
        )
    }
}

/**
 * Exception thrown when SF2 parsing fails
 */
class Sf2ParseException(message: String) : Exception(message)
