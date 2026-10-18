package com.Atom2Universe.app.games.puzzles.map

import com.Atom2Universe.app.games.kit.Hints
import kotlin.random.Random

/**
 * « Map » de Simon Tatham : colorier les pays d'une carte avec quatre couleurs, sans que deux
 * pays voisins aient la même. Quelques pays sont déjà coloriés ; la solution est unique.
 *
 * [region] : pays de chaque case. [colours] : couleur de chaque pays (-1 = aucune).
 * [fixed] : pays coloriés par l'énoncé.
 */
class MapState(val w: Int, val h: Int, val region: IntArray, val colours: IntArray, val fixed: BooleanArray, val solution: IntArray) {
    val regions get() = colours.size
    val adjacency: List<Set<Int>> by lazy { adjacencyOf(w, h, region, regions) }

    fun paint(r: Int, colour: Int): MapState? {
        if (fixed[r] || colours[r] == colour) return null
        val c = colours.copyOf(); c[r] = colour
        return MapState(w, h, region, c, fixed, solution)
    }

    /** Astuce : une région mal coloriée corrigée, sinon une région vide coloriée ; elle devient fixe. */
    fun hint(): MapState? {
        val r = Hints.pick(regions, { !fixed[it] && colours[it] >= 0 && colours[it] != solution[it] }, { colours[it] < 0 }) ?: return null
        val c = colours.copyOf(); c[r] = solution[r]
        val f = fixed.copyOf(); f[r] = true
        return MapState(w, h, region, c, f, solution)
    }

    fun clashes(): Set<Int> = (0 until regions).filter { r ->
        colours[r] >= 0 && adjacency[r].any { colours[it] == colours[r] }
    }.toSet()

    val isSolved: Boolean get() = colours.all { it >= 0 } && clashes().isEmpty()

    fun encode() = "$w,$h:" + region.joinToString(",") + ":" + colours.joinToString(",") + ":" +
        fixed.joinToString("") { if (it) "1" else "0" } + ":" + solution.joinToString(",")

    companion object {
        fun decode(text: String): MapState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            val colours = p[2].split(',').map { it.toInt() }.toIntArray()
            return MapState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), colours,
                BooleanArray(colours.size) { p[3][it] == '1' }, p[4].split(',').map { it.toInt() }.toIntArray())
        }

        fun adjacencyOf(w: Int, h: Int, region: IntArray, count: Int): List<Set<Int>> {
            val adj = List(count) { HashSet<Int>() }
            for (i in region.indices) {
                if (i % w < w - 1 && region[i] != region[i + 1]) { adj[region[i]].add(region[i + 1]); adj[region[i + 1]].add(region[i]) }
                if (i / w < h - 1 && region[i] != region[i + w]) { adj[region[i]].add(region[i + w]); adj[region[i + w]].add(region[i]) }
            }
            return adj
        }

        /** Nombre de coloriages (jusqu'à [limit]) qui prolongent [start]. */
        fun countColourings(adj: List<Set<Int>>, start: IntArray, limit: Int = 2): Int {
            val c = start.copyOf()
            var found = 0; var nodes = 0
            fun search() {
                if (found >= limit || ++nodes > 200_000) return
                var best = -1; var bestOpts = 5
                for (r in c.indices) {
                    if (c[r] >= 0) continue
                    val opts = (0..3).count { k -> adj[r].none { c[it] == k } }
                    if (opts == 0) return
                    if (opts < bestOpts) { best = r; bestOpts = opts }
                }
                if (best < 0) { found++; return }
                for (k in 0..3) {
                    if (adj[best].any { c[it] == k }) continue
                    c[best] = k; search(); c[best] = -1
                    if (found >= limit) return
                }
            }
            search()
            return if (nodes > 200_000) limit else found
        }

        fun generate(w: Int, h: Int, count: Int, random: Random): MapState {
            while (true) {
                // Des pays qui poussent à partir de graines, case par case, au hasard.
                val region = IntArray(w * h) { -1 }
                val frontier = ArrayList<Int>()
                (0 until w * h).shuffled(random).take(count).forEachIndexed { k, cell -> region[cell] = k; frontier.add(cell) }
                while (frontier.isNotEmpty()) {
                    val idx = random.nextInt(frontier.size)
                    val cell = frontier[idx]
                    val x = cell % w; val y = cell / w
                    val free = listOfNotNull(if (x > 0) cell - 1 else null, if (x < w - 1) cell + 1 else null,
                        if (y > 0) cell - w else null, if (y < h - 1) cell + w else null).filter { region[it] < 0 }
                    if (free.isEmpty()) { frontier.removeAt(idx); continue }
                    val n = free.random(random)
                    region[n] = region[cell]
                    frontier.add(n)
                }
                val adj = adjacencyOf(w, h, region, count)
                // Une solution au hasard.
                val solution = IntArray(count) { -1 }
                fun colour(r: Int): Boolean {
                    if (r == count) return true
                    for (k in (0..3).shuffled(random)) {
                        if (adj[r].any { solution[it] == k }) continue
                        solution[r] = k
                        if (colour(r + 1)) return true
                    }
                    solution[r] = -1
                    return false
                }
                if (!colour(0)) continue
                val givens = solution.copyOf()
                for (r in (0 until count).shuffled(random)) {
                    val keep = givens[r]; givens[r] = -1
                    if (countColourings(adj, givens) != 1) givens[r] = keep
                }
                return MapState(w, h, region, givens, BooleanArray(count) { givens[it] >= 0 }, solution)
            }
        }
    }
}
