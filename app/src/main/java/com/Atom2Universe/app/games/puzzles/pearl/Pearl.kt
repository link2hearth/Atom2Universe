package com.Atom2Universe.app.games.puzzles.pearl

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Pearl » de Simon Tatham (Masyu) : tracer une boucle fermée de centre en centre qui passe par
 * toutes les perles.
 * - perle noire : la boucle y tourne, et file tout droit sur la case suivante des deux côtés ;
 * - perle blanche : la boucle la traverse tout droit, et tourne sur au moins une de ses deux voisines.
 *
 * [pearls] : 0 rien, [BLACK], [WHITE]. [edges] : arêtes du quadrillage traversées par la boucle
 * (indexées comme [EdgeGrid]) : 0 rien, 1 trait, 2 croix.
 */
class PearlState(val w: Int, val h: Int, val pearls: IntArray, val edges: IntArray,
    /** Les traits de la solution (1), gardés pour les astuces ; vide si inconnue. */
    val solution: IntArray = IntArray(0),
) {
    val grid by lazy { EdgeGrid(w, h) }

    fun set(list: Collection<Int>, v: Int): PearlState? {
        val next = edges.copyOf()
        var changed = false
        for (e in list) if (next[e] != v) { next[e] = v; changed = true }
        return if (changed) PearlState(w, h, pearls, next, solution) else null
    }

    /** Astuce : un trait en trop barré, une croix fautive tracée, sinon un trait de la solution. */
    fun hint(): PearlState? {
        if (solution.size != edges.size) return null
        val (e, v) = Hints.edge(edges, solution) { grid.cells(it).all { c -> c >= 0 } } ?: return null
        val next = edges.copyOf(); next[e] = v
        return PearlState(w, h, pearls, next, solution)
    }

    /** Direction 0 haut, 1 droite, 2 bas, 3 gauche : la boucle quitte-t-elle la case par là ? */
    fun line(cell: Int, d: Int): Boolean = edges[grid.around(cell)[d]] == 1

    fun degree(cell: Int) = (0..3).count { line(cell, it) }

    private fun step(cell: Int, d: Int): Int {
        val x = cell % w; val y = cell / w
        return when (d) {
            0 -> if (y > 0) cell - w else -1
            1 -> if (x < w - 1) cell + 1 else -1
            2 -> if (y < h - 1) cell + w else -1
            else -> if (x > 0) cell - 1 else -1
        }
    }

    private fun straight(cell: Int) = (line(cell, 0) && line(cell, 2)) || (line(cell, 1) && line(cell, 3))

    fun pearlOk(cell: Int): Boolean {
        if (degree(cell) != 2) return false
        return when (pearls[cell]) {
            BLACK -> !straight(cell) && (0..3).all { d -> !line(cell, d) || step(cell, d).let { n -> n >= 0 && line(n, d) } }
            WHITE -> straight(cell) && (0..3).any { d -> line(cell, d) && step(cell, d).let { n -> n >= 0 && !straight(n) } }
            else -> true
        }
    }

    /** Les cases où plus de deux traits se rejoignent. */
    fun overloaded(): Set<Int> = (0 until w * h).filter { degree(it) > 2 }.toSet()

    val isSolved: Boolean
        get() {
            if ((0 until w * h).any { pearls[it] != 0 && !pearlOk(it) }) return false
            if ((0 until w * h).any { degree(it) != 0 && degree(it) != 2 }) return false
            val start = (0 until w * h).firstOrNull { degree(it) == 2 } ?: return false
            // Une seule boucle : en la suivant depuis une case, on revient après tous les traits.
            var prev = -1; var c = start; var walked = 0
            do {
                val d = (0..3).first { line(c, it) && step(c, it) != prev }
                prev = c; c = step(c, d); walked++
            } while (c != start && walked <= w * h)
            return walked == (0 until w * h).sumOf { degree(it) } / 2
        }

    fun encode() = "$w,$h:" + pearls.joinToString("") + ":" + edges.joinToString("") + ":" + solution.joinToString("")

    companion object {
        const val BLACK = 1
        const val WHITE = 2

        fun decode(text: String): PearlState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return PearlState(w, h, p[1].map { it - '0' }.toIntArray(), p[2].map { it - '0' }.toIntArray(), p[3].map { it - '0' }.toIntArray())
        }

        /**
         * La boucle est le bord d'une région de « carrés » posés entre quatre centres de cases (une
         * grille de (w − 1) × (h − 1)). Un trait quitte le centre (x, y) vers le haut quand les deux
         * carrés de part et d'autre de ce trait diffèrent. -1 = inconnu, 0 dehors, 1 dedans.
         */
        private class Squares(val w: Int, val h: Int) {
            val sw = w - 1; val sh = h - 1
            fun sq(g: IntArray, x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= sw || y >= sh) 0 else g[y * sw + x]
            /** Trait depuis le centre (x, y) dans la direction d : 1 oui, 0 non, -1 inconnu. */
            fun edge(g: IntArray, x: Int, y: Int, d: Int): Int {
                if (x !in 0 until w || y !in 0 until h) return 0
                val (a, b) = when (d) {
                    0 -> sq(g, x - 1, y - 1) to sq(g, x, y - 1)
                    1 -> sq(g, x, y - 1) to sq(g, x, y)
                    2 -> sq(g, x - 1, y) to sq(g, x, y)
                    else -> sq(g, x - 1, y - 1) to sq(g, x - 1, y)
                }
                return if (a < 0 || b < 0) -1 else if (a != b) 1 else 0
            }
        }

        /** Les traits de la boucle (grille duale : de centre en centre) qui entoure les carrés g. */
        private fun loopEdges(s: Squares, g: IntArray): IntArray {
            val grid = EdgeGrid(s.w, s.h)
            return IntArray(grid.count) { e ->
                val (a, b) = grid.cells(e)
                when {
                    a < 0 || b < 0 -> 0
                    grid.isHorizontal(e) -> s.edge(g, a % s.w, a / s.w, 2)
                    else -> s.edge(g, a % s.w, a / s.w, 1)
                }
            }
        }

        private val DX = intArrayOf(0, 1, 0, -1)
        private val DY = intArrayOf(-1, 0, 1, 0)

        /** Vrai si la perle en (x, y) peut encore être respectée. */
        private fun pearlPossible(s: Squares, g: IntArray, pearls: IntArray, x: Int, y: Int): Boolean {
            val kind = pearls[y * s.w + x]
            if (kind == 0) return true
            val e = IntArray(4) { s.edge(g, x, y, it) }
            val on = e.count { it == 1 }; val open = e.count { it < 0 }
            if (on > 2 || on + open < 2) return false
            if (kind == BLACK) {
                if ((e[0] == 1 && e[2] == 1) || (e[1] == 1 && e[3] == 1)) return false
                for (d in 0..3) if (e[d] == 1 && s.edge(g, x + DX[d], y + DY[d], d) == 0) return false
                // Chaque côté doit pouvoir filer deux cases.
                for (d in 0..3) if (e[d] == 1 && (x + 2 * DX[d] !in 0 until s.w || y + 2 * DY[d] !in 0 until s.h)) return false
            } else {
                if ((e[0] == 1 || e[2] == 1) && (e[1] == 1 || e[3] == 1)) return false
                for (axis in 0..1) {
                    val d1 = axis; val d2 = axis + 2
                    if (e[d1] == 1 && e[d2] == 1) {
                        // Au moins une des deux voisines tourne.
                        val ahead = s.edge(g, x + DX[d1], y + DY[d1], d1)
                        val behind = s.edge(g, x + DX[d2], y + DY[d2], d2)
                        if (ahead == 1 && behind == 1) return false
                    }
                }
            }
            return true
        }

        fun ok(w: Int, h: Int, pearls: IntArray, g: IntArray, c: Int): Boolean {
            val s = Squares(w, h)
            val sw = s.sw; val sh = s.sh
            val cx = c % sw; val cy = c / sw
            // Damier interdit.
            for (y0 in maxOf(0, cy - 1)..minOf(sh - 2, cy)) for (x0 in maxOf(0, cx - 1)..minOf(sw - 2, cx)) {
                val a = g[y0 * sw + x0]; val b = g[y0 * sw + x0 + 1]; val d = g[(y0 + 1) * sw + x0]; val e = g[(y0 + 1) * sw + x0 + 1]
                if (a >= 0 && b >= 0 && d >= 0 && e >= 0 && a == e && b == d && a != b) return false
            }
            for (y in maxOf(0, cy - 1)..minOf(h - 1, cy + 2)) for (x in maxOf(0, cx - 1)..minOf(w - 1, cx + 2)) {
                if (!pearlPossible(s, g, pearls, x, y)) return false
            }
            fun around(i: Int) = intArrayOf(if (i >= sw) i - sw else -1, if (i % sw < sw - 1) i + 1 else -1,
                if (i < sw * sh - sw) i + sw else -1, if (i % sw > 0) i - 1 else -1)
            val inside = g.indexOfFirst { it == 1 }
            if (inside >= 0) {
                val seen = BooleanArray(sw * sh)
                val stack = ArrayDeque<Int>()
                stack.add(inside); seen[inside] = true
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    for (n in around(i)) if (n >= 0 && !seen[n] && g[n] != 0) { seen[n] = true; stack.add(n) }
                }
                if (g.indices.any { g[it] == 1 && !seen[it] }) return false
            }
            val seen = BooleanArray(sw * sh)
            val stack = ArrayDeque<Int>()
            for (i in 0 until sw * sh) {
                val x = i % sw; val y = i / sw
                if ((x == 0 || y == 0 || x == sw - 1 || y == sh - 1) && g[i] != 1) { seen[i] = true; stack.add(i) }
            }
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                for (n in around(i)) if (n >= 0 && !seen[n] && g[n] != 1) { seen[n] = true; stack.add(n) }
            }
            return g.indices.none { g[it] == 0 && !seen[it] }
        }

        fun generate(w: Int, h: Int, random: Random): PearlState {
            val sw = w - 1; val sh = h - 1
            val none = IntArray(w * h)
            var attempt = 0
            while (true) {
                attempt++
                val g = IntArray(sw * sh)
                g[random.nextInt(sw * sh)] = 1
                val target = sw * sh * 50 / 100
                var tries = 0
                while (g.count { it == 1 } < target && tries++ < sw * sh * 20) {
                    val cand = g.indices.filter { i ->
                        g[i] == 0 && listOf(i - sw, i + sw, if (i % sw > 0) i - 1 else -1, if (i % sw < sw - 1) i + 1 else -1)
                            .any { it in g.indices && g[it] == 1 }
                    }
                    if (cand.isEmpty()) break
                    val c = cand.random(random)
                    g[c] = 1
                    if (!ok(w, h, none, g, c)) g[c] = 0
                }
                // Toutes les perles que la boucle autorise.
                val s = Squares(w, h)
                val pearls = IntArray(w * h)
                for (y in 0 until h) for (x in 0 until w) {
                    val e = IntArray(4) { s.edge(g, x, y, it) }
                    if (e.count { it == 1 } != 2) continue
                    val straight = (e[0] == 1 && e[2] == 1) || (e[1] == 1 && e[3] == 1)
                    if (!straight) {
                        if ((0..3).all { d -> e[d] == 0 || s.edge(g, x + DX[d], y + DY[d], d) == 1 }) pearls[y * w + x] = BLACK
                    } else {
                        val axis = if (e[0] == 1) 0 else 1
                        val turnAhead = s.edge(g, x + DX[axis], y + DY[axis], axis) == 0
                        val turnBehind = s.edge(g, x + DX[axis + 2], y + DY[axis + 2], axis + 2) == 0
                        if (turnAhead || turnBehind) pearls[y * w + x] = WHITE
                    }
                }
                fun unique(budget: Int) = GridSearch.count(sw * sh, 2, IntArray(sw * sh) { -1 }, budget = budget,
                    complete = { gg -> gg.any { it == 1 } }) { gg, c -> ok(w, h, pearls, gg, c) } == 1
                // Après bien des essais, on garde la grille avec toutes ses perles : la victoire se
                // vérifie par les règles, une seconde solution serait acceptée elle aussi.
                if (!unique(40) && attempt < 25) continue
                if (attempt >= 25) return PearlState(w, h, pearls, IntArray(EdgeGrid(w, h).count), loopEdges(s, g))
                for (i in (0 until w * h).shuffled(random)) {
                    if (pearls[i] == 0) continue
                    val keep = pearls[i]; pearls[i] = 0
                    if (!unique(1)) pearls[i] = keep
                }
                return PearlState(w, h, pearls, IntArray(EdgeGrid(w, h).count), loopEdges(s, g))
            }
        }
    }
}
