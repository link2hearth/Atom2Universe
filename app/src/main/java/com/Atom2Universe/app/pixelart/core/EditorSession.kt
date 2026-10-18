package com.Atom2Universe.app.pixelart.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Tool {
    PENCIL, BRUSH, ERASER, FILL, PICKER, SHAPE,
    SELECT_RECT, SELECT_LASSO, SELECT_WAND, MOVE, HAND,
}

enum class ShapeFill { OUTLINE, FILL, BOTH }

/** Réglages des outils ; chaque outil lit ceux qui le concernent. */
class ToolOptions {
    var size = 1
    var round = true
    var pixelPerfect = true
    /** Force de la brosse douce, 1..100. */
    var opacity = 60
    var shape = ShapeKind.RECT
    var shapeFill = ShapeFill.OUTLINE
    /** Force la forme à rester carrée / ronde / équilatérale. */
    var shapeSquare = false
    var tolerance = 0
    var contiguous = true
    var sampleAllLayers = false
    var mirrorX = false
    var mirrorY = false
    var selectMode = SelectMode.REPLACE
}

/** Ce que la session dit à l'écran. Toutes les méthodes sont facultatives. */
interface SessionListener {
    /** Une zone de l'image composée doit être rafraîchie. */
    fun onPixelsChanged(rect: PixelRect) {}
    /** Tout est à rafraîchir (annuler, calque supprimé, taille changée…). */
    fun onDocumentChanged() {}
    fun onSelectionChanged() {}
    fun onFloatingChanged() {}
    fun onColorPicked(color: Int) {}
    fun onHistoryChanged() {}
    /** Le calque ou l'image active a changé. */
    fun onActiveChanged() {}
    /** Aperçu d'un geste en cours (cadre, lasso) : simple redessin. */
    fun onOverlayChanged() {}
}

/** Contenu détaché de la cel, en train d'être déplacé. */
class Floating(
    val editor: CelEditor,
    val layerId: Int,
    val frameId: Int,
    var pixels: IntArray,
    var mask: ByteArray,
    var bw: Int,
    var bh: Int,
    var x: Int,
    var y: Int,
    /** La sélection d'où vient le contenu, à déplacer avec lui (null = calque entier). */
    val fromSelection: Selection?,
    val originX: Int,
    val originY: Int,
)

/** Presse-papiers partagé entre tous les dessins ouverts dans la session de l'application. */
class ClipData(val pixels: IntArray, val mask: ByteArray, val w: Int, val h: Int)

object PixelClipboard {
    @Volatile
    var data: ClipData? = null
}

private class FloatList {
    var a = FloatArray(64)
    var n = 0
    fun add(v: Float) {
        if (n == a.size) a = a.copyOf(n * 2)
        a[n++] = v
    }
    fun clear() { n = 0 }
    fun toArray(): FloatArray = a.copyOf(n)
}

/**
 * Tout ce que fait l'éditeur, sans un seul appel Android : un geste (appui, déplacement,
 * relâchement) se traduit en écritures dans le [Document] et en une étape d'historique.
 * L'écran n'a plus qu'à convertir les doigts en pixels et à afficher ce que la session signale.
 */
class EditorSession(val doc: Document) {

    val history = History()
    val options = ToolOptions()
    var listener: SessionListener? = null

    /**
     * Couleur lue ailleurs que dans le dessin (l'image de référence, gérée par l'écran) : la pipette
     * y a recours quand le dessin est transparent sous le doigt. Retourne 0 s'il n'y a rien.
     */
    var fallbackSampler: ((Int, Int) -> Int)? = null

    var primary: Int = 0xFF000000.toInt()
    var secondary: Int = 0xFFFFFFFF.toInt()

    var tool: Tool = Tool.PENCIL
        set(value) {
            if (field == value) return
            finishGesture()
            commitFloating()
            field = value
        }

    var activeLayerId: Int = doc.layers.lastOrNull()?.id ?: 0
        private set
    var activeFrameId: Int = doc.frames.firstOrNull()?.id ?: 0
        private set

    val layerIndex get() = doc.layerIndexOf(activeLayerId).coerceAtLeast(0)
    val frameIndex get() = doc.frameIndexOf(activeFrameId).coerceAtLeast(0)
    val activeLayer: Layer get() = doc.layers[layerIndex]
    val activeFrame: Frame get() = doc.frames[frameIndex]

