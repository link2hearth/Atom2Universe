package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.zoomcanvas.core.CacheWindow
import com.Atom2Universe.app.zoomcanvas.core.Entry
import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.OrderMove
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** L'index spatial d'une couche, son instantané pour un fil de fond, et la logique du cache raster (sans Android). */
class ZoomIndexTest {

    private fun stroke(id: Long, x: Double, y: Double, len: Int = 5, width: Double = 4.0, color: Int = 0xFF000000.toInt()): Stroke {
        val pts = FloatArray(2 * len)
        for (i in 0 until len) { pts[2 * i] = i * 3f; pts[2 * i + 1] = (i % 2) * 2f }
        return Stroke(id, x, y, pts, color, width)
    }

    /** Une couche de [n] traits semés au hasard sur un grand carré. */
    private fun scatter(n: Int, seed: Long = 1): Layer {
        val rnd = Random(seed)
        val l = Layer(0, 0.0, 0.0)
        for (i in 1..n) l.add(stroke(i.toLong(), rnd.nextDouble() * 20000 - 10000, rnd.nextDouble() * 20000 - 10000, len = 2 + rnd.nextInt(30)))
        return l
    }

    private fun bruteForce(l: Layer, x0: Double, y0: Double, x1: Double, y1: Double): List<Long> =
        l.entries().filter { it.overlaps(x0, y0, x1, y1) }.map { it.id }

    @Test
    fun queryFindsExactlyWhatABruteForceScanFindsInZOrder() {
        val l = scatter(5000)
        val out = ArrayList<Entry>()
        val rnd = Random(9)
        repeat(60) {
            val x = rnd.nextDouble() * 20000 - 10000
            val y = rnd.nextDouble() * 20000 - 10000
            val w = rnd.nextDouble() * 3000
            val h = rnd.nextDouble() * 3000
            l.query(x, y, x + w, y + h, out)
            assertEquals(bruteForce(l, x, y, x + w, y + h), out.map { it.id })
        }
    }

    @Test
    fun staysExactWhileStrokesAreAddedRemovedMovedAndReordered() {
        val l = scatter(3000, seed = 3)
        val out = ArrayList<Entry>()
        val rnd = Random(5)
        var nextId = 10_000L
        repeat(400) { step ->
            when (rnd.nextInt(4)) {
                0 -> l.add(stroke(nextId++, rnd.nextDouble() * 20000 - 10000, rnd.nextDouble() * 20000 - 10000))
                1 -> l.removeBox(l.entries()[rnd.nextInt(l.size)].id)
                2 -> { val e = l.entries()[rnd.nextInt(l.size)]; l.replaceBox(com.Atom2Universe.app.zoomcanvas.core.StrokeBox((e.item as Stroke)).moved(rnd.nextDouble() * 500, 0.0)) }
                else -> l.moveInOrder(l.entries()[rnd.nextInt(l.size)].id, rnd.nextInt(l.size))
            }
            if (step % 20 == 0) {
                val x = rnd.nextDouble() * 20000 - 10000
                val y = rnd.nextDouble() * 20000 - 10000
                l.query(x, y, x + 4000, y + 4000, out)
                assertEquals("étape $step", bruteForce(l, x, y, x + 4000, y + 4000), out.map { it.id })
            }
        }
        // Les rangs restent strictement croissants et les entrées retrouvables par leur identifiant.
        val zs = l.entries().map { it.z }
        assertEquals(zs.sorted(), zs)
        assertEquals(zs.size, zs.toSet().size)
        for (e in l.entries()) assertTrue(l.entry(e.id) === e)
    }

    @Test
    fun manyInsertionsAtTheSamePlaceRenumberWithoutLosingTheOrder() {
        val l = Layer(0, 0.0, 0.0)
        l.add(stroke(1, 0.0, 0.0)); l.add(stroke(2, 10.0, 0.0))
        // Chaque insertion entre les deux mêmes voisins divise l'écart en deux : il finit par s'épuiser.
        val expected = ArrayList<Long>()
        for (k in 0 until 60) { l.addBox(com.Atom2Universe.app.zoomcanvas.core.StrokeBox(stroke(100L + k, 5.0, 0.0)), 1); expected.add(0, 100L + k) }
        assertEquals(listOf(1L) + expected + listOf(2L), l.drawOrder().map { it.id })
        val zs = l.entries().map { it.z }
        assertEquals(zs.sorted(), zs)
        assertEquals(zs.size, zs.toSet().size)
    }

