package com.Atom2Universe.app.games.caves.ai

import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.OfficeTowerMap
import kotlin.random.Random

/** Garnison créée une fois par manche, répartie par niveau puis par aile. Aucun renfort. */
internal class TowerDeployment(private val grid: NavGrid, spawn: MapPoint) {
    private val zones = Array(OfficeTowerMap.PLAYABLE_LEVELS) { Array(8) { ArrayList<Int>() } }

    init {
        // Écarter les surfaces décoratives isolées et les pièces inaccessibles au joueur.
        val reachable = BooleanArray(grid.nodeCount)
        val queue = IntArray(grid.nodeCount)
        var read = 0; var write = 0
        val start = grid.nodeAt(spawn.x, spawn.y, spawn.z)
        if (start >= 0) { reachable[start] = true; queue[write++] = start }
        while (read < write) {
            val n = queue[read++]
            for (e in grid.edgeStart[n] until grid.edgeStart[n + 1]) {
                val next = grid.edgeTarget[e]
                if (!reachable[next]) { reachable[next] = true; queue[write++] = next }
            }
        }
        for (n in 0 until grid.nodeCount) {
            if (!reachable[n]) continue
            val y = grid.nodeY[n]
            if (y < 3 || (y - 3) % 6 != 0) continue
            val level = (y - 3) / 6
            if (level !in zones.indices) continue
            val x = grid.nodeX[n]; val z = grid.nodeZ[n]
            val wing = when (x) { in 4..61 -> 0; in 78..135 -> 1; else -> continue }
            val room = when (z) {
                in 4..27 -> 0; in 33..51 -> 1; in 68..86 -> 2; in 92..115 -> 3
                else -> continue
            }
            val dx = x - spawn.x; val dz = z - spawn.z
            if (level == 0 && dx * dx + dz * dz < 24 * 24) continue
            zones[level][wing * 4 + room].add(n)
        }
    }

    fun choose(count: Int, rng: Random): IntArray {
        val chosen = ArrayList<Int>(count)
        for (i in 0 until count) {
            val level = i % zones.size
            val preferredRoom = (i / zones.size) % 8
            var selected = -1
            for (offset in 0 until 8) {
                val pool = zones[level][(preferredRoom + offset) % 8]
                if (pool.isEmpty()) continue
                val start = rng.nextInt(pool.size)
                for (j in pool.indices) {
                    val n = pool[(start + j) % pool.size]
                    if (chosen.none { old ->
                        val dx = grid.nodeX[n] - grid.nodeX[old]
                        val dz = grid.nodeZ[n] - grid.nodeZ[old]
                        grid.nodeY[n] == grid.nodeY[old] && dx * dx + dz * dz < 16
                    }) { selected = n; break }
                }
                if (selected >= 0) break
            }
            if (selected >= 0) chosen.add(selected)
        }
        return chosen.toIntArray()
    }
}
