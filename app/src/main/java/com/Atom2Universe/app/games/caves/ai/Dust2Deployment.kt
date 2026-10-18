package com.Atom2Universe.app.games.caves.ai

import com.Atom2Universe.app.games.caves.world.Dust2Map
import com.Atom2Universe.app.games.caves.world.MapPoint
import kotlin.random.Random

/** Une escouade par site et une à CT mid ; uniquement les sols reliés au départ. */
internal class Dust2Deployment(private val grid: NavGrid, spawn: MapPoint) {
    private val sectors: List<List<Int>>

    init {
        val reachable = BooleanArray(grid.nodeCount)
        val queue = IntArray(grid.nodeCount)
        var read = 0
        var write = 0
        val start = grid.nodeAt(spawn.x, spawn.y, spawn.z)
        if (start >= 0) {
            reachable[start] = true
            queue[write++] = start
        }
        while (read < write) {
            val n = queue[read++]
            for (edge in grid.edgeStart[n] until grid.edgeStart[n + 1]) {
                val next = grid.edgeTarget[edge]
                if (!reachable[next]) {
                    reachable[next] = true
                    queue[write++] = next
                }
            }
        }
        sectors = Dust2Map.DEFENSE_SECTORS.map { sector ->
            (0 until grid.nodeCount).filter { n ->
                reachable[n] && sector.contains(grid.nodeX[n], grid.nodeY[n], grid.nodeZ[n])
            }
        }
    }

    fun chooseSquads(size: Int, rng: Random): List<IntArray> =
        SquadSpawn.fromZones(grid, sectors, sectors.size, size, rng)
}
