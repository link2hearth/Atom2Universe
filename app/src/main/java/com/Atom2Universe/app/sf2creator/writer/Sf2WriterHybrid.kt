package com.Atom2Universe.app.sf2creator.writer

import android.util.Log
import com.Atom2Universe.app.sf2creator.data.db.entities.ChunkInfo
import com.Atom2Universe.app.sf2creator.data.db.entities.SampleMappingInfo
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hybrid SF2 writer that can passthrough unchanged chunks from an original SF2 file.
 *
 * This writer is used when:
 * 1. A project was imported from an SF2 file
 * 2. Not all data has been modified
 *
 * Benefits:
 * - Faster export (unchanged chunks are copied directly)
 * - Lossless preservation of unchanged data
 * - Preserves any unsupported/unknown SF2 features in unchanged chunks
 *
 * Chunk dependencies (if one is modified, dependents must be rebuilt):
 * smpl ← shdr ← igen ← ibag ← inst ← pgen ← pbag ← phdr
 *
 * This means:
 * - If smpl is modified, ALL pdta chunks must be rebuilt
 * - If only pgen is modified, phdr/pbag/pgen must be rebuilt, but inst/ibag/igen/imod/shdr/smpl can pass through
 */
class Sf2WriterHybrid {

    companion object {
        private const val TAG = "Sf2WriterHybrid"

        // Chunk dependency tree (chunk -> chunks that depend on it)
        // If a chunk is modified, all its dependents must also be rebuilt
        private val CHUNK_DEPENDENCIES = mapOf(
            "smpl" to listOf("shdr"),
            "shdr" to listOf("igen"),
            "igen" to listOf("ibag"),
            "imod" to listOf("ibag"),
            "ibag" to listOf("inst"),
            "inst" to listOf("pgen"),
            "pgen" to listOf("pbag"),
            "pmod" to listOf("pbag"),
            "pbag" to listOf("phdr"),
            "phdr" to emptyList<String>()
        )

        // Chunks in write order for pdta
        private val PDTA_CHUNK_ORDER = listOf(
            "phdr", "pbag", "pmod", "pgen",
            "inst", "ibag", "imod", "igen",
            "shdr"
        )
    }

    /**
     * Analyze which chunks can use passthrough based on modifications.
     *
     * @param chunkRegistry JSON string containing chunk information
     * @return Set of chunk IDs that can be passed through
     */
    fun analyzePassthroughChunks(chunkRegistry: String): Set<String> {
        try {
            val registry = parseChunkRegistry(chunkRegistry)
            val modifiedChunks = registry.filter { it.value.isModified }.keys.toMutableSet()

            // Propagate modifications through dependency tree
            var changed = true
            while (changed) {
                changed = false
                for ((chunk, dependents) in CHUNK_DEPENDENCIES) {
                    if (chunk in modifiedChunks) {
                        for (dependent in dependents) {
                            if (dependent !in modifiedChunks) {
                                modifiedChunks.add(dependent)
                                changed = true
                            }
                        }
                    }
                }
            }

            // Reverse: mark chunks that depend on modified chunks
            val mustRebuild = mutableSetOf<String>()
            for ((chunk, dependents) in CHUNK_DEPENDENCIES) {
                if (dependents.any { it in modifiedChunks }) {
                    mustRebuild.add(chunk)
                }
            }
            mustRebuild.addAll(modifiedChunks)

            // Return chunks that can pass through (not in mustRebuild)
            return registry.keys - mustRebuild
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing passthrough chunks", e)
            return emptySet()
        }
    }

