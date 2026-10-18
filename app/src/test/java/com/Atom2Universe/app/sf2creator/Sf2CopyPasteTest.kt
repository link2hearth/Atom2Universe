package com.Atom2Universe.app.sf2creator

import com.Atom2Universe.app.sf2creator.Sf2TestBank.describe
import com.Atom2Universe.app.sf2creator.Sf2TestBank.pianoLeft
import com.Atom2Universe.app.sf2creator.Sf2TestBank.range
import com.Atom2Universe.app.sf2creator.Sf2TestBank.tone
import com.Atom2Universe.app.sf2creator.data.PitchResult
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectClipboard
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectCopier
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2Reader
import com.Atom2Universe.app.sf2creator.ui.Sf2ProjectActions
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Copier-coller d'un projet à l'autre : ce qui est collé joue comme l'original et s'exporte
 * avec les mêmes réglages, l'audio reste disponible quand le projet d'origine disparaît.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Sf2CopyPasteTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var dir: File
    private lateinit var source: File
    private val repository get() = Sf2ProjectRepository(context)
    private val copier get() = Sf2ProjectCopier(context)

    @Before
    fun setUp() {
        Sf2TestBank.resetDatabase()
        dir = File(context.cacheDir, "copypaste").apply { deleteRecursively(); mkdirs() }
        source = Sf2TestBank.write(File(dir, "source.sf2"))
        Sf2ProjectClipboard.clear(context)
    }

    @After
    fun tearDown() = Sf2TestBank.resetDatabase()

    private fun importSource(): Long = runBlocking {
        val projectId = repository.createEmptyProject("source")
        val importer = Sf2Importer(context)
        val parsed = importer.parseFile(source)!!
        importer.importPresets(parsed, parsed.presets.indices.toList(), projectId)
        projectId
    }

    private fun export(projectId: Long): File = runBlocking {
        val out = File(dir, "export-${System.nanoTime()}.sf2")
        assertTrue(repository.exportProjectToSf2(projectId, out))
        Sf2TestBank.assertRiffSizesConsistent(out)
        out
    }

    private fun Map<String, String>.withoutInfo() = filterKeys { !it.startsWith("info ") }

    @Test
    fun programsCopiedIntoAnotherProjectAreExportedTheSame() {
        val a = importSource()
        val b = runBlocking {
            val b = repository.createEmptyProject("B")
            copier.copyPrograms(repository.getProgramsForProject(a).map { it.id }, b)
            b
        }
        assertEquals(describe(source).withoutInfo(), describe(export(b)).withoutInfo())
    }

    @Test
    fun pastedProgramTakesAFreeNumber() {
        val a = importSource()
        runBlocking {
            val piano = repository.getProgramsForProject(a).first { it.programNumber == 0 }
            copier.copyPrograms(listOf(piano.id), a)
            val numbers = repository.getProgramsForProject(a).filter { it.bankNumber == 0 }.map { it.programNumber }.sorted()
            assertEquals(listOf(0, 1), numbers)
        }
    }

    @Test
    fun instrumentPastedIntoAnotherProgramKeepsItsSettings() {
        val a = importSource()
        val b = runBlocking {
            val b = repository.createProject("B")
            val zone = repository.getPresetsForProject(a).first { it.pgenAttenuation == 20 }
            copier.copyInstruments(listOf(zone.id), repository.getProgramsForProject(b).single().id)
            b
        }
        val before = describe(source)
        val after = describe(export(b))
        for (key in listOf("", " inst global", " inst zone 0", " inst zone 0 sample", " inst zone 1", " inst zone 1 sample")) {
            assertEquals(key, before["preset 0:0 zone 0$key"], after["preset 0:0 zone 0$key"])
        }
    }

    @Test
    fun samplePastedIntoAnotherInstrumentPlaysAsItDid() {
        val a = importSource()
        val b = runBlocking {
            val b = repository.createProject("B")
            val left = repository.getAllSamplesForProject(a).first { it.name == "Piano L" }
            copier.copySamples(listOf(left.id), repository.getOrCreateDefaultPreset(b).instrumentId)
            b
        }
        val reader = Sf2Reader()
        val result = reader.parse(export(b))!!
        val zone = result.instruments.single().zones.single()
        // Le mode de boucle venait de la zone globale de l'instrument d'origine
        assertEquals(mapOf(17 to -500, 43 to range(21, 64), 54 to 1), zone.generators - 53)
        val sample = result.samples[zone.sampleIndex]
        assertArrayEquals(pianoLeft, reader.extractSampleAudio(sample))
        assertEquals("seul, le canal gauche devient mono", 1, sample.sampleType)
        assertEquals(500L, sample.loopStart - sample.start)
    }

    @Test
    fun duplicatedProjectOutlivesTheOriginal() {
        val a = importSource()
        val copy = runBlocking { copier.duplicateProject(a, "Copie")!! }
        val sourceCopy = File(runBlocking { repository.getSourceMetadata(a)!!.sourceFilePath!! })
        val exported = describe(export(copy))
        assertEquals(describe(source).withoutInfo(), exported.withoutInfo())
        assertEquals("Copie", exported["info INAM"])
        assertEquals("CC-BY", exported["info ICOP"])

        runBlocking { repository.deleteProject(a) }
        assertTrue("le SF2 importé sert encore à la copie", sourceCopy.exists())
        assertEquals(describe(source).withoutInfo(), describe(export(copy)).withoutInfo())

        runBlocking { repository.deleteProject(copy) }
        assertFalse(sourceCopy.exists())
    }

    @Test
    fun recordedAudioIsCopiedWithItsSample() {
        val recorded = tone(3000, 33.0)
        val b = runBlocking {
            val a = repository.createProject("A")
            val id = repository.addSampleToInstrument(repository.getOrCreateDefaultPreset(a).instrumentId, "Voix", recorded, 44100, rootNote = 64)
            val b = repository.createProject("B")
            copier.copySamples(listOf(id), repository.getOrCreateDefaultPreset(b).instrumentId)
            repository.deleteProject(a)
            b
        }
        val reader = Sf2Reader()
        val result = reader.parse(export(b))!!
        assertArrayEquals(recorded, reader.extractSampleAudio(result.samples.single()))
    }

    @Test
    fun clipboardPastesIntoTheRightPlace() {
        val a = importSource()
        runBlocking {
            val samples = repository.getAllSamplesForProject(a)
            Sf2ProjectClipboard.set(context, Sf2ProjectClipboard.Content(Sf2ProjectClipboard.Kind.SAMPLES, a, samples.map { it.id }, "3 samples"))
            val content = Sf2ProjectClipboard.get(context)!!
            assertEquals(Sf2ProjectClipboard.Kind.SAMPLES, content.kind)
            assertEquals(samples.map { it.id }, content.ids)
            assertEquals("3 samples", content.label)

            // Des sons se collent dans un instrument
            assertTrue(Sf2ProjectActions.paste(context, a, null, null) is Sf2ProjectActions.PasteOutcome.NeedsInstrument)
            val b = repository.createProject("B")
            val instrument = repository.getOrCreateDefaultPreset(b).instrumentId
            val outcome = Sf2ProjectActions.paste(context, b, null, instrument)
            assertEquals(3, (outcome as Sf2ProjectActions.PasteOutcome.Pasted).ids.size)
            assertEquals(3, repository.getSamplesForInstrument(instrument).size)

            // Ce qui a été supprimé entre-temps ne se colle plus
            repository.deleteProject(a)
            assertTrue(Sf2ProjectActions.paste(context, b, null, instrument) is Sf2ProjectActions.PasteOutcome.Gone)
        }
    }

    @Test
    fun audioFilesAreSpreadOverTheKeyboard() {
        runBlocking {
            val b = repository.createProject("B")
            val instrument = repository.getOrCreateDefaultPreset(b).instrumentId
            fun sound(name: String, note: Int, cents: Int) = Sf2ProjectRepository.AudioFileSound(
                name, tone(1000, 50.0), 44100,
                PitchResult(440f, note, name, 0.9f, cents)
            )
            repository.addAudioFiles(instrument, listOf(sound("Do", 60, 0), sound("La", 69, 12), sound("Mi", 64, -7)))
            val samples = repository.getSamplesForInstrument(instrument).associateBy { it.name }
            assertEquals(listOf(0, 62), samples.getValue("Do").let { listOf(it.keyRangeStart, it.keyRangeEnd) })
            assertEquals(listOf(63, 66), samples.getValue("Mi").let { listOf(it.keyRangeStart, it.keyRangeEnd) })
            assertEquals(listOf(67, 127), samples.getValue("La").let { listOf(it.keyRangeStart, it.keyRangeEnd) })
            // Une note un peu trop haute est corrigée dans l'échantillon
            assertEquals(-12, samples.getValue("La").pitchCorrection)
        }
    }
}
