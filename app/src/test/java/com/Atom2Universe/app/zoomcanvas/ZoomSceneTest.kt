package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.core.ZoomSnapshot
import com.Atom2Universe.app.zoomcanvas.core.ZoomProjectMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * Le cœur du canvas infini à couches : caméra unique, changement de couche, conversions,
 * ancrages. La règle n°1 — jamais de grandes coordonnées — y est vérifiée à des centaines de niveaux.
 */
class ZoomSceneTest {

    private val black = 0xFF000000.toInt()

    /** Un geste de dessin, en pixels depuis le centre de l'écran (environ 300 px de large). */
    private val gesture = listOf(
        -120.0 to 35.5, -60.25 to 80.125, 13.0 to -44.0, 91.375 to -12.75, 150.75 to 10.5,
    )

    private fun ZoomScene.draw(points: List<Pair<Double, Double>>, widthPx: Double = 4.0): Stroke {
        beginStroke(points[0].first, points[0].second, black, widthPx)
        for (p in points.drop(1)) extendStroke(p.first, p.second)
        return endStroke()!!
    }

    /** Plus grand écart entre le trait tel qu'il s'affiche et le geste, rapporté à la taille du geste. */
    private fun relativeError(scene: ZoomScene, depth: Long, s: Stroke, points: List<Pair<Double, Double>>): Double {
        assertEquals(points.size, s.pointCount)
        var worst = 0.0
        for (i in points.indices) {
            val p = scene.toScreen(depth, s, i)
            worst = max(worst, hypot(p[0] - points[i].first, p[1] - points[i].second))
        }
        return worst / 300.0
    }

    /** Zoome (facteur 2 autour d'un point décentré) jusqu'à atteindre la profondeur voulue. */
    private fun ZoomScene.zoomInto(target: Long, fx: Double = 37.0, fy: Double = -21.0): Int {
        var steps = 0
        while (depth < target) { zoomAt(2.0, fx, fy); steps++ }
        return steps
    }

    private fun ZoomScene.zoomOutSteps(steps: Int, fx: Double = 37.0, fy: Double = -21.0) {
        repeat(steps) { zoomAt(0.5, fx, fy) }
    }

    // ---- Précision ------------------------------------------------------------------------

    @Test
    fun sameRelativeErrorAtLevel0AndLevel200() {
        val scene = ZoomScene(10.0)
        val s0 = scene.draw(gesture)
        val err0 = relativeError(scene, 0, s0, gesture)

        val steps = scene.zoomInto(200)
        assertEquals(200L, scene.depth)
        val s200 = scene.draw(gesture)
        val err200 = relativeError(scene, 200, s200, gesture)

        assertTrue("niveau 0 : $err0", err0 < 1e-9)
        assertTrue("niveau 200 : $err200", err200 < 1e-9)
        // Les nombres restent de la taille d'un écran, même 200 niveaux plus bas.
        assertTrue(abs(s200.x) < 1e4 && abs(s200.y) < 1e4)
        assertTrue(abs(scene.cx) < 1e4 && abs(scene.cy) < 1e4)

        // Retour en haut : le trait du niveau 0 est toujours exactement là où on l'a dessiné.
        scene.zoomOutSteps(steps)
        assertEquals(0L, scene.depth)
        assertTrue(relativeError(scene, 0, s0, gesture) < 1e-9)
    }

    @Test
    fun zoomIn200LevelsThenBackFallsOnTheSameView() {
        val scene = ZoomScene(10.0)
        val s0 = scene.draw(gesture)
        scene.zoomAt(1.7, 12.0, 8.0)
        scene.pan(33.0, -17.5)
        val d0 = scene.depth
        val x0 = scene.cx; val y0 = scene.cy; val z0 = scene.zoom
        val before = (0 until s0.pointCount).map { scene.toScreen(0, s0, it) }

        val rnd = Random(7)
        val ops = ArrayList<Triple<Double, Double, Double>>()
        while (scene.depth < d0 + 200) {
            val f = 1.2 + rnd.nextDouble() * 2.0
            val fx = rnd.nextDouble(-400.0, 400.0)
            val fy = rnd.nextDouble(-700.0, 700.0)
            scene.zoomAt(f, fx, fy)
            ops.add(Triple(f, fx, fy))
        }
        for ((f, fx, fy) in ops.asReversed()) scene.zoomAt(1.0 / f, fx, fy)

        assertEquals(d0, scene.depth)
        assertEquals(z0, scene.zoom, 1e-12 * z0)
        assertEquals(x0, scene.cx, 1e-9)
        assertEquals(y0, scene.cy, 1e-9)
        for (i in before.indices) {
            val p = scene.toScreen(0, s0, i)
            assertEquals(before[i][0], p[0], 1e-9)
            assertEquals(before[i][1], p[1], 1e-9)
        }
        // Les couches vides traversées n'ont pas été gardées.
        assertEquals(listOf(0L), scene.allLayers().map { it.depth })
    }

