package com.Atom2Universe.app.sf2creator.reader

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * SF2 SoundFont file reader with lazy loading support.
 * Parses the structure without loading sample audio into memory,
 * allowing handling of large (1GB+) SoundFont files.
 *
 * Usage:
 * 1. Call parse() to read the SF2 structure
 * 2. Use the returned Sf2ParseResult to browse presets/samples
 * 3. Call extractSampleAudio() to load specific samples on demand
 */
class Sf2Reader {

    companion object {
        private const val TAG = "Sf2Reader"

        // Chunk IDs
        private const val RIFF = "RIFF"
        private const val SFBK = "sfbk"
        private const val LIST = "LIST"
        private const val INFO = "INFO"
        private const val SDTA = "sdta"
        private const val PDTA = "pdta"
        private const val SMPL = "smpl"

        // pdta sub-chunks
        private const val PHDR = "phdr"
        private const val PBAG = "pbag"
        private const val PMOD = "pmod"
        private const val PGEN = "pgen"
        private const val INST = "inst"
        private const val IBAG = "ibag"
        private const val IMOD = "imod"
        private const val IGEN = "igen"
        private const val SHDR = "shdr"

        // Generator types - using Sf2Constants values
        private const val GEN_KEY_RANGE = 43
        private const val GEN_VEL_RANGE = 44
        private const val GEN_INSTRUMENT = 41
        private const val GEN_SAMPLE_ID = 53
        private const val GEN_SAMPLE_MODES = 54
        private const val GEN_OVERRIDING_ROOT_KEY = 58
        private const val GEN_INITIAL_FILTER_FC = 8
        private const val GEN_INITIAL_FILTER_Q = 9
        private const val GEN_CHORUS_EFFECTS_SEND = 15
        private const val GEN_REVERB_EFFECTS_SEND = 16
        private const val GEN_PAN = 17
        private const val GEN_ATTACK_VOL_ENV = 34
        private const val GEN_DECAY_VOL_ENV = 36
        private const val GEN_SUSTAIN_VOL_ENV = 37
        private const val GEN_RELEASE_VOL_ENV = 38
        private const val GEN_INITIAL_ATTENUATION = 48
        private const val GEN_FINE_TUNE = 52

        // Advanced generators
        private const val GEN_MOD_LFO_TO_PITCH = 5
        private const val GEN_VIB_LFO_TO_PITCH = 6
        private const val GEN_MOD_ENV_TO_PITCH = 7
        private const val GEN_MOD_LFO_TO_FILTER_FC = 10
        private const val GEN_MOD_ENV_TO_FILTER_FC = 11
        private const val GEN_MOD_LFO_TO_VOLUME = 13
        private const val GEN_DELAY_MOD_LFO = 21
        private const val GEN_FREQ_MOD_LFO = 22
        private const val GEN_DELAY_VIB_LFO = 23
        private const val GEN_FREQ_VIB_LFO = 24
        private const val GEN_DELAY_MOD_ENV = 25
        private const val GEN_ATTACK_MOD_ENV = 26
        private const val GEN_HOLD_MOD_ENV = 27
        private const val GEN_DECAY_MOD_ENV = 28
        private const val GEN_SUSTAIN_MOD_ENV = 29
        private const val GEN_RELEASE_MOD_ENV = 30
        private const val GEN_DELAY_VOL_ENV = 33
        private const val GEN_HOLD_VOL_ENV = 35
        private const val GEN_COARSE_TUNE = 51
        private const val GEN_SCALE_TUNING = 56
        private const val GEN_EXCLUSIVE_CLASS = 57

        // Key-to-envelope generators
        private const val GEN_KEYNUM_TO_MOD_ENV_HOLD = 31
        private const val GEN_KEYNUM_TO_MOD_ENV_DECAY = 32
        private const val GEN_KEYNUM_TO_VOL_ENV_HOLD = 39
        private const val GEN_KEYNUM_TO_VOL_ENV_DECAY = 40

        // Fixed key/velocity generators
        private const val GEN_FIXED_KEY = 46
        private const val GEN_FIXED_VELOCITY = 47
    }

    // File reference for lazy loading
    private var sf2File: File? = null
    private var smplChunkStart: Long = 0
    private var smplChunkSize: Long = 0

    /**
     * Get the path to the currently parsed SF2 file.
     * Used for patch-based import to store source references.
     */
    fun getSourceFilePath(): String? = sf2File?.absolutePath

    /**
     * Get the byte offset where the smpl chunk data starts in the SF2 file.
     * Used for patch-based import to calculate sample offsets.
     */
    fun getSmplChunkStart(): Long = smplChunkStart

    /**
     * Calculate the byte offset for a sample within the SF2 file.
     * @param sample The parsed sample header
     * @return Byte offset from the start of the file where this sample's audio begins
     */
    fun getSampleByteOffset(sample: Sf2ParsedSample): Long {
        return smplChunkStart + (sample.start * 2L) // 2 bytes per 16-bit sample
    }

    /**
     * Calculate the size in bytes of a sample's audio data.
     * @param sample The parsed sample header
     * @return Size in bytes
     */
    fun getSampleByteSize(sample: Sf2ParsedSample): Long {
        return (sample.end - sample.start) * 2L // 2 bytes per 16-bit sample
    }

    /**
     * Parse an SF2 file and return its structure.
     * Does NOT load sample audio data - use extractSampleAudio() for that.
     *
     * @param file The SF2 file to parse
     * @return Parsed SF2 data or null if parsing fails
     */
    fun parse(file: File): Sf2ParseResult? {
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            return null
        }

        sf2File = file

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read RIFF header
                val riffId = readChunkId(raf)
                if (riffId != RIFF) {
                    Log.e(TAG, "Not a RIFF file: $riffId")
                    return null
                }

                readUInt32(raf)
                val format = readChunkId(raf)
                if (format != SFBK) {
                    Log.e(TAG, "Not an SF2 file: $format")
                    return null
                }

