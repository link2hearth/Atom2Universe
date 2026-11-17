package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs

/**
 * Les arêtes d'une grille de w × h cases, numérotées une fois pour toutes : d'abord les arêtes
 * horizontales ((h + 1) lignes de w), puis les verticales (h lignes de w + 1).
 *
 * Elles servent aux puzzles où l'on trace sur le quadrillage (Loopy, Palisade, Galaxies) ; pour
 * ceux où l'on trace de centre en centre (Pearl, Tracks), on prend la grille « duale » : les
 * arêtes intérieures relient deux cases voisines.
 */
class EdgeGrid(val w: Int, val h: Int) {
    val horizontalCount = (h + 1) * w
    val count = horizontalCount + h * (w + 1)

    fun top(x: Int, y: Int) = y * w + x
    fun bottom(x: Int, y: Int) = (y + 1) * w + x
    fun left(x: Int, y: Int) = horizontalCount + y * (w + 1) + x
    fun right(x: Int, y: Int) = horizontalCount + y * (w + 1) + x + 1

    fun isHorizontal(e: Int) = e < horizontalCount

    /** Les quatre arêtes d'une case. */
    fun around(cell: Int): IntArray {
        val x = cell % w; val y = cell / w
        return intArrayOf(top(x, y), right(x, y), bottom(x, y), left(x, y))
    }

    /** Les deux sommets d'une arête, numérotés y × (w + 1) + x. */
    fun ends(e: Int): IntArray = intArrayOf(endA(e), endB(e))

    /** Le premier sommet d'une arête (haut ou gauche), sans tableau : pour le dessin. */
    fun endA(e: Int): Int = if (isHorizontal(e)) (e / w) * (w + 1) + e % w else e - horizontalCount

    /** Le second sommet d'une arête (droite ou bas). */
    fun endB(e: Int): Int = if (isHorizontal(e)) endA(e) + 1 else endA(e) + w + 1

    /** Les cases de part et d'autre d'une arête, sans tableau (-1 hors de la grille). */
    fun cellA(e: Int): Int = if (isHorizontal(e)) {
        val y = e / w
        if (y > 0) (y - 1) * w + e % w else -1
    } else {
        val k = e - horizontalCount
        val x = k % (w + 1)
        if (x > 0) (k / (w + 1)) * w + x - 1 else -1
    }

    fun cellB(e: Int): Int = if (isHorizontal(e)) {
        val y = e / w
        if (y < h) y * w + e % w else -1
    } else {
        val k = e - horizontalCount
        val x = k % (w + 1)
        if (x < w) (k / (w + 1)) * w + x else -1
    }

    /** Les arêtes qui touchent un sommet. */
    fun atVertex(v: Int): IntArray {
        val x = v % (w + 1); val y = v / (w + 1)
        val out = ArrayList<Int>(4)
        if (x > 0) out.add(y * w + x - 1)
        if (x < w) out.add(y * w + x)
        if (y > 0) out.add(horizontalCount + (y - 1) * (w + 1) + x)
        if (y < h) out.add(horizontalCount + y * (w + 1) + x)
        return out.toIntArray()
    }

    /** Les cases de part et d'autre d'une arête (-1 hors de la grille). */
    fun cells(e: Int): IntArray = if (isHorizontal(e)) {
        val y = e / w; val x = e % w
        intArrayOf(if (y > 0) (y - 1) * w + x else -1, if (y < h) y * w + x else -1)
    } else {
        val k = e - horizontalCount
        val y = k / (w + 1); val x = k % (w + 1)
        intArrayOf(if (x > 0) y * w + x - 1 else -1, if (x < w) y * w + x else -1)
    }

    /** Vrai si les arêtes « tracées » forment exactement une boucle fermée. */
    fun singleLoop(lines: BooleanArray): Boolean {
        val vertices = (w + 1) * (h + 1)
        var start = -1
        for (v in 0 until vertices) {
            val d = atVertex(v).count { lines[it] }
            if (d != 0 && d != 2) return false
            if (d == 2 && start < 0) start = v
        }
        if (start < 0) return false
        val seen = BooleanArray(count)
        var v = start
        var prev = -1
        var walked = 0
        while (true) {
            val e = atVertex(v).first { lines[it] && it != prev }
            if (seen[e]) break
            seen[e] = true; walked++
            val (a, b) = ends(e)
            v = if (a == v) b else a
            prev = e
            if (v == start) break
        }
        return walked == lines.count { it }
    }
}

/**
 * La vue des puzzles où l'on trace sur les arêtes : un appui trace ou efface le trait le plus
 * proche, un appui long y met une croix (« pas de trait ici »), un glissé trace d'un sommet à
 * l'autre sur tout son chemin. [dual] : tracer entre centres de cases plutôt que sur le
 * quadrillage.
 */
abstract class EdgeView<S : Any>(context: Context) : PuzzleView<S>(context) {
    protected val grid = GridGeometry()
    protected val r = RectF()
    protected open val dual = false
    private val dragged = LinkedHashSet<Int>()
    /** Les mêmes arêtes que [dragged], lues à chaque image sans emballer leur numéro. */
    private var draggedMask = BooleanArray(0)
    private var dragValue = 1
    /** Le segment rendu par [segment] : un seul tableau, relu aussitôt. */
    private val seg = FloatArray(4)
    /** Les arêtes traçables, vues une fois par état et non à chaque mouvement du doigt. */
    private val usableCache = PuzzleStateCache<S, BooleanArray> { s -> BooleanArray(edgeGrid(s).count) { usable(s, it) } }

