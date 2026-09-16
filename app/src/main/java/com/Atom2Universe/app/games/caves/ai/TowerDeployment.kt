package com.Atom2Universe.app.games.caves.ai

import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.OfficeTowerMap
import kotlin.random.Random

/** Garnison créée une fois par manche, répartie en escouades par niveau puis par aile. Aucun renfort. */
internal class TowerDeployment(private val grid: NavGrid, spawn: MapPoint) {
    private val zones = Array(OfficeTowerMap.PLAYABLE_LEVELS) { Array(ROOMS) { ArrayList<Int>() } }

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

    /**
     * Une escouade par pièce, réparties sur tous les niveaux : deux par étage pour une garnison de
     * douze. Les hommes d'une même escouade démarrent groupés, c'est ce qui leur donne un secteur
     * à tenir et un côté d'où arriver quand la radio les envoie.
     */
    fun chooseSquads(count: Int, size: Int, rng: Random): List<IntArray> {
        val result = ArrayList<IntArray>(count)
        val used = HashSet<Int>()
        for (i in 0 until count) {
            val level = i % zones.size
            val first = rng.nextInt(ROOMS)
            for (offset in 0 until ROOMS) {
                val room = (first + offset) % ROOMS
                if (!used.add(level * ROOMS + room)) continue
                val group = SquadSpawn.grab(grid, zones[level][room], size, rng)
                if (group.size >= 2) { result.add(group); break }
            }
        }
        return result
    }

    private companion object {
        /** Deux ailes de quatre pièces par niveau. */
        const val ROOMS = 8
    }
}
