package com.Atom2Universe.app.games.puzzles.sixteen

import com.Atom2Universe.app.games.kit.MoveLog
import kotlin.random.Random

/**
 * « Sixteen » de Simon Tatham : un taquin sans trou, où l'on fait tourner une ligne ou une
 * colonne entière d'un cran (la plaque qui sort d'un côté rentre de l'autre).
 */
class SixteenState(
    val w: Int, val h: Int, val tiles: IntArray, val moves: Int,
    /** Les coups depuis la grille rangée (MoveLog : ligne 0..h-1 ou h + colonne, crans). */
    val log: IntArray = IntArray(0),
) {

    val isSolved: Boolean get() = tiles.indices.all { tiles[it] == it + 1 }

    /** Décale la ligne [row] de [by] crans vers la droite (négatif : vers la gauche). */
    fun shiftRow(row: Int, by: Int): SixteenState {
        val next = tiles.copyOf()
        for (x in 0 until w) next[row * w + Math.floorMod(x + by, w)] = tiles[row * w + x]
        return SixteenState(w, h, next, moves + 1, MoveLog.push(log, row, by, w))
    }

    /** Décale la colonne [col] de [by] crans vers le bas (négatif : vers le haut). */
    fun shiftCol(col: Int, by: Int): SixteenState {
        val next = tiles.copyOf()
        for (y in 0 until h) next[Math.floorMod(y + by, h) * w + col] = tiles[y * w + col]
        return SixteenState(w, h, next, moves + 1, MoveLog.push(log, h + col, by, h))
    }

    /** La démonstration : le journal rembobiné, un décalage par étape, jusqu'à la grille rangée. */
    fun rewind(): List<SixteenState> {
        val out = ArrayList<SixteenState>()
        var s = this
        while (s.log.size >= 2) {
            val what = s.log[s.log.size - 2]; val by = s.log[s.log.size - 1]
            s = if (what < h) s.shiftRow(what, -by) else s.shiftCol(what - h, -by)
            out.add(s)
        }
        return out
    }

    fun encode(): String = "$w,$h,$moves:" + tiles.joinToString(" ") + ":" + MoveLog.encode(log)

    companion object {
        fun decode(text: String): SixteenState? {
            val (head, body, log) = text.split(':').takeIf { it.size == 3 } ?: return null
            val (w, h, moves) = head.split(',').map { it.toInt() }
            val tiles = body.split(' ').map { it.toInt() }.toIntArray()
            return if (tiles.size == w * h) SixteenState(w, h, tiles, moves, MoveLog.decode(log)) else null
        }

        /** Des décalages au hasard depuis la grille rangée : toujours faisable. */
        fun generate(w: Int, h: Int, random: Random): SixteenState {
            var s = SixteenState(w, h, IntArray(w * h) { it + 1 }, 0)
            while (true) {
                repeat(12 * (w + h)) {
                    s = if (random.nextBoolean()) s.shiftRow(random.nextInt(h), if (random.nextBoolean()) 1 else -1)
                    else s.shiftCol(random.nextInt(w), if (random.nextBoolean()) 1 else -1)
                }
                if (!s.isSolved) return SixteenState(w, h, s.tiles, 0, s.log)
            }
        }
    }
}
