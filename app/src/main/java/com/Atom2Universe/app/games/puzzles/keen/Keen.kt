package com.Atom2Universe.app.games.puzzles.keen

import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.kit.LatinHolder
import kotlin.random.Random

/**
 * « Keen » de Simon Tatham (le KenKen) : un carré latin découpé en cages ; les chiffres d'une
 * cage donnent son résultat par l'opération indiquée (somme, différence, produit, quotient).
 *
 * [cage] : numéro de cage de chaque case. [ops] et [targets] : opération et résultat par cage.
 */
class KeenState(
    override val board: LatinBoard,
    val cage: IntArray,
    val ops: IntArray,
    val targets: IntArray,
) : LatinHolder<KeenState> {
    val n get() = board.n
    override fun withBoard(board: LatinBoard) = KeenState(board, cage, ops, targets)

    /** Les cases des cages remplies dont le résultat est faux. */
    fun broken(): Set<Int> {
        val out = HashSet<Int>()
        for (k in targets.indices) {
            val cells = cage.indices.filter { cage[it] == k }
            val vals = cells.map { board.values[it] }
            if (vals.all { it != 0 } && !check(ops[k], targets[k], vals, n, true)) out.addAll(cells)
        }
        return out
    }

    val isSolved: Boolean get() = board.full && board.duplicates().isEmpty() && broken().isEmpty()

    fun encode() = board.encode() + "|" + cage.joinToString(",") + "|" + ops.joinToString(",") + "|" + targets.joinToString(",")

    companion object {
        const val ADD = 0
        const val SUB = 1
        const val MUL = 2
        const val DIV = 3
        const val ONE = 4

        fun decode(text: String): KeenState {
            val p = text.split('|')
            fun ints(s: String) = s.split(',').map { it.toInt() }.toIntArray()
            return KeenState(LatinBoard.decode(p[0]), ints(p[1]), ints(p[2]), ints(p[3]))
        }

        /** Vrai si les valeurs (0 = vide si [complete] est faux) peuvent encore donner la cible. */
        fun check(op: Int, target: Int, vals: List<Int>, n: Int, complete: Boolean): Boolean {
            val filled = vals.filter { it != 0 }
            val empty = vals.size - filled.size
            if (empty > 0 && complete) return false
            return when (op) {
                ONE -> filled.isEmpty() || filled[0] == target
                ADD -> {
                    val s = filled.sum()
                    if (empty == 0) s == target else s + empty <= target && s + empty * n >= target
                }
                MUL -> {
                    val p = filled.fold(1L) { a, b -> a * b }
                    if (empty == 0) p == target.toLong() else target % p == 0L
                }
                SUB -> empty > 0 || kotlin.math.abs(vals[0] - vals[1]) == target
                else -> empty > 0 || maxOf(vals[0], vals[1]) == target * minOf(vals[0], vals[1])
            }
        }

        fun consistent(values: IntArray, n: Int, cage: IntArray, ops: IntArray, targets: IntArray, cell: Int): Boolean {
            val k = cage[cell]
            val vals = ArrayList<Int>(4)
            for (i in cage.indices) if (cage[i] == k) vals.add(values[i])
            return check(ops[k], targets[k], vals, n, false)
        }

        /** Découpe la grille en cages de 1 à 4 cases (surtout 2 et 3) d'un seul tenant. */
        private fun partition(n: Int, random: Random): IntArray {
            val cage = IntArray(n * n) { -1 }
            var next = 0
            for (start in (0 until n * n).shuffled(random)) {
                if (cage[start] >= 0) continue
                val size = listOf(1, 2, 2, 2, 3, 3, 3, 4).random(random)
                val cells = arrayListOf(start)
                cage[start] = next
                while (cells.size < size) {
                    val options = cells.flatMap { c ->
                        listOfNotNull(
                            if (c % n > 0) c - 1 else null, if (c % n < n - 1) c + 1 else null,
                            if (c >= n) c - n else null, if (c < n * n - n) c + n else null)
                    }.filter { cage[it] < 0 }
                    if (options.isEmpty()) break
                    val pick = options.random(random)
                    cage[pick] = next; cells.add(pick)
                }
                next++
            }
            return cage
        }

        fun generate(n: Int, random: Random): KeenState {
            while (true) {
                val solution = LatinBoard.randomSquare(n, random)
                val cage = partition(n, random)
                val count = cage.max() + 1
                val ops = IntArray(count); val targets = IntArray(count)
                for (k in 0 until count) {
                    val vals = cage.indices.filter { cage[it] == k }.map { solution[it] }
                    when {
                        vals.size == 1 -> { ops[k] = ONE; targets[k] = vals[0] }
                        vals.size == 2 && maxOf(vals[0], vals[1]) % minOf(vals[0], vals[1]) == 0 && random.nextInt(3) > 0 -> {
                            ops[k] = DIV; targets[k] = maxOf(vals[0], vals[1]) / minOf(vals[0], vals[1])
                        }
                        vals.size == 2 && random.nextBoolean() -> { ops[k] = SUB; targets[k] = kotlin.math.abs(vals[0] - vals[1]) }
                        random.nextBoolean() -> { ops[k] = ADD; targets[k] = vals.sum() }
                        else -> { ops[k] = MUL; targets[k] = vals.fold(1) { a, b -> a * b } }
                    }
                }
                val unique = LatinBoard.countSolutions(n, IntArray(n * n), budget = 300_000) { g, c ->
                    consistent(g, n, cage, ops, targets, c)
                } == 1
                if (unique) return KeenState(LatinBoard.withGivens(n, IntArray(n * n), solution), cage, ops, targets)
            }
        }
    }
}
