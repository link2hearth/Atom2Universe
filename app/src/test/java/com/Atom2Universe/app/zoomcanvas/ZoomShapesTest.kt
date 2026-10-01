package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.ShapeGeometry
import com.Atom2Universe.app.zoomcanvas.core.ShapeItem
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.core.ZoomSnapshot
import com.Atom2Universe.app.zoomcanvas.core.ZoomStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** Les formes et les textes du canvas infini : tracé, sélection, modification, rendu, fichier. */
class ZoomShapesTest {

    private val black = 0xFF000000.toInt()
    private val green = 0xFF44AA88.toInt()
    private val dir = File.createTempFile("zoomshapes", "test").also { it.delete(); it.mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Même géométrie que les tests de la scène : seuil au milieu de l'échelle, ouverte au zoom 1. */
    private fun scene() = ZoomScene(10.0, Math.sqrt(10.0), 1.0)

    private fun ZoomScene.shape(
        kind: ShapeKind, ax: Double, ay: Double, bx: Double, by: Double,
        fill: ShapeFill = ShapeFill.OUTLINE, width: Double = 4.0, square: Boolean = false,
    ): ShapeItem? {
        beginShape(kind, fill, black, green, width, ax, ay, square)
        updateShape(bx, by)
        return endShape()
    }

    @Test
    fun aShapeIsDrawnFromCornerToCornerAndKeptInTheWorkingLayer() {
        val scene = scene()
        val s = scene.shape(ShapeKind.RECT, -100.0, -50.0, 60.0, 70.0)!!
        val r = scene.boxScreenRect(s)
        assertEquals(-100.0, r[0], 1e-9)
        assertEquals(-50.0, r[1], 1e-9)
        assertEquals(60.0, r[2], 1e-9)
        assertEquals(70.0, r[3], 1e-9)
        // L'épaisseur est celle du trait à l'écran : 4 px au zoom 1.
        assertEquals(4.0, s.width * scene.zoom, 1e-9)
        assertEquals(1, scene.layer(scene.depth)!!.shapes.size)
        assertTrue(scene.canUndo)
    }

    @Test
    fun aTinyDragLeavesNothing() {
        val scene = scene()
        assertNull(scene.shape(ShapeKind.ELLIPSE, 10.0, 10.0, 12.0, 11.0))
        assertTrue(scene.layer(scene.depth)!!.isEmpty)
        assertFalse(scene.isDrawingShape)
    }

    @Test
    fun theLiveShapeFollowsTheFingerAndSquareKeepsProportions() {
        val scene = scene()
        scene.beginShape(ShapeKind.ELLIPSE, ShapeFill.FILL, black, green, 2.0, 0.0, 0.0, true)
        scene.updateShape(80.0, -30.0)
        val live = scene.liveShape()!!
        assertEquals(80.0 / scene.zoom, live.w, 1e-9)
        assertEquals(live.w, live.h, 1e-9)
        scene.cancelShape()
        assertNull(scene.liveShape())
        assertTrue(scene.layer(scene.depth)!!.isEmpty)
    }

    @Test
    fun aLineAndAnArrowRememberTheirDirection() {
        val scene = scene()
        val up = scene.shape(ShapeKind.LINE, 50.0, 40.0, -30.0, -20.0)!!
        assertTrue(up.flipX && up.flipY)
        val down = scene.shape(ShapeKind.ARROW, -30.0, -20.0, 50.0, 40.0)!!
        assertFalse(down.flipX || down.flipY)
        // La pointe est le quatrième sommet : au bout du geste, donc en bas à droite de la boîte (80 × 60).
        val pts = ShapeGeometry.outline(down, 1.0)
        assertEquals(40.0, pts[6] * scene.zoom, 1e-9)
        assertEquals(30.0, pts[7] * scene.zoom, 1e-9)
    }

    @Test
    fun everyShapeStaysInsideItsBox() {
        for (kind in ShapeKind.values()) {
            val s = ShapeItem(1, kind, 0.0, 0.0, 120.0, 80.0, false, false, black, green, ShapeFill.BOTH, 3.0)
            val p = ShapeGeometry.outline(s, 1.0)
            assertTrue("$kind", p.size >= 4 && p.size % 2 == 0)
            // Une flèche en diagonale a une épaisseur : ses angles sortent un peu de la boîte, de la tête au plus.
            val slack = if (kind == ShapeKind.ARROW) hypot(120.0, 80.0) * ShapeItem.ARROW_HEAD_HALF else 1e-6
            for (i in p.indices step 2) {
                assertTrue("$kind x=${p[i]}", abs(p[i]) <= 60.0 + slack)
                assertTrue("$kind y=${p[i + 1]}", abs(p[i + 1]) <= 40.0 + slack)
            }
        }
    }

    @Test
    fun anEllipseGetsFinerWhenZoomedAndNeverExceedsTheCap() {
        val s = ShapeItem(1, ShapeKind.ELLIPSE, 0.0, 0.0, 100.0, 100.0, false, false, black, green, ShapeFill.OUTLINE, 2.0)
        val small = ShapeGeometry.outline(s, 1.0).size
        val big = ShapeGeometry.outline(s, 50.0).size
        val absurd = ShapeGeometry.outline(s, 1e12).size
        assertTrue(big > small)
        assertTrue(absurd <= 2 * 2048)
    }

    @Test
    fun aShapeCanBeResizedFreelyAndFlipsWhenPulledAcross() {
        val scene = scene()
        val s = scene.shape(ShapeKind.LINE, -40.0, -40.0, 40.0, 40.0)!!
        assertTrue(scene.beginBoxEdit(s.id))
        // On tire le coin bas-droit (2) au-delà du coin fixe, vers le haut-gauche : la ligne se retourne.
        scene.resizeBoxTo(2, -100.0, -70.0)
        scene.endBoxEdit()
        val moved = scene.box(s.id) as ShapeItem
        val r = scene.boxScreenRect(moved)
        assertEquals(-100.0, r[0], 1e-9)
        assertEquals(-70.0, r[1], 1e-9)
        assertEquals(-40.0, r[2], 1e-9)
        assertEquals(-40.0, r[3], 1e-9)
        assertTrue(moved.flipX && moved.flipY)
        assertTrue(scene.undo())
        assertEquals(40.0, scene.boxScreenRect(scene.box(s.id)!!)[2], 1e-9)
        assertFalse((scene.box(s.id) as ShapeItem).flipX)
    }

    @Test
    fun aTriangleKeepsItsApexUpWhateverTheDragDirection() {
        val scene = scene()
        val s = scene.shape(ShapeKind.TRIANGLE, 60.0, 60.0, -60.0, -60.0)!!
        assertFalse(s.flipX || s.flipY)
        assertTrue(scene.beginBoxEdit(s.id))
        scene.resizeBoxTo(2, -200.0, -200.0)
        scene.endBoxEdit()
        val t = scene.box(s.id) as ShapeItem
        assertFalse(t.flipX || t.flipY)
    }

    @Test
    fun aDiagonalLineIsOnlyHitNearItsStroke() {
        val scene = scene()
        scene.shape(ShapeKind.LINE, -100.0, -100.0, 100.0, 100.0)
        assertNotNull(scene.boxAt(0.0, 0.0))
        assertNotNull(scene.boxAt(40.0, 36.0))
        assertNull(scene.boxAt(80.0, -80.0))
    }

    @Test
    fun theTopmostObjectWinsTheHit() {
        val scene = scene()
        scene.shape(ShapeKind.RECT, -100.0, -100.0, 100.0, 100.0, ShapeFill.FILL)
        val top = scene.shape(ShapeKind.ELLIPSE, -50.0, -50.0, 50.0, 50.0, ShapeFill.FILL)!!
        assertEquals(top.id, scene.boxAt(0.0, 0.0)!!.id)
        assertNotNull(scene.boxAt(90.0, 90.0))
    }

    @Test
    fun aTextIsPlacedUnderTheTapAndScalesWithItsCorner() {
        val scene = scene()
        val t = scene.addText("Bonjour\nle monde", 30.0, -20.0, 40.0, black, TextItem.BOLD, "", 6.0)!!
        assertEquals(40.0, t.fontSize * scene.zoom, 1e-9)
        assertEquals(6.0 * t.fontSize, t.w, 1e-9)
        assertEquals(2 * TextItem.LINE_HEIGHT * t.fontSize, t.h, 1e-9)
        val r = scene.boxScreenRect(t)
        assertEquals(30.0, (r[0] + r[2]) / 2, 1e-9)
        assertEquals(-20.0, (r[1] + r[3]) / 2, 1e-9)

        // Un coin tiré : la police change de taille, la forme du texte reste la même.
        scene.beginBoxEdit(t.id)
        scene.resizeBoxTo(2, r[2] + 120.0, r[3] + 120.0)
        scene.endBoxEdit()
        val big = scene.box(t.id) as TextItem
        assertTrue(big.fontSize > t.fontSize)
        assertEquals(t.w / t.h, big.w / big.h, 1e-9)
        assertEquals(t.w / t.fontSize, big.w / big.fontSize, 1e-9)
    }

    @Test
    fun aBlankTextIsNotKeptAndEditingKeepsTheTopLeftCorner() {
        val scene = scene()
        assertNull(scene.addText("  \n ", 0.0, 0.0, 30.0, black, 0, "", 5.0))
        val t = scene.addText("abc", 0.0, 0.0, 30.0, black, 0, "", 3.0)!!
        scene.changeBox(t.restyled(text = "abcdef\nghi", unitWidth = 6.0))
        val back = scene.box(t.id) as TextItem
        assertEquals("abcdef\nghi", back.text)
        assertEquals(t.x - t.w / 2, back.x - back.w / 2, 1e-9)
        assertEquals(t.y - t.h / 2, back.y - back.h / 2, 1e-9)
        assertTrue(scene.undo())
        assertEquals("abc", (scene.box(t.id) as TextItem).text)
    }

    @Test
    fun changingAShapesStyleIsOneUndoStepAndDuplicatingAddsACopyInFront() {
        val scene = scene()
        val s = scene.shape(ShapeKind.STAR, -30.0, -30.0, 30.0, 30.0)!!
        scene.changeBox(s.copy(fill = ShapeFill.BOTH, fillColor = 0xFFFF0000.toInt()))
        assertEquals(ShapeFill.BOTH, (scene.box(s.id) as ShapeItem).fill)
        assertTrue(scene.undo())
        assertEquals(ShapeFill.OUTLINE, (scene.box(s.id) as ShapeItem).fill)
        assertTrue(scene.redo())

        val copy = scene.duplicateBox(s.id, 16.0) as ShapeItem
        assertTrue(copy.id > s.id)
        assertEquals(2, scene.layer(scene.depth)!!.shapes.size)
        assertEquals(copy.id, scene.boxAt(25.0, 25.0)!!.id)
        scene.deleteBox(copy.id)
        assertEquals(1, scene.layer(scene.depth)!!.shapes.size)
        assertTrue(scene.undo())
        assertEquals(2, scene.layer(scene.depth)!!.shapes.size)
    }

    @Test
    fun shapesAndTextsAreDrawnInTheOrderTheyWerePlacedWithFillBeforeOutline() {
        val scene = scene()
        scene.shape(ShapeKind.RECT, -60.0, -60.0, 60.0, 60.0, ShapeFill.BOTH)
        scene.addText("T", 0.0, 0.0, 30.0, black, 0, "", 1.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 400.0, 400.0, out)
        val kinds = (0 until out.runCount).map { r ->
            when {
                out.runText[r] != null -> "texte"
                out.runKind[r].toInt() == RenderList.KIND_FILL -> "remplissage"
                else -> "contour"
            }
        }
        assertEquals(listOf("remplissage", "contour", "texte"), kinds)
    }

    @Test
    fun aHugeFilledShapeIsClippedToTheScreen() {
        val scene = scene()
        scene.shape(ShapeKind.ELLIPSE, -100.0, -100.0, 100.0, 100.0, ShapeFill.BOTH)
        // Zoom ×3 (sous le seuil de changement de couche) : l'ellipse fait 600 px, le double de l'écran.
        scene.zoomAt(3.0, 0.0, 0.0)
        val out = RenderList()
        ZoomRenderer.build(scene, 300.0, 300.0, out)
        assertTrue(out.coordCount > 0)
        for (i in 0 until out.coordCount) assertTrue("${out.coords[i]}", abs(out.coords[i]) < 400f)
        val fillRun = (0 until out.runCount).first { out.runKind[it].toInt() == RenderList.KIND_FILL }
        assertTrue(out.runPoints[fillRun] >= 3)
    }

    @Test
    fun shapesStayExactlyWhereTheyAreDeepDown() {
        val scene = scene()
        repeat(40) { scene.zoomAt(1.9, 0.0, 0.0) }
        val s = scene.shape(ShapeKind.RECT, -50.0, -30.0, 70.0, 20.0)!!
        val before = scene.boxScreenRect(s)
        scene.zoomAt(0.5, 13.0, -7.0)
        scene.zoomAt(2.0, 13.0, -7.0)
        val after = scene.boxScreenRect(scene.box(s.id)!!)
        for (i in 0 until 4) assertEquals(before[i], after[i], 1e-6)
    }

    @Test
    fun shapesAndTextsSurviveTheFile() {
        val store = ZoomStore(dir)
        val p = store.create("Formes", 10.0)
        p.scene.zoomAt(3.0, 0.0, 0.0)
        val a = p.scene.shape(ShapeKind.ARROW, -80.0, 10.0, 60.0, -40.0, ShapeFill.BOTH, 5.0)!!
        val b = p.scene.shape(ShapeKind.HEXAGON, 0.0, 0.0, 50.0, 50.0, ShapeFill.FILL)!!
        val t = p.scene.addText("Un texte\nà é ü ☃", 10.0, 10.0, 24.0, 0xFF123456.toInt(), TextItem.BOLD or TextItem.ITALIC, "Orbitron-Regular", 8.25)!!
        store.save(ZoomSnapshot.of(p.meta, p.scene))

        val loaded = store.load(p.meta.id)!!
        assertEquals(a, loaded.scene.box(a.id))
        assertEquals(b, loaded.scene.box(b.id))
        assertEquals(t, loaded.scene.box(t.id))
        assertEquals(p.scene.nextStrokeId, loaded.scene.nextStrokeId)
        assertEquals(3, store.list().single().strokeCount)
        // Une forme posée après la réouverture ne reprend pas un identifiant déjà pris.
        val c = loaded.scene.shape(ShapeKind.RECT, 0.0, 0.0, 40.0, 40.0)!!
        assertTrue(c.id > t.id)
    }

    @Test
    fun anEraserStrokeHollowsAShapeDrawnBeforeIt() {
        val scene = scene()
        scene.shape(ShapeKind.RECT, -60.0, -60.0, 60.0, 60.0, ShapeFill.FILL)
        scene.beginStroke(-80.0, 0.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 0.0)
        assertNotNull(scene.endStroke())
        val out = RenderList()
        ZoomRenderer.build(scene, 300.0, 300.0, out)
        assertEquals(1, out.groupCount)
        assertTrue(out.groupIsolated[0])
        val kinds = (0 until out.runCount).map { out.runErase[it] }
        assertEquals(listOf(false, true), kinds)
    }
}
