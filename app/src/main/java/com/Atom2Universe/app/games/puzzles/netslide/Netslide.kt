package com.Atom2Universe.app.games.puzzles.netslide

import com.Atom2Universe.app.games.kit.MoveLog
import kotlin.random.Random

/**
 * « Netslide » de Simon Tatham : un réseau de tuiles à tuyaux, mélangé en faisant tourner des
 * lignes et des colonnes entières. Le remettre en place pour que tout le réseau soit relié à la
 * source, sans un seul tuyau qui débouche dans le vide.
 *
 * [tiles] : bits de chaque tuile ([UP], [RIGHT], [DOWN], [LEFT]) ; [SOURCE] marque la source,
 * qui voyage avec sa tuile.
 */
class NetslideState(
    val w: Int, val h: Int, val tiles: IntArray, val moves: Int,
    /** Les coups depuis la grille rangée (MoveLog : ligne 0..h-1 ou h + colonne, crans). */
    val log: IntArray = IntArray(0),
) {

    val source: Int get() = tiles.indexOfFirst { it and SOURCE != 0 }

    fun shiftRow(row: Int, by: Int): NetslideState {
        val next = tiles.copyOf()
        for (x in 0 until w) next[row * w + Math.floorMod(x + by, w)] = tiles[row * w + x]
        return NetslideState(w, h, next, moves + 1, MoveLog.push(log, row, by, w))
    }

    fun shiftCol(col: Int, by: Int): NetslideState {
        val next = tiles.copyOf()
        for (y in 0 until h) next[Math.floorMod(y + by, h) * w + col] = tiles[y * w + col]
        return NetslideState(w, h, next, moves + 1, MoveLog.push(log, h + col, by, h))
    }

    /** La démonstration : le journal rembobiné, un décalage par étape, jusqu'à la grille rangée. */
    fun rewind(): List<NetslideState> {
        val out = ArrayList<NetslideState>()
        var s = this
        while (s.log.size >= 2) {
            val what = s.log[s.log.size - 2]; val by = s.log[s.log.size - 1]
            s = if (what < h) s.shiftRow(what, -by) else s.shiftCol(what - h, -by)
            out.add(s)
        }
        return out
    }

    /** Les tuiles reliées à la source par des tuyaux qui se répondent. */
    fun powered(): BooleanArray {
        val on = BooleanArray(w * h)
        val s = source
        if (s < 0) return on
        val stack = ArrayDeque<Int>()
        stack.add(s); on[s] = true
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            for (d in 0..3) {
                if (tiles[i] and (1 shl d) == 0) continue
                val j = neighbour(i, d)
                if (j < 0 || on[j] || tiles[j] and (1 shl ((d + 2) % 4)) == 0) continue
                on[j] = true; stack.add(j)
            }
        }
        return on
    }

    fun neighbour(i: Int, d: Int): Int {
        val x = i % w; val y = i / w
        return when (d) {
            0 -> if (y > 0) i - w else -1
            1 -> if (x < w - 1) i + 1 else -1
            2 -> if (y < h - 1) i + w else -1
            else -> if (x > 0) i - 1 else -1
        }
    }

    val isSolved: Boolean
        get() {
            if (!powered().all { it }) return false
            for (i in tiles.indices) for (d in 0..3) {
                if (tiles[i] and (1 shl d) == 0) continue
                val j = neighbour(i, d)
                if (j < 0 || tiles[j] and (1 shl ((d + 2) % 4)) == 0) return false
            }
            return true
        }

    fun encode() = "$w,$h,$moves:" + tiles.joinToString(",") + ":" + MoveLog.encode(log)

    companion object {
        const val UP = 1
        const val RIGHT = 2
        const val DOWN = 4
        const val LEFT = 8
        const val SOURCE = 16

        fun decode(text: String): NetslideState {
            val (a, b, c) = text.split(':')
            val (w, h, moves) = a.split(',').map { it.toInt() }
            return NetslideState(w, h, b.split(',').map { it.toInt() }.toIntArray(), moves, MoveLog.decode(c))
        }

        /** Un arbre couvrant au hasard (algorithme de Prim) depuis la case centrale. */
        fun generate(w: Int, h: Int, random: Random): NetslideState {
            val tiles = IntArray(w * h)
            val inTree = BooleanArray(w * h)
            val centre = (h / 2) * w + w / 2
            inTree[centre] = true
            val frontier = ArrayList<Pair<Int, Int>>() // (case de l'arbre, direction)
            val probe = NetslideState(w, h, tiles, 0)
            fun addEdges(i: Int) { for (d in 0..3) { val j = probe.neighbour(i, d); if (j >= 0 && !inTree[j]) frontier.add(i to d) } }
            addEdges(centre)
            while (frontier.isNotEmpty()) {
                val (i, d) = frontier.removeAt(random.nextInt(frontier.size))
                val j = probe.neighbour(i, d)
                if (inTree[j]) continue
                // Pas de croix à quatre branches : elles rendent la grille trop facile à lire.
                if (Integer.bitCount(tiles[i]) >= 3) continue
                tiles[i] = tiles[i] or (1 shl d)
                tiles[j] = tiles[j] or (1 shl ((d + 2) % 4))
                inTree[j] = true
                addEdges(j)
            }
            if (!inTree.all { it }) return generate(w, h, random)
            tiles[centre] = tiles[centre] or SOURCE
            var s = NetslideState(w, h, tiles, 0)
            while (true) {
                repeat(10 * (w + h)) {
                    s = if (random.nextBoolean()) s.shiftRow(random.nextInt(h), if (random.nextBoolean()) 1 else -1)
                    else s.shiftCol(random.nextInt(w), if (random.nextBoolean()) 1 else -1)
                }
                if (!s.isSolved) return NetslideState(w, h, s.tiles, 0, s.log)
            }
        }
    }
}
