package com.Atom2Universe.app.games.puzzles.samegame

import com.Atom2Universe.app.games.kit.checkPuzzleWork
import kotlin.random.Random

/**
 * « Same Game » de Simon Tatham : retirer un groupe d'au moins deux cases voisines de même
 * couleur ; ce qui est au-dessus tombe, les colonnes vides se resserrent vers la gauche. Il faut
 * vider la grille. Un groupe de n cases rapporte (n − 2)² points.
 *
 * [cells] : -1 pour une case vide, sinon la couleur. Ligne 0 en haut.
 */
class SameGameState(val w: Int, val h: Int, val colours: Int, val cells: IntArray, val score: Int) {

    val isSolved: Boolean get() = cells.all { it < 0 }
    val hasMoves: Boolean get() = cells.indices.any { i -> cells[i] >= 0 &&
        ((i % w + 1 < w && cells[i + 1] == cells[i]) || (i + w < cells.size && cells[i + w] == cells[i])) }
    val remaining: Int get() = cells.count { it >= 0 }

    fun groupOf(index: Int): List<Int> {
        val colour = cells.getOrElse(index) { -1 }
        if (colour < 0) return emptyList()
        val seen = BooleanArray(cells.size)
        val out = ArrayList<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(index); seen[index] = true
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            out.add(i)
            val x = i % w; val y = i / w
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1,
                    if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                if (n >= 0 && !seen[n] && cells[n] == colour) { seen[n] = true; stack.add(n) }
            }
        }
        return out
    }

    fun remove(index: Int): SameGameState? {
        val group = groupOf(index)
        if (group.size < 2) return null
        return removeGroup(group)
    }

    private fun removeGroup(group: List<Int>): SameGameState {
        val next = cells.copyOf()
        for (i in group) next[i] = -1
        // Gravité, puis colonnes resserrées.
        val packed = IntArray(w * h) { -1 }
        var column = 0
        for (x in 0 until w) {
            var row = h - 1
            for (y in h - 1 downTo 0) {
                val value = next[y * w + x]
                if (value >= 0) { packed[row * w + column] = value; row-- }
            }
            if (row < h - 1) column++
        }
        val gain = (group.size - 2) * (group.size - 2)
        return SameGameState(w, h, colours, packed, score + gain)
    }

    /** La démonstration : la grille vidée coup par coup si c'est possible, sinon le mieux trouvé. */
    fun demo(): List<SameGameState> {
        val out = ArrayList<SameGameState>()
        var s = this
        for (i in bestPath(this, 200_000).first) { s = s.remove(i) ?: break; out.add(s) }
        return out
    }

    fun encode(): String = "$w,$h,$colours,$score:" + cells.joinToString("") { if (it < 0) "." else it.toString() }

    companion object {
        fun decode(text: String): SameGameState? {
            val (head, body) = text.split(':').takeIf { it.size == 2 } ?: return null
            val (w, h, colours, score) = head.split(',').map { it.toInt() }
            if (body.length != w * h) return null
            return SameGameState(w, h, colours, IntArray(w * h) { if (body[it] == '.') -1 else body[it] - '0' }, score)
        }

        /**
         * Une grille au hasard qu'une recherche sait vider entièrement (comme Tatham, qui ne
         * propose que des grilles faisables). La recherche essaie d'abord les petits groupes,
         * mémorise les impasses et s'arrête à un budget ; au-delà d'une seconde, on garde la
         * dernière grille tirée.
         */
        fun generate(w: Int, h: Int, colours: Int, random: Random): SameGameState {
            val deadline = System.nanoTime() + 1_500_000_000L
            var last: SameGameState
            do {
                checkPuzzleWork()
                last = SameGameState(w, h, colours, IntArray(w * h) { random.nextInt(colours) }, 0)
                if (clearable(last, 60_000)) return last
            } while (System.nanoTime() < deadline)
            return last
        }

        fun clearable(start: SameGameState, budget: Int): Boolean = bestPath(start, budget).second

        /**
         * La meilleure suite de coups trouvée dans le budget : celle qui vide la grille si la
         * recherche en trouve une (le booléen), sinon celle qui laisse le moins de cases. Les petits
         * groupes d'abord ; les impasses sont mémorisées. Un coup est une case du groupe à retirer.
         */
        fun bestPath(start: SameGameState, budget: Int): Pair<List<Int>, Boolean> {
            val dead = HashSet<String>()
            var nodes = 0
            val path = ArrayList<Int>()
            var best: List<Int> = emptyList()
            var bestLeft = start.remaining
            fun search(s: SameGameState): Boolean {
                checkPuzzleWork()
                if (s.isSolved) { best = ArrayList(path); return true }
                if (s.remaining < bestLeft) { bestLeft = s.remaining; best = ArrayList(path) }
                if (++nodes > budget) return false
                val key = buildString(s.cells.size) { for (value in s.cells) append((value + 1).toChar()) }
                if (key in dead) return false
                // Une couleur réduite à une seule case ne s'en ira jamais.
                val counts = IntArray(s.colours)
                for (c in s.cells) if (c >= 0) counts[c]++
                if (counts.any { it == 1 }) { dead.add(key); return false }
                val seen = BooleanArray(s.cells.size)
                val groups = ArrayList<List<Int>>()
                for (i in s.cells.indices) {
                    if (seen[i] || s.cells[i] < 0) continue
                    val g = s.groupOf(i)
                    g.forEach { seen[it] = true }
                    if (g.size >= 2) groups.add(g)
                }
                for (g in groups.sortedBy { it.size }) {
                    path.add(g[0])
                    if (search(s.removeGroup(g))) return true
                    path.removeAt(path.size - 1)
                    if (nodes > budget) return false
                }
                dead.add(key)
                return false
            }
            val cleared = search(start)
            return best to cleared
        }
    }
}
