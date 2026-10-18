package com.Atom2Universe.app.games.puzzles.unequal

import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.kit.LatinHolder
import kotlin.random.Random

/**
 * « Unequal » de Simon Tatham (le Futoshiki) : un carré latin où des signes « plus petit que »
 * entre deux cases voisines doivent être respectés.
 *
 * [rel] : pour chaque case, 2 entrées — relation avec la voisine de droite puis avec celle du
 * dessous. 0 = rien, [LESS] = cette case est plus petite, [MORE] = plus grande.
 */
class UnequalState(override val board: LatinBoard, val rel: IntArray) : LatinHolder<UnequalState> {
    val n get() = board.n
    override fun withBoard(board: LatinBoard) = UnequalState(board, rel)

    /** Les cases qui contredisent un signe avec une voisine remplie. */
    fun broken(): Set<Int> {
        val out = HashSet<Int>()
        for (cell in 0 until n * n) for (d in 0..1) {
            val other = neighbour(n, cell, d)
            if (other < 0 || !holds(rel[cell * 2 + d], board.values[cell], board.values[other])) { if (other >= 0) { out.add(cell); out.add(other) } }
        }
        return out
    }

    val isSolved: Boolean get() = board.full && board.duplicates().isEmpty() && broken().isEmpty()

    fun encode() = board.encode() + "|" + rel.joinToString("")

    companion object {
        const val LESS = 1
        const val MORE = 2

        fun decode(text: String): UnequalState {
            val (b, r) = text.split('|')
            return UnequalState(LatinBoard.decode(b), r.map { it - '0' }.toIntArray())
        }

        /** La voisine de droite (d = 0) ou du dessous (d = 1), ou -1. */
        fun neighbour(n: Int, cell: Int, d: Int): Int = if (d == 0) {
            if (cell % n < n - 1) cell + 1 else -1
        } else {
            if (cell / n < n - 1) cell + n else -1
        }

        fun holds(relation: Int, a: Int, b: Int): Boolean = when {
            relation == 0 || a == 0 || b == 0 -> true
            relation == LESS -> a < b
            else -> a > b
        }

        fun consistent(values: IntArray, n: Int, rel: IntArray, cell: Int): Boolean {
            for (d in 0..1) {
                val o = neighbour(n, cell, d)
                if (o >= 0 && !holds(rel[cell * 2 + d], values[cell], values[o])) return false
            }
            val left = if (cell % n > 0) cell - 1 else -1
            val up = if (cell >= n) cell - n else -1
            if (left >= 0 && !holds(rel[left * 2], values[left], values[cell])) return false
            if (up >= 0 && !holds(rel[up * 2 + 1], values[up], values[cell])) return false
            return true
        }

        fun generate(n: Int, keepGivens: Int, random: Random): UnequalState {
            val solution = LatinBoard.randomSquare(n, random)
            val rel = IntArray(n * n * 2)
            for (cell in 0 until n * n) for (d in 0..1) {
                val o = neighbour(n, cell, d)
                if (o >= 0) rel[cell * 2 + d] = if (solution[cell] < solution[o]) LESS else MORE
            }
            val givens = solution.copyOf()
            fun unique() = LatinBoard.countSolutions(n, givens) { g, c -> consistent(g, n, rel, c) } == 1
            for (cell in (0 until n * n).shuffled(random)) {
                val keep = givens[cell]; givens[cell] = 0
                if (!unique()) givens[cell] = keep
            }
            for (k in rel.indices.filter { rel[it] != 0 }.shuffled(random)) {
                val keep = rel[k]; rel[k] = 0
                if (!unique()) rel[k] = keep
            }
            var extra = keepGivens
            for (cell in (0 until n * n).shuffled(random)) {
                if (extra <= 0) break
                if (givens[cell] == 0) { givens[cell] = solution[cell]; extra-- }
            }
            return UnequalState(LatinBoard.withGivens(n, givens, solution), rel)
        }
    }
}
