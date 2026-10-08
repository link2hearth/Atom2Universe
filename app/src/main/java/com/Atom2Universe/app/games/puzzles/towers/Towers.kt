package com.Atom2Universe.app.games.puzzles.towers

import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.kit.LatinHolder
import kotlin.random.Random

/**
 * « Towers » de Simon Tatham (les gratte-ciel) : un carré latin dont chaque chiffre est la
 * hauteur d'un immeuble ; un indice au bord dit combien d'immeubles on voit depuis là, les plus
 * hauts cachant les plus bas derrière eux.
 *
 * [clues] : 4n valeurs, 0 = pas d'indice. Dans l'ordre : haut (par colonne, regard vers le bas),
 * bas (regard vers le haut), gauche (par ligne, regard vers la droite), droite.
 */
class TowersState(override val board: LatinBoard, val clues: IntArray) : LatinHolder<TowersState> {
    val n get() = board.n

    override fun withBoard(board: LatinBoard) = TowersState(board, clues)

    /** Les indices contredits par une ligne complète. */
    fun brokenClues(): Set<Int> = (0 until 4 * n).filter { k ->
        clues[k] != 0 && line(board.values, n, k).let { l -> l.all { it != 0 } && visible(l) != clues[k] }
    }.toSet()

    val isSolved: Boolean get() = board.full && board.duplicates().isEmpty() && brokenClues().isEmpty()

    fun encode() = board.encode() + "|" + clues.joinToString(",")

    companion object {
        fun decode(text: String): TowersState {
            val (b, c) = text.split('|')
            return TowersState(LatinBoard.decode(b), c.split(',').map { it.toInt() }.toIntArray())
        }

        /** Les valeurs de la ligne vue depuis l'indice [k], dans l'ordre du regard. */
        fun line(values: IntArray, n: Int, k: Int): IntArray {
            val side = k / n; val i = k % n
            return IntArray(n) { j ->
                when (side) {
                    0 -> values[j * n + i]
                    1 -> values[(n - 1 - j) * n + i]
                    2 -> values[i * n + j]
                    else -> values[i * n + n - 1 - j]
                }
            }
        }

        fun visible(line: IntArray): Int {
            var max = 0; var count = 0
            for (v in line) if (v > max) { max = v; count++ }
            return count
        }

        /** Vrai si la ligne (peut-être incomplète) peut encore respecter son indice. */
        private fun lineOk(l: IntArray, clue: Int, n: Int): Boolean {
            if (clue == 0) return true
            var max = 0; var count = 0
            for (v in l) {
                if (v == 0) return count <= clue && (max < n || count == clue)
                if (v > max) { max = v; count++ }
            }
            return count == clue
        }

        fun consistent(values: IntArray, n: Int, clues: IntArray, cell: Int): Boolean {
            val r = cell / n; val c = cell % n
            for (k in intArrayOf(c, n + c, 2 * n + r, 3 * n + r)) {
                if (!lineOk(line(values, n, k), clues[k], n)) return false
            }
            return true
        }

        /**
         * Une solution au hasard, tous ses indices et tous ses chiffres ; on retire d'abord les
         * chiffres puis les indices, dans le désordre, tant que la solution reste unique.
         * [keepGivens] laisse quelques chiffres en plus pour les grilles faciles.
         */
        fun generate(n: Int, keepGivens: Int, random: Random): TowersState {
            val solution = LatinBoard.randomSquare(n, random)
            val clues = IntArray(4 * n) { visible(line(solution, n, it)) }
            val givens = solution.copyOf()
            fun unique() = LatinBoard.countSolutions(n, givens) { g, cell -> consistent(g, n, clues, cell) } == 1
            for (cell in (0 until n * n).shuffled(random)) {
                val keep = givens[cell]
                givens[cell] = 0
                if (!unique()) givens[cell] = keep
            }
            for (k in (0 until 4 * n).shuffled(random)) {
                val keep = clues[k]
                clues[k] = 0
                if (!unique()) clues[k] = keep
            }
            var extra = keepGivens
            for (cell in (0 until n * n).shuffled(random)) {
                if (extra <= 0) break
                if (givens[cell] == 0) { givens[cell] = solution[cell]; extra-- }
            }
            return TowersState(LatinBoard.withGivens(n, givens, solution), clues)
        }
    }
}