    var selection: Selection? = null
        private set
    var floating: Floating? = null
        private set

    // ---- Aperçus lus par l'écran -------------------------------------------------------------

    /** Cadre de sélection en cours de tracé (outil rectangle). */
    val previewRect = PixelRect.empty()
    var hasPreviewRect = false
        private set
    private val lassoXs = FloatList()
    private val lassoYs = FloatList()
    val lassoPoints: Pair<FloatArray, FloatArray>? get() =
        if (lassoXs.n >= 2) lassoXs.toArray() to lassoYs.toArray() else null

    // ---- État du geste -----------------------------------------------------------------------

    private enum class Gesture { NONE, FREEHAND, SHAPE, RECT_SELECT, LASSO, MOVE_FLOAT, PICK }

    private var gesture = Gesture.NONE
    private var editor: CelEditor? = null
    private var gLayerId = 0
    private var gFrameId = 0
    private var startX = 0
    private var startY = 0
    private var lastX = 0
    private var lastY = 0
    private var spacing = 0f
    private var dragOriginX = 0
    private var dragOriginY = 0
    private val dirty = PixelRect.empty()
    private var ppA: IntArray? = null
    private var ppB: IntArray? = null

    val gestureActive get() = gesture != Gesture.NONE

    // ==== Gestes ================================================================================

    fun pointerDown(x: Int, y: Int) {
        if (gesture != Gesture.NONE) finishGesture()
        startX = x; startY = y; lastX = x; lastY = y
        when (tool) {
            Tool.HAND -> Unit
            Tool.PENCIL, Tool.BRUSH, Tool.ERASER -> beginFreehand(x, y)
            Tool.FILL -> fillAt(x, y)
            Tool.PICKER -> { gesture = Gesture.PICK; pickAt(x, y) }
            Tool.SHAPE -> beginShape()
            Tool.SELECT_RECT -> {
                commitFloating()
                gesture = Gesture.RECT_SELECT
                hasPreviewRect = true
                previewRect.set(x, y, x + 1, y + 1)
                listener?.onOverlayChanged()
            }
            Tool.SELECT_LASSO -> {
                commitFloating()
                gesture = Gesture.LASSO
                lassoXs.clear(); lassoYs.clear()
                lassoXs.add(x.toFloat()); lassoYs.add(y.toFloat())
                listener?.onOverlayChanged()
            }
            Tool.SELECT_WAND -> wandAt(x, y)
            Tool.MOVE -> beginMove(x, y)
        }
    }

    fun pointerMove(x: Int, y: Int) {
        if (x == lastX && y == lastY) return
        when (gesture) {
            Gesture.FREEHAND -> continueFreehand(x, y)
            Gesture.SHAPE -> updateShape(x, y)
            Gesture.RECT_SELECT -> {
                previewRect.set(min(startX, x), min(startY, y), max(startX, x) + 1, max(startY, y) + 1)
                listener?.onOverlayChanged()
            }
            Gesture.LASSO -> {
                lassoXs.add(x.toFloat()); lassoYs.add(y.toFloat())
                listener?.onOverlayChanged()
            }
            Gesture.MOVE_FLOAT -> {
                val f = floating
                if (f != null) {
                    f.x = dragOriginX + (x - startX)
                    f.y = dragOriginY + (y - startY)
                    listener?.onFloatingChanged()
                }
            }
            Gesture.PICK -> pickAt(x, y)
            Gesture.NONE -> Unit
        }
        lastX = x; lastY = y
    }

    fun pointerUp() {
        when (gesture) {
            Gesture.FREEHAND, Gesture.SHAPE -> commitEditor()
            Gesture.RECT_SELECT -> {
                val tap = previewRect.width == 1 && previewRect.height == 1
                if (tap && options.selectMode == SelectMode.REPLACE) setSelection(null)
                else applyMask(rectMask(previewRect), options.selectMode)
                hasPreviewRect = false
                listener?.onOverlayChanged()
            }
            Gesture.LASSO -> {
                val xs = lassoXs.toArray()
                val ys = lassoYs.toArray()
                if (xs.size >= 3) applyMask(Selection.fromPolygon(doc.width, doc.height, xs, ys), options.selectMode)
                else if (options.selectMode == SelectMode.REPLACE) setSelection(null)
                lassoXs.clear(); lassoYs.clear()
                listener?.onOverlayChanged()
            }
            Gesture.MOVE_FLOAT, Gesture.PICK, Gesture.NONE -> Unit
        }
        gesture = Gesture.NONE
    }

