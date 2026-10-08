package com.Atom2Universe.app.games.puzzles.loopy

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Loopy » de Simon Tatham (Slitherlink) : tracer sur le quadrillage une seule boucle fermée,
 * qui ne se croise pas ; un nombre dans une case dit combien de ses côtés la boucle emprunte.
 *
 * [clues] : -1 = pas de nombre. [edges] : 0 rien, 1 trait, 2 croix.
 */
class LoopyState(val w: Int, val h: Int, val clues: IntArray, val edges: IntArray,
    /** Les traits de la solution (1), gardés pour les astuces ; vide si inconnue. */
    val solution: IntArray = IntArray(0),
) {
    val grid by lazy { EdgeGrid(w, h) }

    fun lines(cell: Int) = grid.around(cell).count { edges[it] == 1 }

    fun set(list: Collection<Int>, v: Int): LoopyState? {
        val next = edges.copyOf()
        var changed = false
        for (e in list) if (next[e] != v) { next[e] = v; changed = true }
        return if (changed) LoopyState(w, h, clues, next, solution) else null
    }

    /** Astuce : un trait en trop barré, une croix fautive tracée, sinon un trait de la solution. */
    fun hint(): LoopyState? {
        if (solution.size != edges.size) return null
        val (e, v) = Hints.edge(edges, solution) { true } ?: return null
        val next = edges.copyOf(); next[e] = v
        return LoopyState(w, h, clues, next, solution)
    }

    /** Les sommets où plus de deux traits se rejoignent. */
    fun badVertices(): Set<Int> = (0 until (w + 1) * (h + 1)).filter { v ->
        grid.atVertex(v).count { edges[it] == 1 } > 2
    }.toSet()

    val isSolved: Boolean
        get() = clues.indices.all { clues[it] < 0 || lines(it) == clues[it] } &&
            grid.singleLoop(BooleanArray(grid.count) { edges[it] == 1 })

    fun encode() = "$w,$h:" + clues.joinToString(",") + ":" + edges.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): LoopyState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return LoopyState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].map { it - '0' }.toIntArray(),
                p[3].map { it - '0' }.toIntArray())
        }

        private fun around(w: Int, h: Int, i: Int): IntArray {
            val x = i % w; val y = i / w
            return intArrayOf(if (y > 0) i - w else -1, if (x < w - 1) i + 1 else -1,
                if (y < h - 1) i + w else -1, if (x > 0) i - 1 else -1)
        }

        /**
         * On raisonne sur les cases plutôt que sur les traits : la boucle sépare un « dedans »
         * d'un « dehors ». Un nombre compte les voisines (le bord comptant comme dehors) de l'autre
         * côté que sa case ; une seule boucle veut un dedans d'un seul tenant et un dehors relié
         * au bord. g : -1 inconnu, 0 dehors, 1 dedans.
         */
        fun ok(w: Int, h: Int, clues: IntArray, g: IntArray, c: Int): Boolean {
            for (k in around(w, h, c) + c) {
                if (k < 0 || clues[k] < 0 || g[k] < 0) continue
                var differ = 0; var open = 0
                for (n in around(w, h, k)) {
                    val v = if (n < 0) 0 else g[n]
                    if (v < 0) open++ else if (v != g[k]) differ++
                }
                if (differ > clues[k] || differ + open < clues[k]) return false
            }
            // Pas de damier 2 × 2 : la boucle passerait deux fois par le même coin.
            val cx = c % w; val cy = c / w
            for (y0 in maxOf(0, cy - 1)..minOf(h - 2, cy)) for (x0 in maxOf(0, cx - 1)..minOf(w - 2, cx)) {
                val a = g[y0 * w + x0]; val b = g[y0 * w + x0 + 1]; val d = g[(y0 + 1) * w + x0]; val e = g[(y0 + 1) * w + x0 + 1]
                if (a >= 0 && b >= 0 && d >= 0 && e >= 0 && a == e && b == d && a != b) return false
            }
            // Dedans d'un seul tenant.
            val inside = g.indexOfFirst { it == 1 }
            if (inside >= 0) {
                val seen = BooleanArray(w * h)
                val stack = ArrayDeque<Int>()
                stack.add(inside); seen[inside] = true
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    for (n in around(w, h, i)) if (n >= 0 && !seen[n] && g[n] != 0) { seen[n] = true; stack.add(n) }
                }
                if (g.indices.any { g[it] == 1 && !seen[it] }) return false
            }
            // Dehors relié au bord.
            val seen = BooleanArray(w * h)
            val stack = ArrayDeque<Int>()
            for (i in 0 until w * h) {
                val x = i % w; val y = i / w
                if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && g[i] != 1) { seen[i] = true; stack.add(i) }
            }
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                for (n in around(w, h, i)) if (n >= 0 && !seen[n] && g[n] != 1) { seen[n] = true; stack.add(n) }
            }
            return g.indices.none { g[it] == 0 && !seen[it] }
        }

        fun generate(w: Int, h: Int, random: Random): LoopyState {
            while (true) {
                // Un dedans qui grandit au hasard sans faire de trou.
                val inside = IntArray(w * h)
                inside[random.nextInt(w * h)] = 1
                val target = w * h * 45 / 100
                var tries = 0
                while (inside.count { it == 1 } < target && tries++ < w * h * 20) {
                    val candidates = inside.indices.filter { i -> inside[i] == 0 && around(w, h, i).any { it >= 0 && inside[it] == 1 } }
                    if (candidates.isEmpty()) break
                    val c = candidates.random(random)
                    inside[c] = 1
                    if (!ok(w, h, IntArray(w * h) { -1 }, inside, c)) inside[c] = 0
                }
                val clues = IntArray(w * h) { i ->
                    around(w, h, i).count { n -> (if (n < 0) 0 else inside[n]) != inside[i] }
                }
                fun unique() = GridSearch.count(w * h, 2, IntArray(w * h) { -1 }, budget = 400,
                    complete = { g -> g.any { it == 1 } }) { g, c -> ok(w, h, clues, g, c) } == 1
                if (!unique()) continue
                for (i in (0 until w * h).shuffled(random)) {
                    val keep = clues[i]; clues[i] = -1
                    if (!unique()) clues[i] = keep
                }
                val grid = EdgeGrid(w, h)
                val lines = IntArray(grid.count) { e ->
                    val (a, b) = grid.cells(e)
                    if ((if (a < 0) 0 else inside[a]) != (if (b < 0) 0 else inside[b])) 1 else 0
                }
                return LoopyState(w, h, clues, IntArray(grid.count), lines)
            }
        }
    }
}
