package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les couches qu'on ne voit pas ne sont pas en mémoire : seules celles près de la caméra le sont. */
class ZoomPagingTest {

    private val black = 0xFF000000.toInt()

    private fun stroke(id: Long, x: Double, y: Double) =
        Stroke(id, x, y, floatArrayOf(0f, 0f, 20f, 10f, 40f, 0f), black, 4.0)

    /** La « base » : pour chaque profondeur, ses éléments. Sert de pager à la scène. */
    private class FakeDb(val items: Map<Long, List<Pair<LayerItem, Long>>>) : ZoomScene.LayerPager {
        val requested = ArrayList<Long>()
        var syncLoads = 0
        override fun wantLoad(depth: Long) { requested.add(depth) }
        override fun loadNow(depth: Long): List<Pair<LayerItem, Long>>? { syncLoads++; return items[depth] }
    }

    /** Une scène relue de [n] couches de 3 traits chacune (comme à l'ouverture d'un projet : propres, sans historique), caméra sur [cam]. */
    private fun project(n: Int, cam: Long): Pair<ZoomScene, FakeDb> {
        val layers = ArrayList<Layer>()
        val db = HashMap<Long, List<Pair<LayerItem, Long>>>()
        var id = 1L
        for (d in 0 until n) {
            val items = (0 until 3).map { stroke(id++, it * 60.0, 0.0) to it * Layer.Z_STEP }
            db[d.toLong()] = items
            layers.add(Layer(d.toLong(), 0.0, 0.0).also { it.load(items) })
        }
        val scene = ZoomScene(10.0, Math.sqrt(10.0), 1.0)
        scene.restore(0, layers, cam, 0.0, 0.0, 1.0, id)
        val fake = FakeDb(db)
        scene.pager = fake
        scene.refreshResidency()
        return scene to fake
    }

    private fun loaded(scene: ZoomScene) = scene.allLayers().filter { it.isLoaded }.map { it.depth }

    @Test
    fun onlyTheLayersNearTheCameraStayInMemoryAndTheRestKeepASummary() {
        val (scene, _) = project(12, cam = 9)
        // La couche de travail, une au-dessus, deux en dessous, et un peu de marge avant de décharger.
        assertEquals((7L..11L).toList(), loaded(scene))
        for (d in 0L..6L) {
            val l = scene.layer(d)!!
            assertFalse(l.isLoaded)
            assertFalse(l.isEmpty)
            assertEquals(3, l.itemCount)
            assertTrue(l.extentApprox() > 100.0)
        }
        // Le projet se voit comme avant : les couches vides ou pleines sont les mêmes.
        assertEquals((0L..11L).toList(), scene.nonEmptyDepths())
    }

    @Test
    fun movingTheCameraAsksForTheNextLayersAndUnloadsTheFarOnes() {
        val (scene, db) = project(12, cam = 9)
        db.requested.clear()
        scene.setCamera(5, 0.0, 0.0, 1.0)
        // Voulues : 4 (au-dessus), 5 (travail), 6 et 7 (en dessous) ; 7 est déjà là.
        assertEquals(setOf(4L, 5L, 6L), db.requested.toSet())
        for (d in listOf(4L, 5L, 6L)) assertTrue(scene.installLayer(d, db.items[d]!!))
        assertEquals(3, scene.layer(5)!!.size)
        // La 10 et la 11 sont maintenant bien loin : elles repartent.
        assertFalse(scene.layer(10)!!.isLoaded)
        assertFalse(scene.layer(11)!!.isLoaded)
        assertTrue(scene.layer(8)!!.isLoaded)
        // Déjà là, ou plus voulue : on ne l'installe pas.
        assertFalse(scene.installLayer(5, db.items[5L]!!))
        assertFalse(scene.installLayer(0, db.items[0L]!!))
    }

