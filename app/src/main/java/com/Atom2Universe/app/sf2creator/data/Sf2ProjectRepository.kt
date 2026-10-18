package com.Atom2Universe.app.sf2creator.data

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDao
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ModulatorEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.ModificationFlags
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedModulator
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPreset
import com.Atom2Universe.app.sf2creator.reader.Sf2Reader
import com.Atom2Universe.app.sf2creator.util.WavUtils
import com.Atom2Universe.app.sf2creator.writer.Sf2Document
import com.Atom2Universe.app.sf2creator.writer.Sf2DocumentWriter
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for SF2 Creator projects.
 * Handles database operations and audio file management.
 */
class Sf2ProjectRepository(private val context: Context) {

    companion object {
        private const val TAG = "Sf2ProjectRepository"
    }

    private val database = Sf2ProjectDatabase.getInstance(context)
    private val dao: Sf2ProjectDao = database.projectDao()

    // Directory for storing sample audio files
    private val samplesDir: File
        get() = File(context.filesDir, "sf2_samples").also { it.mkdirs() }

    // Directory for storing sample index mappings (separate from SQLite to avoid OOM)
    private val mappingsDir: File
        get() = File(context.filesDir, "sf2_mappings").also { it.mkdirs() }

    // ==================== Projects ====================

    fun getAllProjectsFlow(): Flow<List<Sf2ProjectEntity>> = dao.getAllProjectsFlow()

    suspend fun getAllProjects(): List<Sf2ProjectEntity> = dao.getAllProjects()

    suspend fun getProjectById(projectId: Long): Sf2ProjectEntity? = dao.getProjectById(projectId)

    suspend fun createProject(name: String): Long {
        return dao.createProjectWithDefaultPreset(name)
    }

    /**
     * Create an empty project with no default entities.
     * Used for SF2 imports where we want an exact match with the source file.
     */
    suspend fun createEmptyProject(name: String): Long {
        return dao.createEmptyProject(name)
    }

    suspend fun updateProject(project: Sf2ProjectEntity) {
        dao.updateProject(project)
    }

    suspend fun deleteProject(projectId: Long) {
        // Delete audio files for all samples in the project
        val samples = dao.getAllSamplesForProject(projectId)
        samples.forEach { sample ->
            if (sample.audioFilePath.isNotEmpty()) File(sample.audioFilePath).delete()
        }
        // Delete the project directory
        File(samplesDir, projectId.toString()).deleteRecursively()

        val sourcePaths = (samples.mapNotNull { it.sourceFilePath } +
            listOfNotNull(dao.getSourceMetadata(projectId)?.sourceFilePath)).toSet()

        // Delete the sample index mapping file (stored separately from SQLite)
        File(mappingsDir, "${projectId}.json").delete()

        // Delete from database (cascades to presets, samples, and source metadata)
        dao.deleteProjectById(projectId)

        // Imported SF2 files are kept while samples pasted or duplicated into other projects read them
        for (path in sourcePaths) {
            if (dao.countSamplesReadingSource(path) == 0 && dao.countProjectsWithSource(path) == 0) {
                File(path).delete()
            }
        }
    }

    /** Key ranges and program counts of every project, for the gallery. */
    suspend fun getProjectOverviews(): Map<Long, Sf2ProjectOverview> {
        val spans = dao.getAllKeySpans().groupBy { it.projectId }
        val programs = dao.getProgramCounts().associate { it.projectId to it.count }
        return (spans.keys + programs.keys).associateWith { id ->
            val keys = BooleanArray(128)
            spans[id].orEmpty().forEach { span ->
                for (k in span.low.coerceIn(0, 127)..span.high.coerceIn(0, 127)) keys[k] = true
            }
            Sf2ProjectOverview(programs[id] ?: 0, spans[id]?.size ?: 0, keys)
        }
    }

    suspend fun getSampleCountForProject(projectId: Long): Int {
        return dao.getSampleCountForProject(projectId)
    }

    /**
     * Check if a note already exists in a project.
     * @return The existing sample if found, null otherwise.
     */
    suspend fun findSampleByNoteInProject(projectId: Long, note: Int): Sf2SampleEntity? {
        return dao.findSampleByNoteInProject(projectId, note)
    }

    /**
     * Find samples in a project whose key ranges overlap with the given range.
     * @return List of overlapping samples (empty if none).
     */
    suspend fun findOverlappingSamplesInProject(
        projectId: Long,
        rangeStart: Int,
        rangeEnd: Int
    ): List<Sf2SampleEntity> {
        return dao.findOverlappingSamplesInProject(projectId, rangeStart, rangeEnd)
    }

    // ==================== Programs ====================

    suspend fun getProgramsForProject(projectId: Long): List<Sf2ProgramEntity> {
        return dao.getProgramsForProject(projectId)
    }

    fun getProgramsForProjectFlow(projectId: Long): Flow<List<Sf2ProgramEntity>> {
        return dao.getProgramsForProjectFlow(projectId)
    }

    suspend fun getProgramById(programId: Long): Sf2ProgramEntity? {
        return dao.getProgramById(programId)
    }

    /**
     * Create a new program for a project.
     * @return The ID of the created program
     */
    suspend fun createProgram(
        projectId: Long,
        name: String,
        programNumber: Int,
        bankNumber: Int = 0
    ): Long {
        val program = Sf2ProgramEntity(
            projectId = projectId,
            name = name,
            programNumber = programNumber,
            bankNumber = bankNumber
        )
        return dao.insertProgram(program)
    }

    /**
     * Update a program.
     */
    suspend fun updateProgram(program: Sf2ProgramEntity) {
        dao.updateProgram(program)
    }

    /**
     * Delete a program and all its preset zones, instruments, and audio files.
     * @return true if deleted
     */
    suspend fun deleteProgram(programId: Long): Boolean = withContext(Dispatchers.IO) {
        val program = dao.getProgramById(programId) ?: return@withContext false

        // Mark ALL chunks as modified BEFORE deleting (for hybrid export detection)
        // Deleting a program removes its preset zones and potentially orphans instruments/samples
        markChunksModified(program.projectId, listOf(
            "phdr", "pbag", "pgen", "pmod",  // Preset chunks
            "inst", "ibag", "igen", "imod",  // Instrument chunks
            "smpl", "shdr"                   // Sample chunks
        ))

        // Get all preset zones for this program BEFORE deletion
        val presetZones = dao.getPresetZonesForProgram(programId)
        val allPresets = dao.getPresetsForProject(program.projectId)

        // Find instruments that will become orphaned after deleting these preset zones
        val instrumentsToDelete = mutableSetOf<Long>()
        for (preset in presetZones) {
            val instrumentId = preset.instrumentId
            // Check if any OTHER preset (not in this program) uses this instrument
            val otherPresetsUsingInstrument = allPresets.filter {
                it.programId != programId && it.instrumentId == instrumentId
            }
            if (otherPresetsUsingInstrument.isEmpty()) {
                instrumentsToDelete.add(instrumentId)
            }
        }

        // Delete preset zones for this program (FK is SET_NULL, so we delete manually)
        for (preset in presetZones) {
            dao.deletePresetById(preset.id)
        }

        // Delete orphaned instruments and their audio files
        for (instrumentId in instrumentsToDelete) {
            val samples = dao.getSamplesForInstrument(instrumentId)
            samples.forEach { sample ->
                File(sample.audioFilePath).delete()
            }
            dao.deleteInstrumentById(instrumentId)
        }

        // Finally delete the program itself
        dao.deleteProgram(program)
        dao.updateProjectModifiedAt(program.projectId)
        true
    }

    /**
     * Get preset zones for a program.
     */
    suspend fun getPresetZonesForProgram(programId: Long): List<Sf2PresetEntity> {
        return dao.getPresetZonesForProgram(programId)
    }

    fun getPresetZonesForProgramFlow(programId: Long): Flow<List<Sf2PresetEntity>> {
        return dao.getPresetZonesForProgramFlow(programId)
    }

    /**
     * Get the next available program number for a project.
     * Returns null if all program numbers (0-127) are already used.
     */
    suspend fun getNextAvailableProgramNumber(projectId: Long): Int? {
        val programs = dao.getProgramsForProject(projectId)
        val usedNumbers = programs.map { it.programNumber }.toSet()
        return (0..127).firstOrNull { it !in usedNumbers }
    }

    /**
     * Find a program by its MIDI program number and bank number.
     */
    suspend fun findProgramByNumber(projectId: Long, programNumber: Int, bankNumber: Int): Sf2ProgramEntity? {
        return dao.findProgramByNumber(projectId, programNumber, bankNumber)
    }

