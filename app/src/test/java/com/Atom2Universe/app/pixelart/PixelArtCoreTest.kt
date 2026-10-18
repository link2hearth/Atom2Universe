package com.Atom2Universe.app.pixelart

import com.Atom2Universe.app.pixelart.core.BlendMode
import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.DocumentOps
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.FloodFill
import com.Atom2Universe.app.pixelart.core.PixelColor
import com.Atom2Universe.app.pixelart.core.PixelRect
import com.Atom2Universe.app.pixelart.core.Raster
import com.Atom2Universe.app.pixelart.core.SelectMode
import com.Atom2Universe.app.pixelart.core.Selection
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.pixelart.core.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde-fous du cœur de l'éditeur de pixel art (aucun Android : tout tourne en JVM). */
class PixelArtCoreTest {

    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()
    private val GREEN = 0xFF00FF00.toInt()

    private fun session(w: Int = 16, h: Int = 16): EditorSession = EditorSession(Document.blank(w, h))

    private fun EditorSession.px(x: Int, y: Int): Int = doc.cel(activeLayerId, activeFrameId)?.get(y * doc.width + x) ?: 0

    private fun EditorSession.count(color: Int): Int {
        val cel = doc.cel(activeLayerId, activeFrameId) ?: return 0
        return cel.count { it == color }
    }

    private fun EditorSession.drag(vararg pts: Pair<Int, Int>) {
        pointerDown(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) pointerMove(pts[i].first, pts[i].second)
        pointerUp()
    }

    // ---- Couleurs ----------------------------------------------------------------------------

    @Test
    fun overOpaqueEtTransparent() {
        assertEquals(RED, PixelColor.over(BLUE, RED))
        assertEquals(BLUE, PixelColor.over(BLUE, 0))
        assertEquals(RED, PixelColor.over(0, RED))
    }

    @Test
    fun overMoitieTransparente() {
        val half = PixelColor.withAlpha(RED, 128)
        val out = PixelColor.over(BLUE, half)
        assertEquals(255, PixelColor.alpha(out))
        assertTrue(PixelColor.red(out) in 127..129)
        assertTrue(PixelColor.blue(out) in 126..128)
    }

    @Test
    fun blendMultiplyAssombritEtSansEffetSurFondTransparent() {
        val grey = PixelColor.argb(255, 128, 128, 128)
        val out = PixelColor.blend(grey, grey, BlendMode.MULTIPLY, 255)
        assertEquals(64, PixelColor.red(out))
        assertEquals(RED, PixelColor.blend(0, RED, BlendMode.MULTIPLY, 255))
    }

    @Test
    fun hexAllerRetour() {
        assertEquals(RED, PixelColor.parseHex("#f00"))
        assertEquals(RED, PixelColor.parseHex("FF0000"))
        assertEquals(0x80FF0000.toInt(), PixelColor.parseHex("#80FF0000"))
        assertNull(PixelColor.parseHex("zz"))
        assertEquals("#FF0000", PixelColor.toHex(RED))
        assertEquals("#80FF0000", PixelColor.toHex(0x80FF0000.toInt()))
    }

