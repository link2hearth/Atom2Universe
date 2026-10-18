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

    // Built data: zones of each instrument, global zone already applied
    private val instrumentZones = mutableListOf<List<Sf2ZoneBuilder.Generators>>()

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
                // Sample data is 16-bit signed PCM (bulk copy, borné à ce qui reste réellement dans le fichier)
                val numSamples = min(chunkSize, buffer.capacity() - chunkStart) / 2
                sampleData = ShortArray(numSamples)
                buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sampleData, 0, numSamples)
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

        val count = max(0, inst.size - 1)  // Last entry is terminal

        for (i in 0 until count) {
            val zoneStart = inst[i].bagIndex
            val zoneEnd = inst.getOrNull(i + 1)?.bagIndex ?: ibag.size
            instrumentZones.add(
                Sf2ZoneBuilder.buildZones(
                    zoneStart, zoneEnd, { ibag.getOrNull(it)?.generatorIndex }, igen,
                    Sf2Generator.SAMPLE_ID.id
                )
            )
        }
    }

    private fun buildPresetRegions(): Map<String, Sf2Preset> {
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val count = max(0, phdr.size - 1)  // Last entry is terminal (EOP)

        for (i in 0 until count) {
            val preset = phdr[i]
            val zoneStart = preset.bagIndex
            val zoneEnd = phdr.getOrNull(i + 1)?.bagIndex ?: pbag.size

            val presetZones = Sf2ZoneBuilder.buildZones(
                zoneStart, zoneEnd, { pbag.getOrNull(it)?.generatorIndex }, pgen,
                Sf2Generator.INSTRUMENT.id
            )
            val regions = mutableListOf<Sf2Region>()

            for (presetZone in presetZones) {
                val instrumentIndex = presetZone.instrumentIndex ?: continue
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()

                // Diagnostic: warn if instrument not found or has no zones
                if (instZones.isEmpty()) {
                    android.util.Log.w("Sf2Parser", "  ⚠️ Preset '${preset.name}': instrument index $instrumentIndex has NO zones (total instruments: ${instrumentZones.size})")
                }

                Sf2ZoneBuilder.stripInstrumentOnly(presetZone)
                for (instZone in instZones) {
                    val combined = Sf2ZoneBuilder.combine(instZone, presetZone, sampleHeaders) ?: continue
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
}

/**
 * Exception thrown when SF2 parsing fails
 */
class Sf2ParseException(message: String) : Exception(message)
