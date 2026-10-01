package com.Atom2Universe.app.zoomcanvas

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.OrderMove
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.StrokeBox
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

/** La pile des éléments d'une couche : tout se superpose par défaut, la sélection les monte ou les descend. */
class ZoomOrderTest {

    private val black = 0xFF000000.toInt()
    private val dir = File.createTempFile("zoomorder", "test").also { it.delete(); it.mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun scene() = ZoomScene(10.0, Math.sqrt(10.0), 1.0)

    /** Un trait horizontal à la hauteur [y], de -100 à 100. */
    private fun ZoomScene.line(y: Double): Long {
        beginStroke(-100.0, y, black, 6.0)
        extendStroke(0.0, y)
        extendStroke(100.0, y)
        return endStroke()!!.id
    }

    private fun ZoomScene.rect(x0: Double, y0: Double, x1: Double, y1: Double): Long {
        beginShape(ShapeKind.RECT, ShapeFill.FILL, black, black, 2.0, x0, y0, false)
        updateShape(x1, y1)
        return endShape()!!.id
    }

    private fun ZoomScene.stack(): List<Long> = layer(depth)!!.drawOrder().map { it.id }

    @Test
    fun everythingStacksInPlacementOrderByDefault() {
        val scene = scene()
        val a = scene.line(0.0)
        val b = scene.rect(-50.0, -50.0, 50.0, 50.0)
        val c = scene.addText("T", 0.0, 0.0, 30.0, black, 0, "", 1.0)!!.id
        assertEquals(listOf(a, b, c), scene.stack())
        assertFalse(scene.layer(scene.depth)!!.hasCustomOrder)
    }

    @Test
    fun aStrokeCanBeSelectedNearItsLineButNotInTheEmptyBoxAroundIt() {
        val scene = scene()
        // Un « V » : sa boîte est large et haute, mais le milieu est vide.
        scene.beginStroke(-100.0, -100.0, black, 6.0)
        scene.extendStroke(0.0, 100.0)
        scene.extendStroke(100.0, -100.0)
        val id = scene.endStroke()!!.id
        assertEquals(id, scene.boxAt(-50.0, 0.0)?.id)
        assertNull(scene.boxAt(0.0, -60.0))
        assertTrue(scene.box(id) is StrokeBox)
    }

    @Test
    fun anEraserStrokeIsOnlySelectedInEraserEditMode() {
        val scene = scene()
        val line = scene.line(0.0)
        scene.beginStroke(-80.0, 0.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 0.0)
        val eraser = scene.endStroke()!!.id
        // Sélection normale : la gomme est invisible pour elle, on tombe sur le trait.
        assertEquals(line, scene.boxAt(0.0, 0.0)!!.id)
        // Mode d'édition des gommes : elle seule se laisse prendre.
        assertEquals(eraser, scene.boxAt(0.0, 0.0, erasers = true)!!.id)
        assertNull(scene.boxAt(0.0, 60.0, erasers = true))
        // Dans la pile, elle compte pour les éléments qu'elle creuse : ici le trait, qui est dessous.
        val info = scene.orderInfo(eraser)!!
        assertEquals(1, info.rank)
        assertEquals(1, info.count)
        assertEquals(2, scene.stack().size)
    }

    @Test
    fun anEraserStrokeCanBeMovedResizedDeletedAndPutBehindWhatItCut() {
        val scene = scene()
        val line = scene.line(0.0)
        scene.beginStroke(-80.0, 0.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 0.0)
        val eraser = scene.endStroke()!!.id
        fun cuts(): Boolean {
            val out = RenderList()
            ZoomRenderer.build(scene, 300.0, 300.0, out)
            return (0 until out.runCount).map { out.runErase[it] } == listOf(false, true)
        }
        assertTrue(cuts())

        // On la descend d'un cran : elle passe sous le trait et ne le creuse plus.
        assertTrue(scene.reorder(eraser, OrderMove.BACKWARD))
        assertEquals(listOf(eraser, line), scene.stack())
        assertFalse(cuts())
        assertTrue(scene.undo())
        assertTrue(cuts())

        // On la déplace hors du trait : le trou suit.
        assertTrue(scene.beginBoxEdit(eraser))
        scene.moveBoxBy(0.0, 80.0)
        scene.endBoxEdit()
        assertEquals(80.0, scene.boxScreenRect(scene.box(eraser)!!)[1], 1e-6)

        // Supprimée, le trait redevient entier ; annulée, elle revient à sa place.
        scene.deleteBox(eraser)
        assertEquals(listOf(line), scene.stack())
        assertFalse(scene.layer(scene.depth)!!.hasEraser)
        assertTrue(scene.undo())
        assertEquals(listOf(line, eraser), scene.stack())
        assertTrue(scene.layer(scene.depth)!!.hasEraser)
    }

    @Test
    fun editModeShowsEverythingFadedAndTheErasersInColorWithoutCutting() {
        val scene = scene()
        scene.line(0.0)
        scene.rect(-50.0, -50.0, 50.0, 50.0)
        scene.beginStroke(-80.0, 0.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 0.0)
        scene.endStroke()
        val out = RenderList()
        ZoomRenderer.build(scene, 300.0, 300.0, out, ghost = true)
        assertEquals(2, out.groupCount)
        // Premier bloc : les éléments, estompés, à part ; aucun coup de gomme dedans.
        assertTrue(out.groupIsolated[0])
        assertEquals(ZoomRenderer.GHOST_ALPHA, out.groupAlpha[0], 0f)
        for (r in out.groupStart[0] until out.groupEnd[0]) assertFalse(out.runErase[r])
        assertEquals(2, out.groupEnd[0] - out.groupStart[0])
        // Second bloc : le coup de gomme, en couleur vive, plein, et il ne creuse rien.
        assertFalse(out.groupIsolated[1])
        assertEquals(1, out.groupEnd[1] - out.groupStart[1])
        val r = out.groupStart[1]
        assertFalse(out.runErase[r])
        assertEquals(ZoomRenderer.ERASER_VIEW_COLOR, out.runColor[r])
        assertTrue(out.runPoints[r] >= 2)
    }

    @Test
    fun editModeStillShowsAnEraserWhenNothingElseIsOnTheLayer() {
        val scene = scene()
        scene.line(300.0)
        // Un coup de gomme loin de tout contenu visible n'est pas gardé : on le pose sur le trait puis on le déplace.
        scene.beginStroke(-80.0, 300.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 300.0)
        val eraser = scene.endStroke()!!.id
        scene.beginBoxEdit(eraser)
        scene.moveBoxBy(0.0, -300.0)
        scene.endBoxEdit()
        val out = RenderList()
        ZoomRenderer.build(scene, 300.0, 300.0, out, ghost = true)
        assertEquals(1, (0 until out.runCount).count { out.runColor[it] == ZoomRenderer.ERASER_VIEW_COLOR })
    }

    @Test
    fun anItemMovesOneStepAtATimeAndAllTheWay() {
        val scene = scene()
        val a = scene.line(0.0)
        val b = scene.rect(-50.0, -50.0, 50.0, 50.0)
        val c = scene.addText("T", 0.0, 0.0, 30.0, black, 0, "", 1.0)!!.id
        assertTrue(scene.reorder(a, OrderMove.FORWARD))
        assertEquals(listOf(b, a, c), scene.stack())
        assertTrue(scene.reorder(a, OrderMove.TO_FRONT))
        assertEquals(listOf(b, c, a), scene.stack())
        assertFalse(scene.reorder(a, OrderMove.TO_FRONT))
        assertFalse(scene.reorder(a, OrderMove.FORWARD))
        assertTrue(scene.reorder(a, OrderMove.BACKWARD))
        assertEquals(listOf(b, a, c), scene.stack())
        assertTrue(scene.reorder(c, OrderMove.TO_BACK))
        assertEquals(listOf(c, b, a), scene.stack())
        assertFalse(scene.reorder(c, OrderMove.BACKWARD))
        assertTrue(scene.layer(scene.depth)!!.hasCustomOrder)
    }

    @Test
    fun theWidgetKnowsWhereTheItemIsAndWhatItCanStillDo() {
        val scene = scene()
        val a = scene.line(0.0)
        val b = scene.rect(-50.0, -50.0, 50.0, 50.0)
        val c = scene.rect(-20.0, -20.0, 20.0, 20.0)
        val mid = scene.orderInfo(b)!!
        assertEquals(2, mid.rank)
        assertEquals(3, mid.count)
        assertTrue(mid.canBackward && mid.canForward && mid.canToBack && mid.canToFront)
        val bottom = scene.orderInfo(a)!!
        assertEquals(1, bottom.rank)
        assertFalse(bottom.canBackward || bottom.canToBack)
        val top = scene.orderInfo(c)!!
        assertEquals(3, top.rank)
        assertFalse(top.canForward || top.canToFront)
    }

    @Test
    fun reorderingIsOneUndoStepAndTheRendererFollowsTheNewOrder() {
        val scene = scene()
        val a = scene.rect(-60.0, -60.0, 60.0, 60.0)
        scene.rect(-30.0, -30.0, 30.0, 30.0)
        fun firstFillColorIsBig(): Boolean {
            val out = RenderList()
            ZoomRenderer.build(scene, 300.0, 300.0, out)
            // La première passe tracée est celle du fond : le grand carré s'étend sur 120 px de large.
            val xs = (0 until out.runPoints[0]).map { out.coords[out.runStart[0] + 2 * it] }
            return xs.max() - xs.min() > 100f
        }
        assertTrue(firstFillColorIsBig())
        assertTrue(scene.reorder(a, OrderMove.TO_FRONT))
        assertFalse(firstFillColorIsBig())
        assertTrue(scene.undo())
        assertTrue(firstFillColorIsBig())
        assertTrue(scene.redo())
        assertFalse(firstFillColorIsBig())
    }

    @Test
    fun movingAnItemInFrontOfAnEraserStrokeStopsItBeingCutByIt() {
        val scene = scene()
        val a = scene.line(0.0)
        scene.beginStroke(-80.0, 0.0, ZoomScene.ERASER, 20.0)
        scene.extendStroke(80.0, 0.0)
        scene.endStroke()
        // Le trait est sous la gomme : elle le creuse.
        fun erasedAfterLine(): Boolean {
            val out = RenderList()
            ZoomRenderer.build(scene, 300.0, 300.0, out)
            val kinds = (0 until out.runCount).map { out.runErase[it] }
            return kinds == listOf(false, true)
        }
        assertTrue(erasedAfterLine())
        assertTrue(scene.reorder(a, OrderMove.TO_FRONT))
        assertFalse(erasedAfterLine())
    }

    @Test
    fun aDeletedItemComesBackAtItsPlaceOnUndo() {
        val scene = scene()
        val a = scene.line(-40.0)
        val b = scene.rect(-50.0, -50.0, 50.0, 50.0)
        val c = scene.line(40.0)
        scene.deleteBox(b)
        assertEquals(listOf(a, c), scene.stack())
        assertTrue(scene.undo())
        assertEquals(listOf(a, b, c), scene.stack())
        assertTrue(scene.redo())
        assertEquals(listOf(a, c), scene.stack())
    }

    @Test
    fun aDuplicateIsPlacedJustAboveItsOriginal() {
        val scene = scene()
        val a = scene.line(-40.0)
        val b = scene.line(40.0)
        val copy = scene.duplicateBox(a, 16.0)!!
        assertEquals(listOf(a, copy.id, b), scene.stack())
        assertTrue(scene.undo())
        assertEquals(listOf(a, b), scene.stack())
        assertTrue(scene.redo())
        assertEquals(listOf(a, copy.id, b), scene.stack())
    }

    @Test
    fun repeatedTapsWalkDownThePileAtTheSamePlace() {
        val scene = scene()
        val a = scene.rect(-60.0, -60.0, 60.0, 60.0)
        val b = scene.rect(-40.0, -40.0, 40.0, 40.0)
        val c = scene.rect(-20.0, -20.0, 20.0, 20.0)
        assertEquals(c, scene.boxAt(0.0, 0.0)!!.id)
        assertEquals(b, scene.boxAt(0.0, 0.0, below = c)!!.id)
        assertEquals(a, scene.boxAt(0.0, 0.0, below = b)!!.id)
        // En bas de la pile, on repart du dessus.
        assertEquals(c, scene.boxAt(0.0, 0.0, below = a)!!.id)
        // Là où il n'y a que le grand carré, il reste lui-même.
        assertEquals(a, scene.boxAt(55.0, 55.0, below = a)!!.id)
    }

    @Test
    fun aStrokeCanBeMovedResizedAndRecoloredWithItsWidthFollowing() {
        val scene = scene()
        val id = scene.line(0.0)
        val before = scene.box(id) as StrokeBox
        assertTrue(scene.beginBoxEdit(id))
        scene.moveBoxBy(30.0, 20.0)
        scene.endBoxEdit()
        val moved = scene.box(id) as StrokeBox
        assertEquals(before.x + 30.0 / scene.zoom, moved.x, 1e-9)
        assertEquals(before.y + 20.0 / scene.zoom, moved.y, 1e-9)

        // Le coin bas-droit (2) tiré : la boîte, le trait et son épaisseur grandissent d'un même facteur.
        assertTrue(scene.beginBoxEdit(id))
        val r = scene.boxScreenRect(moved)
        scene.resizeBoxTo(2, r[2] + 200.0, r[3] + 0.0)
        scene.endBoxEdit()
        val big = scene.box(id) as StrokeBox
        val k = big.w / moved.w
        assertTrue(k > 1.5)
        assertEquals(moved.stroke.width * k, big.stroke.width, 1e-9)
        // Le coin haut-gauche n'a pas bougé.
        assertEquals(moved.x - moved.w / 2, big.x - big.w / 2, 1e-9)

        val red = 0xFFFF0000.toInt()
        scene.changeBox(big.recolored(red))
        assertEquals(red, (scene.box(id) as StrokeBox).stroke.color)
        assertTrue(scene.undo())
        assertEquals(black, (scene.box(id) as StrokeBox).stroke.color)
        assertTrue(scene.undo())
        assertEquals(moved.w, (scene.box(id) as StrokeBox).w, 1e-9)
    }

    @Test
    fun aSinglePointStrokeCanBeMovedButNotResized() {
        val scene = scene()
        scene.beginStroke(10.0, 10.0, black, 12.0)
        val id = scene.endStroke()!!.id
        assertTrue(scene.beginBoxEdit(id))
        scene.resizeBoxTo(2, 100.0, 100.0)
        scene.endBoxEdit()
        val dot = scene.box(id) as StrokeBox
        assertEquals(0.0, dot.w, 0.0)
        assertTrue(dot.x.isFinite() && dot.stroke.width.isFinite())
        assertNotNull(scene.boxAt(10.0, 10.0))
    }

    @Test
    fun theCustomOrderSurvivesTheFileAndTheDefaultOneIsNotStored() {
        val store = ZoomStore(dir)
        val p = store.create("Pile", 10.0)
        val a = p.scene.line(-40.0)
        val b = p.scene.rect(-50.0, -50.0, 50.0, 50.0)
        val c = p.scene.addText("T", 0.0, 0.0, 30.0, black, 0, "", 1.0)!!.id
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        assertEquals(listOf(a, b, c), store.load(p.meta.id)!!.scene.stack())

        p.scene.reorder(a, OrderMove.TO_FRONT)
        store.save(ZoomSnapshot.of(p.meta, p.scene))
        val loaded = store.load(p.meta.id)!!
        assertEquals(listOf(b, c, a), loaded.scene.stack())
        // Ce qu'on pose ensuite reste devant tout, et l'ordre rangé est stable.
        val d = loaded.scene.rect(0.0, 0.0, 40.0, 40.0)
        assertEquals(listOf(b, c, a, d), loaded.scene.stack())
    }
}
