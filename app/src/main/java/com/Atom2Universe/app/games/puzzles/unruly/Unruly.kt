package com.Atom2Universe.app.games.puzzles.unruly

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Unruly » de Simon Tatham (Takuzu, Binairo) : remplir la grille de deux couleurs, autant de
 * chaque dans chaque ligne et chaque colonne, sans jamais trois cases de suite de même couleur.
 *
 * [cells] : -1 vide, 0 ou 1. [fixed] : cases données. [solution] : gardée pour les astuces.
 */
class UnrulyState(val n: Int, val cells: IntArray, val fixed: BooleanArray, val solution: IntArray) {

    fun cycle(i: Int, forward: Boolean): UnrulyState? {
        if (fixed[i]) return null
        val next = cells.copyOf()
        next[i] = if (forward) when (cells[i]) { -1 -> 0; 0 -> 1; else -> -1 } else when (cells[i]) { -1 -> 1; 1 -> 0; else -> -1 }
        return UnrulyState(n, next, fixed, solution)
    }

    /** Astuce : une case fausse corrigée ou une case vide révélée, qui devient donnée. */
    fun hint(): UnrulyState? {
        val i = Hints.pick(n * n, { cells[it] >= 0 && cells[it] != solution[it] }, { cells[it] < 0 }) ?: return null
        val next = cells.copyOf(); next[i] = solution[i]
        val fx = fixed.copyOf(); fx[i] = true
        return UnrulyState(n, next, fx, solution)
    }

    /** Les cases d'un triplet de même couleur, ou d'une ligne qui a trop d'une couleur. */
    fun errors(): Set<Int> {
        val out = HashSet<Int>()
        for (line in 0 until n) for (horizontal in listOf(true, false)) {
            val idx = IntArray(n) { if (horizontal) line * n + it else it * n + line }
            for (k in 0..1) if (idx.count { cells[it] == k } > n / 2) idx.filter { cells[it] == k }.forEach { out.add(it) }
            for (j in 0 until n - 2) {
                val a = cells[idx[j]]
                if (a >= 0 && cells[idx[j + 1]] == a && cells[idx[j + 2]] == a) { out.add(idx[j]); out.add(idx[j + 1]); out.add(idx[j + 2]) }
            }
        }
        return out
    }

    val isSolved: Boolean get() = cells.none { it < 0 } && errors().isEmpty()

    fun encode() = "$n:" + cells.joinToString("") { if (it < 0) "." else it.toString() } + ":" +
        fixed.joinToString("") { if (it) "1" else "0" } + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): UnrulyState {
            val (a, b, c, d) = text.split(':')
            val n = a.toInt()
            return UnrulyState(n, IntArray(n * n) { if (b[it] == '.') -1 else b[it] - '0' }, BooleanArray(n * n) { c[it] == '1' },
                IntArray(n * n) { d[it] - '0' })
        }

        /** Vrai si la ligne et la colonne de [cell] restent possibles. */
        fun ok(g: IntArray, n: Int, cell: Int): Boolean {
            val r = cell / n; val c = cell % n
            for (horizontal in listOf(true, false)) {
                var zeros = 0; var ones = 0
                for (j in 0 until n) {
                    val v = g[if (horizontal) r * n + j else j * n + c]
                    if (v == 0) zeros++ else if (v == 1) ones++
                }
                if (zeros > n / 2 || ones > n / 2) return false
                val p = if (horizontal) c else r
                for (start in maxOf(0, p - 2)..minOf(n - 3, p)) {
                    val a = g[if (horizontal) r * n + start else start * n + c]
                    if (a < 0) continue
                    val b = g[if (horizontal) r * n + start + 1 else (start + 1) * n + c]
                    val d = g[if (horizontal) r * n + start + 2 else (start + 2) * n + c]
                    if (a == b && b == d) return false
                }
            }
            return true
        }

        private fun randomSolution(n: Int, random: Random): IntArray {
            val g = IntArray(n * n) { -1 }
            fun fill(cell: Int): Boolean {
                if (cell == n * n) return true
                for (v in if (random.nextBoolean()) intArrayOf(0, 1) else intArrayOf(1, 0)) {
                    g[cell] = v
                    if (ok(g, n, cell) && fill(cell + 1)) return true
                }
                g[cell] = -1
                return false
            }
            fill(0)
            return g
        }

        fun generate(n: Int, extraGivens: Int, random: Random): UnrulyState {
            val solution = randomSolution(n, random)
            val givens = solution.copyOf()
            for (cell in (0 until n * n).shuffled(random)) {
                val keep = givens[cell]
                givens[cell] = -1
                if (GridSearch.count(n * n, 2, givens) { g, c -> ok(g, n, c) } != 1) givens[cell] = keep
            }
            var extra = extraGivens
            for (cell in (0 until n * n).shuffled(random)) {
                if (extra <= 0) break
                if (givens[cell] < 0) { givens[cell] = solution[cell]; extra-- }
            }
            return UnrulyState(n, givens, BooleanArray(n * n) { givens[it] >= 0 }, solution)
        }
    }
}