                var info: Sf2Info? = null
                val presetHeaders = mutableListOf<RawPresetHeader>()
                val presetBags = mutableListOf<RawBag>()
                val presetMods = mutableListOf<RawModulator>()
                val presetGens = mutableListOf<RawGenerator>()
                val instHeaders = mutableListOf<RawInstHeader>()
                val instBags = mutableListOf<RawBag>()
                val instMods = mutableListOf<RawModulator>()
                val instGens = mutableListOf<RawGenerator>()
                val sampleHeaders = mutableListOf<RawSampleHeader>()

                // Parse chunks
                while (raf.filePointer < raf.length()) {
                    val chunkId = readChunkId(raf)
                    val chunkSize = readUInt32(raf)
                    val chunkStart = raf.filePointer

                    when (chunkId) {
                        LIST -> {
                            val listType = readChunkId(raf)
                            when (listType) {
                                INFO -> {
                                    info = parseInfoChunk(raf, chunkStart + chunkSize)
                                }
                                SDTA -> {
                                    // Find smpl chunk within sdta
                                    while (raf.filePointer < chunkStart + chunkSize) {
                                        val subId = readChunkId(raf)
                                        val subSize = readUInt32(raf)
                                        if (subId == SMPL) {
                                            smplChunkStart = raf.filePointer
                                            smplChunkSize = subSize
                                        }
                                        raf.seek(raf.filePointer + subSize)
                                        // Align to even boundary
                                        if (subSize % 2 != 0L) raf.skipBytes(1)
                                    }
                                }
                                PDTA -> {
                                    // Parse pdta sub-chunks
                                    while (raf.filePointer < chunkStart + chunkSize) {
                                        val subId = readChunkId(raf)
                                        val subSize = readUInt32(raf)
                                        val subStart = raf.filePointer

                                        when (subId) {
                                            PHDR -> parsePresetHeaders(raf, subSize, presetHeaders)
                                            PBAG -> parseBags(raf, subSize, presetBags)
                                            PMOD -> parseModulators(raf, subSize, presetMods)
                                            PGEN -> parseGenerators(raf, subSize, presetGens)
                                            INST -> parseInstHeaders(raf, subSize, instHeaders)
                                            IBAG -> parseBags(raf, subSize, instBags)
                                            IMOD -> parseModulators(raf, subSize, instMods)
                                            IGEN -> parseGenerators(raf, subSize, instGens)
                                            SHDR -> parseSampleHeaders(raf, subSize, sampleHeaders)
                                        }

                                        raf.seek(subStart + subSize)
                                        if (subSize % 2 != 0L) raf.skipBytes(1)
                                    }
                                }
                            }
                        }
                    }

                    // Move to next chunk
                    raf.seek(chunkStart + chunkSize)
                    // Align to even boundary
                    if (chunkSize % 2 != 0L && raf.filePointer < raf.length()) {
                        raf.skipBytes(1)
                    }
                }

