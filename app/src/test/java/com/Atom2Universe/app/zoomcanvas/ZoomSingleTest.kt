package com.Atom2Universe.app.zoomcanvas

import androidx.room.Room
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Le « Canvas » : la même toile, une seule couche. Le zoom bute sur ses bornes au lieu de changer de
 * couche, et les projets à une seule couche ont leur propre galerie, dans la même base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomSingleTest {

    private lateinit var dir: File
    private lateinit var db: ZoomDatabase
    private lateinit var store: ZoomStore

    @Before
    fun setUp() {
        dir = File.createTempFile("zoomsingle", "test").also { it.delete(); it.mkdirs() }
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
        store = ZoomStore(dir, db)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private val black = 0xFF000000.toInt()

    @Test
    fun zoomButtsOnItsBoundsAndNeverChangesLayer() {
        val s = ZoomScene.create(single = true)
        assertEquals(1.0, s.zoom, 1e-9)
        repeat(60) { s.zoomAt(2.0, 40.0, -30.0) }
        assertEquals(ZoomScene.SINGLE_MAX_ZOOM, s.zoom, 1e-6)
        repeat(120) { s.zoomAt(0.5, -10.0, 25.0) }
        assertEquals(ZoomScene.SINGLE_MAX_ZOOM / ZoomScene.SINGLE_RATIO, s.zoom, 1e-9)
        assertEquals(0L, s.depth)
        assertEquals(1, s.allLayers().size)
    }

    @Test
    fun pointUnderTheFingerStaysPutWhileZooming() {
        val s = ZoomScene.create(single = true)
        val before = s.screenToLocal(80.0, -60.0)
        s.zoomAt(3.0, 80.0, -60.0)
        val after = s.screenToLocal(80.0, -60.0)
        assertEquals(before[0], after[0], 1e-9)
        assertEquals(before[1], after[1], 1e-9)
    }

    @Test
    fun drawingKeepsItsFrameInASingleLayer() {
        val s = ZoomScene.create(single = true)
        s.beginStroke(0.0, 0.0, black, 4.0)
        s.extendStroke(50.0, 20.0)
        val stroke = s.endStroke()
        assertNotNull(stroke)
        // Le repère de la couche ne bouge jamais : le trait est où on l'a tracé.
        assertEquals(0.0, stroke!!.x, 1e-9)
        assertEquals(0.0, stroke.y, 1e-9)
        assertEquals(0L, s.depth)
    }

    @Test
    fun fitToContentFramesTheDrawing() {
        val s = ZoomScene.create(single = true)
        s.beginStroke(-30.0, -10.0, black, 4.0)
        s.extendStroke(30.0, 10.0)
        s.endStroke()
        s.zoomAt(0.05, 0.0, 0.0)
        s.pan(5000.0, -3000.0)
        s.jumpTo(0, 800.0, 600.0, ZoomScene.SINGLE_FIT_MAX_ZOOM)
        // Un petit dessin n'est pas agrandi au-delà du plafond.
        assertEquals(ZoomScene.SINGLE_FIT_MAX_ZOOM, s.zoom, 1e-9)
        assertEquals(0.0, s.cx, 1.0)
        assertEquals(0.0, s.cy, 1.0)
    }

    @Test
    fun galleriesAreSeparatedAndSingleProjectsReopenAsSingle() {
        store.create("layers", ZoomScene.DEFAULT_RATIO)
        val p = store.create("flat", ZoomScene.DEFAULT_RATIO, single = true)
        assertEquals(listOf("layers"), store.list().map { it.name })
        val flat = store.list(single = true)
        assertEquals(listOf("flat"), flat.map { it.name })
        assertTrue(flat[0].single)

        p.scene.beginStroke(0.0, 0.0, black, 4.0)
        p.scene.extendStroke(40.0, 40.0)
        p.scene.endStroke()
        p.scene.zoomAt(5.0, 10.0, 10.0)
        store.save(p.meta, p.scene.drainChanges())

        val back = store.load(p.meta.id)!!
        assertTrue(back.scene.single)
        assertEquals(p.scene.zoom, back.scene.zoom, 1e-9)
        assertEquals(1, back.scene.layer(0)!!.itemCount)
        // Et il ne devient jamais un projet à couches en route.
        repeat(40) { back.scene.zoomAt(3.0, 0.0, 0.0) }
        assertEquals(0L, back.scene.depth)
    }
}
