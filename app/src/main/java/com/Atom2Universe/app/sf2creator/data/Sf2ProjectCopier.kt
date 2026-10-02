package com.Atom2Universe.app.sf2creator.data

import android.content.Context
import androidx.room.withTransaction
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies programs, instruments and samples, inside a project or into another one.
 *
 * Copies are complete: every parameter, the generators kept from an imported SF2 and the
 * modulators come along. Audio recorded or edited in the app is copied into the target
 * project; audio still read from an imported SF2 keeps reading it (the file is kept as long
 * as a project uses it), so stereo pairs stay linked at export.
 */
class Sf2ProjectCopier(context: Context) {

    private val database = Sf2ProjectDatabase.getInstance(context)
    private val dao = database.projectDao()
    private val samplesDir = File(context.filesDir, "sf2_samples")

    /** One paste: an instrument used by several copied zones is copied once. */
    private inner class Paste(val targetProjectId: Long) {
        private val instruments = HashMap<Long, Long>()

        suspend fun instrument(sourceId: Long): Long? {
            instruments[sourceId]?.let { return it }
            val source = dao.getInstrumentById(sourceId) ?: return null
            val id = dao.insertInstrument(source.copy(id = 0, projectId = targetProjectId))
            val mods = dao.getModulatorsForInstrument(sourceId).filter { it.sampleId == null }
            if (mods.isNotEmpty()) dao.insertModulators(mods.map { it.copy(id = 0, instrumentId = id) })
            for (zone in dao.getSamplesForInstrument(sourceId).sortedBy { it.id }) {
                sample(zone, id, detach = false)
            }
            instruments[sourceId] = id
            return id
        }

        suspend fun sample(source: Sf2SampleEntity, targetInstrumentId: Long, detach: Boolean): Long {
            val zone = if (detach) Sf2ZoneGenerators.detachSampleZone(source) else source
            val id = dao.insertSample(zone.copy(id = 0, instrumentId = targetInstrumentId, audioFilePath = copyAudio(source)))
            val mods = dao.getModulatorsForSample(source.id)
            if (mods.isNotEmpty()) dao.insertModulators(mods.map { it.copy(id = 0, sampleId = id) })
            return id
        }

        suspend fun presetZone(source: Sf2PresetEntity, program: Sf2ProgramEntity?, detach: Boolean): Long? {
            val instrumentId = instrument(source.instrumentId) ?: return null
            val zone = if (detach) Sf2ZoneGenerators.detachPresetZone(source) else source
            val id = dao.insertPreset(
                zone.copy(
                    id = 0,
                    projectId = targetProjectId,
                    programId = program?.id,
                    instrumentId = instrumentId,
                    programNumber = program?.programNumber ?: source.programNumber,
                    bankNumber = program?.bankNumber ?: source.bankNumber
                )
            )
            val mods = dao.getModulatorsForPreset(source.id)
            if (mods.isNotEmpty()) dao.insertModulators(mods.map { it.copy(id = 0, presetId = id) })
            return id
        }

        suspend fun program(source: Sf2ProgramEntity, keepNumber: Boolean): Long {
            val number = if (keepNumber) source.programNumber else freeProgramNumber(source.programNumber, source.bankNumber)
            val id = dao.insertProgram(source.copy(id = 0, projectId = targetProjectId, programNumber = number))
            val program = dao.getProgramById(id)!!
            val mods = dao.getModulatorsForProgram(source.id)
            if (mods.isNotEmpty()) dao.insertModulators(mods.map { it.copy(id = 0, programId = id) })
            for (zone in dao.getPresetZonesForProgram(source.id).sortedBy { it.id }) {
                presetZone(zone, program, detach = false)
            }
            return id
        }

        /** The program number, or the next one free in its bank, so that both stay playable. */
        private suspend fun freeProgramNumber(wanted: Int, bank: Int): Int {
            val used = dao.getProgramsForProject(targetProjectId).filter { it.bankNumber == bank }.map { it.programNumber }.toSet()
            return (0..127).map { (wanted + it) % 128 }.firstOrNull { it !in used } ?: wanted
        }

        /** Audio in a WAV file gets its own copy in the target project. */
        private fun copyAudio(sample: Sf2SampleEntity): String {
            val source = File(sample.audioFilePath)
            if (sample.audioFilePath.isEmpty() || !source.isFile) return sample.audioFilePath
            val dir = File(samplesDir, targetProjectId.toString()).also { it.mkdirs() }
            val target = File(dir, "${System.nanoTime()}_${source.name.substringAfter('_')}")
            source.copyTo(target)
            return target.absolutePath
        }
    }

    /** Copies whole programs (preset zones, instruments, samples) into [targetProjectId]. */
    suspend fun copyPrograms(programIds: List<Long>, targetProjectId: Long): List<Long> = transaction {
        val paste = Paste(targetProjectId)
        val ids = programIds.mapNotNull { dao.getProgramById(it) }.map { paste.program(it, keepNumber = false) }
        dao.updateProjectModifiedAt(targetProjectId)
        ids
    }

    /** Copies instruments (preset zones with their instrument) into the program [targetProgramId]. */
    suspend fun copyInstruments(presetZoneIds: List<Long>, targetProgramId: Long): List<Long> = transaction {
        val program = dao.getProgramById(targetProgramId) ?: return@transaction emptyList()
        val paste = Paste(program.projectId)
        val ids = presetZoneIds.mapNotNull { dao.getPresetById(it) }.mapNotNull { zone ->
            paste.presetZone(zone, program, detach = zone.programId != targetProgramId)
        }
        dao.updateProjectModifiedAt(program.projectId)
        ids
    }

    /** Copies samples (instrument zones) into the instrument [targetInstrumentId]. */
    suspend fun copySamples(sampleIds: List<Long>, targetInstrumentId: Long): List<Long> = transaction {
        val instrument = dao.getInstrumentById(targetInstrumentId) ?: return@transaction emptyList()
        val paste = Paste(instrument.projectId)
        val ids = sampleIds.mapNotNull { dao.getSampleById(it) }.map { zone ->
            paste.sample(zone, targetInstrumentId, detach = zone.instrumentId != targetInstrumentId)
        }
        dao.updateProjectModifiedAt(instrument.projectId)
        ids
    }

    /** A new project holding a copy of everything in [projectId]. */
    suspend fun duplicateProject(projectId: Long, name: String): Long? = transaction {
        dao.getProjectById(projectId) ?: return@transaction null
        val copyId = dao.insertProject(Sf2ProjectEntity(name = name))
        val paste = Paste(copyId)
        for (program in dao.getProgramsForProject(projectId).sortedBy { it.id }) paste.program(program, keepNumber = true)
        // Preset zones of projects made before programs existed
        for (zone in dao.getPresetZonesForExport(projectId).filter { it.programId == null }) {
            paste.presetZone(zone, null, detach = false)
        }
        // Instruments no preset uses yet
        for (instrument in dao.getInstrumentsForExport(projectId)) paste.instrument(instrument.id)
        // Same INFO fields at export, under the copy's own name; no exact-copy shortcut
        dao.getSourceMetadata(projectId)?.let { source ->
            if (source.sourceFilePath != null) {
                dao.insertSourceMetadata(
                    Sf2SourceMetadataEntity(projectId = copyId, sourceFilePath = source.sourceFilePath, importProjectName = source.importProjectName)
                )
            }
        }
        copyId
    }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { database.withTransaction { block() } }
}
