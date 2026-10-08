package com.Atom2Universe.app.games.puzzles.magnets

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Magnets » de Simon Tatham : la grille est pavée de dominos ; chacun est un aimant (un bout
 * +, un bout −) ou un bloc neutre. Deux bouts de même signe ne se touchent jamais par un côté ;
 * les nombres en marge comptent les + et les − de chaque ligne et colonne.
 *
 * [mate] : la case jumelle de chaque case. [cells] : par case, 0 inconnu, [PLUS], [MINUS],
 * [NEUTRAL] (posés par le joueur, toujours cohérents sur un domino).
 */
class MagnetsState(
    val w: Int,
    val h: Int,
    val mate: IntArray,
    val rowPlus: IntArray,
    val rowMinus: IntArray,
    val colPlus: IntArray,
    val colMinus: IntArray,
    val cells: IntArray,
    /** Le signe de chaque case dans la solution, gardé pour les astuces. */
    val solution: IntArray,
) {
    /** Fait tourner un domino : vide → + − → − + → neutre → vide. */
    fun cycle(i: Int, forward: Boolean): MagnetsState {
        val j = mate[i]
        val order = listOf(0 to 0, PLUS to MINUS, MINUS to PLUS, NEUTRAL to NEUTRAL)
        val k = order.indexOf(cells[i] to cells[j]).coerceAtLeast(0)
        val (a, b) = order[Math.floorMod(k + if (forward) 1 else -1, order.size)]
        val next = cells.copyOf(); next[i] = a; next[j] = b
        return MagnetsState(w, h, mate, rowPlus, rowMinus, colPlus, colMinus, next, solution)
    }

    /** Astuce : un domino faux remis juste, sinon un domino vide rempli (ses deux cases). */
    fun hint(): MagnetsState? {
        val i = Hints.pick(w * h, { cells[it] != 0 && cells[it] != solution[it] }, { cells[it] == 0 }) ?: return null
        val next = cells.copyOf(); next[i] = solution[i]; next[mate[i]] = solution[mate[i]]
        return MagnetsState(w, h, mate, rowPlus, rowMinus, colPlus, colMinus, next, solution)
    }

    fun clashes(): Set<Int> = cells.indices.filter { i ->
        (cells[i] == PLUS || cells[i] == MINUS) && around(w, h, i).any { it != mate[i] && cells[it] == cells[i] }
    }.toSet()

    fun count(row: Boolean, k: Int, sign: Int) =
        if (row) (0 until w).count { cells[k * w + it] == sign } else (0 until h).count { cells[it * w + k] == sign }

    val isSolved: Boolean
        get() = cells.all { it != 0 } && clashes().isEmpty() &&
            (0 until h).all { count(true, it, PLUS) == rowPlus[it] && count(true, it, MINUS) == rowMinus[it] } &&
            (0 until w).all { count(false, it, PLUS) == colPlus[it] && count(false, it, MINUS) == colMinus[it] }

    fun encode() = "$w,$h:" + listOf(mate, rowPlus, rowMinus, colPlus, colMinus, cells, solution).joinToString(":") { it.joinToString(",") }

    companion object {
        const val PLUS = 1
        const val MINUS = 2
        const val NEUTRAL = 3

        fun decode(text: String): MagnetsState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            fun ints(s: String) = s.split(',').map { it.toInt() }.toIntArray()
            return MagnetsState(w, h, ints(p[1]), ints(p[2]), ints(p[3]), ints(p[4]), ints(p[5]), ints(p[6]), ints(p[7]))
        }

        fun around(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            return listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
                if (y > 0) i - w else null, if (y < h - 1) i + w else null)
        }

        /** Signe de la case [i] quand son domino (de tête [head]) vaut [v] (0 neutre, 1 tête +, 2 tête −). */
        private fun sign(v: Int, isHead: Boolean): Int = when (v) {
            0 -> NEUTRAL
            1 -> if (isHead) PLUS else MINUS
            else -> if (isHead) MINUS else PLUS
        }

        fun generate(w: Int, h: Int, random: Random): MagnetsState {
            while (true) {
                // Pavage par dominos au hasard.
                val mate = IntArray(w * h) { -1 }
                fun tile(): Boolean {
                    val c = mate.indexOfFirst { it < 0 }
                    if (c < 0) return true
                    val opts = listOfNotNull(if (c % w < w - 1 && mate[c + 1] < 0) c + 1 else null,
                        if (c / w < h - 1 && mate[c + w] < 0) c + w else null).shuffled(random)
                    for (o in opts) { mate[c] = o; mate[o] = c; if (tile()) return true; mate[c] = -1; mate[o] = -1 }
                    return false
                }
                if (!tile()) continue
                val heads = (0 until w * h).filter { mate[it] > it }
                val headOf = IntArray(w * h) { if (mate[it] > it) it else mate[it] }
                val dominoIndex = IntArray(w * h); heads.forEachIndexed { k, c -> dominoIndex[c] = k; dominoIndex[mate[c]] = k }
                // Une solution : on donne à chaque domino une valeur compatible, au hasard.
                val solution = IntArray(w * h)
                for (c in heads.shuffled(random)) {
                    val options = listOf(1, 2, 0, 0).shuffled(random)
                    for (v in options) {
                        val a = sign(v, true); val b = sign(v, false)
                        val okA = a == NEUTRAL || around(w, h, c).none { it != mate[c] && solution[it] == a }
                        val okB = b == NEUTRAL || around(w, h, mate[c]).none { it != c && solution[it] == b }
                        if (okA && okB) { solution[c] = a; solution[mate[c]] = b; break }
                    }
                    if (solution[c] == 0) { solution[c] = NEUTRAL; solution[mate[c]] = NEUTRAL }
                }
                val rowPlus = IntArray(h) { r -> (0 until w).count { solution[r * w + it] == PLUS } }
                val rowMinus = IntArray(h) { r -> (0 until w).count { solution[r * w + it] == MINUS } }
                val colPlus = IntArray(w) { c -> (0 until h).count { solution[it * w + c] == PLUS } }
                val colMinus = IntArray(w) { c -> (0 until h).count { solution[it * w + c] == MINUS } }
                // Solveur : une variable par domino.
                fun cellSign(g: IntArray, i: Int): Int {
                    val v = g[dominoIndex[i]]
                    return if (v < 0) 0 else sign(v, headOf[i] == i)
                }
                val unique = GridSearch.count(heads.size, 3, IntArray(heads.size) { -1 }, budget = 2_000) { g, k ->
                    val c = heads[k]
                    for (i in intArrayOf(c, mate[c])) {
                        val s = cellSign(g, i)
                        if ((s == PLUS || s == MINUS) && around(w, h, i).any { it != mate[i] && cellSign(g, it) == s }) return@count false
                        val r = i / w; val col = i % w
                        for ((sg, rowT, colT) in listOf(Triple(PLUS, rowPlus, colPlus), Triple(MINUS, rowMinus, colMinus))) {
                            var on = 0; var open = 0
                            for (x in 0 until w) { val t = cellSign(g, r * w + x); if (t == sg) on++ else if (t == 0) open++ }
                            if (on > rowT[r] || on + open < rowT[r]) return@count false
                            on = 0; open = 0
                            for (y in 0 until h) { val t = cellSign(g, y * w + col); if (t == sg) on++ else if (t == 0) open++ }
                            if (on > colT[col] || on + open < colT[col]) return@count false
                        }
                    }
                    true
                } == 1
                if (unique) return MagnetsState(w, h, mate, rowPlus, rowMinus, colPlus, colMinus, IntArray(w * h), solution)
            }
        }
    }
}