    @Test
    fun aLayerThatIsNotWrittenYetOrCitedByTheHistoryIsNeverUnloaded() {
        val l = Layer(0, 0.0, 0.0)
        l.add(stroke(1, 0.0, 0.0))
        assertTrue(l.dirty)
        assertFalse(l.unload())
        assertTrue(l.isLoaded)
        // Écrit tel qu'il était : on peut le décharger. S'il a bougé depuis, non.
        l.markSaved(l.version - 1)
        assertTrue(l.dirty)
        l.markSaved(l.version)
        assertFalse(l.dirty)
        assertTrue(l.unload())
        assertFalse(l.isLoaded)
        assertEquals(1, l.size)

        // L'historique d'annulation cite une couche : elle reste, même propre et loin.
        val (scene, db) = project(12, cam = 9)
        scene.setCamera(9, 0.0, 0.0, 1.0)
        scene.beginStroke(0.0, 0.0, black, 3.0)
        scene.extendStroke(30.0, 30.0)
        scene.endStroke()
        scene.markSaved(scene.drainChanges())
        scene.setCamera(1, 0.0, 0.0, 1.0)
        assertTrue("l'historique cite la 9", scene.layer(9)!!.isLoaded)
        db.requested.clear()
        assertTrue(scene.undo())
        assertEquals(3, scene.layer(9)!!.size)
    }

    @Test
    fun aLayerStaysDirtyUntilItsWriteIsConfirmed() {
        val (scene, _) = project(12, cam = 9)
        scene.beginStroke(0.0, 0.0, black, 3.0)
        scene.extendStroke(30.0, 30.0)
        scene.endStroke()
        val delta = scene.drainChanges()
        // L'écriture n'est pas confirmée (ou a échoué) : la couche reste à écrire, et en mémoire même si la caméra s'en va.
        scene.setCamera(0, 0.0, 0.0, 1.0)
        assertTrue(scene.layer(9)!!.dirty)
        assertTrue(scene.layer(9)!!.isLoaded)
        scene.markSaved(delta)
        assertFalse(scene.layer(9)!!.dirty)
        // Un autre trait posé entre l'envoi et la confirmation : la couche reste à écrire.
        scene.setCamera(9, 0.0, 0.0, 1.0)
        scene.beginStroke(0.0, 0.0, black, 3.0)
        scene.extendStroke(30.0, 30.0)
        scene.endStroke()
        val first = scene.drainChanges()
        scene.beginStroke(50.0, 0.0, black, 3.0)
        scene.extendStroke(80.0, 30.0)
        scene.endStroke()
        scene.markSaved(first)
        assertTrue(scene.layer(9)!!.dirty)
    }

    @Test
    fun drawingInALayerThatHasNotArrivedYetLoadsItFirst() {
        val (scene, db) = project(12, cam = 9)
        // Zoom très vite vers une couche encore déchargée : son contenu doit être là avant le premier trait.
        scene.setCamera(1, 0.0, 0.0, 1.0)
        assertFalse(scene.layer(1)!!.isLoaded)
        scene.beginStroke(5.0, 5.0, black, 3.0)
        scene.extendStroke(60.0, 60.0)
        scene.endStroke()
        assertEquals(1, db.syncLoads)
        assertEquals(4, scene.layer(1)!!.size)
        // L'arrivée tardive de la lecture asynchrone ne doit rien écraser.
        assertFalse(scene.installLayer(1, db.items[1L]!!))
        assertEquals(4, scene.layer(1)!!.size)
    }

    @Test
    fun renderingSkipsAnUnloadedLayerAndShowsItOnceItHasArrived() {
        val (scene, db) = project(12, cam = 9)
        scene.setCamera(3, 0.0, 0.0, 1.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(0, out.runCount)
        for (d in 2L..5L) scene.installLayer(d, db.items[d]!!)
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertTrue(out.runCount >= 3)
    }

    @Test
    fun theSummaryMatchesTheLayerItWasTakenFrom() {
        val l = Layer(2, 1.0, 2.0)
        for (i in 1L..5L) l.add(stroke(i, i * 100.0, 0.0))
        l.markSaved(l.version)
        val before = l.boundsApprox()!!.copyOf()
        val count = l.itemCount
        assertTrue(l.unload())
        assertEquals(count, l.itemCount)
        assertEquals(before.toList(), l.boundsApprox()!!.toList())
        assertNotNull(l.summary().bounds)
        // Une fois relue, elle est comme avant et propre.
        l.load(listOf(stroke(1, 0.0, 0.0) to 0L))
        assertTrue(l.isLoaded)
        assertFalse(l.dirty)
        assertEquals(1, l.size)
    }
}
