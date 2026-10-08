package com.Atom2Universe.app.games.puzzles.pattern

import kotlin.random.Random

/**
 * « Pattern » de Simon Tatham (logimage, nonogramme) : noircir les cases pour que chaque ligne et
 * chaque colonne montre ses blocs, dans l'ordre et de la longueur donnés en marge.
 *
 * [cells] : 0 inconnu, 1 noirci, 2 croix (« blanc », noté par le joueur). [picture] : l'image
 * dessinée du mode « Images » ([PatternPictures]), ou -1 pour une grille tirée au hasard.
 */
class PatternState(
    val w: Int, val h: Int, val rows: List<IntArray>, val cols: List<IntArray>, val cells: IntArray,
    val picture: Int = -1, solved: IntArray? = null,
) {
    /** La solution (1 noir, 0 blanc), unique puisque chaque grille se résout ligne par ligne. */
    val solution: IntArray by lazy {
        solved ?: if (picture >= 0) PatternPictures.all[picture].let { p -> IntArray(w * h) { if (p.filled(it)) 1 else 0 } }
        else lineSolution(w, h, rows, cols) ?: IntArray(w * h)
    }

    /** Astuce : corrige une case fausse, sinon révèle une case encore indécise (noire ou croix). */
    fun hint(): PatternState? {
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val wrong = cells.indices.filter { cells[it] != 0 && cells[it] != want(it) }
        val open = cells.indices.filter { cells[it] == 0 }
        val i = wrong.ifEmpty { open }.randomOrNull() ?: return null
        return with(mapOf(i to want(i)))
    }

    /**
     * Pour l'aide : vrai si le [j]-ième bloc de la ligne est exactement à sa place — ses cases
     * noircies, et celles qui l'encadrent pas.
     */
    fun blockDone(horizontal: Boolean, k: Int, j: Int): Boolean {
        val len = if (horizontal) w else h
        fun at(i: Int) = if (horizontal) k * w + i else i * w + k
        var start = -1
        var found = -1
        var i = 0
        while (i < len) {
            if (solution[at(i)] == 1) {
                start = i
                while (i < len && solution[at(i)] == 1) i++
                if (++found == j) {
                    if ((start until i).any { cells[at(it)] != 1 }) return false
                    if (start > 0 && cells[at(start - 1)] == 1) return false
                    return !(i < len && cells[at(i)] == 1)
                }
            } else i++
        }
        return false
    }

    fun line(horizontal: Boolean, k: Int): IntArray =
        if (horizontal) IntArray(w) { cells[k * w + it] } else IntArray(h) { cells[it * w + k] }

    fun lineDone(horizontal: Boolean, k: Int): Boolean =
        blocks(line(horizontal, k).map { if (it == 1) 1 else 0 }.toIntArray()).contentEquals(if (horizontal) rows[k] else cols[k])

    val isSolved: Boolean get() = (0 until h).all { lineDone(true, it) } && (0 until w).all { lineDone(false, it) }

    fun with(changes: Map<Int, Int>): PatternState {
        val next = cells.copyOf()
        for ((i, v) in changes) next[i] = v
        return PatternState(w, h, rows, cols, next, picture, solution)
    }

    fun encode() = "$w,$h:" + rows.joinToString(";") { it.joinToString(",") } + ":" +
        cols.joinToString(";") { it.joinToString(",") } + ":" + cells.joinToString("") + ":" + picture

    companion object {
        fun decode(text: String): PatternState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            fun clues(s: String) = s.split(';').map { l -> if (l.isEmpty()) IntArray(0) else l.split(',').map { it.toInt() }.toIntArray() }
            return PatternState(w, h, clues(p[1]), clues(p[2]), p[3].map { it - '0' }.toIntArray(), p[4].toInt())
        }

        fun blocks(line: IntArray): IntArray {
            val out = ArrayList<Int>()
            var run = 0
            for (v in line) if (v == 1) run++ else if (run > 0) { out.add(run); run = 0 }
            if (run > 0) out.add(run)
            return out.toIntArray()
        }

        /**
         * Ce qu'on peut déduire d'une ligne : [known] vaut -1 inconnu, 0 blanc, 1 noir. Rend la
         * ligne complétée, ou null si elle est contradictoire. Toutes les façons de placer les
         * blocs sont parcourues par programmation dynamique.
         */
        fun solveLine(known: IntArray, clue: IntArray): IntArray? {
            val len = known.size
            val k = clue.size
            val memo = Array(len + 2) { arrayOfNulls<Boolean>(k + 1) }
            fun canPlaceAt(i: Int, size: Int): Boolean {
                if (i + size > len) return false
                for (j in i until i + size) if (known[j] == 0) return false
                return i + size == len || known[i + size] != 1
            }
            fun feasible(i: Int, b: Int): Boolean {
                if (i >= len) return b == k
                memo[i][b]?.let { return it }
                val ok = if (b == k) (i until len).none { known[it] == 1 }
                else (known[i] != 1 && feasible(i + 1, b)) ||
                    (canPlaceAt(i, clue[b]) && feasible(i + clue[b] + 1, b + 1))
                memo[i][b] = ok
                return ok
            }
            fun feasibleAfter(i: Int, b: Int) = feasible(i, b)
            if (!feasibleAfter(0, 0)) return null
            val canFill = BooleanArray(len)
            val canEmpty = BooleanArray(len)
            val seen = Array(len + 2) { BooleanArray(k + 1) }
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(0 to 0)
            while (stack.isNotEmpty()) {
                val (i, b) = stack.removeLast()
                if (i >= len || seen[i][b]) continue
                seen[i][b] = true
                if (b == k) { for (j in i until len) canEmpty[j] = true; continue }
                if (known[i] != 1 && feasibleAfter(i + 1, b)) { canEmpty[i] = true; stack.add(i + 1 to b) }
                val size = clue[b]
                if (canPlaceAt(i, size) && feasibleAfter(i + size + 1, b + 1)) {
                    for (j in i until i + size) canFill[j] = true
                    if (i + size < len) canEmpty[i + size] = true
                    stack.add(i + size + 1 to b + 1)
                }
            }
            return IntArray(len) { j ->
                when {
                    canFill[j] && !canEmpty[j] -> 1
                    canEmpty[j] && !canFill[j] -> 0
                    else -> known[j]
                }
            }
        }

        /** Vrai si les déductions ligne par ligne suffisent à tout trouver. */
        fun lineSolvable(w: Int, h: Int, rows: List<IntArray>, cols: List<IntArray>): Boolean =
            lineSolution(w, h, rows, cols) != null

        /** La grille trouvée par déductions ligne par ligne, ou null si elles ne suffisent pas. */
        fun lineSolution(w: Int, h: Int, rows: List<IntArray>, cols: List<IntArray>): IntArray? {
            val g = IntArray(w * h) { -1 }
            var changed = true
            while (changed) {
                changed = false
                for (r in 0 until h) {
                    val out = solveLine(IntArray(w) { g[r * w + it] }, rows[r]) ?: return null
                    for (c in 0 until w) if (g[r * w + c] != out[c]) { g[r * w + c] = out[c]; changed = true }
                }
                for (c in 0 until w) {
                    val out = solveLine(IntArray(h) { g[it * w + c] }, cols[c]) ?: return null
                    for (r in 0 until h) if (g[r * w + c] != out[r]) { g[r * w + c] = out[r]; changed = true }
                }
            }
            return if (g.none { it < 0 }) g else null
        }

        /** La grille vierge d'une image dessinée. */
        fun fromPicture(index: Int): PatternState {
            val p = PatternPictures.all[index]
            val rows = (0 until p.h).map { r -> blocks(IntArray(p.w) { if (p.filled(r * p.w + it)) 1 else 0 }) }
            val cols = (0 until p.w).map { c -> blocks(IntArray(p.h) { if (p.filled(it * p.w + c)) 1 else 0 }) }
            return PatternState(p.w, p.h, rows, cols, IntArray(p.w * p.h), index)
        }

        fun generate(w: Int, h: Int, random: Random): PatternState {
            while (true) {
                // Un peu de lissage : des taches plutôt que du bruit, comme Tatham.
                var grid = IntArray(w * h) { if (random.nextFloat() < 0.55f) 1 else 0 }
                repeat(1) {
                    grid = IntArray(w * h) { i ->
                        val x = i % w; val y = i / w
                        var s = 0; var n = 0
                        for (dy in -1..1) for (dx in -1..1) {
                            val nx = x + dx; val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) { s += grid[ny * w + nx]; n++ }
                        }
                        if (s * 2 > n) 1 else if (s * 2 < n) 0 else grid[i]
                    }
                }
                val rows = (0 until h).map { r -> blocks(IntArray(w) { grid[r * w + it] }) }
                val cols = (0 until w).map { c -> blocks(IntArray(h) { grid[it * w + c] }) }
                if (lineSolvable(w, h, rows, cols)) return PatternState(w, h, rows, cols, IntArray(w * h))
            }
        }
    }
}