    /**
     * Get or create a program for a given program/bank number.
     * If a program with the same number exists, it will be reused (and empty instruments removed).
     * Otherwise a new program is created.
     */
    suspend fun getOrCreateProgram(
        projectId: Long,
        name: String,
        programNumber: Int,
        bankNumber: Int
    ): Long {
        val existingProgram = dao.findProgramByNumber(projectId, programNumber, bankNumber)

        if (existingProgram != null) {
            // Remove any empty preset zones (pointing to instruments with no samples) from this program
            val presetZones = dao.getPresetZonesForProgram(existingProgram.id)
            for (presetZone in presetZones) {
                val sampleCount = dao.getSampleCountForInstrument(presetZone.instrumentId)
                if (sampleCount == 0) {
                    dao.deletePresetById(presetZone.id)
                }
            }

            // Update the program name to the imported preset name
            dao.updateProgram(existingProgram.copy(name = name))
            return existingProgram.id
        }

        // Create new program
        return createProgram(projectId, name, programNumber, bankNumber)
    }

    // ==================== Instruments ====================

    suspend fun getInstrumentsForProject(projectId: Long): List<Sf2InstrumentEntity> {
        return dao.getInstrumentsForProject(projectId)
    }

    fun getInstrumentsForProjectFlow(projectId: Long): Flow<List<Sf2InstrumentEntity>> {
        return dao.getInstrumentsForProjectFlow(projectId)
    }

    suspend fun getInstrumentById(instrumentId: Long): Sf2InstrumentEntity? {
        return dao.getInstrumentById(instrumentId)
    }

    /**
     * Create a new instrument for a project.
     * @return The ID of the created instrument
     */
    suspend fun createInstrument(projectId: Long, name: String): Long {
        val instrument = Sf2InstrumentEntity(
            projectId = projectId,
            name = name
        )
        val instrumentId = dao.insertInstrument(instrument)
        dao.updateProjectModifiedAt(projectId)
        return instrumentId
    }

    /**
     * Update an instrument.
     */
    suspend fun updateInstrument(instrument: Sf2InstrumentEntity) {
        dao.updateInstrument(instrument)
        // Mark instrument as modified for hybrid export detection
        markInstrumentModified(instrument.id, ModificationFlags.MOD_FLAG_PARAMS)
        dao.updateProjectModifiedAt(instrument.projectId)
    }

    /**
     * Delete an instrument and all its samples.
     * @return true if deleted, false if not found
     */
    suspend fun deleteInstrument(instrumentId: Long): Boolean = withContext(Dispatchers.IO) {
        val instrument = dao.getInstrumentById(instrumentId) ?: return@withContext false

        // Mark chunks as modified BEFORE deleting (for hybrid export detection)
        // Deleting an instrument affects both instrument and sample chunks
        markChunksModified(instrument.projectId, listOf("smpl", "shdr", "inst", "ibag", "igen", "imod"))

        // Delete audio files for all samples in this instrument
        val samples = dao.getSamplesForInstrument(instrumentId)
        samples.forEach { sample ->
            File(sample.audioFilePath).delete()
        }

        // Delete instrument (samples cascade)
        dao.deleteInstrumentById(instrumentId)
        dao.updateProjectModifiedAt(instrument.projectId)

        true
    }

    /**
     * Update instrument name.
     */
    suspend fun updateInstrumentName(instrumentId: Long, name: String) {
        val instrument = dao.getInstrumentById(instrumentId)
        if (instrument != null) {
            dao.updateInstrument(instrument.copy(name = name))
            // Mark instrument as modified for hybrid export detection
            markInstrumentModified(instrumentId, ModificationFlags.MOD_FLAG_NAME)
            dao.updateProjectModifiedAt(instrument.projectId)
        }
    }

    /**
     * Update global parameters for an instrument from SF2 global zone generators.
     * These are additive parameters that apply to all samples in the instrument.
     *
     * @param instrumentId The instrument to update
     * @param globalGenerators Map of SF2 generator number to value from the global zone
     */
    suspend fun updateInstrumentGlobalParameters(instrumentId: Long, globalGenerators: Map<Int, Int>, fromImport: Boolean = false) {
        if (globalGenerators.isEmpty()) return

        val instrument = dao.getInstrumentById(instrumentId) ?: return

        // SF2 Generator constants
        val GEN_MOD_LFO_TO_PITCH = 5
        val GEN_VIB_LFO_TO_PITCH = 6
        val GEN_MOD_ENV_TO_PITCH = 7
        val GEN_INITIAL_FILTER_FC = 8
        val GEN_INITIAL_FILTER_Q = 9
        val GEN_MOD_LFO_TO_FILTER_FC = 10
        val GEN_MOD_ENV_TO_FILTER_FC = 11
        val GEN_MOD_LFO_TO_VOLUME = 13
        val GEN_CHORUS_EFFECTS_SEND = 15
        val GEN_REVERB_EFFECTS_SEND = 16
        val GEN_PAN = 17
        val GEN_DELAY_MOD_LFO = 21
        val GEN_FREQ_MOD_LFO = 22
        val GEN_DELAY_VIB_LFO = 23
        val GEN_FREQ_VIB_LFO = 24
        val GEN_DELAY_MOD_ENV = 25
        val GEN_ATTACK_MOD_ENV = 26
        val GEN_HOLD_MOD_ENV = 27
        val GEN_DECAY_MOD_ENV = 28
        val GEN_SUSTAIN_MOD_ENV = 29
        val GEN_RELEASE_MOD_ENV = 30
        val GEN_DELAY_VOL_ENV = 33
        val GEN_ATTACK_VOL_ENV = 34
        val GEN_HOLD_VOL_ENV = 35
        val GEN_DECAY_VOL_ENV = 36
        val GEN_KEYNUM_TO_MOD_ENV_HOLD = 31
        val GEN_KEYNUM_TO_MOD_ENV_DECAY = 32
        val GEN_SUSTAIN_VOL_ENV = 37
        val GEN_RELEASE_VOL_ENV = 38
        val GEN_KEYNUM_TO_VOL_ENV_HOLD = 39
        val GEN_KEYNUM_TO_VOL_ENV_DECAY = 40
        val GEN_INITIAL_ATTENUATION = 48
        val GEN_COARSE_TUNE = 51
        val GEN_FINE_TUNE = 52
        val GEN_SCALE_TUNING = 56
        val GEN_EXCLUSIVE_CLASS = 57

        val updatedInstrument = instrument.copy(
            globalAttenuation = globalGenerators[GEN_INITIAL_ATTENUATION] ?: 0,
            globalCoarseTune = globalGenerators[GEN_COARSE_TUNE] ?: 0,
            globalFineTune = globalGenerators[GEN_FINE_TUNE] ?: 0,
            globalVolEnvDelay = globalGenerators[GEN_DELAY_VOL_ENV] ?: 0,
            globalVolEnvAttack = globalGenerators[GEN_ATTACK_VOL_ENV] ?: 0,
            globalVolEnvHold = globalGenerators[GEN_HOLD_VOL_ENV] ?: 0,
            globalVolEnvDecay = globalGenerators[GEN_DECAY_VOL_ENV] ?: 0,
            globalVolEnvSustain = globalGenerators[GEN_SUSTAIN_VOL_ENV] ?: 0,
            globalVolEnvRelease = globalGenerators[GEN_RELEASE_VOL_ENV] ?: 0,
            globalModEnvDelay = globalGenerators[GEN_DELAY_MOD_ENV] ?: 0,
            globalModEnvAttack = globalGenerators[GEN_ATTACK_MOD_ENV] ?: 0,
            globalModEnvHold = globalGenerators[GEN_HOLD_MOD_ENV] ?: 0,
            globalModEnvDecay = globalGenerators[GEN_DECAY_MOD_ENV] ?: 0,
            globalModEnvSustain = globalGenerators[GEN_SUSTAIN_MOD_ENV] ?: 0,
            globalModEnvRelease = globalGenerators[GEN_RELEASE_MOD_ENV] ?: 0,
            globalModEnvToPitch = globalGenerators[GEN_MOD_ENV_TO_PITCH] ?: 0,
            globalModEnvToFilterFc = globalGenerators[GEN_MOD_ENV_TO_FILTER_FC] ?: 0,
            globalVibLfoDelay = globalGenerators[GEN_DELAY_VIB_LFO] ?: 0,
            globalVibLfoFreq = globalGenerators[GEN_FREQ_VIB_LFO] ?: 0,
            globalVibLfoToPitch = globalGenerators[GEN_VIB_LFO_TO_PITCH] ?: 0,
            globalModLfoDelay = globalGenerators[GEN_DELAY_MOD_LFO] ?: 0,
            globalModLfoFreq = globalGenerators[GEN_FREQ_MOD_LFO] ?: 0,
            globalModLfoToPitch = globalGenerators[GEN_MOD_LFO_TO_PITCH] ?: 0,
            globalModLfoToFilterFc = globalGenerators[GEN_MOD_LFO_TO_FILTER_FC] ?: 0,
            globalModLfoToVolume = globalGenerators[GEN_MOD_LFO_TO_VOLUME] ?: 0,
            globalFilterFc = globalGenerators[GEN_INITIAL_FILTER_FC] ?: 0,
            globalFilterQ = globalGenerators[GEN_INITIAL_FILTER_Q] ?: 0,
            globalChorusSend = globalGenerators[GEN_CHORUS_EFFECTS_SEND] ?: 0,
            globalReverbSend = globalGenerators[GEN_REVERB_EFFECTS_SEND] ?: 0,
            globalPan = globalGenerators[GEN_PAN] ?: 0,
            globalKeyToModEnvHold = globalGenerators[GEN_KEYNUM_TO_MOD_ENV_HOLD] ?: 0,
            globalKeyToModEnvDecay = globalGenerators[GEN_KEYNUM_TO_MOD_ENV_DECAY] ?: 0,
            globalKeyToVolEnvHold = globalGenerators[GEN_KEYNUM_TO_VOL_ENV_HOLD] ?: 0,
            globalKeyToVolEnvDecay = globalGenerators[GEN_KEYNUM_TO_VOL_ENV_DECAY] ?: 0,
            globalScaleTuning = globalGenerators[GEN_SCALE_TUNING] ?: 0,
            globalExclusiveClass = globalGenerators[GEN_EXCLUSIVE_CLASS] ?: 0
        )

        dao.updateInstrument(updatedInstrument)
        // Mark instrument as modified for hybrid export detection (only if not from import)
        if (!fromImport) {
            markInstrumentModified(instrumentId, ModificationFlags.MOD_FLAG_PARAMS)
        }
        dao.updateProjectModifiedAt(instrument.projectId)
    }

