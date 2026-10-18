package com.Atom2Universe.app.zoomcanvas.core

import kotlin.math.max
import kotlin.math.min

/**
 * Un élément d'une couche tel que l'index et le rendu le voient : l'objet, son rang de dessin [z]
 * et sa boîte englobante en unités de la couche, épaisseur du trait comprise.
 *
 * Immuable : changer un objet, le monter ou le descendre dans la pile, c'est fabriquer une autre
 * entrée. Un fil de fond qui lit un lot d'entrées ne les voit donc jamais changer sous ses pieds ;
 * seul [dead] bascule (une fois, de faux à vrai) quand l'entrée est retirée de la couche.
 */
class Entry(val item: LayerItem, val z: Long) {
    val id: Long get() = item.id
    val isEraser: Boolean = item is Stroke && item.isEraser

    val x0: Double
    val y0: Double
    val x1: Double
    val y1: Double

    @Volatile
    var dead = false

    /** Posée dans l'arbre de l'index (sinon dans sa liste d'attente). */
    internal var inTree = false

    init {
        var a: Double; var b: Double; var c: Double; var d: Double
        when (item) {
            is Stroke -> {
                val h = item.width / 2
                a = item.x + item.minX - h; b = item.y + item.minY - h
                c = item.x + item.maxX + h; d = item.y + item.maxY + h
            }
            is StrokeBox -> {
                val s = item.stroke
                val h = s.width / 2
                a = s.x + s.minX - h; b = s.y + s.minY - h
                c = s.x + s.maxX + h; d = s.y + s.maxY + h
            }
            is ShapeItem -> {
                val r = ShapeGeometry.bounds(item)
                a = r[0]; b = r[1]; c = r[2]; d = r[3]
            }
            is BoxItem -> {
                a = item.x - item.w / 2; b = item.y - item.h / 2
                c = item.x + item.w / 2; d = item.y + item.h / 2
            }
        }
        // Une boîte non finie ne doit pas empoisonner l'arbre : on la range à l'origine.
        if (!(a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite())) { a = 0.0; b = 0.0; c = 0.0; d = 0.0 }
        x0 = a; y0 = b; x1 = c; y1 = d
    }

    fun overlaps(qx0: Double, qy0: Double, qx1: Double, qy1: Double): Boolean =
        x1 >= qx0 && x0 <= qx1 && y1 >= qy0 && y0 <= qy1
}

/**
 * Un arbre de boîtes englobantes, construit d'un coup et jamais modifié : les entrées sont rangées
 * le long d'une courbe de Hilbert (celles qui sont proches dans le plan se retrouvent côte à côte),
 * puis regroupées par [FANOUT] à chaque niveau. Chercher ce qui touche un rectangle ne visite que
 * les branches qui le touchent : le coût suit ce qu'on voit, pas le nombre de traits du dessin.
 *
 * Comme il est immuable, un fil de fond peut l'interroger pendant que l'écran continue de poser ou
 * de retirer des traits : ce qui change ne va pas dans l'arbre, mais dans la liste d'attente de
 * [LayerIndex].
 */
