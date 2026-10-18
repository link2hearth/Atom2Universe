package com.Atom2Universe.app.sf2creator.reader

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.sf2creator.data.Sf2ImportMapper
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Copy the source SF2 file to app storage: imported samples read their audio from it.
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
     * Store source metadata for a project after import: the copy of the SF2, whose
     * audio and INFO fields the exports reuse.
     */
    private suspend fun storeSourceMetadata(projectId: Long, copiedFilePath: String) {
        repository.saveSourceMetadata(
            Sf2SourceMetadataEntity(
                projectId = projectId,
                sourceFilePath = copiedFilePath,
                importedAt = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Stored source metadata for project $projectId")
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
     * Every zone keeps the generators and modulators of the file (see [Sf2ImportMapper]),
     * so that an export writes back what was imported.
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
        val sf2File = File(parseResult.filePath)
        if (!sf2File.exists()) {
            Log.e(TAG, "SF2 file not found: ${parseResult.filePath}")
            return@withContext 0
        }

        // Parse the file again with this reader: it reads the audio of the samples
        if (reader.parse(sf2File) == null) {
            Log.e(TAG, "Failed to parse SF2 file: ${parseResult.filePath}")
            return@withContext 0
        }

        // The first SF2 imported into a project is kept: its samples are read from that
        // copy (no WAV extraction) and exports reuse its audio and INFO. A project that
        // already holds samples gets WAV copies instead.
        val projectWasEmpty = repository.getSampleCountForProject(targetProjectId) == 0
        val projectHasSource = repository.getSourceMetadata(targetProjectId) != null
        var copiedSourceFilePath: String? = null
        if (projectHasSource) {
            Log.d(TAG, "Project already has samples from another source - extracting to WAV")
            repository.deleteSourceMetadata(targetProjectId)
        } else {
            copiedSourceFilePath = copySourceFile(sf2File, targetProjectId)
            if (copiedSourceFilePath != null) {
                storeSourceMetadata(targetProjectId, copiedSourceFilePath)
            }
        }

        var importedCount = 0
        var failed = false
        val totalPresets = presetIndices.size
        var processedPresets = 0

        // SF2 instrument index -> instrument entity, so preset zones share their instrument
        val importedInstruments = mutableMapOf<Int, Long>()

        for (presetIndex in presetIndices) {
            val preset = parseResult.presets.getOrNull(presetIndex) ?: continue

            if (preset.zones.isEmpty()) {
                Log.w(TAG, "Preset ${preset.name} has no zones, skipping")
                continue
            }

            try {
                // SF2 preset = program; an existing program with the same number is reused
                val existingProgram = repository.findProgramByNumber(targetProjectId, preset.programNumber, preset.bankNumber)
                val programId = repository.getOrCreateProgram(
                    projectId = targetProjectId,
                    name = preset.name.take(20),
                    programNumber = preset.programNumber,
                    bankNumber = preset.bankNumber
                )
                if (existingProgram == null || preset.globalGenerators.isNotEmpty() || preset.globalModulators.isNotEmpty()) {
                    repository.applyImportedProgramGlobals(programId, preset)
                }

                val totalZones = preset.zones.size
                for ((zoneIndex, presetZone) in preset.zones.withIndex()) {
                    val sf2Instrument = presetZone.instrument
                    if (sf2Instrument == null) {
                        Log.w(TAG, "  Zone $zoneIndex has no instrument, skipping")
                        continue
                    }

                    val instrumentId = importedInstruments.getOrPut(sf2Instrument.index) {
                        val newInstrumentId = repository.insertImportedInstrument(
                            Sf2ImportMapper.instrument(
                                projectId = targetProjectId,
                                name = sf2Instrument.name.take(20).ifEmpty { "${preset.name}_inst_${zoneIndex + 1}".take(20) },
                                source = sf2Instrument
                            ),
                            sf2Instrument.globalModulators
                        )

                        for (zone in sf2Instrument.zones) {
                            val sample = parseResult.samples.getOrNull(zone.sampleIndex) ?: continue
                            if (sample.end <= sample.start) {
                                Log.w(TAG, "    Invalid sample size: ${sample.name}")
                                continue
                            }
                            val entity = Sf2ImportMapper.sampleZone(
                                instrumentId = newInstrumentId,
                                name = sample.name.take(20),
                                zone = zone,
                                instrument = sf2Instrument,
                                sample = sample
                            )
                            if (copiedSourceFilePath != null) {
                                // Audio read from the copied source file
                                repository.insertImportedSample(
                                    entity.copy(
                                        sourceFilePath = copiedSourceFilePath,
                                        sourceSmplOffset = reader.getSampleByteOffset(sample),
                                        sourceSampleSize = reader.getSampleByteSize(sample),
                                        isExtracted = false
                                    ),
                                    zone.modulators
                                )
                            } else {
                                val audioData = reader.extractSampleAudio(sample)
                                if (audioData == null || audioData.isEmpty()) {
                                    Log.w(TAG, "    Failed to extract audio for sample: ${sample.name}")
                                    continue
                                }
                                repository.insertImportedSample(entity, zone.modulators, audioData)
                            }
                        }
                        newInstrumentId
                    }

                    repository.insertImportedPresetZone(
                        Sf2ImportMapper.presetZone(
                            projectId = targetProjectId,
                            programId = programId,
                            instrumentId = instrumentId,
                            name = sf2Instrument.name.take(20).ifEmpty { "${preset.name}_${zoneIndex + 1}".take(20) },
                            preset = preset,
                            zone = presetZone
                        ),
                        presetZone.modulators
                    )

                    val zoneProgress = (zoneIndex + 1).toFloat() / totalZones
                    progressCallback?.invoke((processedPresets + zoneProgress) / totalPresets)
                }

                importedCount++
                processedPresets++
                progressCallback?.invoke(processedPresets.toFloat() / totalPresets)

            } catch (e: Exception) {
                failed = true
                Log.e(TAG, "Error importing preset: ${preset.name}", e)
            }
        }

        if (copiedSourceFilePath != null) {
            // A complete import is exported as a copy of the file until something changes
            val complete = !failed && projectWasEmpty &&
                presetIndices.toSet().containsAll(parseResult.presets.indices.toList())
            repository.recordImport(targetProjectId, complete)
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