    /**
     * Parse chunk registry JSON into a map of ChunkInfo.
     */
    fun parseChunkRegistry(chunkRegistryJson: String): Map<String, ChunkInfo> {
        val result = mutableMapOf<String, ChunkInfo>()
        try {
            val json = JSONObject(chunkRegistryJson)
            for (key in json.keys()) {
                val chunkObj = json.getJSONObject(key)
                result[key] = ChunkInfo(
                    chunkId = chunkObj.getString("chunkId"),
                    offset = chunkObj.getLong("offset"),
                    size = chunkObj.getLong("size"),
                    contentHash = chunkObj.optString("contentHash", ""),
                    isModified = chunkObj.optBoolean("isModified", false)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chunk registry", e)
        }
        return result
    }

    /**
     * Copy a chunk from source file to destination file.
     *
     * @param sourceFile The original SF2 file
     * @param destRaf The destination RandomAccessFile (positioned at write location)
     * @param chunkInfo Information about the chunk to copy
     * @return Number of bytes written
     */
    fun copyChunk(sourceFile: File, destRaf: RandomAccessFile, chunkInfo: ChunkInfo): Long {
        try {
            RandomAccessFile(sourceFile, "r").use { sourceRaf ->
                sourceRaf.seek(chunkInfo.offset)

                // Copy in chunks to handle large data efficiently
                val bufferSize = minOf(chunkInfo.size.toInt(), 1024 * 1024) // 1MB max buffer
                val buffer = ByteArray(bufferSize)
                var remaining = chunkInfo.size
                var totalCopied = 0L

                while (remaining > 0) {
                    val toRead = minOf(remaining.toInt(), buffer.size)
                    val bytesRead = sourceRaf.read(buffer, 0, toRead)
                    if (bytesRead <= 0) break

                    destRaf.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                    totalCopied += bytesRead
                }

                Log.d(TAG, "Copied chunk ${chunkInfo.chunkId}: $totalCopied bytes")
                return totalCopied
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying chunk ${chunkInfo.chunkId}", e)
            return 0
        }
    }

    /**
     * Check if hybrid export is possible for a project.
     *
     * @param sourceFilePath Path to the original SF2 source file
     * @param chunkRegistry JSON string containing chunk information
     * @return true if hybrid export can provide benefits
     */
    fun canUseHybridExport(sourceFilePath: String?, chunkRegistry: String): Boolean {
        if (sourceFilePath == null) return false

        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            Log.w(TAG, "Source file not found: $sourceFilePath")
            return false
        }

        // Check if any chunks can pass through
        val passthroughChunks = analyzePassthroughChunks(chunkRegistry)

        // Hybrid export is beneficial if smpl can pass through (biggest chunk)
        val beneficial = "smpl" in passthroughChunks

        Log.d(TAG, "Hybrid export check: ${passthroughChunks.size} passthrough chunks, smpl=${beneficial}")
        return beneficial
    }

    /**
     * Export using hybrid passthrough strategy.
     *
     * This method coordinates between:
     * 1. Chunks that can be copied directly from source (passthrough)
     * 2. Chunks that must be regenerated (modified data)
     *
     * The key insight for smpl passthrough:
     * - Copy the entire smpl chunk from the source file
     * - Regenerate pdta (preset/instrument definitions) with sample indices
     *   pointing to the same offsets in the copied smpl chunk
     * - This requires the sampleIndexMapping to know where each sample lives
     *
     * @param outputFile Output SF2 file
     * @param sourceFilePath Path to original SF2 source file
     * @param chunkRegistry JSON string containing chunk information
     * @param sampleMapping JSON string containing sample index mapping
     * @param presets Preset data to export (for regenerating pdta)
     * @param soundFontName Name of the soundfont
     * @param fallbackWriter Standard Sf2Writer to use for rebuilding chunks
     * @return true if successful
     */
    fun exportWithPassthrough(
        outputFile: File,
        sourceFilePath: String,
        chunkRegistry: String,
        sampleMapping: String,
        presets: List<Sf2Writer.PresetExportData>,
        soundFontName: String,
        fallbackWriter: Sf2Writer
    ): Boolean {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file not found, falling back to standard export")
            return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
        }

        val registry = parseChunkRegistry(chunkRegistry)
        val passthroughChunks = analyzePassthroughChunks(chunkRegistry)

        // Only support smpl passthrough - the largest benefit
        if ("smpl" !in passthroughChunks) {
            Log.d(TAG, "smpl chunk modified, using standard export")
            return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
        }

        // Parse sample mapping
        val sampleMappings = parseSampleMapping(sampleMapping)
        if (sampleMappings.isEmpty()) {
            Log.w(TAG, "No sample mapping available, using standard export")
            return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
        }

        // CRITICAL: Verify ALL samples have a valid mapping
        // If any sample is missing from the mapping (e.g., imported from a different SF2),
        // we MUST use standard export to avoid corruption
        for (preset in presets) {
            for (instrument in preset.instruments) {
                for (sample in instrument.samples) {
                    val sampleName = sample.name.take(20)
                    if (sampleMappings[sampleName] == null) {
                        Log.w(TAG, "Sample '$sampleName' not found in mapping - using standard export (multiple SF2 sources?)")
                        return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
                    }
                }
            }
        }

        // Get smpl chunk info
        val smplInfo = registry["smpl"]
        if (smplInfo == null) {
            Log.e(TAG, "No smpl chunk info in registry, using standard export")
            return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
        }

        Log.d(TAG, "Using hybrid export with smpl passthrough (${smplInfo.size} bytes)")

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0) // Clear file

                // === 1. Write RIFF header (placeholder size) ===
                writeChunkId(raf, "RIFF")
                val riffSizePos = raf.filePointer
                writeUInt32(raf, 0) // Placeholder for total size
                writeChunkId(raf, "sfbk")

                // === 2. Write LIST 'INFO' (always regenerated) ===
                raf.filePointer
                writeChunkId(raf, "LIST")
                val infoSizePos = raf.filePointer
                writeUInt32(raf, 0) // Placeholder
                writeChunkId(raf, "INFO")
                val infoContentStart = raf.filePointer

                // Write INFO sub-chunks
                writeInfoChunks(raf, soundFontName)

                val infoSize = raf.filePointer - infoContentStart + 4 // +4 for 'INFO'
                val savedPos = raf.filePointer
                raf.seek(infoSizePos)
                writeUInt32(raf, infoSize)
                raf.seek(savedPos)

                // === 3. Write LIST 'sdta' with smpl from source ===
                val sdtaStart = raf.filePointer
                writeChunkId(raf, "LIST")
                val sdtaSizePos = raf.filePointer
                writeUInt32(raf, 0) // Placeholder
                writeChunkId(raf, "sdta")

                // Copy smpl chunk directly from source
                // The smplInfo.offset includes the chunk header, but we need just the data
                // smplInfo structure: offset points to 'smpl' + size(4) + data
                // Size includes header (8 bytes)
                val smplDataOffset = smplInfo.offset + 8 // Skip 'smpl' + size
                val smplDataSize = smplInfo.size - 8      // Actual data size

                writeChunkId(raf, "smpl")
                writeUInt32(raf, smplDataSize)
                copyBytes(sourceFile, raf, smplDataOffset, smplDataSize)

                // Pad to even boundary if needed
                if (smplDataSize % 2 != 0L) {
                    raf.writeByte(0)
                }

                // RIFF LIST size = type_id (4) + content, excluding LIST and size field (8)
                val sdtaSize = raf.filePointer - sdtaStart - 8
                val savedSdtaPos = raf.filePointer
                raf.seek(sdtaSizePos)
                writeUInt32(raf, sdtaSize)
                raf.seek(savedSdtaPos)

                // === 4. Write LIST 'pdta' (regenerated with original sample indices) ===
                val pdtaStart = raf.filePointer
                writeChunkId(raf, "LIST")
                val pdtaSizePos = raf.filePointer
                writeUInt32(raf, 0) // Placeholder
                writeChunkId(raf, "pdta")

                // Build pdta using the same logic as Sf2Writer but with original sample offsets
                writePdtaWithPassthrough(raf, presets, sampleMappings)

                // RIFF LIST size = type_id (4) + content, excluding LIST and size field (8)
                val pdtaSize = raf.filePointer - pdtaStart - 8
                val savedPdtaPos = raf.filePointer
                raf.seek(pdtaSizePos)
                writeUInt32(raf, pdtaSize)
                raf.seek(savedPdtaPos)

                // === 5. Update RIFF size ===
                val totalSize = raf.filePointer - 8 // Exclude RIFF header
                raf.seek(riffSizePos)
                writeUInt32(raf, totalSize)

                Log.d(TAG, "Hybrid export completed: ${raf.length()} bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hybrid export failed, falling back to standard", e)
            return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
        }
    }

    /**
     * Overload for backward compatibility (without sampleMapping parameter).
     */
    fun exportWithPassthrough(
        outputFile: File,
        sourceFilePath: String,
        chunkRegistry: String,
        presets: List<Sf2Writer.PresetExportData>,
        soundFontName: String,
        fallbackWriter: Sf2Writer
    ): Boolean {
        // Without sample mapping, fall back to standard export
        Log.d(TAG, "exportWithPassthrough called without sampleMapping, using standard export")
        return fallbackWriter.writeSf2MultiPreset(outputFile, soundFontName, presets)
    }

    /**
     * Parse sample mapping JSON into a map of sample name to mapping info.
     */
    fun parseSampleMapping(mappingJson: String): Map<String, SampleMappingInfo> {
        val result = mutableMapOf<String, SampleMappingInfo>()
        try {
            val json = JSONObject(mappingJson)
            for (key in json.keys()) {
                // Skip index keys (starting with _idx_)
                if (key.startsWith("_idx_")) continue

                val info = json.getJSONObject(key)
                result[key] = SampleMappingInfo(
                    name = info.getString("name"),
                    originalIndex = info.getInt("originalIndex"),
                    startOffset = info.getLong("startOffset"),
                    endOffset = info.getLong("endOffset"),
                    loopStart = info.getLong("loopStart"),
                    loopEnd = info.getLong("loopEnd"),
                    sampleRate = info.getInt("sampleRate"),
                    originalPitch = info.getInt("originalPitch"),
                    pitchCorrection = info.optInt("pitchCorrection", 0),
                    sampleLink = info.optInt("sampleLink", 0),
                    sampleType = info.optInt("sampleType", 1)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sample mapping", e)
        }
        return result
    }

    // ==================== Low-level write helpers ====================

    private fun writeChunkId(raf: RandomAccessFile, id: String) {
        raf.write(id.toByteArray(Charsets.US_ASCII))
    }

    private fun writeUInt32(raf: RandomAccessFile, value: Long) {
        val buffer = ByteArray(4)
        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt())
        raf.write(buffer)
    }

    private fun writeUInt16(raf: RandomAccessFile, value: Int) {
        val buffer = ByteArray(2)
        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        raf.write(buffer)
    }

    private fun writeInt16(raf: RandomAccessFile, value: Int) {
        writeUInt16(raf, value)
    }

    private fun writeString(raf: RandomAccessFile, str: String, length: Int) {
        val bytes = str.toByteArray(Charsets.US_ASCII)
        raf.write(bytes, 0, minOf(bytes.size, length))
        // Pad with zeros
        for (i in bytes.size until length) {
            raf.writeByte(0)
        }
    }

    /**
     * Copy bytes from source file to destination RAF.
     */
    private fun copyBytes(source: File, dest: RandomAccessFile, offset: Long, size: Long) {
        RandomAccessFile(source, "r").use { sourceRaf ->
            sourceRaf.seek(offset)

            val bufferSize = minOf(size.toInt(), 1024 * 1024) // 1MB max buffer
            val buffer = ByteArray(bufferSize)
            var remaining = size

            while (remaining > 0) {
                val toRead = minOf(remaining.toInt(), buffer.size)
                val bytesRead = sourceRaf.read(buffer, 0, toRead)
                if (bytesRead <= 0) break
                dest.write(buffer, 0, bytesRead)
                remaining -= bytesRead
            }
        }
    }

    /**
     * Write INFO sub-chunks.
     */
    private fun writeInfoChunks(raf: RandomAccessFile, soundFontName: String) {
        // IFIL - version
        writeChunkId(raf, "ifil")
        writeUInt32(raf, 4)
        writeUInt16(raf, 2)
        writeUInt16(raf, 1) // SF2.01

        // isng - sound engine
        val engineName = "EMU8000"
        writeChunkId(raf, "isng")
        val engineLen = ((engineName.length + 1 + 1) / 2) * 2 // Pad to even
        writeUInt32(raf, engineLen.toLong())
        writeString(raf, engineName, engineLen)

        // INAM - name
        writeChunkId(raf, "INAM")
        val nameLen = ((soundFontName.length + 1 + 1) / 2) * 2 // Pad to even
        writeUInt32(raf, nameLen.toLong())
        writeString(raf, soundFontName, nameLen)

        // ICMT - comment
        val comment = "Created with A2U (hybrid passthrough)"
        writeChunkId(raf, "ICMT")
        val commentLen = ((comment.length + 1 + 1) / 2) * 2
        writeUInt32(raf, commentLen.toLong())
        writeString(raf, comment, commentLen)

        // ISFT - software
        val software = "A2U SF2 Creator"
        writeChunkId(raf, "ISFT")
        val softwareLen = ((software.length + 1 + 1) / 2) * 2
        writeUInt32(raf, softwareLen.toLong())
        writeString(raf, software, softwareLen)
    }

    /**
     * Write pdta chunks using original sample offsets from passthrough.
     */
    private fun writePdtaWithPassthrough(
        raf: RandomAccessFile,
        presets: List<Sf2Writer.PresetExportData>,
        sampleMappings: Map<String, SampleMappingInfo>
    ) {
        // Build a unique list of samples across all presets with their original mapping
        // Key: sample name (truncated to 20 chars), Value: (original mapping, index in shdr)
        val uniqueSamples = mutableMapOf<String, Pair<SampleMappingInfo, Int>>()
        var sampleIndex = 0

        for (preset in presets) {
            for (instrument in preset.instruments) {
                for (sample in instrument.samples) {
                    val sampleName = sample.name.take(20)
                    if (sampleName !in uniqueSamples) {
                        val mapping = sampleMappings[sampleName]
                        if (mapping != null) {
                            uniqueSamples[sampleName] = Pair(mapping, sampleIndex++)
                        } else {
                            Log.w(TAG, "No mapping for sample: $sampleName")
                        }
                    }
                }
            }
        }

        // Build instrument and preset bag/gen/mod lists
        val instHeaders = mutableListOf<Pair<String, Int>>() // name, bagIndex
        val instBags = mutableListOf<Pair<Int, Int>>() // genIndex, modIndex
        val instGens = mutableListOf<Pair<Int, Int>>() // oper, amount
        val instMods = mutableListOf<Sf2Writer.InMemoryModulator>()

        val presetHeaders = mutableListOf<PresetHeaderData>()
        val presetBags = mutableListOf<Pair<Int, Int>>()
        val presetGens = mutableListOf<Pair<Int, Int>>()
        val presetMods = mutableListOf<Sf2Writer.InMemoryModulator>()

        // Track unique instruments to avoid duplicates
        // Use object identity instead of name to handle instruments with same name but different samples
        val uniqueInstruments = mutableMapOf<Sf2Writer.InstrumentExportData, Int>() // instrument object -> index in instHeaders

        // Process presets
        for (preset in presets) {
            presetHeaders.add(PresetHeaderData(
                name = preset.name.take(20),
                preset = preset.programNumber,
                bank = preset.bankNumber,
                bagIndex = presetBags.size
            ))

            // Global preset zone (if any)
            if (preset.presetGlobalParams != null && preset.presetGlobalParams.hasNonDefaultParams()) {
                presetBags.add(Pair(presetGens.size, presetMods.size))
                for ((gen, value) in preset.presetGlobalParams.buildGenerators()) {
                    presetGens.add(Pair(gen, value))
                }
                for (mod in preset.modulators) {
                    presetMods.add(mod)
                }
            }

            // Process each instrument in the preset
            for (instrument in preset.instruments) {
                // Get or create instrument (use object identity for deduplication)
                val instrumentIndex = uniqueInstruments.getOrPut(instrument) {
                    val idx = instHeaders.size
                    instHeaders.add(Pair(instrument.name.take(20), instBags.size))

                    // Global instrument zone (if any generators or modulators)
                    val hasGlobalZone = instrument.globalParams.hasAnyNonDefault() || instrument.globalModulators.isNotEmpty()
                    if (hasGlobalZone) {
                        instBags.add(Pair(instGens.size, instMods.size))
                        for ((gen, value) in instrument.globalParams.buildGenerators()) {
                            instGens.add(Pair(gen, value))
                        }
                        for (mod in instrument.globalModulators) {
                            instMods.add(mod)
                        }
                    }

                    // Sample zones
                    for (sample in instrument.samples) {
                        val sampleName = sample.name.take(20)
                        val sampleIdx = uniqueSamples[sampleName]?.second

                        if (sampleIdx != null) {
                            instBags.add(Pair(instGens.size, instMods.size))

                            // Key range
                            if (sample.keyRangeStart != 0 || sample.keyRangeEnd != 127) {
                                instGens.add(Pair(Sf2Constants.GEN_KEY_RANGE,
                                    (sample.keyRangeEnd shl 8) or sample.keyRangeStart))
                            }
                            // Vel range
                            if (sample.velRangeStart != 0 || sample.velRangeEnd != 127) {
                                instGens.add(Pair(Sf2Constants.GEN_VEL_RANGE,
                                    (sample.velRangeEnd shl 8) or sample.velRangeStart))
                            }

                            // Add other generators...
                            addSampleGenerators(instGens, sample)

                            // Sample ID (must be last)
                            instGens.add(Pair(Sf2Constants.GEN_SAMPLE_ID, sampleIdx))

                            // Sample modulators
                            for (mod in sample.modulators) {
                                instMods.add(mod)
                            }
                        }
                    }

                    idx
                }

                // Preset zone referencing instrument
                presetBags.add(Pair(presetGens.size, presetMods.size))

                // Preset zone generators (PGEN)
                val pgenParams = instrument.presetZoneParams
                for ((gen, value) in pgenParams.buildGenerators()) {
                    presetGens.add(Pair(gen, value))
                }

                // GEN_INSTRUMENT must be last
                presetGens.add(Pair(Sf2Constants.GEN_INSTRUMENT, instrumentIndex))

                // Preset zone modulators
                for (mod in instrument.presetZoneModulators) {
                    presetMods.add(mod)
                }
            }
        }

        // Add terminal entries
        instHeaders.add(Pair("EOI", instBags.size))
        instBags.add(Pair(instGens.size, instMods.size))
        instGens.add(Pair(0, 0)) // Terminal

        presetHeaders.add(PresetHeaderData("EOP", 0, 0, presetBags.size))
        presetBags.add(Pair(presetGens.size, presetMods.size))
        presetGens.add(Pair(0, 0)) // Terminal

        // Write phdr
        writeChunkId(raf, "phdr")
        writeUInt32(raf, (presetHeaders.size * 38).toLong())
        for (header in presetHeaders) {
            writeString(raf, header.name, 20)
            writeUInt16(raf, header.preset)
            writeUInt16(raf, header.bank)
            writeUInt16(raf, header.bagIndex)
            writeUInt32(raf, 0) // library
            writeUInt32(raf, 0) // genre
            writeUInt32(raf, 0) // morphology
        }

        // Write pbag
        writeChunkId(raf, "pbag")
        writeUInt32(raf, (presetBags.size * 4).toLong())
        for ((genIndex, modIndex) in presetBags) {
            writeUInt16(raf, genIndex)
            writeUInt16(raf, modIndex)
        }

        // Write pmod
        writeChunkId(raf, "pmod")
        val pmodCount = presetMods.size + 1 // +1 for terminal
        writeUInt32(raf, (pmodCount * 10).toLong())
        for (mod in presetMods) {
            writeUInt16(raf, mod.srcOper)
            writeUInt16(raf, mod.destOper)
            writeInt16(raf, mod.amount)
            writeUInt16(raf, mod.amtSrcOper)
            writeUInt16(raf, mod.transOper)
        }
        // Terminal modulator
        writeUInt16(raf, 0)
        writeUInt16(raf, 0)
        writeInt16(raf, 0)
        writeUInt16(raf, 0)
        writeUInt16(raf, 0)

        // Write pgen
        writeChunkId(raf, "pgen")
        writeUInt32(raf, (presetGens.size * 4).toLong())
        for ((oper, amount) in presetGens) {
            writeUInt16(raf, oper)
            writeInt16(raf, amount)
        }

        // Write inst
        writeChunkId(raf, "inst")
        writeUInt32(raf, (instHeaders.size * 22).toLong())
        for ((name, bagIndex) in instHeaders) {
            writeString(raf, name, 20)
            writeUInt16(raf, bagIndex)
        }

        // Write ibag
        writeChunkId(raf, "ibag")
        writeUInt32(raf, (instBags.size * 4).toLong())
        for ((genIndex, modIndex) in instBags) {
            writeUInt16(raf, genIndex)
            writeUInt16(raf, modIndex)
        }

        // Write imod
        writeChunkId(raf, "imod")
        val imodCount = instMods.size + 1 // +1 for terminal
        writeUInt32(raf, (imodCount * 10).toLong())
        for (mod in instMods) {
            writeUInt16(raf, mod.srcOper)
            writeUInt16(raf, mod.destOper)
            writeInt16(raf, mod.amount)
            writeUInt16(raf, mod.amtSrcOper)
            writeUInt16(raf, mod.transOper)
        }
        // Terminal
        writeUInt16(raf, 0)
        writeUInt16(raf, 0)
        writeInt16(raf, 0)
        writeUInt16(raf, 0)
        writeUInt16(raf, 0)

        // Write igen
        writeChunkId(raf, "igen")
        writeUInt32(raf, (instGens.size * 4).toLong())
        for ((oper, amount) in instGens) {
            writeUInt16(raf, oper)
            writeInt16(raf, amount)
        }

        // Write shdr using original sample offsets
        writeChunkId(raf, "shdr")
        val sampleList = uniqueSamples.values.sortedBy { it.second }.map { it.first }
        writeUInt32(raf, ((sampleList.size + 1) * 46).toLong()) // +1 for terminal

        for (mapping in sampleList) {
            writeString(raf, mapping.name, 20)
            writeUInt32(raf, mapping.startOffset)
            writeUInt32(raf, mapping.endOffset)
            writeUInt32(raf, mapping.loopStart)
            writeUInt32(raf, mapping.loopEnd)
            writeUInt32(raf, mapping.sampleRate.toLong())
            raf.writeByte(mapping.originalPitch)
            raf.writeByte(mapping.pitchCorrection)
            writeUInt16(raf, mapping.sampleLink)
            writeUInt16(raf, mapping.sampleType)
        }

        // Terminal sample header
        writeString(raf, "EOS", 20)
        writeUInt32(raf, 0)
        writeUInt32(raf, 0)
        writeUInt32(raf, 0)
        writeUInt32(raf, 0)
        writeUInt32(raf, 0)
        raf.writeByte(0)
        raf.writeByte(0)
        writeUInt16(raf, 0)
        writeUInt16(raf, 0)
    }

    /**
     * Add sample-level generators (excluding key/vel range and sampleId which are added separately).
     */
    private fun addSampleGenerators(gens: MutableList<Pair<Int, Int>>, sample: Sf2Writer.InMemorySample) {
        // LFOs
        if (sample.modLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_PITCH, sample.modLfoToPitch))
        if (sample.vibLfoToPitch != 0) gens.add(Pair(Sf2Constants.GEN_VIB_LFO_TO_PITCH, sample.vibLfoToPitch))
        if (sample.modEnvToPitch != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_PITCH, sample.modEnvToPitch))

        // Filter
        if (sample.filterFc != 13500) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_FC, sample.filterFc))
        if (sample.filterQ != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_FILTER_Q, sample.filterQ))
        if (sample.modLfoToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_FILTER_FC, sample.modLfoToFilterFc))
        if (sample.modEnvToFilterFc != 0) gens.add(Pair(Sf2Constants.GEN_MOD_ENV_TO_FILTER_FC, sample.modEnvToFilterFc))

        // Effects
        if (sample.modLfoToVolume != 0) gens.add(Pair(Sf2Constants.GEN_MOD_LFO_TO_VOLUME, sample.modLfoToVolume))
        if (sample.chorusSend != 0) gens.add(Pair(Sf2Constants.GEN_CHORUS_EFFECTS_SEND, sample.chorusSend))
        if (sample.reverbSend != 0) gens.add(Pair(Sf2Constants.GEN_REVERB_EFFECTS_SEND, sample.reverbSend))
        if (sample.pan != 0) gens.add(Pair(Sf2Constants.GEN_PAN, sample.pan))

        // LFO timing
        if (sample.modLfoDelay != -12000) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_LFO, sample.modLfoDelay))
        if (sample.modLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_MOD_LFO, sample.modLfoFreq))
        if (sample.vibLfoDelay != -12000) gens.add(Pair(Sf2Constants.GEN_DELAY_VIB_LFO, sample.vibLfoDelay))
        if (sample.vibLfoFreq != 0) gens.add(Pair(Sf2Constants.GEN_FREQ_VIB_LFO, sample.vibLfoFreq))

        // Mod Envelope
        if (sample.modEnvDelay != -12000) gens.add(Pair(Sf2Constants.GEN_DELAY_MOD_ENV, sample.modEnvDelay))
        if (sample.modEnvAttack != -12000) gens.add(Pair(Sf2Constants.GEN_ATTACK_MOD_ENV, sample.modEnvAttack))
        if (sample.modEnvHold != -12000) gens.add(Pair(Sf2Constants.GEN_HOLD_MOD_ENV, sample.modEnvHold))
        if (sample.modEnvDecay != -12000) gens.add(Pair(Sf2Constants.GEN_DECAY_MOD_ENV, sample.modEnvDecay))
        if (sample.modEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_MOD_ENV, sample.modEnvSustain))
        if (sample.modEnvRelease != -12000) gens.add(Pair(Sf2Constants.GEN_RELEASE_MOD_ENV, sample.modEnvRelease))

        // Key scaling
        if (sample.keyToModEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_HOLD, sample.keyToModEnvHold))
        if (sample.keyToModEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_MOD_ENV_DECAY, sample.keyToModEnvDecay))

        // Vol Envelope
        if (sample.volEnvDelay != -12000) gens.add(Pair(Sf2Constants.GEN_DELAY_VOL_ENV, sample.volEnvDelay))
        if (sample.volEnvAttack != -12000) gens.add(Pair(Sf2Constants.GEN_ATTACK_VOL_ENV, sample.volEnvAttack))
        if (sample.volEnvHold != -12000) gens.add(Pair(Sf2Constants.GEN_HOLD_VOL_ENV, sample.volEnvHold))
        if (sample.volEnvDecay != -12000) gens.add(Pair(Sf2Constants.GEN_DECAY_VOL_ENV, sample.volEnvDecay))
        if (sample.volEnvSustain != 0) gens.add(Pair(Sf2Constants.GEN_SUSTAIN_VOL_ENV, sample.volEnvSustain))
        if (sample.volEnvRelease != -12000) gens.add(Pair(Sf2Constants.GEN_RELEASE_VOL_ENV, sample.volEnvRelease))

        // More key scaling
        if (sample.keyToVolEnvHold != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_HOLD, sample.keyToVolEnvHold))
        if (sample.keyToVolEnvDecay != 0) gens.add(Pair(Sf2Constants.GEN_KEYNUM_TO_VOL_ENV_DECAY, sample.keyToVolEnvDecay))

        // Fixed key/velocity
        if (sample.fixedKey >= 0) gens.add(Pair(Sf2Constants.GEN_FIXED_KEY, sample.fixedKey))
        if (sample.fixedVelocity >= 0) gens.add(Pair(Sf2Constants.GEN_FIXED_VELOCITY, sample.fixedVelocity))

        // Attenuation and tuning
        if (sample.attenuation != 0) gens.add(Pair(Sf2Constants.GEN_INITIAL_ATTENUATION, sample.attenuation))
        if (sample.coarseTune != 0) gens.add(Pair(Sf2Constants.GEN_COARSE_TUNE, sample.coarseTune))
        if (sample.fineTuneCents != 0) gens.add(Pair(Sf2Constants.GEN_FINE_TUNE, sample.fineTuneCents))

        // Loop mode - use sampleModes if set, otherwise fallback to hasLoop
        val sampleMode = if (sample.sampleModes != 0) sample.sampleModes
                         else if (sample.hasLoop) 1  // Loop continuously
                         else 0
        if (sampleMode != 0) {
            gens.add(Pair(Sf2Constants.GEN_SAMPLE_MODES, sampleMode))
        }

        // Scale tuning and exclusive class
        if (sample.scaleTuning != 100) gens.add(Pair(Sf2Constants.GEN_SCALE_TUNING, sample.scaleTuning))
        if (sample.exclusiveClass != 0) gens.add(Pair(Sf2Constants.GEN_EXCLUSIVE_CLASS, sample.exclusiveClass))

        // Root key override
        gens.add(Pair(Sf2Constants.GEN_OVERRIDING_ROOT_KEY, sample.rootNote))
    }

    private data class PresetHeaderData(
        val name: String,
        val preset: Int,
        val bank: Int,
        val bagIndex: Int
    )

    /**
     * Data class for export context used during hybrid export.
     */
    data class HybridExportContext(
        val sourceFile: File,
        val registry: Map<String, ChunkInfo>,
        val passthroughChunks: Set<String>,
        val sampleIndexRemap: Map<Int, Int> // old index -> new index
    )
}