    @Test
    fun lateralPanOfOneBillionUnitsDoesNotDrift() {
        val scene = ZoomScene(10.0)
        scene.zoomAt(3.7, 0.0, 0.0)
        val s0 = scene.draw(gesture)

        // 10^9 unités en 1000 glissés.
        val stepPx = 1e6 * scene.zoom
        repeat(1000) { scene.pan(-stepPx, 0.0) }
        assertEquals(1e9, scene.cx, 1e-3)

        // Un trait dessiné là-bas s'affiche exactement sous le doigt, et sa forme reste locale.
        val far = scene.draw(gesture)
        assertTrue(relativeError(scene, 0, far, gesture) < 1e-9)
        assertTrue(far.pts.all { abs(it) < 1000 })
        assertEquals(1e9, far.x - s0.x, 1e-3)

        // On peut y plonger : les couches du dessous repartent de petits nombres.
        val steps = scene.zoomInto(30)
        assertTrue(abs(scene.cx) < 1e4 && abs(scene.cy) < 1e4)
        val deep = scene.draw(gesture)
        assertTrue(relativeError(scene, 30, deep, gesture) < 1e-9)

        // Remontée : le trait lointain est toujours sous le doigt.
        scene.zoomOutSteps(steps)
        assertEquals(0L, scene.depth)
        assertTrue(relativeError(scene, 0, far, gesture) < 1e-9)

        // Retour au point de départ : aucune dérive visible (bien moins d'un millième de pixel).
        repeat(1000) { scene.pan(stepPx, 0.0) }
        assertTrue(relativeError(scene, 0, s0, gesture) < 1e-5 / 300)
    }

    // ---- Tailles et changement de couche --------------------------------------------------

    @Test
    fun zoomScalesEveryLayerAroundTheSamePointAcrossLayerSwitches() {
        val scene = ZoomScene(10.0)
        val strokes = ArrayList<Pair<Long, Stroke>>()
        strokes.add(0L to scene.draw(gesture))
        scene.zoomInto(1, 0.0, 0.0)
        strokes.add(1L to scene.draw(gesture.map { it.first * 0.5 to it.second * 0.5 }))
        scene.zoomInto(2, 0.0, 0.0)
        strokes.add(2L to scene.draw(gesture.map { it.first * 0.3 to it.second * 0.3 }))
        while (scene.depth > 0 || scene.zoom > 1.5) scene.zoomAt(0.8, 0.0, 0.0)

        val fx = 23.0
        val fy = -41.0
        repeat(40) {
            val f = 1.37
            val before = strokes.map { (d, s) -> (0 until s.pointCount).map { i -> scene.toScreen(d, s, i) } }
            scene.zoomAt(f, fx, fy)
            strokes.forEachIndexed { k, (d, s) ->
                for (i in 0 until s.pointCount) {
                    val p = scene.toScreen(d, s, i)
                    val ex = fx + (before[k][i][0] - fx) * f
                    val ey = fy + (before[k][i][1] - fy) * f
                    val tol = 1e-9 * max(1.0, hypot(ex, ey))
                    assertEquals(ex, p[0], tol)
                    assertEquals(ey, p[1], tol)
                }
            }
        }
        assertTrue(scene.depth >= 2)
    }

    @Test
    fun aStrokeDrawnOneLayerDownLooksRatioTimesSmallerFromAbove() {
        for (ratio in ZoomScene.RATIOS) {
            val scene = ZoomScene(ratio)
            scene.zoomAt(2.0, 0.0, 0.0)
            scene.draw(gesture) // quelque chose au niveau 0
            scene.zoomAt(ratio, 0.0, 0.0)
            assertEquals(1L, scene.depth)
            assertEquals(2.0, scene.zoom, 1e-12)
            val s1 = scene.draw(gesture)
            val lenBelow = screenLength(scene, 1, s1)
            scene.zoomAt(1 / ratio, 0.0, 0.0)
            assertEquals(0L, scene.depth)
            assertEquals(2.0, scene.zoom, 1e-12)
            assertEquals(lenBelow / ratio, screenLength(scene, 1, s1), 1e-9)
        }
    }

