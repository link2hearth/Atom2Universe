package com.Atom2Universe.app.games.caves.world

/** Recherche locale du premier trou : au plus quatre pas, sans charger de chunks. */
internal object WaterFlowRouting {
    const val SEARCH_DISTANCE = 4
    private const val WIDTH = SEARCH_DISTANCE * 2 + 1
    private const val CENTER = SEARCH_DISTANCE + SEARCH_DISTANCE * WIDTH
    private val dx = intArrayOf(1, -1, 0, 0)
    private val dz = intArrayOf(0, 0, 1, -1)

    private class Scratch {
        val queue = IntArray(WIDTH * WIDTH)
        val distance = IntArray(WIDTH * WIDTH)
    }
    private val scratch = ThreadLocal.withInitial { Scratch() }

    /** Bits +X, -X, +Z, -Z. null désigne une cellule non chargée, jamais du vide. */
    fun directions(x: Int, y: Int, z: Int, remaining: Int,
                   block: (Int, Int, Int) -> Short?): Int {
        if (remaining <= 0) return 0
        val below = block(x, y - 1, z) ?: return 0
        if (below == AIR || below == WATER_FLOW) return 0 // La gravité passe avant tout.
        val limit = minOf(SEARCH_DISTANCE, remaining)
        val work = scratch.get()!!
        var available = 0
        var selected = 0
        var shortest = Int.MAX_VALUE
        for (direction in 0..3) {
            val first = block(x + dx[direction], y, z + dz[direction])
            if (first != AIR && first != WATER_FLOW) continue
            available = available or (1 shl direction)
            work.distance.fill(-1)
            work.distance[CENTER] = 0 // Ne jamais rebrousser chemin par la cellule d'origine.
            val start = CENTER + dx[direction] + dz[direction] * WIDTH
            var head = 0
            var tail = 1
            work.queue[0] = start
            work.distance[start] = 1
            var cost = Int.MAX_VALUE
            while (head < tail) {
                val cell = work.queue[head++]
                val distance = work.distance[cell]
                val cx = cell % WIDTH
                val cz = cell / WIDTH
                val wx = x + cx - SEARCH_DISTANCE
                val wz = z + cz - SEARCH_DISTANCE
                val support = block(wx, y - 1, wz)
                if (support == AIR || support == WATER_FLOW) {
                    cost = distance
                    break // BFS : première descente = chemin le plus court.
                }
                if (support == null || distance >= limit || distance >= shortest) continue
                for (next in 0..3) {
                    val nx = cx + dx[next]
                    val nz = cz + dz[next]
                    if (nx !in 0 until WIDTH || nz !in 0 until WIDTH) continue
                    val index = nx + nz * WIDTH
                    if (work.distance[index] >= 0) continue
                    work.distance[index] = distance + 1
                    val target = block(x + nx - SEARCH_DISTANCE, y, z + nz - SEARCH_DISTANCE)
                    if (target == AIR || target == WATER_FLOW) work.queue[tail++] = index
                }
            }
            if (cost < shortest) {
                shortest = cost
                selected = 1 shl direction
            } else if (cost == shortest && cost != Int.MAX_VALUE) {
                selected = selected or (1 shl direction)
            }
        }
        // Sur un terrain plat sans descente visible, une nappe peut encore s'étendre.
        return if (shortest == Int.MAX_VALUE) available else selected
    }
}
