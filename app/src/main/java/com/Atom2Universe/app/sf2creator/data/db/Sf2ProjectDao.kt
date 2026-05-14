package com.Atom2Universe.app.sf2creator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.OnConflictStrategy
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ModulatorEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Sf2ProjectDao {

    // ==================== Projects ====================

    @Query("SELECT * FROM sf2_projects ORDER BY modifiedAt DESC")
    fun getAllProjectsFlow(): Flow<List<Sf2ProjectEntity>>

    @Query("SELECT * FROM sf2_projects ORDER BY modifiedAt DESC")
    suspend fun getAllProjects(): List<Sf2ProjectEntity>

    @Query("SELECT * FROM sf2_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): Sf2ProjectEntity?

    @Insert
    suspend fun insertProject(project: Sf2ProjectEntity): Long

    @Update
    suspend fun updateProject(project: Sf2ProjectEntity)

    @Delete
    suspend fun deleteProject(project: Sf2ProjectEntity)

    @Query("DELETE FROM sf2_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("UPDATE sf2_projects SET modifiedAt = :timestamp WHERE id = :projectId")
    suspend fun updateProjectModifiedAt(projectId: Long, timestamp: Long = System.currentTimeMillis())

    // ==================== Programs ====================

    @Query("SELECT * FROM sf2_programs WHERE projectId = :projectId ORDER BY bankNumber, programNumber")
    suspend fun getProgramsForProject(projectId: Long): List<Sf2ProgramEntity>

    @Query("SELECT * FROM sf2_programs WHERE projectId = :projectId ORDER BY bankNumber, programNumber")
    fun getProgramsForProjectFlow(projectId: Long): Flow<List<Sf2ProgramEntity>>

    @Query("SELECT * FROM sf2_programs WHERE id = :programId")
    suspend fun getProgramById(programId: Long): Sf2ProgramEntity?

    @Insert
    suspend fun insertProgram(program: Sf2ProgramEntity): Long

    @Update
    suspend fun updateProgram(program: Sf2ProgramEntity)

    @Delete
    suspend fun deleteProgram(program: Sf2ProgramEntity)

    @Query("DELETE FROM sf2_programs WHERE id = :programId")
    suspend fun deleteProgramById(programId: Long)

    @Query("SELECT COUNT(*) FROM sf2_programs WHERE projectId = :projectId")
    suspend fun getProgramCountForProject(projectId: Long): Int

    @Query("SELECT * FROM sf2_programs WHERE projectId = :projectId AND programNumber = :programNumber AND bankNumber = :bankNumber LIMIT 1")
    suspend fun findProgramByNumber(projectId: Long, programNumber: Int, bankNumber: Int): Sf2ProgramEntity?

    // ==================== Presets (Instruments) ====================

    @Query("SELECT * FROM sf2_presets WHERE projectId = :projectId ORDER BY programId, programNumber, bankNumber")
    suspend fun getPresetsForProject(projectId: Long): List<Sf2PresetEntity>

    @Query("SELECT * FROM sf2_presets WHERE projectId = :projectId ORDER BY programNumber, bankNumber")
    fun getPresetsForProjectFlow(projectId: Long): Flow<List<Sf2PresetEntity>>

    @Query("SELECT * FROM sf2_presets WHERE id = :presetId")
    suspend fun getPresetById(presetId: Long): Sf2PresetEntity?

    @Insert
    suspend fun insertPreset(preset: Sf2PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: Sf2PresetEntity)

    @Delete
    suspend fun deletePreset(preset: Sf2PresetEntity)

    @Query("DELETE FROM sf2_presets WHERE id = :presetId")
    suspend fun deletePresetById(presetId: Long)

    @Query("SELECT * FROM sf2_presets WHERE programId = :programId ORDER BY id")
    suspend fun getPresetZonesForProgram(programId: Long): List<Sf2PresetEntity>

    @Query("SELECT * FROM sf2_presets WHERE programId = :programId ORDER BY id")
    fun getPresetZonesForProgramFlow(programId: Long): Flow<List<Sf2PresetEntity>>

    @Query("SELECT COUNT(*) FROM sf2_presets WHERE programId = :programId")
    suspend fun getPresetZoneCountForProgram(programId: Long): Int

    // ==================== Instruments ====================

    @Query("SELECT * FROM sf2_instruments WHERE projectId = :projectId ORDER BY name")
    suspend fun getInstrumentsForProject(projectId: Long): List<Sf2InstrumentEntity>

    @Query("SELECT * FROM sf2_instruments WHERE projectId = :projectId ORDER BY name")
    fun getInstrumentsForProjectFlow(projectId: Long): Flow<List<Sf2InstrumentEntity>>

    @Query("SELECT * FROM sf2_instruments WHERE id = :instrumentId")
    suspend fun getInstrumentById(instrumentId: Long): Sf2InstrumentEntity?

    @Insert
    suspend fun insertInstrument(instrument: Sf2InstrumentEntity): Long

    @Update
    suspend fun updateInstrument(instrument: Sf2InstrumentEntity)

    @Delete
    suspend fun deleteInstrument(instrument: Sf2InstrumentEntity)

    @Query("DELETE FROM sf2_instruments WHERE id = :instrumentId")
    suspend fun deleteInstrumentById(instrumentId: Long)

    @Query("SELECT COUNT(*) FROM sf2_instruments WHERE projectId = :projectId")
    suspend fun getInstrumentCountForProject(projectId: Long): Int

    @Query("SELECT * FROM sf2_presets WHERE instrumentId = :instrumentId ORDER BY id")
    suspend fun getPresetZonesForInstrument(instrumentId: Long): List<Sf2PresetEntity>

    @Query("SELECT * FROM sf2_presets WHERE instrumentId = :instrumentId ORDER BY id")
    fun getPresetZonesForInstrumentFlow(instrumentId: Long): Flow<List<Sf2PresetEntity>>

    @Query("SELECT COUNT(*) FROM sf2_presets WHERE instrumentId = :instrumentId")
    suspend fun getPresetZoneCountForInstrument(instrumentId: Long): Int

    // ==================== Samples ====================

    @Query("SELECT * FROM sf2_samples WHERE instrumentId = :instrumentId ORDER BY keyRangeStart")
    suspend fun getSamplesForInstrument(instrumentId: Long): List<Sf2SampleEntity>

    @Query("SELECT * FROM sf2_samples WHERE instrumentId = :instrumentId ORDER BY keyRangeStart")
    fun getSamplesForInstrumentFlow(instrumentId: Long): Flow<List<Sf2SampleEntity>>

    @Query("SELECT * FROM sf2_samples WHERE id = :sampleId")
    suspend fun getSampleById(sampleId: Long): Sf2SampleEntity?

    @Insert
    suspend fun insertSample(sample: Sf2SampleEntity): Long

    @Update
    suspend fun updateSample(sample: Sf2SampleEntity)

    @Delete
    suspend fun deleteSample(sample: Sf2SampleEntity)

    @Query("DELETE FROM sf2_samples WHERE id = :sampleId")
    suspend fun deleteSampleById(sampleId: Long)

    // ==================== Modulators ====================

    @Query("SELECT * FROM sf2_modulators WHERE programId = :programId ORDER BY id")
    suspend fun getModulatorsForProgram(programId: Long): List<Sf2ModulatorEntity>

    @Query("SELECT * FROM sf2_modulators WHERE programId = :programId ORDER BY id")
    fun getModulatorsForProgramFlow(programId: Long): Flow<List<Sf2ModulatorEntity>>

    @Query("SELECT * FROM sf2_modulators WHERE presetId = :presetId ORDER BY id")
    suspend fun getModulatorsForPreset(presetId: Long): List<Sf2ModulatorEntity>

    @Query("SELECT * FROM sf2_modulators WHERE presetId = :presetId ORDER BY id")
    fun getModulatorsForPresetFlow(presetId: Long): Flow<List<Sf2ModulatorEntity>>

    @Query("SELECT * FROM sf2_modulators WHERE instrumentId = :instrumentId ORDER BY id")
    suspend fun getModulatorsForInstrument(instrumentId: Long): List<Sf2ModulatorEntity>

    @Query("SELECT * FROM sf2_modulators WHERE instrumentId = :instrumentId ORDER BY id")
    fun getModulatorsForInstrumentFlow(instrumentId: Long): Flow<List<Sf2ModulatorEntity>>

    @Query("SELECT * FROM sf2_modulators WHERE sampleId = :sampleId ORDER BY id")
    suspend fun getModulatorsForSample(sampleId: Long): List<Sf2ModulatorEntity>

    @Query("SELECT * FROM sf2_modulators WHERE sampleId = :sampleId ORDER BY id")
    fun getModulatorsForSampleFlow(sampleId: Long): Flow<List<Sf2ModulatorEntity>>

    @Query("SELECT * FROM sf2_modulators WHERE id = :modulatorId")
    suspend fun getModulatorById(modulatorId: Long): Sf2ModulatorEntity?

    @Insert
    suspend fun insertModulator(modulator: Sf2ModulatorEntity): Long

    @Insert
    suspend fun insertModulators(modulators: List<Sf2ModulatorEntity>): List<Long>

    @Update
    suspend fun updateModulator(modulator: Sf2ModulatorEntity)

    @Delete
    suspend fun deleteModulator(modulator: Sf2ModulatorEntity)

    @Query("DELETE FROM sf2_modulators WHERE id = :modulatorId")
    suspend fun deleteModulatorById(modulatorId: Long)

    @Query("DELETE FROM sf2_modulators WHERE programId = :programId")
    suspend fun deleteModulatorsForProgram(programId: Long)

    @Query("DELETE FROM sf2_modulators WHERE presetId = :presetId")
    suspend fun deleteModulatorsForPreset(presetId: Long)

    @Query("DELETE FROM sf2_modulators WHERE instrumentId = :instrumentId")
    suspend fun deleteModulatorsForInstrument(instrumentId: Long)

    @Query("DELETE FROM sf2_modulators WHERE sampleId = :sampleId")
    suspend fun deleteModulatorsForSample(sampleId: Long)

    @Query("SELECT COUNT(*) FROM sf2_modulators WHERE programId = :programId")
    suspend fun getModulatorCountForProgram(programId: Long): Int

    @Query("SELECT COUNT(*) FROM sf2_modulators WHERE presetId = :presetId")
    suspend fun getModulatorCountForPreset(presetId: Long): Int

    @Query("SELECT COUNT(*) FROM sf2_modulators WHERE instrumentId = :instrumentId")
    suspend fun getModulatorCountForInstrument(instrumentId: Long): Int

    @Query("SELECT COUNT(*) FROM sf2_modulators WHERE sampleId = :sampleId")
    suspend fun getModulatorCountForSample(sampleId: Long): Int

    // ==================== Aggregate Queries ====================

    @Query("SELECT COUNT(*) FROM sf2_samples WHERE instrumentId = :instrumentId")
    suspend fun getSampleCountForInstrument(instrumentId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sf2_samples s
        INNER JOIN sf2_instruments i ON s.instrumentId = i.id
        WHERE i.projectId = :projectId
    """)
    suspend fun getSampleCountForProject(projectId: Long): Int

    @Query("""
        SELECT s.* FROM sf2_samples s
        INNER JOIN sf2_instruments i ON s.instrumentId = i.id
        WHERE i.projectId = :projectId
        ORDER BY i.name, s.keyRangeStart
    """)
    suspend fun getAllSamplesForProject(projectId: Long): List<Sf2SampleEntity>

    /**
     * Check if a note already exists in an instrument.
     * Returns the sample if found, null otherwise.
     */
    @Query("""
        SELECT * FROM sf2_samples
        WHERE instrumentId = :instrumentId AND rootNote = :note
        LIMIT 1
    """)
    suspend fun findSampleByNoteInInstrument(instrumentId: Long, note: Int): Sf2SampleEntity?

    /**
     * Check if a note already exists in a project.
     * Returns the sample if found, null otherwise.
     */
    @Query("""
        SELECT s.* FROM sf2_samples s
        INNER JOIN sf2_instruments i ON s.instrumentId = i.id
        WHERE i.projectId = :projectId AND s.rootNote = :note
        LIMIT 1
    """)
    suspend fun findSampleByNoteInProject(projectId: Long, note: Int): Sf2SampleEntity?

    /**
     * Find samples in an instrument whose key ranges overlap with the given range.
     * Two ranges overlap if: rangeAStart <= rangeBEnd AND rangeAEnd >= rangeBStart
     */
    @Query("""
        SELECT * FROM sf2_samples
        WHERE instrumentId = :instrumentId
        AND keyRangeStart <= :rangeEnd
        AND keyRangeEnd >= :rangeStart
    """)
    suspend fun findOverlappingSamplesInInstrument(
        instrumentId: Long,
        rangeStart: Int,
        rangeEnd: Int
    ): List<Sf2SampleEntity>

    /**
     * Find samples in a project whose key ranges overlap with the given range.
     * Two ranges overlap if: rangeAStart <= rangeBEnd AND rangeAEnd >= rangeBStart
     */
    @Query("""
        SELECT s.* FROM sf2_samples s
        INNER JOIN sf2_instruments i ON s.instrumentId = i.id
        WHERE i.projectId = :projectId
        AND s.keyRangeStart <= :rangeEnd
        AND s.keyRangeEnd >= :rangeStart
    """)
    suspend fun findOverlappingSamplesInProject(
        projectId: Long,
        rangeStart: Int,
        rangeEnd: Int
    ): List<Sf2SampleEntity>

    // ==================== Source Metadata (Hybrid Passthrough) ====================

    /**
     * Insert or replace source metadata for a project.
     * Used when importing an SF2 file to track the original source.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceMetadata(metadata: Sf2SourceMetadataEntity)

    /**
     * Get source metadata for a project.
     * Returns null if the project was created from scratch (not imported).
     */
    @Query("SELECT * FROM sf2_source_metadata WHERE projectId = :projectId")
    suspend fun getSourceMetadata(projectId: Long): Sf2SourceMetadataEntity?

    /**
     * Update the chunk registry JSON for a project.
     * Called when entities are modified to mark affected chunks.
     */
    @Query("UPDATE sf2_source_metadata SET chunkRegistry = :registry WHERE projectId = :projectId")
    suspend fun updateChunkRegistry(projectId: Long, registry: String)

    /**
     * Update the sample index mapping JSON for a project.
     * Called when sample mappings need to be updated.
     */
    @Query("UPDATE sf2_source_metadata SET sampleIndexMapping = :mapping WHERE projectId = :projectId")
    suspend fun updateSampleIndexMapping(projectId: Long, mapping: String)

    /**
     * Delete source metadata for a project.
     * Usually cascades when project is deleted, but can be called manually.
     */
    @Query("DELETE FROM sf2_source_metadata WHERE projectId = :projectId")
    suspend fun deleteSourceMetadata(projectId: Long)

    // ==================== Modification Tracking ====================

    /**
     * Mark a sample as modified by the user.
     * @param sampleId The sample ID
     * @param flags Modification flags to add (OR'd with existing)
     */
    @Query("UPDATE sf2_samples SET isModifiedByUser = 1, modificationFlags = modificationFlags | :flags WHERE id = :sampleId")
    suspend fun markSampleModified(sampleId: Long, flags: Int)

    /**
     * Mark a preset zone as modified by the user.
     * @param presetId The preset zone ID
     * @param flags Modification flags to add
     */
    @Query("UPDATE sf2_presets SET isModifiedByUser = 1, modificationFlags = modificationFlags | :flags WHERE id = :presetId")
    suspend fun markPresetModified(presetId: Long, flags: Int)

    /**
     * Mark an instrument as modified by the user.
     * @param instrumentId The instrument ID
     * @param flags Modification flags to add
     */
    @Query("UPDATE sf2_instruments SET isModifiedByUser = 1, modificationFlags = modificationFlags | :flags WHERE id = :instrumentId")
    suspend fun markInstrumentModified(instrumentId: Long, flags: Int)

    /**
     * Get all samples that have been modified since import.
     */
    @Query("SELECT * FROM sf2_samples WHERE instrumentId IN (SELECT id FROM sf2_instruments WHERE projectId = :projectId) AND isModifiedByUser = 1")
    suspend fun getModifiedSamplesForProject(projectId: Long): List<Sf2SampleEntity>

    /**
     * Check if any samples in a project have been modified.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM sf2_samples
        WHERE instrumentId IN (SELECT id FROM sf2_instruments WHERE projectId = :projectId)
        AND isModifiedByUser = 1
    """)
    suspend fun hasModifiedSamples(projectId: Long): Boolean

    /**
     * Check if any preset zones in a project have been modified.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM sf2_presets
        WHERE projectId = :projectId
        AND isModifiedByUser = 1
    """)
    suspend fun hasModifiedPresets(projectId: Long): Boolean

    /**
     * Check if any instruments in a project have been modified.
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM sf2_instruments
        WHERE projectId = :projectId
        AND isModifiedByUser = 1
    """)
    suspend fun hasModifiedInstruments(projectId: Long): Boolean

    /**
     * Check if the project has any user modifications (samples, presets, or instruments).
     * Returns true if ANY entity has been modified by the user.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sf2_samples s
            INNER JOIN sf2_instruments i ON s.instrumentId = i.id
            WHERE i.projectId = :projectId AND s.isModifiedByUser = 1
            UNION ALL
            SELECT 1 FROM sf2_presets WHERE projectId = :projectId AND isModifiedByUser = 1
            UNION ALL
            SELECT 1 FROM sf2_instruments WHERE projectId = :projectId AND isModifiedByUser = 1
        )
    """)
    suspend fun hasAnyModifications(projectId: Long): Boolean

    /**
     * Check if any new entities have been added since import.
     * MOD_FLAG_ADDED = 8
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sf2_samples s
            INNER JOIN sf2_instruments i ON s.instrumentId = i.id
            WHERE i.projectId = :projectId AND (s.modificationFlags & 8) != 0
            UNION ALL
            SELECT 1 FROM sf2_presets WHERE projectId = :projectId AND (modificationFlags & 8) != 0
            UNION ALL
            SELECT 1 FROM sf2_instruments WHERE projectId = :projectId AND (modificationFlags & 8) != 0
        )
    """)
    suspend fun hasAddedEntities(projectId: Long): Boolean

    /**
     * Check if any entities have been deleted since import.
     * MOD_FLAG_DELETED = 16
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sf2_samples s
            INNER JOIN sf2_instruments i ON s.instrumentId = i.id
            WHERE i.projectId = :projectId AND (s.modificationFlags & 16) != 0
            UNION ALL
            SELECT 1 FROM sf2_presets WHERE projectId = :projectId AND (modificationFlags & 16) != 0
            UNION ALL
            SELECT 1 FROM sf2_instruments WHERE projectId = :projectId AND (modificationFlags & 16) != 0
        )
    """)
    suspend fun hasDeletedEntities(projectId: Long): Boolean

    /**
     * Check if any audio data has been modified.
     * MOD_FLAG_AUDIO = 1
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sf2_samples s
            INNER JOIN sf2_instruments i ON s.instrumentId = i.id
            WHERE i.projectId = :projectId AND (s.modificationFlags & 1) != 0
        )
    """)
    suspend fun hasAudioModifications(projectId: Long): Boolean

    // ==================== Transactions ====================

    /**
     * Create an empty project with no default entities.
     * Used for SF2 imports where we want an exact match with the source file.
     * Returns the project ID.
     */
    @Transaction
    suspend fun createEmptyProject(projectName: String): Long {
        return insertProject(Sf2ProjectEntity(name = projectName))
    }

    /**
     * Create a new project with a default program, instrument, and preset zone.
     * Returns the project ID.
     */
    @Transaction
    suspend fun createProjectWithDefaultPreset(projectName: String, presetName: String = "Default"): Long {
        val projectId = insertProject(Sf2ProjectEntity(name = projectName))

        // Create default program (MIDI Program 0)
        val programId = insertProgram(Sf2ProgramEntity(
            projectId = projectId,
            name = "Piano",
            programNumber = 0,
            bankNumber = 0
        ))

        // Create default instrument
        val instrumentId = insertInstrument(Sf2InstrumentEntity(
            projectId = projectId,
            name = presetName
        ))

        // Create default preset zone linking program to instrument
        insertPreset(Sf2PresetEntity(
            projectId = projectId,
            programId = programId,
            instrumentId = instrumentId,
            name = presetName,
            programNumber = 0,
            bankNumber = 0
        ))
        return projectId
    }

    /**
     * Get the default (first) instrument for a project.
     * Creates one if it doesn't exist.
     */
    @Transaction
    suspend fun getOrCreateDefaultInstrument(projectId: Long): Sf2InstrumentEntity {
        val instruments = getInstrumentsForProject(projectId)
        return if (instruments.isNotEmpty()) {
            instruments.first()
        } else {
            val instrumentId = insertInstrument(Sf2InstrumentEntity(
                projectId = projectId,
                name = "Default"
            ))
            getInstrumentById(instrumentId)!!
        }
    }

    /**
     * Get the default (first) preset zone for a project.
     * Creates one if it doesn't exist.
     */
    @Transaction
    suspend fun getOrCreateDefaultPreset(projectId: Long): Sf2PresetEntity {
        val presets = getPresetsForProject(projectId)
        return if (presets.isNotEmpty()) {
            presets.first()
        } else {
            // Ensure there's a program first
            val programs = getProgramsForProject(projectId)
            val programId = if (programs.isEmpty()) {
                insertProgram(Sf2ProgramEntity(
                    projectId = projectId,
                    name = "Piano",
                    programNumber = 0,
                    bankNumber = 0
                ))
            } else {
                programs.first().id
            }

            // Ensure there's an instrument
            val instrument = getOrCreateDefaultInstrument(projectId)

            val presetId = insertPreset(Sf2PresetEntity(
                projectId = projectId,
                programId = programId,
                instrumentId = instrument.id,
                name = "Default",
                programNumber = 0,
                bankNumber = 0
            ))
            getPresetById(presetId)!!
        }
    }
}
