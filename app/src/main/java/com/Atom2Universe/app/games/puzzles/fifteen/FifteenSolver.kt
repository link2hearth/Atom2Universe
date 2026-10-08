package com.Atom2Universe.app.games.puzzles.fifteen

/**
 * Résoudre le taquin comme on l'apprend, étape par étape, pour la démonstration :
 * - les rangées du haut une à une, sauf les deux dernières : plaque par plaque de gauche à
 *   droite, les deux dernières plaques d'une rangée ensemble (c'est là qu'il faut « l'astuce » de
 *   les amener côte à côte avant de les rentrer) ;
 * - les deux dernières rangées colonne par colonne, les deux plaques d'une colonne ensemble ;
 * - le dernier carré de 2 × 2 pour finir, le trou en bas à droite.
 *
 * Chaque étape est le plus court chemin (recherche en largeur) qui amène ses plaques à leur
 * place sans jamais bouger celles déjà rangées. Une étape ne suit que ses propres plaques et le
 * trou : au plus trois positions, quelques milliers d'états, c'est instantané.
 */
object FifteenSolver {

    /** Un coup de la démonstration : l'état d'après, et les plaques que l'étape est en train de placer. */
    class Step(val state: FifteenState, val focus: IntArray)

    fun solve(start: FifteenState): List<Step>? {
        val w = start.w; val h = start.h
        if (w < 2 || h < 2) return null
        val locked = BooleanArray(w * h)
        var state = start
        val steps = ArrayList<Step>()

        fun target(x: Int, y: Int) = y * w + x
        fun stage(cells: List<Int>, gapGoal: Int) {
            val tiles = cells.map { it + 1 }.toIntArray()
            val moves = bfs(state, tiles, cells.toIntArray(), gapGoal, locked) ?: return
            for (m in moves) {
                state = state.slide(m) ?: return
                steps.add(Step(state, tiles))
            }
            cells.forEach { locked[it] = true }
        }

        for (y in 0 until h - 2) {
            for (x in 0 until w - 2) stage(listOf(target(x, y)), -1)
            stage(listOf(target(w - 2, y), target(w - 1, y)), -1)
        }
        for (x in 0 until w - 2) stage(listOf(target(x, h - 2), target(x, h - 1)), -1)
        // Le dernier carré : ses trois plaques, et le trou dans le coin.
        stage(listOf(target(w - 2, h - 2), target(w - 1, h - 2), target(w - 2, h - 1)), target(w - 1, h - 1))
        return if (state.isSolved) steps else null
    }

    /**
     * Le plus court enchaînement de coups (les cases que l'on fait glisser dans le trou) qui
     * amène les plaques [tiles] sur [goals], sans toucher aux cases [locked].
     */
    private fun bfs(state: FifteenState, tiles: IntArray, goals: IntArray, gapGoal: Int, locked: BooleanArray): List<Int>? {
        val w = state.w; val n = state.w * state.h
        val k = tiles.size
        fun key(pos: IntArray, gap: Int): Long {
            var v = gap.toLong()
            for (p in pos) v = v * n + p
            return v
        }
        val startPos = IntArray(k) { state.tiles.indexOf(tiles[it]) }
        val startGap = state.gap
        fun done(pos: IntArray, gap: Int) = pos.contentEquals(goals) && (gapGoal < 0 || gap == gapGoal)
        if (done(startPos, startGap)) return emptyList()
        val parent = HashMap<Long, Pair<Long, Int>>()
        val queue = ArrayDeque<Pair<IntArray, Int>>()
        val startKey = key(startPos, startGap)
        parent[startKey] = -1L to -1
        queue.add(startPos to startGap)
        while (queue.isNotEmpty()) {
            val (pos, gap) = queue.removeFirst()
            val here = key(pos, gap)
            val gx = gap % w; val gy = gap / w
            for (o in intArrayOf(if (gy > 0) gap - w else -1, if (gx < w - 1) gap + 1 else -1,
                    if (gy < state.h - 1) gap + w else -1, if (gx > 0) gap - 1 else -1)) {
                if (o < 0 || locked[o]) continue
                // La plaque en o glisse dans le trou : si c'est une des nôtres, elle se déplace.
                val next = pos.copyOf()
                for (t in 0 until k) if (next[t] == o) next[t] = gap
                val nk = key(next, o)
                if (parent.containsKey(nk)) continue
                parent[nk] = here to o
                if (done(next, o)) {
                    val moves = ArrayList<Int>()
                    var c = nk
                    while (true) {
                        val (p, m) = parent.getValue(c)
                        if (m < 0) break
                        moves.add(m); c = p
                    }
                    return moves.reversed()
                }
                queue.add(next to o)
            }
        }
        return null
    }
}