    protected abstract fun edgeGrid(state: S): EdgeGrid
    /** 0 rien, 1 trait, 2 croix. */
    protected abstract fun edgeValue(state: S, e: Int): Int
    protected abstract fun setEdges(state: S, edges: Collection<Int>, value: Int): S?
    protected open fun margins(state: S): FloatArray = floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f)
    /** Une arête qu'on ne peut pas tracer (bord de la grille en mode dual…). */
    protected open fun usable(state: S, e: Int): Boolean = true

    protected fun layout(state: S, area: RectF) {
        val g = edgeGrid(state)
        val m = margins(state)
        grid.fit(area, g.w, g.h, m[0], m[1], m[2], m[3])
    }

    /**
     * Le segment d'écran d'une arête : sur le quadrillage, ou entre deux centres en dual. Le
     * tableau rendu est réutilisé à l'appel suivant : le lire tout de suite, sans le garder.
     */
    protected fun segment(g: EdgeGrid, e: Int): FloatArray {
        val cell = grid.cell
        val a = g.endA(e); val b = g.endB(e)
        val ax = grid.left + (a % (g.w + 1)) * cell; val ay = grid.top + (a / (g.w + 1)) * cell
        val bx = grid.left + (b % (g.w + 1)) * cell; val by = grid.top + (b / (g.w + 1)) * cell
        val out = seg
        if (!dual) {
            out[0] = ax; out[1] = ay; out[2] = bx; out[3] = by
            return out
        }
        // En dual, l'arête du quadrillage est « traversée » : le trait relie les deux centres.
        val mx = (ax + bx) / 2; val my = (ay + by) / 2
        if (g.isHorizontal(e)) {
            out[0] = mx; out[1] = my - cell / 2; out[2] = mx; out[3] = my + cell / 2
        } else {
            out[0] = mx - cell / 2; out[1] = my; out[2] = mx + cell / 2; out[3] = my
        }
        return out
    }

    protected fun nearestEdge(state: S, x: Float, y: Float): Int {
        val g = edgeGrid(state)
        val allowed = usableCache[state]
        var best = -1
        var bestD = grid.cell * 0.45f
        for (e in 0 until g.count) {
            if (!allowed[e]) continue
            val s = segment(g, e)
            val mx = (s[0] + s[2]) / 2; val my = (s[1] + s[3]) / 2
            val horizontal = abs(s[1] - s[3]) < 1f
            val along = if (horizontal) abs(x - mx) else abs(y - my)
            val across = if (horizontal) abs(y - my) else abs(x - mx)
            if (along > grid.cell * 0.5f) continue
            val d = across + along * 0.25f
            if (d < bestD) { bestD = d; best = e }
        }
        return best
    }

    /** Dessine traits et croix ; [lineColour] permet de colorer certains traits (erreurs). */
    protected fun drawEdges(canvas: Canvas, state: S, lineColour: (Int) -> Int) {
        val g = edgeGrid(state)
        val cell = grid.cell
        val mask = draggedMask
        for (e in 0 until g.count) {
            val inDrag = e < mask.size && mask[e]
            val v = if (inDrag) dragValue else edgeValue(state, e)
            if (v == 0) continue
            if (v == 1) {
                // La couleur d'abord : [lineColour] pourrait lui-même demander un segment.
                val colour = if (inDrag) palette.withAlpha(palette.accent, 0.7f) else lineColour(e)
                val s = segment(g, e)
                stroke.color = colour
                stroke.strokeWidth = cell * 0.11f
                canvas.drawLine(s[0], s[1], s[2], s[3], stroke)
            } else {
                val s = segment(g, e)
                val mx = (s[0] + s[2]) / 2; val my = (s[1] + s[3]) / 2
                val k = cell * 0.09f
                stroke.color = palette.tertiary
                stroke.strokeWidth = cell * 0.04f
                canvas.drawLine(mx - k, my - k, mx + k, my + k, stroke)
                canvas.drawLine(mx - k, my + k, mx + k, my - k, stroke)
            }
        }
    }

    private fun toggle(x: Float, y: Float, target: Int) {
        val s = state ?: return
        val e = nearestEdge(s, x, y)
        if (e < 0) return
        val v = edgeValue(s, e)
        setEdges(s, listOf(e), if (v == target) 0 else target)?.let { tick(); move(it) }
    }

    override fun onTap(x: Float, y: Float) = toggle(x, y, 1)
    override fun onLongPress(x: Float, y: Float) = toggle(x, y, 2)

    override fun wantsDrag(x: Float, y: Float) = state?.let { nearestEdge(it, x, y) >= 0 } ?: false

    private fun addDragged(e: Int): Boolean {
        if (!dragged.add(e)) return false
        if (e < draggedMask.size) draggedMask[e] = true
        return true
    }

    private fun clearDragged() {
        for (e in dragged) if (e < draggedMask.size) draggedMask[e] = false
        dragged.clear()
    }

    override fun onDragStart(x: Float, y: Float) {
        val s = state ?: return
        clearDragged()
        val count = edgeGrid(s).count
        if (draggedMask.size != count) draggedMask = BooleanArray(count)
        val e = nearestEdge(s, x, y)
        dragValue = if (e >= 0 && edgeValue(s, e) == 1) 0 else 1
        if (e >= 0) addDragged(e)
    }

    override fun onDragMove(x: Float, y: Float) {
        val s = state ?: return
        val e = nearestEdge(s, x, y)
        if (e >= 0 && addDragged(e)) invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        val next = if (s != null && dragged.isNotEmpty()) setEdges(s, dragged.toList(), dragValue) else null
        clearDragged()
        if (next != null) { tick(); move(next) } else invalidate()
    }

    override fun onDragCancel() { clearDragged(); invalidate() }
}
