package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.EnvPoint
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.core.Marker
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.Thumbnail
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.core.deleteClip
import com.Atom2Universe.app.audioeditor.core.setClipFades
import com.Atom2Universe.app.audioeditor.core.updateClip
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.ManifestCodec
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.ProjectMeta
import com.Atom2Universe.app.audioeditor.io.ProjectStore
import com.Atom2Universe.app.audioeditor.io.ViewState
import com.Atom2Universe.app.audioeditor.io.WavWriter
import com.Atom2Universe.app.audioeditor.io.peaksFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectStoreTest {

    private lateinit var root: File
    private var clock = 1_000L
    private lateinit var store: ProjectStore

    @Before fun setUp() {
        root = createTempDir("audiostore")
        store = ProjectStore(File(root, "projects")) { clock++ }
    }
    @After fun tearDown() { root.deleteRecursively() }

    /** Écrit une vraie source (sinus 16 bits) dans le dossier du projet [id] et rend son descripteur. */
    private fun writeSource(id: String, sourceId: String, frames: Int = 4410, channels: Int = 1, amp: Float = 0.5f): Source {
        val dir = store.dir(id)
        val file = File(dir, "sources/$sourceId.wav")
        file.parentFile!!.mkdirs()
        val ch = Array(channels) { c -> DspTest.sine(frames, 440.0 * (c + 1), amp) }
        WavWriter(file, channels, 44100, float = false).use { it.write(ch, frames) }
        PeakFiles.save(PeakFiles.build(file), File(dir, "sources/$sourceId.peaks"))
        return Source(sourceId, "sources/$sourceId.wav", channels, frames.toLong(), 44100)
    }

    /** Un projet complet : deux sources, deux pistes, fondus, retourné, enveloppe, repères. */
    private fun richProject(id: String): Project {
        var p = Project().addSource(writeSource(id, "sa")).addSource(writeSource(id, "sb", channels = 2))
        val (p1, t1) = p.addTrack("Voix", color = 0xFF3366.toInt())
        val (p2, t2) = p1.addTrack("Musique")
        p = p2
        val (p3, c1) = p.addClip(t1, "sa", 100, name = "intro")
        val (p4, c2) = p3.addClip(t2, "sb", 0, srcStart = 10, length = 2000)
        p = p4.setClipFades(c1!!, 300, 500, FadeShape.S_CURVE)
        p = p.updateClip(c2!!) { it.copy(reversed = true, gain = 0.8f) }
        p = p.mapTrack(t1) { it.copy(volume = 0.7f, pan = -0.25f, mute = false, solo = true, envelope = listOf(EnvPoint(0, 1f), EnvPoint(2000, 0.3f))) }
        return p.copy(
            markers = listOf(Marker(p.nextId, 500, 500, "ici"), Marker(p.nextId + 1, 1000, 2000, "zone")),
            nextId = p.nextId + 2,
            master = 0.9f,
        )
    }

    // ---- Manifeste ----------------------------------------------------------------------

    @Test
    fun `le manifeste fait l'aller-retour sans rien perdre`() {
        val id = store.create("Essai").id
        val p = richProject(id)
        val meta = ProjectMeta("Essai", 5, 9, ViewState(cursor = 123, selStart = 10, selEnd = 99, framesPerPixel = 12.5, scrollFrame = 77, activeTrack = 2, snap = false, spectrogramTracks = listOf(2, 5)))
        val (q, m) = ManifestCodec.decode(JSONObject(ManifestCodec.encode(p, meta).toString()))
        assertEquals(p, q)
        assertEquals(meta, m)
    }

    @Test
    fun `les champs inconnus sont ignores et les champs absents prennent leur valeur par defaut`() {
        val j = JSONObject()
            .put("rate", 48000).put("futur", "inconnu")
            .put("tracks", JSONArray().put(JSONObject().put("id", 3).put("extra", 1)))
        val (p, m) = ManifestCodec.decode(j)
        assertEquals(48000, p.sampleRate)
        assertEquals(1f, p.master)
        assertEquals(1, p.tracks.size)
        assertEquals(1f, p.tracks[0].volume)
        assertEquals("", m.name)
        assertEquals(ViewState(), m.view)
        assertTrue(p.nextId > 3)
    }

    @Test
    fun `un chemin de source qui sort de sources est refuse`() {
        for (bad in listOf("../evil.wav", "sources/../evil.wav", "sources/a/b.wav", "/etc/passwd", "sources/a.mp3", "a.wav")) {
            val j = JSONObject().put(
                "sources",
                JSONArray().put(JSONObject().put("id", "a").put("file", bad).put("ch", 1).put("frames", 10)),
            )
            try {
                ManifestCodec.decode(j)
                fail("aurait dû refuser « $bad »")
            } catch (e: IllegalArgumentException) {
                // attendu
            }
        }
    }

    @Test
    fun `un identifiant libre trop petit est corrige et un clip vide est ecarte`() {
        val j = JSONObject().put("nextId", 1).put(
            "tracks",
            JSONArray().put(
                JSONObject().put("id", 1).put(
                    "clips",
                    JSONArray()
                        .put(JSONObject().put("id", 7).put("src", "a").put("len", 100))
                        .put(JSONObject().put("id", 8).put("src", "a").put("len", 0)),
                ),
            ),
        )
        val (p, _) = ManifestCodec.decode(j)
        assertEquals(1, p.tracks[0].clips.size)
        assertEquals(8, p.nextId)
    }

    @Test
    fun `des valeurs non finies s'ecrivent sans exception`() {
        val id = store.create("x").id
        val p = Project().addSource(writeSource(id, "s1")).let { it.addTrack("t") }.let { (q, t) ->
            q.addClip(t, "s1", 0).first.copy(master = Float.NaN)
        }
        val back = ManifestCodec.decode(JSONObject(ManifestCodec.encode(p, ProjectMeta("n", 0, 0)).toString())).first
        assertEquals(1f, back.master)
    }

    // ---- Catalogue et gestion -----------------------------------------------------------

    @Test
    fun `un projet cree apparait dans la galerie`() {
        val a = store.create("Premier", 48000)
        val list = store.list()
        assertEquals(1, list.size)
        assertEquals(a.id, list[0].id)
        assertEquals("Premier", list[0].name)
        assertEquals(48000, list[0].sampleRate)
        assertEquals(0, list[0].trackCount)
        assertEquals(0L, list[0].frames)
    }

    @Test
    fun `la galerie est triee du plus recent au plus ancien et resume la duree`() {
        val a = store.create("A")
        val b = store.create("B")
        val p = richProject(a.id)
        store.save(a.id, p, a.meta)   // A devient le plus récent
        val list = store.list()
        assertEquals(listOf(a.id, b.id), list.map { it.id })
        assertEquals(p.length, list[0].frames)
        assertEquals(2, list[0].trackCount)
    }

    @Test
    fun `enregistrer puis ouvrir rend le meme projet`() {
        val a = store.create("A")
        val p = richProject(a.id)
        val meta = store.save(a.id, p, a.meta.copy(view = ViewState(cursor = 42)))
        val back = store.open(a.id)!!
        assertEquals(p, back.project)
        assertEquals(meta, back.meta)
        assertEquals(42L, back.meta.view.cursor)
    }

    @Test
    fun `touch a faux garde la date de modification`() {
        val a = store.create("A")
        val m1 = store.save(a.id, a.project, a.meta)
        val m2 = store.save(a.id, a.project, m1.copy(view = ViewState(cursor = 5)), touch = false)
        assertEquals(m1.modified, m2.modified)
        assertTrue(m1.modified > a.meta.modified)
        assertEquals(m1.modified, store.list()[0].modified)
    }

    @Test
    fun `un manifeste abime n'empeche pas de lister les autres`() {
        val a = store.create("A")
        val b = store.create("B")
        File(store.dir(b.id), "manifest.json").writeText("{ pas du json")
        File(store.root, ".incoming_x").mkdirs()
        File(store.root, ".incoming_x/manifest.json").writeText("{}")
        assertEquals(listOf(a.id), store.list().map { it.id })
        assertNull(store.open(b.id))
    }

    @Test
    fun `renommer change le nom et la date`() {
        val a = store.create("A")
        store.rename(a.id, "Nouveau")
        assertEquals("Nouveau", store.nameOf(a.id))
        assertEquals("Nouveau", store.open(a.id)!!.meta.name)
        assertTrue(store.open(a.id)!!.meta.modified > a.meta.modified)
    }

    @Test
    fun `dupliquer copie l'audio et les deux projets restent independants`() {
        val a = store.create("A")
        val p = richProject(a.id)
        store.save(a.id, p, a.meta)
        val copy = store.duplicate(a.id, "Copie")!!
        assertEquals("Copie", store.open(copy)!!.meta.name)
        assertEquals(p, store.open(copy)!!.project)
        assertEquals(File(store.dir(a.id), "sources/sa.wav").length(), File(store.dir(copy), "sources/sa.wav").length())
        store.delete(a.id)
        assertFalse(store.exists(a.id))
        assertTrue(File(store.dir(copy), "sources/sa.wav").isFile)
        assertNull(store.duplicate("nexistepas", "x"))
    }

    // ---- Ramasse-miettes ----------------------------------------------------------------

    @Test
    fun `le ramasse-miettes efface ce que le projet n'utilise plus`() {
        val a = store.create("A")
        val p = richProject(a.id)
        writeSource(a.id, "orphelin")
        File(store.dir(a.id), "sources/sa.wav.tmp").writeText("coupé")
        File(store.dir(a.id), "sources/sb.peaks.tmp").writeText("coupé")
        val freed = store.collectGarbage(a.id, p.usedSourceIds())
        assertTrue(freed > 0)
        val names = File(store.dir(a.id), "sources").list()!!.sorted()
        assertEquals(listOf("sa.peaks", "sa.wav", "sb.peaks", "sb.wav"), names)
    }

    @Test
    fun `finish oublie les sources sans clip, nettoie le disque et fait la vignette`() {
        val a = store.create("A")
        val p = richProject(a.id)
        val clipOfSb = p.tracks[1].clips[0].id
        val reduced = p.deleteClip(clipOfSb)
        assertTrue("sb" in reduced.sources)
        val clean = store.finish(a.id, reduced, a.meta)
        assertFalse("sb" in clean.sources)
        assertFalse(File(store.dir(a.id), "sources/sb.wav").exists())
        assertFalse(File(store.dir(a.id), "sources/sb.peaks").exists())
        assertTrue(File(store.dir(a.id), "sources/sa.wav").isFile)
        assertEquals(clean, store.open(a.id)!!.project)
        val png = store.thumbFile(a.id).readBytes()
        assertTrue(png.size > 100)
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])
    }

    @Test
    fun `ouvrir avec ramasse-miettes retire les rendus d'une session plantee`() {
        val a = store.create("A")
        val p = richProject(a.id)
        store.save(a.id, p, a.meta)
        writeSource(a.id, "rendu_jamais_enregistre")
        assertNotNull(store.open(a.id, collectGarbage = true))
        assertFalse(File(store.dir(a.id), "sources/rendu_jamais_enregistre.wav").exists())
        assertTrue(File(store.dir(a.id), "sources/sa.wav").isFile)
    }

    @Test
    fun `peaksFile suit la source`() {
        val s = Source("x1", "sources/x1.wav", 1, 10, 44100)
        assertEquals(File("/p/sources/x1.peaks"), s.peaksFile(File("/p")))
    }

    // ---- Zip ----------------------------------------------------------------------------

    private fun zipOf(id: String): ByteArray = ByteArrayOutputStream().also { store.exportZip(id, it) }.toByteArray()

    private fun zipWith(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return bos.toByteArray()
    }

    @Test
    fun `un projet fait l'aller-retour par zip`() {
        val a = store.create("Zip")
        val p = richProject(a.id)
        store.save(a.id, p, a.meta)
        store.updateThumb(a.id, p)
        val b = store.importZip(ByteArrayInputStream(zipOf(a.id)), "secours")!!
        assertTrue(b != a.id)
        val back = store.open(b)!!
        assertEquals(p, back.project)
        assertEquals("Zip", back.meta.name)
        for (name in listOf("sa.wav", "sb.wav", "sa.peaks", "sb.peaks")) {
            assertEquals(name, File(store.dir(a.id), "sources/$name").readBytes().toList(), File(store.dir(b), "sources/$name").readBytes().toList())
        }
        assertTrue(store.thumbFile(b).isFile)
        assertEquals(2, store.list().size)
    }

    @Test
    fun `le zip n'emporte ni fichier temporaire ni intrus`() {
        val a = store.create("Zip")
        store.save(a.id, richProject(a.id), a.meta)
        File(store.dir(a.id), "sources/sa.wav.tmp").writeText("x")
        File(store.dir(a.id), "notes.txt").writeText("x")
        val names = java.util.zip.ZipInputStream(ByteArrayInputStream(zipOf(a.id))).let { z ->
            generateSequence { z.nextEntry }.map { it.name }.toList()
        }
        assertTrue("manifest.json" in names)
        assertTrue(names.none { it.endsWith(".tmp") || it == "notes.txt" })
    }

    @Test
    fun `un zip sans manifeste est refuse et ne laisse rien`() {
        val z = zipWith("sources/a.wav" to ByteArray(10))
        assertNull(store.importZip(ByteArrayInputStream(z), "x"))
        assertEquals(emptyList<String>(), store.root.list()!!.toList())
    }

    @Test
    fun `un zip qui tente de sortir du dossier est neutralise`() {
        val a = store.create("Zip")
        store.save(a.id, richProject(a.id), a.meta)
        val manifest = File(store.dir(a.id), "manifest.json").readBytes()
        val z = zipWith("manifest.json" to manifest, "../evil.txt" to "x".toByteArray(), "sources/../../evil2.txt" to "x".toByteArray())
        assertNull(store.importZip(ByteArrayInputStream(z), "x"))   // les sources manquent : refusé
        assertFalse(File(store.root, "evil.txt").exists())
        assertFalse(File(store.root.parentFile, "evil.txt").exists())
        assertFalse(File(store.root.parentFile, "evil2.txt").exists())
    }

    @Test
    fun `un zip dont une source est tronquee est refuse`() {
        val a = store.create("Zip")
        val p = richProject(a.id)
        store.save(a.id, p, a.meta)
        val wav = File(store.dir(a.id), "sources/sa.wav").readBytes()
        val z = zipWith(
            "manifest.json" to File(store.dir(a.id), "manifest.json").readBytes(),
            "sources/sa.wav" to wav.copyOf(wav.size / 4),
            "sources/sb.wav" to File(store.dir(a.id), "sources/sb.wav").readBytes(),
        )
        assertNull(store.importZip(ByteArrayInputStream(z), "x"))
        assertEquals(listOf(a.id), store.root.list()!!.toList())
    }

    @Test
    fun `un nom vide prend le nom de secours a l'import`() {
        val a = store.create("")
        store.save(a.id, richProject(a.id), a.meta)
        val b = store.importZip(ByteArrayInputStream(zipOf(a.id)), "Importé")!!
        assertEquals("Importé", store.open(b)!!.meta.name)
    }

    // ---- Vignette -----------------------------------------------------------------------

    @Test
    fun `la vignette d'un projet vide est une ligne au milieu`() {
        val t = Thumbnail.render(Project(), FileSampleProvider(root), 40, 20, background = 0, wave = 1)
        for (x in 0 until 40) assertEquals(1, t.pixels[10 * 40 + x])
        assertEquals(0, t.pixels[0])
    }

    @Test
    fun `la vignette suit l'amplitude et agrandit un enregistrement faible`() {
        val a = store.create("A")
        var p = Project().addSource(writeSource(a.id, "loud", frames = 44100, amp = 0.9f)).addSource(writeSource(a.id, "quiet", frames = 44100, amp = 0.05f))
        p = p.addTrack("t").let { (q, t) -> q.addClip(t, "loud", 0).first }
        val q = p.addTrack("u").let { (r, t) -> r.addClip(t, "quiet", 0).first }
        FileSampleProvider(store.dir(a.id)).use { prov ->
            fun height(project: Project): Int {
                val th = Thumbnail.render(project, prov, 32, 60, background = 0, wave = 1)
                return (0 until 60).count { y -> th.pixels[y * 32 + 16] == 1 }
            }
            val loudOnly = p
            val quietOnly = Project(sources = p.sources, tracks = listOf(q.tracks[1]), nextId = q.nextId)
            assertTrue("fort : ${height(loudOnly)}", height(loudOnly) > 40)
            // Le faible est remis à l'échelle (×8 au plus : 0,05 → 0,4 de la demi-hauteur).
            val h = height(quietOnly)
            assertTrue("faible : $h", h in 15..40)
        }
    }
}
