package com.Atom2Universe.app.sf2creator

import com.Atom2Universe.app.sf2creator.data.Sf2EditNoise
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.sf2creator.reader.Sf2Importer
import com.Atom2Universe.app.sf2creator.reader.Sf2InfoField
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedModulator
import com.Atom2Universe.app.sf2creator.reader.Sf2Reader
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter
import com.Atom2Universe.app.sf2creator.writer.Sf2Document
import com.Atom2Universe.app.sf2creator.writer.Sf2Document.Modulator
import com.Atom2Universe.app.sf2creator.writer.Sf2Document.Zone
import com.Atom2Universe.app.sf2creator.writer.Sf2DocumentWriter
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sin
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
        resetDatabase()
        dir = File(context.cacheDir, "roundtrip").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = resetDatabase()

    private fun resetDatabase() {
        val field = Sf2ProjectDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as? Sf2ProjectDatabase)?.close()
        field.set(null, null)
    }

    // ==================== Fichier source ====================

    private fun tone(frames: Int, period: Double, amplitude: Double = 12000.0) =
        ShortArray(frames) { (sin(2 * Math.PI * it / period) * amplitude).toInt().toShort() }

    private fun pcm(data: ShortArray) = Sf2Document.Audio.Pcm(data.size, "test") { Sf2Document.LoadedPcm(data) }

    private fun range(lo: Int, hi: Int) = (hi shl 8) or lo

    private val pianoLeft = tone(2000, 40.0)
    private val pianoRight = tone(2000, 41.0)
    private val bell = tone(1500, 25.0, 8000.0)

    /**
     * Banque qui contient tout ce que l'ancien export perdait : zones globales (attaque à
     * 0 timecent = 1 s, scaleTuning 0, mode de boucle, plage, note de base), zone qui répète
     * une valeur par défaut pour écraser sa zone globale, décalage de début d'échantillon,
     * paire stéréo, modulateurs de zone de preset à côté d'une zone globale, zone globale de
     * preset sans générateur, correction de hauteur négative, champs INFO.
     */
    private fun sourceDocument(): Sf2Document {
        val info = listOf(
            Sf2InfoField("ifil", byteArrayOf(2, 0, 1, 0)),
            Sf2DocumentWriter.textField("isng", "EMU8000"),
            Sf2DocumentWriter.textField("INAM", "Banque test"),
            Sf2DocumentWriter.textField("ICRD", "2024"),
            Sf2DocumentWriter.textField("IENG", "Quelqu'un"),
            Sf2DocumentWriter.textField("ICOP", "CC-BY"),
            Sf2DocumentWriter.textField("ICMT", "Commentaire accentué"),
            Sf2DocumentWriter.textField("ISFT", "Polyphone")
        )
        val samples = listOf(
            Sf2Document.Sample("Piano L", pcm(pianoLeft), 500, 1500, 44100, 60, -5, link = 1, type = 4),
            Sf2Document.Sample("Piano R", pcm(pianoRight), 500, 1500, 44100, 60, -5, link = 0, type = 2),
            Sf2Document.Sample("Cloche à vent", pcm(bell), 0, 0, 32000, 72, 3, link = 0, type = 1)
        )
        val piano = Sf2Document.Instrument(
            "Piano", listOf(
                Zone(listOf(43 to range(21, 108), 34 to 0, 48 to 100, 54 to 1, 56 to 0, 58 to 60), listOf(Modulator(0x0081, 6, 30, 0, 0))),
                Zone(listOf(43 to range(21, 64), 17 to -500, 53 to 0), emptyList()),
                Zone(listOf(43 to range(65, 108), 0 to 10, 17 to 500, 48 to 0, 53 to 1), listOf(Modulator(0x0502, 8, -1200, 0, 0)))
            )
        )
        val bellInstrument = Sf2Document.Instrument(
            "Cloche", listOf(
                Zone(listOf(44 to range(0, 100), 8 to 13500, 38 to -12000, 54 to 0, 53 to 2), emptyList())
            )
        )
        val presets = listOf(
            Sf2Document.Preset(
                "Piano", 0, 0, listOf(
                    Zone(emptyList(), listOf(Modulator(0x008B, 48, 960, 0, 0))),
                    Zone(listOf(43 to range(0, 127), 48 to 20, 41 to 0), listOf(Modulator(0x0081, 22, 100, 0, 0))),
                    Zone(listOf(44 to range(0, 90), 41 to 1), emptyList())
                )
            ),
            Sf2Document.Preset(
                "Cloches", 9, 128, listOf(
                    Zone(listOf(52 to 5), emptyList()),
                    Zone(listOf(41 to 1), emptyList())
                )
            )
        )
        return Sf2Document(info, presets, listOf(piano, bellInstrument), samples)
    }

    private fun writeSource(): File = File(dir, "source.sf2").also { Sf2DocumentWriter.write(sourceDocument(), it) }

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

    // ==================== Comparaison ====================

    private fun gens(map: Map<Int, Int>) = map.toSortedMap().entries.joinToString(" ") { "${it.key}=${it.value}" }
    private fun mods(list: List<Sf2ParsedModulator>) =
        list.joinToString(" ") { "[${it.srcOper},${it.destOper},${it.amount},${it.amtSrcOper},${it.transOper}]" }

    /** Tout ce que Polyphone montre, indépendamment de l'ordre des éléments dans le fichier. */
    private fun describe(file: File): Map<String, String> {
        val reader = Sf2Reader()
        val r = reader.parse(file)!!
        val out = linkedMapOf<String, String>()
        for (field in r.info.fields) out["info ${field.id}"] = String(field.data, Charsets.ISO_8859_1).trimEnd('\u0000')
        for (p in r.presets.sortedWith(compareBy({ it.bankNumber }, { it.programNumber }))) {
            val key = "preset ${p.bankNumber}:${p.programNumber}"
            out["$key name"] = p.name
            out["$key global"] = gens(p.globalGenerators) + " " + mods(p.globalModulators)
            p.zones.forEachIndexed { zi, z ->
                val inst = z.instrument!!
                out["$key zone $zi"] = gens(z.generators - 41) + " " + mods(z.modulators) + " -> " + inst.name
                out["$key zone $zi inst global"] = gens(inst.globalGenerators) + " " + mods(inst.globalModulators)
                inst.zones.forEachIndexed { ii, iz ->
                    val s = r.samples[iz.sampleIndex]
                    val link = if (s.sampleType == 1) "-" else r.samples[s.sampleLink].name
                    out["$key zone $zi inst zone $ii"] = gens(iz.generators - 53) + " " + mods(iz.modulators)
                    out["$key zone $zi inst zone $ii sample"] =
                        "${s.name} rate=${s.sampleRate} pitch=${s.originalPitch} corr=${s.pitchCorrection} " +
                            "type=${s.sampleType} link=$link loop=${s.loopStart - s.start}..${s.loopEnd - s.start} " +
                            "audio=${reader.extractSampleAudio(s)!!.contentHashCode()}"
                }
            }
        }
        return out
    }

    private fun assertSameExcept(expected: Map<String, String>, actual: Map<String, String>, vararg changes: Pair<String, String>) {
        val wanted = LinkedHashMap(expected).apply { changes.forEach { (k, v) -> put(k, v) } }
        assertEquals(wanted.entries.joinToString("\n"), actual.entries.joinToString("\n"))
    }

    /** Chaque chunk RIFF annonce la taille qu'il occupe vraiment. */
    private fun assertRiffSizesConsistent(file: File) {
        RandomAccessFile(file, "r").use { raf ->
            fun u32(): Long {
                val b = ByteArray(4); raf.readFully(b)
                return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            }
            fun id(): String = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
            assertEquals("RIFF", id())
            assertEquals(file.length() - 8, u32())
            assertEquals("sfbk", id())
            while (raf.filePointer < raf.length()) {
                assertEquals("LIST", id())
                val size = u32()
                val end = raf.filePointer + size
                id()
                while (raf.filePointer < end) {
                    id()
                    val sub = u32()
                    raf.seek(raf.filePointer + sub + (sub and 1))
                }
                assertEquals(end, raf.filePointer)
            }
        }
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
