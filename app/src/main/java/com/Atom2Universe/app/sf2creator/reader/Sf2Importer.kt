package com.Atom2Universe.app.sf2creator.reader

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.ChunkInfo
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Handles importing SF2 files into projects.
 * Supports importing complete presets or individual samples.
 */
class Sf2Importer(private val context: Context) {

    companion object {
        private const val TAG = "Sf2Importer"
    }

    private val repository = Sf2ProjectRepository(context)
    private val reader = Sf2Reader()

    // Directory for storing original SF2 source files
    private val sf2SourcesDir: File
        get() = File(context.filesDir, "sf2_sources").also { it.mkdirs() }

    // Directory for storing sample index mappings (separate from SQLite to avoid OOM)
    private val sf2MappingsDir: File
        get() = File(context.filesDir, "sf2_mappings").also { it.mkdirs() }

    /**
     * Copy the source SF2 file to app storage for hybrid passthrough support.
     * @param sourceFile Original SF2 file
     * @param projectId Target project ID
     * @return Path to the copied file, or null if copy failed
     */
    private fun copySourceFile(sourceFile: File, projectId: Long): String? {
        try {
            val destFile = File(sf2SourcesDir, "${projectId}.sf2")
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Copied source SF2 to ${destFile.absolutePath}")
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy source SF2 file", e)
            return null
        }
    }

    /**
     * Store source metadata for a project after import.
     * This enables hybrid passthrough export.
     */
    private suspend fun storeSourceMetadata(
        projectId: Long,
        sourceFile: File,
        copiedFilePath: String,
        chunks: Map<String, ChunkScanInfo>,
        samples: List<Sf2ParsedSample>
    ) {
        // Compute file hash
        val fileHash = reader.computeFileHash(sourceFile)

        // Convert chunk scan info to ChunkInfo for storage
        val chunkRegistry = chunks.mapValues { (_, scanInfo) ->
            ChunkInfo(
                chunkId = scanInfo.chunkId,
                offset = scanInfo.offset,
                size = scanInfo.size,
                contentHash = scanInfo.contentHash,
                isModified = false
            )
        }

        // Serialize chunk registry to JSON using org.json
        val chunkRegistryJson = try {
            val jsonObj = JSONObject()
            chunkRegistry.forEach { (key, info) ->
                val chunkObj = JSONObject().apply {
                    put("chunkId", info.chunkId)
                    put("offset", info.offset)
                    put("size", info.size)
                    put("contentHash", info.contentHash)
                    put("isModified", info.isModified)
                }
                jsonObj.put(key, chunkObj)
            }
            jsonObj.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize chunk registry", e)
            "{}"
        }

        // Build sample index mapping for hybrid passthrough export
        // Maps sample name (truncated to 20 chars like SF2 spec) to original sample header info
        // IMPORTANT: Store in a file instead of SQLite to avoid OOM for large SF2 files
        val sampleMappingJson = try {
            val mappingObj = JSONObject()
            samples.forEachIndexed { index, sample ->
                val sampleInfo = JSONObject().apply {
                    put("name", sample.name)
                    put("originalIndex", index)
                    put("startOffset", sample.start)
                    put("endOffset", sample.end)
                    put("loopStart", sample.loopStart)
                    put("loopEnd", sample.loopEnd)
                    put("sampleRate", sample.sampleRate)
                    put("originalPitch", sample.originalPitch)
                    put("pitchCorrection", sample.pitchCorrection)
                    put("sampleType", sample.sampleType)
                }
                // Use sample name as key (truncated like SF2 spec)
                // Also add by index for direct lookup
                mappingObj.put(sample.name.take(20), sampleInfo)
                mappingObj.put("_idx_$index", sampleInfo)
            }
            mappingObj.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize sample mapping", e)
            "{}"
        }

        // Save sample mapping to a separate file (not in SQLite) to avoid OOM
        val mappingFile = File(sf2MappingsDir, "${projectId}.json")
        try {
            mappingFile.writeText(sampleMappingJson)
            Log.d(TAG, "Saved sample mapping to file: ${mappingFile.absolutePath} (${sampleMappingJson.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sample mapping file", e)
        }

        // Create and save metadata entity (sampleIndexMapping is empty - stored in file instead)
        val metadata = Sf2SourceMetadataEntity(
            projectId = projectId,
            sourceFilePath = copiedFilePath,
            sourceFileHash = fileHash,
            chunkRegistry = chunkRegistryJson,
            sampleIndexMapping = "{}", // Empty - mapping is in sf2_mappings/{projectId}.json
            importedAt = System.currentTimeMillis()
        )

        repository.saveSourceMetadata(metadata)
        Log.d(TAG, "Stored source metadata for project $projectId: ${chunks.size} chunks, ${samples.size} samples mapped (mapping in file)")
    }