    /**
     * Save instrument-level modulators (IMOD global zone) for an instrument.
     * These modulators apply to all sample zones within the instrument.
     *
     * @param instrumentId The instrument to attach modulators to
     * @param modulators List of parsed modulators from SF2 import
     */
    suspend fun saveInstrumentLevelModulators(
        instrumentId: Long,
        modulators: List<Sf2ParsedModulator>
    ) {
        if (modulators.isEmpty()) return

        val modulatorEntities = modulators.map { mod ->
            Sf2ModulatorEntity(
                instrumentId = instrumentId,
                sampleId = null, // Instrument-level, not sample-level
                srcOper = mod.srcOper,
                destOper = mod.destOper,
                amount = mod.amount,
                amtSrcOper = mod.amtSrcOper,
                transOper = mod.transOper
            )
        }
        dao.insertModulators(modulatorEntities)
    }

    /**
     * Get sample count for each instrument in a project.
     * @return Map of instrumentId to sample count
     */
    suspend fun getInstrumentSampleCounts(projectId: Long): Map<Long, Int> {
        val instruments = dao.getInstrumentsForProject(projectId)
        return instruments.associate { instrument ->
            instrument.id to dao.getSampleCountForInstrument(instrument.id)
        }
    }

    // ==================== Preset Zones ====================

    suspend fun getPresetsForProject(projectId: Long): List<Sf2PresetEntity> {
        return dao.getPresetsForProject(projectId)
    }

    fun getPresetsForProjectFlow(projectId: Long): Flow<List<Sf2PresetEntity>> {
        return dao.getPresetsForProjectFlow(projectId)
    }

    suspend fun getPresetById(presetId: Long): Sf2PresetEntity? {
        return dao.getPresetById(presetId)
    }

    suspend fun getOrCreateDefaultPreset(projectId: Long): Sf2PresetEntity {
        return dao.getOrCreateDefaultPreset(projectId)
    }

    suspend fun updatePresetProgramNumber(presetId: Long, programNumber: Int) {
        val preset = dao.getPresetById(presetId)
        if (preset != null) {
            dao.updatePreset(preset.copy(programNumber = programNumber))
            // Mark preset as modified for hybrid export detection
            markPresetModified(presetId, ModificationFlags.MOD_FLAG_PARAMS)
            dao.updateProjectModifiedAt(preset.projectId)
        }
    }

    /**
     * Update a preset (name, program number, bank number).
     */
    suspend fun updatePreset(preset: Sf2PresetEntity) {
        dao.updatePreset(preset)
        // Mark preset as modified for hybrid export detection
        markPresetModified(preset.id, ModificationFlags.MOD_FLAG_PARAMS)
        dao.updateProjectModifiedAt(preset.projectId)
    }

    /**
     * Create a new preset zone for a project.
     * @return The ID of the created preset zone
     */
    suspend fun createPresetZone(
        projectId: Long,
        instrumentId: Long,
        name: String,
        programNumber: Int,
        bankNumber: Int = 0
    ): Long {
        val preset = Sf2PresetEntity(
            projectId = projectId,
            instrumentId = instrumentId,
            name = name,
            programNumber = programNumber,
            bankNumber = bankNumber
        )
        val presetId = dao.insertPreset(preset)
        dao.updateProjectModifiedAt(projectId)
        return presetId
    }

    /**
     * Create a new preset zone for a program.
     * @return The ID of the created preset zone
     */
    suspend fun createPresetZoneForProgram(
        projectId: Long,
        programId: Long,
        instrumentId: Long,
        name: String,
        programNumber: Int,
        bankNumber: Int = 0
    ): Long {
        val preset = Sf2PresetEntity(
            projectId = projectId,
            programId = programId,
            instrumentId = instrumentId,
            name = name,
            programNumber = programNumber,
            bankNumber = bankNumber
        )
        val presetId = dao.insertPreset(preset)
        dao.updateProjectModifiedAt(projectId)
        return presetId
    }

    /**
     * Data class for preset zone parameters (PGEN).
     * These are the generators from the preset zone that references this instrument.
     */
    data class PresetZoneParams(
        val keyRangeLow: Int = 0,
        val keyRangeHigh: Int = 127,
        val velRangeLow: Int = 0,
        val velRangeHigh: Int = 127,
        val attenuation: Int = 0,
        val coarseTune: Int = 0,
        val fineTune: Int = 0,
        val filterFc: Int = 0,
        val filterQ: Int = 0,
        val chorusSend: Int = 0,
        val reverbSend: Int = 0,
        val pan: Int = 0,
        val volEnvDelay: Int = 0,
        val volEnvAttack: Int = 0,
        val volEnvHold: Int = 0,
        val volEnvDecay: Int = 0,
        val volEnvSustain: Int = 0,
        val volEnvRelease: Int = 0,
        val modEnvDelay: Int = 0,
        val modEnvAttack: Int = 0,
        val modEnvHold: Int = 0,
        val modEnvDecay: Int = 0,
        val modEnvSustain: Int = 0,
        val modEnvRelease: Int = 0,
        val modEnvToPitch: Int = 0,
        val modEnvToFilterFc: Int = 0,
        val vibLfoDelay: Int = 0,
        val vibLfoFreq: Int = 0,
        val vibLfoToPitch: Int = 0,
        val modLfoDelay: Int = 0,
        val modLfoFreq: Int = 0,
        val modLfoToPitch: Int = 0,
        val modLfoToFilterFc: Int = 0,
        val modLfoToVolume: Int = 0,
        // Key-based envelope scaling
        val keyToModEnvHold: Int = 0,       // GEN 31
        val keyToModEnvDecay: Int = 0,      // GEN 32
        val keyToVolEnvHold: Int = 0,       // GEN 39
        val keyToVolEnvDecay: Int = 0,      // GEN 40
        // Additional generators
        val scaleTuning: Int = 0,           // GEN 56
        val exclusiveClass: Int = 0         // GEN 57
    )

