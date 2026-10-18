package com.Atom2Universe.app.games.puzzles.flood

import kotlin.random.Random

/**
 * « Flood » de Simon Tatham : la tache du coin haut-gauche prend la couleur choisie et avale
 * les cases voisines de cette couleur. Tout remplir d'une seule couleur avant d'épuiser les coups.
 */
class FloodState(val size: Int, val colours: Int, val cells: IntArray, val moves: Int, val limit: Int) {

    val isSolved: Boolean get() = cells.all { it == cells[0] }
    val isLost: Boolean get() = !isSolved && moves >= limit

    /** Les cases de la tache du coin. */
    fun region(): BooleanArray = regionOf(size, cells)

    fun flood(colour: Int): FloodState? {
        if (colour == cells[0] || colour !in 0 until colours) return null
        return FloodState(size, colours, fill(size, cells, colour), moves + 1, limit)
    }

    /**
     * La démonstration : la fin de partie jouée par le joueur appliqué (la couleur qui fait le
     * plus grossir la tache, deux coups devant). La limite de coups ne compte plus : c'est une
     * leçon, pas une victoire.
     */
    fun demo(): List<FloodState> {
        val out = ArrayList<FloodState>()
        var s = FloodState(size, colours, cells, moves, Int.MAX_VALUE)
        while (!s.isSolved) { s = s.flood(greedyColour(size, colours, s.cells)) ?: break; out.add(s) }
        // La limite affichée s'allonge juste assez pour que la démonstration aille au bout.
        val cap = maxOf(limit, out.lastOrNull()?.moves ?: limit)
        return out.map { FloodState(size, colours, it.cells, it.moves, cap) }
    }

    fun encode(): String = "$size,$colours,$moves,$limit:" + cells.joinToString("")

    companion object {
        fun decode(text: String): FloodState? {
            val (head, body) = text.split(':').takeIf { it.size == 2 } ?: return null
            val (size, colours, moves, limit) = head.split(',').map { it.toInt() }
            if (body.length != size * size) return null
            return FloodState(size, colours, IntArray(size * size) { body[it] - '0' }, moves, limit)
        }

        fun regionOf(size: Int, cells: IntArray): BooleanArray {
            val inside = BooleanArray(cells.size)
            val stack = ArrayDeque<Int>()
            stack.add(0); inside[0] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % size; val y = i / size
                for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < size - 1) i + 1 else -1,
                        if (y > 0) i - size else -1, if (y < size - 1) i + size else -1)) {
                    if (n >= 0 && !inside[n] && cells[n] == cells[0]) { inside[n] = true; stack.add(n) }
                }
            }
            return inside
        }

        fun fill(size: Int, cells: IntArray, colour: Int): IntArray {
            val inside = regionOf(size, cells)
            return IntArray(cells.size) { if (inside[it]) colour else cells[it] }
        }

        /**
         * Le nombre de coups d'un joueur appliqué : à chaque coup, la couleur qui fait le plus
         * grossir la tache en regardant deux coups devant. C'est un plafond atteignable, donc
         * la limite (ce nombre plus la marge du préréglage) laisse toujours la partie faisable.
         */
        fun greedyMoves(size: Int, colours: Int, start: IntArray): Int {
            var cells = start
            var count = 0
            while (!cells.all { it == cells[0] }) {
                cells = fill(size, cells, greedyColour(size, colours, cells))
                count++
            }
            return count
        }

        /** La couleur qui fait le plus grossir la tache, en regardant deux coups devant. */
        fun greedyColour(size: Int, colours: Int, cells: IntArray): Int {
            var best = -1
            var bestScore = -1
            for (c in 0 until colours) {
                if (c == cells[0]) continue
                val a = fill(size, cells, c)
                var score = regionOf(size, a).count { it } * 4
                var look = 0
                for (d in 0 until colours) if (d != c) look = maxOf(look, regionOf(size, fill(size, a, d)).count { it })
                score += look
                if (a.all { it == a[0] }) score = Int.MAX_VALUE
                if (score > bestScore) { bestScore = score; best = c }
            }
            return best
        }

        fun generate(size: Int, colours: Int, leeway: Int, random: Random): FloodState {
            val cells = IntArray(size * size) { random.nextInt(colours) }
            return FloodState(size, colours, cells, 0, greedyMoves(size, colours, cells) + leeway)
        }
    }
}