    @Test
    fun aSnapshotKeepsServingWhatWasThereAndSkipsWhatDiedSince() {
        val l = scatter(2000, seed = 4)
        val snap = l.snapshot()
        val before = ArrayList<Entry>().also { snap.query(-1e5, -1e5, 1e5, 1e5, it) }
        assertEquals(2000, before.size)
        val victim = l.entries()[10].id
        l.removeBox(victim)
        l.add(stroke(99_999, 0.0, 0.0))
        val after = ArrayList<Entry>().also { snap.query(-1e5, -1e5, 1e5, 1e5, it) }
        assertEquals(1999, after.size)
        assertFalse(after.any { it.id == victim || it.id == 99_999L })
        assertEquals(after.map { it.z }, after.map { it.z }.sorted())
    }

    @Test
    fun observersHearEveryChange() {
        val l = Layer(0, 0.0, 0.0)
        val seen = ArrayList<String>()
        l.observer = object : Layer.Observer {
            override fun changed(removed: Entry?, added: Entry?) { seen.add("${removed?.id}>${added?.id}") }
            override fun reset() { seen.add("reset") }
        }
        l.add(stroke(1, 0.0, 0.0))
        l.add(stroke(2, 0.0, 0.0))
        l.replaceBox(com.Atom2Universe.app.zoomcanvas.core.StrokeBox(stroke(1, 50.0, 0.0)))
        l.moveInOrder(1, 1)
        l.removeBox(2)
        assertEquals(listOf("null>1", "null>2", "1>1", "1>1", "2>null"), seen)
        l.load(emptyList())
        assertEquals("reset", seen.last())
    }

    @Test
    fun theBoundsOfALayerNeverShrinkUnderTheRealOnesAndGetExactOnRequest() {
        val l = scatter(500, seed = 8)
        val approx = l.boundsApprox()!!
        val exact = l.bounds()!!
        assertTrue(approx[0] <= exact[0] && approx[1] <= exact[1] && approx[2] >= exact[2] && approx[3] >= exact[3])
        val widest = l.entries().maxBy { it.x1 - it.x0 }
        l.removeBox(widest.id)
        val after = l.bounds()!!
        val approxAfter = l.boundsApprox()!!
        assertTrue(approxAfter[0] <= after[0] && approxAfter[2] >= after[2])
    }

    @Test
    fun aTapFindsTheStrokeUnderItAmongThousands() {
        val l = scatter(8000, seed = 2)
        val target = l.entries()[4321]
        val s = target.item as Stroke
        val hit = l.boxAt(s.x + s.pts[0], s.y + s.pts[1], 2.0)
        assertNotNull(hit)
        assertNull(l.boxAt(1e7, 1e7, 2.0))
    }

    // ---- Cache raster : la logique, sans Android ------------------------------------------------

    @Test
    fun aDenseLayerIsHandedToTheCacheAndGoesBackToVectorsWithAGap() {
        val scene = ZoomScene(10.0, Math.sqrt(10.0), 1.0)
        val rnd = Random(1)
        for (i in 0 until 1500) {
            val x = rnd.nextDouble() * 900 - 450
            val y = rnd.nextDouble() * 900 - 450
            scene.beginStroke(x, y, 0xFF000000.toInt(), 3.0)
            scene.extendStroke(x + rnd.nextDouble() * 30, y + rnd.nextDouble() * 30)
            scene.endStroke()
        }
        val out = RenderList()
        val policy = ZoomRenderer.CachePolicy(on = 600, off = 350)
        ZoomRenderer.build(scene, 1000.0, 1000.0, out, false, policy)
        assertNotNull(out.groupLayer[0])
        assertEquals(0, out.runCount)

        // Sans politique, ou en mode d'édition des gommes : tout se trace trait par trait.
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertTrue(out.runCount > 1000)
        assertNull(out.groupLayer[0])
        ZoomRenderer.build(scene, 1000.0, 1000.0, out, true, policy)
        assertNull(out.groupLayer[0])

        // Zoomé sur un coin : moins de 350 traits à l'écran, la couche repasse en vecteurs.
        scene.zoomAt(4.0, 0.0, 0.0)
        ZoomRenderer.build(scene, 1000.0, 1000.0, out, false, policy)
        assertNull(out.groupLayer[0])
        assertTrue(out.runCount in 1..600)
    }

