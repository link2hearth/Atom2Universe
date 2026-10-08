package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.io.ImportedAudio
import com.Atom2Universe.app.audioeditor.io.LegacyMigration
import com.Atom2Universe.app.audioeditor.io.ProjectStore
import com.Atom2Universe.app.audioeditor.io.SourceImporter
import com.Atom2Universe.app.audioeditor.io.WavPcmStream
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class LegacyMigrationTest {

    private lateinit var filesDir: File
    private lateinit var store: ProjectStore

    @Before fun setUp() {
        filesDir = createTempDir("legacy")
        store = ProjectStore(File(filesDir, "audioeditor/projects"))
    }
    @After fun tearDown() { filesDir.deleteRecursively() }

    private val legacy get() = File(filesDir, "audio_editor")

    private fun wav(name: String, rate: Int = 44100, frames: Int = 4410, dir: File = File(legacy, "tracks")): File {
        dir.mkdirs()
        val f = File(dir, name)
        WavWriter(f, 1, rate, float = false).use { it.write(arrayOf(DspTest.sine(frames, 440.0, 0.4f, rate)), frames) }
        return f
    }

    private fun writeLegacy(vararg tracks: Pair<String, File?>) {
        legacy.mkdirs()
        val arr = JSONArray()
        for ((name, f) in tracks) arr.put(JSONObject().put("id", name).put("name", name).put("filePath", f?.absolutePath ?: "").put("durationMs", 100))
        File(legacy, LegacyMigration.PROJECT_FILE).writeText(JSONObject().put("version", 1).put("tracks", arr).toString())
        File(legacy, "history/t1").mkdirs()
        File(legacy, "history/t1/undo_1.wav").writeText("copie d'annulation")
    }

    private val wavImporter: (File, File, RunContext) -> ImportedAudio = { f, dir, ctx ->
        ImportedAudio(WavPcmStream(f).use { SourceImporter.import(it, dir, 44100, ctx) }, f.nameWithoutExtension)
    }

    @Test
    fun `il n'y a rien a migrer sans ancien projet, ou avec un fichier illisible`() {
        assertTrue(LegacyMigration.pending(filesDir).isEmpty())
        assertNull(LegacyMigration.migrate(filesDir, store, "Ancien", 44100, RunContext(44100), wavImporter))
        legacy.mkdirs()
        File(legacy, LegacyMigration.PROJECT_FILE).writeText("{ pas du json")
        assertTrue(LegacyMigration.pending(filesDir).isEmpty())
        assertEquals(0, store.list().size)
    }

    @Test
    fun `parse saute les pistes dont le fichier a disparu`() {
        val a = wav("a.wav")
        val json = JSONObject().put(
            "tracks",
            JSONArray()
                .put(JSONObject().put("name", "A").put("filePath", a.absolutePath))
                .put(JSONObject().put("name", "Perdue").put("filePath", File(legacy, "tracks/absente.wav").absolutePath))
                .put(JSONObject().put("name", "Sans chemin"))
                .put(JSONObject().put("name", "").put("filePath", a.absolutePath)),
        ).toString()
        val t = LegacyMigration.parse(json)
        assertEquals(listOf("A", "a"), t.map { it.name })   // un nom vide prend celui du fichier
    }

    @Test
    fun `chaque ancienne piste devient une piste du nouveau projet`() {
        val a = wav("a.wav"); val b = wav("b.wav", rate = 48000, frames = 9600)
        writeLegacy("Voix" to a, "Fond" to b)
        var last = 0f
        val id = LegacyMigration.migrate(filesDir, store, "Ancien projet", 44100, RunContext(44100, progress = { last = it }), wavImporter)
        assertNotNull(id)
        assertEquals(1f, last.coerceAtMost(1f))

        val p = store.open(id!!)!!
        assertEquals("Ancien projet", p.meta.name)
        assertEquals(listOf("Voix", "Fond"), p.project.tracks.map { it.name })
        // 9600 trames à 48 kHz = 8820 à 44,1 kHz, à une trame près (le rééchantillonneur vide sa queue).
        assertEquals(4410L, p.project.tracks[0].end)
        assertTrue(kotlin.math.abs(p.project.tracks[1].end - 8820L) <= 2)
        assertTrue(p.project.tracks.all { it.clips.size == 1 && it.clips[0].start == 0L })
        assertEquals(2, p.project.sources.size)
        for (s in p.project.sources.values) assertTrue(File(store.dir(id), s.file).isFile)
        assertTrue(store.thumbFile(id).isFile)
        assertEquals(1, store.list().size)

        // L'ancien est rangé : le JSON est renommé, l'historique et les pistes reprises ont disparu.
        assertFalse(File(legacy, LegacyMigration.PROJECT_FILE).exists())
        assertTrue(File(legacy, LegacyMigration.PROJECT_FILE + LegacyMigration.MIGRATED_SUFFIX).isFile)
        assertFalse(File(legacy, "history").exists())
        assertFalse(a.exists()); assertFalse(b.exists())
        assertTrue(LegacyMigration.pending(filesDir).isEmpty())
    }

    @Test
    fun `une piste qui ne s'importe pas est sautee, les autres passent`() {
        val a = wav("a.wav"); val b = wav("b.wav")
        writeLegacy("A" to a, "B" to b)
        val picky: (File, File, RunContext) -> ImportedAudio = { f, dir, ctx -> if (f.name == "a.wav") throw IOException("illisible") else wavImporter(f, dir, ctx) }
        val id = LegacyMigration.migrate(filesDir, store, "x", 44100, RunContext(44100), picky)!!
        assertEquals(listOf("B"), store.open(id)!!.project.tracks.map { it.name })
    }

    @Test
    fun `si rien ne s'importe, l'ancien projet reste intact et aucun projet n'est cree`() {
        val a = wav("a.wav")
        writeLegacy("A" to a)
        val failing: (File, File, RunContext) -> ImportedAudio = { _, _, _ -> throw IOException("illisible") }
        assertNull(LegacyMigration.migrate(filesDir, store, "x", 44100, RunContext(44100), failing))
        assertEquals(0, store.list().size)
        assertEquals(emptyList<String>(), store.root.list()!!.toList())
        assertTrue(File(legacy, LegacyMigration.PROJECT_FILE).isFile)
        assertTrue(a.exists())
        assertTrue(File(legacy, "history").exists())
    }

    @Test
    fun `une annulation en cours de route ne laisse rien derriere elle`() {
        val a = wav("a.wav"); val b = wav("b.wav")
        writeLegacy("A" to a, "B" to b)
        var calls = 0
        val cancel = RunContext(44100, isCancelled = { calls >= 1 })
        val counting: (File, File, RunContext) -> ImportedAudio = { f, d, c -> calls++; wavImporter(f, d, c) }
        try {
            LegacyMigration.migrate(filesDir, store, "x", 44100, cancel, counting)
            org.junit.Assert.fail("aurait dû être annulé")
        } catch (e: java.util.concurrent.CancellationException) {
            // attendu
        }
        assertEquals(emptyList<String>(), store.root.list()!!.toList())
        assertTrue(a.exists() && b.exists())
        assertTrue(File(legacy, LegacyMigration.PROJECT_FILE).isFile)
    }

    @Test
    fun `un fichier hors du dossier de l'ancien editeur n'est jamais efface`() {
        val outside = wav("dehors.wav", dir = File(filesDir, "ailleurs"))
        val inside = wav("dedans.wav")
        writeLegacy("Dehors" to outside, "Dedans" to inside)
        assertNotNull(LegacyMigration.migrate(filesDir, store, "x", 44100, RunContext(44100), wavImporter))
        assertTrue(outside.exists())
        assertFalse(inside.exists())
    }
}