    /** Un deuxième doigt arrive : le geste en cours n'était pas un dessin, on l'efface. */
    fun pointerCancel() {
        when (gesture) {
            Gesture.FREEHAND, Gesture.SHAPE -> {
                editor?.let {
                    it.revertAll()
                    it.takeDirty(dirty)
                }
                editor = null
                flushDirty()
            }
            Gesture.RECT_SELECT -> { hasPreviewRect = false; listener?.onOverlayChanged() }
            Gesture.LASSO -> { lassoXs.clear(); lassoYs.clear(); listener?.onOverlayChanged() }
            Gesture.MOVE_FLOAT -> {
                val f = floating
                if (f != null) { f.x = dragOriginX; f.y = dragOriginY; listener?.onFloatingChanged() }
            }
            else -> Unit
        }
        gesture = Gesture.NONE
    }

    private fun finishGesture() {
        if (gesture != Gesture.NONE) pointerUp()
    }

    // ==== Dessin à main levée ===================================================================

    /** Le calque actif peut-il recevoir des pixels ? Verrouillé ou masqué : le geste ne fait rien. */
    private fun canPaint(): Boolean = activeLayer.let { !it.locked && it.visible }

    /** [clipToSelection] : faux pour le contenu flottant, qui a le droit de quitter la sélection. */
    private fun openEditor(clipToSelection: Boolean = true): CelEditor? {
        if (!canPaint()) return null
        val layer = activeLayer
        val frame = activeFrame
        gLayerId = layer.id
        gFrameId = frame.id
        val cel = doc.celOrCreate(layer.id, frame.id)
        return CelEditor(cel, doc.width, doc.height, if (clipToSelection) selection else null).also { editor = it }
    }

    private fun beginFreehand(x: Int, y: Int) {
        val ed = openEditor() ?: return
        gesture = Gesture.FREEHAND
        spacing = 0f
        ppA = null; ppB = null
        strokePixel(ed, x, y, first = true)
        flushEditorDirty(ed)
    }

    private fun continueFreehand(x: Int, y: Int) {
        val ed = editor ?: return
        var first = true
        Raster.line(lastX, lastY, x, y) { px, py ->
            if (first) { first = false } else strokePixel(ed, px, py, first = false)
        }
        flushEditorDirty(ed)
    }

    /** « Pixel parfait » : seulement au crayon d'un pixel (la gomme doit effacer exactement le trajet). */
    private val pixelPerfectActive get() =
        options.pixelPerfect && options.size <= 1 && tool == Tool.PENCIL

    private fun strokePixel(ed: CelEditor, x: Int, y: Int, first: Boolean) {
        if (pixelPerfectActive) {
            val a = ppA
            val b = ppB
            if (a != null && b != null &&
                abs(x - a[0]) == 1 && abs(y - a[1]) == 1 &&
                (b[0] == a[0] || b[1] == a[1]) && (b[0] == x || b[1] == y)
            ) {
                // b est le coin d'un « L » : on le retire pour garder un trait d'un pixel d'épaisseur.
                unpaint(ed, b[0], b[1])
                ppB = intArrayOf(x, y)
            } else {
                ppA = b
                ppB = intArrayOf(x, y)
            }
        }
        if (tool == Tool.BRUSH) {
            if (!first) {
                spacing += 1f
                if (spacing < max(1f, options.size / 4f)) return
                spacing = 0f
            }
        }
        paintStamp(ed, x, y)
    }

    private inline fun forMirrors(x: Int, y: Int, f: (Int, Int) -> Unit) {
        val mx = doc.width - 1 - x
        val my = doc.height - 1 - y
        f(x, y)
        if (options.mirrorX) f(mx, y)
        if (options.mirrorY) f(x, my)
        if (options.mirrorX && options.mirrorY) f(mx, my)
    }

