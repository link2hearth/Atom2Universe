package com.Atom2Universe.app.games.puzzles.untangle

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * « Untangle » de Simon Tatham : un graphe dont les arêtes se croisent ; déplacer les points
 * jusqu'à ce qu'aucune arête n'en croise une autre. Le graphe est planaire par construction.
 *
 * Les positions sont dans le carré unité ; [edges] liste les paires d'extrémités.
 */
class UntangleState(
    val xs: FloatArray, val ys: FloatArray, val edges: IntArray, val moves: Int,
    /** Les positions d'où le graphe a été tiré, sans croisement : la solution, pour l'astuce. */
    val goalX: FloatArray, val goalY: FloatArray,
) {
    val n: Int get() = xs.size

    fun moved(point: Int, x: Float, y: Float): UntangleState {
        val nx = xs.copyOf(); val ny = ys.copyOf()
        nx[point] = x.coerceIn(0f, 1f); ny[point] = y.coerceIn(0f, 1f)
        return UntangleState(nx, ny, edges, moves + 1, goalX, goalY)
    }

    /** Astuce : un indice n'aurait pas de sens ici, on montre la fin (tous les points en place). */
    fun hint(): UntangleState? =
        if (isSolved) null else UntangleState(goalX.copyOf(), goalY.copyOf(), edges, moves + 1, goalX, goalY)

    /** Les arêtes (indices dans [edges] / 2) qui en croisent au moins une autre. */
    fun crossingEdges(): Set<Int> {
        val m = edges.size / 2
        val out = HashSet<Int>()
        for (i in 0 until m) for (j in i + 1 until m) {
            if (cross(xs, ys, edges[2 * i], edges[2 * i + 1], edges[2 * j], edges[2 * j + 1])) {
                out.add(i); out.add(j)
            }
        }
        return out
    }

    val isSolved: Boolean get() = crossingEdges().isEmpty()

    fun encode(): String = buildString {
        append(moves).append(':')
        append(xs.indices.joinToString(";") { "${xs[it]},${ys[it]}" })
        append(':').append(edges.joinToString(","))
        append(':').append(goalX.indices.joinToString(";") { "${goalX[it]},${goalY[it]}" })
    }

    companion object {
        fun decode(text: String): UntangleState? {
            val parts = text.split(':')
            if (parts.size != 4) return null
            val pts = parts[1].split(';').map { p -> p.split(',').map { it.toFloat() } }
            val goal = parts[3].split(';').map { p -> p.split(',').map { it.toFloat() } }
            return UntangleState(pts.map { it[0] }.toFloatArray(), pts.map { it[1] }.toFloatArray(),
                parts[2].split(',').map { it.toInt() }.toIntArray(), parts[0].toInt(),
                goal.map { it[0] }.toFloatArray(), goal.map { it[1] }.toFloatArray())
        }

        /** Vrai si les segments a-b et c-d se coupent ailleurs qu'en une extrémité commune. */
        fun cross(xs: FloatArray, ys: FloatArray, a: Int, b: Int, c: Int, d: Int): Boolean {
            if (a == c || a == d || b == c || b == d) return false
            fun orient(p: Int, q: Int, r: Int): Float =
                (xs[q] - xs[p]) * (ys[r] - ys[p]) - (ys[q] - ys[p]) * (xs[r] - xs[p])
            val o1 = orient(a, b, c); val o2 = orient(a, b, d)
            val o3 = orient(c, d, a); val o4 = orient(c, d, b)
            return ((o1 > 0f && o2 < 0f) || (o1 < 0f && o2 > 0f)) &&
                ((o3 > 0f && o4 < 0f) || (o3 < 0f && o4 > 0f))
        }

        /**
         * Des points au hasard, reliés à leurs plus proches voisins tant que rien ne se croise :
         * un graphe planaire bien maillé. Puis on pose les points sur un cercle, dans le désordre.
         */
        fun generate(n: Int, random: Random): UntangleState {
            while (true) {
                val px = FloatArray(n) { random.nextFloat() }
                val py = FloatArray(n) { random.nextFloat() }
                val candidates = ArrayList<Pair<Int, Int>>()
                for (i in 0 until n) {
                    (0 until n).filter { it != i }
                        .sortedBy { (px[it] - px[i]) * (px[it] - px[i]) + (py[it] - py[i]) * (py[it] - py[i]) }
                        .take(5).forEach { j -> if (i < j) candidates.add(i to j) else candidates.add(j to i) }
                }
                val edges = ArrayList<Int>()
                val degree = IntArray(n)
                for ((a, b) in candidates.distinct().shuffled(random)) {
                    val ok = (0 until edges.size / 2).none { cross(px, py, a, b, edges[2 * it], edges[2 * it + 1]) }
                    if (ok && edges.size / 2 < n * 2 - 3) {
                        edges.add(a); edges.add(b); degree[a]++; degree[b]++
                    }
                }
                if (degree.any { it < 2 }) continue
                val order = (0 until n).shuffled(random)
                val xs = FloatArray(n); val ys = FloatArray(n)
                order.forEachIndexed { k, p ->
                    val angle = 2 * PI * k / n
                    xs[p] = (0.5 + 0.45 * cos(angle)).toFloat()
                    ys[p] = (0.5 + 0.45 * sin(angle)).toFloat()
                }
                // Le dessin d'origine, ramené dans le même cadre que le cercle (marges comprises).
                val gx = FloatArray(n) { 0.05f + 0.9f * px[it] }; val gy = FloatArray(n) { 0.05f + 0.9f * py[it] }
                val state = UntangleState(xs, ys, edges.toIntArray(), 0, gx, gy)
                if (!state.isSolved) return state
            }
        }
    }
}
