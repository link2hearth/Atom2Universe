package com.Atom2Universe.app.sf2creator.migration

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.sf2creator.data.Sf2PatchService
import com.Atom2Universe.app.sf2creator.data.db.Sf2IndexDao
import com.Atom2Universe.app.sf2creator.data.db.Sf2PatchDao
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDao
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2IndexEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2LazyReader
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import java.io.File

/**
 * Handles migration of legacy projects to the new "Polyphone-like" architecture.
 *
 * Legacy projects (created before v14) store full data in the database:
 * - Sample audio data in files
 * - All parameters in DB entities
 *
 * New projects (v14+) use the SF2 file as source of truth:
 * - SF2 file stored locally
 * - Only modifications (patches) stored in DB
 * - Lightweight index for navigation
 *
 * Migration strategy:
 * 1. If project has sourceFilePath and file exists:
 *    - Build index from SF2 file
 *    - Calculate differences between DB state and SF2
 *    - Convert differences to patches
 *
 * 2. If no source SF2 (project created from scratch):
 *    - Export current state to SF2 file
 *    - That SF2 becomes the new source
 *    - Clear patches (SF2 now matches DB state)
 *
 * 3. Mark project as migrated (isLegacyProject = false)
 */
class ProjectMigrator(
    private val context: Context,
    private val projectDao: Sf2ProjectDao,
    private val patchDao: Sf2PatchDao,
    private val indexDao: Sf2IndexDao
) {

    companion object {
        private const val TAG = "ProjectMigrator"

        // Directory for storing SF2 source files
        private const val SF2_SOURCES_DIR = "sf2_sources"
    }

    private val patchService = Sf2PatchService(patchDao)
    private val sf2Writer = Sf2Writer()

    /**
     * Get the directory where SF2 source files are stored.
     */
    fun getSourcesDirectory(): File {
        val dir = File(context.filesDir, SF2_SOURCES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Check if a project needs migration.
     */
    suspend fun needsMigration(projectId: Long): Boolean {
        val project = projectDao.getProjectById(projectId) ?: return false
        return project.isLegacyProject
    }

    /**
     * Migrate a legacy project to the new architecture.
     *
     * @param projectId The project to migrate
     * @return true if migration succeeded
     */
    suspend fun migrateProject(projectId: Long): Boolean {
        val project = projectDao.getProjectById(projectId)
        if (project == null) {
            Log.e(TAG, "Project $projectId not found")
            return false
        }

        if (!project.isLegacyProject) {
            Log.d(TAG, "Project $projectId is already migrated")
            return true
        }

        Log.d(TAG, "Starting migration for project '${project.name}' ($projectId)")

        return try {
            if (project.sourceFilePath != null && File(project.sourceFilePath).exists()) {
                // Case 1: Has source SF2 file
                migrateWithExistingSource(project)
            } else {
                // Case 2: No source file, need to create one
                migrateWithNewSource(project)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed for project $projectId", e)
            false
        }
    }

    /**
     * Migrate a project that already has a source SF2 file.
     * Build index and calculate patches from differences.
     */
    private suspend fun migrateWithExistingSource(project: Sf2ProjectEntity): Boolean {
        val sourceFile = File(project.sourceFilePath!!)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${project.sourceFilePath}")
            return migrateWithNewSource(project)
        }

        val reader = Sf2LazyReader(sourceFile.absolutePath)
        val parseResult = reader.getParseResult()
        if (parseResult == null) {
            Log.e(TAG, "Failed to parse source file")
            return false
        }

        // Build index from SF2 file
        buildIndex(project.id, reader)

        // TODO: Calculate differences between DB state and SF2
        // For now, we just mark the project as migrated without generating patches
        // The assumption is that imported projects haven't been heavily modified

        // Mark as migrated
        projectDao.updateProject(project.copy(isLegacyProject = false))

        Log.d(TAG, "Migration complete for '${project.name}' (with existing source)")
        return true
    }

    /**
     * Migrate a project by creating a new SF2 source file from DB state.
     */
    private suspend fun migrateWithNewSource(project: Sf2ProjectEntity): Boolean {
        // Export current state to a new SF2 file
        val sourcesDir = getSourcesDirectory()
        val sourceFile = File(sourcesDir, "${project.name.sanitizeFileName()}_${project.id}.sf2")

        Log.d(TAG, "Creating new source file: ${sourceFile.absolutePath}")

        // Get all samples for the project
        val samples = projectDao.getAllSamplesForProject(project.id)
        if (samples.isEmpty()) {
            Log.w(TAG, "No samples in project, creating empty source")
            // Create minimal valid SF2 file
            // For now, just mark as migrated with null source
            projectDao.updateProject(project.copy(
                isLegacyProject = false
            ))
            return true
        }

        // Convert DB samples to SampleData for export
        val sampleDataList = samples.mapNotNull { sample ->
            try {
                val audioFile = File(sample.audioFilePath)
                if (!audioFile.exists()) {
                    Log.w(TAG, "Audio file not found: ${sample.audioFilePath}")
                    return@mapNotNull null
                }

                com.Atom2Universe.app.sf2creator.data.SampleData(
                    audioFile = audioFile,
                    sampleRate = sample.sampleRate,
                    rootNote = sample.rootNote,
                    keyRangeStart = sample.keyRangeStart,
                    keyRangeEnd = sample.keyRangeEnd,
                    loopStart = sample.loopStart,
                    loopEnd = sample.loopEnd,
                    hasLoop = sample.hasLoop,
                    sampleModes = sample.sampleModes,
                    name = sample.name,
                    attenuation = sample.attenuation,
                    fineTuneCents = sample.fineTuneCents,
                    volEnvAttack = sample.volEnvAttack,
                    volEnvDecay = sample.volEnvDecay,
                    volEnvSustain = sample.volEnvSustain,
                    volEnvRelease = sample.volEnvRelease,
                    filterFc = sample.filterFc,
                    filterQ = sample.filterQ,
                    chorusSend = sample.chorusSend,
                    reverbSend = sample.reverbSend,
                    pan = sample.pan
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert sample ${sample.id}", e)
                null
            }
        }

        if (sampleDataList.isEmpty()) {
            Log.w(TAG, "No valid samples to export")
            projectDao.updateProject(project.copy(isLegacyProject = false))
            return true
        }

        // Export to SF2
        val success = sf2Writer.writeSf2(
            outputFile = sourceFile,
            instrumentName = project.name,
            samples = sampleDataList,
            presetNumber = 0,
            bankNumber = 0
        )

        if (!success) {
            Log.e(TAG, "Failed to write SF2 file")
            return false
        }

        // Build index from the new SF2 file
        val reader = Sf2LazyReader(sourceFile.absolutePath)
        buildIndex(project.id, reader)

        // Update project with new source file path
        projectDao.updateProject(project.copy(
            sourceFilePath = sourceFile.absolutePath,
            isLegacyProject = false
        ))

        Log.d(TAG, "Migration complete for '${project.name}' (created new source)")
        return true
    }

    /**
     * Build the lightweight index from an SF2 file.
     */
    private suspend fun buildIndex(projectId: Long, reader: Sf2LazyReader) {
        // Clear existing index
        indexDao.deleteIndexForProject(projectId)

        val parseResult = reader.getParseResult() ?: return

        val indexEntries = mutableListOf<Sf2IndexEntity>()

        // Index presets
        for (preset in parseResult.presets) {
            indexEntries.add(Sf2IndexEntity(
                projectId = projectId,
                elementType = Sf2IndexEntity.TYPE_PRESET,
                originalIndex = preset.index,
                name = preset.name,
                programNumber = preset.programNumber,
                bankNumber = preset.bankNumber
            ))
        }

        // Index instruments
        for (instrument in parseResult.instruments) {
            indexEntries.add(Sf2IndexEntity(
                projectId = projectId,
                elementType = Sf2IndexEntity.TYPE_INSTRUMENT,
                originalIndex = instrument.index,
                name = instrument.name
            ))
        }

        // Index samples
        for (sample in parseResult.samples) {
            indexEntries.add(Sf2IndexEntity(
                projectId = projectId,
                elementType = Sf2IndexEntity.TYPE_SAMPLE,
                originalIndex = sample.index,
                name = sample.name,
                rootNote = sample.originalPitch,
                sampleRate = sample.sampleRate
            ))
        }

        // Insert all entries
        indexDao.insertIndexEntries(indexEntries)

        Log.d(TAG, "Built index with ${indexEntries.size} entries for project $projectId")
    }

    /**
     * Copy an SF2 file to the sources directory.
     * Returns the path to the copied file.
     */
    fun copyToSourcesDirectory(sourceFile: File, projectName: String): File {
        val sourcesDir = getSourcesDirectory()
        val destFile = File(sourcesDir, "${projectName.sanitizeFileName()}_${System.currentTimeMillis()}.sf2")
        sourceFile.copyTo(destFile, overwrite = true)
        return destFile
    }

    /**
     * Delete the source file for a project.
     * Called when a project is deleted.
     */
    fun deleteSourceFile(sourceFilePath: String?) {
        if (sourceFilePath == null) return
        val file = File(sourceFilePath)
        if (file.exists() && file.parentFile?.absolutePath == getSourcesDirectory().absolutePath) {
            file.delete()
            Log.d(TAG, "Deleted source file: $sourceFilePath")
        }
    }

    /**
     * Sanitize a string for use as a filename.
     */
    private fun String.sanitizeFileName(): String {
        return this.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
    }
}
