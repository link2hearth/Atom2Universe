package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import androidx.room.Room
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Banc de mesure (exclu par défaut, voir `bancsDeMesure`) : combien coûtent le rendu, la sauvegarde
 * et la mémoire quand un projet accumule les coups de pinceau (le « Van Gogh »).
 *
 *     ./gradlew testDebugUnitTest -PbancsMesure --tests "*ZoomPerfBench*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomPerfBench {

    private val viewW = 1280.0
    private val viewH = 800.0

    /** Un projet de [n] traits de ~[pts] points, semés au hasard sur [areaScreens] écrans de large. */
    private fun paint(n: Int, pts: Int = 40, areaScreens: Int = 24): ZoomScene {
        val scene = ZoomScene(625.0)
        val rnd = Random(42)
        val spanX = areaScreens * viewW
        val spanY = areaScreens * viewH * 0.7
        var camX = 0.0
        var camY = 0.0
        for (i in 0 until n) {
            // La caméra saute au hasard dans la zone peinte (pan en pixels d'écran à zoom 1).
            val tx = (rnd.nextDouble() - 0.5) * spanX
            val ty = (rnd.nextDouble() - 0.5) * spanY
            scene.pan(camX - tx, camY - ty)
            camX = tx; camY = ty
            var x = rnd.nextDouble() * 600 - 300
            var y = rnd.nextDouble() * 400 - 200
            scene.beginStroke(x, y, 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF), 3.0 + rnd.nextInt(8), if (i % 5 == 0) Stroke.BRUSH else Stroke.PEN)
            for (k in 1 until pts) {
                x += rnd.nextGaussian() * 5 + 3
                y += rnd.nextGaussian() * 5
                scene.extendStroke(x, y)
            }
            scene.endStroke()
        }
        return scene
    }

    private fun usedMb(): Double {
        repeat(3) { System.gc(); Thread.sleep(50) }
        val r = Runtime.getRuntime()
        return (r.totalMemory() - r.freeMemory()) / 1048576.0
    }

    private inline fun median(runs: Int, f: () -> Unit): Double {
        val t = DoubleArray(runs)
        for (i in 0 until runs) { val s = System.nanoTime(); f(); t[i] = (System.nanoTime() - s) / 1e6 }
        t.sort()
        return t[runs / 2]
    }

    @Test
    fun bench() {
        for (n in intArrayOf(5_000, 30_000, 100_000)) {
            val before = usedMb()
            val scene = paint(n)
            val after = usedMb()
            val out = RenderList()
            // Vue normale (taille 1) et vue dézoomée au maximum de la couche (1/25) : tout y est.
            scene.setCamera(0, 0.0, 0.0, 1.0)
            val closeMs = median(15) { ZoomRenderer.build(scene, viewW, viewH, out) }
            val closeRuns = out.runCount
            scene.setCamera(0, 0.0, 0.0, 0.05)
            val farMs = median(15) { ZoomRenderer.build(scene, viewW, viewH, out) }
            val farRuns = out.runCount
            // Avec le cache raster : la couche dense ne coûte plus que son décompte.
            val policy = ZoomRenderer.CachePolicy()
            val farCachedMs = median(15) { ZoomRenderer.build(scene, viewW, viewH, out, false, policy) }

            val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
            val dir = File.createTempFile("zcbench", "d").also { it.delete(); it.mkdirs() }
            val store = ZoomStore(dir, db)
            val p = store.create("bench", 625.0)
            // Les traits du banc ont été posés dans une autre scène : on les recopie dans celle du projet.
            val t0 = System.nanoTime()
            val full = scene.drainChanges()
            store.save(p.meta, full)
            val firstSaveMs = (System.nanoTime() - t0) / 1e6
            // Un trait de plus : c'est ce que coûte chaque trait ensuite.
            scene.setCamera(0, 0.0, 0.0, 1.0)
            val oneMs = median(9) {
                scene.beginStroke(0.0, 0.0, 0xFF000000.toInt(), 4.0)
                for (k in 1..40) scene.extendStroke(k * 3.0, (k % 5) * 2.0)
                scene.endStroke()
                store.save(p.meta, scene.drainChanges())
            }
            val loadMs = median(2) { store.load(p.meta.id) }
            db.close()
            dir.deleteRecursively()
            println(
                "N=%d  heap=%.0f Mo | build proche %.2f ms (%d passes)  lointain %.2f ms (%d passes), avec cache %.2f ms | 1re sauvegarde %.0f ms | un trait de plus %.1f ms | load %.0f ms"
                    .format(n, after - before, closeMs, closeRuns, farMs, farRuns, farCachedMs, firstSaveMs, oneMs, loadMs),
            )
        }
    }

    /** Un tableau profond : [layers] couches de [perLayer] traits ; mémoire avec tout chargé, puis avec seulement les couches proches de la caméra. */
    @Test
    fun pagingBench() {
        val layers = 10
        val perLayer = 30_000
        val rnd = Random(3)
        val before = usedMb()
        val list = ArrayList<com.Atom2Universe.app.zoomcanvas.core.Layer>()
        val source = HashMap<Long, List<Pair<com.Atom2Universe.app.zoomcanvas.core.LayerItem, Long>>>()
        var id = 1L
        for (d in 0 until layers) {
            val items = ArrayList<Pair<com.Atom2Universe.app.zoomcanvas.core.LayerItem, Long>>(perLayer)
            for (i in 0 until perLayer) {
                val pts = FloatArray(80)
                var x = 0f; var y = 0f
                for (k in 0 until 40) { pts[2 * k] = x; pts[2 * k + 1] = y; x += (rnd.nextGaussian() * 5 + 3).toFloat(); y += (rnd.nextGaussian() * 5).toFloat() }
                items.add(Stroke(id++, rnd.nextDouble() * 20000 - 10000, rnd.nextDouble() * 20000 - 10000, pts, 0xFF000000.toInt(), 4.0) to i * com.Atom2Universe.app.zoomcanvas.core.Layer.Z_STEP)
            }
            source[d.toLong()] = items
            list.add(com.Atom2Universe.app.zoomcanvas.core.Layer(d.toLong(), 0.0, 0.0).also { it.load(items) })
        }
        val scene = ZoomScene(625.0)
        scene.restore(0, list, 5, 0.0, 0.0, 1.0, id)
        list.clear()
        val allMb = usedMb() - before
        // Les éléments « de la base » comptent dans la mesure : on les lâche pour ne garder que ce que la scène tient.
        val kept = HashMap(source)
        source.clear()
        val fake = object : ZoomScene.LayerPager {
            override fun wantLoad(depth: Long) {}
            override fun loadNow(depth: Long) = kept[depth]
        }
        scene.pager = fake
        scene.refreshResidency()
        kept.clear()
        val pagedMb = usedMb() - before
        println("PAGING %d couches x %d traits : tout en mémoire %.0f Mo, couches proches seulement %.0f Mo (%d chargées)"
            .format(layers, perLayer, allMb, pagedMb, scene.allLayers().count { it.isLoaded }))
    }
}