    @Test
    fun aSceneDrawnInsideALittleFrameStaysInsideIt() {
        val scene = ZoomScene(10.0)
        // Un petit cadre de 20 px dessiné au niveau 0, autour d'un point décentré.
        val c = 80.0 to -50.0
        val frame = listOf(c.first - 10 to c.second - 10, c.first + 10 to c.second - 10, c.first + 10 to c.second + 10, c.first - 10 to c.second + 10, c.first - 10 to c.second - 10)
        val sFrame = scene.draw(frame, 1.0)
        // On zoome ×10 sur le cadre : on est dans la couche 1, le cadre fait 200 px.
        scene.zoomAt(10.0, c.first, c.second)
        assertEquals(1L, scene.depth)
        val corners = (0 until 5).map { scene.toScreen(0, sFrame, it) }
        assertEquals(200.0, corners[1][0] - corners[0][0], 1e-9)
        // On dessine la scène intérieure le long des bords du cadre, dans la couche 1.
        val inner = scene.draw(corners.map { it[0] to it[1] }, 1.0)
        // Dézoom : la scène intérieure retombe exactement sur le cadre.
        scene.zoomAt(0.1, c.first, c.second)
        assertEquals(0L, scene.depth)
        for (i in 0 until 5) {
            val a = scene.toScreen(0, sFrame, i)
            val b = scene.toScreen(1, inner, i)
            assertEquals(a[0], b[0], 1e-9)
            assertEquals(a[1], b[1], 1e-9)
            assertEquals(frame[i].first, a[0], 1e-9)
        }
    }