internal class PackedTree private constructor(
    private val items: Array<Entry>,
    /** Pour chaque niveau (0 = feuilles) : les boîtes (x0, y0, x1, y1) de ses nœuds. */
    private val boxes: Array<DoubleArray>,
    /** Pour chaque niveau : le nombre d'entrées sous chaque nœud. */
    private val counts: Array<IntArray>,
) {
    val size: Int get() = items.size

    /** Boîte de tout l'arbre, ou null s'il est vide. */
    fun bounds(out: DoubleArray): Boolean {
        if (boxes.isEmpty()) return false
        val top = boxes[boxes.size - 1]
        out[0] = top[0]; out[1] = top[1]; out[2] = top[2]; out[3] = top[3]
        return true
    }

    /** Ajoute à [out] les entrées vivantes dont la boîte touche le rectangle. */
    fun query(x0: Double, y0: Double, x1: Double, y1: Double, out: MutableList<Entry>) {
        if (boxes.isEmpty()) return
        visit(boxes.size - 1, 0, x0, y0, x1, y1, out)
    }

    private fun visit(level: Int, node: Int, x0: Double, y0: Double, x1: Double, y1: Double, out: MutableList<Entry>) {
        val b = boxes[level]
        val o = 4 * node
        if (b[o + 2] < x0 || b[o] > x1 || b[o + 3] < y0 || b[o + 1] > y1) return
        val first = node * FANOUT
        if (level == 0) {
            val end = min(items.size, first + FANOUT)
            for (i in first until end) {
                val e = items[i]
                if (!e.dead && e.overlaps(x0, y0, x1, y1)) out.add(e)
            }
        } else {
            val end = min(boxes[level - 1].size / 4, first + FANOUT)
            for (c in first until end) visit(level - 1, c, x0, y0, x1, y1, out)
        }
    }

    /**
     * Combien d'entrées touchent le rectangle (les entrées retirées depuis la construction sont
     * comptées : c'est une borne haute). Une branche entièrement dedans se compte sans la visiter.
     */
    fun count(x0: Double, y0: Double, x1: Double, y1: Double): Int {
        if (boxes.isEmpty()) return 0
        return countNode(boxes.size - 1, 0, x0, y0, x1, y1)
    }

    private fun countNode(level: Int, node: Int, x0: Double, y0: Double, x1: Double, y1: Double): Int {
        val b = boxes[level]
        val o = 4 * node
        if (b[o + 2] < x0 || b[o] > x1 || b[o + 3] < y0 || b[o + 1] > y1) return 0
        if (b[o] >= x0 && b[o + 2] <= x1 && b[o + 1] >= y0 && b[o + 3] <= y1) return counts[level][node]
        val first = node * FANOUT
        var n = 0
        if (level == 0) {
            val end = min(items.size, first + FANOUT)
            for (i in first until end) if (items[i].overlaps(x0, y0, x1, y1)) n++
        } else {
            val end = min(boxes[level - 1].size / 4, first + FANOUT)
            for (c in first until end) n += countNode(level - 1, c, x0, y0, x1, y1)
        }
        return n
    }

    companion object {
        const val FANOUT = 16
        val EMPTY = PackedTree(emptyArray(), emptyArray(), emptyArray())

        fun build(entries: List<Entry>): PackedTree {
            val n = entries.size
            if (n == 0) return EMPTY
            // Étendue des centres, pour placer chaque entrée sur la grille de la courbe de Hilbert.
            var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
            for (e in entries) {
                val cx = (e.x0 + e.x1) / 2; val cy = (e.y0 + e.y1) / 2
                if (cx < minX) minX = cx; if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy; if (cy > maxY) maxY = cy
            }
            val spanX = max(maxX - minX, 1e-12)
            val spanY = max(maxY - minY, 1e-12)
            val keys = LongArray(n)
            for (i in 0 until n) {
                val e = entries[i]
                val gx = (((e.x0 + e.x1) / 2 - minX) / spanX * GRID_MAX).toInt().coerceIn(0, GRID_MAX)
                val gy = (((e.y0 + e.y1) / 2 - minY) / spanY * GRID_MAX).toInt().coerceIn(0, GRID_MAX)
                keys[i] = (hilbert(gx, gy) shl 32) or i.toLong()
            }
            keys.sort()
            val sorted = Array(n) { entries[(keys[it] and 0xFFFFFFFFL).toInt()] }
            for (e in sorted) e.inTree = true

            val levelBoxes = ArrayList<DoubleArray>()
            val levelCounts = ArrayList<IntArray>()
            // Feuilles : FANOUT entrées par nœud.
            var nodes = (n + FANOUT - 1) / FANOUT
            var box = DoubleArray(4 * nodes)
            var cnt = IntArray(nodes)
            for (k in 0 until nodes) {
                var a = Double.POSITIVE_INFINITY; var b = Double.POSITIVE_INFINITY
                var c = Double.NEGATIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
                val first = k * FANOUT
                val end = min(n, first + FANOUT)
                for (i in first until end) {
                    val e = sorted[i]
                    if (e.x0 < a) a = e.x0; if (e.y0 < b) b = e.y0
                    if (e.x1 > c) c = e.x1; if (e.y1 > d) d = e.y1
                }
                box[4 * k] = a; box[4 * k + 1] = b; box[4 * k + 2] = c; box[4 * k + 3] = d
                cnt[k] = end - first
            }
            levelBoxes.add(box); levelCounts.add(cnt)
            // Niveaux du dessus : FANOUT nœuds du niveau d'en dessous par nœud, jusqu'à la racine.
            while (nodes > 1) {
                val below = box
                val belowCnt = cnt
                val belowNodes = nodes
                nodes = (belowNodes + FANOUT - 1) / FANOUT
                box = DoubleArray(4 * nodes)
                cnt = IntArray(nodes)
                for (k in 0 until nodes) {
                    var a = Double.POSITIVE_INFINITY; var b = Double.POSITIVE_INFINITY
                    var c = Double.NEGATIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
                    var total = 0
                    val first = k * FANOUT
                    val end = min(belowNodes, first + FANOUT)
                    for (i in first until end) {
                        if (below[4 * i] < a) a = below[4 * i]; if (below[4 * i + 1] < b) b = below[4 * i + 1]
                        if (below[4 * i + 2] > c) c = below[4 * i + 2]; if (below[4 * i + 3] > d) d = below[4 * i + 3]
                        total += belowCnt[i]
                    }
                    box[4 * k] = a; box[4 * k + 1] = b; box[4 * k + 2] = c; box[4 * k + 3] = d
                    cnt[k] = total
                }
                levelBoxes.add(box); levelCounts.add(cnt)
            }
            return PackedTree(sorted, levelBoxes.toTypedArray(), levelCounts.toTypedArray())
        }

        /** Côté de la grille de Hilbert : 2^15 cases, soit un indice de 30 bits. */
        private const val GRID_BITS = 15
        private const val GRID_MAX = (1 shl GRID_BITS) - 1

        /** Rang de la case ([x], [y]) le long de la courbe de Hilbert. */
        private fun hilbert(x0: Int, y0: Int): Long {
            var x = x0
            var y = y0
            var d = 0L
            var s = 1 shl (GRID_BITS - 1)
            while (s > 0) {
                val rx = if (x and s != 0) 1 else 0
                val ry = if (y and s != 0) 1 else 0
                d += s.toLong() * s.toLong() * ((3 * rx) xor ry)
                if (ry == 0) {
                    if (rx == 1) { x = GRID_MAX - x; y = GRID_MAX - y }
                    val t = x; x = y; y = t
                }
                s = s shr 1
            }
            return d
        }
    }
}

