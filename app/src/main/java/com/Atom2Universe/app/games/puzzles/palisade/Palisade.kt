package com.Atom2Universe.app.games.puzzles.palisade

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.EdgeGrid
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Palisade » de Simon Tatham : découper la grille en régions qui ont toutes [k] cases, en
 * traçant des murs. Un nombre dans une case dit combien de ses côtés sont des murs (le bord de la
 * grille compte comme un mur).
 *
 * [edges] : arêtes intérieures, 0 rien, 1 mur, 2 croix. Les arêtes du bord sont toujours des murs.
 */
class PalisadeState(val w: Int, val h: Int, val k: Int, val clues: IntArray, val edges: IntArray,
    /** Les traits de la solution (1), gardés pour les astuces ; vide si inconnue. */
    val solution: IntArray = IntArray(0),
) {
    val grid by lazy { EdgeGrid(w, h) }

    fun border(e: Int) = grid.cells(e).any { it < 0 }
    fun wall(e: Int) = border(e) || edges[e] == 1
    fun walls(cell: Int) = grid.around(cell).count { wall(it) }

    fun set(list: Collection<Int>, v: Int): PalisadeState? {
        val next = edges.copyOf()
        var changed = false
        for (e in list) if (!border(e) && next[e] != v) { next[e] = v; changed = true }
        return if (changed) PalisadeState(w, h, k, clues, next, solution) else null
    }

    /** Astuce : un trait en trop barré, une croix fautive tracée, sinon un trait de la solution. */
    fun hint(): PalisadeState? {
        if (solution.size != edges.size) return null
        val (e, v) = Hints.edge(edges, solution) { !border(it) } ?: return null
        val next = edges.copyOf(); next[e] = v
        return PalisadeState(w, h, k, clues, next, solution)
    }

    /** Les régions délimitées par les murs posés : numéro de région de chaque case. */
    fun regions(): IntArray {
        val id = IntArray(w * h) { -1 }
        var next = 0
        for (s in 0 until w * h) {
            if (id[s] >= 0) continue
            val stack = ArrayDeque<Int>()
            stack.add(s); id[s] = next
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                for (e in grid.around(c)) {
                    if (wall(e)) continue
                    val (a, b) = grid.cells(e)
                    val o = if (a == c) b else a
                    if (o >= 0 && id[o] < 0) { id[o] = next; stack.add(o) }
                }
            }
            next++
        }
        return id
    }

    val isSolved: Boolean
        get() {
            val id = regions()
            val sizes = IntArray(w * h)
            for (r in id) sizes[r]++
            if (id.any { sizes[it] != k }) return false
            // Chaque mur sépare deux régions différentes (pas de mur qui pend dans une région).
            for (e in 0 until grid.count) if (!border(e) && edges[e] == 1) {
                val (a, b) = grid.cells(e)
                if (id[a] == id[b]) return false
            }
            return clues.indices.all { clues[it] < 0 || walls(it) == clues[it] }
        }

    fun encode() = "$w,$h,$k:" + clues.joinToString(",") + ":" + edges.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): PalisadeState {
            val p = text.split(':')
            val (w, h, k) = p[0].split(',').map { it.toInt() }
            return PalisadeState(w, h, k, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].map { it - '0' }.toIntArray(),
                p[3].map { it - '0' }.toIntArray())
        }

        /** g par arête : -1 inconnu, 0 ouvert, 1 mur (bords toujours 1). */
        fun ok(grid: EdgeGrid, k: Int, clues: IntArray, g: IntArray, e: Int): Boolean {
            val w = grid.w; val h = grid.h
            for (c in grid.cells(e)) {
                if (c < 0 || clues[c] < 0) continue
                val around = grid.around(c)
                val on = around.count { g[it] == 1 }; val open = around.count { g[it] < 0 }
                if (on > clues[c] || on + open < clues[c]) return false
            }
            fun component(start: Int, through: (Int) -> Boolean): Set<Int> {
                val seen = HashSet<Int>(); val stack = ArrayDeque<Int>()
                seen.add(start); stack.add(start)
                while (stack.isNotEmpty()) {
                    val c = stack.removeLast()
                    for (f in grid.around(c)) {
                        if (!through(g[f])) continue
                        val (a, b) = grid.cells(f)
                        val o = if (a == c) b else a
                        if (o >= 0 && seen.add(o)) stack.add(o)
                    }
                }
                return seen
            }
            for (c in grid.cells(e)) {
                if (c < 0) continue
                val sure = component(c) { it == 0 }
                if (sure.size > k) return false
                if (component(c) { it != 1 }.size < k) return false
            }
            // Un mur entre deux cases déjà reliées par des passages ouverts est impossible.
            if (g[e] == 1) {
                val (a, b) = grid.cells(e)
                if (a >= 0 && b >= 0 && b in component(a) { it == 0 }) return false
            }
            return w > 0 && h > 0
        }

        fun generate(w: Int, h: Int, k: Int, random: Random): PalisadeState {
            val grid = EdgeGrid(w, h)
            while (true) {
                // Découpage en polyominos de k cases, à rebours.
                val region = IntArray(w * h) { -1 }
                var nodes = 0
                fun around(c: Int) = listOfNotNull(if (c % w > 0) c - 1 else null, if (c % w < w - 1) c + 1 else null,
                    if (c >= w) c - w else null, if (c < w * h - w) c + w else null)
                fun fill(id: Int): Boolean {
                    if (++nodes > 20_000) return false
                    val start = region.indexOfFirst { it < 0 }
                    if (start < 0) return true
                    val cells = arrayListOf(start); region[start] = id
                    fun grow(): Boolean {
                        if (cells.size == k) return fill(id + 1)
                        val options = cells.flatMap { around(it) }.distinct().filter { region[it] < 0 }.shuffled(random)
                        for (o in options.take(3)) {
                            region[o] = id; cells.add(o)
                            if (grow()) return true
                            cells.removeAt(cells.size - 1); region[o] = -1
                        }
                        return false
                    }
                    if (grow()) return true
                    region[start] = -1
                    return false
                }
                if (!fill(0)) continue
                val wallsOf = IntArray(grid.count) { e -> grid.cells(e).let { (a, b) -> if (a < 0 || b < 0 || region[a] != region[b]) 1 else 0 } }
                val clues = IntArray(w * h) { c -> grid.around(c).count { wallsOf[it] == 1 } }
                fun unique(budget: Int): Boolean {
                    val init = IntArray(grid.count) { e -> if (grid.cells(e).any { it < 0 }) 1 else -1 }
                    return GridSearch.count(grid.count, 2, init, budget = budget,
                        complete = { g -> PalisadeState(w, h, k, clues, IntArray(grid.count) { if (g[it] == 1) 1 else 0 }).isSolved }) { g, e ->
                        ok(grid, k, clues, g, e)
                    } == 1
                }
                if (!unique(200)) continue
                for (c in (0 until w * h).shuffled(random)) {
                    val keep = clues[c]; clues[c] = -1
                    if (!unique(1)) clues[c] = keep
                }
                return PalisadeState(w, h, k, clues, IntArray(grid.count), wallsOf)
            }
        }
    }
}
