package com.Atom2Universe.app.games.puzzles.undead

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Undead » de Simon Tatham : remplir les cases libres de fantômes, de vampires et de zombies.
 * Un nombre au bord dit combien de monstres on voit en regardant depuis là, le regard rebondissant
 * sur les miroirs. Un vampire ne se voit que directement (pas dans un miroir), un fantôme que
 * dans un miroir, un zombie toujours. Les totaux de chaque monstre sont donnés.
 *
 * [mirrors] : 0 libre, [SLASH] « / », [BACKSLASH] « \ ». [cells] : 0 vide, [GHOST], [VAMPIRE], [ZOMBIE].
 * [clues] : un par port (comme la Boîte noire : haut, droite, bas, gauche), -1 = aucun.
 */
class UndeadState(
    val w: Int,
    val h: Int,
    val mirrors: IntArray,
    val clues: IntArray,
    val totals: IntArray,
    val cells: IntArray,
    /** Le monstre de chaque case libre, gardé pour les astuces. */
    val solution: IntArray,
) {
    val paths: List<List<Pair<Int, Boolean>>> by lazy { pathsOf(w, h, mirrors) }

    fun cycle(i: Int, forward: Boolean): UndeadState? {
        if (mirrors[i] != 0) return null
        val next = cells.copyOf()
        next[i] = Math.floorMod(cells[i] + if (forward) 1 else -1, 4)
        return UndeadState(w, h, mirrors, clues, totals, next, solution)
    }

    /** Astuce : un monstre faux remplacé, sinon un monstre posé dans une case vide. */
    fun hint(): UndeadState? {
        val free = { i: Int -> mirrors[i] == 0 }
        val i = Hints.pick(w * h, { free(it) && cells[it] != 0 && cells[it] != solution[it] }, { free(it) && cells[it] == 0 }) ?: return null
        val next = cells.copyOf(); next[i] = solution[i]
        return UndeadState(w, h, mirrors, clues, totals, next, solution)
    }

    fun seen(port: Int): Int = paths[port].count { (c, reflected) -> visible(cells[c] - 1, reflected) }
    fun placed(kind: Int) = cells.count { it == kind }

    val isSolved: Boolean
        get() = (0 until w * h).all { mirrors[it] != 0 || cells[it] != 0 } &&
            clues.indices.all { clues[it] < 0 || seen(it) == clues[it] } &&
            (1..3).all { placed(it) == totals[it - 1] }

    fun encode() = "$w,$h:" + listOf(mirrors, clues, totals, cells, solution).joinToString(":") { it.joinToString(",") }

    companion object {
        const val SLASH = 1
        const val BACKSLASH = 2
        const val GHOST = 1
        const val VAMPIRE = 2
        const val ZOMBIE = 3

        fun decode(text: String): UndeadState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            fun ints(s: String) = s.split(',').map { it.toInt() }.toIntArray()
            return UndeadState(w, h, ints(p[1]), ints(p[2]), ints(p[3]), ints(p[4]), ints(p[5]))
        }

        /** kind : 0 fantôme, 1 vampire, 2 zombie. */
        fun visible(kind: Int, reflected: Boolean): Boolean = when (kind) {
            0 -> reflected
            1 -> !reflected
            2 -> true
            else -> false
        }

        /** Pour chaque port, les cases libres traversées et si un miroir a déjà été passé. */
        fun pathsOf(w: Int, h: Int, mirrors: IntArray): List<List<Pair<Int, Boolean>>> {
            val ports = 2 * (w + h)
            return (0 until ports).map { port ->
                var (x, y, dx, dy) = when {
                    port < w -> intArrayOf(port, 0, 0, 1)
                    port < w + h -> intArrayOf(w - 1, port - w, -1, 0)
                    port < 2 * w + h -> intArrayOf(w - 1 - (port - w - h), h - 1, 0, -1)
                    else -> intArrayOf(0, h - 1 - (port - 2 * w - h), 1, 0)
                }
                val out = ArrayList<Pair<Int, Boolean>>()
                var reflected = false
                var guard = 0
                while (x in 0 until w && y in 0 until h && guard++ < 4 * w * h) {
                    val c = y * w + x
                    when (mirrors[c]) {
                        SLASH -> { val t = dx; dx = -dy; dy = -t; reflected = true }
                        BACKSLASH -> { val t = dx; dx = dy; dy = t; reflected = true }
                        else -> out.add(c to reflected)
                    }
                    x += dx; y += dy
                }
                out
            }
        }

        fun generate(w: Int, h: Int, random: Random): UndeadState {
            while (true) {
                val mirrors = IntArray(w * h) { if (random.nextInt(100) < 35) 1 + random.nextInt(2) else 0 }
                val free = (0 until w * h).filter { mirrors[it] == 0 }
                if (free.size < w * h / 2) continue
                val solution = IntArray(w * h)
                for (c in free) solution[c] = 1 + random.nextInt(3)
                val paths = pathsOf(w, h, mirrors)
                val clues = IntArray(paths.size) { p -> paths[p].count { (c, r) -> visible(solution[c] - 1, r) } }
                val totals = IntArray(3) { k -> free.count { solution[it] == k + 1 } }
                val byCell = HashMap<Int, MutableList<Int>>()
                paths.forEachIndexed { p, path -> path.forEach { (c, _) -> byCell.getOrPut(c) { ArrayList() }.add(p) } }
                val index = IntArray(w * h) { -1 }; free.forEachIndexed { k, c -> index[c] = k }
                fun unique(budget: Int) = GridSearch.count(free.size, 3, IntArray(free.size) { -1 }, budget = budget) { g, k ->
                    val cell = free[k]
                    for (p in byCell[cell].orEmpty().distinct()) {
                        if (clues[p] < 0) continue
                        var sure = 0; var maybe = 0
                        for ((c, r) in paths[p]) {
                            val v = g[index[c]]
                            if (v < 0) { maybe++ } else if (visible(v, r)) sure++
                        }
                        if (sure > clues[p] || sure + maybe < clues[p]) return@count false
                    }
                    val v = g[k]
                    if (v >= 0) {
                        var placed = 0; var open = 0
                        for (x in g) if (x == v) placed++ else if (x < 0) open++
                        if (placed > totals[v]) return@count false
                    }
                    for (kind in 0..2) {
                        val placed = g.count { it == kind }; val open = g.count { it < 0 }
                        if (placed + open < totals[kind]) return@count false
                    }
                    true
                } == 1
                if (!unique(400)) continue
                for (p in clues.indices.shuffled(random)) {
                    val keep = clues[p]; clues[p] = -1
                    if (!unique(1)) clues[p] = keep
                }
                return UndeadState(w, h, mirrors, clues, totals, IntArray(w * h), solution)
            }
        }
    }
}
