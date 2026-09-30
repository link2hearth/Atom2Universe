package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.core.ZoomSnapshot
import com.Atom2Universe.app.zoomcanvas.core.ZoomStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class ZoomStoreTest {

    private lateinit var dir: File
    private lateinit var store: ZoomStore

    @Before
    fun setUp() {
        dir = File.createTempFile("zoomcanvas", "test").also { it.delete(); it.mkdirs() }
        store = ZoomStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun ZoomScene.scribble(n: Int, dx: Double = 0.0) {
        beginStroke(-50.0 + dx, 10.0, 0xFF112233.toInt(), 5.0)
        for (i in 1..n) extendStroke(-50.0 + dx + i * 7.0, 10.0 + (i % 3) * 9.0)
        endStroke()
    }

    @Test
    fun saveAndLoadKeepsEveryLayerCameraAndStrokeExactly() {
        val p = store.create("Mise en abyme", 10.0)
        val scene = p.scene
        scene.scribble(12)
        repeat(40) { scene.zoomAt(1.9, 21.0, -13.0) }
        scene.scribble(5, 30.0)
        repeat(100) { scene.zoomAt(2.3, -4.0, 9.0) }
        scene.scribble(8)
        scene.zoomAt(0.3, 0.0, 0.0)
        store.save(ZoomSnapshot.of(p.meta, scene))

        val loaded = store.load(p.meta.id)!!
        val s2 = loaded.scene
        assertEquals("Mise en abyme", loaded.meta.name)
        assertEquals(10.0, s2.ratio, 0.0)
        assertEquals(scene.depth, s2.depth)
        assertEquals(scene.cx, s2.cx, 0.0)
        assertEquals(scene.cy, s2.cy, 0.0)
        assertEquals(scene.zoom, s2.zoom, 0.0)
        assertEquals(scene.nonEmptyDepths(), s2.nonEmptyDepths())
        for (d in scene.nonEmptyDepths()) {
            val a = scene.layer(d)!!
            val b = s2.layer(d)!!
            assertEquals(a.ax, b.ax, 0.0)
            assertEquals(a.ay, b.ay, 0.0)
            assertEquals(a.strokes.size, b.strokes.size)
            for (i in a.strokes.indices) {
                assertEquals(a.strokes[i].x, b.strokes[i].x, 0.0)
                assertEquals(a.strokes[i].y, b.strokes[i].y, 0.0)
                assertArrayEquals(a.strokes[i].pts, b.strokes[i].pts, 0.0)
                assertEquals(a.strokes[i].color, b.strokes[i].color)
            }
        }
        // Les couches vides intermédiaires gardent leur ancre : même rendu à l'écran.
        val deepest = scene.nonEmptyDepths().last()
        val st = scene.layer(deepest)!!.strokes[0]
        val a = scene.toScreen(deepest, st, 3)
        val b = s2.toScreen(deepest, s2.layer(deepest)!!.strokes[0], 3)
        assertEquals(a[0], b[0], 0.0)
        assertEquals(a[1], b[1], 0.0)
        // Et on peut continuer à dessiner : les identifiants ne se répètent pas.
        s2.scribble(3)
        val ids = s2.allLayers().flatMap { l -> l.strokes.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun listReadsTheHeaderOnly() {
        val p = store.create("A", 20.0)
        p.scene.scribble(4)
        repeat(10) { p.scene.zoomAt(2.0, 0.0, 0.0) }
        p.scene.scribble(4)
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        store.create("B", 5.0)
        val list = store.list()
        assertEquals(2, list.size)
        val a = list.first { it.name == "A" }
        assertEquals(20.0, a.ratio, 0.0)
        assertEquals(2, a.layerCount)
        assertEquals(2, a.strokeCount)
    }

    @Test
    fun writesAreAtomicAndABackupIsKept() {
        val p = store.create("A", 10.0)
        p.scene.scribble(4)
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        p.scene.scribble(6)
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        val folder = store.dir(p.meta.id)
        assertFalse(File(folder, "scene.bin.tmp").exists())
        assertTrue(File(folder, "scene.bak").isFile)

        // Le fichier principal est abîmé (coupure, carte pleine…) : on relit la version précédente.
        RandomAccessFile(File(folder, "scene.bin"), "rw").use { it.setLength(it.length() / 2) }
        val back = store.load(p.meta.id)
        assertNotNull(back)
        assertEquals(1, back!!.scene.layer(0)!!.strokes.size)

        // Les deux sont abîmés : rien n'est inventé.
        RandomAccessFile(File(folder, "scene.bak"), "rw").use { it.setLength(10) }
        assertNull(store.load(p.meta.id))
    }

    @Test
    fun renameDuplicateDelete() {
        val p = store.create("A", 10.0)
        p.scene.scribble(4)
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        store.rename(p.meta.id, "B")
        assertEquals("B", store.load(p.meta.id)!!.meta.name)
        val copy = store.duplicate(p.meta.id, "C")!!
        assertEquals(1, store.load(copy)!!.scene.layer(0)!!.strokes.size)
        store.delete(p.meta.id)
        assertEquals(listOf("C"), store.list().map { it.name })
    }
}