    private fun paintStamp(ed: CelEditor, cx: Int, cy: Int) {
        forMirrors(cx, cy) { x, y ->
            when (tool) {
                Tool.PENCIL -> Raster.stamp(x, y, options.size, options.round) { px, py -> ed.set(px, py, primary) }
                Tool.ERASER -> Raster.stamp(x, y, options.size, options.round) { px, py -> ed.set(px, py, 0) }
                Tool.BRUSH -> softStamp(ed, x, y)
                else -> Unit
            }
        }
    }

    private fun unpaint(ed: CelEditor, cx: Int, cy: Int) {
        forMirrors(cx, cy) { x, y -> ed.set(x, y, ed.original(x, y)) }
    }

    /** Brosse douce : disque au centre plein, bord qui s'estompe. */
    private fun softStamp(ed: CelEditor, cx: Int, cy: Int) {
        val n = max(1, options.size)
        val r = n / 2f + 0.5f
        val reach = n / 2
        val baseAlpha = PixelColor.alpha(primary) * options.opacity / 100f
        for (j in -reach..reach) for (i in -reach..reach) {
            val d = sqrt((i * i + j * j).toFloat())
            if (d > r) continue
            val t = d / r
            val k = if (t < 0.25f) 1f else {
                val u = 1f - (t - 0.25f) / 0.75f
                u * u
            }
            val a = (baseAlpha * k + 0.5f).toInt()
            if (a <= 0) continue
            val x = cx + i
            val y = cy + j
            ed.set(x, y, PixelColor.over(ed.get(x, y), PixelColor.withAlpha(primary, a)))
        }
    }

    // ==== Formes ================================================================================

    private fun beginShape() {
        val ed = openEditor() ?: return
        gesture = Gesture.SHAPE
        drawShape(ed, startX, startY)
        flushEditorDirty(ed)
    }

    private fun updateShape(x: Int, y: Int) {
        val ed = editor ?: return
        ed.revertAll()
        drawShape(ed, startX, startY, x, y)
        flushEditorDirty(ed)
    }

    private fun drawShape(ed: CelEditor, sx: Int, sy: Int, ex: Int = sx, ey: Int = sy) {
        var x1 = ex
        var y1 = ey
        if (options.shapeSquare && options.shape != ShapeKind.LINE && options.shape != ShapeKind.ARROW) {
            val dx = x1 - sx
            val dy = y1 - sy
            val s = max(abs(dx), abs(dy))
            x1 = sx + if (dx < 0) -s else s
            y1 = sy + if (dy < 0) -s else s
        }
        val kind = options.shape
        val fill = if (kind == ShapeKind.LINE) ShapeFill.OUTLINE else options.shapeFill
        forShapeMirrors(sx, sy, x1, y1) { ax, ay, bx, by ->
            if (fill == ShapeFill.FILL || fill == ShapeFill.BOTH) {
                val color = if (fill == ShapeFill.BOTH) secondary else primary
                Raster.shape(kind, ax, ay, bx, by, true) { px, py -> ed.set(px, py, color) }
            }
            if (fill == ShapeFill.OUTLINE || fill == ShapeFill.BOTH) {
                Raster.shape(kind, ax, ay, bx, by, false) { px, py ->
                    Raster.stamp(px, py, options.size, options.round) { qx, qy -> ed.set(qx, qy, primary) }
                }
            }
        }
    }

    private inline fun forShapeMirrors(ax: Int, ay: Int, bx: Int, by: Int, f: (Int, Int, Int, Int) -> Unit) {
        val w = doc.width - 1
        val h = doc.height - 1
        f(ax, ay, bx, by)
        if (options.mirrorX) f(w - ax, ay, w - bx, by)
        if (options.mirrorY) f(ax, h - ay, bx, h - by)
        if (options.mirrorX && options.mirrorY) f(w - ax, h - ay, w - bx, h - by)
    }

    // ==== Pot de peinture, pipette, baguette ====================================================

    private fun sourcePixels(): IntArray =
        if (options.sampleAllLayers) Compositor.compositeFrame(doc, activeFrameId)
        else doc.cel(activeLayerId, activeFrameId) ?: IntArray(doc.pixelCount)

