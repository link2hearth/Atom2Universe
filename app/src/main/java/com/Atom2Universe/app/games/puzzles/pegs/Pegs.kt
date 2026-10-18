package com.Atom2Universe.app.games.puzzles.pegs

import kotlin.math.abs
import kotlin.random.Random

/**
 * Le solitaire à fiches (« Pegs » de Simon Tatham) : une fiche saute par-dessus une voisine,
 * à l'horizontale ou à la verticale, dans un trou juste derrière ; la fiche sautée s'en va. Il
 * faut finir avec une seule fiche.
 *
 * [cells] : [WALL] hors du plateau, [HOLE] trou vide, [PEG] fiche.
 */
class PegsState(
    val w: Int, val h: Int, val cells: IntArray, val moves: Int,
    /** Les sauts joués depuis le début, en paires (départ, arrivée), pour revenir en arrière. */
    val log: IntArray = IntArray(0),
) {

    val pegs: Int get() = cells.count { it == PEG }
    val isSolved: Boolean get() = pegs == 1

    /** Le saut de [from] vers [to] s'il est permis, sinon null. */
    fun jump(from: Int, to: Int): PegsState? {
        if (from !in cells.indices || to !in cells.indices) return null
        if (cells[from] != PEG || cells[to] != HOLE) return null
        val fx = from % w; val fy = from / w
        val tx = to % w; val ty = to / w
        val ok = (fy == ty && abs(fx - tx) == 2) || (fx == tx && abs(fy - ty) == 2)
        if (!ok) return null
        val mid = ((fy + ty) / 2) * w + (fx + tx) / 2
        if (cells[mid] != PEG) return null
        val next = cells.copyOf()
        next[from] = HOLE; next[mid] = HOLE; next[to] = PEG
        return PegsState(w, h, next, moves + 1, log + intArrayOf(from, to))
    }

    /** Défait le dernier saut (la fiche revient, la fiche sautée réapparaît). */
    fun undoLast(): PegsState? {
        if (log.size < 2) return null
        val from = log[log.size - 2]; val to = log[log.size - 1]
        val mid = ((from / w + to / w) / 2) * w + (from % w + to % w) / 2
        val next = cells.copyOf()
        next[from] = PEG; next[mid] = PEG; next[to] = HOLE
        return PegsState(w, h, next, moves + 1, log.copyOf(log.size - 2))
    }

    /**
     * La démonstration : si la partie peut encore se gagner, la solution jouée saut par saut ;
     * sinon on revient d'abord en arrière jusqu'au dernier moment où elle le pouvait.
     */
    fun demo(): List<PegsState>? {
        val steps = ArrayList<PegsState>()
        var s = this
        // Chaque essai coûte au plus quelques dixièmes de seconde ; au-delà de huit retours en
        // arrière (ou sur l'octogone en début de partie, trop vaste), on renonce : rien n'est payé.
        repeat(9) {
            val solution = PegsSolver.solve(s, budget = 300_000)
            if (solution != null) {
                for (j in solution) { s = s.jump(j.from, j.to) ?: return null; steps.add(s) }
                return steps
            }
            s = s.undoLast() ?: return null
            steps.add(s)
        }
        return null
    }

    /** Les arrivées possibles de la fiche en [from]. */
    fun targets(from: Int): List<Int> {
        val x = from % w; val y = from / w
        return listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2).mapNotNull { (dx, dy) ->
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) null
            else (ny * w + nx).takeIf { jump(from, it) != null }
        }
    }

    val hasMoves: Boolean get() = cells.indices.any { cells[it] == PEG && targets(it).isNotEmpty() }

    fun encode(): String = "$w,$h,$moves:" + cells.joinToString("") + ":" + log.joinToString(",")

    companion object {
        const val WALL = 0
        const val HOLE = 1
        const val PEG = 2

        fun decode(text: String): PegsState? {
            val (head, body, log) = text.split(':').takeIf { it.size == 3 } ?: return null
            val (w, h, moves) = head.split(',').map { it.toInt() }
            if (body.length != w * h) return null
            return PegsState(w, h, IntArray(w * h) { body[it] - '0' }, moves,
                if (log.isEmpty()) IntArray(0) else log.split(',').map { it.toInt() }.toIntArray())
        }

        /** La croix anglaise : trente-trois trous, le centre vide. */
        fun cross(size: Int): PegsState {
            val c = size / 2
            val cells = IntArray(size * size) { i ->
                val dx = abs(i % size - c); val dy = abs(i / size - c)
                when {
                    dx == 0 && dy == 0 -> HOLE
                    dx > 1 && dy > 1 -> WALL
                    else -> PEG
                }
            }
            return PegsState(size, size, cells, 0)
        }

        /**
         * L'octogone européen. Avec le trou au centre il est insoluble (preuve de Tatham par
         * coloriage en trois bandes) : on ôte une des fiches qui laissent la partie faisable.
         */
        fun octagon(random: Random): PegsState {
            val cells = IntArray(49) { i ->
                if (abs(i % 7 - 3) + abs(i / 7 - 3) > 4) WALL else PEG
            }
            val starts = listOf(2 to 0, 4 to 0, 3 to 1, 0 to 2, 3 to 2, 6 to 2, 1 to 3, 2 to 3, 4 to 3,
                5 to 3, 0 to 4, 3 to 4, 6 to 4, 3 to 5, 2 to 6, 4 to 6)
            val (x, y) = starts.random(random)
            cells[y * 7 + x] = HOLE
            return PegsState(7, 7, cells, 0)
        }

        /**
         * Un plateau au hasard, construit à l'envers depuis une seule fiche (méthode de Tatham) :
         * chaque « saut à rebours » ajoute une fiche, en préférant les sauts qui restent dans le
         * plateau déjà tracé. La partie est donc faisable par construction.
         */
        fun random(w: Int, h: Int, random: Random): PegsState {
            while (true) {
                val cells = IntArray(w * h) { WALL }
                cells[random.nextInt(h) * w + random.nextInt(w)] = PEG
                var steps = 0
                while (true) {
                    val options = ArrayList<Triple<Int, Int, Int>>() // fiche, direction, coût
                    for (i in cells.indices) {
                        if (cells[i] != PEG) continue
                        val x = i % w; val y = i / w
                        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                            val x2 = x + 2 * dx; val y2 = y + 2 * dy
                            if (x2 !in 0 until w || y2 !in 0 until h) continue
                            val m = (y + dy) * w + x + dx
                            val e = y2 * w + x2
                            if (cells[m] == PEG || cells[e] == PEG) continue
                            val cost = (if (cells[m] == WALL) 1 else 0) + (if (cells[e] == WALL) 1 else 0)
                            options.add(Triple(i, dy * w + dx, cost))
                        }
                    }
                    val maxCost = if (steps < w * h / 2) 2 else 1
                    val best = (0..maxCost).firstNotNullOfOrNull { c -> options.filter { it.third == c }.takeIf { it.isNotEmpty() } }
                        ?: break
                    val (i, d, _) = best.random(random)
                    cells[i] = HOLE; cells[i + d] = PEG; cells[i + 2 * d] = PEG
                    steps++
                }
                val state = PegsState(w, h, cells, 0)
                if (state.pegs >= w * h / 3) return state
            }
        }
    }
}
