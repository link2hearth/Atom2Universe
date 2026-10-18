package com.Atom2Universe.app.games.puzzles.rect

import com.Atom2Universe.app.games.kit.Hints
import kotlin.random.Random

/**
 * « Rectangles » de Simon Tatham (Shikaku) : découper la grille en rectangles qui contiennent
 * chacun un seul nombre, égal à leur surface.
 *
 * [numbers] : 0 = pas de nombre. [rects] : rectangles tracés par le joueur, en quadruplets
 * (x, y, largeur, hauteur).
 */
class RectState(
    val w: Int, val h: Int, val numbers: IntArray, val rects: List<IntArray>,
    /** Les rectangles de la solution, gardés pour les astuces. */
    val solution: List<IntArray>,
) {

    /** Le rectangle tracé qui couvre la case, ou -1. */
    fun rectAt(cell: Int): Int {
        val x = cell % w; val y = cell / w
        return rects.indexOfFirst { r -> x in r[0] until r[0] + r[2] && y in r[1] until r[1] + r[3] }
    }

    fun valid(r: IntArray): Boolean {
        var count = 0; var value = 0
        for (y in r[1] until r[1] + r[3]) for (x in r[0] until r[0] + r[2]) {
            val v = numbers[y * w + x]
            if (v > 0) { count++; value = v }
        }
        return count == 1 && value == r[2] * r[3]
    }

    val isSolved: Boolean
        get() = rects.sumOf { it[2] * it[3] } == w * h && rects.all { valid(it) } &&
            (0 until w * h).all { rectAt(it) >= 0 }

    /** Pose un rectangle : ceux qu'il recouvre en partie disparaissent. */
    fun draw(r: IntArray): RectState {
        val kept = rects.filter { o ->
            o[0] + o[2] <= r[0] || r[0] + r[2] <= o[0] || o[1] + o[3] <= r[1] || r[1] + r[3] <= o[1]
        }
        return RectState(w, h, numbers, kept + listOf(r), solution)
    }

    /**
     * Astuce : un rectangle de la solution est tracé — d'abord un de ceux qu'un rectangle faux
     * recouvre (le faux s'efface), sinon un qui manque encore.
     */
    fun hint(): RectState? {
        fun drawn(s: IntArray) = rects.any { it.contentEquals(s) }
        fun overlaps(a: IntArray, b: IntArray) =
            !(a[0] + a[2] <= b[0] || b[0] + b[2] <= a[0] || a[1] + a[3] <= b[1] || b[1] + b[3] <= a[1])
        val wrong = rects.filter { r -> solution.none { it.contentEquals(r) } }
        val k = Hints.pick(solution.size, { i -> !drawn(solution[i]) && wrong.any { overlaps(it, solution[i]) } },
            { i -> !drawn(solution[i]) }) ?: return null
        return draw(solution[k])
    }

    fun erase(index: Int) = RectState(w, h, numbers, rects.filterIndexed { i, _ -> i != index }, solution)

    fun encode() = "$w,$h:" + numbers.joinToString(",") + ":" + rects.joinToString(";") { it.joinToString(",") } + ":" +
        solution.joinToString(";") { it.joinToString(",") }

    companion object {
        fun decode(text: String): RectState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            fun rects(s: String) = if (s.isEmpty()) emptyList() else s.split(';').map { r -> r.split(',').map { it.toInt() }.toIntArray() }
            return RectState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), rects(p[2]), rects(p[3]))
        }

        /** Les rectangles possibles pour le nombre en [cell] : sa surface, aucun autre nombre. */
        fun candidates(w: Int, h: Int, numbers: IntArray, cell: Int): List<IntArray> {
            val area = numbers[cell]
            val cx = cell % w; val cy = cell / w
            val out = ArrayList<IntArray>()
            for (rw in 1..area) {
                if (area % rw != 0) continue
                val rh = area / rw
                if (rw > w || rh > h) continue
                for (x0 in maxOf(0, cx - rw + 1)..minOf(cx, w - rw)) for (y0 in maxOf(0, cy - rh + 1)..minOf(cy, h - rh)) {
                    var others = false
                    loop@ for (y in y0 until y0 + rh) for (x in x0 until x0 + rw) {
                        val i = y * w + x
                        if (i != cell && numbers[i] > 0) { others = true; break@loop }
                    }
                    if (!others) out.add(intArrayOf(x0, y0, rw, rh))
                }
            }
            return out
        }

        /** Nombre de découpages (plafonné à [limit]) : couverture exacte, nombre le plus contraint d'abord. */
        fun countSolutions(w: Int, h: Int, numbers: IntArray, limit: Int = 2): Int {
            val clues = numbers.indices.filter { numbers[it] > 0 }
            val cand = clues.associateWith { candidates(w, h, numbers, it) }
            val covered = BooleanArray(w * h)
            var found = 0
            var nodes = 0
            fun fits(r: IntArray): Boolean {
                for (y in r[1] until r[1] + r[3]) for (x in r[0] until r[0] + r[2]) if (covered[y * w + x]) return false
                return true
            }
            fun mark(r: IntArray, v: Boolean) {
                for (y in r[1] until r[1] + r[3]) for (x in r[0] until r[0] + r[2]) covered[y * w + x] = v
            }
            fun search(left: List<Int>) {
                if (found >= limit || ++nodes > 200_000) return
                if (left.isEmpty()) { if (covered.all { it }) found++; return }
                var best = -1; var bestList: List<IntArray> = emptyList()
                for (c in left) {
                    val l = cand[c]!!.filter { fits(it) }
                    if (l.isEmpty()) return
                    if (best < 0 || l.size < bestList.size) { best = c; bestList = l }
                }
                val rest = left - best
                for (r in bestList) {
                    mark(r, true); search(rest); mark(r, false)
                    if (found >= limit) return
                }
            }
            search(clues)
            return if (nodes > 200_000) limit else found
        }

        fun generate(w: Int, h: Int, maxArea: Int, random: Random): RectState {
            while (true) {
                val owner = IntArray(w * h) { -1 }
                val numbers = IntArray(w * h)
                var id = 0
                for (start in 0 until w * h) {
                    if (owner[start] >= 0) continue
                    val x0 = start % w; val y0 = start / w
                    // Les tailles possibles à partir de ce coin, puis une au hasard (pas trop de 1).
                    val options = ArrayList<Pair<Int, Int>>()
                    var maxW = 0
                    while (x0 + maxW < w && owner[y0 * w + x0 + maxW] < 0) maxW++
                    for (rw in 1..maxW) for (rh in 1..h - y0) {
                        if (rw * rh > maxArea) break
                        val free = (y0 until y0 + rh).all { y -> (x0 until x0 + rw).all { x -> owner[y * w + x] < 0 } }
                        if (!free) break
                        if (rw * rh >= 2 || random.nextInt(8) == 0) options.add(rw to rh)
                    }
                    val (rw, rh) = if (options.isEmpty()) 1 to 1 else options.random(random)
                    val cells = (y0 until y0 + rh).flatMap { y -> (x0 until x0 + rw).map { x -> y * w + x } }
                    cells.forEach { owner[it] = id }
                    numbers[cells.random(random)] = rw * rh
                    id++
                }
                if (countSolutions(w, h, numbers) == 1) {
                    val solution = (0 until id).map { k ->
                        val cells = owner.indices.filter { owner[it] == k }
                        val xs = cells.map { it % w }; val ys = cells.map { it / w }
                        intArrayOf(xs.min(), ys.min(), xs.max() - xs.min() + 1, ys.max() - ys.min() + 1)
                    }
                    return RectState(w, h, numbers, emptyList(), solution)
                }
            }
        }
    }
}