    private fun fillAt(x: Int, y: Int) {
        if (x !in 0 until doc.width || y !in 0 until doc.height) return
        val ed = openEditor() ?: return
        val src = sourcePixels()
        val bounds = PixelRect.empty()
        val mask = FloodFill.region(
            src, doc.width, doc.height, x, y, options.tolerance, options.contiguous, selection?.mask, bounds,
        )
        val w = doc.width
        for (yy in bounds.top until bounds.bottom) for (xx in bounds.left until bounds.right) {
            if (mask[yy * w + xx].toInt() != 0) ed.set(xx, yy, primary)
        }
        flushEditorDirty(ed)
        commitEditor()
    }

    /** Couleur de l'image composée (tous les calques visibles) en un pixel. */
    fun compositeAt(x: Int, y: Int, frameId: Int = activeFrameId): Int {
        if (x !in 0 until doc.width || y !in 0 until doc.height) return 0
        val i = y * doc.width + x
        var out = 0
        for (layer in doc.layers) {
            if (!layer.visible) continue
            val c = doc.cel(layer.id, frameId)?.get(i) ?: continue
            if (c ushr 24 != 0) out = PixelColor.blend(out, c, layer.blend, layer.opacity)
        }
        return out
    }

    private fun pickAt(x: Int, y: Int) {
        var c = compositeAt(x, y)
        if (c ushr 24 == 0) c = fallbackSampler?.invoke(x, y) ?: 0
        if (c ushr 24 != 0) {
            primary = c
            listener?.onColorPicked(c)
        }
    }

    private fun wandAt(x: Int, y: Int) {
        commitFloating()
        if (x !in 0 until doc.width || y !in 0 until doc.height) return
        val src = sourcePixels()
        val mask = FloodFill.region(src, doc.width, doc.height, x, y, options.tolerance, options.contiguous)
        applyMask(mask, options.selectMode)
    }

    // ==== Sélection =============================================================================

    private fun rectMask(r: PixelRect): ByteArray {
        val m = ByteArray(doc.pixelCount)
        for (y in r.top.coerceAtLeast(0) until r.bottom.coerceAtMost(doc.height)) {
            for (x in r.left.coerceAtLeast(0) until r.right.coerceAtMost(doc.width)) m[y * doc.width + x] = 1
        }
        return m
    }

    private fun applyMask(mask: ByteArray, mode: SelectMode) {
        val sel = selection ?: Selection(doc.width, doc.height)
        sel.combine(mask, if (selection == null && mode == SelectMode.SUBTRACT) SelectMode.REPLACE else mode)
        selection = if (sel.isEmpty) null else sel
        listener?.onSelectionChanged()
    }

    fun setSelection(sel: Selection?) {
        commitFloating()
        selection = sel?.takeIf { !it.isEmpty }
        listener?.onSelectionChanged()
    }

    fun selectAll() {
        commitFloating()
        selection = Selection(doc.width, doc.height).also { it.selectAll() }
        listener?.onSelectionChanged()
    }

    fun deselect() = setSelection(null)

    fun invertSelection() {
        commitFloating()
        val sel = selection ?: Selection(doc.width, doc.height)
        sel.invert()
        selection = if (sel.isEmpty) null else sel
        listener?.onSelectionChanged()
    }

    // ==== Contenu flottant (déplacer, retourner, coller) ========================================

    private fun beginMove(x: Int, y: Int) {
        var f = floating
        if (f == null) f = lift(selection) ?: return
        gesture = Gesture.MOVE_FLOAT
        dragOriginX = f.x
        dragOriginY = f.y
        startX = x; startY = y
    }

    /**
     * Détache du calque actif les pixels de [sel] (ou tout le calque si null) pour les déplacer.
     * Les pixels quittent la cel dès maintenant ; ils y reviennent à [commitFloating].
     */
    private fun lift(sel: Selection?): Floating? {
        if (!canPaint()) return null
        val ed = openEditor(clipToSelection = false) ?: return null
        val cel = ed.cel
        val w = doc.width
        val bounds = PixelRect.empty()
        val useMask: ByteArray
        if (sel != null) {
            bounds.set(sel.bounds.left, sel.bounds.top, sel.bounds.right, sel.bounds.bottom)
            useMask = sel.mask
        } else {
            useMask = ByteArray(doc.pixelCount)
            for (i in cel.indices) if (cel[i] ushr 24 != 0) {
                useMask[i] = 1
                bounds.union(i % w, i / w, i % w + 1, i / w + 1)
            }
        }
        if (bounds.isEmpty) { editor = null; return null }
        val bw = bounds.width
        val bh = bounds.height
        val pixels = IntArray(bw * bh)
        val mask = ByteArray(bw * bh)
        for (j in 0 until bh) for (i in 0 until bw) {
            val src = (bounds.top + j) * w + bounds.left + i
            if (useMask[src].toInt() != 0) {
                pixels[j * bw + i] = cel[src]
                mask[j * bw + i] = 1
                ed.set(bounds.left + i, bounds.top + j, 0)
            }
        }
        flushEditorDirty(ed)
        val f = Floating(ed, gLayerId, gFrameId, pixels, mask, bw, bh, bounds.left, bounds.top, sel?.copy(), bounds.left, bounds.top)
        floating = f
        editor = null
        listener?.onFloatingChanged()
        return f
    }