/**
 * L'index spatial d'une couche : un [PackedTree] immuable pour le gros des entrées, et une petite
 * liste d'attente pour ce qui est arrivé depuis sa construction. Poser un trait n'ajoute qu'à la
 * liste ; retirer ou remplacer une entrée la marque morte. L'arbre est reconstruit seulement quand
 * la liste d'attente ou le nombre de morts devient une part notable de l'ensemble, ce qui répartit
 * son coût sur de nombreux traits.
 */
internal class LayerIndex {
    private var tree = PackedTree.EMPTY
    private val pending = ArrayList<Entry>()
    private var deadInTree = 0

    /** Boîte de ce qu'on voit (coups de gomme exclus), bornée par le haut : elle ne rétrécit qu'à la reconstruction. */
    private var vx0 = Double.POSITIVE_INFINITY
    private var vy0 = Double.POSITIVE_INFINITY
    private var vx1 = Double.NEGATIVE_INFINITY
    private var vy1 = Double.NEGATIVE_INFINITY

    fun add(e: Entry) {
        pending.add(e)
        e.inTree = false
        if (!e.isEraser) grow(e)
    }

    fun remove(e: Entry) {
        if (e.inTree) deadInTree++ else removePending(e)
    }

    private fun removePending(e: Entry) {
        // Le plus souvent la dernière posée (un objet qu'on déplace d'un doigt) : on cherche par la fin.
        for (i in pending.indices.reversed()) if (pending[i] === e) { pending.removeAt(i); return }
    }

    private fun grow(e: Entry) {
        if (e.x0 < vx0) vx0 = e.x0
        if (e.y0 < vy0) vy0 = e.y0
        if (e.x1 > vx1) vx1 = e.x1
        if (e.y1 > vy1) vy1 = e.y1
    }

    /** Reconstruit l'arbre si la liste d'attente ou les morts ont trop grossi. [all] : les entrées vivantes. */
    fun ensure(all: List<Entry>) {
        val live = all.size
        if (pending.size > max(MIN_PENDING, live / 4) || deadInTree > max(MIN_PENDING, live / 4)) rebuild(all)
    }

    /** Oublie tout (la couche est déchargée de la mémoire). */
    fun clear() {
        tree = PackedTree.EMPTY
        pending.clear()
        deadInTree = 0
        vx0 = Double.POSITIVE_INFINITY; vy0 = Double.POSITIVE_INFINITY
        vx1 = Double.NEGATIVE_INFINITY; vy1 = Double.NEGATIVE_INFINITY
    }

    fun rebuild(all: List<Entry>) {
        tree = PackedTree.build(all)
        pending.clear()
        deadInTree = 0
        vx0 = Double.POSITIVE_INFINITY; vy0 = Double.POSITIVE_INFINITY
        vx1 = Double.NEGATIVE_INFINITY; vy1 = Double.NEGATIVE_INFINITY
        for (e in all) if (!e.isEraser) grow(e)
    }

    /** Les entrées dont la boîte touche le rectangle, dans un ordre quelconque. */
    fun query(x0: Double, y0: Double, x1: Double, y1: Double, out: MutableList<Entry>) {
        tree.query(x0, y0, x1, y1, out)
        for (i in 0 until pending.size) {
            val e = pending[i]
            if (e.overlaps(x0, y0, x1, y1)) out.add(e)
        }
    }

    /** Borne haute du nombre d'entrées qui touchent le rectangle. */
    fun count(x0: Double, y0: Double, x1: Double, y1: Double): Int {
        var n = tree.count(x0, y0, x1, y1)
        for (i in 0 until pending.size) if (pending[i].overlaps(x0, y0, x1, y1)) n++
        return n
    }

    /** L'arbre et la liste d'attente tels qu'ils sont, pour un fil de fond (voir [Layer.snapshot]). */
    fun snapshot(version: Long): Layer.Snapshot = Layer.Snapshot(tree, pending.toTypedArray(), version)

    /** Boîte (x0, y0, x1, y1) de ce qu'on voit, ou null s'il n'y a rien. Borne haute. */
    fun visibleBounds(): DoubleArray? =
        if (vx1 < vx0) null else doubleArrayOf(vx0, vy0, vx1, vy1)

    companion object {
        /** En dessous, la liste d'attente se parcourt si vite qu'il ne vaut pas la peine de reconstruire. */
        const val MIN_PENDING = 1024
    }
}
