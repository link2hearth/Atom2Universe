package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
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

    /**
     * Une toile à la géométrie des tests : seuil au milieu de l'échelle (√ratio), ouverte au zoom 1 —
     * celle de la vraie toile, mais avec un petit rapport pour que les tests changent souvent de couche.
     */
    private fun scene(ratio: Double = 10.0) = ZoomScene(ratio, Math.sqrt(ratio), 1.0)

    /** Des rapports variés : un projet enregistré garde le sien, le moteur doit tous les tenir. */
    private val ratios = doubleArrayOf(10.0, 25.0, 35.0, 625.0)


    /** Un geste de dessin, en pixels depuis le centre de l'écran (environ 300 px de large). */
    private val gesture = listOf(
        -120.0 to 35.5, -60.25 to 80.125, 13.0 to -44.0, 91.375 to -12.75, 150.75 to 10.5,
    )

    /** Un coup de gomme passant par ces écarts d'écran (null s'il n'a rien touché). */
    private fun ZoomScene.erase(points: List<Pair<Double, Double>>, widthPx: Double): Stroke? {
        beginStroke(points[0].first, points[0].second, ZoomScene.ERASER, widthPx)
        for (p in points.drop(1)) extendStroke(p.first, p.second)
        return endStroke()
    }

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

    /**
     * Annule exactement [steps] zooms de [zoomInto] : la vue redevient celle de départ, mais la
     * plage d'une couche est large, donc la couche de travail peut être une de plus qu'au départ.
     */
    private fun ZoomScene.zoomOutSteps(steps: Int, fx: Double = 37.0, fy: Double = -21.0) {
        repeat(steps) { zoomAt(0.5, fx, fy) }
    }

    // ---- Précision ------------------------------------------------------------------------

    @Test
    fun sameRelativeErrorAtLevel0AndLevel200() {
        val scene = scene(10.0)
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
        assertTrue(scene.depth in 0L..1L)
        assertTrue(relativeError(scene, 0, s0, gesture) < 1e-9)
    }

    @Test
    fun zoomIn200LevelsThenBackFallsOnTheSameView() {
        val scene = scene(10.0)
        val s0 = scene.draw(gesture)
        scene.zoomAt(1.7, 12.0, 8.0)
        scene.pan(33.0, -17.5)
        val d0 = scene.depth
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

        // La plage d'une couche est large : on peut se retrouver une couche plus bas qu'au départ
        // (même vue, autre couche de travail). C'est la vue qui doit être la même.
        assertTrue(scene.depth >= d0)
        for (i in before.indices) {
            val p = scene.toScreen(0, s0, i)
            assertEquals(before[i][0], p[0], 1e-9)
            assertEquals(before[i][1], p[1], 1e-9)
        }
        // Les couches vides traversées n'ont pas été gardées.
        while (scene.depth > 0) scene.zoomAt(0.5, 0.0, 0.0)
        assertEquals(listOf(0L), scene.allLayers().map { it.depth })
    }

    @Test
    fun lateralPanOfOneBillionUnitsDoesNotDrift() {
        val scene = scene(10.0)
        scene.zoomAt(2.7, 0.0, 0.0)
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
        assertTrue(scene.depth in 0L..1L)
        // À 10^9 unités de l'origine de la couche, un Double ne distingue pas mieux que Math.ulp(1e9)
        // (≈ 1,2e-7 unité) : on tolère deux de ces pas, soit quelques dix-millionièmes de pixel.
        val ulpPx = Math.ulp(1e9) * scene.viewOf(0).zoom
        assertTrue(relativeError(scene, 0, far, gesture) * 300 <= 2 * ulpPx)

        // Retour au point de départ : aucune dérive visible (bien moins d'un millième de pixel).
        repeat(1000) { scene.pan(stepPx, 0.0) }
        assertTrue(relativeError(scene, 0, s0, gesture) < 1e-5 / 300)
    }

    // ---- Tailles et changement de couche --------------------------------------------------

    @Test
    fun zoomScalesEveryLayerAroundTheSamePointAcrossLayerSwitches() {
        val scene = scene(10.0)
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
        for (ratio in ratios) {
            val scene = scene(ratio)
            scene.zoomAt(2.0, 0.0, 0.0)
            scene.draw(gesture) // quelque chose au niveau 0
            scene.zoomAt(ratio, 0.0, 0.0)
            assertEquals(1L, scene.depth)
            assertEquals(2.0, scene.zoom, 1e-12)
            val s1 = scene.draw(gesture)
            val lenBelow = screenLength(scene, 1, s1)
            // Un peu sous le bord bas de la plage (x1/ratio) : on repasse dans la couche 0, a x0,8.
            scene.zoomAt(0.4 / ratio, 0.0, 0.0)
            assertEquals(0L, scene.depth)
            assertEquals(0.8, scene.zoom, 1e-12)
            assertEquals(lenBelow * 0.4 / ratio, screenLength(scene, 1, s1), 1e-9)
        }
    }

    @Test
    fun aSceneDrawnInsideALittleFrameStaysInsideIt() {
        val scene = scene(10.0)
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
        scene.zoomAt(0.09, c.first, c.second)
        assertEquals(0L, scene.depth)
        for (i in 0 until 5) {
            val a = scene.toScreen(0, sFrame, i)
            val b = scene.toScreen(1, inner, i)
            assertEquals(a[0], b[0], 1e-9)
            assertEquals(a[1], b[1], 1e-9)
            // x0,9 autour du point de zoom (qui n'a pas bougé).
            assertEquals(c.first + (frame[i].first - c.first) * 0.9, a[0], 1e-9)
        }
    }

    @Test
    fun zoomStaysWithinOneLayerRangeAndDepthIsContinuous() {
        val scene = scene(10.0)
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
     * Un seul seuil, sans fondu : avec ×10 il est à ×3,16. En dessous, on voit la couche 0 et la 1 ;
     * au-dessus, la couche 0 a disparu, la 1 est la couche de travail et la 2 s'affiche.
     */
    @Test
    fun oneThresholdSwapsTheLayers() {
        val scene = scene(10.0)
        scene.draw(gesture, 14.0)
        assertEquals(1.0, scene.zoom, 0.0)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)
        assertEquals(1.0, scene.layerAlpha(1), 0.0)
        assertEquals(0.0, scene.layerAlpha(2), 0.0)

        scene.zoomAt(3.1, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)
        scene.zoomAt(3.17 / 3.1, 0.0, 0.0)
        assertEquals(1L, scene.depth)
        assertEquals(3.17 / 10, scene.zoom, 1e-9)
        // La couche 1 devient la couche de travail ; la 0 est encore presque opaque, par-dessus…
        assertTrue(scene.layerAlpha(0) > 0.99)
        assertEquals(1.0, scene.layerAlpha(1), 0.0)
        assertEquals(1.0, scene.layerAlpha(2), 0.0)
        // … et s'efface pendant un zoom ×2.
        scene.zoomAt(ZoomScene.FADE_ZOOM, 0.0, 0.0)
        assertEquals(0.0, scene.layerAlpha(0), 0.0)
    }

    @Test
    fun zoomingBackAndForthAcrossTheThresholdSwapsEveryTime() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomAt(scene.maxZoom * 0.9995, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        repeat(20) {
            scene.zoomAt(1.001, 0.0, 0.0) // juste au-dessus du seuil : on dessine dans la couche 1…
            assertEquals(1L, scene.depth)
            assertTrue(scene.layerAlpha(0) > 0.99) // … et la 0, par-dessus, commence à peine à s'effacer
            scene.zoomAt(1 / 1.001, 0.0, 0.0) // juste en dessous : aucune zone morte
            assertEquals(0L, scene.depth)
            assertEquals(1.0, scene.layerAlpha(0), 0.0)
        }
    }

    @Test
    fun nothingFromTheLayerAboveIsVisibleOnceItIsGone() {
        // Le reproche : « affiché couche 0, et pourtant des dessins d'une autre couche par-dessus ».
        val scene = scene(10.0)
        scene.zoomAt(0.09, 0.0, 0.0)
        assertEquals(-1L, scene.depth)
        scene.draw(gesture, 14.0) // un dessin dans la couche -1
        scene.zoomAt(12.0, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        assertEquals(1.08, scene.zoom, 1e-9)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(0, out.layersDrawn)
    }

    @Test
    fun aLayerAppearsFarAwayAndGrowsToNormalSize() {
        val scene = scene(10.0)
        val s = scene.draw(gesture, 14.0)
        // On sort de la couche 0 par le bas de sa plage : elle est au loin, dix fois plus petite
        // que la couche -1 où l'on est (à ×0,9), entièrement visible.
        scene.zoomAt(0.09, 0.0, 0.0)
        assertEquals(-1L, scene.depth)
        assertEquals(1.0, scene.layerAlpha(0), 0.0)
        val a = scene.toScreen(0, s, 0)
        assertEquals(gesture[0].first * 0.09, a[0], 1e-9)
        // Encore ×1000 : elle fait moins d'un pixel et n'est plus dessinée.
        scene.zoomAt(0.001, 0.0, 0.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(0, out.layersDrawn)
    }

    @Test
    fun theThresholdIsInTheMiddleOfTheScaleForEveryRatio() {
        for (r in ratios) {
            val scene = scene(r)
            assertEquals(Math.sqrt(r), scene.maxZoom, 1e-12)
            assertEquals(1 / Math.sqrt(r), scene.minZoom, 1e-12)
            scene.draw(gesture)
            scene.zoomAt(scene.maxZoom * 0.99, 0.0, 0.0)
            assertEquals(0L, scene.depth)
            scene.zoomAt(1.02, 0.0, 0.0)
            assertEquals(1L, scene.depth)
        }
        assertTrue(scene(20.0).maxZoom > scene(5.0).maxZoom)
    }

    @Test
    fun strokeThicknessIsChosenOnScreenThenGrowsWithTheDrawing() {
        val scene = scene(10.0)
        val a = scene.draw(gesture, 14.0) // à la taille normale : 14 unités de la couche
        scene.zoomAt(2.5, 0.0, 0.0)
        val b = scene.draw(gesture, 14.0) // zoomé ×2,5 : 14 px à l'écran, soit 5,6 unités
        assertEquals(14.0, a.width, 1e-12)
        assertEquals(14.0 / 2.5, b.width, 1e-12)
        // À l'écran : le trait dessiné zoomé fait bien 14 px, le premier a grossi avec le dessin (35 px).
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        val widths = (0 until out.runCount).map { out.runWidth[it] }.toSet()
        assertTrue(widths.all { abs(it - 35f) < 1e-3f || abs(it - 14f) < 1e-3f })
        assertTrue(widths.any { abs(it - 35f) < 1e-3f } && widths.any { abs(it - 14f) < 1e-3f })
    }

    @Test
    fun whatIDrawIsAlwaysVisible() {
        // Un trait ne doit jamais atterrir dans une couche effacée.
        val scene = scene(10.0)
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
    fun crossingTheThresholdDoesNotMoveTheLayerThatStays() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        val s1 = scene.draw(gesture.map { it.first * 0.4 to it.second * 0.4 })
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture.map { it.first * 0.2 to it.second * 0.2 })
        // Retour au niveau 0, juste avant le seuil.
        while (scene.depth > 0) scene.zoomAt(0.5, 0.0, 0.0)
        scene.zoomAt(scene.maxZoom / scene.zoom * (1 - 1e-7), 0.0, 0.0)
        assertEquals(0L, scene.depth)
        val before = visibleRuns(scene)
        val p0 = scene.toScreen(1, s1, 0)
        scene.zoomAt(1 + 2e-7, 0.0, 0.0)
        assertEquals(1L, scene.depth)
        val after = visibleRuns(scene)
        val p1 = scene.toScreen(1, s1, 0)
        // La couche 1 ne bouge pas d'un pixel.
        assertEquals(p0[0], p1[0], 1e-3)
        assertEquals(p0[1], p1[1], 1e-3)
        // Rien ne saute : tout ce qu'on voyait avant est toujours là, au même endroit (la couche 0
        // passe par-dessus, encore opaque), et seule la minuscule couche 2 s'ajoute.
        assertEquals(2, before.size)
        assertEquals(3, after.size)
        for (b in before) assertTrue(after.any { a -> a.size == b.size && a.indices.all { abs(a[it] - b[it]) < 1e-2f } })
    }

    @Test
    fun theLayerAboveFadesOutLightlyJustAfterTheThreshold() {
        val scene = ZoomScene()
        scene.draw(gesture, 4.0)
        scene.zoomAt(scene.maxZoom * 1.0001, 0.0, 0.0) // juste après le seuil
        assertEquals(1L, scene.depth)
        assertTrue(scene.layerAlpha(0) > 0.999)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        // La couche 0 est tracée en dernier, par-dessus, avec l'opacité du fondu.
        assertTrue(out.fadeStart < out.runCount)
        assertTrue(out.fadeAlpha > 0.999f)
        // À mi-chemin du fondu (zoom ×√2), elle est à moitié effacée ; après ×2, elle a disparu.
        scene.zoomAt(Math.sqrt(ZoomScene.FADE_ZOOM), 0.0, 0.0)
        assertEquals(0.5, scene.layerAlpha(0), 1e-3)
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(0.5f, out.fadeAlpha, 1e-3f)
        scene.zoomAt(Math.sqrt(ZoomScene.FADE_ZOOM) * 1.0001, 0.0, 0.0)
        assertEquals(0.0, scene.layerAlpha(0), 0.0)
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(out.runCount, out.fadeStart)
    }

    @Test
    fun duringTheFadeTheStrokeGoesToTheLayerBelow() {
        val scene = ZoomScene()
        scene.draw(gesture, 4.0)
        scene.zoomAt(scene.maxZoom * Math.sqrt(ZoomScene.FADE_ZOOM), 0.0, 0.0) // au milieu du fondu
        assertEquals(1L, scene.depth)
        assertTrue(scene.layerAlpha(0) in 0.3..0.7)
        val s = scene.draw(listOf(0.0 to 0.0, 40.0 to 20.0), 4.0)
        assertTrue(scene.layer(1)!!.strokes.any { it.id == s.id })
        assertTrue(scene.layer(0)!!.strokes.none { it.id == s.id })
    }

    @Test
    fun theFadeDependsOnlyOnTheZoom() {
        // Même zoom, même opacité, qu'on y arrive en zoomant ou en revenant en arrière.
        val a = ZoomScene().apply { draw(gesture, 4.0); zoomAt(maxZoom * 1.3, 0.0, 0.0) }
        val b = ZoomScene().apply { draw(gesture, 4.0); zoomAt(maxZoom * 1.9, 0.0, 0.0); zoomAt(1.3 / 1.9, 0.0, 0.0) }
        assertEquals(1L, a.depth)
        assertEquals(1L, b.depth)
        assertEquals(a.layerAlpha(0), b.layerAlpha(0), 1e-9)
        assertTrue(a.layerAlpha(0) in 0.01..0.99)
    }

    // ---- Ancrage, réalignement, couches virtuelles -----------------------------------------

    @Test
    fun newLayerIsAnchoredWhereTheCameraIsWhenDrawingStarts() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomInto(3)
        scene.pan(-5000.0, 3000.0) // on se promène dans la couche 3, encore vide
        val s = scene.draw(gesture)
        // Le repère de la couche a été recentré sur la caméra : petites coordonnées.
        // Le repère est recentré sur la caméra, à un multiple du rapport près (ancre entière).
        assertTrue(abs(scene.cx) <= 5.0 + 1e-9 && abs(scene.cy) <= 5.0 + 1e-9)
        assertTrue(abs(s.x) < 1000 && abs(s.y) < 1000)
        assertTrue(relativeError(scene, 3, s, gesture) < 1e-9)
    }

    @Test
    fun anchoringAnEmptyLayerDoesNotMoveTheLayersBelow() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomInto(2, 0.0, 0.0)
        val deep = scene.draw(gesture)
        while (scene.depth > 1) scene.zoomAt(0.5, 0.0, 0.0) // retour dans la couche 1, vide
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
        val scene = scene(10.0)
        val top = scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        val below = scene.draw(gesture)
        scene.zoomAt(0.09, 0.0, 0.0)
        assertEquals(0L, scene.depth)

        val topBefore = scene.toScreen(0, top, 1)
        val belowBefore = scene.toScreen(1, below, 1)
        assertTrue(scene.beginMoveLayer())
        scene.moveLayerBy(27.0, -9.0)
        scene.moveLayerBy(9.0, 0.0)
        scene.endMoveLayer()
        val topAfter = scene.toScreen(0, top, 1)
        val belowAfter = scene.toScreen(1, below, 1)
        assertEquals(topBefore[0], topAfter[0], 1e-9)
        // 36 px d'écran = 40 pixels de la couche de travail (zéro 0,9), déplacés par pixels entiers.
        assertEquals(belowBefore[0] + 36.0, belowAfter[0], 1e-9)
        assertEquals(belowBefore[1] - 9.0, belowAfter[1], 1e-9)

        assertTrue(scene.undo())
        val undone = scene.toScreen(1, below, 1)
        assertEquals(belowBefore[0], undone[0], 1e-9)
        assertEquals(belowBefore[1], undone[1], 1e-9)
        assertTrue(scene.redo())
        assertEquals(belowAfter[0], scene.toScreen(1, below, 1)[0], 1e-9)
    }

    @Test
    fun onlyLayersWithContentAndTheAnchorsBetweenThemAreKept() {
        val scene = scene(10.0)
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
        val scene = scene(10.0)
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

    // ---- La vraie toile : ×625, ouverte au milieu, épaisseur à l'écran -----------------------------

    @Test
    fun theRealCanvasOpensAtNormalSizeWithAsMuchRoomToZoomOutAsIn() {
        val scene = ZoomScene()
        assertEquals(625.0, scene.ratio, 0.0)
        assertEquals(25.0, scene.maxZoom, 1e-12)
        assertEquals(1.0 / 25, scene.minZoom, 1e-12)
        assertEquals(1.0, scene.zoom, 1e-12) // taille normale, au milieu de la plage
        // ×25 de dézoom et ×25 de zoom avant de changer de couche (environ deux pincements chacun).
        ZoomScene().apply { zoomAt(1 / 24.9, 0.0, 0.0); assertEquals(0L, depth) }
        ZoomScene().apply { zoomAt(1 / 25.1, 0.0, 0.0); assertEquals(-1L, depth) }
        ZoomScene().apply { zoomAt(24.9, 0.0, 0.0); assertEquals(0L, depth) }
        ZoomScene().apply {
            zoomAt(25.1, 0.0, 0.0)
            assertEquals(1L, depth)
            assertEquals(25.1 / 625, zoom, 1e-12) // la couche du dessous prend la main au bas de sa plage
        }
    }

    @Test
    fun aPenOfEightDrawsEightScreenPixelsWhateverTheZoom() {
        for (z in doubleArrayOf(0.05, 1.0, 20.0)) {
            val scene = ZoomScene()
            scene.zoomAt(z, 0.0, 0.0)
            assertEquals(0L, scene.depth)
            val st = scene.draw(gesture, 8.0)
            assertEquals(8.0 / z, st.width, 1e-9)
            val out = RenderList()
            ZoomRenderer.build(scene, 1000.0, 1000.0, out)
            assertTrue(out.runCount > 0)
            assertTrue((0 until out.runCount).all { abs(out.runWidth[it] - 8f) < 1e-3f })
        }
    }

    @Test
    fun theEraserWidthIsChosenOnScreenToo() {
        val scene = ZoomScene()
        scene.zoomAt(20.0, 0.0, 0.0)
        scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0), 2.0)
        val e = scene.erase(listOf(0.0 to -40.0, 0.0 to 40.0), 10.0)!!
        assertEquals(10.0 / 20, e.width, 1e-12)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        val r = (0 until out.runCount).single { out.runErase[it] }
        assertEquals(10f, out.runWidth[r], 1e-3f)
    }

    @Test
    fun layerGridsNestExactly() {
        val r = 25.0
        val scene = ZoomScene(r)
        scene.draw(gesture)
        scene.zoomAt(37.3, 11.0, -7.0)
        scene.zoomAt(0.0123, 0.0, 0.0)
        scene.pan(123.4, -56.7)
        scene.zoomAt(180.0, 17.0, 5.0) // plusieurs couches plus bas
        scene.pan(-31.3, 77.7)
        scene.draw(gesture)
        for (l in scene.allLayers()) {
            assertEquals("ancre x de la couche ${l.depth}", Math.rint(l.ax), l.ax, 0.0)
            assertEquals("ancre y de la couche ${l.depth}", Math.rint(l.ay), l.ay, 0.0)
        }
        assertTrue(scene.depth >= 1)
    }

    @Test
    fun movingALayerKeepsTheGridsAligned() {
        val scene = ZoomScene(25.0)
        scene.draw(gesture)
        scene.zoomAt(100.0 / scene.zoom, 0.0, 0.0) // descend dans la couche 1
        scene.draw(gesture)
        scene.zoomAt(0.9, 0.0, 0.0)
        while (scene.depth > 0) scene.zoomAt(0.9, 0.0, 0.0)
        assertTrue(scene.beginMoveLayer())
        scene.moveLayerBy(13.7, -9.2)
        scene.moveLayerBy(3.1, 4.4)
        scene.endMoveLayer()
        val l = scene.layer(1)!!
        assertEquals(Math.rint(l.ax), l.ax, 0.0)
        assertEquals(Math.rint(l.ay), l.ay, 0.0)
    }

    // ---- Dessin, gomme, historique --------------------------------------------------------

    @Test
    fun theEraserDigsAHoleInItsLayerOnly() {
        val scene = scene(10.0)
        val a = scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0), 30.0)
        scene.zoomAt(10.0, 0.0, 0.0)
        val b = scene.draw(listOf(-100.0 to 50.0, 100.0 to 50.0))
        scene.zoomAt(0.09, 0.0, 0.0)
        assertEquals(0L, scene.depth)

        // Un coup de gomme au milieu du trait a : un trait transparent posé dans la couche 0. Le trait
        // a reste entier (seul le milieu est creusé), la couche 1 n'est pas touchée.
        val e = scene.erase(listOf(0.0 to -30.0, 0.0 to 30.0), 20.0)!!
        assertTrue(e.isEraser)
        assertEquals(listOf(a.id, e.id), scene.layer(0)!!.drawOrder().map { it.id })
        assertEquals(listOf(b.id), scene.layer(1)!!.strokes.map { it.id })

        // Au rendu : la couche 1 d'abord, telle quelle ; puis la couche 0 composée à part, le trait a
        // puis la gomme, qui ne creuse donc que lui.
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(2, out.groupCount)
        assertFalse(out.groupIsolated[0])
        assertTrue((out.groupStart[0] until out.groupEnd[0]).none { out.runErase[it] })
        assertTrue(out.groupIsolated[1])
        val top = (out.groupStart[1] until out.groupEnd[1]).map { out.runErase[it] }
        assertEquals(listOf(false, true), top)
        // Le contenu visible de la couche ne compte pas la gomme.
        assertEquals(a.let { it.x + it.minX - it.width / 2 }, scene.layer(0)!!.bounds()!![0], 1e-9)

        assertTrue(scene.undo())
        assertEquals(listOf(a.id), scene.layer(0)!!.strokes.map { it.id })
        assertTrue(scene.redo())
        assertEquals(listOf(a.id, e.id), scene.layer(0)!!.strokes.map { it.id })
    }

    @Test
    fun whatIsPosedAfterTheEraserIsNotCut() {
        val scene = scene(10.0)
        val a = scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0), 30.0)
        val e = scene.erase(listOf(0.0 to -30.0, 0.0 to 30.0), 20.0)!!
        // On redessine dans le trou, puis on pose une image par-dessus : ni l'un ni l'autre n'est creusé.
        val c = scene.draw(listOf(-10.0 to 0.0, 10.0 to 0.0), 6.0)
        val img = scene.addImage("a.png", 100, 100, 50.0, 50.0)
        assertEquals(listOf(a.id, e.id, c.id, img.id), scene.layer(0)!!.drawOrder().map { it.id })
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        val kinds = (0 until out.runCount).map { r ->
            when {
                out.runImage[r] >= 0 -> "image"
                out.runErase[r] -> "gomme"
                else -> "trait"
            }
        }
        assertEquals(listOf("trait", "gomme", "trait", "image"), kinds)
    }

    @Test
    fun anImageCanBeErasedToo() {
        val scene = scene(10.0)
        val img = scene.addImage("a.png", 200, 200, 300.0, 300.0)
        val e = scene.erase(listOf(-20.0 to -20.0, 20.0 to 20.0), 40.0)!!
        assertEquals(listOf(img.id, e.id), scene.layer(0)!!.drawOrder().map { it.id })
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(1, out.groupCount)
        assertTrue(out.groupIsolated[0])
        assertTrue(out.runImage[0] >= 0 && out.runErase[1])
    }

    @Test
    fun anEraserOverNothingLeavesNoTrace() {
        val scene = scene(10.0)
        assertNull(scene.erase(listOf(0.0 to 0.0, 50.0 to 50.0), 20.0))
        scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0), 4.0)
        // Loin du trait : rien n'est gardé, l'historique n'a qu'un pas (le trait).
        assertNull(scene.erase(listOf(0.0 to 300.0, 50.0 to 350.0), 20.0))
        assertEquals(1, scene.layer(0)!!.strokes.size)
        assertTrue(scene.undo())
        assertFalse(scene.canUndo)
    }

    @Test
    fun theEraserBeingDrawnDigsLiveInItsLayer() {
        val scene = scene(10.0)
        scene.draw(listOf(-100.0 to 0.0, 100.0 to 0.0), 30.0)
        scene.beginStroke(0.0, -30.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(0.0, 30.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(1, out.groupCount)
        assertTrue(out.groupIsolated[0])
        assertTrue(out.runErase[out.runCount - 1])
        scene.cancelStroke()
    }

    @Test
    fun undoingTheOnlyStrokeOfALayerKeepsItsFrameForRedo() {
        val scene = scene(10.0)
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
        val scene = scene(10.0)
        scene.beginStroke(0.0, 0.0, black, 3.0)
        scene.extendStroke(10.0, 10.0)
        assertNotNull(scene.liveStroke())
        scene.cancelStroke()
        assertNull(scene.liveStroke())
        assertNull(scene.endStroke())
        assertFalse(scene.canUndo)
    }

    // ---- Images -----------------------------------------------------------------------------

    @Test
    fun anImportedImageIsPlacedAtTheCenterOfTheScreenAndFits() {
        val scene = scene(10.0)
        scene.zoomAt(2.0, 100.0, 50.0)
        val img = scene.addImage("a.png", 400, 200, 600.0, 600.0)
        val r = scene.boxScreenRect(img)
        // 400×200 px réduits pour tenir dans 600×600 : 600×300 à l'écran, centré.
        assertEquals(-300.0, r[0], 1e-9)
        assertEquals(-150.0, r[1], 1e-9)
        assertEquals(300.0, r[2], 1e-9)
        assertEquals(150.0, r[3], 1e-9)
        assertEquals(img.id, scene.boxAt(10.0, 10.0)?.id)
        assertNull(scene.boxAt(400.0, 0.0))
    }

    @Test
    fun anImageCanBeMovedResizedAndUndone() {
        val scene = scene(10.0)
        val img = scene.addImage("a.png", 100, 50, 200.0, 200.0) // 200×100 à l'écran
        assertTrue(scene.beginBoxEdit(img.id))
        scene.moveBoxBy(30.0, -10.0)
        scene.endBoxEdit()
        val moved = scene.boxScreenRect(scene.box(img.id)!!)
        assertEquals(-70.0, moved[0], 1e-9)
        assertEquals(-60.0, moved[1], 1e-9)

        // On tire le coin bas-droit : le coin haut-gauche ne bouge pas, les proportions restent.
        assertTrue(scene.beginBoxEdit(img.id))
        scene.resizeBoxTo(2, 330.0, 140.0)
        scene.endBoxEdit()
        val resized = scene.boxScreenRect(scene.box(img.id)!!)
        assertEquals(-70.0, resized[0], 1e-9)
        assertEquals(-60.0, resized[1], 1e-9)
        val w = resized[2] - resized[0]
        val h = resized[3] - resized[1]
        assertEquals(2.0, w / h, 1e-9)
        assertEquals(400.0, w, 1e-6)

        assertTrue(scene.undo())
        assertEquals(moved[2], scene.boxScreenRect(scene.box(img.id)!!)[2], 1e-9)
        assertTrue(scene.undo())
        assertEquals(100.0, scene.boxScreenRect(scene.box(img.id)!!)[2], 1e-9)
        assertTrue(scene.undo())
        assertNull(scene.box(img.id))
        assertTrue(scene.redo())
        assertNotNull(scene.box(img.id))

        scene.deleteBox(img.id)
        assertNull(scene.box(img.id))
        assertTrue(scene.undo())
        assertNotNull(scene.box(img.id))
    }

    @Test
    fun anImageDeepDownStaysExactlyInPlace() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomInto(200)
        val img = scene.addImage("a.png", 300, 300, 300.0, 300.0)
        val r = scene.boxScreenRect(img)
        assertEquals(-150.0, r[0], 1e-9)
        assertEquals(150.0, r[3], 1e-9)
        assertTrue(abs(img.x) < 1e4 && abs(img.w) < 1e4)
    }

    @Test
    fun aHugelyZoomedImageIsCroppedToTheScreen() {
        val scene = scene(10.0)
        scene.addImage("a.png", 1000, 1000, 500.0, 500.0)
        // Zoom ×3 : l'image fait 1500 px de côté, bien plus que l'écran.
        scene.zoomAt(3.0, 37.0, -12.0)
        assertEquals(0L, scene.depth)
        val out = RenderList()
        ZoomRenderer.build(scene, 1080.0, 1920.0, out)
        assertEquals(1, out.imageCount)
        val d = out.imageDst
        assertTrue(d[0] >= -1f && d[1] >= -1f && d[2] <= 1081f && d[3] <= 1921f)
        val src = out.imageSrc
        assertTrue(src[0] >= 0f && src[2] <= 1000f && src[2] - src[0] < 1000f)
    }

    // ---- Rendu -----------------------------------------------------------------------------

    @Test
    fun rendererNeverEmitsLargeFloats() {
        val w = 1080.0
        val h = 2200.0
        val scene = scene(10.0)
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
                8 -> when (rnd.nextInt(5)) {
                    0 -> scene.undo()
                    1 -> scene.redo()
                    3 -> {
                        // Des formes de toutes sortes et de toutes tailles, plus grandes que l'écran comprises.
                        val kind = ShapeKind.values()[rnd.nextInt(ShapeKind.values().size)]
                        val fill = ShapeFill.values()[rnd.nextInt(3)]
                        val size = rnd.nextDouble(8.0, 9000.0)
                        val x0 = rnd.nextDouble(-w, w)
                        val y0 = rnd.nextDouble(-h, h)
                        scene.beginShape(kind, fill, black, 0xFF44AA88.toInt(), rnd.nextDouble(1.0, 40.0), x0, y0, rnd.nextBoolean())
                        scene.updateShape(x0 + rnd.nextDouble(-size, size), y0 + rnd.nextDouble(-size, size))
                        scene.endShape()
                    }
                    4 -> scene.addText("Un texte\nsur deux lignes",rnd.nextDouble(-w, w), rnd.nextDouble(-h, h), rnd.nextDouble(8.0, 300.0), black, 0, "", 9.5)
                    else -> scene.addImage("k", rnd.nextInt(10, 3000), rnd.nextInt(10, 3000), rnd.nextDouble(10.0, 5000.0), rnd.nextDouble(10.0, 5000.0))
                }
                else -> {
                    val x0 = rnd.nextDouble(-w / 2, w / 2)
                    scene.beginStroke(x0, 0.0, ZoomScene.ERASER, rnd.nextDouble(4.0, 80.0))
                    scene.extendStroke(x0 + rnd.nextDouble(-300.0, 300.0), rnd.nextDouble(-300.0, 300.0))
                    scene.endStroke()
                }
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
            for (r in 0 until out.runCount) if (out.runText[r] != null) {
                val x = out.runTextX[r]; val y = out.runTextY[r]; val sz = out.runTextSize[r]
                assertTrue("texte ($x, $y, $sz) à l'étape $step", x.isFinite() && y.isFinite() && sz.isFinite() && abs(x) <= 1e5f && abs(y) <= 1e5f && sz <= ZoomRenderer.MAX_TEXT_PX)
            }
            for (i in 0 until 4 * out.imageCount) {
                val v = out.imageDst[i]
                assertTrue("image $v à l'étape $step", v.isFinite() && v >= -1f && v <= h.toFloat() + 1f)
            }
        }
        assertTrue("profondeur max $maxDepth", maxDepth >= 30)
        assertTrue("profondeur min $minDepth", minDepth <= -3)
    }

    @Test
    fun rendererDrawsBackLayersBehindAndSkipsSubPixelOnes() {
        val scene = scene(10.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture)
        scene.zoomAt(10.0, 0.0, 0.0)
        scene.draw(gesture)
        repeat(6) { scene.zoomAt(10.0, 0.0, 0.0) }
        scene.draw(gesture) // niveau 8 : minuscule vu d'en haut
        while (scene.depth > 0) scene.zoomAt(0.0999, 0.0, 0.0)
        assertEquals(0L, scene.depth)
        val out = RenderList()
        ZoomRenderer.build(scene, 1080.0, 1920.0, out)
        // Seulement la couche de travail et celle d'en dessous : les niveaux 0 et 1.
        assertEquals(2, out.layersDrawn)
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

    @Test
    fun theBrushThinsAtBothEndsAndIsFullInTheMiddle() {
        val scene = ZoomScene()
        scene.zoomAt(4.0, 0.0, 0.0)
        scene.beginStroke(-300.0, 0.0, black, 20.0, Stroke.BRUSH)
        for (k in 1..60) scene.extendStroke(-300.0 + k * 10.0, 0.0)
        val s = scene.endStroke()!!
        assertEquals(Stroke.BRUSH, s.kind)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(1, out.runCount)
        val first = out.runStart[0] / 2
        val n = out.runPoints[0]
        val w = (0 until n).map { out.pointWidth[first + it] }
        // Pointe aux deux bouts, épaisseur pleine (20 px) au milieu, et jamais plus.
        assertEquals(20f * Stroke.BRUSH_TIP.toFloat(), w.first(), 1e-3f)
        assertEquals(20f * Stroke.BRUSH_TIP.toFloat(), w.last(), 1e-3f)
        assertEquals(20f, w[n / 2], 1e-3f)
        assertTrue(w.all { it <= 20f + 1e-3f })
        // L'affinement se fait sur trois épaisseurs (60 px d'écran) : à 30 px du bout, on est entre les deux.
        assertTrue(w[3] > w[0] && w[3] < 20f)
        assertEquals(20f, w[6], 1e-3f)
    }

    @Test
    fun theBrushTaperFollowsTheDrawingWhenZooming() {
        // Le même trait vu deux fois plus gros : l'affinement couvre la même part du trait.
        val scene = ZoomScene()
        scene.beginStroke(-100.0, 0.0, black, 8.0, Stroke.BRUSH)
        for (k in 1..20) scene.extendStroke(-100.0 + k * 10.0, 0.0)
        scene.endStroke()
        val a = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, a)
        scene.zoomAt(2.0, 0.0, 0.0)
        val b = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, b)
        for (k in 0 until a.runPoints[0]) assertEquals(2 * a.pointWidth[k], b.pointWidth[k], 1e-3f)
    }

    @Test
    fun penAndMarkerKeepTheirWidthAndTheEraserIsNeverABrush() {
        val scene = ZoomScene()
        scene.beginStroke(-100.0, 0.0, black, 10.0, Stroke.MARKER)
        for (k in 1..20) scene.extendStroke(-100.0 + k * 10.0, 0.0)
        assertEquals(Stroke.MARKER, scene.endStroke()!!.kind)
        val out = RenderList()
        ZoomRenderer.build(scene, 1000.0, 1000.0, out)
        assertEquals(Stroke.MARKER, out.runKind[0].toInt())
        for (k in 0 until out.runPoints[0]) assertEquals(10f, out.pointWidth[k], 1e-3f)
        scene.beginStroke(-50.0, -20.0, ZoomScene.ERASER, 10.0, Stroke.BRUSH)
        scene.extendStroke(50.0, 20.0)
        assertEquals(Stroke.PEN, scene.endStroke()!!.kind)
    }

}