    /** Repose le contenu flottant sur le calque et enregistre le tout en une seule étape annulable. */
    fun commitFloating() {
        val f = floating ?: return
        floating = null
        val ed = f.editor
        val w = doc.width
        for (j in 0 until f.bh) for (i in 0 until f.bw) {
            if (f.mask[j * f.bw + i].toInt() == 0) continue
            val c = f.pixels[j * f.bw + i]
            if (c ushr 24 == 0) continue
            val x = f.x + i
            val y = f.y + j
            if (x !in 0 until w || y !in 0 until doc.height) continue
            ed.set(x, y, PixelColor.over(ed.get(x, y), c))
        }
        val delta = ed.build(f.layerId, f.frameId)
        if (delta != null) {
            doc.touch(f.layerId, f.frameId)
            history.push(CelEdit(listOf(delta)))
            listener?.onHistoryChanged()
        }
        // La sélection suit le contenu à son nouvel emplacement.
        if (f.fromSelection != null) {
            val moved = Selection(doc.width, doc.height)
            for (j in 0 until f.bh) for (i in 0 until f.bw) {
                if (f.mask[j * f.bw + i].toInt() == 0) continue
                val x = f.x + i
                val y = f.y + j
                if (x in 0 until w && y in 0 until doc.height) moved.mask[y * w + x] = 1
            }
            moved.recompute()
            selection = if (moved.isEmpty) null else moved
            listener?.onSelectionChanged()
        }
        listener?.onFloatingChanged()
        listener?.onDocumentChanged()
    }

    /** Abandonne le déplacement en cours : tout revient comme avant. */
    fun cancelFloating() {
        val f = floating ?: return
        floating = null
        f.editor.revertAll()
        doc.touch(f.layerId, f.frameId)
        listener?.onFloatingChanged()
        listener?.onDocumentChanged()
    }

    /** Retourne le contenu sélectionné (ou tout le calque s'il n'y a pas de sélection). */
    fun flipSelection(horizontal: Boolean) {
        val f = floating ?: lift(selection) ?: return
        f.pixels = if (horizontal) Transform.flipH(f.pixels, f.bw, f.bh) else Transform.flipV(f.pixels, f.bw, f.bh)
        f.mask = if (horizontal) Transform.flipH(f.mask.toIntArray(), f.bw, f.bh).toByteArray()
        else Transform.flipV(f.mask.toIntArray(), f.bw, f.bh).toByteArray()
        commitFloating()
    }

    fun rotateSelection(clockwise: Boolean) {
        val f = floating ?: lift(selection) ?: return
        val cx = f.x + f.bw / 2
        val cy = f.y + f.bh / 2
        f.pixels = Transform.rotate90(f.pixels, f.bw, f.bh, clockwise)
        f.mask = Transform.rotate90(f.mask.toIntArray(), f.bw, f.bh, clockwise).toByteArray()
        val nw = f.bh
        val nh = f.bw
        f.bw = nw
        f.bh = nh
        f.x = cx - nw / 2
        f.y = cy - nh / 2
        commitFloating()
    }

    // ==== Presse-papiers ========================================================================

    fun copy(): Boolean {
        commitFloating()
        val sel = selection
        val cel = doc.cel(activeLayerId, activeFrameId)
        if (cel == null) return false
        val w = doc.width
        val b = sel?.bounds ?: PixelRect(0, 0, doc.width, doc.height)
        if (b.isEmpty) return false
        val pixels = IntArray(b.width * b.height)
        val mask = ByteArray(b.width * b.height)
        for (j in 0 until b.height) for (i in 0 until b.width) {
            val src = (b.top + j) * w + b.left + i
            if (sel == null || sel.mask[src].toInt() != 0) {
                pixels[j * b.width + i] = cel[src]
                mask[j * b.width + i] = if (sel == null && cel[src] ushr 24 == 0) 0 else 1
            }
        }
        PixelClipboard.data = ClipData(pixels, mask, b.width, b.height)
        return true
    }

