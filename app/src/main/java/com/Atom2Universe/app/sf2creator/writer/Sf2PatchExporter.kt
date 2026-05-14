package com.Atom2Universe.app.sf2creator.writer

import android.util.Log
import com.Atom2Universe.app.sf2creator.data.AddedSample
import com.Atom2Universe.app.sf2creator.data.Sf2PatchService
import com.Atom2Universe.app.sf2creator.data.db.Sf2PatchDao
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PatchEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2LazyReader
import com.Atom2Universe.app.sf2creator.reader.Sf2ParseResult
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedInstrument
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPreset
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedSample
import com.Atom2Universe.app.sf2creator.util.WavUtils
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exports SF2 files with patches applied.
 *
 * In the "Polyphone-like" architecture:
 * - The original SF2 file is the source of truth
 * - We store only modifications (patches) in the database
 * - Export reads the original, applies patches, and writes the result
 *
 * Export strategies:
 * 1. Direct copy: No patches - just copy the original file
 * 2. Hybrid passthrough: Only parameter changes - copy smpl chunk, regenerate pdta
 * 3. Full regeneration: Structural changes (add/delete samples) - regenerate everything
 */
class Sf2PatchExporter(
    private val patchDao: Sf2PatchDao,
    private val patchService: Sf2PatchService
) {

    companion object {
        private const val TAG = "Sf2PatchExporter"
    }

    /**
     * Export a project to an SF2 file.
     *
     * @param sourceFilePath Path to the original SF2 file
     * @param projectId Project ID (for fetching patches)
     * @param outputFile Output file to write
     * @return true if successful
     */
    suspend fun export(
        sourceFilePath: String,
        projectId: Long,
        outputFile: File
    ): Boolean {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: $sourceFilePath")
            return false
        }

        val patches = patchDao.getPatchesForProject(projectId)

        return when {
            patches.isEmpty() -> {
                // No patches - just copy the original file
                Log.d(TAG, "No patches, copying original file directly")
                copyDirectly(sourceFile, outputFile)
            }
            !patchService.hasStructuralChanges(projectId) -> {
                // Only parameter changes - hybrid passthrough
                Log.d(TAG, "Only parameter changes, using hybrid passthrough")
                exportHybrid(sourceFile, projectId, patches, outputFile)
            }
            else -> {
                // Structural changes - full regeneration
                Log.d(TAG, "Structural changes detected, using full regeneration")
                exportFull(sourceFile, projectId, patches, outputFile)
            }
        }
    }

    /**
     * Export with automatic sample cleanup.
     * Includes only samples that are actually referenced by the included presets/instruments.
     * Use this when presets or instruments have been deleted and you want to remove
     * unreferenced samples as well.
     *
     * @param sourceFilePath Path to the original SF2 file
     * @param outputFile Output file to write
     * @param includedPresetIndices Set of preset indices to include. If null, includes all.
     * @param includedInstrumentIndices Set of instrument indices to include. If null, includes all.
     * @return true if successful
     */
    fun exportWithSampleCleanup(
        sourceFilePath: String,
        outputFile: File,
        includedPresetIndices: Set<Int>? = null,
        includedInstrumentIndices: Set<Int>? = null
    ): Boolean {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: $sourceFilePath")
            return false
        }

        val reader = Sf2LazyReader(sourceFile.absolutePath)
        val parseResult = reader.getParseResult()
        if (parseResult == null) {
            Log.e(TAG, "Failed to parse source file")
            return false
        }

        // Determine included presets and instruments
        val presetsToInclude = includedPresetIndices ?: parseResult.presets.map { it.index }.toSet()
        val instrumentsToInclude = includedInstrumentIndices ?: parseResult.instruments.map { it.index }.toSet()

        // Calculate which samples are actually referenced by included presets/instruments
        val referencedSamples = mutableSetOf<Int>()

        for (preset in parseResult.presets) {
            if (preset.index !in presetsToInclude) continue

            for (zone in preset.zones) {
                val instrument = zone.instrument ?: continue
                if (instrument.index !in instrumentsToInclude) continue

                for (instZone in instrument.zones) {
                    referencedSamples.add(instZone.sampleIndex)
                }
            }
        }

        Log.d(TAG, "Export with cleanup: ${presetsToInclude.size} presets, " +
                "${instrumentsToInclude.size} instruments, ${referencedSamples.size} samples")

        return exportFiltered(
            sourceFilePath = sourceFilePath,
            outputFile = outputFile,
            includedSampleIndices = referencedSamples,
            includedPresetIndices = presetsToInclude,
            includedInstrumentIndices = instrumentsToInclude
        )
    }

    /**
     * Export with explicit sample filtering.
     * Use this when the UI deletes samples directly from the DB (without patches).
     *
     * @param sourceFilePath Path to the original SF2 file
     * @param outputFile Output file to write
     * @param includedSampleIndices Set of sample indices from the source SF2 to include.
     *                              If null, includes all samples.
     * @param includedPresetIndices Set of preset indices to include. If null, includes all.
     * @param includedInstrumentIndices Set of instrument indices to include. If null, includes all.
     * @return true if successful
     */
    fun exportFiltered(
        sourceFilePath: String,
        outputFile: File,
        includedSampleIndices: Set<Int>? = null,
        includedPresetIndices: Set<Int>? = null,
        includedInstrumentIndices: Set<Int>? = null
    ): Boolean {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: $sourceFilePath")
            return false
        }

        val reader = Sf2LazyReader(sourceFile.absolutePath)
        val parseResult = reader.getParseResult()
        if (parseResult == null) {
            Log.e(TAG, "Failed to parse source file")
            return false
        }

        // Determine what to include/exclude
        val allSampleIndices = parseResult.samples.map { it.index }.toSet()
        val allPresetIndices = parseResult.presets.map { it.index }.toSet()
        val allInstrumentIndices = parseResult.instruments.map { it.index }.toSet()

        val samplesToInclude = includedSampleIndices ?: allSampleIndices
        val presetsToInclude = includedPresetIndices ?: allPresetIndices
        val instrumentsToInclude = includedInstrumentIndices ?: allInstrumentIndices

        val deletedSamples = allSampleIndices - samplesToInclude
        val deletedPresets = allPresetIndices - presetsToInclude
        val deletedInstruments = allInstrumentIndices - instrumentsToInclude

        // If nothing is deleted, just copy the file
        if (deletedSamples.isEmpty() && deletedPresets.isEmpty() && deletedInstruments.isEmpty()) {
            Log.d(TAG, "No elements filtered, copying original file directly")
            return copyDirectly(sourceFile, outputFile)
        }

        Log.d(TAG, "Exporting with filter: excluding ${deletedSamples.size} samples, " +
                "${deletedPresets.size} presets, ${deletedInstruments.size} instruments")

        return exportFilteredFull(
            reader = reader,
            parseResult = parseResult,
            outputFile = outputFile,
            deletedSamples = deletedSamples,
            deletedPresets = deletedPresets,
            deletedInstruments = deletedInstruments
        )
    }

    /**
     * Full regeneration export with explicit filtering.
     * Rewrites the entire SF2 file, only including specified elements.
     */
    private fun exportFilteredFull(
        reader: Sf2LazyReader,
        parseResult: Sf2ParseResult,
        outputFile: File,
        deletedSamples: Set<Int>,
        deletedPresets: Set<Int>,
        deletedInstruments: Set<Int>
    ): Boolean {
        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0)

                // Write RIFF header placeholder
                val riffStart = raf.filePointer
                raf.writeBytes("RIFF")
                raf.writeInt(0)
                raf.writeBytes("sfbk")

                // Write INFO chunk
                writeInfoChunk(raf, parseResult.info.name)

                // Filter samples and build mapping
                val includedSamples = parseResult.samples.filter { it.index !in deletedSamples }

                // Write sdta with only included samples
                val sampleMapping = writeSdtaChunkFiltered(raf, reader, includedSamples)

                // Filter presets and instruments
                val includedPresets = parseResult.presets.filter { it.index !in deletedPresets }
                val includedInstruments = parseResult.instruments.filter { it.index !in deletedInstruments }

                // Build instrument index mapping (old index -> new index)
                val instrumentMapping = includedInstruments.mapIndexed { newIdx, inst ->
                    inst.index to newIdx
                }.toMap()

                // Write pdta with filtered and remapped data
                writePdtaChunkFiltered(
                    raf = raf,
                    presets = includedPresets,
                    instruments = includedInstruments,
                    sampleMapping = sampleMapping,
                    instrumentMapping = instrumentMapping,
                    deletedSamples = deletedSamples
                )

                // Update RIFF size
                val fileSize = raf.filePointer
                raf.seek(riffStart + 4)
                writeInt32LE(raf, (fileSize - 8).toInt())

                Log.d(TAG, "Filtered export complete: ${outputFile.length()} bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export filtered", e)
            return false
        }
    }

    /**
     * Write sdta chunk with only included samples.
     * Returns a mapping from old sample index to (new index, new start offset, new end offset).
     */
    private fun writeSdtaChunkFiltered(
        raf: RandomAccessFile,
        reader: Sf2LazyReader,
        includedSamples: List<Sf2ParsedSample>
    ): Map<Int, SampleMapping> {
        val sampleMapping = mutableMapOf<Int, SampleMapping>()

        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0)
        raf.writeBytes("sdta")

        val smplStart = raf.filePointer
        raf.writeBytes("smpl")
        raf.writeInt(0) // Placeholder

        var currentOffset = 0L // In samples (not bytes)
        var newSampleIndex = 0

        for (sample in includedSamples) {
            val audio = reader.extractSampleAudio(sample.index)
            if (audio != null) {
                val newStart = currentOffset

                // Write audio data
                for (s in audio) {
                    writeInt16LE(raf, s.toInt())
                }

                val audioLength = audio.size.toLong()
                val newEnd = currentOffset + audioLength

                // Calculate new loop points relative to new start
                val loopOffset = sample.loopStart - sample.start
                val loopLength = sample.loopEnd - sample.loopStart
                val newLoopStart = newStart + loopOffset
                val newLoopEnd = newLoopStart + loopLength

                // SF2 requires 46 zero samples padding after each sample
                repeat(46) { writeInt16LE(raf, 0) }

                sampleMapping[sample.index] = SampleMapping(
                    newIndex = newSampleIndex,
                    newStart = newStart,
                    newEnd = newEnd,
                    newLoopStart = newLoopStart,
                    newLoopEnd = newLoopEnd,
                    originalSample = sample
                )

                currentOffset = newEnd + 46 // Include padding in offset
                newSampleIndex++
            }
        }

        // Update smpl size
        val smplEnd = raf.filePointer
        raf.seek(smplStart + 4)
        writeInt32LE(raf, (smplEnd - smplStart - 8).toInt())
        raf.seek(smplEnd)

        // Pad to even if necessary
        if ((smplEnd - smplStart) % 2 != 0L) {
            raf.writeByte(0)
        }

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)

        return sampleMapping
    }

    /**
     * Write pdta chunk with filtered and remapped data.
     */
    private fun writePdtaChunkFiltered(
        raf: RandomAccessFile,
        presets: List<Sf2ParsedPreset>,
        instruments: List<Sf2ParsedInstrument>,
        sampleMapping: Map<Int, SampleMapping>,
        instrumentMapping: Map<Int, Int>,
        deletedSamples: Set<Int>
    ) {
        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0)
        raf.writeBytes("pdta")

        // phdr - Preset headers
        writePhdrFiltered(raf, presets)

        // pbag - Preset bags (with remapped instrument indices)
        writePbagFiltered(raf, presets, instrumentMapping)

        // pmod - Preset modulators
        writePmodFiltered(raf, presets)

        // pgen - Preset generators (with remapped instrument indices)
        writePgenFiltered(raf, presets, instrumentMapping)

        // inst - Instrument headers
        writeInstFiltered(raf, instruments, deletedSamples)

        // ibag - Instrument bags
        writeIbagFiltered(raf, instruments, deletedSamples)

        // imod - Instrument modulators
        writeImodFiltered(raf, instruments, deletedSamples)

        // igen - Instrument generators (with remapped sample indices)
        writeIgenFiltered(raf, instruments, sampleMapping, deletedSamples)

        // shdr - Sample headers (with remapped offsets)
        writeShdrFiltered(raf, sampleMapping)

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)
    }

    private fun writePhdrFiltered(raf: RandomAccessFile, presets: List<Sf2ParsedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("phdr")
        raf.writeInt(0)

        var bagIndex = 0
        for (preset in presets) {
            writePaddedString(raf, preset.name, 20)
            writeInt16LE(raf, preset.programNumber)
            writeInt16LE(raf, preset.bankNumber)
            writeInt16LE(raf, bagIndex)
            writeInt32LE(raf, 0) // library
            writeInt32LE(raf, 0) // genre
            writeInt32LE(raf, 0) // morphology

            // Count zones (global + regular)
            val hasGlobal = preset.globalGenerators.isNotEmpty() || preset.globalModulators.isNotEmpty()
            bagIndex += preset.zones.size + (if (hasGlobal) 1 else 0)
        }

        // Terminal preset
        writePaddedString(raf, "EOP", 20)
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)
        writeInt16LE(raf, bagIndex)
        writeInt32LE(raf, 0)
        writeInt32LE(raf, 0)
        writeInt32LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writePbagFiltered(
        raf: RandomAccessFile,
        presets: List<Sf2ParsedPreset>,
        instrumentMapping: Map<Int, Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pbag")
        raf.writeInt(0)

        var genIndex = 0
        var modIndex = 0
        for (preset in presets) {
            // Global zone
            if (preset.globalGenerators.isNotEmpty() || preset.globalModulators.isNotEmpty()) {
                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += preset.globalGenerators.size
                modIndex += preset.globalModulators.size
            }
            // Regular zones
            for (zone in preset.zones) {
                // Skip zones with unmapped instruments
                if (zone.instrumentIndex !in instrumentMapping) continue

                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += zone.generators.size + 1 // +1 for instrument reference
                modIndex += zone.modulators.size
            }
        }

        // Terminal bag
        writeInt16LE(raf, genIndex)
        writeInt16LE(raf, modIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writePmodFiltered(raf: RandomAccessFile, presets: List<Sf2ParsedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pmod")
        raf.writeInt(0)

        for (preset in presets) {
            for (mod in preset.globalModulators) {
                writeInt16LE(raf, mod.srcOper)
                writeInt16LE(raf, mod.destOper)
                writeInt16LE(raf, mod.amount)
                writeInt16LE(raf, mod.amtSrcOper)
                writeInt16LE(raf, mod.transOper)
            }
            for (zone in preset.zones) {
                for (mod in zone.modulators) {
                    writeInt16LE(raf, mod.srcOper)
                    writeInt16LE(raf, mod.destOper)
                    writeInt16LE(raf, mod.amount)
                    writeInt16LE(raf, mod.amtSrcOper)
                    writeInt16LE(raf, mod.transOper)
                }
            }
        }

        // Terminal modulator
        repeat(5) { writeInt16LE(raf, 0) }

        updateChunkSize(raf, chunkStart)
    }

    private fun writePgenFiltered(
        raf: RandomAccessFile,
        presets: List<Sf2ParsedPreset>,
        instrumentMapping: Map<Int, Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pgen")
        raf.writeInt(0)

        for (preset in presets) {
            // Global zone generators
            for ((genId, value) in preset.globalGenerators) {
                writeInt16LE(raf, genId)
                writeInt16LE(raf, value)
            }
            // Regular zone generators
            for (zone in preset.zones) {
                val newInstrumentIndex = instrumentMapping[zone.instrumentIndex] ?: continue

                for ((genId, value) in zone.generators) {
                    writeInt16LE(raf, genId)
                    writeInt16LE(raf, value)
                }
                // Instrument reference with remapped index
                writeInt16LE(raf, 41) // GEN_INSTRUMENT
                writeInt16LE(raf, newInstrumentIndex)
            }
        }

        // Terminal generator
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeInstFiltered(
        raf: RandomAccessFile,
        instruments: List<Sf2ParsedInstrument>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("inst")
        raf.writeInt(0)

        var bagIndex = 0
        for (inst in instruments) {
            writePaddedString(raf, inst.name, 20)
            writeInt16LE(raf, bagIndex)

            // Count zones (global + non-deleted sample zones)
            val hasGlobal = inst.globalGenerators.isNotEmpty() || inst.globalModulators.isNotEmpty()
            val validZones = inst.zones.count { it.sampleIndex !in deletedSamples }
            bagIndex += validZones + (if (hasGlobal) 1 else 0)
        }

        // Terminal instrument
        writePaddedString(raf, "EOI", 20)
        writeInt16LE(raf, bagIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeIbagFiltered(
        raf: RandomAccessFile,
        instruments: List<Sf2ParsedInstrument>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("ibag")
        raf.writeInt(0)

        var genIndex = 0
        var modIndex = 0
        for (inst in instruments) {
            // Global zone
            if (inst.globalGenerators.isNotEmpty() || inst.globalModulators.isNotEmpty()) {
                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += inst.globalGenerators.size
                modIndex += inst.globalModulators.size
            }
            // Sample zones
            for (zone in inst.zones) {
                if (zone.sampleIndex in deletedSamples) continue

                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += zone.generators.size + 1 // +1 for sample reference
                modIndex += zone.modulators.size
            }
        }

        // Terminal bag
        writeInt16LE(raf, genIndex)
        writeInt16LE(raf, modIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeImodFiltered(
        raf: RandomAccessFile,
        instruments: List<Sf2ParsedInstrument>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("imod")
        raf.writeInt(0)

        for (inst in instruments) {
            for (mod in inst.globalModulators) {
                writeInt16LE(raf, mod.srcOper)
                writeInt16LE(raf, mod.destOper)
                writeInt16LE(raf, mod.amount)
                writeInt16LE(raf, mod.amtSrcOper)
                writeInt16LE(raf, mod.transOper)
            }
            for (zone in inst.zones) {
                if (zone.sampleIndex in deletedSamples) continue

                for (mod in zone.modulators) {
                    writeInt16LE(raf, mod.srcOper)
                    writeInt16LE(raf, mod.destOper)
                    writeInt16LE(raf, mod.amount)
                    writeInt16LE(raf, mod.amtSrcOper)
                    writeInt16LE(raf, mod.transOper)
                }
            }
        }

        // Terminal modulator
        repeat(5) { writeInt16LE(raf, 0) }

        updateChunkSize(raf, chunkStart)
    }

    private fun writeIgenFiltered(
        raf: RandomAccessFile,
        instruments: List<Sf2ParsedInstrument>,
        sampleMapping: Map<Int, SampleMapping>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("igen")
        raf.writeInt(0)

        for (inst in instruments) {
            // Global zone generators
            for ((genId, value) in inst.globalGenerators) {
                writeInt16LE(raf, genId)
                writeInt16LE(raf, value)
            }

            // Sample zone generators
            for (zone in inst.zones) {
                if (zone.sampleIndex in deletedSamples) continue

                val mapping = sampleMapping[zone.sampleIndex] ?: continue

                for ((genId, value) in zone.generators) {
                    writeInt16LE(raf, genId)
                    writeInt16LE(raf, value)
                }

                // Sample reference with remapped index
                writeInt16LE(raf, 53) // GEN_SAMPLE_ID
                writeInt16LE(raf, mapping.newIndex)
            }
        }

        // Terminal generator
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeShdrFiltered(
        raf: RandomAccessFile,
        sampleMapping: Map<Int, SampleMapping>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("shdr")
        raf.writeInt(0)

        // Sort by new index to write in correct order
        val sortedMappings = sampleMapping.entries.sortedBy { it.value.newIndex }

        for ((_, mapping) in sortedMappings) {
            val sample = mapping.originalSample

            writePaddedString(raf, sample.name, 20)
            writeInt32LE(raf, mapping.newStart.toInt())
            writeInt32LE(raf, mapping.newEnd.toInt())
            writeInt32LE(raf, mapping.newLoopStart.toInt())
            writeInt32LE(raf, mapping.newLoopEnd.toInt())
            writeInt32LE(raf, sample.sampleRate)
            raf.writeByte(sample.originalPitch)
            raf.writeByte(sample.pitchCorrection)
            writeInt16LE(raf, 0) // sampleLink
            writeInt16LE(raf, sample.sampleType)
        }

        // Terminal sample
        writePaddedString(raf, "EOS", 20)
        repeat(9) { writeInt32LE(raf, 0) }
        writeInt16LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    /**
     * Sample mapping info for filtered export.
     */
    data class SampleMapping(
        val newIndex: Int,
        val newStart: Long,
        val newEnd: Long,
        val newLoopStart: Long,
        val newLoopEnd: Long,
        val originalSample: Sf2ParsedSample
    )

    /**
     * Copy the source file directly (no patches).
     */
    private fun copyDirectly(sourceFile: File, outputFile: File): Boolean {
        return try {
            sourceFile.copyTo(outputFile, overwrite = true)
            Log.d(TAG, "Copied ${sourceFile.length()} bytes to ${outputFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file", e)
            false
        }
    }

    /**
     * Hybrid passthrough export.
     * Copies the smpl chunk directly from the source, regenerates pdta with patches.
     *
     * This is efficient because:
     * - The smpl chunk (sample audio data) is usually the largest part (>90% of file size)
     * - We only regenerate the smaller pdta chunk with modified parameters
     */
    private suspend fun exportHybrid(
        sourceFile: File,
        projectId: Long,
        patches: List<Sf2PatchEntity>,
        outputFile: File
    ): Boolean {
        val reader = Sf2LazyReader(sourceFile.absolutePath)
        val parseResult = reader.getParseResult()
        if (parseResult == null) {
            Log.e(TAG, "Failed to parse source file")
            return false
        }

        val chunkInfo = reader.scanChunks()
        if (chunkInfo == null) {
            Log.e(TAG, "Failed to scan source file chunks")
            return false
        }

        val smplChunk = chunkInfo["smpl"]
        if (smplChunk == null) {
            Log.e(TAG, "No smpl chunk found in source file")
            return false
        }

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0)

                // Collect patches by target
                val presetPatches = patches.filter { it.targetType == Sf2PatchEntity.TARGET_PRESET }
                    .groupBy { it.targetIndex }
                val instrumentPatches = patches.filter { it.targetType == Sf2PatchEntity.TARGET_INSTRUMENT }
                    .groupBy { it.targetIndex }
                val samplePatches = patches.filter { it.targetType == Sf2PatchEntity.TARGET_SAMPLE }
                    .groupBy { it.targetIndex }

                // Filter out deleted elements
                val deletedPresets = patches.filter {
                    it.targetType == Sf2PatchEntity.TARGET_PRESET &&
                    it.patchType == Sf2PatchEntity.PATCH_DELETE
                }.map { it.targetIndex }.toSet()

                val deletedInstruments = patches.filter {
                    it.targetType == Sf2PatchEntity.TARGET_INSTRUMENT &&
                    it.patchType == Sf2PatchEntity.PATCH_DELETE
                }.map { it.targetIndex }.toSet()

                val deletedSamples = patches.filter {
                    it.targetType == Sf2PatchEntity.TARGET_SAMPLE &&
                    it.patchType == Sf2PatchEntity.PATCH_DELETE
                }.map { it.targetIndex }.toSet()

                // Build patched data structures
                val patchedPresets = parseResult.presets
                    .filter { it.index !in deletedPresets }
                    .map { preset ->
                        val presetPatchList = presetPatches[preset.index] ?: emptyList()
                        patchService.applyPresetPatches(projectId, preset, presetPatchList)
                    }

                val patchedInstruments = parseResult.instruments
                    .filter { it.index !in deletedInstruments }
                    .map { instrument ->
                        val instPatchList = instrumentPatches[instrument.index] ?: emptyList()
                        patchService.applyInstrumentPatches(projectId, instrument, instPatchList)
                    }

                // Write RIFF header placeholder
                val riffStart = raf.filePointer
                raf.writeBytes("RIFF")
                raf.writeInt(0) // Placeholder for size
                raf.writeBytes("sfbk")

                // Write INFO chunk (regenerated with updated name if needed)
                writeInfoChunk(raf, parseResult.info.name)

                // Write sdta (copy smpl chunk from source)
                writeSdtaChunkFromSource(raf, sourceFile, smplChunk.offset, smplChunk.size)

                // Write pdta with patches applied
                writePdtaChunk(raf, parseResult, patchedPresets, patchedInstruments,
                    samplePatches, deletedSamples)

                // Update RIFF size
                val fileSize = raf.filePointer
                raf.seek(riffStart + 4)
                writeInt32LE(raf, (fileSize - 8).toInt())

                Log.d(TAG, "Hybrid export complete: ${outputFile.length()} bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export hybrid", e)
            return false
        }
    }

    /**
     * Full regeneration export.
     * Used when samples have been added or deleted.
     */
    private suspend fun exportFull(
        sourceFile: File,
        projectId: Long,
        patches: List<Sf2PatchEntity>,
        outputFile: File
    ): Boolean {
        val reader = Sf2LazyReader(sourceFile.absolutePath)
        val parseResult = reader.getParseResult()
        if (parseResult == null) {
            Log.e(TAG, "Failed to parse source file")
            return false
        }

        // Get added samples
        val addedSamples = patchService.getAddedSamples(projectId)

        // Filter deleted samples
        val deletedSamples = patches.filter {
            it.targetType == Sf2PatchEntity.TARGET_SAMPLE &&
            it.patchType == Sf2PatchEntity.PATCH_DELETE
        }.map { it.targetIndex }.toSet()

        // Collect patches by target
        val samplePatches = patches.filter { it.targetType == Sf2PatchEntity.TARGET_SAMPLE }
            .groupBy { it.targetIndex }

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(0)

                // Write RIFF header placeholder
                val riffStart = raf.filePointer
                raf.writeBytes("RIFF")
                raf.writeInt(0)
                raf.writeBytes("sfbk")

                // Write INFO chunk
                writeInfoChunk(raf, parseResult.info.name)

                // Build combined sample list (original minus deleted plus added)
                val originalSamples = parseResult.samples.filter { it.index !in deletedSamples }

                // Write sdta with combined samples
                val sampleMapping = writeSdtaChunkFull(raf, reader, originalSamples, addedSamples)

                // Write pdta with updated indices
                writePdtaChunkFull(raf, parseResult, sampleMapping, samplePatches,
                    deletedSamples, addedSamples)

                // Update RIFF size
                val fileSize = raf.filePointer
                raf.seek(riffStart + 4)
                writeInt32LE(raf, (fileSize - 8).toInt())

                Log.d(TAG, "Full export complete: ${outputFile.length()} bytes")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export full", e)
            return false
        }
    }

    // ==================== Chunk Writers ====================

    private fun writeInfoChunk(raf: RandomAccessFile, name: String) {
        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0) // Placeholder
        raf.writeBytes("INFO")

        // ifil - SF2 version (2.01)
        raf.writeBytes("ifil")
        writeInt32LE(raf, 4)
        writeInt16LE(raf, 2)
        writeInt16LE(raf, 1)

        // isng - Sound engine
        val engine = "EMU8000"
        raf.writeBytes("isng")
        val engineBytes = engine.toByteArray(Charsets.US_ASCII)
        val engineSize = ((engineBytes.size + 1 + 1) / 2) * 2 // Pad to even
        writeInt32LE(raf, engineSize)
        raf.write(engineBytes)
        raf.writeByte(0) // Null terminator
        if (engineSize > engineBytes.size + 1) {
            raf.writeByte(0) // Padding
        }

        // INAM - Name
        raf.writeBytes("INAM")
        val nameBytes = name.take(255).toByteArray(Charsets.US_ASCII)
        val nameSize = ((nameBytes.size + 1 + 1) / 2) * 2
        writeInt32LE(raf, nameSize)
        raf.write(nameBytes)
        raf.writeByte(0)
        if (nameSize > nameBytes.size + 1) {
            raf.writeByte(0)
        }

        // ISFT - Software
        val software = "A2U SF2 Creator"
        raf.writeBytes("ISFT")
        val softwareBytes = software.toByteArray(Charsets.US_ASCII)
        val softwareSize = ((softwareBytes.size + 1 + 1) / 2) * 2
        writeInt32LE(raf, softwareSize)
        raf.write(softwareBytes)
        raf.writeByte(0)
        if (softwareSize > softwareBytes.size + 1) {
            raf.writeByte(0)
        }

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)
    }

    private fun writeSdtaChunkFromSource(
        raf: RandomAccessFile,
        sourceFile: File,
        smplOffset: Long,
        smplSize: Long
    ) {
        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0) // Placeholder
        raf.writeBytes("sdta")

        // Copy smpl chunk from source
        RandomAccessFile(sourceFile, "r").use { sourceRaf ->
            sourceRaf.seek(smplOffset)
            val buffer = ByteArray(65536)
            var remaining = smplSize
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = sourceRaf.read(buffer, 0, toRead)
                if (read <= 0) break
                raf.write(buffer, 0, read)
                remaining -= read
            }
        }

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)
    }

    private fun writeSdtaChunkFull(
        raf: RandomAccessFile,
        reader: Sf2LazyReader,
        originalSamples: List<Sf2ParsedSample>,
        addedSamples: List<AddedSample>
    ): Map<Int, Int> {
        // Maps original sample index to new sample index
        val sampleMapping = mutableMapOf<Int, Int>()

        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0)
        raf.writeBytes("sdta")

        val smplStart = raf.filePointer
        raf.writeBytes("smpl")
        raf.writeInt(0) // Placeholder

        var currentSampleIndex = 0
        var sampleDataOffset = 0L

        // Write original samples
        for (sample in originalSamples) {
            sampleMapping[sample.index] = currentSampleIndex
            val audio = reader.extractSampleAudio(sample.index)
            if (audio != null) {
                for (s in audio) {
                    writeInt16LE(raf, s.toInt())
                }
                // SF2 requires 46 zero samples after each sample
                repeat(46) { writeInt16LE(raf, 0) }
            }
            currentSampleIndex++
        }

        // Write added samples
        for (added in addedSamples) {
            val audioFile = File(added.audioFilePath)
            if (audioFile.exists()) {
                val audio = WavUtils.loadWavFile(audioFile)
                if (audio != null) {
                    for (s in audio) {
                        writeInt16LE(raf, s.toInt())
                    }
                    repeat(46) { writeInt16LE(raf, 0) }
                }
            }
            // Added samples get negative indices in the mapping (to distinguish from original)
            sampleMapping[-currentSampleIndex - 1] = currentSampleIndex
            currentSampleIndex++
        }

        // Update smpl size
        val smplEnd = raf.filePointer
        raf.seek(smplStart + 4)
        writeInt32LE(raf, (smplEnd - smplStart - 8).toInt())
        raf.seek(smplEnd)

        // Pad to even if necessary
        if ((smplEnd - smplStart) % 2 != 0L) {
            raf.writeByte(0)
        }

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)

        return sampleMapping
    }

    private fun writePdtaChunk(
        raf: RandomAccessFile,
        parseResult: Sf2ParseResult,
        patchedPresets: List<com.Atom2Universe.app.sf2creator.data.PatchedPreset>,
        patchedInstruments: List<com.Atom2Universe.app.sf2creator.data.PatchedInstrument>,
        samplePatches: Map<Int, List<Sf2PatchEntity>>,
        deletedSamples: Set<Int>
    ) {
        // For hybrid export, sample indices remain the same
        // Just write pdta with patched parameters

        val listStart = raf.filePointer
        raf.writeBytes("LIST")
        raf.writeInt(0)
        raf.writeBytes("pdta")

        // phdr - Preset headers
        writePhdr(raf, patchedPresets)

        // pbag - Preset bags
        writePbag(raf, patchedPresets)

        // pmod - Preset modulators (write empty for now, could be enhanced)
        writePmod(raf, patchedPresets)

        // pgen - Preset generators
        writePgen(raf, patchedPresets)

        // inst - Instrument headers
        writeInst(raf, patchedInstruments)

        // ibag - Instrument bags
        writeIbag(raf, patchedInstruments, parseResult, deletedSamples)

        // imod - Instrument modulators
        writeImod(raf, patchedInstruments, parseResult)

        // igen - Instrument generators
        writeIgen(raf, patchedInstruments, parseResult, samplePatches, deletedSamples)

        // shdr - Sample headers
        writeShdr(raf, parseResult.samples, deletedSamples)

        // Update LIST size
        val listEnd = raf.filePointer
        raf.seek(listStart + 4)
        writeInt32LE(raf, (listEnd - listStart - 8).toInt())
        raf.seek(listEnd)
    }

    private fun writePdtaChunkFull(
        raf: RandomAccessFile,
        parseResult: Sf2ParseResult,
        sampleMapping: Map<Int, Int>,
        samplePatches: Map<Int, List<Sf2PatchEntity>>,
        deletedSamples: Set<Int>,
        addedSamples: List<AddedSample>
    ) {
        // Full regeneration with sample index remapping
        // TODO: Implement full pdta regeneration with index remapping
        // For now, fall back to basic pdta writing
        val emptyPatchedPresets = parseResult.presets.map { preset ->
            com.Atom2Universe.app.sf2creator.data.PatchedPreset(
                originalPreset = preset,
                name = preset.name,
                modifiedGenerators = preset.globalGenerators,
                isDeleted = false
            )
        }
        val emptyPatchedInstruments = parseResult.instruments.map { inst ->
            com.Atom2Universe.app.sf2creator.data.PatchedInstrument(
                originalInstrument = inst,
                name = inst.name,
                modifiedGenerators = inst.globalGenerators,
                isDeleted = false
            )
        }

        writePdtaChunk(raf, parseResult, emptyPatchedPresets, emptyPatchedInstruments,
            samplePatches, deletedSamples)
    }

    // ==================== pdta Sub-chunk Writers ====================

    private fun writePhdr(raf: RandomAccessFile, presets: List<com.Atom2Universe.app.sf2creator.data.PatchedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("phdr")
        raf.writeInt(0) // Placeholder

        var bagIndex = 0
        for (preset in presets) {
            writePaddedString(raf, preset.name, 20)
            writeInt16LE(raf, preset.programNumber)
            writeInt16LE(raf, preset.bankNumber)
            writeInt16LE(raf, bagIndex)
            writeInt32LE(raf, 0) // library
            writeInt32LE(raf, 0) // genre
            writeInt32LE(raf, 0) // morphology
            bagIndex += preset.zones.size + 1 // +1 for global zone if any
        }

        // Terminal preset
        writePaddedString(raf, "EOP", 20)
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)
        writeInt16LE(raf, bagIndex)
        writeInt32LE(raf, 0)
        writeInt32LE(raf, 0)
        writeInt32LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writePbag(raf: RandomAccessFile, presets: List<com.Atom2Universe.app.sf2creator.data.PatchedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pbag")
        raf.writeInt(0)

        var genIndex = 0
        var modIndex = 0
        for (preset in presets) {
            // Global zone (if present)
            if (preset.originalPreset.globalGenerators.isNotEmpty() ||
                preset.originalPreset.globalModulators.isNotEmpty()) {
                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += preset.originalPreset.globalGenerators.size
                modIndex += preset.originalPreset.globalModulators.size
            }
            // Regular zones
            for (zone in preset.zones) {
                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += zone.generators.size + 1 // +1 for instrument reference
                modIndex += zone.modulators.size
            }
        }

        // Terminal bag
        writeInt16LE(raf, genIndex)
        writeInt16LE(raf, modIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writePmod(raf: RandomAccessFile, presets: List<com.Atom2Universe.app.sf2creator.data.PatchedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pmod")
        raf.writeInt(0)

        for (preset in presets) {
            for (mod in preset.originalPreset.globalModulators) {
                writeInt16LE(raf, mod.srcOper)
                writeInt16LE(raf, mod.destOper)
                writeInt16LE(raf, mod.amount)
                writeInt16LE(raf, mod.amtSrcOper)
                writeInt16LE(raf, mod.transOper)
            }
            for (zone in preset.zones) {
                for (mod in zone.modulators) {
                    writeInt16LE(raf, mod.srcOper)
                    writeInt16LE(raf, mod.destOper)
                    writeInt16LE(raf, mod.amount)
                    writeInt16LE(raf, mod.amtSrcOper)
                    writeInt16LE(raf, mod.transOper)
                }
            }
        }

        // Terminal modulator
        repeat(5) { writeInt16LE(raf, 0) }

        updateChunkSize(raf, chunkStart)
    }

    private fun writePgen(raf: RandomAccessFile, presets: List<com.Atom2Universe.app.sf2creator.data.PatchedPreset>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("pgen")
        raf.writeInt(0)

        for (preset in presets) {
            // Global zone generators
            for ((genId, value) in preset.modifiedGenerators) {
                writeInt16LE(raf, genId)
                writeInt16LE(raf, value)
            }
            // Regular zone generators
            for (zone in preset.zones) {
                for ((genId, value) in zone.generators) {
                    writeInt16LE(raf, genId)
                    writeInt16LE(raf, value)
                }
                // Instrument reference (gen 41)
                writeInt16LE(raf, 41) // GEN_INSTRUMENT
                writeInt16LE(raf, zone.instrumentIndex)
            }
        }

        // Terminal generator
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeInst(raf: RandomAccessFile, instruments: List<com.Atom2Universe.app.sf2creator.data.PatchedInstrument>) {
        val chunkStart = raf.filePointer
        raf.writeBytes("inst")
        raf.writeInt(0)

        var bagIndex = 0
        for (inst in instruments) {
            writePaddedString(raf, inst.name, 20)
            writeInt16LE(raf, bagIndex)
            bagIndex += inst.zones.size + 1 // +1 for global zone
        }

        // Terminal instrument
        writePaddedString(raf, "EOI", 20)
        writeInt16LE(raf, bagIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeIbag(
        raf: RandomAccessFile,
        instruments: List<com.Atom2Universe.app.sf2creator.data.PatchedInstrument>,
        parseResult: Sf2ParseResult,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("ibag")
        raf.writeInt(0)

        var genIndex = 0
        var modIndex = 0
        for (inst in instruments) {
            val origInst = inst.originalInstrument
            // Global zone
            if (origInst.globalGenerators.isNotEmpty() || origInst.globalModulators.isNotEmpty()) {
                writeInt16LE(raf, genIndex)
                writeInt16LE(raf, modIndex)
                genIndex += origInst.globalGenerators.size
                modIndex += origInst.globalModulators.size
            }
            // Sample zones
            for (zone in origInst.zones) {
                if (zone.sampleIndex !in deletedSamples) {
                    writeInt16LE(raf, genIndex)
                    writeInt16LE(raf, modIndex)
                    genIndex += zone.generators.size + 1 // +1 for sample reference
                    modIndex += zone.modulators.size
                }
            }
        }

        // Terminal bag
        writeInt16LE(raf, genIndex)
        writeInt16LE(raf, modIndex)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeImod(
        raf: RandomAccessFile,
        instruments: List<com.Atom2Universe.app.sf2creator.data.PatchedInstrument>,
        parseResult: Sf2ParseResult
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("imod")
        raf.writeInt(0)

        for (inst in instruments) {
            val origInst = inst.originalInstrument
            for (mod in origInst.globalModulators) {
                writeInt16LE(raf, mod.srcOper)
                writeInt16LE(raf, mod.destOper)
                writeInt16LE(raf, mod.amount)
                writeInt16LE(raf, mod.amtSrcOper)
                writeInt16LE(raf, mod.transOper)
            }
            for (zone in origInst.zones) {
                for (mod in zone.modulators) {
                    writeInt16LE(raf, mod.srcOper)
                    writeInt16LE(raf, mod.destOper)
                    writeInt16LE(raf, mod.amount)
                    writeInt16LE(raf, mod.amtSrcOper)
                    writeInt16LE(raf, mod.transOper)
                }
            }
        }

        // Terminal modulator
        repeat(5) { writeInt16LE(raf, 0) }

        updateChunkSize(raf, chunkStart)
    }

    private fun writeIgen(
        raf: RandomAccessFile,
        instruments: List<com.Atom2Universe.app.sf2creator.data.PatchedInstrument>,
        parseResult: Sf2ParseResult,
        samplePatches: Map<Int, List<Sf2PatchEntity>>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("igen")
        raf.writeInt(0)

        for (inst in instruments) {
            val origInst = inst.originalInstrument

            // Global zone generators (with patches applied)
            for ((genId, value) in inst.modifiedGenerators) {
                writeInt16LE(raf, genId)
                writeInt16LE(raf, value)
            }

            // Sample zone generators
            for (zone in origInst.zones) {
                if (zone.sampleIndex in deletedSamples) continue

                // Apply sample patches if any
                val patches = samplePatches[zone.sampleIndex] ?: emptyList()
                val patchedGens = zone.generators.toMutableMap()
                for (patch in patches) {
                    if (patch.patchType == Sf2PatchEntity.PATCH_MODIFY_GEN) {
                        val data = org.json.JSONObject(patch.patchData)
                        val genId = data.getInt("genId")
                        val value = data.getInt("value")
                        patchedGens[genId] = value
                    }
                }

                for ((genId, value) in patchedGens) {
                    writeInt16LE(raf, genId)
                    writeInt16LE(raf, value)
                }

                // Sample reference (gen 53)
                writeInt16LE(raf, 53) // GEN_SAMPLE_ID
                writeInt16LE(raf, zone.sampleIndex)
            }
        }

        // Terminal generator
        writeInt16LE(raf, 0)
        writeInt16LE(raf, 0)

        updateChunkSize(raf, chunkStart)
    }

    private fun writeShdr(
        raf: RandomAccessFile,
        samples: List<Sf2ParsedSample>,
        deletedSamples: Set<Int>
    ) {
        val chunkStart = raf.filePointer
        raf.writeBytes("shdr")
        raf.writeInt(0)

        var sampleDataOffset = 0L
        for (sample in samples) {
            if (sample.index in deletedSamples) continue

            writePaddedString(raf, sample.name, 20)
            writeInt32LE(raf, sample.start.toInt())
            writeInt32LE(raf, sample.end.toInt())
            writeInt32LE(raf, sample.loopStart.toInt())
            writeInt32LE(raf, sample.loopEnd.toInt())
            writeInt32LE(raf, sample.sampleRate)
            raf.writeByte(sample.originalPitch)
            raf.writeByte(sample.pitchCorrection)
            writeInt16LE(raf, 0) // sampleLink
            writeInt16LE(raf, sample.sampleType)
        }

        // Terminal sample
        writePaddedString(raf, "EOS", 20)
        repeat(9) { writeInt32LE(raf, 0) } // 9 * 4 = 36 bytes remaining
        writeInt16LE(raf, 0) // sampleType

        updateChunkSize(raf, chunkStart)
    }

    // ==================== Utility Methods ====================

    private fun writeInt16LE(raf: RandomAccessFile, value: Int) {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(value.toShort())
        raf.write(buffer.array())
    }

    private fun writeInt32LE(raf: RandomAccessFile, value: Int) {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value)
        raf.write(buffer.array())
    }

    private fun writePaddedString(raf: RandomAccessFile, str: String, length: Int) {
        val bytes = str.take(length - 1).toByteArray(Charsets.US_ASCII)
        raf.write(bytes)
        repeat(length - bytes.size) { raf.writeByte(0) }
    }

    private fun updateChunkSize(raf: RandomAccessFile, chunkStart: Long) {
        val chunkEnd = raf.filePointer
        val size = (chunkEnd - chunkStart - 8).toInt()
        raf.seek(chunkStart + 4)
        writeInt32LE(raf, size)
        raf.seek(chunkEnd)
        // Pad to even if necessary
        if (size % 2 != 0) {
            raf.writeByte(0)
        }
    }
}
