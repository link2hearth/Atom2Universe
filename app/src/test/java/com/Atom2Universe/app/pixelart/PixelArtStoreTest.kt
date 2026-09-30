package com.Atom2Universe.app.pixelart

import com.Atom2Universe.app.pixelart.core.BlendMode
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.Tool
import com.Atom2Universe.app.pixelart.io.ImageExport
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.PaletteFormats
import com.Atom2Universe.app.pixelart.io.ProjectStore
import com.Atom2Universe.app.pixelart.io.ReferenceState
import com.Atom2Universe.app.pixelart.io.SourceLink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class PixelArtStoreTest {

    private lateinit var dir: File
    private lateinit var store: ProjectStore
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()

    @Before
    fun setUp() {
        dir = File.createTempFile("pixelart", "test").also { it.delete(); it.mkdirs() }
        store = ProjectStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun EditorSession.dot(x: Int, y: Int, c: Int) {
        primary = c
        tool = Tool.PENCIL
        pointerDown(x, y); pointerUp()
    }

    @Test
    fun creerEcrireRelire() {
        val p = store.create("Mon dessin", 16, 12)
        val s = EditorSession(p.doc)
        s.dot(3, 4, RED)
        s.addLayer("2")
        s.editLayer(s.activeLayerId) { it.opacity = 128; it.blend = BlendMode.MULTIPLY; it.name = "ombres" }
        s.dot(5, 6, BLUE)
        s.addFrame(copyCurrent = true)
        s.setFrameDuration(s.activeFrameId, 250)
        p.meta.primary = BLUE
        p.meta.activeLayerId = s.activeLayerId
        store.saveNow(p)

        val back = store.load(p.meta.id)!!
        assertEquals(16, back.doc.width)
        assertEquals(12, back.doc.height)
        assertEquals(2, back.doc.layers.size)
        assertEquals(2, back.doc.frames.size)
        assertEquals("ombres", back.doc.layers[1].name)
        assertEquals(128, back.doc.layers[1].opacity)
        assertEquals(BlendMode.MULTIPLY, back.doc.layers[1].blend)
        assertEquals(250, back.doc.frames[1].durationMs)
        assertEquals("Mon dessin", back.meta.name)
        assertEquals(BLUE, back.meta.primary)
        for (l in p.doc.layers) for (f in p.doc.frames) {
            val a = p.doc.cel(l.id, f.id)
            val b = back.doc.cel(l.id, f.id)
            if (a == null || a.all { it == 0 }) assertTrue(b == null || b.all { it == 0 })
            else assertTrue("cel ${l.id}/${f.id}", a.contentEquals(b))
        }
    }

    @Test
    fun enregistrementIncrementalEtCelsVides() {
        val p = store.create("x", 8, 8)
        val s = EditorSession(p.doc)
        s.dot(1, 1, RED)
        s.addLayer("2")
        s.dot(2, 2, BLUE)
        store.saveNow(p)
        assertTrue(p.doc.peekDirty().isEmpty)

        // On ne touche qu'à un calque : une seule cel à écrire.
        s.dot(3, 3, RED)
        val plan = store.prepareSave(p)
        assertEquals(1, plan.dirty.written.size)
        store.save(plan)
        p.doc.markSaved(plan.dirty)
        assertTrue(p.doc.peekDirty().isEmpty)

        // Un changement pendant l'écriture reste à écrire.
        s.dot(4, 4, RED)
        val plan2 = store.prepareSave(p)
        s.dot(5, 5, RED)
        p.doc.markSaved(plan2.dirty)
        assertFalse(p.doc.peekDirty().isEmpty)

        // Une cel effacée n'occupe plus le disque.
        store.saveNow(p)
        s.tool = Tool.ERASER
        for (y in 0 until 8) for (x in 0 until 8) { s.pointerDown(x, y); s.pointerUp() }
        store.saveNow(p)
        val celsDir = File(File(dir, p.meta.id), "cels")
        assertEquals(1, celsDir.listFiles()!!.count { it.name.endsWith(".cel") })
    }

    @Test
    fun calqueSupprimeDisparaitDuDisque() {
        val p = store.create("x", 8, 8)
        val s = EditorSession(p.doc)
        s.dot(1, 1, RED)
        s.addLayer("2")
        s.dot(2, 2, BLUE)
        store.saveNow(p)
        val celsDir = File(File(dir, p.meta.id), "cels")
        assertEquals(2, celsDir.listFiles()!!.size)
        s.deleteLayer()
        store.saveNow(p)
        assertEquals(1, celsDir.listFiles()!!.size)
        s.undo()
        store.saveNow(p)
        assertEquals(2, celsDir.listFiles()!!.size)
        val back = store.load(p.meta.id)!!
        assertEquals(2, back.doc.layers.size)
    }

    @Test
    fun lienEtReferenceSontConserves() {
        val p = store.create("x", 4, 4)
        p.meta.link = SourceLink("content://exemple/doc/12", "sprite.png", ImageFormat.PNG, scale = 8, writable = true)
        p.meta.reference = ReferenceState(scale = 2f, x = 3f, y = -4f, opacity = 0.25f, visible = false)
        store.saveNow(p, forceAll = true)
        val back = store.load(p.meta.id)!!
        assertEquals(p.meta.link, back.meta.link)
        assertEquals(p.meta.reference, back.meta.reference)
    }

    @Test
    fun listeTrieeParDateEtDuplicationSansLien() {
        val a = store.create("A", 4, 4)
        Thread.sleep(5)
        val b = store.createFromPixels("B", 2, 2, IntArray(4) { RED }, SourceLink("content://x/1", "b.png", ImageFormat.PNG))
        var list = store.list()
        assertEquals(listOf(b.meta.id, a.meta.id), list.map { it.id })
        assertEquals("b.png", list[0].linkName)

        val copyId = store.duplicate(b.meta.id, "B copie")!!
        list = store.list()
        assertEquals(3, list.size)
        val copy = store.load(copyId)!!
        assertNull(copy.meta.link)
        assertEquals("B copie", copy.meta.name)
        assertEquals(RED, copy.doc.cel(copy.doc.layers[0].id, copy.doc.frames[0].id)!![0])

        store.rename(a.meta.id, "Autre nom")
        assertEquals("Autre nom", store.load(a.meta.id)!!.meta.name)
        store.delete(a.meta.id)
        assertFalse(store.exists(a.meta.id))
    }

    @Test
    fun fichierDeProjetAllerRetour() {
        val p = store.create("Partage", 8, 8)
        val s = EditorSession(p.doc)
        s.dot(2, 2, RED)
        store.saveNow(p)
        val bos = ByteArrayOutputStream()
        store.exportZip(p.meta.id, bos)
        val newId = store.importZip(ByteArrayInputStream(bos.toByteArray()), "sans nom")
        assertNotNull(newId)
        val back = store.load(newId!!)!!
        assertEquals("Partage", back.meta.name)
        assertEquals(RED, back.doc.cel(back.doc.layers[0].id, back.doc.frames[0].id)!![2 * 8 + 2])
        assertNull(store.importZip(ByteArrayInputStream(ByteArray(10)), "x"))
    }

    @Test
    fun manifesteAbimeNeCasseNiLaListeNiLeChargement() {
        val p = store.create("ok", 4, 4)
        val bad = File(dir, "cassé").apply { mkdirs() }
        File(bad, "manifest.json").writeText("{pas du json")
        assertEquals(1, store.list().size)
        assertNull(store.load("cassé"))
        assertNotNull(store.load(p.meta.id))
    }

    // ---- Exports ---------------------------------------------------------------------------

    @Test
    fun ordresDeLecture() {
        assertEquals(listOf(0, 1, 2), ImageExport.frameOrder(3, ImageExport.GifDirection.FORWARD))
        assertEquals(listOf(2, 1, 0), ImageExport.frameOrder(3, ImageExport.GifDirection.REVERSE))
        assertEquals(listOf(0, 1, 2, 3, 2, 1), ImageExport.frameOrder(4, ImageExport.GifDirection.PING_PONG))
        assertEquals(listOf(0, 1), ImageExport.frameOrder(2, ImageExport.GifDirection.PING_PONG))
    }

    @Test
    fun planchePuisDecoupageRendentLesImages() {
        val d = Document.blank(3, 2)
        val s = EditorSession(d)
        s.dot(0, 0, RED)
        s.addFrame(copyCurrent = false)
        s.dot(1, 1, BLUE)
        s.addFrame(copyCurrent = false)
        s.dot(2, 0, RED)
        val sheet = ImageExport.sheet(d, columns = 3, padding = 0)
        assertEquals(9, sheet.w)
        assertEquals(2, sheet.h)
        val cells = ImageExport.splitSheet(sheet.pixels, sheet.w, sheet.h, 3, 1)
        assertEquals(3, cells.size)
        for (i in 0 until 3) assertTrue(ImageExport.flatten(d, i).contentEquals(cells[i]))
        assertTrue(ImageExport.splitSheet(sheet.pixels, sheet.w, sheet.h, 4, 1).isEmpty())
        assertEquals(listOf(1, 3, 9), ImageExport.divisors(9))
    }

    @Test
    fun agrandissementPlusProcheVoisin() {
        val out = ImageExport.scaleNearest(intArrayOf(RED, BLUE, BLUE, RED), 2, 2, 3)
        assertEquals(36, out.size)
        assertEquals(RED, out[0]); assertEquals(RED, out[2 * 6 + 2])
        assertEquals(BLUE, out[3]); assertEquals(BLUE, out[3 * 6])
        assertEquals(RED, out[35])
    }

    // ---- Palettes ------------------------------------------------------------------------------

    @Test
    fun paletteHexEtGpl() {
        val hex = listOf("FF0000", "#0000ff", "; commentaire", "zz", "FF0000").joinToString("\n")
        assertEquals(listOf(RED, BLUE), PaletteFormats.parse(hex))
        val gpl = listOf("GIMP Palette", "Name: test", "Columns: 2", "#", "255 0 0 Rouge", "0 0 255 Bleu").joinToString("\n")
        assertEquals(listOf(RED, BLUE), PaletteFormats.parse(gpl))
        assertEquals("FF0000\n0000FF", PaletteFormats.toHexLines(listOf(RED, BLUE)))
    }

    @Test
    fun couleursDistinctesDUneImage() {
        val px = intArrayOf(0, RED, RED, BLUE, 0x00FFFFFF, BLUE)
        assertEquals(listOf(RED, BLUE), PaletteFormats.uniqueColors(px))
        assertEquals(listOf(RED), PaletteFormats.uniqueColors(px, max = 1))
    }
}