    /**
     * Parameters for the global preset zone (PGEN global).
     * These are written to a zone WITHOUT GEN_INSTRUMENT and apply to ALL zones in the preset.
     * All values are in native SF2 units (timecents, centibels, etc).
     */
    data class ProgramGlobalParams(
        val attenuation: Int = 0,           // GEN 48
        val coarseTune: Int = 0,            // GEN 51
        val fineTune: Int = 0,              // GEN 52
        val filterFc: Int = 0,              // GEN 8
        val filterQ: Int = 0,               // GEN 9
        val chorusSend: Int = 0,            // GEN 15
        val reverbSend: Int = 0,            // GEN 16
        val pan: Int = 0,                   // GEN 17
        val volEnvDelay: Int = 0,           // GEN 33
        val volEnvAttack: Int = 0,          // GEN 34
        val volEnvHold: Int = 0,            // GEN 35
        val volEnvDecay: Int = 0,           // GEN 36
        val volEnvSustain: Int = 0,         // GEN 37
        val volEnvRelease: Int = 0,         // GEN 38
        val modEnvDelay: Int = 0,           // GEN 25
        val modEnvAttack: Int = 0,          // GEN 26
        val modEnvHold: Int = 0,            // GEN 27
        val modEnvDecay: Int = 0,           // GEN 28
        val modEnvSustain: Int = 0,         // GEN 29
        val modEnvRelease: Int = 0,         // GEN 30
        val modEnvToPitch: Int = 0,         // GEN 7
        val modEnvToFilterFc: Int = 0,      // GEN 11
        val vibLfoDelay: Int = 0,           // GEN 23
        val vibLfoFreq: Int = 0,            // GEN 24
        val vibLfoToPitch: Int = 0,         // GEN 6
        val modLfoDelay: Int = 0,           // GEN 21
        val modLfoFreq: Int = 0,            // GEN 22
        val modLfoToPitch: Int = 0,         // GEN 5
        val modLfoToFilterFc: Int = 0,      // GEN 10
        val modLfoToVolume: Int = 0,        // GEN 13
        // Key-based envelope scaling
        val keyToModEnvHold: Int = 0,       // GEN 31
        val keyToModEnvDecay: Int = 0,      // GEN 32
        val keyToVolEnvHold: Int = 0,       // GEN 39
        val keyToVolEnvDecay: Int = 0,      // GEN 40
        // Additional generators
        val scaleTuning: Int = 0,           // GEN 56
        val exclusiveClass: Int = 0         // GEN 57
    ) {
        fun hasAnyNonDefault(): Boolean {
            return attenuation != 0 || coarseTune != 0 || fineTune != 0 ||
                   filterFc != 0 || filterQ != 0 || chorusSend != 0 ||
                   reverbSend != 0 || pan != 0 || volEnvDelay != 0 ||
                   volEnvAttack != 0 || volEnvHold != 0 || volEnvDecay != 0 ||
                   volEnvSustain != 0 || volEnvRelease != 0 || modEnvDelay != 0 ||
                   modEnvAttack != 0 || modEnvHold != 0 || modEnvDecay != 0 ||
                   modEnvSustain != 0 || modEnvRelease != 0 || modEnvToPitch != 0 ||
                   modEnvToFilterFc != 0 || vibLfoDelay != 0 || vibLfoFreq != 0 ||
                   vibLfoToPitch != 0 || modLfoDelay != 0 || modLfoFreq != 0 ||
                   modLfoToPitch != 0 || modLfoToFilterFc != 0 || modLfoToVolume != 0 ||
                   keyToModEnvHold != 0 || keyToModEnvDecay != 0 ||
                   keyToVolEnvHold != 0 || keyToVolEnvDecay != 0 ||
                   scaleTuning != 0 || exclusiveClass != 0
        }
    }

    /**
     * Update the global parameters of a program (global PGEN zone).
     * These parameters are stored in Sf2ProgramEntity and written to a global preset zone
     * (zone without GEN_INSTRUMENT) at export time.
     */
    suspend fun updateProgramGlobalParams(programId: Long, params: ProgramGlobalParams) {
        val program = dao.getProgramById(programId) ?: return
        val updated = program.copy(
            globalAttenuation = params.attenuation,
            globalCoarseTune = params.coarseTune,
            globalFineTune = params.fineTune,
            globalFilterFc = params.filterFc,
            globalFilterQ = params.filterQ,
            globalChorusSend = params.chorusSend,
            globalReverbSend = params.reverbSend,
            globalPan = params.pan,
            globalVolEnvDelay = params.volEnvDelay,
            globalVolEnvAttack = params.volEnvAttack,
            globalVolEnvHold = params.volEnvHold,
            globalVolEnvDecay = params.volEnvDecay,
            globalVolEnvSustain = params.volEnvSustain,
            globalVolEnvRelease = params.volEnvRelease,
            globalModEnvDelay = params.modEnvDelay,
            globalModEnvAttack = params.modEnvAttack,
            globalModEnvHold = params.modEnvHold,
            globalModEnvDecay = params.modEnvDecay,
            globalModEnvSustain = params.modEnvSustain,
            globalModEnvRelease = params.modEnvRelease,
            globalModEnvToPitch = params.modEnvToPitch,
            globalModEnvToFilterFc = params.modEnvToFilterFc,
            globalVibLfoDelay = params.vibLfoDelay,
            globalVibLfoFreq = params.vibLfoFreq,
            globalVibLfoToPitch = params.vibLfoToPitch,
            globalModLfoDelay = params.modLfoDelay,
            globalModLfoFreq = params.modLfoFreq,
            globalModLfoToPitch = params.modLfoToPitch,
            globalModLfoToFilterFc = params.modLfoToFilterFc,
            globalModLfoToVolume = params.modLfoToVolume,
            globalKeyToModEnvHold = params.keyToModEnvHold,
            globalKeyToModEnvDecay = params.keyToModEnvDecay,
            globalKeyToVolEnvHold = params.keyToVolEnvHold,
            globalKeyToVolEnvDecay = params.keyToVolEnvDecay,
            globalScaleTuning = params.scaleTuning,
            globalExclusiveClass = params.exclusiveClass
        )
        dao.updateProgram(updated)
    }

    /**
     * Create a preset zone under a program with preset zone parameters.
     * This is used when importing SF2 files to preserve the preset zone generators (PGEN).
     */
    suspend fun createPresetZoneForProgramWithPgen(
        projectId: Long,
        programId: Long,
        instrumentId: Long,
        name: String,
        programNumber: Int,
        bankNumber: Int = 0,
        pgenParams: PresetZoneParams? = null
    ): Long {
        val params = pgenParams ?: PresetZoneParams()
        val preset = Sf2PresetEntity(
            projectId = projectId,
            programId = programId,
            instrumentId = instrumentId,
            name = name,
            programNumber = programNumber,
            bankNumber = bankNumber,
            // Preset zone parameters (PGEN)
            pgenKeyRangeLow = params.keyRangeLow,
            pgenKeyRangeHigh = params.keyRangeHigh,
            pgenVelRangeLow = params.velRangeLow,
            pgenVelRangeHigh = params.velRangeHigh,
            pgenAttenuation = params.attenuation,
            pgenCoarseTune = params.coarseTune,
            pgenFineTune = params.fineTune,
            pgenFilterFc = params.filterFc,
            pgenFilterQ = params.filterQ,
            pgenChorusSend = params.chorusSend,
            pgenReverbSend = params.reverbSend,
            pgenPan = params.pan,
            pgenVolEnvDelay = params.volEnvDelay,
            pgenVolEnvAttack = params.volEnvAttack,
            pgenVolEnvHold = params.volEnvHold,
            pgenVolEnvDecay = params.volEnvDecay,
            pgenVolEnvSustain = params.volEnvSustain,
            pgenVolEnvRelease = params.volEnvRelease,
            pgenModEnvDelay = params.modEnvDelay,
            pgenModEnvAttack = params.modEnvAttack,
            pgenModEnvHold = params.modEnvHold,
            pgenModEnvDecay = params.modEnvDecay,
            pgenModEnvSustain = params.modEnvSustain,
            pgenModEnvRelease = params.modEnvRelease,
            pgenModEnvToPitch = params.modEnvToPitch,
            pgenModEnvToFilterFc = params.modEnvToFilterFc,
            pgenVibLfoDelay = params.vibLfoDelay,
            pgenVibLfoFreq = params.vibLfoFreq,
            pgenVibLfoToPitch = params.vibLfoToPitch,
            pgenModLfoDelay = params.modLfoDelay,
            pgenModLfoFreq = params.modLfoFreq,
            pgenModLfoToPitch = params.modLfoToPitch,
            pgenModLfoToFilterFc = params.modLfoToFilterFc,
            pgenModLfoToVolume = params.modLfoToVolume,
            pgenKeyToModEnvHold = params.keyToModEnvHold,
            pgenKeyToModEnvDecay = params.keyToModEnvDecay,
            pgenKeyToVolEnvHold = params.keyToVolEnvHold,
            pgenKeyToVolEnvDecay = params.keyToVolEnvDecay,
            pgenScaleTuning = params.scaleTuning,
            pgenExclusiveClass = params.exclusiveClass
        )
        val presetId = dao.insertPreset(preset)
        dao.updateProjectModifiedAt(projectId)
        return presetId
    }

    /**
     * Delete a preset zone.
     * Also deletes the referenced instrument if no other preset zones use it.
     * @return true if deleted, false if it's the last preset zone (cannot delete)
     */
    suspend fun deletePresetZone(presetId: Long): Boolean = withContext(Dispatchers.IO) {
        val preset = dao.getPresetById(presetId) ?: return@withContext false

        // Check if this is the last preset zone
        val presets = dao.getPresetsForProject(preset.projectId)
        if (presets.size <= 1) {
            return@withContext false // Cannot delete the last preset zone
        }

        // Check if the instrument is still referenced by other preset zones
        val instrumentId = preset.instrumentId
        val otherPresetsUsingInstrument = presets.filter {
            it.id != presetId && it.instrumentId == instrumentId
        }

        // Mark chunks as modified BEFORE deleting (for hybrid export detection)
        // If the instrument is no longer used, its samples won't be exported,
        // so we need to mark smpl/shdr as modified to force a rebuild
        val instrumentWillBeOrphaned = otherPresetsUsingInstrument.isEmpty()
        if (instrumentWillBeOrphaned) {
            // Instrument will be deleted - mark all chunks as modified
            markChunksModified(preset.projectId, listOf(
                "phdr", "pbag", "pgen", "pmod",  // Preset chunks
                "inst", "ibag", "igen", "imod",  // Instrument chunks
                "smpl", "shdr"                   // Sample chunks
            ))
        } else {
            // Instrument still used by other presets - only preset chunks affected
            markChunksModified(preset.projectId, listOf("phdr", "pbag", "pgen", "pmod"))
        }

        // Delete preset zone first
        dao.deletePresetById(presetId)

        // If instrument is now orphaned, delete it and its audio files
        if (instrumentWillBeOrphaned) {
            val samples = dao.getSamplesForInstrument(instrumentId)
            samples.forEach { sample ->
                File(sample.audioFilePath).delete()
            }
            dao.deleteInstrumentById(instrumentId)
        }

        dao.updateProjectModifiedAt(preset.projectId)

        true
    }