    @Test
    fun hsvAllerRetour() {
        for (c in intArrayOf(RED, GREEN, BLUE, 0xFF336699.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
            val hsv = PixelColor.toHsv(c)
            val back = PixelColor.fromHsv(hsv[0], hsv[1], hsv[2])
            assertTrue("$c -> $back", PixelColor.distance(c, back) <= 1)
        }
    }

    // ---- Rasterisation -----------------------------------------------------------------------

    private fun collect(block: (com.Atom2Universe.app.pixelart.core.PointSink) -> Unit): Set<Long> {
        val set = HashSet<Long>()
        block(com.Atom2Universe.app.pixelart.core.PointSink { x, y -> set.add((x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)) })
        return set
    }

    private fun Set<Long>.has(x: Int, y: Int) = contains((x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL))

    @Test
    fun ligneRelieLesDeuxExtremites() {
        val pts = collect { Raster.line(1, 1, 8, 4, it) }
        assertTrue(pts.has(1, 1))
        assertTrue(pts.has(8, 4))
        assertEquals(8, pts.size)
    }

    @Test
    fun rectanglePleinEtVide() {
        assertEquals(20, collect { Raster.rect(0, 0, 4, 3, true, it) }.size)
        assertEquals(14, collect { Raster.rect(0, 0, 4, 3, false, it) }.size)
    }

    @Test
    fun ellipseSymetriqueEtRemplissageContientLeContour() {
        for ((w, h) in listOf(5 to 5, 8 to 5, 11 to 4, 3 to 9, 2 to 2, 1 to 6)) {
            val outline = collect { Raster.ellipse(0, 0, w - 1, h - 1, false, it) }
            val filled = collect { Raster.ellipse(0, 0, w - 1, h - 1, true, it) }
            assertTrue("contour $w×$h dans le remplissage", filled.containsAll(outline))
            for (x in 0 until w) for (y in 0 until h) {
                assertEquals("symétrie H $w×$h ($x,$y)", filled.has(x, y), filled.has(w - 1 - x, y))
                assertEquals("symétrie V $w×$h ($x,$y)", filled.has(x, y), filled.has(x, h - 1 - y))
            }
            assertTrue(filled.all { it.let { k -> (k shr 32).toInt() in 0 until w && (k and 0xFFFFFFFFL).toInt() in 0 until h } })
        }
    }

    @Test
    fun empreinteDuPinceau() {
        assertEquals(1, Raster.footprint(1, true).size / 2)
        assertEquals(5, Raster.footprint(3, true).size / 2)      // une croix
        assertEquals(9, Raster.footprint(3, false).size / 2)     // un carré
        assertEquals(21, Raster.footprint(5, true).size / 2)     // un disque
        assertEquals(4, Raster.footprint(2, true).size / 2)
    }

    @Test
    fun toutesLesFormesTiennentDansLeurBoite() {
        for (kind in ShapeKind.values()) for (filled in listOf(false, true)) {
            val pts = collect { Raster.shape(kind, 2, 3, 12, 9, filled, it) }
            assertTrue("$kind vide", pts.isNotEmpty())
            for (k in pts) {
                val x = (k shr 32).toInt()
                val y = (k and 0xFFFFFFFFL).toInt()
                if (kind == ShapeKind.ARROW) continue // la flèche déborde de sa boîte : sa pointe est le point d'arrivée
                assertTrue("$kind sort de la boîte ($x,$y)", x in 2..12 && y in 3..9)
            }
        }
    }

    @Test
    fun triangleRempliCouvreSonMilieu() {
        val pts = collect { Raster.shape(ShapeKind.TRIANGLE, 0, 0, 10, 10, true, it) }
        assertTrue(pts.has(5, 5))
        assertTrue(pts.has(5, 0))
        assertFalse(pts.has(0, 0))
    }

    // ---- Remplissage -------------------------------------------------------------------------

    @Test
    fun remplissageContigu() {
        val w = 6
        val h = 4
        val img = IntArray(w * h)
        for (y in 0 until h) img[y * w + 3] = RED  // un mur vertical
        val bounds = PixelRect.empty()
        val m = FloodFill.region(img, w, h, 0, 0, 0, true, null, bounds)
        assertEquals(3 * h, m.count { it.toInt() != 0 })
        assertEquals(3, bounds.width)
        val all = FloodFill.region(img, w, h, 0, 0, 0, false)
        assertEquals(5 * h, all.count { it.toInt() != 0 })
    }

    @Test
    fun remplissageAvecTolerance() {
        val img = intArrayOf(0xFF000000.toInt(), 0xFF101010.toInt(), 0xFF808080.toInt())
        assertEquals(1, FloodFill.region(img, 3, 1, 0, 0, 0, true).count { it.toInt() != 0 })
        assertEquals(2, FloodFill.region(img, 3, 1, 0, 0, 20, true).count { it.toInt() != 0 })
    }

    // ---- Dessin et historique ------------------------------------------------------------------

    @Test
    fun traitAnnulerRetablir() {
        val s = session()
        s.primary = RED
        s.drag(2 to 2, 8 to 2)
        assertEquals(7, s.count(RED))
        s.undo()
        assertEquals(0, s.count(RED))
        s.redo()
        assertEquals(7, s.count(RED))
    }

    @Test
    fun gommeEfface() {
        val s = session()
        s.primary = RED
        s.drag(0 to 0, 5 to 0)
        s.tool = Tool.ERASER
        s.drag(2 to 0, 3 to 0)
        assertEquals(4, s.count(RED))
        s.undo()
        assertEquals(6, s.count(RED))
    }

    @Test
    fun pixelParfaitRetireLesCoins() {
        val s = session()
        s.primary = RED
        s.options.pixelPerfect = true
        // Un escalier : sans correction on obtient des « L ».
        s.drag(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2)
        assertEquals(3, s.count(RED))
        assertEquals(RED, s.px(0, 0))
        assertEquals(RED, s.px(1, 1))
        assertEquals(RED, s.px(2, 2))
        val t = session()
        t.primary = RED
        t.options.pixelPerfect = false
        t.drag(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2)
        assertEquals(5, t.count(RED))
    }

    @Test
    fun symetrieMiroir() {
        val s = session(10, 10)
        s.primary = RED
        s.options.mirrorX = true
        s.drag(1 to 3, 1 to 3)
        assertEquals(RED, s.px(1, 3))
        assertEquals(RED, s.px(8, 3))
        assertEquals(2, s.count(RED))
    }

    @Test
    fun potDePeintureRespecteLesMurs() {
        val s = session(8, 8)
        s.primary = BLUE
        s.tool = Tool.SHAPE
        s.options.shape = ShapeKind.RECT
        s.options.shapeFill = ShapeFill.OUTLINE
        s.drag(0 to 0, 3 to 3)
        s.primary = RED
        s.tool = Tool.FILL
        s.drag(1 to 1, 1 to 1)
        assertEquals(4, s.count(RED))       // l'intérieur 2×2
        s.undo()
        assertEquals(0, s.count(RED))
        assertEquals(12, s.count(BLUE))
    }

    @Test
    fun calqueVerrouilleNeSeDessinePas() {
        val s = session()
        s.primary = RED
        s.editLayer(s.activeLayerId) { it.locked = true }
        s.drag(1 to 1, 4 to 1)
        assertEquals(0, s.count(RED))
    }

    @Test
    fun selectionLimiteLeDessin() {
        val s = session()
        s.primary = RED
        s.tool = Tool.SELECT_RECT
        s.drag(2 to 2, 5 to 5)
        assertNotNull(s.selection)
        assertEquals(16, s.selection!!.count)
        s.tool = Tool.PENCIL
        s.drag(0 to 3, 10 to 3)
        assertEquals(4, s.count(RED))
    }

    @Test
    fun deplacerUneSelectionEstUneSeuleEtapeAnnulable() {
        val s = session()
        s.primary = RED
        s.tool = Tool.SHAPE
        s.options.shape = ShapeKind.RECT
        s.options.shapeFill = ShapeFill.FILL
        s.drag(1 to 1, 2 to 2)
        s.tool = Tool.SELECT_RECT
        s.drag(1 to 1, 2 to 2)
        s.tool = Tool.MOVE
        s.drag(1 to 1, 6 to 1)   // décalage de 5 vers la droite
        s.commitFloating()
        assertEquals(RED, s.px(6, 1))
        assertEquals(0, s.px(1, 1))
        assertEquals(4, s.count(RED))
        s.undo()
        assertEquals(RED, s.px(1, 1))
        assertEquals(0, s.px(6, 1))
        assertEquals(4, s.count(RED))
    }

    @Test
    fun deplacementAnnuleParUndoAvantValidation() {
        val s = session()
        s.primary = RED
        s.tool = Tool.SHAPE
        s.options.shapeFill = ShapeFill.FILL
        s.drag(1 to 1, 2 to 2)
        s.tool = Tool.MOVE                 // pas de sélection : tout le calque
        s.drag(1 to 1, 4 to 4)
        assertNotNull(s.floating)
        assertEquals(0, s.count(RED))      // le contenu est « en l'air »
        s.undo()
        assertEquals(4, s.count(RED))
        assertEquals(RED, s.px(1, 1))
    }

    @Test
    fun copierColler() {
        val s = session()
        s.primary = RED
        s.tool = Tool.SHAPE
        s.options.shapeFill = ShapeFill.FILL
        s.drag(0 to 0, 1 to 1)
        s.tool = Tool.SELECT_RECT
        s.drag(0 to 0, 1 to 1)
        assertTrue(s.copy())
        assertTrue(s.paste(5, 5))
        s.commitFloating()
        assertEquals(8, s.count(RED))
        assertEquals(RED, s.px(5, 5))
    }

    @Test
    fun retournerUneSelection() {
        val s = session()
        s.primary = RED
        s.tool = Tool.PENCIL
        s.drag(0 to 0, 0 to 0)
        s.primary = BLUE
        s.drag(2 to 0, 2 to 0)
        s.tool = Tool.SELECT_RECT
        s.drag(0 to 0, 2 to 0)
        s.flipSelection(horizontal = true)
        assertEquals(BLUE, s.px(0, 0))
        assertEquals(RED, s.px(2, 0))
    }

    @Test
    fun historiqueSaitSiLeDessinAChange() {
        val s = session()
        assertFalse(s.history.modifiedSinceSave)
        s.primary = RED
        s.drag(1 to 1, 1 to 1)
        assertTrue(s.history.modifiedSinceSave)
        s.history.markSaved()
        assertFalse(s.history.modifiedSinceSave)
        s.undo()
        assertTrue(s.history.modifiedSinceSave)
        s.redo()
        assertFalse(s.history.modifiedSinceSave)
    }

    // ---- Structure ---------------------------------------------------------------------------

    @Test
    fun calquesAjoutSuppressionEtAnnulation() {
        val s = session()
        s.primary = RED
        s.drag(1 to 1, 1 to 1)
        val first = s.activeLayerId
        s.addLayer("2")
        assertEquals(2, s.doc.layers.size)
        s.primary = BLUE
        s.drag(2 to 2, 2 to 2)
        s.deleteLayer()
        assertEquals(1, s.doc.layers.size)
        assertEquals(first, s.activeLayerId)
        s.undo()
        assertEquals(2, s.doc.layers.size)
        assertEquals(BLUE, s.doc.cel(s.doc.layers[1].id, s.activeFrameId)!![2 * 16 + 2])
        s.undo()  // le trait bleu
        s.undo()  // l'ajout du calque
        assertEquals(1, s.doc.layers.size)
        assertEquals(RED, s.px(1, 1))
    }

    @Test
    fun uneSeuleImageOuUnSeulCalqueNeSePeutPasSupprimer() {
        val s = session()
        s.deleteLayer()
        s.deleteFrame()
        assertEquals(1, s.doc.layers.size)
        assertEquals(1, s.doc.frames.size)
    }

    @Test
    fun imagesDupliquerEtAnnuler() {
        val s = session()
        s.primary = RED
        s.drag(3 to 3, 3 to 3)
        s.addFrame(copyCurrent = true)
        assertEquals(2, s.doc.frames.size)
        assertEquals(1, s.frameIndex)
        assertEquals(RED, s.px(3, 3))
        s.undo()
        assertEquals(1, s.doc.frames.size)
        s.redo()
        assertEquals(2, s.doc.frames.size)
        assertEquals(RED, s.px(3, 3))
    }

    @Test
    fun fusionnerVersLeBas() {
        val s = session()
        s.primary = RED
        s.drag(1 to 1, 1 to 1)
        s.addLayer("haut")
        s.primary = BLUE
        s.drag(2 to 2, 2 to 2)
        s.mergeLayerDown()
        assertEquals(1, s.doc.layers.size)
        assertEquals(RED, s.px(1, 1))
        assertEquals(BLUE, s.px(2, 2))
        s.undo()
        assertEquals(2, s.doc.layers.size)
        assertEquals(0, s.doc.cel(s.doc.layers[0].id, s.activeFrameId)!![2 * 16 + 2])
    }

    @Test
    fun redimensionnerLaToileEtAnnuler() {
        val s = session(8, 8)
        s.primary = RED
        s.drag(7 to 7, 7 to 7)
        s.resizeCanvas(12, 10, anchorX = 2, anchorY = 2)
        assertEquals(12, s.doc.width)
        assertEquals(10, s.doc.height)
        assertEquals(RED, s.px(11, 9))
        s.undo()
        assertEquals(8, s.doc.width)
        assertEquals(RED, s.px(7, 7))
    }

    @Test
    fun rotationDeLaToile() {
        val s = session(4, 2)
        s.primary = RED
        s.drag(0 to 0, 0 to 0)
        s.rotateCanvas(clockwise = true)
        assertEquals(2, s.doc.width)
        assertEquals(4, s.doc.height)
        assertEquals(RED, s.px(1, 0))
        s.undo()
        assertEquals(4, s.doc.width)
        assertEquals(RED, s.px(0, 0))
    }

    // ---- Composition -------------------------------------------------------------------------

    @Test
    fun compositionOrdreOpaciteEtVisibilite() {
        val d = Document.blank(2, 1)
        val bottom = d.layers[0]
        val top = DocumentOps.addLayer(d, 0, "haut").first
        val f = d.frames[0].id
        d.celOrCreate(bottom.id, f)[0] = RED
        d.celOrCreate(top.id, f)[0] = BLUE
        assertEquals(BLUE, Compositor.compositeFrame(d, f)[0])
        top.visible = false
        assertEquals(RED, Compositor.compositeFrame(d, f)[0])
        top.visible = true
        top.opacity = 0
        assertEquals(RED, Compositor.compositeFrame(d, f)[0])
    }

    @Test
    fun selectionContourRectangleEnQuatreSegments() {
        val sel = Selection(10, 10)
        sel.selectRect(2, 2, 6, 5, SelectMode.REPLACE)
        assertEquals(4 * 4, sel.outline().size)
        sel.selectRect(3, 3, 4, 4, SelectMode.SUBTRACT)
        assertTrue(sel.outline().size > 16)
    }

    @Test
    fun documentSuitLesCelsModifiees() {
        val d = Document.blank(4, 4)
        d.peekDirty().let { d.markSaved(it) }
        assertTrue(d.peekDirty().written.isEmpty())
        d.celOrCreate(d.layers[0].id, d.frames[0].id)
        assertEquals(1, d.peekDirty().written.size)
        d.markSaved(d.peekDirty())
        assertTrue(d.peekDirty().written.isEmpty())
    }

    @Test
    fun pipetteLitLeDessinPuisLaReference() {
        val s = session()
        s.fallbackSampler = { x, y -> if (x == 3 && y == 3) BLUE else 0 }
        s.primary = RED
        s.tool = Tool.PENCIL
        s.drag(1 to 1, 1 to 1)
        s.tool = Tool.PICKER
        s.primary = 0xFF000000.toInt()
        s.drag(3 to 3, 3 to 3)          // dessin transparent : on lit la référence
        assertEquals(BLUE, s.primary)
        s.drag(1 to 1, 1 to 1)          // le dessin passe avant la référence
        assertEquals(RED, s.primary)
        s.primary = GREEN
        s.drag(5 to 5, 5 to 5)          // rien nulle part : la couleur ne bouge pas
        assertEquals(GREEN, s.primary)
    }
}
