package com.Atom2Universe.app.games.puzzles.range

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Range » de Simon Tatham (Kurodoko) : noircir des cases pour que chaque nombre voie
 * exactement ce nombre de cases blanches en ligne droite dans les quatre directions, lui compris,
 * jusqu'au premier noir ou au bord. Deux noires ne se touchent pas par un côté, et toutes les
 * blanches forment un seul bloc.
 *
 * [numbers] : -1 = pas de nombre. [cells] : 0 indécis, 1 noir, 2 point (« blanc »).
 */
class RangeState(val w: Int, val h: Int, val numbers: IntArray, val cells: IntArray, val solution: IntArray) {

    fun with(i: Int, v: Int): RangeState {
        val next = cells.copyOf(); next[i] = v
        return RangeState(w, h, numbers, next, solution)
    }
    /** Astuce : une case fausse corrigée, sinon une case indécise révélée (noire, ou marquée blanche). */
    fun hint(): RangeState? {
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val i = Hints.pick(cells.size, { cells[it] != 0 && cells[it] != want(it) }, { cells[it] == 0 }) ?: return null
        return with(i, want(i))
    }

    fun seen(i: Int): Int = visible(w, h, IntArray(w * h) { if (cells[it] == 1) 1 else 0 }, i, unknownAsWhite = false)

    val isSolved: Boolean
        get() {
            val g = IntArray(w * h) { if (cells[it] == 1) 1 else 0 }
            return (0 until w * h).all { ok(w, h, numbers, g, it) }
        }

    fun encode() = "$w,$h:" + numbers.joinToString(",") + ":" + cells.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): RangeState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return RangeState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].map { it - '0' }.toIntArray(),
                p[3].map { it - '0' }.toIntArray())
        }

        fun around(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            return listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
                if (y > 0) i - w else null, if (y < h - 1) i + w else null)
        }

        /** Cases vues depuis [i] ; g : -1 inconnu, 0 blanc, 1 noir. */
        fun visible(w: Int, h: Int, g: IntArray, i: Int, unknownAsWhite: Boolean): Int {
            var count = 1
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                var x = i % w + dx; var y = i / w + dy
                while (x in 0 until w && y in 0 until h) {
                    val v = g[y * w + x]
                    if (v == 1 || (v < 0 && !unknownAsWhite)) break
                    count++; x += dx; y += dy
                }
            }
            return count
        }

        fun ok(w: Int, h: Int, numbers: IntArray, g: IntArray, c: Int): Boolean {
            if (g[c] == 1 && (numbers[c] >= 0 || around(w, h, c).any { g[it] == 1 })) return false
            // Les nombres de la ligne et de la colonne de c.
            val x = c % w; val y = c / w
            for (k in (0 until w).map { y * w + it } + (0 until h).map { it * w + x }) {
                if (numbers[k] < 0) continue
                if (visible(w, h, g, k, false) > numbers[k] || visible(w, h, g, k, true) < numbers[k]) return false
            }
            val start = g.indexOfFirst { it == 0 }
            if (start < 0) return true
            val seen = BooleanArray(w * h)
            val stack = ArrayDeque<Int>()
            stack.add(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                for (j in around(w, h, i)) if (!seen[j] && g[j] != 1) { seen[j] = true; stack.add(j) }
            }
            return g.indices.none { g[it] == 0 && !seen[it] }
        }

        fun generate(w: Int, h: Int, random: Random): RangeState {
            while (true) {
                val g = IntArray(w * h)
                val none = IntArray(w * h) { -1 }
                for (i in (0 until w * h).shuffled(random)) {
                    if (random.nextFloat() > 0.3f) continue
                    g[i] = 1
                    if (!ok(w, h, none, g, i)) g[i] = 0
                }
                val numbers = IntArray(w * h) { if (g[it] == 1) -1 else visible(w, h, g, it, false) }
                fun unique() = GridSearch.count(w * h, 2, IntArray(w * h) { -1 }, budget = 1) { gg, cc -> ok(w, h, numbers, gg, cc) } == 1
                if (!unique()) continue
                for (i in (0 until w * h).shuffled(random)) {
                    if (numbers[i] < 0) continue
                    val keep = numbers[i]
                    numbers[i] = -1
                    if (!unique()) numbers[i] = keep
                }
                return RangeState(w, h, numbers, IntArray(w * h), g)
            }
        }
    }
}