    @Test
    fun zoomStaysWithinOneLayerRangeAndDepthIsContinuous() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        var lastLevel = scene.level()
        repeat(500) {
            scene.zoomAt(1.05, 11.0, 7.0)
            assertTrue(scene.zoom >= scene.minZoom && scene.zoom < scene.maxZoom)
            val lvl = scene.level()
            assertTrue(lvl > lastLevel && lvl - lastLevel < 0.03)
            lastLevel = lvl
        }
        repeat(1500) {
            scene.zoomAt(1 / 1.05, 11.0, 7.0)
            val lvl = scene.level()
            assertTrue(lvl < lastLevel && lastLevel - lvl < 0.03)
            lastLevel = lvl
        }
        assertTrue(scene.depth < 0)
    }

    /**
     * Le scénario de référence : on ouvre, on dessine un cercle de 14 px d'épaisseur à la taille
     * normale ; il reste pleinement visible jusqu'à ×7,9, s'efface ensuite et a disparu à ×10.
     */
    @Test
    fun aLayerFadesOnlyAtTheVeryEndOfItsTimesTenZoom() {
        val scene = ZoomScene(10.0)
        val circle = (0..36).map { 200 * Math.cos(it * Math.PI / 18) to 200 * Math.sin(it * Math.PI / 18) }
        scene.draw(circle, 14.0)
        assertEquals(1.0, scene.zoom, 0.0)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)

        scene.zoomAt(Math.pow(10.0, 0.9) * 0.999, 0.0, 0.0)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)
        scene.zoomAt(1.03, 0.0, 0.0)
        assertTrue(scene.layerAlpha(0) in 0.0..0.999)
        // À ×10 : disparu, et la couche 1 est à sa taille normale.
        scene.zoomAt(10.0 / (Math.pow(10.0, 0.9) * 0.999 * 1.03), 0.0, 0.0)
        assertEquals(0.0, scene.layerAlpha(0), 1e-9)
        assertEquals(1L, scene.depth)
        assertEquals(1.0, scene.zoom, 1e-9)
        assertEquals(1.0, scene.layerAlpha(1), 0.0)
    }

    @Test
    fun aLayerAppearsFarAwayAndGrowsToNormalSize() {
        val scene = ZoomScene(10.0)
        val s = scene.draw(gesture, 14.0)
        // Dézoom ×10 : la couche 0 est au loin, dix fois plus petite, entièrement visible.
        scene.zoomAt(0.1, 0.0, 0.0)
        assertEquals(-1L, scene.depth)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)
        val a = scene.toScreen(0, s, 0)
        assertEquals(gesture[0].first / 10, a[0], 1e-9)
        // Encore ×10 : elle fait moins d'un pixel et n'est plus dessinée.
        scene.zoomAt(0.001, 0.0, 0.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(0, out.layersDrawn)
    }

    @Test
    fun theEditableLayerSwitchesInTheMiddleOfTheFade() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        // Un léger dézoom ne change pas de couche : on dessine toujours dans la couche 0.
        scene.zoomAt(0.9, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        // ×8 : la couche 0 commence à peine à s'effacer, elle reste la couche éditable.
        scene.zoomAt(8.0 / 0.9, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        // ×9 : elle est à plus de moitié effacée, on édite la couche 1.
        scene.zoomAt(9.0 / 8.0, 0.0, 0.0)
        assertEquals(1L, scene.depth)
        assertTrue(scene.layerAlpha(0) < 0.5)
        assertEquals(1.0, scene.layerAlpha(1), 0.0)
    }

    @Test
    fun whatIDrawIsAlwaysVisible() {
        // Un trait ne doit jamais atterrir dans une couche effacée.
        val scene = ZoomScene(10.0)
        val out = RenderList()
        repeat(60) {
            scene.zoomAt(if (it % 2 == 0) 1.37 else 0.61, 13.0, 8.0)
            scene.draw(gesture)
            ZoomRenderer.build(scene, 1000.0, 1000.0, out)
            val x0 = (500 + gesture[0].first).toFloat()
            val y0 = (500 + gesture[0].second).toFloat()
            val found = (0 until out.runCount).any { r ->
                abs(out.coords[out.runStart[r]] - x0) < 1e-3f && abs(out.coords[out.runStart[r] + 1] - y0) < 1e-3f && out.runAlpha[r] >= 0.5f
            }
            assertTrue("étape $it, niveau ${scene.depth}", found)
        }
    }

    @Test
    fun switchingLayerIsSeamless() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture.map { it.first * 0.4 to it.second * 0.4 })
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture.map { it.first * 0.2 to it.second * 0.2 })
        // Retour au niveau 0, juste avant le seuil.
        scene.zoomAt(0.01, 0.0, 0.0)
        scene.zoomAt(scene.maxZoom / scene.zoom * (1 - 1e-7), 0.0, 0.0)
        assertEquals(0L, scene.depth)
        val before = visibleRuns(scene)
        scene.zoomAt(1 + 2e-7, 0.0, 0.0)
        assertEquals(1L, scene.depth)
        val after = visibleRuns(scene)
        assertEquals(before.size, after.size)
        for (i in before.indices) {
            assertEquals(before[i].size, after[i].size)
            for (k in before[i].indices) assertEquals(before[i][k], after[i][k], 1e-3f)
        }
    }

    // ---- Ancrage, réalignement, couches virtuelles -----------------------------------------

    @Test
    fun newLayerIsAnchoredWhereTheCameraIsWhenDrawingStarts() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomInto(3)
        scene.pan(-5000.0, 3000.0) // on se promène dans la couche 3, encore vide
        val s = scene.draw(gesture)
        // Le repère de la couche a été recentré sur la caméra : petites coordonnées.
        assertEquals(0.0, scene.cx, 1e-12)
        assertEquals(0.0, scene.cy, 1e-12)
        assertTrue(abs(s.x) < 1000 && abs(s.y) < 1000)
        assertTrue(relativeError(scene, 3, s, gesture) < 1e-9)
    }

    @Test
    fun anchoringAnEmptyLayerDoesNotMoveTheLayersBelow() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomInto(2, 0.0, 0.0)
        val deep = scene.draw(gesture)
        scene.zoomAt(0.1, 0.0, 0.0) // retour dans la couche 1, vide
        assertEquals(1L, scene.depth)
        scene.pan(40.0, 25.0)
        val before = scene.toScreen(2, deep, 2)
        scene.draw(listOf(0.0 to 0.0, 10.0 to 10.0))
        val after = scene.toScreen(2, deep, 2)
        assertEquals(before[0], after[0], 1e-9)
        assertEquals(before[1], after[1], 1e-9)
    }

    @Test
    fun movingTheBackgroundLayerRealignsItAndCanBeUndone() {
        val scene = ZoomScene(10.0)
        val top = scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        val below = scene.draw(gesture)
        scene.zoomAt(0.5, 0.0, 0.0)
        assertEquals(0L, scene.depth)

        val topBefore = scene.toScreen(0, top, 1)
        val belowBefore = scene.toScreen(1, below, 1)
        assertTrue(scene.beginMoveLayer())
        scene.moveLayerBy(30.0, -12.0)
        scene.moveLayerBy(5.0, 2.0)
        scene.endMoveLayer()
        val topAfter = scene.toScreen(0, top, 1)
        val belowAfter = scene.toScreen(1, below, 1)
        assertEquals(topBefore[0], topAfter[0], 1e-9)
        assertEquals(belowBefore[0] + 35.0, belowAfter[0], 1e-9)
        assertEquals(belowBefore[1] - 10.0, belowAfter[1], 1e-9)

        assertTrue(scene.undo())
        val undone = scene.toScreen(1, below, 1)
        assertEquals(belowBefore[0], undone[0], 1e-9)
        assertEquals(belowBefore[1], undone[1], 1e-9)
        assertTrue(scene.redo())
        assertEquals(belowAfter[0], scene.toScreen(1, below, 1)[0], 1e-9)
    }

    @Test
    fun onlyLayersWithContentAndTheAnchorsBetweenThemAreKept() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomInto(50)
        scene.zoomAt(1e-60, 0.0, 0.0)
        assertTrue(scene.depth < -5)
        while (scene.depth < 0) scene.zoomAt(2.0, 0.0, 0.0)
        assertEquals(listOf(0L), scene.allLayers().map { it.depth })

        scene.zoomInto(6)
        scene.draw(gesture)
        scene.zoomAt(1e-3, 0.0, 0.0)
        // Couches 0 à 6 : 0 et 6 dessinées, 1 à 5 vides mais gardées pour leur ancre.
        assertEquals((0L..6L).toList(), scene.allLayers().map { it.depth })
        assertEquals(listOf(0L, 6L), scene.nonEmptyDepths())
        val snap = ZoomSnapshot.of(ZoomProjectMeta("x", "x", 0, 0), scene)
        assertEquals(0L, snap.firstDepth)
        assertEquals(7, snap.layers.size)
    }

    @Test
    fun jumpToFramesTheLayerContent() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomInto(4)
        scene.pan(300.0, 0.0)
        val s = scene.draw(gesture)
        while (scene.depth > 0) scene.zoomAt(0.5, 0.0, 0.0)
        scene.jumpTo(4, 1080.0, 1920.0)
        assertEquals(4L, scene.depth)
        assertEquals(1.0, scene.layerAlpha(4), 0.0)
        for (i in 0 until s.pointCount) {
            val p = scene.toScreen(4, s, i)
            assertTrue(abs(p[0]) < 540 && abs(p[1]) < 960)
        }
    }

    // ---- Dessin, gomme, historique --------------------------------------------------------

    @Test
    fun eraserRemovesTouchedStrokesOfTheWorkingLayerOnly() {
        val scene = ZoomScene(10.0)
        val a = scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0))
        scene.zoomAt(10.0, 0.0, 0.0)
        val b = scene.draw(listOf(-100.0 to 50.0, 100.0 to 50.0))
        scene.zoomAt(0.1, 0.0, 0.0)
        assertEquals(0L, scene.depth)

        scene.beginErase()
        assertFalse(scene.eraseAt(0.0, 30.0, 5.0))
        assertTrue(scene.eraseAt(0.0, 3.0, 5.0))
        scene.endErase()
        assertTrue(scene.layer(0)!!.strokes.isEmpty())
        assertEquals(listOf(b.id), scene.layer(1)!!.strokes.map { it.id })

        assertTrue(scene.undo())
        assertEquals(listOf(a.id), scene.layer(0)!!.strokes.map { it.id })
        assertTrue(scene.undo()) // le trait b
        assertTrue(scene.layer(1)!!.strokes.isEmpty())
        assertTrue(scene.redo())
        assertEquals(listOf(b.id), scene.layer(1)!!.strokes.map { it.id })
    }

    @Test
    fun undoingTheOnlyStrokeOfALayerKeepsItsFrameForRedo() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomInto(3)
        val s = scene.draw(gesture)
        val before = scene.toScreen(3, s, 2)
        scene.undo()
        scene.zoomAt(1e-4, 0.0, 0.0)
        scene.zoomAt(1e4, 0.0, 0.0)
        scene.redo()
        assertNotNull(scene.layer(3))
        val after = scene.toScreen(3, s, 2)
        assertEquals(before[0], after[0], 1e-6)
        assertEquals(before[1], after[1], 1e-6)
    }

    @Test
    fun cancelledStrokeLeavesNothing() {
        val scene = ZoomScene(10.0)
        scene.beginStroke(0.0, 0.0, black, 3.0)
        scene.extendStroke(10.0, 10.0)
        assertNotNull(scene.liveStroke())
        scene.cancelStroke()
        assertNull(scene.liveStroke())
        assertNull(scene.endStroke())
        assertFalse(scene.canUndo)
    }

    // ---- Rendu -----------------------------------------------------------------------------

    @Test
    fun rendererNeverEmitsLargeFloats() {
        val w = 1080.0
        val h = 2200.0
        val scene = ZoomScene(10.0)
        val out = RenderList()
        val rnd = Random(42)
        var minDepth = 0L
        var maxDepth = 0L
        repeat(6000) { step ->
            val goingDown = step < 3000
            when (rnd.nextInt(10)) {
                in 0..4 -> {
                    val f = if (goingDown) rnd.nextDouble(0.8, 2.2) else rnd.nextDouble(0.15, 1.1)
                    scene.zoomAt(f, rnd.nextDouble(-w / 2, w / 2), rnd.nextDouble(-h / 2, h / 2))
                }
                in 5..6 -> scene.pan(rnd.nextDouble(-800.0, 800.0), rnd.nextDouble(-800.0, 800.0))
                7 -> {
                    // Des traits de toutes tailles, jusqu'à bien plus grands que l'écran.
                    val size = rnd.nextDouble(2.0, 6000.0)
                    val x0 = rnd.nextDouble(-w, w)
                    val y0 = rnd.nextDouble(-h, h)
                    scene.beginStroke(x0, y0, black, rnd.nextDouble(1.0, 60.0))
                    repeat(rnd.nextInt(1, 30)) { scene.extendStroke(x0 + rnd.nextDouble(-size, size), y0 + rnd.nextDouble(-size, size)) }
                    scene.endStroke()
                }
                8 -> if (rnd.nextBoolean()) scene.undo() else scene.redo()
                else -> { scene.beginErase(); scene.eraseAt(rnd.nextDouble(-w / 2, w / 2), 0.0, 30.0); scene.endErase() }
            }
            minDepth = minOf(minDepth, scene.depth)
            maxDepth = maxOf(maxDepth, scene.depth)
            assertTrue(abs(scene.cx) < 1e7 && abs(scene.cy) < 1e7)
            ZoomRenderer.build(scene, w, h, out)
            val limit = 4000f
            for (i in 0 until out.coordCount) {
                val v = out.coords[i]
                assertTrue("valeur $v à l'étape $step (niveau ${scene.depth})", v.isFinite() && abs(v) <= limit)
            }
            for (r in 0 until out.runCount) assertTrue(out.runWidth[r] <= 4 * h)
        }
        assertTrue("profondeur max $maxDepth", maxDepth >= 30)
        assertTrue("profondeur min $minDepth", minDepth <= -3)
    }

    @Test
    fun rendererDrawsBackLayersBehindAndSkipsSubPixelOnes() {
        val scene = ZoomScene(10.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture)
        repeat(6) { scene.zoomAt(10.0, 0.0, 0.0) }
        scene.draw(gesture) // niveau 8 : minuscule vu d'en haut
        while (scene.depth > 0) scene.zoomAt(0.1, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        val out = RenderList()
        ZoomRenderer.build(scene, 1080.0, 1920.0, out)
        // Niveaux 0, 1 et 2 dessinés (2 fait encore quelques pixels) ; le niveau 8 est sauté.
        assertEquals(3, out.layersDrawn)
        // Ordre : du fond vers l'avant, donc le premier tracé est le plus petit.
        val firstSpan = spanOfRun(out, 0)
        val lastSpan = spanOfRun(out, out.runCount - 1)
        assertTrue(firstSpan < lastSpan)
    }

    private fun spanOfRun(out: RenderList, r: Int): Float {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE
        val s = out.runStart[r]
        for (k in 0 until out.runPoints[r]) { x0 = minOf(x0, out.coords[s + 2 * k]); x1 = maxOf(x1, out.coords[s + 2 * k]) }
        return x1 - x0
    }

    private fun visibleRuns(scene: ZoomScene): List<FloatArray> {
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        return (0 until out.runCount).filter { out.runAlpha[it] > 1e-3f }.map { r ->
            out.coords.copyOfRange(out.runStart[r], out.runStart[r] + 2 * out.runPoints[r])
        }
    }

    private fun screenLength(scene: ZoomScene, d: Long, s: Stroke): Double {
        var sum = 0.0
        for (i in 1 until s.pointCount) {
            val a = scene.toScreen(d, s, i - 1)
            val b = scene.toScreen(d, s, i)
            sum += hypot(b[0] - a[0], b[1] - a[1])
        }
        return sum
    }
}