    /**
     * Update preset name.
     */
    suspend fun updatePresetName(presetId: Long, name: String) {
        val preset = dao.getPresetById(presetId)
        if (preset != null) {
            dao.updatePreset(preset.copy(name = name))
            // Mark preset as modified for hybrid export detection
            markPresetModified(presetId, ModificationFlags.MOD_FLAG_NAME)
            dao.updateProjectModifiedAt(preset.projectId)
        }
    }

    /**
     * Get sample count for each preset zone in a project (via its instrument).
     * @return Map of presetId to sample count
     */
    suspend fun getPresetZoneSampleCounts(projectId: Long): Map<Long, Int> {
        val presets = dao.getPresetsForProject(projectId)
        return presets.associate { preset ->
            preset.id to dao.getSampleCountForInstrument(preset.instrumentId)
        }
    }

    // ==================== Samples ====================

    suspend fun getSamplesForInstrument(instrumentId: Long): List<Sf2SampleEntity> {
        return dao.getSamplesForInstrument(instrumentId)
    }

    fun getSamplesForInstrumentFlow(instrumentId: Long): Flow<List<Sf2SampleEntity>> {
        return dao.getSamplesForInstrumentFlow(instrumentId)
    }

    suspend fun getAllSamplesForProject(projectId: Long): List<Sf2SampleEntity> {
        return dao.getAllSamplesForProject(projectId)
    }

    suspend fun getModulatorsForSample(sampleId: Long): List<Sf2ModulatorEntity> {
        return dao.getModulatorsForSample(sampleId)
    }

    /**
     * Add a sample to a preset from in-memory audio data.
     * Saves the audio to a WAV file and creates the database entry.
     *
     * IMPORTANT: All envelope/filter parameters use SF2 NATIVE UNITS:
     * - Time values: timecents (-12000 = instant, 0 = 1 second)
     * - Sustain levels: centibels of attenuation (0 = full volume, 1000 = silent)
     * - Filter cutoff: absolute cents (13500 = ~20kHz)
     *
     * @param presetId The preset to add the sample to
     * @param name Sample name
     * @param samples Audio data as ShortArray
     * @param sampleRate Sample rate in Hz
     * @param rootNote MIDI note number of the original pitch
     * @param keyRangeStart Start of key range (0-127)
     * @param keyRangeEnd End of key range (0-127)
     * @param loopStart Loop start sample index
     * @param loopEnd Loop end sample index
     * @param hasLoop Whether the sample has loop points
     * @param attenuation Volume attenuation in centibels
     * @param fineTuneCents Fine tuning in cents
     * @param volEnvDelay Volume envelope delay (timecents, -12000 = instant)
     * @param volEnvAttack Volume envelope attack (timecents)
     * @param volEnvHold Volume envelope hold (timecents)
     * @param volEnvDecay Volume envelope decay (timecents)
     * @param volEnvSustain Volume envelope sustain (centibels, 0 = full)
     * @param volEnvRelease Volume envelope release (timecents)
     * @param filterFc Filter cutoff (absolute cents, 13500 = ~20kHz)
     * @param filterQ Filter resonance in centibels (0-960)
     * @param chorusSend Chorus send amount (0-1000)
     * @param reverbSend Reverb send amount (0-1000)
     * @param pan Stereo pan (-500 to +500)
     * @param velRangeStart Start of velocity range (0-127)
     * @param velRangeEnd End of velocity range (0-127)
     * @param coarseTune Coarse tuning in semitones (-120 to +120)
     * @param scaleTuning Scale tuning in cents per semitone (0-1200)
     * @param modEnvDelay Modulation envelope delay (timecents)
     * @param modEnvAttack Modulation envelope attack (timecents)
     * @param modEnvHold Modulation envelope hold (timecents)
     * @param modEnvDecay Modulation envelope decay (timecents)
     * @param modEnvSustain Modulation envelope sustain (centibels)
     * @param modEnvRelease Modulation envelope release (timecents)
     * @param modEnvToPitch Mod envelope to pitch in cents
     * @param modEnvToFilterFc Mod envelope to filter cutoff in cents
     * @param vibLfoDelay Vibrato LFO delay (timecents)
     * @param vibLfoFreq Vibrato LFO frequency in cents (0 = ~8.176 Hz)
     * @param vibLfoToPitch Vibrato LFO to pitch in cents
     * @param modLfoDelay Modulation LFO delay (timecents)
     * @param modLfoFreq Modulation LFO frequency in cents
     * @param modLfoToPitch Mod LFO to pitch in cents
     * @param modLfoToFilterFc Mod LFO to filter cutoff in cents
     * @param modLfoToVolume Mod LFO to volume in centibels
     * @param exclusiveClass Exclusive class (0 = none)
     * @param keyToVolEnvHold Key to volume envelope hold (timecents per key)
     * @param keyToVolEnvDecay Key to volume envelope decay (timecents per key)
     * @param keyToModEnvHold Key to modulation envelope hold (timecents per key)
     * @param keyToModEnvDecay Key to modulation envelope decay (timecents per key)
     * @param fixedKey Fixed MIDI key (-1 = not fixed, 0-127 = fixed)
     * @param fixedVelocity Fixed velocity (-1 = not fixed, 0-127 = fixed)
     * @param modulators List of modulators for this sample (optional)
     * @return The ID of the created sample
     */
    suspend fun addSampleToInstrument(
        instrumentId: Long,
        name: String,
        samples: ShortArray,
        sampleRate: Int = 44100,
        rootNote: Int,
        keyRangeStart: Int = 0,
        keyRangeEnd: Int = 127,
        loopStart: Int = 0,
        loopEnd: Int = 0,
        hasLoop: Boolean = false,
        sampleModes: Int = 0,
        attenuation: Int = 0,
        fineTuneCents: Int = 0,
        // Volume Envelope - SF2 native units (timecents for times, centibels for sustain)
        volEnvDelay: Int = -12000,
        volEnvAttack: Int = -12000,
        volEnvHold: Int = -12000,
        volEnvDecay: Int = -12000,
        volEnvSustain: Int = 0,       // centibels (0 = full volume)
        volEnvRelease: Int = -12000,
        // Filter - SF2 native units (absolute cents)
        filterFc: Int = 13500,        // 13500 cents = ~20kHz (fully open)
        filterQ: Int = 0,
        chorusSend: Int = 0,
        reverbSend: Int = 0,
        pan: Int = 0,
        // Advanced SF2 parameters
        velRangeStart: Int = 0,
        velRangeEnd: Int = 127,
        coarseTune: Int = 0,
        scaleTuning: Int = 100,
        // Modulation Envelope - SF2 native units
        modEnvDelay: Int = -12000,
        modEnvAttack: Int = -12000,
        modEnvHold: Int = -12000,
        modEnvDecay: Int = -12000,
        modEnvSustain: Int = 0,       // centibels
        modEnvRelease: Int = -12000,
        modEnvToPitch: Int = 0,
        modEnvToFilterFc: Int = 0,
        // Vibrato LFO - SF2 native units
        vibLfoDelay: Int = -12000,
        vibLfoFreq: Int = 0,
        vibLfoToPitch: Int = 0,
        // Modulation LFO - SF2 native units
        modLfoDelay: Int = -12000,
        modLfoFreq: Int = 0,
        modLfoToPitch: Int = 0,
        modLfoToFilterFc: Int = 0,
        modLfoToVolume: Int = 0,
        exclusiveClass: Int = 0,
        // Key-to-envelope scaling
        keyToVolEnvHold: Int = 0,
        keyToVolEnvDecay: Int = 0,
        keyToModEnvHold: Int = 0,
        keyToModEnvDecay: Int = 0,
        // Fixed key/velocity
        fixedKey: Int = -1,
        fixedVelocity: Int = -1,
        // Sample header fields
        pitchCorrection: Int = 0,
        // Modulators
        modulators: List<Sf2ParsedModulator> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        // Get the instrument for project ID
        val instrument = dao.getInstrumentById(instrumentId)
        if (instrument == null) {
            Log.e(TAG, "addSample: instrument not found: $instrumentId")
            return@withContext -1L
        }

        // Create project sample directory
        val projectDir = File(samplesDir, instrument.projectId.toString())
        projectDir.mkdirs()

        // Generate unique filename
        val timestamp = System.currentTimeMillis()
        val audioFile = File(projectDir, "${timestamp}_${name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.wav")

        // Write WAV file
        writeWavFile(audioFile, samples, sampleRate)

        // Create database entry with SF2 native units
        val sampleEntity = Sf2SampleEntity(
            instrumentId = instrumentId,
            name = name,
            audioFilePath = audioFile.absolutePath,
            sampleRate = sampleRate,
            rootNote = rootNote,
            keyRangeStart = keyRangeStart,
            keyRangeEnd = keyRangeEnd,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = hasLoop,
            sampleModes = sampleModes,
            attenuation = attenuation,
            fineTuneCents = fineTuneCents,
            // Volume Envelope - SF2 native units
            volEnvDelay = volEnvDelay,
            volEnvAttack = volEnvAttack,
            volEnvHold = volEnvHold,
            volEnvDecay = volEnvDecay,
            volEnvSustain = volEnvSustain,
            volEnvRelease = volEnvRelease,
            // Filter - SF2 native units
            filterFc = filterFc,
            filterQ = filterQ,
            chorusSend = chorusSend,
            reverbSend = reverbSend,
            pan = pan,
            // Advanced SF2 parameters
            velRangeStart = velRangeStart,
            velRangeEnd = velRangeEnd,
            coarseTune = coarseTune,
            scaleTuning = scaleTuning,
            // Modulation Envelope - SF2 native units
            modEnvDelay = modEnvDelay,
            modEnvAttack = modEnvAttack,
            modEnvHold = modEnvHold,
            modEnvDecay = modEnvDecay,
            modEnvSustain = modEnvSustain,
            modEnvRelease = modEnvRelease,
            modEnvToPitch = modEnvToPitch,
            modEnvToFilterFc = modEnvToFilterFc,
            // Vibrato LFO - SF2 native units
            vibLfoDelay = vibLfoDelay,
            vibLfoFreq = vibLfoFreq,
            vibLfoToPitch = vibLfoToPitch,
            // Modulation LFO - SF2 native units
            modLfoDelay = modLfoDelay,
            modLfoFreq = modLfoFreq,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilterFc = modLfoToFilterFc,
            modLfoToVolume = modLfoToVolume,
            exclusiveClass = exclusiveClass,
            // Key-to-envelope scaling
            keyToVolEnvHold = keyToVolEnvHold,
            keyToVolEnvDecay = keyToVolEnvDecay,
            keyToModEnvHold = keyToModEnvHold,
            keyToModEnvDecay = keyToModEnvDecay,
            // Fixed key/velocity
            fixedKey = fixedKey,
            fixedVelocity = fixedVelocity,
            // Sample header fields
            pitchCorrection = pitchCorrection
        )

        val sampleId = dao.insertSample(sampleEntity)

        // Insert modulators for this sample
        if (modulators.isNotEmpty()) {
            val modulatorEntities = modulators.map { mod ->
                Sf2ModulatorEntity(
                    sampleId = sampleId,
                    srcOper = mod.srcOper,
                    destOper = mod.destOper,
                    amount = mod.amount,
                    amtSrcOper = mod.amtSrcOper,
                    transOper = mod.transOper
                )
            }
            dao.insertModulators(modulatorEntities)
        }

        // Update project modification time
        dao.updateProjectModifiedAt(instrument.projectId)

        sampleId
    }

