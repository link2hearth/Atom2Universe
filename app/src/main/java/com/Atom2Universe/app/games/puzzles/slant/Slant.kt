package com.Atom2Universe.app.games.puzzles.slant

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Slant » de Simon Tatham (Gokigen Naname) : tracer une diagonale dans chaque case. Un nombre
 * posé sur un coin dit combien de diagonales y touchent, et les diagonales ne doivent jamais
 * former de boucle fermée.
 *
 * [cells] : -1 vide, [BACK] « \ », [FORWARD] « / ». [clues] : un par coin, -1 = aucun.
 */
class SlantState(val w: Int, val h: Int, val clues: IntArray, val cells: IntArray, val solution: IntArray) {

    fun with(i: Int, v: Int): SlantState {
        val next = cells.copyOf(); next[i] = v
        return SlantState(w, h, clues, next, solution)
    }

    /** Astuce : une diagonale fausse retournée, sinon une case vide remplie. */
    fun hint(): SlantState? {
        val i = Hints.pick(cells.size, { cells[it] >= 0 && cells[it] != solution[it] }, { cells[it] < 0 }) ?: return null
        return with(i, solution[i])
    }

    fun touching(v: Int): Int = touchingOf(w, h, cells, v).first

    /** Les cases qui appartiennent à une boucle. */
    fun loopCells(): Set<Int> {
        val out = HashSet<Int>()
        for (c in cells.indices) {
            if (cells[c] < 0) continue
            val (a, b) = ends(w, c, cells[c])
            if (connected(w, h, cells, a, b, c)) out.add(c)
        }
        return out
    }

    val isSolved: Boolean
        get() = cells.all { it >= 0 } && clues.indices.all { clues[it] < 0 || touching(it) == clues[it] } && loopCells().isEmpty()

    fun encode() = "$w,$h:" + clues.joinToString(",") + ":" + cells.joinToString(",") + ":" + solution.joinToString("")

    companion object {
        const val BACK = 0
        const val FORWARD = 1

        fun decode(text: String): SlantState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return SlantState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].split(',').map { it.toInt() }.toIntArray(),
                p[3].map { it - '0' }.toIntArray())
        }

        /** Les deux coins reliés par la diagonale de la case [c]. */
        fun ends(w: Int, c: Int, v: Int): Pair<Int, Int> {
            val x = c % w; val y = c / w
            val vw = w + 1
            return if (v == BACK) (y * vw + x) to ((y + 1) * vw + x + 1) else (y * vw + x + 1) to ((y + 1) * vw + x)
        }

        /** (diagonales qui touchent le coin, cases voisines encore vides). */
        fun touchingOf(w: Int, h: Int, g: IntArray, v: Int): Pair<Int, Int> {
            val vx = v % (w + 1); val vy = v / (w + 1)
            var on = 0; var open = 0
            for ((dx, dy) in listOf(-1 to -1, 0 to -1, -1 to 0, 0 to 0)) {
                val cx = vx + dx; val cy = vy + dy
                if (cx !in 0 until w || cy !in 0 until h) continue
                val c = cy * w + cx
                val val0 = g[c]
                if (val0 < 0) { open++; continue }
                val (a, b) = ends(w, c, val0)
                if (a == v || b == v) on++
            }
            return on to open
        }

        /** Vrai si [a] et [b] sont déjà reliés par d'autres diagonales que celle de [skip]. */
        fun connected(w: Int, h: Int, g: IntArray, a: Int, b: Int, skip: Int): Boolean {
            val vw = w + 1
            val seen = HashSet<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(a); seen.add(a)
            while (stack.isNotEmpty()) {
                val v = stack.removeLast()
                if (v == b) return true
                val vx = v % vw; val vy = v / vw
                for ((dx, dy) in listOf(-1 to -1, 0 to -1, -1 to 0, 0 to 0)) {
                    val cx = vx + dx; val cy = vy + dy
                    if (cx !in 0 until w || cy !in 0 until h) continue
                    val c = cy * w + cx
                    if (c == skip || g[c] < 0) continue
                    val (p, q) = ends(w, c, g[c])
                    val o = if (p == v) q else if (q == v) p else -1
                    if (o >= 0 && seen.add(o)) stack.add(o)
                }
            }
            return false
        }

        fun ok(w: Int, h: Int, clues: IntArray, g: IntArray, c: Int): Boolean {
            if (g[c] < 0) return true
            val (a, b) = ends(w, c, g[c])
            if (connected(w, h, g, a, b, c)) return false
            val x = c % w; val y = c / w
            for (v in intArrayOf(y * (w + 1) + x, y * (w + 1) + x + 1, (y + 1) * (w + 1) + x, (y + 1) * (w + 1) + x + 1)) {
                if (clues[v] < 0) continue
                val (on, open) = touchingOf(w, h, g, v)
                if (on > clues[v] || on + open < clues[v]) return false
            }
            return true
        }

        fun generate(w: Int, h: Int, random: Random): SlantState {
            while (true) {
                val g = IntArray(w * h) { -1 }
                for (c in (0 until w * h).shuffled(random)) {
                    val first = random.nextInt(2)
                    g[c] = first
                    val (a, b) = ends(w, c, first)
                    if (connected(w, h, g, a, b, c)) g[c] = 1 - first
                }
                val clues = IntArray((w + 1) * (h + 1)) { touchingOf(w, h, g, it).first }
                fun unique() = GridSearch.count(w * h, 2, IntArray(w * h) { -1 }, budget = 1) { gg, cc -> ok(w, h, clues, gg, cc) } == 1
                if (!unique()) continue
                for (v in (0 until clues.size).shuffled(random)) {
                    val keep = clues[v]; clues[v] = -1
                    if (!unique()) clues[v] = keep
                }
                return SlantState(w, h, clues, IntArray(w * h) { -1 }, g)
            }
        }
    }
}
