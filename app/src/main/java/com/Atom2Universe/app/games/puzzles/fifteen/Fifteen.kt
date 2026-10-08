package com.Atom2Universe.app.games.puzzles.fifteen

import kotlin.math.abs
import kotlin.random.Random

/**
 * Le taquin (« Fifteen » de Simon Tatham) : faire glisser les plaques dans le trou jusqu'à les
 * remettre dans l'ordre, le trou en bas à droite.
 *
 * [tiles] liste les plaques ligne par ligne, 0 pour le trou.
 */
class FifteenState(val w: Int, val h: Int, val tiles: IntArray, val moves: Int) {

    val gap: Int get() = tiles.indexOf(0)

    val isSolved: Boolean
        get() = (0 until w * h - 1).all { tiles[it] == it + 1 } && tiles[w * h - 1] == 0

    /** Glisse vers le trou toutes les plaques entre la case [index] et lui, s'ils sont alignés. */
    fun slide(index: Int): FifteenState? {
        val g = gap
        val gx = g % w
        val gy = g / w
        val x = index % w
        val y = index / w
        if (index == g || (x != gx && y != gy)) return null
        val next = tiles.copyOf()
        val step = when {
            y == gy -> if (x > gx) 1 else -1
            else -> if (y > gy) w else -w
        }
        var hole = g
        while (hole != index) {
            next[hole] = next[hole + step]
            hole += step
        }
        next[index] = 0
        return FifteenState(w, h, next, moves + 1)
    }

    fun encode(): String = "$w,$h,$moves:" + tiles.joinToString(" ")

    companion object {
        fun decode(text: String): FifteenState? {
            val (head, body) = text.split(':').takeIf { it.size == 2 } ?: return null
            val (w, h, moves) = head.split(',').map { it.toInt() }
            val tiles = body.split(' ').map { it.toInt() }.toIntArray()
            if (tiles.size != w * h) return null
            return FifteenState(w, h, tiles, moves)
        }

        /**
         * Une disposition au hasard, ramenée du bon côté de la parité : chaque glissement échange
         * le trou avec une voisine, donc change d'un coup la parité de la permutation et celle de
         * la distance du trou à son coin. Seules les dispositions où les deux s'accordent se
         * résolvent ; sinon on échange deux plaques, ce qui retourne la parité.
         */
        fun generate(w: Int, h: Int, random: Random): FifteenState {
            val n = w * h
            while (true) {
                val tiles = IntArray(n) { it }.also { it.shuffle(random) }
                if (!solvable(w, h, tiles)) {
                    val a = (0 until n).first { tiles[it] != 0 }
                    val b = (a + 1 until n).first { tiles[it] != 0 }
                    val t = tiles[a]; tiles[a] = tiles[b]; tiles[b] = t
                }
                val state = FifteenState(w, h, tiles, 0)
                if (!state.isSolved) return state
            }
        }

        fun solvable(w: Int, h: Int, tiles: IntArray): Boolean {
            val n = w * h
            // Le trou compte comme la plaque n : la disposition visée est alors 1, 2, … n.
            val values = IntArray(n) { if (tiles[it] == 0) n else tiles[it] }
            var inversions = 0
            for (i in 0 until n) for (j in i + 1 until n) if (values[i] > values[j]) inversions++
            val g = tiles.indexOf(0)
            val distance = abs(w - 1 - g % w) + abs(h - 1 - g / w)
            return inversions % 2 == distance % 2
        }
    }
}