    /**
     * Parse an SF2 file to get its contents for preview.
     * This is fast and doesn't load audio data.
     *
     * @param file The SF2 file to parse
     * @return Parsed structure or null if parsing fails
     */
    suspend fun parseFile(file: File): Sf2ParseResult? = withContext(Dispatchers.IO) {
        reader.parse(file)
    }

    /**
     * Import selected presets from an SF2 file into a project.
     *
     * @param parseResult The parsed SF2 data
     * @param presetIndices Indices of presets to import
     * @param targetProjectId The project to import into
     * @param progressCallback Called with progress (0.0 to 1.0)
     * @return Number of presets successfully imported
     */
    suspend fun importPresets(
        parseResult: Sf2ParseResult,
        presetIndices: List<Int>,
        targetProjectId: Long,
        progressCallback: ((Float) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        // Re-initialize reader with the file from parseResult if needed
        // This handles the case where a different Importer instance is used for import vs parsing
        val sf2File = File(parseResult.filePath)
        if (!sf2File.exists()) {
            Log.e(TAG, "SF2 file not found: ${parseResult.filePath}")
            return@withContext 0
        }

        // Parse the file - must succeed to extract samples
        val parseSuccess = reader.parse(sf2File)
        if (parseSuccess == null) {
            Log.e(TAG, "Failed to parse SF2 file: ${parseResult.filePath}")
            return@withContext 0
        }

        // Hybrid passthrough: scan chunks and copy source file
        // IMPORTANT: Only store source metadata if this is the FIRST import into the project
        // If the project already has samples from a previous import, hybrid passthrough won't work
        val existingMetadata = repository.getSourceMetadata(targetProjectId)
        val projectHasExistingSamples = existingMetadata != null

        // ALWAYS copy the source file first for patch-based import
        // Samples will reference this COPIED file, not the original
        var copiedSourceFilePath: String? = null

        if (projectHasExistingSamples) {
            // Project already has samples from another SF2 - delete source metadata
            // This forces standard export which regenerates everything correctly
            Log.d(TAG, "Project already has samples from another source - disabling hybrid export")
            repository.deleteSourceMetadata(targetProjectId)
        } else {
            // First import - copy source and store metadata for patch-based export
            val chunks = reader.scanChunks(sf2File)
            if (chunks != null) {
                copiedSourceFilePath = copySourceFile(sf2File, targetProjectId)
                if (copiedSourceFilePath != null) {
                    // Pass all samples for mapping (from parseSuccess, not parseResult)
                    storeSourceMetadata(targetProjectId, sf2File, copiedSourceFilePath, chunks, parseSuccess.samples)
                    Log.d(TAG, "Source file copied to: $copiedSourceFilePath")
                }
            }
        }

        var importedCount = 0
        val totalPresets = presetIndices.size
        var processedPresets = 0

        // Track imported SF2 instruments by their SF2 index
        // Key: SF2 instrument index, Value: created Sf2InstrumentEntity ID
        // This allows multiple preset zones to share the same instrument
        val importedInstruments = mutableMapOf<Int, Long>()

        // Track imported preset zones to avoid duplicates
        // Key: hash of (presetIndex, zoneIndex), Value: imported preset zone ID
        val importedPresetZones = mutableMapOf<Int, Long>()

        // Patch-based import: samples reference the COPIED source file
        // If copy failed or project has existing samples, fall back to WAV extraction
        val usePatchBasedImport = copiedSourceFilePath != null

        for (presetIndex in presetIndices) {
            val preset = parseResult.presets.getOrNull(presetIndex) ?: continue

            // Skip presets with no zones
            if (preset.zones.isEmpty()) {
                Log.w(TAG, "Preset ${preset.name} has no zones, skipping")
                continue
            }

            Log.d(TAG, "Importing preset: ${preset.name}, zones: ${preset.zones.size}, total samples: ${preset.getSampleCount()}")

            try {
                // Get or create a program for this SF2 preset (SF2 preset = MIDI program)
                // This will reuse an existing empty program with the same number if available
                val programId = repository.getOrCreateProgram(
                    projectId = targetProjectId,
                    name = preset.name.take(20),
                    programNumber = preset.programNumber,
                    bankNumber = preset.bankNumber
                )
                Log.d(TAG, "Using program ID: $programId for preset ${preset.name} (program ${preset.programNumber}, bank ${preset.bankNumber})")

                // Store global preset zone parameters (PGEN global) if present
                // These apply to ALL zones in this preset and are written to a global zone at export
                if (preset.globalGenerators.isNotEmpty()) {
                    val globalParams = Sf2ProjectRepository.ProgramGlobalParams(
                        attenuation = preset.globalGenerators[48] ?: 0,   // GEN_INITIAL_ATTENUATION
                        coarseTune = preset.globalGenerators[51] ?: 0,    // GEN_COARSE_TUNE
                        fineTune = preset.globalGenerators[52] ?: 0,      // GEN_FINE_TUNE
                        filterFc = preset.globalGenerators[8] ?: 0,       // GEN_INITIAL_FILTER_FC
                        filterQ = preset.globalGenerators[9] ?: 0,        // GEN_INITIAL_FILTER_Q
                        chorusSend = preset.globalGenerators[15] ?: 0,    // GEN_CHORUS_EFFECTS_SEND
                        reverbSend = preset.globalGenerators[16] ?: 0,    // GEN_REVERB_EFFECTS_SEND
                        pan = preset.globalGenerators[17] ?: 0,           // GEN_PAN
                        volEnvDelay = preset.globalGenerators[33] ?: 0,   // GEN_DELAY_VOL_ENV
                        volEnvAttack = preset.globalGenerators[34] ?: 0,  // GEN_ATTACK_VOL_ENV
                        volEnvHold = preset.globalGenerators[35] ?: 0,    // GEN_HOLD_VOL_ENV
                        volEnvDecay = preset.globalGenerators[36] ?: 0,   // GEN_DECAY_VOL_ENV
                        volEnvSustain = preset.globalGenerators[37] ?: 0, // GEN_SUSTAIN_VOL_ENV
                        volEnvRelease = preset.globalGenerators[38] ?: 0, // GEN_RELEASE_VOL_ENV
                        modEnvDelay = preset.globalGenerators[25] ?: 0,   // GEN_DELAY_MOD_ENV
                        modEnvAttack = preset.globalGenerators[26] ?: 0,  // GEN_ATTACK_MOD_ENV
                        modEnvHold = preset.globalGenerators[27] ?: 0,    // GEN_HOLD_MOD_ENV
                        modEnvDecay = preset.globalGenerators[28] ?: 0,   // GEN_DECAY_MOD_ENV
                        modEnvSustain = preset.globalGenerators[29] ?: 0, // GEN_SUSTAIN_MOD_ENV
                        modEnvRelease = preset.globalGenerators[30] ?: 0, // GEN_RELEASE_MOD_ENV
                        modEnvToPitch = preset.globalGenerators[7] ?: 0,  // GEN_MOD_ENV_TO_PITCH
                        modEnvToFilterFc = preset.globalGenerators[11] ?: 0, // GEN_MOD_ENV_TO_FILTER_FC
                        vibLfoDelay = preset.globalGenerators[23] ?: 0,   // GEN_DELAY_VIB_LFO
                        vibLfoFreq = preset.globalGenerators[24] ?: 0,    // GEN_FREQ_VIB_LFO
                        vibLfoToPitch = preset.globalGenerators[6] ?: 0,  // GEN_VIB_LFO_TO_PITCH
                        modLfoDelay = preset.globalGenerators[21] ?: 0,   // GEN_DELAY_MOD_LFO
                        modLfoFreq = preset.globalGenerators[22] ?: 0,    // GEN_FREQ_MOD_LFO
                        modLfoToPitch = preset.globalGenerators[5] ?: 0,  // GEN_MOD_LFO_TO_PITCH
                        modLfoToFilterFc = preset.globalGenerators[10] ?: 0, // GEN_MOD_LFO_TO_FILTER_FC
                        modLfoToVolume = preset.globalGenerators[13] ?: 0, // GEN_MOD_LFO_TO_VOLUME
                        keyToModEnvHold = preset.globalGenerators[31] ?: 0, // GEN_KEYNUM_TO_MOD_ENV_HOLD
                        keyToModEnvDecay = preset.globalGenerators[32] ?: 0, // GEN_KEYNUM_TO_MOD_ENV_DECAY
                        keyToVolEnvHold = preset.globalGenerators[39] ?: 0, // GEN_KEYNUM_TO_VOL_ENV_HOLD
                        keyToVolEnvDecay = preset.globalGenerators[40] ?: 0, // GEN_KEYNUM_TO_VOL_ENV_DECAY
                        scaleTuning = preset.globalGenerators[56] ?: 0,   // GEN_SCALE_TUNING
                        exclusiveClass = preset.globalGenerators[57] ?: 0 // GEN_EXCLUSIVE_CLASS
                    )
                    repository.updateProgramGlobalParams(programId, globalParams)
                    Log.d(TAG, "  Stored ${preset.globalGenerators.size} global PGEN parameters for program $programId")
                }

                // Store global preset zone modulators (PMOD global) if present
                // These modulators apply to ALL zones in this preset
                if (preset.globalModulators.isNotEmpty()) {
                    repository.saveProgramLevelModulators(programId, preset.globalModulators)
                    Log.d(TAG, "  Stored ${preset.globalModulators.size} global PMOD modulators for program $programId")
                }

                // Import each preset zone (each zone references an instrument)
                val totalZones = preset.zones.size
                for ((zoneIndex, presetZone) in preset.zones.withIndex()) {
                    val sf2Instrument = presetZone.instrument
                    if (sf2Instrument == null) {
                        Log.w(TAG, "  Zone $zoneIndex has no instrument, skipping")
                        continue
                    }

                    // Check if this preset zone was already imported
                    val zoneKey = presetIndex * 1000 + zoneIndex
                    if (importedPresetZones.containsKey(zoneKey)) {
                        Log.d(TAG, "  Skipping zone $zoneIndex - already imported in this session")
                        continue
                    }

                    Log.d(TAG, "  Importing zone ${zoneIndex + 1}/$totalZones: instrument ${sf2Instrument.name} (index ${sf2Instrument.index}), keyRange: ${presetZone.keyRangeLow}-${presetZone.keyRangeHigh}, samples: ${sf2Instrument.zones.size}")

                    // Get or create the instrument entity
                    // Multiple preset zones can share the same instrument
                    val instrumentId = importedInstruments.getOrPut(sf2Instrument.index) {
                        // Create new instrument entity
                        val newInstrumentId = repository.createInstrument(
                            projectId = targetProjectId,
                            name = sf2Instrument.name.take(20).ifEmpty { "${preset.name}_inst_${zoneIndex + 1}".take(20) }
                        )
                        Log.d(TAG, "  Created instrument ID: $newInstrumentId for SF2 instrument ${sf2Instrument.index}")

                        // Save instrument global zone parameters (IGEN global)
                        if (sf2Instrument.globalGenerators.isNotEmpty()) {
                            repository.updateInstrumentGlobalParameters(newInstrumentId, sf2Instrument.globalGenerators, fromImport = true)
                            Log.d(TAG, "  Saved ${sf2Instrument.globalGenerators.size} instrument global parameters to instrument $newInstrumentId")
                        }

                        // Save instrument-level modulators (IMOD global zone)
                        if (sf2Instrument.globalModulators.isNotEmpty()) {
                            repository.saveInstrumentLevelModulators(newInstrumentId, sf2Instrument.globalModulators)
                            Log.d(TAG, "  Stored ${sf2Instrument.globalModulators.size} global IMOD modulators for instrument $newInstrumentId")
                        }

                        // Import each sample/zone for this instrument
                        for ((sampleZoneIndex, zone) in sf2Instrument.zones.withIndex()) {
                            val sample = parseResult.samples.getOrNull(zone.sampleIndex) ?: continue

                            // Determine root note
                            // SF2 spec: Check zone first, then instrument global zone, then sample header
                            val rootNote = zone.getRootKey()
                                ?: sf2Instrument.globalGenerators[Sf2Constants.GEN_OVERRIDING_ROOT_KEY]
                                ?: sample.originalPitch

                            val sampleCount = (sample.end - sample.start).toInt()
                            if (sampleCount <= 0) {
                                Log.w(TAG, "    Invalid sample size: ${sample.name}, count: $sampleCount")
                                continue
                            }

                            // Calculate loop points relative to sample start
                            // SF2 loopEnd is exclusive (first sample after the loop)
                            // Internal convention is inclusive (last sample of the loop)
                            val loopStart = (sample.loopStart - sample.start).toInt().coerceAtLeast(0)
                            val loopEnd = (sample.loopEnd - sample.start - 1).toInt().coerceIn(0, sampleCount - 1)

                            if (usePatchBasedImport) {
                                // PATCH-BASED: Store reference to COPIED source file
                                val sourceSmplOffset = reader.getSampleByteOffset(sample)
                                val sourceSampleSize = reader.getSampleByteSize(sample)

                                repository.addSampleReferenceToInstrument(
                                    instrumentId = newInstrumentId,
                                    name = sample.name.take(20),
                                    sourceFilePath = copiedSourceFilePath,
                                    sourceSmplOffset = sourceSmplOffset,
                                    sourceSampleSize = sourceSampleSize,
                                    sampleRate = sample.sampleRate,
                                    sampleCount = sampleCount,
                                    rootNote = rootNote,
                                    keyRangeStart = zone.keyRangeLow,
                                    keyRangeEnd = zone.keyRangeHigh,
                                    loopStart = loopStart,
                                    loopEnd = loopEnd,
                                    hasLoop = zone.hasLoop() && sample.hasLoop(),
                                    sampleModes = zone.getSampleModes(),
                                    attenuation = zone.getAttenuation(),
                                    fineTuneCents = zone.getFineTune(),
                                    volEnvDelay = zone.getVolEnvDelay(),
                                    volEnvAttack = zone.getAttack(),
                                    volEnvHold = zone.getVolEnvHold(),
                                    volEnvDecay = zone.getDecay(),
                                    volEnvSustain = zone.getSustain(),
                                    volEnvRelease = zone.getRelease(),
                                    filterFc = zone.getFilterCutoff(),
                                    filterQ = zone.getFilterResonance(),
                                    chorusSend = zone.getChorusSend(),
                                    reverbSend = zone.getReverbSend(),
                                    pan = zone.getPan(),
                                    velRangeStart = zone.getVelRangeLow(),
                                    velRangeEnd = zone.getVelRangeHigh(),
                                    coarseTune = zone.getCoarseTune(),
                                    scaleTuning = zone.getScaleTuning(),
                                    modEnvDelay = zone.getModEnvDelay(),
                                    modEnvAttack = zone.getModEnvAttack(),
                                    modEnvHold = zone.getModEnvHold(),
                                    modEnvDecay = zone.getModEnvDecay(),
                                    modEnvSustain = zone.getModEnvSustain(),
                                    modEnvRelease = zone.getModEnvRelease(),
                                    modEnvToPitch = zone.getModEnvToPitch(),
                                    modEnvToFilterFc = zone.getModEnvToFilterFc(),
                                    vibLfoDelay = zone.getVibLfoDelay(),
                                    vibLfoFreq = zone.getVibLfoFreq(),
                                    vibLfoToPitch = zone.getVibLfoToPitch(),
                                    modLfoDelay = zone.getModLfoDelay(),
                                    modLfoFreq = zone.getModLfoFreq(),
                                    modLfoToPitch = zone.getModLfoToPitch(),
                                    modLfoToFilterFc = zone.getModLfoToFilterFc(),
                                    modLfoToVolume = zone.getModLfoToVolume(),
                                    exclusiveClass = zone.getExclusiveClass(),
                                    keyToVolEnvHold = zone.getKeyToVolEnvHold(),
                                    keyToVolEnvDecay = zone.getKeyToVolEnvDecay(),
                                    keyToModEnvHold = zone.getKeyToModEnvHold(),
                                    keyToModEnvDecay = zone.getKeyToModEnvDecay(),
                                    fixedKey = zone.getFixedKey() ?: -1,
                                    fixedVelocity = zone.getFixedVelocity() ?: -1,
                                    pitchCorrection = sample.pitchCorrection,
                                    modulators = zone.modulators
                                )
                                Log.d(TAG, "    Added sample reference: ${sample.name}")
                            } else {
                                // FALLBACK: Extract audio to WAV file (original method)
                                val audioData = reader.extractSampleAudio(sample)
                                if (audioData == null || audioData.isEmpty()) {
                                    Log.w(TAG, "    Failed to extract audio for sample: ${sample.name}")
                                    continue
                                }

                                repository.addSampleToInstrument(
                                    instrumentId = newInstrumentId,
                                    name = sample.name.take(20),
                                    samples = audioData,
                                    sampleRate = sample.sampleRate,
                                    rootNote = rootNote,
                                    keyRangeStart = zone.keyRangeLow,
                                    keyRangeEnd = zone.keyRangeHigh,
                                    loopStart = loopStart,
                                    loopEnd = loopEnd.coerceIn(0, audioData.size - 1),
                                    hasLoop = zone.hasLoop() && sample.hasLoop(),
                                    sampleModes = zone.getSampleModes(),
                                    attenuation = zone.getAttenuation(),
                                    fineTuneCents = zone.getFineTune(),
                                    volEnvDelay = zone.getVolEnvDelay(),
                                    volEnvAttack = zone.getAttack(),
                                    volEnvHold = zone.getVolEnvHold(),
                                    volEnvDecay = zone.getDecay(),
                                    volEnvSustain = zone.getSustain(),
                                    volEnvRelease = zone.getRelease(),
                                    filterFc = zone.getFilterCutoff(),
                                    filterQ = zone.getFilterResonance(),
                                    chorusSend = zone.getChorusSend(),
                                    reverbSend = zone.getReverbSend(),
                                    pan = zone.getPan(),
                                    velRangeStart = zone.getVelRangeLow(),
                                    velRangeEnd = zone.getVelRangeHigh(),
                                    coarseTune = zone.getCoarseTune(),
                                    scaleTuning = zone.getScaleTuning(),
                                    modEnvDelay = zone.getModEnvDelay(),
                                    modEnvAttack = zone.getModEnvAttack(),
                                    modEnvHold = zone.getModEnvHold(),
                                    modEnvDecay = zone.getModEnvDecay(),
                                    modEnvSustain = zone.getModEnvSustain(),
                                    modEnvRelease = zone.getModEnvRelease(),
                                    modEnvToPitch = zone.getModEnvToPitch(),
                                    modEnvToFilterFc = zone.getModEnvToFilterFc(),
                                    vibLfoDelay = zone.getVibLfoDelay(),
                                    vibLfoFreq = zone.getVibLfoFreq(),
                                    vibLfoToPitch = zone.getVibLfoToPitch(),
                                    modLfoDelay = zone.getModLfoDelay(),
                                    modLfoFreq = zone.getModLfoFreq(),
                                    modLfoToPitch = zone.getModLfoToPitch(),
                                    modLfoToFilterFc = zone.getModLfoToFilterFc(),
                                    modLfoToVolume = zone.getModLfoToVolume(),
                                    exclusiveClass = zone.getExclusiveClass(),
                                    keyToVolEnvHold = zone.getKeyToVolEnvHold(),
                                    keyToVolEnvDecay = zone.getKeyToVolEnvDecay(),
                                    keyToModEnvHold = zone.getKeyToModEnvHold(),
                                    keyToModEnvDecay = zone.getKeyToModEnvDecay(),
                                    fixedKey = zone.getFixedKey() ?: -1,
                                    fixedVelocity = zone.getFixedVelocity() ?: -1,
                                    pitchCorrection = sample.pitchCorrection,
                                    modulators = zone.modulators
                                )
                                Log.d(TAG, "    Added sample with WAV extraction: ${sample.name}")
                            }
                        }

                        newInstrumentId
                    }

                    // Build preset zone parameters (PGEN)
                    val pgenParams = Sf2ProjectRepository.PresetZoneParams(
                        keyRangeLow = presetZone.keyRangeLow,
                        keyRangeHigh = presetZone.keyRangeHigh,
                        velRangeLow = presetZone.velRangeLow,
                        velRangeHigh = presetZone.velRangeHigh,
                        attenuation = presetZone.getAttenuation(),
                        coarseTune = presetZone.getCoarseTune(),
                        fineTune = presetZone.getFineTune(),
                        filterFc = presetZone.getFilterCutoff(),
                        filterQ = presetZone.getFilterResonance(),
                        chorusSend = presetZone.getChorusSend(),
                        reverbSend = presetZone.getReverbSend(),
                        pan = presetZone.getPan(),
                        volEnvDelay = presetZone.getVolEnvDelay(),
                        volEnvAttack = presetZone.getVolEnvAttack(),
                        volEnvHold = presetZone.getVolEnvHold(),
                        volEnvDecay = presetZone.getVolEnvDecay(),
                        volEnvSustain = presetZone.getVolEnvSustain(),
                        volEnvRelease = presetZone.getVolEnvRelease(),
                        modEnvDelay = presetZone.getModEnvDelay(),
                        modEnvAttack = presetZone.getModEnvAttack(),
                        modEnvHold = presetZone.getModEnvHold(),
                        modEnvDecay = presetZone.getModEnvDecay(),
                        modEnvSustain = presetZone.getModEnvSustain(),
                        modEnvRelease = presetZone.getModEnvRelease(),
                        modEnvToPitch = presetZone.getModEnvToPitch(),
                        modEnvToFilterFc = presetZone.getModEnvToFilterFc(),
                        vibLfoDelay = presetZone.getVibLfoDelay(),
                        vibLfoFreq = presetZone.getVibLfoFreq(),
                        vibLfoToPitch = presetZone.getVibLfoToPitch(),
                        modLfoDelay = presetZone.getModLfoDelay(),
                        modLfoFreq = presetZone.getModLfoFreq(),
                        modLfoToPitch = presetZone.getModLfoToPitch(),
                        modLfoToFilterFc = presetZone.getModLfoToFilterFc(),
                        modLfoToVolume = presetZone.getModLfoToVolume(),
                        keyToModEnvHold = presetZone.getKeyToModEnvHold(),
                        keyToModEnvDecay = presetZone.getKeyToModEnvDecay(),
                        keyToVolEnvHold = presetZone.getKeyToVolEnvHold(),
                        keyToVolEnvDecay = presetZone.getKeyToVolEnvDecay(),
                        scaleTuning = presetZone.getScaleTuning(),
                        exclusiveClass = presetZone.getExclusiveClass()
                    )

                    // Create a preset zone referencing the (possibly shared) instrument
                    val newPresetZoneId = repository.createPresetZoneForProgramWithPgen(
                        projectId = targetProjectId,
                        programId = programId,
                        instrumentId = instrumentId,
                        name = sf2Instrument.name.take(20).ifEmpty { "${preset.name}_${zoneIndex + 1}".take(20) },
                        programNumber = preset.programNumber,
                        bankNumber = preset.bankNumber,
                        pgenParams = pgenParams
                    )
                    Log.d(TAG, "  Created preset zone ID: $newPresetZoneId with PGEN params, referencing instrument $instrumentId")

                    // Mark this preset zone as imported
                    importedPresetZones[zoneKey] = newPresetZoneId

                    // Save preset zone modulators (PMOD) on this preset zone
                    if (presetZone.modulators.isNotEmpty()) {
                        repository.savePresetLevelModulators(newPresetZoneId, presetZone.modulators)
                        Log.d(TAG, "  Saved ${presetZone.modulators.size} preset zone modulators to preset zone $newPresetZoneId")
                    }

                    // Update progress per zone
                    val zoneProgress = (zoneIndex + 1).toFloat() / totalZones
                    val presetProgress = (processedPresets + zoneProgress) / totalPresets
                    progressCallback?.invoke(presetProgress)
                }

                importedCount++
                processedPresets++
                progressCallback?.invoke(processedPresets.toFloat() / totalPresets)

            } catch (e: Exception) {
                Log.e(TAG, "Error importing preset: ${preset.name}", e)
            }
        }

        importedCount
    }

    /**
     * Import a single sample into a specific instrument.
     *
     * @param parseResult The parsed SF2 data
     * @param presetIndex Index of the preset containing the sample
     * @param zoneIndex Index of the zone/sample within the preset's instrument
     * @param targetInstrumentId The instrument to import into
     * @return true if successful
     */
    suspend fun importSample(
        parseResult: Sf2ParseResult,
        presetIndex: Int,
        zoneIndex: Int,
        targetInstrumentId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val preset = parseResult.presets.getOrNull(presetIndex) ?: return@withContext false
        val instrument = preset.instrument ?: return@withContext false
        val zone = instrument.zones.getOrNull(zoneIndex) ?: return@withContext false
        val sample = parseResult.samples.getOrNull(zone.sampleIndex) ?: return@withContext false

        try {
            val audioData = reader.extractSampleAudio(sample) ?: return@withContext false

            // SF2 spec: Check zone first, then instrument global zone, then sample header
            val rootNote = zone.getRootKey()
                ?: instrument.globalGenerators[Sf2Constants.GEN_OVERRIDING_ROOT_KEY]
                ?: sample.originalPitch
            // SF2 loopEnd is exclusive, internal convention is inclusive
            val loopStart = (sample.loopStart - sample.start).toInt().coerceAtLeast(0)
            val loopEnd = (sample.loopEnd - sample.start - 1).toInt().coerceIn(0, audioData.size - 1)

            // SF2 native units - no conversion needed
            repository.addSampleToInstrument(
                instrumentId = targetInstrumentId,
                name = sample.name.take(20),
                samples = audioData,
                sampleRate = sample.sampleRate,
                rootNote = rootNote,
                keyRangeStart = zone.keyRangeLow,
                keyRangeEnd = zone.keyRangeHigh,
                loopStart = loopStart,
                loopEnd = loopEnd,
                hasLoop = zone.hasLoop() && sample.hasLoop(),
                sampleModes = zone.getSampleModes(),
                attenuation = zone.getAttenuation(),
                fineTuneCents = zone.getFineTune(),
                volEnvDelay = zone.getVolEnvDelay(),
                volEnvAttack = zone.getAttack(),
                volEnvHold = zone.getVolEnvHold(),
                volEnvDecay = zone.getDecay(),
                volEnvSustain = zone.getSustain(),
                volEnvRelease = zone.getRelease(),
                filterFc = zone.getFilterCutoff(),
                filterQ = zone.getFilterResonance(),
                chorusSend = zone.getChorusSend(),
                reverbSend = zone.getReverbSend(),
                pan = zone.getPan(),
                // Advanced SF2 parameters
                velRangeStart = zone.getVelRangeLow(),
                velRangeEnd = zone.getVelRangeHigh(),
                coarseTune = zone.getCoarseTune(),
                scaleTuning = zone.getScaleTuning(),
                modEnvDelay = zone.getModEnvDelay(),
                modEnvAttack = zone.getModEnvAttack(),
                modEnvHold = zone.getModEnvHold(),
                modEnvDecay = zone.getModEnvDecay(),
                modEnvSustain = zone.getModEnvSustain(),
                modEnvRelease = zone.getModEnvRelease(),
                modEnvToPitch = zone.getModEnvToPitch(),
                modEnvToFilterFc = zone.getModEnvToFilterFc(),
                vibLfoDelay = zone.getVibLfoDelay(),
                vibLfoFreq = zone.getVibLfoFreq(),
                vibLfoToPitch = zone.getVibLfoToPitch(),
                modLfoDelay = zone.getModLfoDelay(),
                modLfoFreq = zone.getModLfoFreq(),
                modLfoToPitch = zone.getModLfoToPitch(),
                modLfoToFilterFc = zone.getModLfoToFilterFc(),
                modLfoToVolume = zone.getModLfoToVolume(),
                exclusiveClass = zone.getExclusiveClass(),
                // Key-to-envelope scaling
                keyToVolEnvHold = zone.getKeyToVolEnvHold(),
                keyToVolEnvDecay = zone.getKeyToVolEnvDecay(),
                keyToModEnvHold = zone.getKeyToModEnvHold(),
                keyToModEnvDecay = zone.getKeyToModEnvDecay(),
                // Fixed key/velocity
                fixedKey = zone.getFixedKey() ?: -1,
                fixedVelocity = zone.getFixedVelocity() ?: -1,
                // Sample header fields (preserved for lossless export)
                pitchCorrection = sample.pitchCorrection,
                // Modulators
                modulators = zone.modulators
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing sample: ${sample.name}", e)
            false
        }
    }

    // Unit conversions have been removed - all values are now stored in SF2 native units
    // Conversions for UI display should use Sf2UnitConverter instead
}