    /**
     * Add a sample reference to an instrument (patch-based import).
     * Unlike addSampleToInstrument, this does NOT extract audio to a WAV file.
     * Instead, it stores references to the source SF2 file for efficient export.
     *
     * @param instrumentId The instrument to add the sample to
     * @param name Sample name
     * @param sourceFilePath Path to the source SF2 file
     * @param sourceSmplOffset Byte offset within the source SF2's smpl chunk
     * @param sourceSampleSize Size in bytes of the sample data
     * @param sampleRate Sample rate in Hz
     * @param sampleCount Number of samples (for calculating loop positions)
     * @param rootNote MIDI note number of the original pitch
     * @param ... (other SF2 parameters same as addSampleToInstrument)
     * @return The ID of the created sample
     */
    suspend fun addSampleReferenceToInstrument(
        instrumentId: Long,
        name: String,
        sourceFilePath: String,
        sourceSmplOffset: Long,
        sourceSampleSize: Long,
        sampleRate: Int,
        sampleCount: Int, // Number of samples for loop calculation
        rootNote: Int,
        keyRangeStart: Int = 0,
        keyRangeEnd: Int = 127,
        loopStart: Int = 0,
        loopEnd: Int = 0,
        hasLoop: Boolean = false,
        sampleModes: Int = 0,
        attenuation: Int = 0,
        fineTuneCents: Int = 0,
        volEnvDelay: Int = -12000,
        volEnvAttack: Int = -12000,
        volEnvHold: Int = -12000,
        volEnvDecay: Int = -12000,
        volEnvSustain: Int = 0,
        volEnvRelease: Int = -12000,
        filterFc: Int = 13500,
        filterQ: Int = 0,
        chorusSend: Int = 0,
        reverbSend: Int = 0,
        pan: Int = 0,
        velRangeStart: Int = 0,
        velRangeEnd: Int = 127,
        coarseTune: Int = 0,
        scaleTuning: Int = 100,
        modEnvDelay: Int = -12000,
        modEnvAttack: Int = -12000,
        modEnvHold: Int = -12000,
        modEnvDecay: Int = -12000,
        modEnvSustain: Int = 0,
        modEnvRelease: Int = -12000,
        modEnvToPitch: Int = 0,
        modEnvToFilterFc: Int = 0,
        vibLfoDelay: Int = -12000,
        vibLfoFreq: Int = 0,
        vibLfoToPitch: Int = 0,
        modLfoDelay: Int = -12000,
        modLfoFreq: Int = 0,
        modLfoToPitch: Int = 0,
        modLfoToFilterFc: Int = 0,
        modLfoToVolume: Int = 0,
        exclusiveClass: Int = 0,
        keyToVolEnvHold: Int = 0,
        keyToVolEnvDecay: Int = 0,
        keyToModEnvHold: Int = 0,
        keyToModEnvDecay: Int = 0,
        fixedKey: Int = -1,
        fixedVelocity: Int = -1,
        pitchCorrection: Int = 0,
        modulators: List<Sf2ParsedModulator> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        // Get the instrument for project ID
        val instrument = dao.getInstrumentById(instrumentId)
        if (instrument == null) {
            Log.e(TAG, "addImportedSample: instrument not found: $instrumentId")
            return@withContext -1L
        }

        // Create database entry with source reference (no WAV file)
        val sampleEntity = Sf2SampleEntity(
            instrumentId = instrumentId,
            name = name,
            audioFilePath = "", // No WAV file - audio is read from source
            sampleRate = sampleRate,
            rootNote = rootNote,
            keyRangeStart = keyRangeStart,
            keyRangeEnd = keyRangeEnd,
            loopStart = loopStart,
            loopEnd = loopEnd.coerceIn(0, sampleCount - 1),
            hasLoop = hasLoop,
            sampleModes = sampleModes,
            attenuation = attenuation,
            fineTuneCents = fineTuneCents,
            volEnvDelay = volEnvDelay,
            volEnvAttack = volEnvAttack,
            volEnvHold = volEnvHold,
            volEnvDecay = volEnvDecay,
            volEnvSustain = volEnvSustain,
            volEnvRelease = volEnvRelease,
            filterFc = filterFc,
            filterQ = filterQ,
            chorusSend = chorusSend,
            reverbSend = reverbSend,
            pan = pan,
            velRangeStart = velRangeStart,
            velRangeEnd = velRangeEnd,
            coarseTune = coarseTune,
            scaleTuning = scaleTuning,
            modEnvDelay = modEnvDelay,
            modEnvAttack = modEnvAttack,
            modEnvHold = modEnvHold,
            modEnvDecay = modEnvDecay,
            modEnvSustain = modEnvSustain,
            modEnvRelease = modEnvRelease,
            modEnvToPitch = modEnvToPitch,
            modEnvToFilterFc = modEnvToFilterFc,
            vibLfoDelay = vibLfoDelay,
            vibLfoFreq = vibLfoFreq,
            vibLfoToPitch = vibLfoToPitch,
            modLfoDelay = modLfoDelay,
            modLfoFreq = modLfoFreq,
            modLfoToPitch = modLfoToPitch,
            modLfoToFilterFc = modLfoToFilterFc,
            modLfoToVolume = modLfoToVolume,
            exclusiveClass = exclusiveClass,
            keyToVolEnvHold = keyToVolEnvHold,
            keyToVolEnvDecay = keyToVolEnvDecay,
            keyToModEnvHold = keyToModEnvHold,
            keyToModEnvDecay = keyToModEnvDecay,
            fixedKey = fixedKey,
            fixedVelocity = fixedVelocity,
            pitchCorrection = pitchCorrection,
            // Source reference fields (patch-based import)
            sourceFilePath = sourceFilePath,
            sourceSmplOffset = sourceSmplOffset,
            sourceSampleSize = sourceSampleSize,
            isExtracted = false // Audio is NOT extracted to WAV
        )

        val sampleId = dao.insertSample(sampleEntity)

        // Insert modulators for this sample
        if (modulators.isNotEmpty()) {
            val modulatorEntities = modulators.map { mod ->
                Sf2ModulatorEntity(
                    sampleId = sampleId,
                    srcOper = mod.srcOper,
                    destOper = mod.destOper,
                    amount = mod.amount,
                    amtSrcOper = mod.amtSrcOper,
                    transOper = mod.transOper
                )
            }
            dao.insertModulators(modulatorEntities)
        }

        // Update project modification time
        dao.updateProjectModifiedAt(instrument.projectId)

        sampleId
    }