    fun cut(): Boolean {
        if (!copy()) return false
        deleteSelection()
        return true
    }

    /** Colle au coin haut-gauche (x, y) — ramené dans le dessin si besoin — comme contenu flottant. */
    fun paste(x: Int, y: Int, data: ClipData? = PixelClipboard.data): Boolean {
        val clip = data ?: return false
        commitFloating()
        if (!canPaint()) return false
        tool = Tool.MOVE
        val ed = openEditor(clipToSelection = false) ?: return false
        val px = x.coerceIn(min(0, doc.width - clip.w), max(0, doc.width - clip.w))
        val py = y.coerceIn(min(0, doc.height - clip.h), max(0, doc.height - clip.h))
        floating = Floating(ed, gLayerId, gFrameId, clip.pixels.copyOf(), clip.mask.copyOf(), clip.w, clip.h, px, py, null, px, py)
        editor = null
        listener?.onFloatingChanged()
        return true
    }

    fun deleteSelection() {
        commitFloating()
        if (!canPaint()) return
        val ed = openEditor() ?: return
        val sel = selection
        if (sel == null) {
            for (i in ed.cel.indices) if (ed.cel[i] != 0) ed.set(i % doc.width, i / doc.width, 0)
        } else {
            for (y in sel.bounds.top until sel.bounds.bottom) for (x in sel.bounds.left until sel.bounds.right) {
                if (sel.isSet(x, y)) ed.set(x, y, 0)
            }
        }
        flushEditorDirty(ed)
        commitEditor()
    }

    /** Remplit la sélection (ou tout le calque) de la couleur principale. */
    fun fillSelection() {
        commitFloating()
        if (!canPaint()) return
        val ed = openEditor() ?: return
        val sel = selection
        val b = sel?.bounds ?: PixelRect(0, 0, doc.width, doc.height)
        for (y in b.top until b.bottom) for (x in b.left until b.right) {
            if (sel == null || sel.isSet(x, y)) ed.set(x, y, primary)
        }
        flushEditorDirty(ed)
        commitEditor()
    }

    // ==== Validation d'un geste ================================================================

    private fun flushEditorDirty(ed: CelEditor) {
        ed.takeDirty(dirty)
        flushDirty()
    }

    private fun flushDirty() {
        if (dirty.isEmpty) return
        doc.touch(gLayerId, gFrameId)
        listener?.onPixelsChanged(dirty.copy())
        dirty.set(0, 0, 0, 0)
    }

    private fun commitEditor() {
        val ed = editor ?: return
        editor = null
        val delta = ed.build(gLayerId, gFrameId)
        if (delta != null) {
            history.push(CelEdit(listOf(delta)))
            listener?.onHistoryChanged()
        }
    }

    // ==== Historique ===========================================================================

    fun undo() {
        finishGesture()
        if (floating != null) { cancelFloating(); return }
        history.undo(doc) ?: return
        afterHistory()
    }

    fun redo() {
        finishGesture()
        if (floating != null) commitFloating()
        history.redo(doc) ?: return
        afterHistory()
    }

    /** Abandonne les modifications faites depuis le dernier enregistrement. Faux si l'historique ne remonte pas jusque-là. */
    fun revertToSaved(): Boolean {
        finishGesture()
        cancelFloating()
        val ok = history.seekSaved(doc)
        afterHistory()
        return ok
    }

    private fun afterHistory() {
        fixActive()
        if (selection != null && (selection!!.w != doc.width || selection!!.h != doc.height)) selection = null
        listener?.onHistoryChanged()
        listener?.onDocumentChanged()
        listener?.onActiveChanged()
    }

    // ==== Calques et images actifs ==================================================================

    private fun fixActive() {
        if (doc.layerById(activeLayerId) == null) activeLayerId = doc.layers.last().id
        if (doc.frameById(activeFrameId) == null) activeFrameId = doc.frames.first().id
    }

