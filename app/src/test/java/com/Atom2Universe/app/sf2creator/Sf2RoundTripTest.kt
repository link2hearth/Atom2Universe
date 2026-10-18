package com.Atom2Universe.app.sf2creator

import com.Atom2Universe.app.sf2creator.Sf2TestBank.assertRiffSizesConsistent
import com.Atom2Universe.app.sf2creator.Sf2TestBank.assertSameExcept
import com.Atom2Universe.app.sf2creator.Sf2TestBank.bell
import com.Atom2Universe.app.sf2creator.Sf2TestBank.describe
import com.Atom2Universe.app.sf2creator.Sf2TestBank.pianoRight
import com.Atom2Universe.app.sf2creator.Sf2TestBank.range
import com.Atom2Universe.app.sf2creator.data.Sf2EditNoise
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2Reader
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Un SF2 importé dans le créateur puis exporté doit se relire comme l'original : générateurs
 * de chaque zone (y compris globales), modulateurs, en-têtes d'échantillons, liens stéréo,
 * audio et champs INFO. Seules les modifications faites dans l'application changent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Sf2RoundTripTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var dir: File

    @Before
    fun setUp() {
        Sf2TestBank.resetDatabase()
        dir = File(context.cacheDir, "roundtrip").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = Sf2TestBank.resetDatabase()

    private fun writeSource(): File = Sf2TestBank.write(File(dir, "source.sf2"))

    private class Imported(val repository: Sf2ProjectRepository, val projectId: Long)

    private fun import(source: File, projectName: String = "source"): Imported = runBlocking {
        val repository = Sf2ProjectRepository(context)
        val projectId = repository.createEmptyProject(projectName)
        val importer = Sf2Importer(context)
        val parsed = importer.parseFile(source)!!
        assertEquals(parsed.presets.size, importer.importPresets(parsed, parsed.presets.indices.toList(), projectId))
        Imported(repository, projectId)
    }

    private fun export(imported: Imported): File = runBlocking {
        val out = File(dir, "export-${System.nanoTime()}.sf2")
        assertTrue(imported.repository.exportProjectToSf2(imported.projectId, out))
        assertRiffSizesConsistent(out)
        out
    }

    // ==================== Tests ====================

    @Test
    fun unchangedImportIsExportedAsTheOriginalFile() {
        val source = writeSource()
        val exported = export(import(source))
        assertArrayEquals(source.readBytes(), exported.readBytes())
    }

    @Test
    fun rebuiltFileKeepsEverythingThatWasNotEdited() {
        val source = writeSource()
        val imported = import(source)
        runBlocking {
            val program = imported.repository.getProgramsForProject(imported.projectId).first { it.programNumber == 0 }
            imported.repository.updateProgram(program.copy(name = "Piano renommé"))
        }
        val before = describe(source)
        val after = describe(export(imported))
        // Avant : le renommage d'un programme n'était pas détecté, l'export recopiait l'original
        assertSameExcept(
            before, after,
            "preset 0:0 name" to "Piano renommé",
            "info ISFT" to "Polyphone:A2U SF2 Creator"
        )
    }

    @Test
    fun editedValuesReplaceOnlyWhatChanged() {
        val source = writeSource()
        val imported = import(source)
        runBlocking {
            val samples = imported.repository.getAllSamplesForProject(imported.projectId)
            val bellZone = samples.first { it.name == "Cloche à vent" }
            imported.repository.updateSample(bellZone.copy(volEnvRelease = 1200, attenuation = 60))
            val instrument = imported.repository.getInstrumentById(bellZone.instrumentId)!!
            imported.repository.updateInstrument(instrument.copy(globalVolEnvDecay = 2400))
            val presetZone = imported.repository.getPresetsForProject(imported.projectId).first { it.pgenVelRangeHigh == 90 }
            imported.repository.updatePreset(presetZone.copy(pgenVelRangeHigh = 127, pgenPan = 100))
        }
        val before = describe(source)
        val after = describe(export(imported))
        assertSameExcept(
            before, after,
            "info ISFT" to "Polyphone:A2U SF2 Creator",
            "preset 0:0 zone 1" to "17=100  -> Cloche",
            "preset 0:0 zone 1 inst global" to "36=2400 ",
            "preset 0:0 zone 1 inst zone 0" to "8=13500 38=1200 44=25600 48=60 54=0 ",
            "preset 128:9 zone 0 inst global" to "36=2400 ",
            "preset 128:9 zone 0 inst zone 0" to "8=13500 38=1200 44=25600 48=60 54=0 "
        )
    }

    @Test
    fun editedLoopBecomesZoneOffsetsAndKeepsTheSampleHeader() {
        val source = writeSource()
        val imported = import(source)
        runBlocking {
            val left = imported.repository.getAllSamplesForProject(imported.projectId).first { it.name == "Piano L" }
            assertEquals(500, left.loopStart)
            assertEquals(1499, left.loopEnd)
            imported.repository.updateSample(left.copy(loopStart = 520, loopEnd = 1399))
        }
        val after = describe(export(imported))
        assertEquals("2=20 3=-100 17=-500 43=16405 ", after["preset 0:0 zone 0 inst zone 0"])
        assertEquals(describe(source)["preset 0:0 zone 0 inst zone 0 sample"], after["preset 0:0 zone 0 inst zone 0 sample"])
    }

    @Test
    fun editedAudioBecomesItsOwnSample() {
        val source = writeSource()
        val imported = import(source)
        val edited = pianoRight.copyOf(1800)
        runBlocking {
            val right = imported.repository.getAllSamplesForProject(imported.projectId).first { it.name == "Piano R" }
            assertArrayEquals(pianoRight, imported.repository.loadSampleAudio(right))
            // Avant : l'éditeur écrivait dans un WAV au chemin vide et perdait tout
            val saved = imported.repository.saveEditedSampleAudio(right, edited)
            assertNotNull(saved)
            imported.repository.updateSample(saved!!.copy(volEnvAttack = -2400))
        }
        val reader = Sf2Reader()
        val result = reader.parse(export(imported))!!
        val right = result.samples.first { it.name == "Piano R" }
        assertArrayEquals(edited, reader.extractSampleAudio(right))
        assertEquals(1, right.sampleType)
        // Le canal gauche a perdu son partenaire : il devient mono
        assertEquals(1, result.samples.first { it.name == "Piano L" }.sampleType)
        val zone = result.instruments.first { it.name == "Piano" }.zones[1]
        assertFalse("décalage de début d'un audio qui a changé", zone.generators.containsKey(0))
        assertEquals(-2400, zone.generators[34])
        assertEquals(0, zone.generators[48])
    }

    @Test
    fun secondImportIntoAProjectKeepsZoneSettingsWithWavCopies() {
        val source = writeSource()
        val imported = import(source)
        runBlocking {
            val importer = Sf2Importer(context)
            val parsed = importer.parseFile(source)!!
            importer.importPresets(parsed, listOf(1), imported.projectId)
        }
        val reader = Sf2Reader()
        val result = reader.parse(export(imported))!!
        val bells = result.instruments.filter { it.name == "Cloche" }
        assertEquals(2, bells.size)
        for (instrument in bells) {
            assertEquals(mapOf(8 to 13500, 38 to -12000, 44 to range(0, 100), 54 to 0), instrument.zones.single().generators - 53)
            assertArrayEquals(bell, reader.extractSampleAudio(result.samples[instrument.zones.single().sampleIndex]))
        }
    }

    @Test
    fun importedZonesShowWhatTheyPlay() {
        val imported = import(writeSource())
        runBlocking {
            val left = imported.repository.getAllSamplesForProject(imported.projectId).first { it.name == "Piano L" }
            // Boucle et note de base viennent de la zone globale de l'instrument
            assertTrue(left.hasLoop)
            assertEquals(60, left.rootNote)
            assertEquals(-5, left.pitchCorrection)
            val piano = imported.repository.getInstrumentById(left.instrumentId)!!
            assertEquals(100, piano.globalAttenuation)
            val presetZone = imported.repository.getPresetsForProject(imported.projectId).first { it.instrumentId == piano.id }
            assertEquals(20, presetZone.pgenAttenuation)
        }
    }

    @Test
    fun databaseFromVersion15KeepsItsProjects() {
        context.deleteDatabase("sf2_projects.db")
        val file = context.getDatabasePath("sf2_projects.db").apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            javaClass.getResource("/sf2creator/schema_v15.sql")!!.readText().lines()
                .filter { it.isNotBlank() }
                .forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO sf2_projects (name, isLegacyProject, createdAt, modifiedAt) VALUES ('Ancien projet', 0, 0, 0)")
            db.version = 15
        }
        val projects = runBlocking { Sf2ProjectRepository(context).getAllProjects() }
        assertEquals(listOf("Ancien projet"), projects.map { it.name })
    }

    @Test
    fun dialogRoundTripsDoNotChangeStoredValues() {
        val imported = import(writeSource())
        runBlocking {
            val bellZone = imported.repository.getAllSamplesForProject(imported.projectId).first { it.name == "Cloche à vent" }
            val stored = bellZone.copy(volEnvAttack = -8000, volEnvSustain = 345, filterFc = 9001)
            imported.repository.updateSample(stored)
            // Ce que le dialogue renvoie sans qu'on touche aux réglages
            val dialog = stored.copy(
                volEnvAttack = Sf2UnitConverter.msToTimecents(Sf2UnitConverter.timecentsToMs(stored.volEnvAttack)),
                volEnvSustain = Sf2UnitConverter.sustainPercentToCentibels(Sf2UnitConverter.centibelsToSustainPercent(stored.volEnvSustain)),
                filterFc = Sf2UnitConverter.hzToFilterCents(Sf2UnitConverter.filterCentsToHz(stored.filterFc))
            )
            assertTrue(dialog.volEnvAttack != stored.volEnvAttack && dialog.volEnvSustain != stored.volEnvSustain)
            imported.repository.updateSample(dialog)
            val saved = imported.repository.getAllSamplesForProject(imported.projectId).first { it.id == bellZone.id }
            assertEquals(-8000, saved.volEnvAttack)
            assertEquals(345, saved.volEnvSustain)
            assertEquals(9001, saved.filterFc)
            // Une vraie modification passe
            assertEquals(-1200, Sf2EditNoise.keepUnedited(saved, saved.copy(volEnvAttack = -1200)).volEnvAttack)
        }
    }
}
