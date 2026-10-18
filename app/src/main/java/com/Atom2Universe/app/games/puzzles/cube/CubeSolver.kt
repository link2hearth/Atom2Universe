package com.Atom2Universe.app.games.puzzles.cube

/**
 * Le solveur du Cube, pour la démonstration : une recherche en largeur, donc le plus court
 * chemin. Un état tient dans un entier de 64 bits : la case du cube (6 bits), ses six faces
 * (6 bits) et les cases colorées du damier (49 au plus). Au-delà de [budget] états, on abandonne.
 */
object CubeSolver {

    private val DX = intArrayOf(0, 1, 0, -1)
    private val DY = intArrayOf(-1, 0, 1, 0)

    /** Les directions (0 haut, 1 droite, 2 bas, 3 gauche) du plus court chemin, ou null. */
    fun solve(start: CubeState, budget: Int = 1_500_000): List<Int>? {
        val w = start.w; val h = start.h
        if (w * h > 49) return null
        fun pack(s: CubeState): Long {
            var v = 0L
            for (i in 0 until w * h) if (s.blue[i]) v = v or (1L shl i)
            for (f in 0 until 6) if (s.faces[f]) v = v or (1L shl (49 + f))
            return v or ((s.y * w + s.x).toLong() shl 55)
        }
        fun unpack(v: Long): CubeState {
            val pos = (v ushr 55).toInt()
            return CubeState(w, h, pos % w, pos / w, BooleanArray(6) { v and (1L shl (49 + it)) != 0L },
                BooleanArray(w * h) { v and (1L shl it) != 0L }, 0)
        }
        if (start.isSolved) return emptyList()
        val parent = HashMap<Long, Long>()
        val dirOf = HashMap<Long, Int>()
        val first = pack(start)
        parent[first] = first
        val queue = ArrayDeque<Long>()
        queue.add(first)
        while (queue.isNotEmpty()) {
            val here = queue.removeFirst()
            val s = unpack(here)
            for (d in 0..3) {
                val n = s.roll(DX[d], DY[d]) ?: continue
                val k = pack(n)
                if (parent.containsKey(k)) continue
                parent[k] = here; dirOf[k] = d
                if (n.isSolved) {
                    val dirs = ArrayList<Int>()
                    var c = k
                    while (c != first) { dirs.add(dirOf.getValue(c)); c = parent.getValue(c) }
                    return dirs.reversed()
                }
                if (parent.size > budget) return null
                queue.add(k)
            }
        }
        return null
    }

    /**
     * Pour les grands damiers : une recherche guidée (A* pondéré). Elle explore d'abord les états
     * qui semblent proches du but — peu de faces encore à colorer, une case colorée tout près —
     * et trouve vite un chemin, un peu plus long que le plus court.
     */
    fun solveGuided(start: CubeState, budget: Int = 1_500_000): List<Int>? {
        val w = start.w; val h = start.h
        if (w * h > 49) return null
        fun pack(s: CubeState): Long {
            var v = 0L
            for (i in 0 until w * h) if (s.blue[i]) v = v or (1L shl i)
            for (f in 0 until 6) if (s.faces[f]) v = v or (1L shl (49 + f))
            return v or ((s.y * w + s.x).toLong() shl 55)
        }
        fun estimate(s: CubeState): Int {
            val missing = s.faces.count { !it }
            if (missing == 0) return 0
            var near = Int.MAX_VALUE
            for (i in 0 until w * h) if (s.blue[i]) near = minOf(near, kotlin.math.abs(i % w - s.x) + kotlin.math.abs(i / w - s.y))
            return missing * 3 + (if (near == Int.MAX_VALUE) 0 else near)
        }
        class Node(val s: CubeState, val key: Long, val g: Int, val f: Int)
        val parent = HashMap<Long, Long>()
        val dirOf = HashMap<Long, Int>()
        val best = HashMap<Long, Int>()
        val open = java.util.PriorityQueue<Node>(compareBy { it.f })
        val first = pack(start)
        parent[first] = first; best[first] = 0
        open.add(Node(start, first, 0, estimate(start)))
        while (open.isNotEmpty()) {
            val n = open.poll()!!
            if (n.s.isSolved) {
                val dirs = ArrayList<Int>()
                var c = n.key
                while (c != first) { dirs.add(dirOf.getValue(c)); c = parent.getValue(c) }
                return dirs.reversed()
            }
            if (n.g > (best[n.key] ?: Int.MAX_VALUE)) continue
            for (d in 0..3) {
                val m = n.s.roll(DX[d], DY[d]) ?: continue
                val k = pack(m)
                val g = n.g + 1
                if (g >= (best[k] ?: Int.MAX_VALUE)) continue
                best[k] = g; parent[k] = n.key; dirOf[k] = d
                open.add(Node(m, k, g, g + 2 * estimate(m)))
            }
            if (best.size > budget) return null
        }
        return null
    }

    fun demo(start: CubeState): List<CubeState>? {
        val dirs = solve(start, budget = 300_000) ?: solveGuided(start) ?: return null
        val out = ArrayList<CubeState>()
        var s = start
        for (d in dirs) { s = s.roll(DX[d], DY[d]) ?: return null; out.add(s) }
        return out
    }
}
