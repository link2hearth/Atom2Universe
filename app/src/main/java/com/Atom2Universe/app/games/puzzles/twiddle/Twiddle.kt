package com.Atom2Universe.app.games.puzzles.twiddle

import com.Atom2Universe.app.games.kit.MoveLog
import kotlin.random.Random

/**
 * « Twiddle » de Simon Tatham : faire tourner un bloc carré de [k] × [k] plaques d'un quart de
 * tour, jusqu'à remettre toute la grille dans l'ordre.
 */
class TwiddleState(
    val n: Int, val k: Int, val tiles: IntArray, val moves: Int,
    /** Les quarts de tour depuis la grille rangée (MoveLog : bloc y × n + x, +1 horaire, -1 sinon). */
    val log: IntArray = IntArray(0),
) {

    val isSolved: Boolean get() = tiles.indices.all { tiles[it] == it + 1 }

    /** Tourne le bloc dont le coin haut-gauche est (x, y), dans le sens horaire ou non. */
    fun rotate(x: Int, y: Int, clockwise: Boolean): TwiddleState {
        val next = tiles.copyOf()
        for (dy in 0 until k) for (dx in 0 until k) {
            // Sens horaire : la case (dx, dy) reçoit ce qui était en (dy, k-1-dx).
            val (sx, sy) = if (clockwise) dy to (k - 1 - dx) else (k - 1 - dy) to dx
            next[(y + dy) * n + x + dx] = tiles[(y + sy) * n + x + sx]
        }
        return TwiddleState(n, k, next, moves + 1, MoveLog.push(log, y * n + x, if (clockwise) 1 else -1, 4))
    }

    /** La démonstration : le journal rembobiné, un quart de tour par étape. */
    fun rewind(): List<TwiddleState> {
        val out = ArrayList<TwiddleState>()
        var s = this
        while (s.log.size >= 2) {
            val block = s.log[s.log.size - 2]; val turns = s.log[s.log.size - 1]
            // Défaire « turns » quarts horaires : un quart dans l'autre sens (ou un seul horaire pour 3).
            s = s.rotate(block % n, block / n, clockwise = turns == 3)
            out.add(s)
        }
        return out
    }

    fun encode(): String = "$n,$k,$moves:" + tiles.joinToString(" ") + ":" + MoveLog.encode(log)

    companion object {
        fun decode(text: String): TwiddleState? {
            val (head, body, log) = text.split(':').takeIf { it.size == 3 } ?: return null
            val (n, k, moves) = head.split(',').map { it.toInt() }
            val tiles = body.split(' ').map { it.toInt() }.toIntArray()
            return if (tiles.size == n * n) TwiddleState(n, k, tiles, moves, MoveLog.decode(log)) else null
        }

        fun generate(n: Int, k: Int, random: Random): TwiddleState {
            var s = TwiddleState(n, k, IntArray(n * n) { it + 1 }, 0)
            while (true) {
                repeat(n * n * 6) {
                    s = s.rotate(random.nextInt(n - k + 1), random.nextInt(n - k + 1), random.nextBoolean())
                }
                if (!s.isSolved) return TwiddleState(n, k, s.tiles, 0, s.log)
            }
        }
    }
}
