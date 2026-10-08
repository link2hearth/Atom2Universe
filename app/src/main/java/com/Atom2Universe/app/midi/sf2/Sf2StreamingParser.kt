package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer.RequiredInstruments
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * SF2 Streaming Parser - Optimized for large SoundFont files
 *
 * This parser works in two phases:
 * 1. parseMetadata() - Parse all metadata (INFO, PDTA) without loading sample data
 * 2. loadSamplesForPresets() - Load only the samples needed for specific presets
 *
 * This approach reduces memory usage by 80-95% for typical MIDI files.
 */
class Sf2StreamingParser {

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
        private const val INST = "inst"
        private const val IBAG = "ibag"
        private const val IGEN = "igen"
        private const val SHDR = "shdr"

        // Zéros insérés autour de chaque échantillon dans la copie compacte (voir loadSamplesForPresets)
        private const val GUARD_SAMPLES = 8
    }

    /**
     * Phase 1: Parse metadata only, without loading sample data.
     * Returns Sf2Metadata containing all structural information needed for selective loading.
     */
    fun parseMetadata(file: File): Sf2Metadata {
        return RandomAccessFile(file, "r").use { raf ->
            parseMetadataFromRaf(raf, file.absolutePath)
        }
    }

    fun parseMetadata(filePath: String): Sf2Metadata {
        return parseMetadata(File(filePath))
    }

    private fun parseMetadataFromRaf(raf: RandomAccessFile, filePath: String): Sf2Metadata {
        val fileSize = raf.length()

        // Read RIFF header (12 bytes)
        val headerBuffer = ByteArray(12)
        raf.seek(0)
        raf.readFully(headerBuffer)
        val header = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

        val riffId = readFourCCFromArray(headerBuffer, 0)
        if (riffId != RIFF) {
            throw Sf2ParseException("Invalid SF2 header: expected RIFF, got $riffId")
        }

        val riffSize = header.getInt(4)
        val sfbkId = readFourCCFromArray(headerBuffer, 8)
        if (sfbkId != SFBK) {
            throw Sf2ParseException("Invalid SF2 format: expected sfbk, got $sfbkId")
        }

        // Parsed data
        var name = ""
        var smplOffset: Long = 0
        var smplSize: Long = 0
        val sampleHeaders = mutableListOf<Sf2SampleHeader>()
        val phdr = mutableListOf<Sf2PresetHeader>()
        val pbag = mutableListOf<Sf2PresetBag>()
        val pgen = mutableListOf<Sf2GeneratorEntry>()
        val inst = mutableListOf<Sf2InstrumentHeader>()
        val ibag = mutableListOf<Sf2InstrumentBag>()
        val igen = mutableListOf<Sf2GeneratorEntry>()

        // Parse chunks sequentially - only read what we need
        var position = 12L  // After RIFF header
        val endPosition = min(fileSize, riffSize.toLong() + 8)
        val chunkHeaderBuffer = ByteArray(8)

        while (position + 8 <= endPosition) {
            raf.seek(position)
            raf.readFully(chunkHeaderBuffer)

            val chunkId = readFourCCFromArray(chunkHeaderBuffer, 0)
            val chunkSize = ByteBuffer.wrap(chunkHeaderBuffer, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            val chunkDataStart = position + 8

            if (chunkId == LIST) {
                // Read LIST type (4 bytes)
                val listTypeBuffer = ByteArray(4)
                raf.readFully(listTypeBuffer)
                val listType = readFourCCFromArray(listTypeBuffer, 0)
                val listDataStart = chunkDataStart + 4
                val listDataSize = chunkSize - 4

                when (listType) {
                    INFO -> {
                        // INFO chunk is small, read it fully
                        val infoData = ByteArray(listDataSize)
                        raf.readFully(infoData)
                        name = parseInfoForNameFromArray(infoData)
                    }
                    SDTA -> {
                        // Don't read SMPL data, just find its offset
                        val (offset, size) = parseSdtaMetadataStreaming(raf, listDataStart, listDataSize)
                        smplOffset = offset
                        smplSize = size
                    }
                    PDTA -> {
                        // PDTA is essential, read it fully (usually 1-5MB)
                        val pdtaData = ByteArray(listDataSize)
                        raf.readFully(pdtaData)
                        val pdtaBuffer = ByteBuffer.wrap(pdtaData).order(ByteOrder.LITTLE_ENDIAN)
                        parsePdtaFromBuffer(pdtaBuffer, listDataSize, sampleHeaders, phdr, pbag, pgen, inst, ibag, igen)
                    }
                }
            }

            // Move to next chunk (with padding)
            position = chunkDataStart + chunkSize + (chunkSize % 2)
        }

        return Sf2Metadata(
            name = name,
            filePath = filePath,
            smplByteOffset = smplOffset,
            smplByteSize = smplSize,
            sampleHeaders = sampleHeaders.toList(),
            presetHeaders = phdr.toList(),
            presetBags = pbag.toList(),
            presetGenerators = pgen.toList(),
            instrumentHeaders = inst.toList(),
            instrumentBags = ibag.toList(),
            instrumentGenerators = igen.toList()
        )
    }

    /**
     * Phase 2: Load only the samples needed for specific presets.
     *
     * Creates a COMPACT sample array containing only the required samples,
     * with remapped sample headers pointing to the new positions.
     *
     * @param metadata Metadata from parseMetadata()
     * @param requiredPresets Set of (bank, program) pairs to load
     * @return Sf2File with only the required samples loaded
     */
    fun loadSamplesForPresets(
        metadata: Sf2Metadata,
        requiredPresets: Set<RequiredInstruments.Sf2PresetKey>
    ): Sf2File {
        // Find all sample IDs needed by the required presets
        val requiredSampleIds = findRequiredSampleIds(metadata, requiredPresets)

        if (requiredSampleIds.isEmpty()) {
            return Sf2File(
                name = metadata.name,
                sampleData = ShortArray(0),
                presetMap = emptyMap(),
                sampleHeaders = emptyList()
            )
        }

        // Calculate total samples needed and create remapping.
        // Chaque échantillon est entouré de GUARD_SAMPLES zéros : l'interpolation cubique lit
        // index-1 et index+2, elle ne doit pas déborder sur l'échantillon voisin (le fichier SF2
        // d'origine prévoit 46 zéros après chaque échantillon, la copie compacte les supprimait).
        val sampleRemapping = mutableMapOf<Int, SampleRemapInfo>()
        var compactOffset = GUARD_SAMPLES.toLong()

        for (sampleId in requiredSampleIds.sorted()) {
            val header = metadata.sampleHeaders.getOrNull(sampleId) ?: continue
            val sampleLengthLong = header.end - header.start
            // Safety check: individual sample cannot exceed Int.MAX_VALUE (2GB+)
            if (sampleLengthLong <= 0 || sampleLengthLong > Int.MAX_VALUE) continue
            val sampleLength = sampleLengthLong.toInt()

            // Store remapping: original position -> compact position
            sampleRemapping[sampleId] = SampleRemapInfo(
                compactStart = compactOffset,
                loopStartOffset = header.startLoop - header.start,
                loopEndOffset = header.endLoop - header.start
            )
            compactOffset += sampleLength + GUARD_SAMPLES
        }

        // Safety check: total samples cannot exceed Int.MAX_VALUE (array limit)
        if (compactOffset > Int.MAX_VALUE) {
            throw Sf2ParseException("Sample data too large: $compactOffset samples exceeds array limit")
        }
        val totalSamples = compactOffset.toInt()

        // Load samples into compact array
        val compactSampleData = ShortArray(totalSamples)
        RandomAccessFile(File(metadata.filePath), "r").use { raf ->
            val smplSampleCount = metadata.smplByteSize / 2
            for ((sampleId, remap) in sampleRemapping) {
                val header = metadata.sampleHeaders[sampleId]
                // Ne jamais lire au-delà du chunk smpl (en-têtes corrompus)
                val available = (smplSampleCount - header.start).coerceAtLeast(0)
                val sampleCount = minOf(header.end - header.start, available).toInt()
                if (sampleCount <= 0) continue

                raf.seek(metadata.smplByteOffset + (header.start * 2))
                val bytes = ByteArray(sampleCount * 2)
                raf.readFully(bytes)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .get(compactSampleData, remap.compactStart.toInt(), sampleCount)
            }
        }

        // Create remapped sample headers
        val remappedHeaders = metadata.sampleHeaders.mapIndexed { index, header ->
            val remap = sampleRemapping[index]
            if (remap != null) {
                // Remap to compact positions
                Sf2SampleHeader(
                    name = header.name,
                    start = remap.compactStart,
                    end = remap.compactStart + (header.end - header.start),
                    startLoop = remap.compactStart + remap.loopStartOffset,
                    endLoop = remap.compactStart + remap.loopEndOffset,
                    sampleRate = header.sampleRate,
                    originalPitch = header.originalPitch,
                    pitchCorrection = header.pitchCorrection,
                    sampleLink = header.sampleLink,
                    sampleType = header.sampleType
                )
            } else {
                // Keep original (won't be used but needed for indexing)
                header
            }
        }

        // Build presets using remapped headers
        val presetMap = buildPresetsCompact(metadata, requiredPresets, compactSampleData, remappedHeaders)

        return Sf2File(
            name = metadata.name,
            sampleData = compactSampleData,
            presetMap = presetMap,
            sampleHeaders = remappedHeaders
        )
    }

    /**
     * Info for remapping a sample from original to compact position
     */
    private data class SampleRemapInfo(
        val compactStart: Long,
        val loopStartOffset: Long,
        val loopEndOffset: Long
    )

    /**
     * Load SF2 using full memory-mapping (zero RAM for sample data).
     *
     * This mode is ideal for very large SF2 files (500MB-1GB+):
     * - No sample data is loaded into heap memory
     * - Samples are read on-demand via memory-mapped file
     * - OS handles caching and paging automatically
     * - Can play SF2 files larger than available RAM
     *
     * Trade-off: Slightly higher latency for initial note attacks
     * as samples are read from disk on first access.
     */
    fun loadWithMemoryMapping(metadata: Sf2Metadata): Sf2File {
        // Create memory-mapped provider for sample access
        val sampleProvider = MemoryMappedSampleProvider(
            filePath = metadata.filePath,
            smplByteOffset = metadata.smplByteOffset,
            smplByteSize = metadata.smplByteSize
        )

        // Build ALL presets using ORIGINAL sample positions (no remapping needed)
        val presetMap = buildAllPresetsForMmap(metadata)

        return Sf2File(
            name = metadata.name,
            sampleProvider = sampleProvider,
            presetMap = presetMap,
            sampleHeaders = metadata.sampleHeaders
        )
    }

    /**
     * Build all presets for memory-mapped mode.
     * Uses original sample positions (no remapping).
     */
    private fun buildAllPresetsForMmap(metadata: Sf2Metadata): Map<String, Sf2Preset> {
        return buildPresets(metadata, metadata.sampleHeaders, presetFilter = null) { zone ->
            Sf2Region.fromZoneDataMmap(zone, metadata.sampleHeaders)
        }
    }

    // ==================== Sample ID Finding ====================

    private fun findRequiredSampleIds(
        metadata: Sf2Metadata,
        requiredPresets: Set<RequiredInstruments.Sf2PresetKey>
    ): Set<Int> {
        val sampleIds = mutableSetOf<Int>()

        // Build instrument zones first
        val instrumentZones = buildInstrumentZones(metadata)

        // For each required preset, find its sample IDs
        for (presetKey in requiredPresets) {
            // Find the preset header
            val presetIndex = metadata.presetHeaders.indexOfFirst {
                it.bank == presetKey.bank && it.preset == presetKey.program
            }
            if (presetIndex < 0 || presetIndex >= metadata.presetHeaders.size - 1) continue

            val preset = metadata.presetHeaders[presetIndex]
            val nextPreset = metadata.presetHeaders.getOrNull(presetIndex + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

            // Process preset zones
            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

                // Find instrument reference in generators
                for (genIndex in genStart until genEnd) {
                    val gen = metadata.presetGenerators.getOrNull(genIndex) ?: continue
                    if (gen.operator == Sf2Generator.INSTRUMENT.id) {
                        val instrumentIndex = gen.amount and 0xFFFF  // Unsigned word
                        // Get sample IDs from this instrument
                        val zones = instrumentZones.getOrNull(instrumentIndex) ?: continue
                        for (zone in zones) {
                            zone.sampleId?.let { sampleIds.add(it) }

                            // Also add linked samples (for stereo)
                            zone.sampleId?.let { sampleId ->
                                val header = metadata.sampleHeaders.getOrNull(sampleId)
                                if (header != null && header.sampleLink > 0 && header.sampleLink < metadata.sampleHeaders.size) {
                                    sampleIds.add(header.sampleLink)
                                }
                            }
                        }
                    }
                }
            }
        }

        return sampleIds
    }

    // ==================== Preset Building ====================

    private fun buildInstrumentZones(metadata: Sf2Metadata): List<List<Sf2ZoneBuilder.Generators>> {
        val count = max(0, metadata.instrumentHeaders.size - 1)
        return List(count) { i ->
            val zoneStart = metadata.instrumentHeaders[i].bagIndex
            val zoneEnd = metadata.instrumentHeaders.getOrNull(i + 1)?.bagIndex ?: metadata.instrumentBags.size
            Sf2ZoneBuilder.buildZones(
                zoneStart, zoneEnd, { metadata.instrumentBags.getOrNull(it)?.generatorIndex },
                metadata.instrumentGenerators, Sf2Generator.SAMPLE_ID.id
            )
        }
    }

    /**
     * Builds presets (optionally only those accepted by [presetFilter]) with the given
     * sample headers, creating each region through [regionFactory].
     */
    private fun buildPresets(
        metadata: Sf2Metadata,
        sampleHeaders: List<Sf2SampleHeader>,
        presetFilter: ((Sf2PresetHeader) -> Boolean)?,
        regionFactory: (Sf2ZoneData) -> Sf2Region?
    ): Map<String, Sf2Preset> {
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val instrumentZones = buildInstrumentZones(metadata)

        val count = max(0, metadata.presetHeaders.size - 1)

        for (i in 0 until count) {
            val preset = metadata.presetHeaders[i]
            if (presetFilter != null && !presetFilter(preset)) continue

            val zoneStart = preset.bagIndex
            val zoneEnd = metadata.presetHeaders.getOrNull(i + 1)?.bagIndex ?: metadata.presetBags.size
            val presetZones = Sf2ZoneBuilder.buildZones(
                zoneStart, zoneEnd, { metadata.presetBags.getOrNull(it)?.generatorIndex },
                metadata.presetGenerators, Sf2Generator.INSTRUMENT.id
            )

            val regions = mutableListOf<Sf2Region>()
            for (presetZone in presetZones) {
                val instrumentIndex = presetZone.instrumentIndex ?: continue
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: continue
                Sf2ZoneBuilder.stripInstrumentOnly(presetZone)
                for (instZone in instZones) {
                    val combined = Sf2ZoneBuilder.combine(instZone, presetZone, sampleHeaders) ?: continue
                    regionFactory(combined)?.let { regions.add(it) }
                }
            }

            presetMap[preset.getKey()] = Sf2Preset(
                name = preset.name,
                bank = preset.bank,
                program = preset.preset,
                regions = regions
            )
        }

        return presetMap
    }

    /**
     * Build presets using compact sample data and remapped headers
     */
    private fun buildPresetsCompact(
        metadata: Sf2Metadata,
        requiredPresets: Set<RequiredInstruments.Sf2PresetKey>,
        sampleData: ShortArray,
        remappedHeaders: List<Sf2SampleHeader>
    ): Map<String, Sf2Preset> {
        return buildPresets(
            metadata,
            remappedHeaders,
            presetFilter = { requiredPresets.contains(RequiredInstruments.Sf2PresetKey(it.bank, it.preset)) }
        ) { zone ->
            Sf2Region.fromZoneData(zone, sampleData, remappedHeaders)
        }
    }

    // ==================== Parsing Helpers ====================

    private fun readFourCC(buffer: ByteBuffer): String {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readFourCCFromArray(bytes: ByteArray, offset: Int): String {
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    @Suppress("SameParameterValue")
    private fun readString(buffer: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        val end = bytes.indexOf(0)
        val actualLength = if (end >= 0) end else length
        return String(bytes, 0, actualLength, Charsets.US_ASCII).trim()
    }

    private fun readStringFromArray(bytes: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { bytes[it] == 0.toByte() } ?: (offset + length)
        val actualLength = end - offset
        return String(bytes, offset, actualLength, Charsets.US_ASCII).trim()
    }

    private fun parseInfoForNameFromArray(data: ByteArray): String {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var position = 0

        while (position + 8 <= data.size) {
            val id = readFourCCFromArray(data, position)
            val chunkSize = buffer.getInt(position + 4)
            val chunkDataStart = position + 8

            if (id == INAM) {
                return readStringFromArray(data, chunkDataStart, chunkSize)
            }

            position = chunkDataStart + chunkSize + (chunkSize % 2)
        }
        return ""
    }

    private fun parseSdtaMetadataStreaming(raf: RandomAccessFile, start: Long, size: Int): Pair<Long, Long> {
        val chunkHeader = ByteArray(8)
        var position = start
        val endPosition = start + size

        while (position + 8 <= endPosition) {
            raf.seek(position)
            raf.readFully(chunkHeader)

            val id = readFourCCFromArray(chunkHeader, 0)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkDataStart = position + 8

            if (id == SMPL) {
                // Return byte offset and size (don't load data)
                return Pair(chunkDataStart, chunkSize.toLong())
            }

            position = chunkDataStart + chunkSize + (chunkSize % 2)
        }
        return Pair(0L, 0L)
    }

    private fun parsePdtaFromBuffer(
        buffer: ByteBuffer,
        size: Int,
        sampleHeaders: MutableList<Sf2SampleHeader>,
        phdr: MutableList<Sf2PresetHeader>,
        pbag: MutableList<Sf2PresetBag>,
        pgen: MutableList<Sf2GeneratorEntry>,
        inst: MutableList<Sf2InstrumentHeader>,
        ibag: MutableList<Sf2InstrumentBag>,
        igen: MutableList<Sf2GeneratorEntry>
    ) {
        buffer.rewind()
        var position = 0

        while (position + 8 <= size) {
            buffer.position(position)
            val id = readFourCC(buffer)
            val chunkSize = buffer.int
            val chunkStart = buffer.position()

            when (id) {
                PHDR -> parsePhdr(buffer, chunkStart, chunkSize, phdr)
                PBAG -> parseBag(buffer, chunkStart, chunkSize, pbag)
                PGEN -> parseGen(buffer, chunkStart, chunkSize, pgen)
                INST -> parseInst(buffer, chunkStart, chunkSize, inst)
                IBAG -> parseIBag(buffer, chunkStart, chunkSize, ibag)
                IGEN -> parseGen(buffer, chunkStart, chunkSize, igen)
                SHDR -> parseShdr(buffer, chunkStart, chunkSize, sampleHeaders)
            }

            position = chunkStart + chunkSize + (chunkSize % 2)
        }
    }

    private fun parsePhdr(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2PresetHeader>) {
        buffer.position(start)
        val count = size / 38

        repeat(count) {
            val name = readString(buffer, 20)
            val preset = buffer.short.toInt() and 0xFFFF
            val bank = buffer.short.toInt() and 0xFFFF
            val bagIndex = buffer.short.toInt() and 0xFFFF
            buffer.int; buffer.int; buffer.int  // library, genre, morphology (unused)
            list.add(Sf2PresetHeader(name, preset, bank, bagIndex))
        }
    }

    private fun parseBag(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2PresetBag>) {
        buffer.position(start)
        val count = size / 4

        repeat(count) {
            val genIndex = buffer.short.toInt() and 0xFFFF
            val modIndex = buffer.short.toInt() and 0xFFFF
            list.add(Sf2PresetBag(genIndex, modIndex))
        }
    }

    private fun parseIBag(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2InstrumentBag>) {
        buffer.position(start)
        val count = size / 4

        repeat(count) {
            val genIndex = buffer.short.toInt() and 0xFFFF
            val modIndex = buffer.short.toInt() and 0xFFFF
            list.add(Sf2InstrumentBag(genIndex, modIndex))
        }
    }

    private fun parseGen(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2GeneratorEntry>) {
        buffer.position(start)
        val count = size / 4

        repeat(count) {
            val operator = buffer.short.toInt() and 0xFFFF
            val amount = buffer.short.toInt()
            list.add(Sf2GeneratorEntry(operator, amount))
        }
    }

    private fun parseInst(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2InstrumentHeader>) {
        buffer.position(start)
        val count = size / 22

        repeat(count) {
            val name = readString(buffer, 20)
            val bagIndex = buffer.short.toInt() and 0xFFFF
            list.add(Sf2InstrumentHeader(name, bagIndex))
        }
    }

    private fun parseShdr(buffer: ByteBuffer, start: Int, size: Int, list: MutableList<Sf2SampleHeader>) {
        buffer.position(start)
        val count = size / 46

        repeat(count) {
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

            list.add(Sf2SampleHeader(
                name, sampleStart, sampleEnd, startLoop, endLoop,
                sampleRate, originalPitch, pitchCorrection, sampleLink, sampleType
            ))
        }
    }
}