    @Test
    fun theTrailingStrokeIsStillDrawnAsVectorsOnTopOfTheCache() {
        val scene = ZoomScene(10.0, Math.sqrt(10.0), 1.0)
        val rnd = Random(2)
        for (i in 0 until 800) {
            val x = rnd.nextDouble() * 900 - 450
            val y = rnd.nextDouble() * 900 - 450
            scene.beginStroke(x, y, 0xFF000000.toInt(), 3.0)
            scene.extendStroke(x + rnd.nextDouble() * 30, y + rnd.nextDouble() * 30)
            scene.endStroke()
        }
        scene.beginStroke(0.0, 0.0, 0xFFFF0000.toInt(), 5.0)
        scene.extendStroke(100.0, 100.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out, false, ZoomRenderer.CachePolicy(on = 600, off = 350))
        assertNotNull(out.groupLayer[0])
        assertEquals(1, out.groupEnd[0] - out.groupStart[0])
        assertEquals(0xFFFF0000.toInt(), out.runColor[out.groupStart[0]])
    }

    @Test
    fun bakingInChunksGivesTheSameRunsAsOneFullRender() {
        val l = scatter(3000, seed = 6)
        val all = ArrayList<Entry>().also { l.query(-4000.0, -3000.0, 4000.0, 3000.0, it) }
        assertTrue(all.size > 50)
        val b = ZoomRenderer.Builder()
        val full = RenderList()
        b.renderEntries(all, 0.0, 0.0, 1.0, 8000.0, 6000.0, full)
        var runs = 0
        var coords = 0
        val part = RenderList()
        var from = 0
        while (from < all.size) {
            val to = minOf(all.size, from + 7)
            b.renderEntries(all.subList(from, to), 0.0, 0.0, 1.0, 8000.0, 6000.0, part)
            runs += part.runCount; coords += part.coordCount
            from = to
        }
        assertEquals(full.runCount, runs)
        assertEquals(full.coordCount, coords)
    }

    @Test
    fun theCacheWindowMapsItsPixelsOntoTheScreen() {
        val w = CacheWindow.around(100.0, 50.0, 2.0, 1000.0, 600.0, maxPixels = 10_000_000)
        assertEquals(1300, w.w)
        assertEquals(780, w.h)
        assertEquals(2.0, w.zoom, 0.0)
        // Même caméra : l'image est centrée, à l'échelle 1.
        val t = w.transformTo(100.0, 50.0, 2.0, 1000.0, 600.0)
        assertEquals(1.0, t.scale, 1e-12)
        assertEquals(-150.0, t.tx, 1e-9)
        assertEquals(-90.0, t.ty, 1e-9)
        assertTrue(w.covers(100.0, 50.0, 2.0, 1000.0, 600.0, 0.0))
        // Réserve de 150 px de chaque côté en largeur, 90 px en hauteur.
        assertTrue(w.covers(100.0, 50.0, 2.0, 1000.0, 600.0, 90.0))
        assertFalse(w.covers(100.0, 50.0, 2.0, 1000.0, 600.0, 100.0))
        // Glissé de 40 unités (80 px) : la réserve de 150 px de ce côté fond à 70.
        assertTrue(w.covers(140.0, 50.0, 2.0, 1000.0, 600.0, 60.0))
        assertFalse(w.covers(140.0, 50.0, 2.0, 1000.0, 600.0, 80.0))
        // Zoomé deux fois : le point du cache sous le centre de l'écran y reste.
        val z = w.transformTo(100.0, 50.0, 4.0, 1000.0, 600.0)
        assertEquals(2.0, z.scale, 1e-12)
        assertEquals(500.0, z.tx + z.scale * w.w / 2, 1e-9)
        assertTrue(w.sharpEnough(2.4))
        assertFalse(w.sharpEnough(3.0))
        assertFalse(w.sharpEnough(0.9))
    }

    @Test
    fun aHugeScreenIsBakedAtAReducedResolutionButCoversTheSameWorld() {
        val w = CacheWindow.around(0.0, 0.0, 1.0, 2800.0, 1800.0, maxPixels = 4_000_000)
        assertTrue(w.w.toLong() * w.h <= 4_100_000)
        assertTrue(w.zoom < 1.0)
        // Il couvre quand même tout l'écran, avec sa réserve.
        assertTrue(w.covers(0.0, 0.0, 1.0, 2800.0, 1800.0, 0.0))
        assertEquals(1.0, w.viewZoom, 0.0)
        assertTrue(w.sharpEnough(1.0))
    }

    @Test
    fun oneContinuousGestureIsCappedSoItsRowStaysReadable() {
        val scene = ZoomScene(10.0, Math.sqrt(10.0), 1.0)
        scene.beginStroke(0.0, 0.0, 0xFF000000.toInt(), 3.0)
        for (i in 1..ZoomScene.MAX_STROKE_POINTS + 5000) scene.extendStroke(i * 1.0, (i % 2) * 5.0)
        val s = scene.endStroke()!!
        assertEquals(ZoomScene.MAX_STROKE_POINTS, s.pointCount)
    }
}