                // Build the parsed structure
                return buildParseResult(
                    info ?: Sf2Info("Unknown", "", ""),
                    presetHeaders, presetBags, presetMods, presetGens,
                    instHeaders, instBags, instMods, instGens,
                    sampleHeaders
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SF2 file", e)
            return null
        }
    }

    /**
     * Extract audio data for a specific sample.
     * Call this when you need the actual audio - it reads from the file on demand.
     *
     * @param sample The sample to extract (from parse result)
     * @return Audio data as ShortArray, or null if extraction fails
     */
    fun extractSampleAudio(sample: Sf2ParsedSample): ShortArray? {
        val file = sf2File ?: return null

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Calculate byte positions (use Long arithmetic to avoid overflow with large files)
                val startByte = smplChunkStart + (sample.start * 2L)
                val numSamples = (sample.end - sample.start).toInt()

                if (numSamples <= 0 || numSamples > 100_000_000) {
                    Log.e(TAG, "Invalid sample size: $numSamples")
                    return null
                }

                raf.seek(startByte)

                // Read in chunks to avoid memory issues with large samples
                val result = ShortArray(numSamples)
                val bufferSize = minOf(numSamples * 2, 1024 * 1024) // 1MB max buffer
                val buffer = ByteArray(bufferSize)

                var samplesRead = 0
                while (samplesRead < numSamples) {
                    val samplesToRead = minOf(bufferSize / 2, numSamples - samplesRead)
                    val bytesToRead = samplesToRead * 2
                    val bytesRead = raf.read(buffer, 0, bytesToRead)

                    if (bytesRead <= 0) break

                    // Convert bytes to shorts (little-endian)
                    // Note: Both bytes must be masked with 0xFF to prevent sign extension
                    for (i in 0 until bytesRead / 2) {
                        val low = buffer[i * 2].toInt() and 0xFF
                        val high = buffer[i * 2 + 1].toInt() and 0xFF
                        result[samplesRead + i] = ((high shl 8) or low).toShort()
                    }

                    samplesRead += bytesRead / 2
                }

                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting sample audio", e)
            return null
        }
    }

    // ==================== Chunk Scanning for Hybrid Passthrough ====================

    /**
     * Scan an SF2 file and extract information about all chunks.
     * This is used for the hybrid passthrough export strategy.
     *
     * @param file The SF2 file to scan
     * @return Map of chunk identifier to ChunkScanInfo, or null if scanning fails
     */
    fun scanChunks(file: File): Map<String, ChunkScanInfo>? {
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            return null
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read RIFF header
                val riffId = readChunkId(raf)
                if (riffId != RIFF) {
                    Log.e(TAG, "Not a RIFF file: $riffId")
                    return null
                }

                readUInt32(raf)
                val format = readChunkId(raf)
                if (format != SFBK) {
                    Log.e(TAG, "Not an SF2 file: $format")
                    return null
                }

                val chunks = mutableMapOf<String, ChunkScanInfo>()

                // Parse chunks
                while (raf.filePointer < raf.length()) {
                    val chunkId = readChunkId(raf)
                    val chunkSize = readUInt32(raf)
                    val chunkDataStart = raf.filePointer

                    when (chunkId) {
                        LIST -> {
                            val listType = readChunkId(raf)
                            when (listType) {
                                INFO -> {
                                    // Record INFO list position
                                    chunks["INFO"] = ChunkScanInfo(
                                        chunkId = "INFO",
                                        offset = chunkDataStart - 8, // Include LIST header
                                        size = chunkSize + 8,
                                        contentHash = computeChunkHash(raf, chunkDataStart, chunkSize)
                                    )
                                }
                                SDTA -> {
                                    // Record SDTA list and scan for smpl
                                    chunks["sdta"] = ChunkScanInfo(
                                        chunkId = "sdta",
                                        offset = chunkDataStart - 8,
                                        size = chunkSize + 8,
                                        contentHash = "" // Don't hash entire sdta, too large
                                    )

                                    while (raf.filePointer < chunkDataStart + chunkSize) {
                                        val subId = readChunkId(raf)
                                        val subSize = readUInt32(raf)
                                        val subDataStart = raf.filePointer

                                        if (subId == SMPL) {
                                            chunks["smpl"] = ChunkScanInfo(
                                                chunkId = "smpl",
                                                offset = subDataStart - 8, // Include sub-chunk header
                                                size = subSize + 8,
                                                contentHash = "" // Don't hash smpl, too large
                                            )
                                        }

                                        raf.seek(raf.filePointer + subSize)
                                        if (subSize % 2 != 0L) raf.skipBytes(1)
                                    }
                                }
                                PDTA -> {
                                    // Record PDTA list and each sub-chunk
                                    chunks["pdta"] = ChunkScanInfo(
                                        chunkId = "pdta",
                                        offset = chunkDataStart - 8,
                                        size = chunkSize + 8,
                                        contentHash = "" // Will compute per-subchunk
                                    )

                                    while (raf.filePointer < chunkDataStart + chunkSize) {
                                        val subId = readChunkId(raf)
                                        val subSize = readUInt32(raf)
                                        val subDataStart = raf.filePointer

                                        // Record each pdta sub-chunk
                                        chunks[subId] = ChunkScanInfo(
                                            chunkId = subId,
                                            offset = subDataStart - 8,
                                            size = subSize + 8,
                                            contentHash = computeChunkHash(raf, subDataStart, subSize)
                                        )

                                        raf.seek(subDataStart + subSize)
                                        if (subSize % 2 != 0L) raf.skipBytes(1)
                                    }
                                }
                            }
                        }
                    }

                    // Move to next chunk
                    raf.seek(chunkDataStart + chunkSize)
                    if (chunkSize % 2 != 0L && raf.filePointer < raf.length()) {
                        raf.skipBytes(1)
                    }
                }

                Log.d(TAG, "Scanned ${chunks.size} chunks from ${file.name}")
                return chunks
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SF2 chunks", e)
            return null
        }
    }

    /**
     * Compute SHA-256 hash of a chunk's content.
     */
    private fun computeChunkHash(raf: RandomAccessFile, offset: Long, size: Long): String {
        if (size > 10_000_000) {
            // Skip hashing for very large chunks (>10MB)
            return ""
        }

        val savedPos = raf.filePointer
        try {
            raf.seek(offset)
            val buffer = ByteArray(size.toInt())
            raf.readFully(buffer)

            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(buffer)
            return hashBytes.joinToString("") { "%02x".format(it) }
        } finally {
            raf.seek(savedPos)
        }
    }

    /**
     * Compute SHA-256 hash of the entire file.
     */
    fun computeFileHash(file: File): String? {
        if (!file.exists()) return null

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            file.inputStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing file hash", e)
            return null
        }
    }

    // ==================== Parsing helpers ====================

    private fun readChunkId(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.read(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun readUInt16(raf: RandomAccessFile): Int {
        val bytes = ByteArray(2)
        raf.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun readInt16(raf: RandomAccessFile): Int {
        val bytes = ByteArray(2)
        raf.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    }

    private fun readInt8(raf: RandomAccessFile): Int {
        return raf.read()
    }

    private fun readString(raf: RandomAccessFile, length: Int): String {
        val bytes = ByteArray(length)
        raf.read(bytes)
        // Find null terminator
        val end = bytes.indexOf(0.toByte()).takeIf { it >= 0 } ?: length
        return String(bytes, 0, end, Charsets.US_ASCII)
    }

    private fun parseInfoChunk(raf: RandomAccessFile, endPos: Long): Sf2Info {
        var name = "Unknown"
        var engine = ""
        var comment = ""

        while (raf.filePointer < endPos) {
            val subId = readChunkId(raf)
            val subSize = readUInt32(raf).toInt()
            val subStart = raf.filePointer

            when (subId) {
                "INAM" -> name = readString(raf, subSize).trim()
                "isng" -> engine = readString(raf, subSize).trim()
                "ICMT" -> comment = readString(raf, subSize).trim()
            }

            raf.seek(subStart + subSize)
            if (subSize % 2 != 0) raf.skipBytes(1)
        }

        return Sf2Info(name, engine, comment)
    }

    private fun parsePresetHeaders(raf: RandomAccessFile, size: Long, list: MutableList<RawPresetHeader>) {
        val count = (size / 38).toInt()
        repeat(count) {
            val name = readString(raf, 20)
            val preset = readUInt16(raf)
            val bank = readUInt16(raf)
            val bagIndex = readUInt16(raf)
            raf.skipBytes(12) // library, genre, morphology
            list.add(RawPresetHeader(name, preset, bank, bagIndex))
        }
    }

    private fun parseInstHeaders(raf: RandomAccessFile, size: Long, list: MutableList<RawInstHeader>) {
        val count = (size / 22).toInt()
        repeat(count) {
            val name = readString(raf, 20)
            val bagIndex = readUInt16(raf)
            list.add(RawInstHeader(name, bagIndex))
        }
    }

    private fun parseBags(raf: RandomAccessFile, size: Long, list: MutableList<RawBag>) {
        val count = (size / 4).toInt()
        repeat(count) {
            val genIndex = readUInt16(raf)
            val modIndex = readUInt16(raf)
            list.add(RawBag(genIndex, modIndex))
        }
    }

    private fun parseGenerators(raf: RandomAccessFile, size: Long, list: MutableList<RawGenerator>) {
        val count = (size / 4).toInt()
        repeat(count) {
            val oper = readUInt16(raf)
            val amount = readInt16(raf)
            list.add(RawGenerator(oper, amount))
        }
    }

    private fun parseModulators(raf: RandomAccessFile, size: Long, list: MutableList<RawModulator>) {
        val count = (size / 10).toInt() // Each modulator is 10 bytes
        repeat(count) {
            val srcOper = readUInt16(raf)
            val destOper = readUInt16(raf)
            val amount = readInt16(raf)
            val amtSrcOper = readUInt16(raf)
            val transOper = readUInt16(raf)
            list.add(RawModulator(srcOper, destOper, amount, amtSrcOper, transOper))
        }
    }

    private fun parseSampleHeaders(raf: RandomAccessFile, size: Long, list: MutableList<RawSampleHeader>) {
        val count = (size / 46).toInt()
        repeat(count) {
            val name = readString(raf, 20)
            val start = readUInt32(raf)
            val end = readUInt32(raf)
            val loopStart = readUInt32(raf)
            val loopEnd = readUInt32(raf)
            val sampleRate = readUInt32(raf).toInt()
            val originalPitch = readInt8(raf)
            val pitchCorrection = readInt8(raf)
            val sampleLink = readUInt16(raf)
            val sampleType = readUInt16(raf)

            list.add(RawSampleHeader(
                name, start, end, loopStart, loopEnd,
                sampleRate, originalPitch, pitchCorrection, sampleLink, sampleType
            ))
        }
    }

    // ==================== Build parsed structure ====================

    private fun buildParseResult(
        info: Sf2Info,
        presetHeaders: List<RawPresetHeader>,
        presetBags: List<RawBag>,
        presetMods: List<RawModulator>,
        presetGens: List<RawGenerator>,
        instHeaders: List<RawInstHeader>,
        instBags: List<RawBag>,
        instMods: List<RawModulator>,
        instGens: List<RawGenerator>,
        sampleHeaders: List<RawSampleHeader>
    ): Sf2ParseResult {
        // Build samples list (exclude terminal)
        val samples = sampleHeaders.dropLast(1).mapIndexed { index, header ->
            Sf2ParsedSample(
                index = index,
                name = header.name,
                start = header.start,
                end = header.end,
                loopStart = header.loopStart,
                loopEnd = header.loopEnd,
                sampleRate = header.sampleRate,
                originalPitch = header.originalPitch,
                pitchCorrection = header.pitchCorrection,
                sampleType = header.sampleType
            )
        }

        // Build instruments (exclude terminal) - use Map for efficient lookup by original index
        val instrumentsMap = mutableMapOf<Int, Sf2ParsedInstrument>()
        val instrumentsList = mutableListOf<Sf2ParsedInstrument>()

        for (i in 0 until instHeaders.size - 1) {
            val header = instHeaders[i]
            val nextHeader = instHeaders[i + 1]

            val zones = mutableListOf<Sf2ParsedZone>()
            var globalGenerators = mutableMapOf<Int, Int>()
            var globalModulators = listOf<Sf2ParsedModulator>()
            var isFirstZone = true

            // Get zones for this instrument
            for (bagIdx in header.bagIndex until nextHeader.bagIndex) {
                if (bagIdx >= instBags.size - 1) break

                val bag = instBags[bagIdx]
                val nextBag = instBags[bagIdx + 1]

                // Parse generators for this zone
                val generators = mutableMapOf<Int, Int>()
                for (genIdx in bag.genIndex until nextBag.genIndex) {
                    if (genIdx >= instGens.size) break
                    val gen = instGens[genIdx]
                    generators[gen.oper] = gen.amount
                }

                // Parse modulators for this zone
                val modulators = mutableListOf<Sf2ParsedModulator>()
                for (modIdx in bag.modIndex until nextBag.modIndex) {
                    if (modIdx >= instMods.size) break
                    val mod = instMods[modIdx]
                    // Skip terminal modulator (all zeros)
                    if (mod.srcOper == 0 && mod.destOper == 0 && mod.amount == 0) continue
                    modulators.add(Sf2ParsedModulator(
                        srcOper = mod.srcOper,
                        destOper = mod.destOper,
                        amount = mod.amount,
                        amtSrcOper = mod.amtSrcOper,
                        transOper = mod.transOper
                    ))
                }

                val sampleId = generators[GEN_SAMPLE_ID]

                // Check if this is a global zone (no sampleId or invalid sampleId)
                // SF2 spec: The first zone in an instrument may be a global zone with no sampleId
                // Global zone generators/modulators apply to ALL subsequent zones
                if (sampleId == null || sampleId < 0 || sampleId >= samples.size) {
                    if (isFirstZone) {
                        // This is the global zone - store its generators and modulators as defaults
                        globalGenerators = generators
                        globalModulators = modulators.toList()
                        Log.d(TAG, "Found global zone for instrument ${header.name} with ${generators.size} generators, ${modulators.size} modulators")
                    }
                    isFirstZone = false
                    continue
                }
                isFirstZone = false

                // SF2 spec Section 9.4: Global zone generators provide defaults for zones
                // that do NOT explicitly define them. At import time, we now PRESERVE this
                // separation to enable lossless round-trip import/export.
                //
                // The global generators are stored in instrument.globalGenerators and will
                // be written to the global zone at export time. Zone generators only contain
                // values explicitly set for that zone (not merged with globals).
                //
                // This allows:
                // 1. Smaller SF2 files (shared params in global zone, not repeated)
                // 2. UI editing of global params separate from per-sample params
                // 3. Faithful reproduction of original SF2 structure
                val zoneGenerators = mutableMapOf<Int, Int>()
                generators.forEach { (key, value) ->
                    zoneGenerators[key] = value
                }
                // globalGenerators remain available via instrument.globalGenerators for export

                // Extract zone properties from zone-specific generators
                val keyRange = zoneGenerators[GEN_KEY_RANGE]
                val keyLo = if (keyRange != null) keyRange and 0xFF else 0
                val keyHi = if (keyRange != null) (keyRange shr 8) and 0xFF else 127

                // SF2 spec: Global zone modulators are stored separately in instrument.globalModulators
                // and apply to all zones at runtime. For lossless round-trip, we keep them separate.
                // Zone-specific modulators only (not combined with global) for proper export.
                zones.add(Sf2ParsedZone(
                    keyRangeLow = keyLo,
                    keyRangeHigh = keyHi,
                    sampleIndex = sampleId,
                    generators = zoneGenerators,
                    modulators = modulators
                ))
            }

            // Create instrument even if zones are empty (may have global zone only)
            val instrument = Sf2ParsedInstrument(
                index = i,
                name = header.name,
                zones = zones,
                globalGenerators = globalGenerators,
                globalModulators = globalModulators
            )
            instrumentsMap[i] = instrument
            instrumentsList.add(instrument)
        }

        // Build presets (exclude terminal)
        val presets = mutableListOf<Sf2ParsedPreset>()
        for (i in 0 until presetHeaders.size - 1) {
            val header = presetHeaders[i]
            val nextHeader = presetHeaders[i + 1]

            // Collect preset zones with ALL their generators (not just GEN_INSTRUMENT)
            val presetZones = mutableListOf<Sf2ParsedPresetZone>()
            var globalZoneGenerators: Map<Int, Int>? = null
            var globalZoneModulators: List<Sf2ParsedModulator> = emptyList()
            var isFirstZone = true

            // Parse each preset zone (bag)
            for (bagIdx in header.bagIndex until nextHeader.bagIndex) {
                if (bagIdx >= presetBags.size - 1) break

                val bag = presetBags[bagIdx]
                val nextBag = presetBags[bagIdx + 1]

                // Parse ALL generators for this zone
                val zoneGenerators = mutableMapOf<Int, Int>()
                var instrumentIndex: Int? = null

                for (genIdx in bag.genIndex until nextBag.genIndex) {
                    if (genIdx >= presetGens.size) break
                    val gen = presetGens[genIdx]
                    zoneGenerators[gen.oper] = gen.amount
                    if (gen.oper == GEN_INSTRUMENT) {
                        instrumentIndex = gen.amount
                    }
                }

                // Parse modulators for this zone
                val zoneModulators = mutableListOf<Sf2ParsedModulator>()
                for (modIdx in bag.modIndex until nextBag.modIndex) {
                    if (modIdx >= presetMods.size) break
                    val mod = presetMods[modIdx]
                    // Skip terminal modulator (all zeros)
                    if (mod.srcOper == 0 && mod.destOper == 0 && mod.amount == 0) continue
                    zoneModulators.add(Sf2ParsedModulator(
                        srcOper = mod.srcOper,
                        destOper = mod.destOper,
                        amount = mod.amount,
                        amtSrcOper = mod.amtSrcOper,
                        transOper = mod.transOper
                    ))
                }

                // SF2 spec: First zone without GEN_INSTRUMENT is the global zone
                // Global zone generators/modulators apply to ALL subsequent zones
                if (instrumentIndex == null && isFirstZone) {
                    globalZoneGenerators = zoneGenerators
                    globalZoneModulators = zoneModulators.toList()
                    isFirstZone = false
                    Log.d(TAG, "Found global preset zone for ${header.name} with ${zoneGenerators.size} generators, ${zoneModulators.size} modulators")
                    continue
                }
                isFirstZone = false

                // Get the instrument reference
                val instrument = if (instrumentIndex != null && instrumentIndex in instrumentsMap) {
                    instrumentsMap[instrumentIndex]
                } else {
                    Log.w(TAG, "Preset zone without valid instrument reference in ${header.name}")
                    null
                }

                // SF2 spec Section 9.4: For LOSSLESS round-trip import/export, we now
                // PRESERVE the separation between global zone and per-zone generators.
                //
                // The global zone generators are stored in preset.globalGenerators and will
                // be written to a global preset zone at export time. Per-zone generators
                // only contain values explicitly set for that zone (not merged with globals).
                //
                // This allows:
                // 1. Smaller SF2 files (shared params in global zone, not repeated)
                // 2. Exact round-trip preservation of SF2 structure
                // 3. Proper editing of global vs per-zone parameters
                val zoneOnlyGenerators = mutableMapOf<Int, Int>()
                zoneGenerators.forEach { (key, value) ->
                    zoneOnlyGenerators[key] = value
                }
                // globalZoneGenerators remain available via preset.globalGenerators for export

                // Extract key range from zone generators (or use default)
                val keyRange = zoneOnlyGenerators[GEN_KEY_RANGE]
                val keyLo = if (keyRange != null) keyRange and 0xFF else 0
                val keyHi = if (keyRange != null) (keyRange shr 8) and 0xFF else 127

                // Extract velocity range
                val velRange = zoneOnlyGenerators[GEN_VEL_RANGE]
                val velLo = if (velRange != null) velRange and 0xFF else 0
                val velHi = if (velRange != null) (velRange shr 8) and 0xFF else 127

                // For modulators: keep zone-specific only, global ones stored in preset.globalModulators
                // This preserves the separation for lossless round-trip
                val zoneOnlyModulators = zoneModulators.toList()

                presetZones.add(Sf2ParsedPresetZone(
                    instrument = instrument,
                    instrumentIndex = instrumentIndex ?: -1,
                    keyRangeLow = keyLo,
                    keyRangeHigh = keyHi,
                    velRangeLow = velLo,
                    velRangeHigh = velHi,
                    generators = zoneOnlyGenerators,
                    modulators = zoneOnlyModulators
                ))
            }

            presets.add(Sf2ParsedPreset(
                index = i,
                name = header.name,
                programNumber = header.preset,
                bankNumber = header.bank,
                zones = presetZones,
                globalGenerators = globalZoneGenerators ?: emptyMap(),
                globalModulators = globalZoneModulators
            ))
        }

        return Sf2ParseResult(
            info = info,
            presets = presets,
            instruments = instrumentsList,
            samples = samples,
            filePath = sf2File?.absolutePath ?: ""
        )
    }

    // ==================== Raw data classes ====================

    private data class RawPresetHeader(
        val name: String,
        val preset: Int,
        val bank: Int,
        val bagIndex: Int
    )

    private data class RawInstHeader(
        val name: String,
        val bagIndex: Int
    )

    private data class RawBag(
        val genIndex: Int,
        val modIndex: Int
    )

    private data class RawGenerator(
        val oper: Int,
        val amount: Int
    )

    private data class RawModulator(
        val srcOper: Int,
        val destOper: Int,
        val amount: Int,
        val amtSrcOper: Int,
        val transOper: Int
    )

    private data class RawSampleHeader(
        val name: String,
        val start: Long,
        val end: Long,
        val loopStart: Long,
        val loopEnd: Long,
        val sampleRate: Int,
        val originalPitch: Int,
        val pitchCorrection: Int,
        val sampleLink: Int,
        val sampleType: Int
    )
}

// ==================== Public data classes ====================

/**
 * SF2 file metadata from INFO chunk.
 */
data class Sf2Info(
    val name: String,
    val engine: String,
    val comment: String
)

/**
 * Result of parsing an SF2 file.
 * Structure mirrors Polyphone:
 * - Presets (programs) reference Instruments
 * - Instruments contain Zones that reference Samples
 * - Samples are the raw audio data
 */
data class Sf2ParseResult(
    val info: Sf2Info,
    val presets: List<Sf2ParsedPreset>,
    val instruments: List<Sf2ParsedInstrument>,
    val samples: List<Sf2ParsedSample>,
    val filePath: String
) {
    /**
     * Get total number of unique samples used across all presets.
     */
    fun getUsedSampleCount(): Int {
        return presets
            .flatMap { preset -> preset.zones.mapNotNull { it.instrument } }
            .flatMap { it.zones }
            .map { it.sampleIndex }
            .distinct()
            .count()
    }

    /**
     * Get estimated audio data size in bytes.
     */
    fun getEstimatedAudioSize(): Long {
        return samples.sumOf { (it.end - it.start) * 2 }
    }

    /**
     * Get all instruments with at least one zone.
     */
    fun getInstrumentsWithZones(): List<Sf2ParsedInstrument> {
        return instruments.filter { it.zones.isNotEmpty() }
    }

    /**
     * Get sample by index.
     */
    fun getSample(index: Int): Sf2ParsedSample? {
        return samples.getOrNull(index)
    }
}

/**
 * Parsed SF2 modulator.
 * Represents a modulation routing from a source to a destination.
 */
data class Sf2ParsedModulator(
    val srcOper: Int,      // Source operator (controller + flags)
    val destOper: Int,     // Destination generator number
    val amount: Int,       // Modulation amount (signed)
    val amtSrcOper: Int,   // Secondary source operator
    val transOper: Int     // Transform operator
)

/**
 * Parsed preset zone (layer within a preset).
 * SF2 spec: A preset zone contains a reference to an instrument plus optional
 * generators/modulators that modify the instrument's parameters.
 *
 * The generators in a preset zone are ADDITIVE to the instrument's generators.
 */
data class Sf2ParsedPresetZone(
    val instrument: Sf2ParsedInstrument?,
    val instrumentIndex: Int,
    val keyRangeLow: Int = 0,
    val keyRangeHigh: Int = 127,
    val velRangeLow: Int = 0,
    val velRangeHigh: Int = 127,
    val generators: Map<Int, Int> = emptyMap(),
    val modulators: List<Sf2ParsedModulator> = emptyList()
) {
    // Generator accessors (same pattern as Sf2ParsedZone)
    fun getAttenuation(): Int = generators[48] ?: 0 // GEN_INITIAL_ATTENUATION
    fun getCoarseTune(): Int = generators[51] ?: 0 // GEN_COARSE_TUNE
    fun getFineTune(): Int = generators[52] ?: 0 // GEN_FINE_TUNE
    fun getFilterCutoff(): Int = generators[8] ?: 0 // GEN_INITIAL_FILTER_FC (0 = no modification)
    fun getFilterResonance(): Int = generators[9] ?: 0 // GEN_INITIAL_FILTER_Q
    fun getChorusSend(): Int = generators[15] ?: 0 // GEN_CHORUS_EFFECTS_SEND
    fun getReverbSend(): Int = generators[16] ?: 0 // GEN_REVERB_EFFECTS_SEND
    fun getPan(): Int = generators[17] ?: 0 // GEN_PAN

    // Volume Envelope modifiers
    fun getVolEnvDelay(): Int = generators[33] ?: 0
    fun getVolEnvAttack(): Int = generators[34] ?: 0
    fun getVolEnvHold(): Int = generators[35] ?: 0
    fun getVolEnvDecay(): Int = generators[36] ?: 0
    fun getVolEnvSustain(): Int = generators[37] ?: 0
    fun getVolEnvRelease(): Int = generators[38] ?: 0

    // Modulation Envelope modifiers
    fun getModEnvDelay(): Int = generators[25] ?: 0
    fun getModEnvAttack(): Int = generators[26] ?: 0
    fun getModEnvHold(): Int = generators[27] ?: 0
    fun getModEnvDecay(): Int = generators[28] ?: 0
    fun getModEnvSustain(): Int = generators[29] ?: 0
    fun getModEnvRelease(): Int = generators[30] ?: 0
    fun getModEnvToPitch(): Int = generators[7] ?: 0
    fun getModEnvToFilterFc(): Int = generators[11] ?: 0

    // LFO modifiers
    fun getVibLfoDelay(): Int = generators[23] ?: 0
    fun getVibLfoFreq(): Int = generators[24] ?: 0
    fun getVibLfoToPitch(): Int = generators[6] ?: 0
    fun getModLfoDelay(): Int = generators[21] ?: 0
    fun getModLfoFreq(): Int = generators[22] ?: 0
    fun getModLfoToPitch(): Int = generators[5] ?: 0
    fun getModLfoToFilterFc(): Int = generators[10] ?: 0
    fun getModLfoToVolume(): Int = generators[13] ?: 0

    // Key-based envelope scaling
    fun getKeyToModEnvHold(): Int = generators[31] ?: 0 // GEN_KEYNUM_TO_MOD_ENV_HOLD
    fun getKeyToModEnvDecay(): Int = generators[32] ?: 0 // GEN_KEYNUM_TO_MOD_ENV_DECAY
    fun getKeyToVolEnvHold(): Int = generators[39] ?: 0 // GEN_KEYNUM_TO_VOL_ENV_HOLD
    fun getKeyToVolEnvDecay(): Int = generators[40] ?: 0 // GEN_KEYNUM_TO_VOL_ENV_DECAY

    // Additional generators
    fun getScaleTuning(): Int = generators[56] ?: 0 // GEN_SCALE_TUNING
    fun getExclusiveClass(): Int = generators[57] ?: 0 // GEN_EXCLUSIVE_CLASS

    /**
     * Check if this zone has any non-default generators (excluding keyRange/velRange/instrument).
     */
    fun hasNonDefaultGenerators(): Boolean {
        return generators.any { (key, value) ->
            key != 43 && key != 44 && key != 41 && value != 0
        }
    }
}

/**
 * Parsed preset with its zones.
 * A preset can contain multiple zones, each referencing an instrument.
 *
 * SF2 hierarchy: Preset -> Preset Zones (with PGEN/PMOD) -> Instruments -> Instrument Zones -> Samples
 */
data class Sf2ParsedPreset(
    val index: Int,
    val name: String,
    val programNumber: Int,
    val bankNumber: Int,
    val zones: List<Sf2ParsedPresetZone> = emptyList(),
    val globalGenerators: Map<Int, Int> = emptyMap(),
    val globalModulators: List<Sf2ParsedModulator> = emptyList()
) {
    /**
     * Get all instruments from all zones (for backward compatibility).
     */
    val instruments: List<Sf2ParsedInstrument>
        get() = zones.mapNotNull { it.instrument }.distinctBy { it.index }

    /**
     * Get all modulators from all zones (for backward compatibility).
     */
    val modulators: List<Sf2ParsedModulator>
        get() = zones.flatMap { it.modulators }

    /**
     * Get the first/primary instrument (for backward compatibility).
     */
    val instrument: Sf2ParsedInstrument?
        get() = zones.firstOrNull()?.instrument

    /**
     * Get all samples used by this preset (from all instruments in all zones).
     */
    fun getSampleIndices(): List<Int> {
        return zones.mapNotNull { it.instrument }
            .flatMap { instr -> instr.zones.map { it.sampleIndex } }
    }

    /**
     * Get sample count for this preset (from all instruments in all zones).
     */
    fun getSampleCount(): Int = zones.mapNotNull { it.instrument }.sumOf { it.zones.size }

    /**
     * Get zone count for this preset.
     */
    fun getZoneCount(): Int = zones.size

    /**
     * Get instrument count for this preset (unique instruments).
     */
    fun getInstrumentCount(): Int = instruments.size
}

/**
 * Parsed instrument with its zones.
 * @param globalGenerators Generators from the global zone (first zone without sampleId).
 *                         These are default values that should be added to each zone's generators.
 * @param globalModulators Modulators from the global zone (IMOD global zone).
 *                         These modulators apply to all sample zones within the instrument.
 */
data class Sf2ParsedInstrument(
    val index: Int,
    val name: String,
    val zones: List<Sf2ParsedZone>,
    val globalGenerators: Map<Int, Int> = emptyMap(),
    val globalModulators: List<Sf2ParsedModulator> = emptyList()
)

/**
 * Parsed zone (sample mapping within an instrument).
 * Contains all SF2 generators including advanced parameters.
 */
data class Sf2ParsedZone(
    val keyRangeLow: Int,
    val keyRangeHigh: Int,
    val sampleIndex: Int,
    val generators: Map<Int, Int>,
    val modulators: List<Sf2ParsedModulator> = emptyList()
) {
    // ==================== Volume/Attenuation ====================

    fun getAttenuation(): Int = generators[48] ?: 0 // GEN_INITIAL_ATTENUATION

    // ==================== Tuning ====================

    fun getRootKey(): Int? = generators[58] // GEN_OVERRIDING_ROOT_KEY
    fun getCoarseTune(): Int = generators[51] ?: 0 // GEN_COARSE_TUNE
    fun getFineTune(): Int = generators[52] ?: 0 // GEN_FINE_TUNE
    fun getScaleTuning(): Int = generators[56] ?: 100 // GEN_SCALE_TUNING

    // ==================== Filter ====================

    fun getFilterCutoff(): Int = generators[8] ?: 13500 // GEN_INITIAL_FILTER_FC
    fun getFilterResonance(): Int = generators[9] ?: 0 // GEN_INITIAL_FILTER_Q

    // ==================== Effects ====================

    fun getChorusSend(): Int = generators[15] ?: 0 // GEN_CHORUS_EFFECTS_SEND
    fun getReverbSend(): Int = generators[16] ?: 0 // GEN_REVERB_EFFECTS_SEND
    fun getPan(): Int = generators[17] ?: 0 // GEN_PAN

    // ==================== Volume Envelope (DAHDSR) ====================

    fun getVolEnvDelay(): Int = generators[33] ?: -12000 // GEN_DELAY_VOL_ENV
    fun getAttack(): Int = generators[34] ?: -12000 // GEN_ATTACK_VOL_ENV
    fun getVolEnvHold(): Int = generators[35] ?: -12000 // GEN_HOLD_VOL_ENV
    fun getDecay(): Int = generators[36] ?: -12000 // GEN_DECAY_VOL_ENV
    fun getSustain(): Int = generators[37] ?: 0 // GEN_SUSTAIN_VOL_ENV
    fun getRelease(): Int = generators[38] ?: -12000 // GEN_RELEASE_VOL_ENV

    // ==================== Modulation Envelope (DAHDSR) ====================

    fun getModEnvDelay(): Int = generators[25] ?: -12000 // GEN_DELAY_MOD_ENV
    fun getModEnvAttack(): Int = generators[26] ?: -12000 // GEN_ATTACK_MOD_ENV
    fun getModEnvHold(): Int = generators[27] ?: -12000 // GEN_HOLD_MOD_ENV
    fun getModEnvDecay(): Int = generators[28] ?: -12000 // GEN_DECAY_MOD_ENV
    fun getModEnvSustain(): Int = generators[29] ?: 0 // GEN_SUSTAIN_MOD_ENV
    fun getModEnvRelease(): Int = generators[30] ?: -12000 // GEN_RELEASE_MOD_ENV
    fun getModEnvToPitch(): Int = generators[7] ?: 0 // GEN_MOD_ENV_TO_PITCH
    fun getModEnvToFilterFc(): Int = generators[11] ?: 0 // GEN_MOD_ENV_TO_FILTER_FC

    // ==================== Vibrato LFO ====================

    fun getVibLfoDelay(): Int = generators[23] ?: -12000 // GEN_DELAY_VIB_LFO
    fun getVibLfoFreq(): Int = generators[24] ?: 0 // GEN_FREQ_VIB_LFO
    fun getVibLfoToPitch(): Int = generators[6] ?: 0 // GEN_VIB_LFO_TO_PITCH

    // ==================== Modulation LFO ====================

    fun getModLfoDelay(): Int = generators[21] ?: -12000 // GEN_DELAY_MOD_LFO
    fun getModLfoFreq(): Int = generators[22] ?: 0 // GEN_FREQ_MOD_LFO
    fun getModLfoToPitch(): Int = generators[5] ?: 0 // GEN_MOD_LFO_TO_PITCH
    fun getModLfoToFilterFc(): Int = generators[10] ?: 0 // GEN_MOD_LFO_TO_FILTER_FC
    fun getModLfoToVolume(): Int = generators[13] ?: 0 // GEN_MOD_LFO_TO_VOLUME

    // ==================== Velocity Range ====================

    fun getVelRangeLow(): Int {
        val velRange = generators[44] ?: return 0
        return velRange and 0xFF
    }

    fun getVelRangeHigh(): Int {
        val velRange = generators[44] ?: return 127
        return (velRange shr 8) and 0xFF
    }

    // ==================== Exclusive Class ====================

    fun getExclusiveClass(): Int = generators[57] ?: 0 // GEN_EXCLUSIVE_CLASS

    // ==================== Sample Modes ====================

    fun getSampleModes(): Int = generators[54] ?: 0 // GEN_SAMPLE_MODES
    fun hasLoop(): Boolean = (getSampleModes() and 1) != 0

    // ==================== Key-to-Envelope Scaling ====================

    fun getKeyToModEnvHold(): Int = generators[31] ?: 0 // GEN_KEYNUM_TO_MOD_ENV_HOLD
    fun getKeyToModEnvDecay(): Int = generators[32] ?: 0 // GEN_KEYNUM_TO_MOD_ENV_DECAY
    fun getKeyToVolEnvHold(): Int = generators[39] ?: 0 // GEN_KEYNUM_TO_VOL_ENV_HOLD
    fun getKeyToVolEnvDecay(): Int = generators[40] ?: 0 // GEN_KEYNUM_TO_VOL_ENV_DECAY

    // ==================== Fixed Key/Velocity ====================

    fun getFixedKey(): Int? = generators[46] // GEN_FIXED_KEY (null = not fixed)
    fun getFixedVelocity(): Int? = generators[47] // GEN_FIXED_VELOCITY (null = not fixed)
}

/**
 * Parsed sample header (metadata only, no audio data).
 */
data class Sf2ParsedSample(
    val index: Int,
    val name: String,
    val start: Long,
    val end: Long,
    val loopStart: Long,
    val loopEnd: Long,
    val sampleRate: Int,
    val originalPitch: Int,
    val pitchCorrection: Int,
    val sampleType: Int
) {
    /**
     * Get sample duration in seconds.
     */
    fun getDurationSeconds(): Float {
        val numSamples = end - start
        return if (sampleRate > 0) numSamples.toFloat() / sampleRate else 0f
    }

    /**
     * Get sample size in bytes.
     */
    fun getSizeBytes(): Long = (end - start) * 2

    /**
     * Check if this is a mono sample.
     */
    fun isMono(): Boolean = sampleType == 1

    /**
     * Check if sample has loop points.
     */
    fun hasLoop(): Boolean = loopEnd > loopStart
}

/**
 * Information about a scanned SF2 chunk.
 * Used for hybrid passthrough export strategy.
 */
data class ChunkScanInfo(
    /**
     * Chunk identifier (e.g., "smpl", "shdr", "phdr")
     */
    val chunkId: String,

    /**
     * Byte offset from the start of the SF2 file.
     * This is the offset of the chunk header, not the data.
     */
    val offset: Long,

    /**
     * Total size in bytes including the 8-byte header.
     */
    val size: Long,

    /**
     * SHA-256 hash of the chunk content (excluding header).
     * Empty string for very large chunks (smpl).
     */
    val contentHash: String
)
