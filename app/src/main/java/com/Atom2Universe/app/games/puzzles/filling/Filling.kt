package com.Atom2Universe.app.games.puzzles.filling

import com.Atom2Universe.app.games.kit.Hints
import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Filling » de Simon Tatham (Fillomino) : remplir la grille de chiffres pour que chaque bloc
 * de cases voisines portant le même chiffre compte exactement ce nombre de cases.
 *
 * [givens] : chiffres donnés (0 = vide). [values] : chiffres de la grille (donnés compris).
 */
class FillingState(val w: Int, val h: Int, val givens: IntArray, val values: IntArray, val solution: IntArray) {

    fun set(i: Int, v: Int): FillingState? {
        if (givens[i] != 0 || values[i] == v) return null
        val next = values.copyOf(); next[i] = v
        return FillingState(w, h, givens, next, solution)
    }

    /** Astuce : un chiffre faux corrigé, sinon une case vide remplie ; il devient donné. */
    fun hint(): FillingState? {
        val i = Hints.pick(w * h, { givens[it] == 0 && values[it] != 0 && values[it] != solution[it] }, { values[it] == 0 }) ?: return null
        val v = values.copyOf(); v[i] = solution[i]
        val g = givens.copyOf(); g[i] = solution[i]
        return FillingState(w, h, g, v, solution)
    }

    fun setAll(cells: Collection<Int>, v: Int): FillingState? {
        val next = values.copyOf()
        var changed = false
        for (i in cells) if (givens[i] == 0 && next[i] != v) { next[i] = v; changed = true }
        return if (changed) FillingState(w, h, givens, next, solution) else null
    }

    /** Le bloc de [i] (cases voisines de même chiffre) et s'il touche encore une case vide. */
    fun component(i: Int): Pair<List<Int>, Boolean> = componentOf(w, h, IntArray(w * h) { values[it] - 1 }, i)

    fun errors(): Set<Int> {
        val out = HashSet<Int>()
        for (i in values.indices) {
            if (values[i] == 0 || i in out) continue
            val (cells, open) = component(i)
            if (cells.size > values[i] || (!open && cells.size < values[i])) out.addAll(cells)
        }
        return out
    }

    fun complete(i: Int): Boolean = values[i] != 0 && component(i).first.size == values[i]

    val isSolved: Boolean get() = values.all { it != 0 } && errors().isEmpty()

    fun encode() = "$w,$h:" + givens.joinToString(",") + ":" + values.joinToString(",") + ":" + solution.joinToString(",")

    companion object {
        fun decode(text: String): FillingState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return FillingState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].split(',').map { it.toInt() }.toIntArray(),
                p[3].split(',').map { it.toInt() }.toIntArray())
        }

        private fun around(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            return listOfNotNull(if (x > 0) i - 1 else null, if (x < w - 1) i + 1 else null,
                if (y > 0) i - w else null, if (y < h - 1) i + w else null)
        }

        /** g : -1 inconnu, sinon chiffre − 1. */
        fun componentOf(w: Int, h: Int, g: IntArray, i: Int): Pair<List<Int>, Boolean> {
            val v = g[i]
            val seen = HashSet<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i); seen.add(i)
            var open = false
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                for (n in around(w, h, c)) {
                    if (g[n] < 0) open = true
                    else if (g[n] == v && seen.add(n)) stack.add(n)
                }
            }
            return seen.toList() to open
        }

        fun ok(w: Int, h: Int, g: IntArray, c: Int): Boolean {
            for (k in around(w, h, c) + c) {
                if (g[k] < 0) continue
                val (cells, open) = componentOf(w, h, g, k)
                val size = g[k] + 1
                if (cells.size > size || (!open && cells.size < size)) return false
            }
            return true
        }

        fun generate(w: Int, h: Int, maxSize: Int, random: Random): FillingState {
            while (true) {
                val region = IntArray(w * h) { -1 }
                var id = 0
                for (start in (0 until w * h).shuffled(random)) {
                    if (region[start] >= 0) continue
                    val target = 1 + random.nextInt(maxSize)
                    val cells = arrayListOf(start); region[start] = id
                    while (cells.size < target) {
                        val options = cells.flatMap { around(w, h, it) }.filter { region[it] < 0 }
                        if (options.isEmpty()) break
                        val pick = options.random(random); region[pick] = id; cells.add(pick)
                    }
                    id++
                }
                // Deux blocs voisins de même taille se fondraient : on les fusionne, tant que ça tient.
                var sizes = IntArray(id) { k -> region.count { it == k } }
                var merged = true
                var guard = 0
                while (merged && guard++ < 200) {
                    merged = false
                    loop@ for (i in region.indices) for (n in around(w, h, i)) {
                        val a = region[i]; val b = region[n]
                        if (a != b && sizes[a] == sizes[b]) {
                            for (j in region.indices) if (region[j] == b) region[j] = a
                            sizes[a] += sizes[b]; sizes[b] = 0
                            merged = true
                            break@loop
                        }
                    }
                }
                if (merged || sizes.any { it > 9 }) continue
                val solution = IntArray(w * h) { sizes[region[it]] }
                val g = IntArray(w * h) { solution[it] - 1 }
                for (i in (0 until w * h).shuffled(random)) {
                    val keep = g[i]; g[i] = -1
                    if (GridSearch.count(w * h, 9, g, budget = 300) { gg, c -> ok(w, h, gg, c) } != 1) g[i] = keep
                }
                val givens = IntArray(w * h) { if (g[it] >= 0) g[it] + 1 else 0 }
                return FillingState(w, h, givens, givens.copyOf(), solution)
            }
        }
    }
}