    fun selectLayer(index: Int) {
        finishGesture()
        commitFloating()
        val id = doc.layers.getOrNull(index)?.id ?: return
        if (id == activeLayerId) return
        activeLayerId = id
        listener?.onActiveChanged()
    }

    fun selectFrame(index: Int) {
        finishGesture()
        commitFloating()
        val id = doc.frames.getOrNull(index)?.id ?: return
        if (id == activeFrameId) return
        activeFrameId = id
        listener?.onActiveChanged()
        listener?.onDocumentChanged()
    }

    // ==== Structure ============================================================================

    private fun structural(edit: Edit?) {
        if (edit == null) return
        history.push(edit)
        fixActive()
        listener?.onHistoryChanged()
        listener?.onDocumentChanged()
        listener?.onActiveChanged()
    }

    fun addLayer(name: String) {
        commitFloating()
        val (layer, edit) = DocumentOps.addLayer(doc, layerIndex, name)
        activeLayerId = layer.id
        structural(edit)
    }

    fun duplicateLayer(name: String) {
        commitFloating()
        val (layer, edit) = DocumentOps.duplicateLayer(doc, layerIndex, name)
        activeLayerId = layer.id
        structural(edit)
    }

    fun deleteLayer() {
        commitFloating()
        val i = layerIndex
        val edit = DocumentOps.deleteLayer(doc, i) ?: return
        activeLayerId = doc.layers[(i - 1).coerceAtLeast(0).coerceAtMost(doc.layers.size - 1)].id
        structural(edit)
    }

    fun moveLayer(from: Int, to: Int) {
        commitFloating()
        structural(DocumentOps.moveLayer(doc, from, to))
    }

    fun mergeLayerDown() {
        commitFloating()
        val i = layerIndex
        val edit = DocumentOps.mergeDown(doc, i) ?: return
        activeLayerId = doc.layers[i - 1].id
        structural(edit)
    }

    fun editLayer(layerId: Int, change: (Layer) -> Unit) {
        commitFloating()
        structural(DocumentOps.editLayer(doc, layerId, change))
    }

    fun addFrame(copyCurrent: Boolean) {
        commitFloating()
        val (frame, edit) = DocumentOps.addFrame(doc, frameIndex, if (copyCurrent) frameIndex else null)
        activeFrameId = frame.id
        structural(edit)
    }

    fun deleteFrame() {
        commitFloating()
        val i = frameIndex
        val edit = DocumentOps.deleteFrame(doc, i) ?: return
        activeFrameId = doc.frames[(i - 1).coerceAtLeast(0).coerceAtMost(doc.frames.size - 1)].id
        structural(edit)
    }

    fun moveFrame(from: Int, to: Int) {
        commitFloating()
        structural(DocumentOps.moveFrame(doc, from, to))
    }

    fun setFrameDuration(frameId: Int, ms: Int) {
        structural(DocumentOps.setFrameDuration(doc, frameId, ms))
    }

    fun setAllFrameDurations(ms: Int) {
        for (f in doc.frames) structural(DocumentOps.setFrameDuration(doc, f.id, ms))
    }

    private fun canvasChanged(edit: Edit?) {
        if (edit == null) return
        selection = null
        listener?.onSelectionChanged()
        structural(edit)
    }

    fun resizeCanvas(w: Int, h: Int, anchorX: Int, anchorY: Int) {
        commitFloating()
        canvasChanged(DocumentOps.resizeCanvas(doc, w, h, anchorX, anchorY))
    }

    fun scaleCanvas(w: Int, h: Int) {
        commitFloating()
        canvasChanged(DocumentOps.scaleCanvas(doc, w, h))
    }

    fun flipCanvas(horizontal: Boolean) {
        commitFloating()
        canvasChanged(DocumentOps.flipCanvas(doc, horizontal))
    }

    fun rotateCanvas(clockwise: Boolean) {
        commitFloating()
        canvasChanged(DocumentOps.rotateCanvas(doc, clockwise))
    }

    /** À appeler quand tout travail en attente doit rejoindre le document (enregistrement, export). */
    fun settle() {
        finishGesture()
        commitFloating()
    }
}

private fun ByteArray.toIntArray(): IntArray = IntArray(size) { this[it].toInt() and 0xFF }
private fun IntArray.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }
