package com.Atom2Universe.app.zoomcanvas

import androidx.room.Room
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.ImageItem
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.OrderMove
import com.Atom2Universe.app.zoomcanvas.core.ShapeItem
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.Atom2Universe.app.zoomcanvas.core.ZoomCodec
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.LoadedZoomProject
import com.Atom2Universe.app.zoomcanvas.data.ZcItem
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** La sauvegarde du canvas infini sur une vraie base SQLite (Room, en mémoire), de bout en bout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomStoreTest {

    private lateinit var dir: File
    private lateinit var db: ZoomDatabase
    private lateinit var store: ZoomStore

    @Before
    fun setUp() {
        dir = File.createTempFile("zoomcanvas", "test").also { it.delete(); it.mkdirs() }
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
        store = ZoomStore(dir, db)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private val black = 0xFF112233.toInt()

    private fun ZoomScene.scribble(n: Int, dx: Double = 0.0, kind: Int = Stroke.PEN): Stroke {
        beginStroke(-50.0 + dx, 10.0, black, 5.0, kind)
        for (i in 1..n) extendStroke(-50.0 + dx + i * 7.0, 10.0 + (i % 3) * 9.0)
        return endStroke()!!
    }

    /** Écrit ce qui a changé depuis la dernière fois, comme le fait le ViewModel. */
    private fun LoadedZoomProject.save() = store.save(meta, scene.drainChanges())

    /** Tout ce qu'une scène contient, en texte : deux scènes identiques donnent le même texte (ordre de pile compris). */
    private fun dump(scene: ZoomScene): String = buildString {
        append("cam ${scene.depth} ${scene.cx} ${scene.cy} ${scene.zoom} next=${scene.nextStrokeId}\n")
        for (d in scene.nonEmptyDepths()) {
            val l = scene.layer(d)!!
            append("layer $d ${l.ax} ${l.ay}\n")
            for (item in l.drawOrder()) append("  ").append(describe(item)).append('\n')
        }
    }

    private fun describe(i: LayerItem): String = when (i) {
        is Stroke -> "stroke ${i.id} ${i.x} ${i.y} ${i.color} ${i.width} ${i.kind} ${i.pts.joinToString(",")}"
        is ImageItem -> "image ${i.id} ${i.key} ${i.pxW}x${i.pxH} ${i.x} ${i.y} ${i.w} ${i.h}"
        is ShapeItem -> "shape ${i.id} $i"
        is TextItem -> "text ${i.id} $i"
        else -> error("unexpected $i")
    }

    /** Relit tout le projet, toutes les couches en mémoire (la lecture normale ne garde que celles près de la caméra). */
    private fun reload(p: LoadedZoomProject) = store.load(p.meta.id, all = true)!!

    @Test
    fun saveAndLoadKeepsEveryLayerCameraAndStrokeExactly() {
        val p = store.create("Mise en abyme", 10.0)
        val scene = p.scene
        scene.scribble(12)
        repeat(40) { scene.zoomAt(1.9, 21.0, -13.0) }
        scene.scribble(5, 30.0, Stroke.BRUSH)
        repeat(100) { scene.zoomAt(2.3, -4.0, 9.0) }
        scene.scribble(8, kind = Stroke.MARKER)
        scene.zoomAt(0.3, 0.0, 0.0)
        p.save()

        val loaded = reload(p)
        assertEquals("Mise en abyme", loaded.meta.name)
        assertEquals(10.0, loaded.scene.ratio, 0.0)
        assertEquals(dump(scene), dump(loaded.scene))
        assertEquals(scene.nonEmptyDepths(), loaded.scene.nonEmptyDepths())

        // Les couches vides intermédiaires gardent leur ancre : même rendu à l'écran.
        val deepest = scene.nonEmptyDepths().last()
        val st = scene.layer(deepest)!!.strokes[0]
        val a = scene.toScreen(deepest, st, 3)
        val b = loaded.scene.toScreen(deepest, loaded.scene.layer(deepest)!!.strokes[0], 3)
        assertEquals(a[0], b[0], 0.0)
        assertEquals(a[1], b[1], 0.0)

        // Et on peut continuer à dessiner : les identifiants ne se répètent pas.
        loaded.scene.scribble(3)
        val ids = loaded.scene.allLayers().flatMap { l -> l.entries().map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyKindOfItemSurvivesAndTheStackOrderToo() {
        val p = store.create("Tout", 10.0)
        val scene = p.scene
        val a = scene.scribble(6)
        scene.beginShape(ShapeKind.ELLIPSE, ShapeFill.BOTH, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 4.0, -80.0, -60.0, false)
        scene.updateShape(90.0, 70.0)
        val shape = scene.endShape()!!
        val text = scene.addText("Bonjour\nle monde", 10.0, -100.0, 24.0, black, TextItem.BOLD or TextItem.ITALIC, "Serif", 6.5)!!
        val img = scene.addImage("a.png", 640, 480, 300.0, 300.0)
        scene.beginStroke(0.0, -20.0, ZoomScene.ERASER, 12.0)
        scene.extendStroke(0.0, 60.0)
        val eraser = scene.endStroke()!!
        // Un ordre qui n'est plus celui de la pose : le texte au fond, le premier trait devant.
        assertTrue(scene.reorder(text.id, OrderMove.TO_BACK))
        assertTrue(scene.reorder(a.id, OrderMove.TO_FRONT))
        p.save()

        val loaded = reload(p)
        assertEquals(dump(scene), dump(loaded.scene))
        assertEquals(scene.layer(0)!!.drawOrder().map { it.id }, loaded.scene.layer(0)!!.drawOrder().map { it.id })
        assertEquals(setOf("a.png"), loaded.scene.imageKeys())
        assertTrue(loaded.scene.layer(0)!!.hasEraser)
        assertNotNull(loaded.scene.box(shape.id) as? ShapeItem)
        assertEquals("Bonjour\nle monde", (loaded.scene.box(text.id) as TextItem).text)
        assertEquals(img.pxW, (loaded.scene.box(img.id) as ImageItem).pxW)
        assertNotNull(loaded.scene.box(eraser.id))
    }

    @Test
    fun onlyWhatChangedIsWrittenAndItStaysCorrectThroughEditsUndoAndRedo() {
        val p = store.create("Edits", 10.0)
        val scene = p.scene
        val a = scene.scribble(5)
        val b = scene.scribble(7, 20.0)
        val c = scene.scribble(3, 40.0)
        p.save()
        assertEquals(0, scene.drainChanges().upserts.size)

        // Un déplacement : une seule ligne à réécrire, pas les trois traits.
        scene.beginBoxEdit(b.id)
        scene.moveBoxBy(25.0, -10.0)
        scene.endBoxEdit()
        val delta = scene.drainChanges()
        assertEquals(listOf(b.id), delta.upserts.map { it.entry.id })
        scene.requeueChanges(delta)
        p.save()
        assertEquals(dump(scene), dump(reload(p).scene))

        // Retrait, annulation du retrait, retrait de nouveau : la base suit.
        scene.deleteBox(a.id)
        p.save()
        assertNull(reload(p).scene.box(a.id))
        assertTrue(scene.undo())
        p.save()
        assertNotNull(reload(p).scene.box(a.id))
        assertTrue(scene.redo())
        scene.reorder(c.id, OrderMove.TO_BACK)
        p.save()
        assertEquals(dump(scene), dump(reload(p).scene))
        // Un élément posé puis effacé avant l'écriture ne laisse rien.
        val d = scene.scribble(4, 60.0)
        scene.deleteBox(d.id)
        p.save()
        assertNull(reload(p).scene.box(d.id))
        assertEquals(dump(scene), dump(reload(p).scene))
    }

    @Test
    fun aFailedWriteIsRetriedWithoutLosingAnything() {
        val p = store.create("Panne", 10.0)
        val scene = p.scene
        scene.scribble(5)
        p.save()
        val b = scene.scribble(6, 30.0)
        // L'écriture échoue : le lot est rendu, d'autres changements arrivent, puis tout part ensemble.
        val lost = scene.drainChanges()
        val c = scene.scribble(4, 60.0)
        scene.deleteBox(b.id)
        scene.requeueChanges(lost)
        p.save()
        val back = reload(p)
        assertNull(back.scene.box(b.id))
        assertNotNull(back.scene.box(c.id))
        assertEquals(dump(scene), dump(back.scene))
    }

    @Test
    fun galleryListReadsTheSummaryOnly() {
        val p = store.create("A", 20.0)
        p.scene.scribble(4)
        repeat(10) { p.scene.zoomAt(2.0, 0.0, 0.0) }
        p.scene.scribble(4)
        p.save()
        store.create("B", 5.0)
        val list = store.list()
        assertEquals(2, list.size)
        val a = list.first { it.name == "A" }
        assertEquals(20.0, a.ratio, 0.0)
        assertEquals(2, a.layerCount)
        assertEquals(2, a.itemCount)
        assertEquals(0, list.first { it.name == "B" }.layerCount)
    }

    @Test
    fun imagesAreKeptWithTheirLayerAndCopiedWhenDuplicating() {
        val p = store.create("Images", 10.0)
        store.saveImage(p.meta.id, "a.png", byteArrayOf(1, 2, 3))
        store.saveImage(p.meta.id, "orphan.png", byteArrayOf(4))
        repeat(12) { p.scene.zoomAt(2.0, 0.0, 0.0) }
        val img = p.scene.addImage("a.png", 640, 480, 300.0, 300.0)
        p.save()

        val loaded = reload(p)
        val back = loaded.scene.box(img.id) as ImageItem
        assertEquals("a.png", back.key)
        assertEquals(img.x, back.x, 0.0)
        assertEquals(img.w, back.w, 0.0)

        store.deleteUnusedImages(p.meta.id, loaded.scene.imageKeys())
        assertTrue(store.imageFile(p.meta.id, "a.png").isFile)
        assertFalse(store.imageFile(p.meta.id, "orphan.png").exists())

        val copy = store.duplicate(p.meta.id, "Copie")!!
        assertTrue(store.imageFile(copy, "a.png").isFile)
        assertNotNull(store.load(copy)!!.scene.box(img.id) as? ImageItem)
    }

    @Test
    fun renameDuplicateDelete() {
        val p = store.create("A", 10.0)
        p.scene.scribble(4)
        p.save()
        store.rename(p.meta.id, "B")
        assertEquals("B", store.load(p.meta.id)!!.meta.name)
        val copy = store.duplicate(p.meta.id, "C")!!
        assertEquals(dump(p.scene), dump(store.load(copy)!!.scene))
        // La copie est indépendante : dessiner dans l'une ne touche pas l'autre.
        p.scene.scribble(3, 90.0)
        p.save()
        assertEquals(1, store.load(copy)!!.scene.layer(0)!!.size)
        store.delete(p.meta.id)
        assertEquals(listOf("C"), store.list().map { it.name })
        assertNull(store.load(p.meta.id))
    }

    @Test
    fun aDamagedItemIsLostButNotTheLayer() {
        val p = store.create("Abîmé", 10.0)
        val a = p.scene.scribble(5)
        p.scene.scribble(5, 30.0)
        p.save()
        // Une ligne dont les octets sont tronqués.
        db.dao().putItems(listOf(ZcItem(p.meta.pid, a.id, 0, ZoomCodec.STROKE, 0L, byteArrayOf(1, 2, 3))))
        val back = reload(p)
        assertEquals(1, back.scene.layer(0)!!.size)
        assertNull(back.scene.box(a.id))
    }

    @Test
    fun aBigLayerIsReadInSeveralPagesAndStaysInOrder() {
        val p = store.create("Gros", 10.0)
        repeat(4500) { i -> p.scene.scribble(2, dx = (i % 50) * 3.0) }
        p.save()
        val back = reload(p)
        assertEquals(4500, back.scene.layer(0)!!.size)
        assertEquals(p.scene.layer(0)!!.drawOrder().map { it.id }, back.scene.layer(0)!!.drawOrder().map { it.id })
    }

    /** Un pager branché sur la vraie base, qui s'exécute tout de suite (à l'écran, la lecture se fait sur un autre fil). */
    private fun attachPager(p: LoadedZoomProject): ZoomScene.LayerPager {
        val pager = object : ZoomScene.LayerPager {
            override fun wantLoad(depth: Long) { p.scene.installLayer(depth, store.loadLayerItems(p.meta.pid, depth)) }
            override fun loadNow(depth: Long) = store.loadLayerItems(p.meta.pid, depth)
        }
        p.scene.pager = pager
        return pager
    }

    @Test
    fun openingAProjectLoadsOnlyTheLayersNearTheCameraAndTheRestComesOnDemand() {
        val p = store.create("Profond", 10.0)
        val scene = p.scene
        store.saveImage(p.meta.id, "a.png", byteArrayOf(1))
        scene.addImage("a.png", 100, 100, 50.0, 50.0)
        val originals = HashMap<Long, String>()
        for (i in 0..7) {
            if (i > 0) scene.scribble(5 + i)
            originals[scene.depth] = scene.layer(scene.depth)!!.drawOrder().joinToString { describe(it) }
            scene.zoomAt(10.0, 0.0, 0.0)
        }
        assertEquals(8L, scene.depth)
        p.save()

        val opened = store.load(p.meta.id)!!
        val s = opened.scene
        // Seule la couche juste au-dessus de la caméra est lue ; les autres ne gardent que leur résumé.
        assertEquals(listOf(7L, 8L), s.allLayers().filter { it.isLoaded }.map { it.depth })
        for (d in 0L..6L) {
            assertFalse(s.layer(d)!!.isLoaded)
            assertFalse(s.layer(d)!!.isEmpty)
            assertNotNull(s.layer(d)!!.boundsApprox())
        }
        assertEquals(1, s.layer(0)!!.itemCount)
        // Rien à réécrire juste après l'ouverture : aucune couche n'a changé.
        val delta = s.drainChanges()
        assertEquals(0, delta.upserts.size + delta.deletes.size + delta.layers.size)
        // Les images de toutes les couches se retrouvent dans la base, même celles qui ne sont pas en mémoire.
        assertEquals(emptySet<String>(), s.imageKeys())
        assertEquals(setOf("a.png"), store.usedImageKeys(opened.meta.pid))
        assertEquals(8, store.list().single().layerCount)

        // On remonte vers le milieu : les couches voisines arrivent de la base, identiques à ce qu'on avait écrit.
        attachPager(opened)
        s.setCamera(3, 0.0, 0.0, 1.0)
        for (d in 2L..5L) {
            assertTrue("couche $d chargée", s.layer(d)!!.isLoaded)
            assertEquals(originals[d], s.layer(d)!!.drawOrder().joinToString { describe(it) })
        }
        // Les plus profondes, propres, repartent de la mémoire (la 8, vide, n'est même plus gardée).
        assertFalse(s.layer(7)!!.isLoaded)
    }

    @Test
    fun whatIsDrawnInALayerSurvivesItBeingUnloadedAndReloaded() {
        val p = store.create("Va-et-vient", 10.0)
        val scene = p.scene
        for (i in 0..6) { scene.scribble(4); scene.zoomAt(10.0, 0.0, 0.0) }
        p.save()
        val opened = store.load(p.meta.id)!!
        attachPager(opened)
        val s = opened.scene
        s.refreshResidency()
        // On dessine dans la couche de travail et on l'écrit.
        val w = s.depth
        s.beginStroke(10.0, 10.0, black, 3.0)
        s.extendStroke(80.0, 40.0)
        val extra = s.endStroke()!!
        val delta = s.drainChanges()
        store.save(opened.meta, delta)
        s.markSaved(delta)
        // On rouvre le projet (historique vide) : le trait est dans la base, la couche se recharge à la demande.
        val reopened = store.load(p.meta.id)!!
        attachPager(reopened)
        reopened.scene.setCamera(0, 0.0, 0.0, 1.0)
        assertFalse(reopened.scene.layer(w)!!.isLoaded)
        reopened.scene.setCamera(w, 0.0, 0.0, 1.0)
        assertNotNull(reopened.scene.box(extra.id))
    }

    @Test
    fun theGallerySizeCountsTheDrawingTheImagesAndTheThumbnail() {
        val p = store.create("Poids", 10.0)
        val empty = store.list().single().sizeBytes
        repeat(200) { p.scene.scribble(30, dx = it * 2.0) }
        p.save()
        val drawn = store.list().single().sizeBytes
        // 200 traits de 31 points en Float : au moins 200 x 31 x 8 octets.
        assertTrue("dessin : $drawn", drawn - empty >= 200 * 31 * 8)
        store.saveImage(p.meta.id, "a.png", ByteArray(5000))
        store.saveThumb(p.meta.id, ByteArray(700))
        assertEquals(drawn + 5700, store.list().single().sizeBytes)
    }
}
