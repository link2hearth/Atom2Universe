package com.Atom2Universe.app.games.puzzles.lightup

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Light Up » de Simon Tatham (Akari) : poser des ampoules pour éclairer toutes les cases
 * blanches. Une ampoule éclaire sa ligne et sa colonne jusqu'au premier mur ; deux ampoules ne
 * doivent pas se voir ; un mur numéroté touche exactement ce nombre d'ampoules.
 *
 * [walls] : [OPEN] case blanche, [WALL] mur sans nombre, 0 à 4 mur numéroté.
 * [cells] : 0 rien, 1 ampoule, 2 point (« pas d'ampoule ici », noté par le joueur).
 */
class LightUpState(val w: Int, val h: Int, val walls: IntArray, val cells: IntArray, val solution: IntArray) {
    /** Astuce : une case fausse corrigée, sinon une case indécise révélée (noire, ou marquée blanche). */
    fun hint(): LightUpState? {
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val i = Hints.pick(cells.size, { isOpen(it) && cells[it] != 0 && cells[it] != want(it) }, { isOpen(it) && cells[it] == 0 }) ?: return null
        return with(i, want(i))
    }

    fun isOpen(i: Int) = walls[i] == OPEN

    /** Les cases vues depuis [i] (elle comprise), jusqu'aux murs. */
    fun sight(i: Int): List<Int> = sightOf(w, h, walls, i)

    fun lit(): BooleanArray {
        val out = BooleanArray(w * h)
        for (i in cells.indices) if (cells[i] == 1) for (j in sight(i)) out[j] = true
        return out
    }

    fun clashingLights(): Set<Int> = cells.indices.filter { i ->
        cells[i] == 1 && sight(i).any { it != i && cells[it] == 1 }
    }.toSet()

    fun lightsAround(i: Int): Int = around(i).count { cells[it] == 1 }

    fun around(i: Int): List<Int> {
        val x = i % w; val y = i / w
        return listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
            if (y > 0) i - w else null, if (y < h - 1) i + w else null)
    }

    val isSolved: Boolean
        get() {
            val lit = lit()
            return (0 until w * h).all { !isOpen(it) || lit[it] } && clashingLights().isEmpty() &&
                (0 until w * h).all { walls[it] < 0 || lightsAround(it) == walls[it] }
        }

    fun with(i: Int, value: Int): LightUpState {
        val next = cells.copyOf(); next[i] = value
        return LightUpState(w, h, walls, next, solution)
    }

    fun encode() = "$w,$h:" + walls.joinToString(",") + ":" + cells.joinToString("") + ":" + solution.joinToString("")

    companion object {
        const val OPEN = -2
        const val WALL = -1

        fun decode(text: String): LightUpState {
            val (a, b, c) = text.split(':')
            val (w, h) = a.split(',').map { it.toInt() }
            return LightUpState(w, h, b.split(',').map { it.toInt() }.toIntArray(), c.map { it - '0' }.toIntArray(),
                text.substringAfterLast(':').map { it - '0' }.toIntArray())
        }

        fun sightOf(w: Int, h: Int, walls: IntArray, i: Int): List<Int> {
            val out = arrayListOf(i)
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                var x = i % w + dx; var y = i / w + dy
                while (x in 0 until w && y in 0 until h && walls[y * w + x] == OPEN) {
                    out.add(y * w + x); x += dx; y += dy
                }
            }
            return out
        }

        fun generate(w: Int, h: Int, wallRate: Float, random: Random): LightUpState {
            while (true) {
                // Murs symétriques par rotation, comme chez Tatham.
                val walls = IntArray(w * h) { OPEN }
                for (i in 0 until w * h) {
                    val j = w * h - 1 - i
                    if (i <= j && random.nextFloat() < wallRate) { walls[i] = WALL; walls[j] = WALL }
                }
                val sights = Array(w * h) { if (walls[it] == OPEN) sightOf(w, h, walls, it) else emptyList() }
                val light = IntArray(w * h)
                val lit = BooleanArray(w * h)
                while (true) {
                    val dark = (0 until w * h).filter { walls[it] == OPEN && !lit[it] }
                    if (dark.isEmpty()) break
                    val p = dark.random(random)
                    light[p] = 1
                    for (j in sights[p]) lit[j] = true
                }
                for (i in 0 until w * h) if (walls[i] == WALL) {
                    val x = i % w; val y = i / w
                    walls[i] = listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
                        if (y > 0) i - w else null, if (y < h - 1) i + w else null).count { light[it] == 1 }
                }
                val sightArrays = Array(w * h) { sights[it].toIntArray() }
                fun unique(): Boolean {
                    val init = IntArray(w * h) { if (walls[it] == OPEN) -1 else 0 }
                    return GridSearch.count(w * h, 2, init) { g, c -> consistent(w, h, walls, sightArrays, g, c) } == 1
                }
                if (!unique()) continue
                for (i in (0 until w * h).shuffled(random)) {
                    if (walls[i] < 0) continue
                    val keep = walls[i]
                    walls[i] = WALL
                    if (!unique()) walls[i] = keep
                }
                return LightUpState(w, h, walls, IntArray(w * h), light)
            }
        }

        /** g : -1 inconnu, 0 sans ampoule, 1 ampoule (les murs valent 0). */
        fun consistent(w: Int, h: Int, walls: IntArray, sights: Array<IntArray>, g: IntArray, c: Int): Boolean {
            if (walls[c] != OPEN) return g[c] == 0
            val sight = sights[c]
            if (g[c] == 1 && sight.any { it != c && g[it] == 1 }) return false
            // Les murs numérotés voisins.
            val x = c % w; val y = c / w
            for (n in listOfNotNull(if (x > 0) c - 1 else null, if (x < w - 1) c + 1 else null,
                    if (y > 0) c - w else null, if (y < h - 1) c + w else null)) {
                val v = walls[n]
                if (v < 0) continue
                val nx = n % w; val ny = n / w
                var on = 0; var unknown = 0
                for (m in listOfNotNull(if (nx > 0) n - 1 else null, if (nx < w - 1) n + 1 else null,
                        if (ny > 0) n - w else null, if (ny < h - 1) n + w else null)) {
                    if (walls[m] != OPEN) continue
                    if (g[m] == 1) on++ else if (g[m] < 0) unknown++
                }
                if (on > v || on + unknown < v) return false
            }
            // Chaque case sans ampoule de la croix doit encore pouvoir être éclairée.
            for (s in sight) {
                if (g[s] != 0) continue
                if (sights[s].none { g[it] != 0 }) return false
            }
            return true
        }
    }
}
