package com.Atom2Universe.app.games.puzzles.inertia

import kotlin.random.Random

/**
 * « Inertia » de Simon Tatham : une bille glisse dans l'une des huit directions et ne s'arrête
 * que sur une case d'arrêt ou devant un mur. Elle ramasse les gemmes qu'elle traverse ; une
 * mine la détruit. Ramasser toutes les gemmes.
 *
 * [cells] : [EMPTY], [WALL], [STOP], [GEM], [MINE]. [ball] : case de la bille ; [dead] : la
 * bille a sauté sur une mine (la partie est perdue, on peut annuler).
 */
class InertiaState(val w: Int, val h: Int, val cells: IntArray, val ball: Int, val moves: Int, val dead: Boolean) {

    val gems: Int get() = cells.count { it == GEM }
    val isSolved: Boolean get() = !dead && gems == 0

    /** Le coup dans la direction [d] (0 = haut, puis sens horaire), ou null s'il ne bouge pas. */
    fun roll(d: Int): InertiaState? {
        if (dead) return null
        val next = cells.copyOf()
        var pos = ball
        var moved = false
        while (true) {
            val x = pos % w + DX[d]; val y = pos / w + DY[d]
            if (x !in 0 until w || y !in 0 until h || next[y * w + x] == WALL) break
            pos = y * w + x
            moved = true
            when (next[pos]) {
                GEM -> next[pos] = EMPTY
                MINE -> return InertiaState(w, h, next, pos, moves + 1, true)
                STOP -> break
            }
        }
        return if (moved) InertiaState(w, h, next, pos, moves + 1, false) else null
    }

    /**
     * La démonstration : à chaque fois, le plus court enchaînement de glissades (sans mine) qui
     * ramasse au moins une gemme, jusqu'à la dernière. Les gemmes ne gênent pas le mouvement, et
     * le générateur garantit qu'on peut toujours revenir au départ : aller au plus près ne bloque
     * jamais. null si la bille a sauté (il faut d'abord annuler) ou si rien n'est atteignable.
     */
    fun demo(): List<InertiaState>? {
        if (dead) return null
        val steps = ArrayList<InertiaState>()
        var s = this
        while (s.gems > 0) {
            // Recherche en largeur sur les positions de la bille.
            val from = IntArray(w * h) { -1 }
            val dirOf = IntArray(w * h) { -1 }
            val queue = ArrayDeque<Int>()
            from[s.ball] = s.ball; queue.add(s.ball)
            var goal: Pair<Int, Int>? = null // (position de départ du dernier coup, direction)
            search@ while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                val probe = InertiaState(w, h, s.cells, p, 0, false)
                for (d in 0..7) {
                    val r = probe.roll(d) ?: continue
                    if (r.dead) continue
                    if (r.gems < s.gems) { goal = p to d; break@search }
                    if (from[r.ball] < 0) { from[r.ball] = p; dirOf[r.ball] = d; queue.add(r.ball) }
                }
            }
            val (last, lastDir) = goal ?: return null
            val dirs = ArrayList<Int>()
            var c = last
            while (c != s.ball) { dirs.add(dirOf[c]); c = from[c] }
            dirs.reverse(); dirs.add(lastDir)
            for (d in dirs) { s = s.roll(d) ?: return null; steps.add(s) }
        }
        return steps
    }

    fun encode() = "$w,$h,$ball,$moves,${if (dead) 1 else 0}:" + cells.joinToString("")

    companion object {
        const val EMPTY = 0
        const val WALL = 1
        const val STOP = 2
        const val GEM = 3
        const val MINE = 4
        val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

        fun decode(text: String): InertiaState {
            val (a, b) = text.split(':')
            val (w, h, ball, moves, dead) = a.split(',').map { it.toInt() }
            return InertiaState(w, h, b.map { it - '0' }.toIntArray(), ball, moves, dead == 1)
        }

        /**
         * Une grille au hasard, gardée si toute position atteignable permet de revenir au départ
         * (l'ordre de ramassage ne peut alors jamais bloquer) ; les gemmes qu'aucun trajet ne
         * traverse sont retirées, comme le fait le générateur de Tatham.
         */
        fun generate(w: Int, h: Int, random: Random): InertiaState {
            while (true) {
                val cells = IntArray(w * h) {
                    when (random.nextInt(20)) {
                        0, 1 -> WALL
                        2, 3, 4, 5 -> STOP
                        6, 7, 8, 9, 10 -> GEM
                        11, 12 -> MINE
                        else -> EMPTY
                    }
                }
                val start = (0 until w * h).filter { cells[it] == STOP || cells[it] == EMPTY }.randomOrNull(random) ?: continue
                cells[start] = STOP
                // Les gemmes ne gênent pas les déplacements : on étudie la grille sans elles.
                val bare = IntArray(w * h) { if (cells[it] == GEM) EMPTY else cells[it] }
                fun moveFrom(p: Int, d: Int): Pair<Int, List<Int>>? {
                    var pos = p
                    val path = ArrayList<Int>()
                    while (true) {
                        val x = pos % w + DX[d]; val y = pos / w + DY[d]
                        if (x !in 0 until w || y !in 0 until h || bare[y * w + x] == WALL) break
                        pos = y * w + x
                        if (bare[pos] == MINE) return null
                        path.add(pos)
                        if (bare[pos] == STOP) break
                    }
                    return if (path.isEmpty()) null else pos to path
                }
                val reach = HashSet<Int>()
                val stack = ArrayDeque<Int>()
                reach.add(start); stack.add(start)
                val edges = HashMap<Int, MutableList<Int>>()
                val crossed = HashSet<Int>()
                while (stack.isNotEmpty()) {
                    val p = stack.removeLast()
                    for (d in 0..7) {
                        val (q, path) = moveFrom(p, d) ?: continue
                        edges.getOrPut(p) { ArrayList() }.add(q)
                        crossed.addAll(path)
                        if (reach.add(q)) stack.add(q)
                    }
                }
                // Retour au départ possible depuis chaque position atteinte.
                val back = HashSet<Int>()
                val rev = HashMap<Int, MutableList<Int>>()
                for ((p, qs) in edges) for (q in qs) rev.getOrPut(q) { ArrayList() }.add(p)
                stack.add(start); back.add(start)
                while (stack.isNotEmpty()) {
                    val q = stack.removeLast()
                    for (p in rev[q].orEmpty()) if (back.add(p)) stack.add(p)
                }
                if (!back.containsAll(reach)) continue
                for (i in cells.indices) if (cells[i] == GEM && i !in crossed) cells[i] = EMPTY
                if (cells.count { it == GEM } < w * h / 8) continue
                return InertiaState(w, h, cells, start, 0, false)
            }
        }
    }
}
