package com.Atom2Universe.app.games.puzzles.singles

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import com.Atom2Universe.app.games.kit.LatinBoard
import kotlin.random.Random

/**
 * « Singles » de Simon Tatham (Hitori) : noircir des cases pour qu'aucun nombre ne se répète
 * dans une ligne ou une colonne parmi les cases restées blanches ; deux cases noires ne se
 * touchent pas par un côté, et toutes les blanches forment un seul bloc.
 *
 * [cells] : 0 indécis, 1 noir, 2 entouré (« reste blanc », noté par le joueur).
 */
class SinglesState(val n: Int, val numbers: IntArray, val cells: IntArray, val solution: IntArray) {

    fun with(i: Int, v: Int): SinglesState {
        val next = cells.copyOf(); next[i] = v
        return SinglesState(n, numbers, next, solution)
    }
    /** Astuce : une case fausse corrigée, sinon une case indécise révélée (noire, ou marquée blanche). */
    fun hint(): SinglesState? {
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val i = Hints.pick(cells.size, { cells[it] != 0 && cells[it] != want(it) }, { cells[it] == 0 }) ?: return null
        return with(i, want(i))
    }

    /** Les cases en faute : noires accolées, ou nombres répétés parmi les non-noires. */
    fun errors(): Set<Int> {
        val out = HashSet<Int>()
        for (i in cells.indices) {
            if (cells[i] == 1) {
                for (j in around(n, i)) if (cells[j] == 1) out.add(i)
                continue
            }
            val r = i / n; val c = i % n
            for (k in 0 until n) {
                val a = r * n + k; val b = k * n + c
                if (a != i && cells[a] != 1 && numbers[a] == numbers[i] && cells[a] == 2 && cells[i] == 2) out.add(i)
                if (b != i && cells[b] != 1 && numbers[b] == numbers[i] && cells[b] == 2 && cells[i] == 2) out.add(i)
            }
        }
        return out
    }

    val isSolved: Boolean
        get() {
            val g = IntArray(n * n) { if (cells[it] == 1) 1 else 0 }
            return (0 until n * n).all { ok(n, numbers, g, it) }
        }

    fun encode() = "$n:" + numbers.joinToString(",") + ":" + cells.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): SinglesState {
            val p = text.split(':')
            val n = p[0].toInt()
            return SinglesState(n, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].map { it - '0' }.toIntArray(),
                p[3].map { it - '0' }.toIntArray())
        }

        fun around(n: Int, i: Int): List<Int> {
            val x = i % n; val y = i / n
            return listOfNotNull(if (x > 0) i - 1 else null, if (x < n - 1) i + 1 else null,
                if (y > 0) i - n else null, if (y < n - 1) i + n else null)
        }

        /** g : -1 inconnu, 0 blanc, 1 noir. */
        fun ok(n: Int, numbers: IntArray, g: IntArray, c: Int): Boolean {
            if (g[c] == 1 && around(n, c).any { g[it] == 1 }) return false
            if (g[c] == 0) {
                val r = c / n; val col = c % n
                for (k in 0 until n) {
                    val a = r * n + k; val b = k * n + col
                    if (a != c && g[a] == 0 && numbers[a] == numbers[c]) return false
                    if (b != c && g[b] == 0 && numbers[b] == numbers[c]) return false
                }
            }
            // Les blanches doivent pouvoir se rejoindre en passant par des cases non noires.
            val start = g.indexOfFirst { it == 0 }
            if (start < 0) return true
            val seen = BooleanArray(n * n)
            val stack = ArrayDeque<Int>()
            stack.add(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                for (j in around(n, i)) if (!seen[j] && g[j] != 1) { seen[j] = true; stack.add(j) }
            }
            return g.indices.none { g[it] == 0 && !seen[it] }
        }

        /**
         * Méthode de Tatham : un carré latin, des cases noires au hasard (jamais accolées, blanches
         * d'un seul tenant), et chaque case noire reçoit le nombre d'une blanche de sa ligne ou de
         * sa colonne. On garde la grille si la solution est unique.
         */
        fun generate(n: Int, random: Random): SinglesState {
            while (true) {
                val numbers = LatinBoard.randomSquare(n, random)
                val g = IntArray(n * n)
                for (i in (0 until n * n).shuffled(random)) {
                    if (random.nextFloat() > 0.45f) continue
                    g[i] = 1
                    if (!ok(n, IntArray(n * n) { -it - 1 }, g, i)) g[i] = 0
                }
                for (i in 0 until n * n) if (g[i] == 1) {
                    val r = i / n; val c = i % n
                    val donors = (0 until n).map { r * n + it } + (0 until n).map { it * n + c }
                    numbers[i] = numbers[donors.filter { g[it] == 0 }.random(random)]
                }
                if (GridSearch.count(n * n, 2, IntArray(n * n) { -1 }) { gg, cc -> ok(n, numbers, gg, cc) } == 1)
                    return SinglesState(n, numbers, IntArray(n * n), g)
            }
        }
    }
}