    /**
     * @deprecated Use updateInstrumentGlobalParameters instead
     */
    @Deprecated("Use updateInstrumentGlobalParameters instead", ReplaceWith("updateInstrumentGlobalParameters(instrumentId, globalGenerators)"))
    suspend fun updatePresetGlobalParameters(presetId: Long, globalGenerators: Map<Int, Int>) {
        // Find the instrument via the preset
        val preset = dao.getPresetById(presetId) ?: return
        updateInstrumentGlobalParameters(preset.instrumentId, globalGenerators)
    }

    /**
     * Save program-level modulators (PMOD global zone) for a program.
     * These modulators apply to all zones within the preset/program.
     *
     * @param programId The program to attach modulators to
     * @param modulators List of parsed modulators from SF2 import
     */
    suspend fun saveProgramLevelModulators(
        programId: Long,
        modulators: List<Sf2ParsedModulator>
    ) {
        if (modulators.isEmpty()) return

        val modulatorEntities = modulators.map { mod ->
            Sf2ModulatorEntity(
                programId = programId,
                presetId = null,
                sampleId = null,
                srcOper = mod.srcOper,
                destOper = mod.destOper,
                amount = mod.amount,
                amtSrcOper = mod.amtSrcOper,
                transOper = mod.transOper
            )
        }
        dao.insertModulators(modulatorEntities)
    }

    /**
     * Save preset-level modulators (PMOD) for an instrument.
     * These modulators apply to the entire preset/instrument, not individual samples.
     *
     * @param presetId The preset (instrument) to attach modulators to
     * @param modulators List of parsed modulators from SF2 import
     */
    suspend fun savePresetLevelModulators(
        presetId: Long,
        modulators: List<Sf2ParsedModulator>
    ) {
        if (modulators.isEmpty()) return

        val modulatorEntities = modulators.map { mod ->
            Sf2ModulatorEntity(
                presetId = presetId,
                sampleId = null, // Preset-level, not sample-level
                srcOper = mod.srcOper,
                destOper = mod.destOper,
                amount = mod.amount,
                amtSrcOper = mod.amtSrcOper,
                transOper = mod.transOper
            )
        }
        dao.insertModulators(modulatorEntities)
    }

    suspend fun updateSample(sample: Sf2SampleEntity) {
        val previous = dao.getSampleById(sample.id)
        dao.updateSample(if (previous != null) Sf2EditNoise.keepUnedited(previous, sample) else sample)
        // Mark sample as modified (parameters changed) for hybrid export detection
        markSampleModified(sample.id, ModificationFlags.MOD_FLAG_PARAMS)
        // Update project modification time
        val instrument = dao.getInstrumentById(sample.instrumentId)
        instrument?.let { dao.updateProjectModifiedAt(it.projectId) }
    }

    suspend fun deleteSample(sampleId: Long) {
        val sample = dao.getSampleById(sampleId)
        if (sample != null) {
            // Get instrument BEFORE deleting (for hybrid export detection and timestamp update)
            val instrument = dao.getInstrumentById(sample.instrumentId)
            // Mark chunks as modified for hybrid export detection
            instrument?.let {
                markChunksModified(it.projectId, listOf("smpl", "shdr", "igen", "ibag"))
            }
            // Delete audio file
            File(sample.audioFilePath).delete()
            // Delete from database
            dao.deleteSampleById(sampleId)
            // Update project modification time
            instrument?.let { dao.updateProjectModifiedAt(it.projectId) }
        }
    }

    // ==================== SF2 import ====================

    /** Inserts an instrument built by [Sf2ImportMapper], with its global zone modulators. */
    suspend fun insertImportedInstrument(instrument: Sf2InstrumentEntity, modulators: List<Sf2ParsedModulator>): Long {
        val instrumentId = dao.insertInstrument(instrument)
        saveInstrumentLevelModulators(instrumentId, modulators)
        dao.updateProjectModifiedAt(instrument.projectId)
        return instrumentId
    }

    /**
     * Inserts an instrument zone built by [Sf2ImportMapper]. With [audio], the zone plays a
     * WAV copy of it; otherwise it reads its source file.
     */
    suspend fun insertImportedSample(
        sample: Sf2SampleEntity,
        modulators: List<Sf2ParsedModulator>,
        audio: ShortArray? = null
    ): Long = withContext(Dispatchers.IO) {
        var entity = sample
        if (audio != null) {
            val instrument = dao.getInstrumentById(sample.instrumentId) ?: return@withContext -1L
            val projectDir = File(samplesDir, instrument.projectId.toString()).also { it.mkdirs() }
            val audioFile = File(projectDir, "${System.nanoTime()}_${sample.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.wav")
            writeWavFile(audioFile, audio, sample.sampleRate)
            entity = entity.copy(audioFilePath = audioFile.absolutePath)
        }
        val sampleId = dao.insertSample(entity)
        if (modulators.isNotEmpty()) {
            dao.insertModulators(modulators.map { mod ->
                Sf2ModulatorEntity(
                    sampleId = sampleId,
                    srcOper = mod.srcOper,
                    destOper = mod.destOper,
                    amount = mod.amount,
                    amtSrcOper = mod.amtSrcOper,
                    transOper = mod.transOper
                )
            })
        }
        sampleId
    }

    /** Inserts a preset zone built by [Sf2ImportMapper], with its modulators. */
    suspend fun insertImportedPresetZone(zone: Sf2PresetEntity, modulators: List<Sf2ParsedModulator>): Long {
        val presetId = dao.insertPreset(zone)
        savePresetLevelModulators(presetId, modulators)
        dao.updateProjectModifiedAt(zone.projectId)
        return presetId
    }

    /** Gives a program the global zone of an imported preset. */
    suspend fun applyImportedProgramGlobals(programId: Long, preset: Sf2ParsedPreset) {
        val program = dao.getProgramById(programId) ?: return
        dao.updateProgram(Sf2ImportMapper.programGlobals(program, preset))
        saveProgramLevelModulators(programId, preset.globalModulators)
    }

    // ==================== Source Metadata ====================

    /**
     * Save source metadata for a project.
     * Called after importing an SF2 file to enable hybrid passthrough export.
     */
    suspend fun saveSourceMetadata(metadata: Sf2SourceMetadataEntity) {
        dao.insertSourceMetadata(metadata)
    }

    /**
     * Get source metadata for a project.
     * Returns null if the project was created from scratch (not imported).
     */
    suspend fun getSourceMetadata(projectId: Long): Sf2SourceMetadataEntity? {
        return dao.getSourceMetadata(projectId)
    }

    /**
     * Delete source metadata for a project.
     */
    suspend fun deleteSourceMetadata(projectId: Long) {
        // Delete the mapping file
        File(mappingsDir, "${projectId}.json").delete()
        // Delete from database
        dao.deleteSourceMetadata(projectId)
    }

    /**
     * Update the chunk registry for a project.
     * Called when modifications are made to mark affected chunks.
     */
    suspend fun updateChunkRegistry(projectId: Long, registry: String) {
        dao.updateChunkRegistry(projectId, registry)
    }

    // ==================== Modification Tracking ====================

    /**
     * Mark a sample as modified by the user.
     * @param sampleId The sample ID
     * @param flags Modification flags (from ModificationFlags)
     */
    suspend fun markSampleModified(sampleId: Long, flags: Int) {
        dao.markSampleModified(sampleId, flags)
        // Also update the project's chunk registry to mark affected chunks
        val sample = dao.getSampleById(sampleId) ?: return
        val instrument = dao.getInstrumentById(sample.instrumentId) ?: return

        // Only mark smpl as modified if audio data changed, not just parameters
        val chunksToMark = if ((flags and ModificationFlags.MOD_FLAG_AUDIO) != 0) {
            listOf("smpl", "shdr", "igen", "ibag")
        } else {
            // Parameter-only changes don't affect smpl chunk
            listOf("shdr", "igen", "ibag")
        }
        markChunksModified(instrument.projectId, chunksToMark)
    }

