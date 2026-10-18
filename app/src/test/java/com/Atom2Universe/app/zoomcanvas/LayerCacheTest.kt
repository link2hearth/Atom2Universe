package com.Atom2Universe.app.zoomcanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.StrokeBox
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Le cache raster d'une couche dense, avec de vrais pixels : ce qu'il montre doit être ce que le
 * tracé trait par trait montre, et rester juste quand on ajoute, retire ou déplace un trait.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayerCacheTest {

    private val w = 600
    private val h = 400

    /** Un hôte de test : les « fils de l'écran » s'exécutent tout de suite, et on peut attendre la fin d'une cuisson. */
    private class TestHost : LayerCache.Host {
        override val imageProvider: ((String) -> Bitmap?)? = null
        override val typefaceProvider: ((String, Int) -> Typeface)? = null
        @Volatile var latch = CountDownLatch(1)
        override fun cacheReady() { latch.countDown() }
        override fun runOnMain(r: Runnable) { r.run() }
        fun await() { assertTrue("cuisson trop longue", latch.await(30, TimeUnit.SECONDS)); latch = CountDownLatch(1) }
    }

    private fun scene(n: Int = 900): ZoomScene {
        val scene = ZoomScene(10.0, Math.sqrt(10.0), 1.0)
        val rnd = Random(11)
        for (i in 0 until n) {
            val x = rnd.nextDouble() * 800 - 400
            val y = rnd.nextDouble() * 500 - 250
            scene.beginStroke(x, y, 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF), 2.0 + rnd.nextInt(5), if (i % 4 == 0) 1 else 0)
            for (k in 1..6) scene.extendStroke(x + k * 6 + rnd.nextDouble() * 4, y + rnd.nextDouble() * 20)
            scene.endStroke()
        }
        return scene
    }

    private fun vector(scene: ZoomScene): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val list = RenderList()
        ZoomRenderer.build(scene, w.toDouble(), h.toDouble(), list)
        val painter = RunPainter()
        for (g in 0 until list.groupCount) {
            // Comme la vue : une couche avec gomme est composée dans un calque à part.
            if (list.groupIsolated[g]) {
                val saved = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (list.groupAlpha[g] * 255).toInt())
                painter.drawRuns(canvas, list, list.groupStart[g], list.groupEnd[g])
                canvas.restoreToCount(saved)
            } else painter.drawRuns(canvas, list, list.groupStart[g], list.groupEnd[g])
        }
        return bmp
    }

    private fun cached(scene: ZoomScene, cache: LayerCache): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        // Comme la vue : le cache d'une couche avec gomme se pose dans le même calque que ses passes.
        val isolated = scene.layer(0)!!.hasEraser
        val saved = if (isolated) canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), 255) else -1
        cache.draw(canvas, scene.cx, scene.cy, scene.zoom, w.toDouble(), h.toDouble(), 255)
        if (isolated) canvas.restoreToCount(saved)
        return bmp
    }

    /** Part des pixels qui diffèrent nettement (l'anti-crénelage d'un trait cuit puis retracé ne tombe pas juste au pixel près). */
    private fun differing(a: Bitmap, b: Bitmap): Double {
        var bad = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            val d = maxOf(
                Math.abs((p shr 16 and 255) - (q shr 16 and 255)),
                Math.abs((p shr 8 and 255) - (q shr 8 and 255)),
                Math.abs((p and 255) - (q and 255)),
            )
            if (d > 60) bad++
        }
        return bad.toDouble() / (w * h)
    }

    private fun inked(a: Bitmap): Int {
        var n = 0
        for (y in 0 until h) for (x in 0 until w) if (a.getPixel(x, y) != 0xFFFFFFFF.toInt()) n++
        return n
    }

    @Test
    fun theCachedLayerLooksLikeTheVectorOne() {
        val scene = scene()
        val host = TestHost()
        val cache = LayerCache(scene.layer(0)!!, host, 8_000_000)
        val reference = vector(scene)
        assertTrue("le banc doit dessiner quelque chose", inked(reference) > 2000)

        // Première image : rien à montrer, la cuisson part.
        assertEquals(0, inked(cached(scene, cache)))
        host.await()
        val baked = cached(scene, cache)
        assertTrue("trop de différences : ${differing(reference, baked)}", differing(reference, baked) < 0.01)
    }

    @Test
    fun anAddedStrokeAppearsAtOnceAndARemovedOneDisappears() {
        val scene = scene()
        val host = TestHost()
        val layer = scene.layer(0)!!
        val cache = LayerCache(layer, host, 8_000_000)
        cached(scene, cache)
        host.await()

        // Un trait de plus, tout devant : tracé directement dans le cache.
        scene.beginStroke(-300.0, -200.0, 0xFFFF0000.toInt(), 12.0)
        scene.extendStroke(300.0, 150.0)
        val red = scene.endStroke()!!
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.01)

        // Retiré : la zone qu'il couvrait est recalculée, avec tout ce qui s'y croise, dans l'ordre.
        scene.deleteBox(red.id)
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.01)

        // Déplacé : l'ancienne place se vide, la nouvelle se remplit.
        val victim = layer.entries()[100].id
        scene.beginBoxEdit(victim)
        scene.moveBoxBy(120.0, 60.0)
        scene.endBoxEdit()
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.01)

        // Une gomme creuse ce qui est avant elle, dans le cache comme à l'écran.
        scene.beginStroke(-400.0, 0.0, ZoomScene.ERASER, 40.0)
        scene.extendStroke(400.0, 0.0)
        scene.endStroke()
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.01)

        // Annuler la gomme rend ce qu'elle avait creusé.
        assertTrue(scene.undo())
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.01)
    }

    @Test
    fun aGestureFarFromTheBakedViewRebakesAndCatchesUp() {
        val scene = scene()
        val host = TestHost()
        val cache = LayerCache(scene.layer(0)!!, host, 8_000_000)
        cached(scene, cache)
        host.await()
        // On a bien dézoomé, puis glissé : la fenêtre cuite ne suffit plus.
        scene.zoomAt(0.4, 10.0, 10.0)
        scene.pan(250.0, -80.0)
        cached(scene, cache)
        host.await()
        assertTrue(differing(vector(scene), cached(scene, cache)) < 0.02)
    }

    @Test
    fun releasingTheCacheStopsItsObserver() {
        val scene = scene(50)
        val layer = scene.layer(0)!!
        val cache = LayerCache(layer, TestHost(), 4_000_000)
        cache.release()
        // Plus personne n'écoute : poser un trait ne doit rien déclencher.
        scene.beginStroke(0.0, 0.0, 0xFF000000.toInt(), 3.0)
        scene.extendStroke(40.0, 40.0)
        scene.endStroke()
        assertTrue(layer.observer == null)
        assertTrue(layer.size == 51)
    }
}
