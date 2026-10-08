package com.Atom2Universe.app.games.puzzles.tents

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Tents » de Simon Tatham : planter une tente à côté de chaque arbre (à gauche, à droite,
 * dessus ou dessous), une tente par arbre ; deux tentes ne se touchent jamais, même en diagonale ;
 * les nombres en marge donnent les tentes de chaque ligne et colonne.
 *
 * [trees] : arbres. [cells] : 0 rien, 1 tente, 2 herbe (« pas de tente », noté par le joueur).
 */
class TentsState(
    val w: Int, val h: Int, val trees: BooleanArray, val rowCounts: IntArray, val colCounts: IntArray, val cells: IntArray,
    /** 1 là où va une lune (une tente), gardé pour les astuces. */
    val solution: IntArray,
) {

    fun with(i: Int, v: Int): TentsState {
        val next = cells.copyOf(); next[i] = v
        return TentsState(w, h, trees, rowCounts, colCounts, next, solution)
    }
    /** Astuce : une case fausse corrigée, sinon une case indécise révélée (noire, ou marquée blanche). */
    fun hint(): TentsState? {
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val i = Hints.pick(cells.size, { !trees[it] && cells[it] != 0 && cells[it] != want(it) }, { !trees[it] && cells[it] == 0 }) ?: return null
        return with(i, want(i))
    }

    fun touchingTents(): Set<Int> = cells.indices.filter { i ->
        cells[i] == 1 && neighbours8(w, h, i).any { cells[it] == 1 }
    }.toSet()

    /** Les lunes collées à aucune planète : elles ne peuvent appartenir à personne. */
    fun orphanMoons(): Set<Int> = cells.indices.filter { i ->
        cells[i] == 1 && neighbours4(w, h, i).none { trees[it] }
    }.toSet()

    /**
     * Quand toutes les lunes sont posées (autant que de planètes), les planètes qui n'en ont
     * aucune à côté d'elles : c'est la règle « une lune par planète » qui manque.
     */
    fun lonelyPlanets(): Set<Int> {
        if (cells.count { it == 1 } != trees.count { it }) return emptySet()
        return trees.indices.filter { t -> trees[t] && neighbours4(w, h, t).none { cells[it] == 1 } }.toSet()
    }

    fun rowTents(r: Int) = (0 until w).count { cells[r * w + it] == 1 }
    fun colTents(c: Int) = (0 until h).count { cells[it * w + c] == 1 }

    val isSolved: Boolean
        get() {
            val tents = cells.indices.filter { cells[it] == 1 }
            if (touchingTents().isNotEmpty()) return false
            if ((0 until h).any { rowTents(it) != rowCounts[it] } || (0 until w).any { colTents(it) != colCounts[it] }) return false
            return matched(w, h, trees, tents.toSet())
        }

    fun encode() = "$w,$h:" + trees.joinToString("") { if (it) "1" else "0" } + ":" + rowCounts.joinToString(",") +
        ":" + colCounts.joinToString(",") + ":" + cells.joinToString("") + ":" + solution.joinToString("")

    companion object {
        fun decode(text: String): TentsState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return TentsState(w, h, BooleanArray(w * h) { p[1][it] == '1' }, p[2].split(',').map { it.toInt() }.toIntArray(),
                p[3].split(',').map { it.toInt() }.toIntArray(), p[4].map { it - '0' }.toIntArray(), p[5].map { it - '0' }.toIntArray())
        }

        fun neighbours4(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            return listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
                if (y > 0) i - w else null, if (y < h - 1) i + w else null)
        }

        fun neighbours8(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            val out = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out.add(ny * w + nx)
            }
            return out
        }

        /** Chaque arbre a sa tente voisine, une seule par arbre (couplage parfait). */
        fun matched(w: Int, h: Int, trees: BooleanArray, tents: Set<Int>): Boolean {
            val treeList = trees.indices.filter { trees[it] }
            if (treeList.size != tents.size) return false
            val owner = HashMap<Int, Int>()
            fun augment(t: Int, seen: HashSet<Int>): Boolean {
                for (n in neighbours4(w, h, t)) {
                    if (n !in tents || !seen.add(n)) continue
                    val o = owner[n]
                    if (o == null || augment(o, seen)) { owner[n] = t; return true }
                }
                return false
            }
            return treeList.all { augment(it, HashSet()) }
        }

        fun consistent(w: Int, h: Int, trees: BooleanArray, rows: IntArray, cols: IntArray, g: IntArray, c: Int): Boolean {
            if (trees[c]) return g[c] == 0
            if (g[c] == 1) {
                if (neighbours8(w, h, c).any { g[it] == 1 }) return false
                if (neighbours4(w, h, c).none { trees[it] }) return false
            }
            val r = c / w; val col = c % w
            var on = 0; var unk = 0
            for (x in 0 until w) { val v = g[r * w + x]; if (v == 1) on++ else if (v < 0) unk++ }
            if (on > rows[r] || on + unk < rows[r]) return false
            on = 0; unk = 0
            for (y in 0 until h) { val v = g[y * w + col]; if (v == 1) on++ else if (v < 0) unk++ }
            if (on > cols[col] || on + unk < cols[col]) return false
            for (t in neighbours4(w, h, c)) if (trees[t] && neighbours4(w, h, t).none { g[it] != 0 && !trees[it] }) return false
            return true
        }

        fun generate(w: Int, h: Int, random: Random): TentsState {
            while (true) {
                val tent = BooleanArray(w * h)
                val trees = BooleanArray(w * h)
                val target = w * h / 5
                var placed = 0
                for (i in (0 until w * h).shuffled(random)) {
                    if (placed >= target) break
                    if (tent[i] || trees[i] || neighbours8(w, h, i).any { tent[it] }) continue
                    val spots = neighbours4(w, h, i).filter { !tent[it] && !trees[it] }
                    if (spots.isEmpty()) continue
                    tent[i] = true
                    trees[spots.random(random)] = true
                    placed++
                }
                val rows = IntArray(h) { r -> (0 until w).count { tent[r * w + it] } }
                val cols = IntArray(w) { c -> (0 until h).count { tent[it * w + c] } }
                val init = IntArray(w * h) { if (trees[it]) 0 else -1 }
                val n = GridSearch.count(w * h, 2, init, complete = { g ->
                    matched(w, h, trees, g.indices.filter { g[it] == 1 }.toSet())
                }) { g, c -> consistent(w, h, trees, rows, cols, g, c) }
                if (n == 1) return TentsState(w, h, trees, rows, cols, IntArray(w * h), IntArray(w * h) { if (tent[it]) 1 else 0 })
            }
        }
    }
}
