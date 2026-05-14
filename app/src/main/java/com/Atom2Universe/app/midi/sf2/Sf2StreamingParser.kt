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

        // Calculate total samples needed and create remapping
        val sampleRemapping = mutableMapOf<Int, SampleRemapInfo>()
        var compactOffset = 0L

        for (sampleId in requiredSampleIds.sorted()) {
            val header = metadata.sampleHeaders.getOrNull(sampleId) ?: continue
            val sampleLengthLong = header.end - header.start
            // Safety check: individual sample cannot exceed Int.MAX_VALUE (2GB+)
            if (sampleLengthLong <= 0 || sampleLengthLong > Int.MAX_VALUE) continue
            val sampleLength = sampleLengthLong.toInt()

            // Store remapping: original position -> compact position
            sampleRemapping[sampleId] = SampleRemapInfo(
                originalStart = header.start,
                originalEnd = header.end,
                compactStart = compactOffset,
                loopStartOffset = header.startLoop - header.start,
                loopEndOffset = header.endLoop - header.start
            )
            compactOffset += sampleLength
        }

        // Safety check: total samples cannot exceed Int.MAX_VALUE (array limit)
        if (compactOffset > Int.MAX_VALUE) {
            throw Sf2ParseException("Sample data too large: $compactOffset samples exceeds array limit")
        }
        val totalSamples = compactOffset.toInt()

        // Load samples into compact array
        val compactSampleData = ShortArray(totalSamples)
        RandomAccessFile(File(metadata.filePath), "r").use { raf ->
            for ((sampleId, remap) in sampleRemapping) {
                val header = metadata.sampleHeaders[sampleId]
                val byteOffset = metadata.smplByteOffset + (header.start * 2)
                val sampleCount = (header.end - header.start).toInt()

                raf.seek(byteOffset)
                val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                raf.channel.read(buffer)
                buffer.rewind()

                val destOffset = remap.compactStart.toInt()
                for (i in 0 until sampleCount) {
                    compactSampleData[destOffset + i] = buffer.short
                }
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
        val originalStart: Long,
        val originalEnd: Long,
        val compactStart: Long,
        val loopStartOffset: Long,
        val loopEndOffset: Long
    )

    /**
     * Load all samples (fallback for small SF2 files or when all instruments are needed)
     */
    @Suppress("unused")
    fun loadAllSamples(metadata: Sf2Metadata): Sf2File {
        val sampleData = RandomAccessFile(File(metadata.filePath), "r").use { raf ->
            raf.seek(metadata.smplByteOffset)
            val numSamples = (metadata.smplByteSize / 2).toInt()
            val buffer = ByteBuffer.allocate(metadata.smplByteSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            raf.channel.read(buffer)
            buffer.rewind()

            ShortArray(numSamples) { buffer.short }
        }

        val presetMap = buildAllPresets(metadata, sampleData)

        return Sf2File(
            name = metadata.name,
            sampleData = sampleData,
            presetMap = presetMap,
            sampleHeaders = metadata.sampleHeaders
        )
    }

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
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val (instrumentZones, instrumentGlobals) = buildInstrumentZonesMetadata(metadata)

        val count = max(0, metadata.presetHeaders.size - 1)

        for (i in 0 until count) {
            val preset = metadata.presetHeaders[i]
            val nextPreset = metadata.presetHeaders.getOrNull(i + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

            var presetGlobal: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val regions = mutableListOf<Sf2Region>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

                val zoneData = collectGeneratorValues(metadata.presetGenerators, genStart, genEnd)

                if (zoneData.instrumentIndex == null) {
                    presetGlobal = mergeZoneData(presetGlobal, zoneData)
                    continue
                }

                val instrumentIndex = zoneData.instrumentIndex
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()

                val presetApplied = mergeZoneData(presetGlobal, zoneData)

                for (instZone in instZones) {
                    // instZone already has instrument global merged in (from buildInstrumentZonesMetadata),
                    // so we merge directly with presetApplied to avoid double-applying
                    // additive generators (attenuation, tuning, offsets)
                    val combined = mergeZoneData(presetApplied, instZone)
                    // Use original sample headers (no remapping for mmap)
                    val region = Sf2Region.fromZoneDataMmap(combined, metadata.sampleHeaders)
                    if (region != null) {
                        regions.add(region)
                    }
                }
            }

            val key = preset.getKey()
            presetMap[key] = Sf2Preset(
                name = preset.name,
                bank = preset.bank,
                program = preset.preset,
                regions = regions
            )
        }

        return presetMap
    }

    // ==================== Sample ID Finding ====================

    private fun findRequiredSampleIds(
        metadata: Sf2Metadata,
        requiredPresets: Set<RequiredInstruments.Sf2PresetKey>
    ): Set<Int> {
        val sampleIds = mutableSetOf<Int>()

        // Build instrument zones first
        val (instrumentZones, _) = buildInstrumentZonesMetadata(metadata)

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

    // ==================== Sample Range Calculation ====================

    data class SampleRange(
        val sampleId: Int,
        val startSample: Long,
        val endSample: Long
    ) {
        val sampleCount: Int get() = (endSample - startSample).toInt()
    }

    @Suppress("unused")
    private fun calculateSampleRanges(
        metadata: Sf2Metadata,
        requiredSampleIds: Set<Int>
    ): List<SampleRange> {
        return requiredSampleIds.mapNotNull { sampleId ->
            val header = metadata.sampleHeaders.getOrNull(sampleId) ?: return@mapNotNull null
            SampleRange(
                sampleId = sampleId,
                startSample = header.start,
                endSample = header.end
            )
        }.sortedBy { it.startSample }
    }

    // ==================== Sample Loading ====================

    @Suppress("unused")
    private fun loadSampleRanges(
        metadata: Sf2Metadata,
        ranges: List<SampleRange>
    ): SparseSampleData {
        val loadedRanges = mutableListOf<SparseSampleData.LoadedRange>()
        var totalSamplesLoaded = 0L

        RandomAccessFile(File(metadata.filePath), "r").use { raf ->
            for (range in ranges) {
                val byteOffset = metadata.smplByteOffset + (range.startSample * 2)
                val byteCount = range.sampleCount * 2

                raf.seek(byteOffset)
                val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
                raf.channel.read(buffer)
                buffer.rewind()

                val samples = ShortArray(range.sampleCount) { buffer.short }
                loadedRanges.add(SparseSampleData.LoadedRange(
                    startSample = range.startSample,
                    endSample = range.endSample,
                    data = samples
                ))

                totalSamplesLoaded += range.sampleCount
            }
        }

        return SparseSampleData(loadedRanges)
    }

    // ==================== Preset Building ====================

    private fun buildInstrumentZonesMetadata(
        metadata: Sf2Metadata
    ): Pair<List<List<Sf2ZoneData>>, List<Sf2ZoneData?>> {
        val instrumentZones = mutableListOf<List<Sf2ZoneData>>()
        val instrumentGlobals = mutableListOf<Sf2ZoneData?>()

        val count = max(0, metadata.instrumentHeaders.size - 1)

        for (i in 0 until count) {
            val instrument = metadata.instrumentHeaders[i]
            val nextInstrument = metadata.instrumentHeaders.getOrNull(i + 1)
            val zoneStart = instrument.bagIndex
            val zoneEnd = nextInstrument?.bagIndex ?: metadata.instrumentBags.size

            var globalZone: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val zones = mutableListOf<Sf2ZoneData>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.instrumentBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.instrumentBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.instrumentGenerators.size

                val zoneData = collectGeneratorValues(metadata.instrumentGenerators, genStart, genEnd)

                if (zoneData.sampleId == null) {
                    globalZone = mergeZoneData(globalZone, zoneData)
                } else {
                    val merged = mergeZoneData(globalZone, zoneData)
                    val withHeader = merged.copy(
                        sampleHeader = metadata.sampleHeaders.getOrNull(merged.sampleId ?: 0)
                    )
                    zones.add(withHeader)
                }
            }

            instrumentGlobals.add(globalZone)
            instrumentZones.add(zones)
        }

        return Pair(instrumentZones, instrumentGlobals)
    }

    @Suppress("unused")
    private fun buildPresets(
        metadata: Sf2Metadata,
        requiredPresets: Set<RequiredInstruments.Sf2PresetKey>,
        sampleData: SparseSampleData
    ): Map<String, Sf2Preset> {
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val (instrumentZones, instrumentGlobals) = buildInstrumentZonesMetadata(metadata)

        val count = max(0, metadata.presetHeaders.size - 1)

        for (i in 0 until count) {
            val preset = metadata.presetHeaders[i]
            val presetKey = RequiredInstruments.Sf2PresetKey(preset.bank, preset.preset)

            // Only build required presets
            if (!requiredPresets.contains(presetKey)) continue

            val nextPreset = metadata.presetHeaders.getOrNull(i + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

            var presetGlobal: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val regions = mutableListOf<Sf2Region>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

                val zoneData = collectGeneratorValues(metadata.presetGenerators, genStart, genEnd)

                if (zoneData.instrumentIndex == null) {
                    presetGlobal = mergeZoneData(presetGlobal, zoneData)
                    continue
                }

                val instrumentIndex = zoneData.instrumentIndex
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()

                val presetApplied = mergeZoneData(presetGlobal, zoneData)

                for (instZone in instZones) {
                    // instZone already has instrument global merged in,
                    // merge directly with presetApplied to avoid double-applying additive generators
                    val combined = mergeZoneData(presetApplied, instZone)
                    val region = Sf2Region.fromZoneDataSparse(combined, sampleData, metadata.sampleHeaders)
                    if (region != null) {
                        regions.add(region)
                    }
                }
            }

            val key = preset.getKey()
            presetMap[key] = Sf2Preset(
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
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val (instrumentZones, instrumentGlobals) = buildInstrumentZonesWithHeaders(metadata, remappedHeaders)

        val count = max(0, metadata.presetHeaders.size - 1)

        for (i in 0 until count) {
            val preset = metadata.presetHeaders[i]
            val presetKey = RequiredInstruments.Sf2PresetKey(preset.bank, preset.preset)

            // Only build required presets
            if (!requiredPresets.contains(presetKey)) continue

            val nextPreset = metadata.presetHeaders.getOrNull(i + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

            var presetGlobal: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val regions = mutableListOf<Sf2Region>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

                val zoneData = collectGeneratorValues(metadata.presetGenerators, genStart, genEnd)

                if (zoneData.instrumentIndex == null) {
                    presetGlobal = mergeZoneData(presetGlobal, zoneData)
                    continue
                }

                val instrumentIndex = zoneData.instrumentIndex
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()

                val presetApplied = mergeZoneData(presetGlobal, zoneData)

                for (instZone in instZones) {
                    // instZone already has instrument global merged in,
                    // merge directly with presetApplied to avoid double-applying additive generators
                    val combined = mergeZoneData(presetApplied, instZone)
                    // Use remapped headers for region creation
                    val region = Sf2Region.fromZoneData(combined, sampleData, remappedHeaders)
                    if (region != null) {
                        regions.add(region)
                    }
                }
            }

            val key = preset.getKey()
            presetMap[key] = Sf2Preset(
                name = preset.name,
                bank = preset.bank,
                program = preset.preset,
                regions = regions
            )
        }

        return presetMap
    }

    /**
     * Build instrument zones using remapped sample headers
     */
    private fun buildInstrumentZonesWithHeaders(
        metadata: Sf2Metadata,
        remappedHeaders: List<Sf2SampleHeader>
    ): Pair<List<List<Sf2ZoneData>>, List<Sf2ZoneData?>> {
        val instrumentZones = mutableListOf<List<Sf2ZoneData>>()
        val instrumentGlobals = mutableListOf<Sf2ZoneData?>()

        val count = max(0, metadata.instrumentHeaders.size - 1)

        for (i in 0 until count) {
            val instrument = metadata.instrumentHeaders[i]
            val nextInstrument = metadata.instrumentHeaders.getOrNull(i + 1)
            val zoneStart = instrument.bagIndex
            val zoneEnd = nextInstrument?.bagIndex ?: metadata.instrumentBags.size

            var globalZone: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val zones = mutableListOf<Sf2ZoneData>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.instrumentBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.instrumentBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.instrumentGenerators.size

                val zoneData = collectGeneratorValues(metadata.instrumentGenerators, genStart, genEnd)

                if (zoneData.sampleId == null) {
                    globalZone = mergeZoneData(globalZone, zoneData)
                } else {
                    val merged = mergeZoneData(globalZone, zoneData)
                    // Use remapped headers
                    val withHeader = merged.copy(
                        sampleHeader = remappedHeaders.getOrNull(merged.sampleId ?: 0)
                    )
                    zones.add(withHeader)
                }
            }

            instrumentGlobals.add(globalZone)
            instrumentZones.add(zones)
        }

        return Pair(instrumentZones, instrumentGlobals)
    }

    private fun buildAllPresets(
        metadata: Sf2Metadata,
        sampleData: ShortArray
    ): Map<String, Sf2Preset> {
        val presetMap = mutableMapOf<String, Sf2Preset>()
        val (instrumentZones, instrumentGlobals) = buildInstrumentZonesMetadata(metadata)

        val count = max(0, metadata.presetHeaders.size - 1)

        for (i in 0 until count) {
            val preset = metadata.presetHeaders[i]
            val nextPreset = metadata.presetHeaders.getOrNull(i + 1)
            val zoneStart = preset.bagIndex
            val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

            var presetGlobal: Sf2ZoneData = Sf2ZoneData.createDefaults()
            val regions = mutableListOf<Sf2Region>()

            for (zoneIndex in zoneStart until zoneEnd) {
                val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
                val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
                val genStart = bag.generatorIndex
                val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

                val zoneData = collectGeneratorValues(metadata.presetGenerators, genStart, genEnd)

                if (zoneData.instrumentIndex == null) {
                    presetGlobal = mergeZoneData(presetGlobal, zoneData)
                    continue
                }

                val instrumentIndex = zoneData.instrumentIndex
                val instZones = instrumentZones.getOrNull(instrumentIndex) ?: emptyList()

                val presetApplied = mergeZoneData(presetGlobal, zoneData)

                for (instZone in instZones) {
                    // instZone already has instrument global merged in,
                    // merge directly with presetApplied to avoid double-applying additive generators
                    val combined = mergeZoneData(presetApplied, instZone)
                    val region = Sf2Region.fromZoneData(combined, sampleData, metadata.sampleHeaders)
                    if (region != null) {
                        regions.add(region)
                    }
                }
            }

            val key = preset.getKey()
            presetMap[key] = Sf2Preset(
                name = preset.name,
                bank = preset.bank,
                program = preset.preset,
                regions = regions
            )
        }

        return presetMap
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

    @Suppress("unused")
    private fun parsePdta(
        buffer: ByteBuffer,
        start: Int,
        size: Int,
        sampleHeaders: MutableList<Sf2SampleHeader>,
        phdr: MutableList<Sf2PresetHeader>,
        pbag: MutableList<Sf2PresetBag>,
        pgen: MutableList<Sf2GeneratorEntry>,
        inst: MutableList<Sf2InstrumentHeader>,
        ibag: MutableList<Sf2InstrumentBag>,
        igen: MutableList<Sf2GeneratorEntry>
    ) {
        val end = min(buffer.capacity(), start + size)
        buffer.position(start)

        while (buffer.position() + 8 <= end) {
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

            val nextPos = chunkStart + chunkSize + (chunkSize % 2)
            buffer.position(max(buffer.position(), nextPos))
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

    // ==================== Generator Collection (from Sf2Parser) ====================

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
        var filterFc: Int? = null
        var filterQ: Int? = null
        var vibLfoDelay: Int? = null
        var vibLfoFreq: Int? = null
        var vibLfoToPitch: Int? = null
        var modLfoDelay: Int? = null
        var modLfoFreq: Int? = null
        var modLfoToPitch: Int? = null
        var modLfoToFilterFc: Int? = null
        var modLfoToVolume: Int? = null
        var envDelay: Int? = null
        var envAttack: Int? = null
        var envHold: Int? = null
        var envDecay: Int? = null
        var envKeynumToHold: Int? = null
        var envKeynumToDecay: Int? = null
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

            when (entry.operator) {
                Sf2Generator.START_ADDRS_OFFSET.id -> startOffset += amount
                Sf2Generator.END_ADDRS_OFFSET.id -> endOffset += amount
                Sf2Generator.STARTLOOP_ADDRS_OFFSET.id -> startLoopOffset += amount
                Sf2Generator.ENDLOOP_ADDRS_OFFSET.id -> endLoopOffset += amount
                Sf2Generator.START_ADDRS_COARSE_OFFSET.id -> startOffset += amount * 32768
                Sf2Generator.END_ADDRS_COARSE_OFFSET.id -> endOffset += amount * 32768
                Sf2Generator.STARTLOOP_ADDRS_COARSE_OFFSET.id -> startLoopOffset += amount * 32768
                Sf2Generator.ENDLOOP_ADDRS_COARSE_OFFSET.id -> endLoopOffset += amount * 32768
                Sf2Generator.INITIAL_FILTER_FC.id -> filterFc = amount
                Sf2Generator.INITIAL_FILTER_Q.id -> filterQ = amount
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
                Sf2Generator.PAN.id -> pan = amount / 500f
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
                Sf2Generator.DELAY_VOL_ENV.id -> envDelay = amount
                Sf2Generator.ATTACK_VOL_ENV.id -> envAttack = amount
                Sf2Generator.HOLD_VOL_ENV.id -> envHold = amount
                Sf2Generator.DECAY_VOL_ENV.id -> envDecay = amount
                Sf2Generator.SUSTAIN_VOL_ENV.id -> envSustain = amount
                Sf2Generator.RELEASE_VOL_ENV.id -> envRelease = amount
                Sf2Generator.KEYNUM_TO_VOL_ENV_HOLD.id -> envKeynumToHold = amount
                Sf2Generator.KEYNUM_TO_VOL_ENV_DECAY.id -> envKeynumToDecay = amount
                Sf2Generator.INSTRUMENT.id -> {
                    instrumentIndex = amount and 0xFFFF
                }
                Sf2Generator.KEY_RANGE.id -> keyRange = entry.decodeRange()
                Sf2Generator.VEL_RANGE.id -> velRange = entry.decodeRange()
                Sf2Generator.INITIAL_ATTENUATION.id -> attenuation += amount
                Sf2Generator.COARSE_TUNE.id -> coarseTune += amount
                Sf2Generator.FINE_TUNE.id -> fineTune += amount
                Sf2Generator.SAMPLE_ID.id -> {
                    sampleId = amount and 0xFFFF
                }
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
            vibLfoDelay = source.vibLfoDelay ?: base.vibLfoDelay,
            vibLfoFreq = source.vibLfoFreq ?: base.vibLfoFreq,
            vibLfoToPitch = source.vibLfoToPitch ?: base.vibLfoToPitch,
            modLfoDelay = source.modLfoDelay ?: base.modLfoDelay,
            modLfoFreq = source.modLfoFreq ?: base.modLfoFreq,
            modLfoToPitch = source.modLfoToPitch ?: base.modLfoToPitch,
            modLfoToFilterFc = source.modLfoToFilterFc ?: base.modLfoToFilterFc,
            modLfoToVolume = source.modLfoToVolume ?: base.modLfoToVolume,
            modEnvelope = mergeEnvelope(base.modEnvelope, source.modEnvelope),
            modEnvToPitch = source.modEnvToPitch ?: base.modEnvToPitch,
            modEnvToFilterFc = source.modEnvToFilterFc ?: base.modEnvToFilterFc,
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
