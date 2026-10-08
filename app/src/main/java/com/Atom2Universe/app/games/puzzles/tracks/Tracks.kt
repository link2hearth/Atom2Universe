package com.Atom2Universe.app.games.puzzles.tracks

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Tracks » de Simon Tatham : poser une voie ferrée continue de l'entrée, sur le bord gauche,
 * à la sortie, sur le bord du bas. Elle ne se croise pas et ne fait pas de boucle ; les nombres
 * en marge disent combien de cases de chaque ligne et colonne elle traverse. Quelques morceaux
 * de voie sont déjà posés.
 *
 * Les arêtes sont celles du quadrillage ([EdgeGrid]) : une arête « tracée » relie les deux cases
 * qu'elle sépare, ou une case au dehors pour l'entrée et la sortie. [fixed] : arêtes données.
 */
class TracksState(
    val w: Int,
    val h: Int,
    val entry: Int,
    val exit: Int,
    val rowCounts: IntArray,
    val colCounts: IntArray,
    val fixed: BooleanArray,
    val edges: IntArray,
    /** Les traits de la solution (1), gardés pour les astuces ; vide si inconnue. */
    val solution: IntArray = IntArray(0),
) {
    val grid by lazy { EdgeGrid(w, h) }

    fun set(list: Collection<Int>, v: Int): TracksState? {
        val next = edges.copyOf()
        var changed = false
        for (e in list) if (!fixed[e] && next[e] != v) { next[e] = v; changed = true }
        return if (changed) TracksState(w, h, entry, exit, rowCounts, colCounts, fixed, next, solution) else null
    }

    /** Astuce : un trait en trop barré, une croix fautive tracée, sinon un trait de la solution. */
    fun hint(): TracksState? {
        if (solution.size != edges.size) return null
        val (e, v) = Hints.edge(edges, solution) { !fixed[it] } ?: return null
        val next = edges.copyOf(); next[e] = v
        return TracksState(w, h, entry, exit, rowCounts, colCounts, fixed, next, solution)
    }

    fun degree(cell: Int) = grid.around(cell).count { edges[it] == 1 }
    fun rowUsed(r: Int) = (0 until w).count { degree(r * w + it) > 0 }
    fun colUsed(c: Int) = (0 until h).count { degree(it * w + c) > 0 }

    val isSolved: Boolean get() = solvedLines(w, h, entry, exit, rowCounts, colCounts, IntArray(grid.count) { if (edges[it] == 1) 1 else 0 })

    fun encode() = "$w,$h,$entry,$exit:" + rowCounts.joinToString(",") + ":" + colCounts.joinToString(",") + ":" +
        fixed.joinToString("") { if (it) "1" else "0" } + ":" + edges.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): TracksState {
            val p = text.split(':')
            val (w, h, entry, exit) = p[0].split(',').map { it.toInt() }
            return TracksState(w, h, entry, exit, p[1].split(',').map { it.toInt() }.toIntArray(),
                p[2].split(',').map { it.toInt() }.toIntArray(), BooleanArray(p[3].length) { p[3][it] == '1' },
                p[4].map { it - '0' }.toIntArray(), p[5].map { it - '0' }.toIntArray())
        }

        /** Les arêtes de bord autorisées : seulement l'entrée (gauche, ligne [entry]) et la sortie (bas, colonne [exit]). */
        fun borderAllowed(g: EdgeGrid, entry: Int, exit: Int, e: Int): Boolean {
            val (a, b) = g.cells(e)
            if (a >= 0 && b >= 0) return true
            return e == g.left(0, entry) || e == g.bottom(exit, g.h - 1)
        }

        /** g : 1 trait, 0 rien (complet). Une voie unique de l'entrée à la sortie, comptes justes. */
        fun solvedLines(w: Int, h: Int, entry: Int, exit: Int, rows: IntArray, cols: IntArray, g: IntArray): Boolean {
            val grid = EdgeGrid(w, h)
            if (g[grid.left(0, entry)] != 1 || g[grid.bottom(exit, h - 1)] != 1) return false
            for (e in 0 until grid.count) if (g[e] == 1 && !borderAllowed(grid, entry, exit, e)) return false
            val deg = IntArray(w * h) { c -> grid.around(c).count { g[it] == 1 } }
            if (deg.any { it != 0 && it != 2 }) return false
            for (r in 0 until h) if ((0 until w).count { deg[r * w + it] > 0 } != rows[r]) return false
            for (c in 0 until w) if ((0 until h).count { deg[it * w + c] > 0 } != cols[c]) return false
            // On suit la voie depuis l'entrée : elle doit passer par toutes les cases occupées.
            var cell = entry * w
            var from = grid.left(0, entry)
            var visited = 0
            while (true) {
                visited++
                val next = grid.around(cell).firstOrNull { g[it] == 1 && it != from } ?: return false
                if (next == grid.bottom(exit, h - 1) && cell == (h - 1) * w + exit) break
                val (a, b) = grid.cells(next)
                val other = if (a == cell) b else a
                if (other < 0 || visited > w * h) return false
                from = next; cell = other
            }
            return visited == deg.count { it > 0 }
        }

        fun ok(w: Int, h: Int, entry: Int, exit: Int, rows: IntArray, cols: IntArray, grid: EdgeGrid, g: IntArray, e: Int): Boolean {
            if (g[e] == 1 && !borderAllowed(grid, entry, exit, e)) return false
            for (c in grid.cells(e)) {
                if (c < 0) continue
                val around = grid.around(c)
                val on = around.count { g[it] == 1 }
                val open = around.count { g[it] < 0 }
                if (on > 2 || (on == 1 && open == 0)) return false
                // Comptes de ligne et de colonne.
                val r = c / w; val col = c % w
                var used = 0; var maybe = 0
                for (x in 0 until w) {
                    val a = grid.around(r * w + x)
                    if (a.any { g[it] == 1 }) used++ else if (a.any { g[it] < 0 }) maybe++
                }
                if (used > rows[r] || used + maybe < rows[r]) return false
                used = 0; maybe = 0
                for (y in 0 until h) {
                    val a = grid.around(y * w + col)
                    if (a.any { g[it] == 1 }) used++ else if (a.any { g[it] < 0 }) maybe++
                }
                if (used > cols[col] || used + maybe < cols[col]) return false
            }
            return true
        }

        fun generate(w: Int, h: Int, random: Random): TracksState {
            val grid = EdgeGrid(w, h)
            while (true) {
                val entry = random.nextInt(h)
                val exit = random.nextInt(w)
                // Une marche au hasard sans se recouper, de l'entrée à la sortie.
                val path = ArrayList<Int>()
                val used = BooleanArray(w * h)
                val goal = (h - 1) * w + exit
                val targetLen = w * h * 55 / 100
                var nodes = 0
                fun walk(c: Int): Boolean {
                    if (++nodes > 50_000) return false
                    path.add(c); used[c] = true
                    if (c == goal && path.size >= targetLen) return true
                    if (c != goal) {
                        val x = c % w; val y = c / w
                        val next = listOfNotNull(if (y > 0) c - w else null, if (x < w - 1) c + 1 else null,
                            if (y < h - 1) c + w else null, if (x > 0) c - 1 else null).filter { !used[it] }.shuffled(random)
                        for (n in next) if (walk(n)) return true
                    }
                    path.removeAt(path.size - 1); used[c] = false
                    return false
                }
                if (!walk(entry * w)) continue
                val lines = IntArray(grid.count)
                lines[grid.left(0, entry)] = 1
                lines[grid.bottom(exit, h - 1)] = 1
                for (k in 0 until path.size - 1) {
                    val a = path[k]; val b = path[k + 1]
                    val e = grid.around(a).first { grid.cells(it).let { (p, q) -> (p == a && q == b) || (p == b && q == a) } }
                    lines[e] = 1
                }
                val rows = IntArray(h) { r -> (0 until w).count { used[r * w + it] } }
                val cols = IntArray(w) { c -> (0 until h).count { used[it * w + c] } }
                // Les morceaux donnés : d'abord tous les traits ; on retire tant que c'est unique.
                val fixed = BooleanArray(grid.count) { lines[it] == 1 }
                fun unique(): Boolean {
                    val init = IntArray(grid.count) { e ->
                        when {
                            fixed[e] -> 1
                            !borderAllowed(grid, entry, exit, e) -> 0
                            else -> -1
                        }
                    }
                    return GridSearch.count(grid.count, 2, init, budget = 300,
                        complete = { g -> solvedLines(w, h, entry, exit, rows, cols, g) }) { g, e ->
                        ok(w, h, entry, exit, rows, cols, grid, g, e)
                    } == 1
                }
                fixed[grid.left(0, entry)] = true
                for (e in (0 until grid.count).shuffled(random)) {
                    if (!fixed[e] || e == grid.left(0, entry) || e == grid.bottom(exit, h - 1)) continue
                    fixed[e] = false
                    if (!unique()) fixed[e] = true
                }
                if (!unique()) continue
                return TracksState(w, h, entry, exit, rows, cols, fixed, IntArray(grid.count) { if (fixed[it]) 1 else 0 }, lines)
            }
        }
    }
}