    /**
     * Mark a preset zone as modified by the user.
     */
    suspend fun markPresetModified(presetId: Long, flags: Int) {
        dao.markPresetModified(presetId, flags)
        val preset = dao.getPresetById(presetId) ?: return
        markChunksModified(preset.projectId, listOf("pgen", "pbag"))
    }

    /**
     * Mark an instrument as modified by the user.
     */
    suspend fun markInstrumentModified(instrumentId: Long, flags: Int) {
        dao.markInstrumentModified(instrumentId, flags)
        val instrument = dao.getInstrumentById(instrumentId) ?: return
        markChunksModified(instrument.projectId, listOf("inst", "igen", "ibag", "imod"))
    }

    /**
     * Mark specific chunks as modified in the chunk registry.
     * This affects which chunks can use passthrough at export time.
     */
    private suspend fun markChunksModified(projectId: Long, chunkIds: List<String>) {
        val metadata = dao.getSourceMetadata(projectId) ?: return

        try {
            val registry = org.json.JSONObject(metadata.chunkRegistry)
            for (chunkId in chunkIds) {
                if (registry.has(chunkId)) {
                    val chunk = registry.getJSONObject(chunkId)
                    chunk.put("isModified", true)
                }
            }
            dao.updateChunkRegistry(projectId, registry.toString())
        } catch (e: Exception) {
            // Ignore JSON errors
        }
    }

    /**
     * Check if a project has any modified samples.
     */
    suspend fun hasModifiedSamples(projectId: Long): Boolean {
        return dao.hasModifiedSamples(projectId)
    }

    // ==================== Export ====================

    /** Everything stored for a project, for export. */
    suspend fun loadExportSnapshot(projectId: Long): Sf2ProjectSnapshot? {
        val project = dao.getProjectById(projectId) ?: return null
        val metadata = dao.getSourceMetadata(projectId)
        return Sf2ProjectSnapshot(
            projectName = project.name,
            programs = dao.getProgramsForProject(projectId),
            presetZones = dao.getPresetZonesForExport(projectId),
            instruments = dao.getInstrumentsForExport(projectId),
            samples = dao.getSamplesForExport(projectId),
            modulators = dao.getModulatorsForExport(projectId),
            sourceFilePath = metadata?.sourceFilePath?.takeIf { File(it).exists() },
            importProjectName = metadata?.importProjectName
        )
    }

    private val projectWavAccess = object : Sf2ProjectExporter.WavAccess {
        override fun frameCount(path: String): Int = File(path).let { if (it.isFile) WavUtils.getWavSampleCount(it) else 0 }
        override fun identity(path: String): String = File(path).let { "$path:${it.length()}:${it.lastModified()}" }
        override fun load(path: String): ShortArray? = WavUtils.loadWavFile(File(path))
        override fun prepareLoop(data: ShortArray, sample: Sf2SampleEntity) =
            Sf2Writer().prepareRecordedLoop(data, sample.sampleRate, sample.loopStart, sample.loopEnd)
    }

    private suspend fun buildExportDocument(projectId: Long): Sf2Document? {
        val snapshot = loadExportSnapshot(projectId) ?: return null
        return Sf2ProjectExporter.build(snapshot, { path -> Sf2Reader().parse(File(path)) }, projectWavAccess)
    }

    /**
     * Export a project to an SF2 file.
     *
     * A project imported from a whole SF2 and left unchanged is written as a copy of that
     * file. Otherwise the file is rebuilt from the project ([Sf2ProjectExporter]): imported
     * audio is copied as is, and every zone keeps the generators the app did not change.
     *
     * @return true if successful
     */
    suspend fun exportProjectToSf2(projectId: Long, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = buildExportDocument(projectId) ?: return@withContext false
            val metadata = dao.getSourceMetadata(projectId)
            val source = metadata?.sourceFilePath?.let { File(it) }
            if (source != null && source.isFile && metadata.importFingerprint != null &&
                metadata.importFingerprint == document.fingerprint()
            ) {
                source.copyTo(outputFile, overwrite = true)
            } else {
                Sf2DocumentWriter.write(document, outputFile)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "SF2 export failed", e)
            outputFile.delete()
            false
        }
    }

    /**
     * Records the state of a project right after an import from its source SF2. When the
     * whole file was imported ([complete]), an unchanged project is later exported as an
     * exact copy of it.
     */
    suspend fun recordImport(projectId: Long, complete: Boolean) = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: return@withContext
        // The name first: the exported INFO depends on it
        dao.updateImportFingerprint(projectId, null, project.name)
        if (complete) {
            dao.updateImportFingerprint(projectId, buildExportDocument(projectId)?.fingerprint(), project.name)
        }
    }

    // ==================== Audio files ====================

    /** A sound decoded from an audio file, with the note it plays if it has a clear pitch. */
    class AudioFileSound(val name: String, val samples: ShortArray, val sampleRate: Int, val pitch: PitchResult?)

    /**
     * Adds sounds from audio files to an instrument, placed on the keyboard by
     * [Sf2SamplePlacement]. A detected pitch that is slightly off is corrected in the sample.
     * @return ids of the new samples
     */
    suspend fun addAudioFiles(instrumentId: Long, sounds: List<AudioFileSound>): List<Long> = withContext(Dispatchers.IO) {
        val occupied = dao.getSamplesForInstrument(instrumentId)
            .flatMap { (it.keyRangeStart.coerceIn(0, 127)..it.keyRangeEnd.coerceIn(0, 127)).toList() }
            .toSet()
        val placements = Sf2SamplePlacement.place(sounds.map { it.pitch?.midiNote }, occupied)
        sounds.zip(placements).map { (sound, place) ->
            addSampleToInstrument(
                instrumentId = instrumentId,
                name = sound.name.take(20),
                samples = sound.samples,
                sampleRate = sound.sampleRate,
                rootNote = place.rootNote,
                keyRangeStart = place.low,
                keyRangeEnd = place.high,
                pitchCorrection = -(sound.pitch?.cents ?: 0)
            )
        }
    }

    // ==================== Sample audio ====================

    /** Audio of a sample: its WAV file, or its data in the imported SF2. */
    suspend fun loadSampleAudio(sample: Sf2SampleEntity): ShortArray? = withContext(Dispatchers.IO) {
        if (!sample.isExtracted && sample.sourceFilePath != null && sample.audioFilePath.isEmpty()) {
            readSourceAudio(File(sample.sourceFilePath), sample.sourceSmplOffset, sample.sourceSampleSize)
        } else {
            loadWavFile(File(sample.audioFilePath))
        }
    }

    /**
     * Stores the audio of a sample edited in the sample editor. A sample read from its
     * imported SF2 keeps that reference while its audio is unchanged, and gets its own WAV
     * file otherwise. Returns the sample with its audio reference, or null on failure.
     */
    suspend fun saveEditedSampleAudio(sample: Sf2SampleEntity, audio: ShortArray): Sf2SampleEntity? =
        withContext(Dispatchers.IO) {
            if (loadSampleAudio(sample)?.contentEquals(audio) == true) return@withContext sample
            val target = if (sample.audioFilePath.isNotEmpty()) {
                File(sample.audioFilePath)
            } else {
                val instrument = dao.getInstrumentById(sample.instrumentId) ?: return@withContext null
                val projectDir = File(samplesDir, instrument.projectId.toString()).also { it.mkdirs() }
                File(projectDir, "${System.nanoTime()}_${sample.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.wav")
            }
            if (!WavUtils.writeWavFile(target, audio, sample.sampleRate)) return@withContext null
            sample.copy(
                audioFilePath = target.absolutePath,
                isExtracted = true,
                modificationFlags = sample.modificationFlags or ModificationFlags.MOD_FLAG_AUDIO
            )
        }

    private fun readSourceAudio(file: File, offset: Long, size: Long): ShortArray? = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(size.toInt())
            raf.seek(offset)
            raf.readFully(bytes)
            ShortArray(bytes.size / 2) { i ->
                ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot read sample audio from ${file.absolutePath}", e)
        null
    }

    // ==================== Audio File Utilities ====================

    /**
     * Write a WAV file from sample data.
     * Delegates to WavUtils.
     */
    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        WavUtils.writeWavFile(file, samples, sampleRate)
    }

    /**
     * Load samples from a WAV file.
     * Delegates to WavUtils.
     */
    private fun loadWavFile(file: File): ShortArray? = WavUtils.loadWavFile(file)
}

/** What the gallery shows of a project: counts and the keys that have a sound. */
class Sf2ProjectOverview(val programCount: Int, val sampleCount: Int, val coveredKeys: BooleanArray)
