package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.IntList
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.ai.TowerDeployment
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.GLASS
import com.Atom2Universe.app.games.caves.world.OfficeTowerMap
import com.Atom2Universe.app.games.caves.world.TORCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfficeTowerMapTest {
    private val map = OfficeTowerMap.create()
    private val world = object : SolidGrid {
        override fun isSolid(x: Int, y: Int, z: Int): Boolean {
            val b = map.blockAt(x, y, z)
            return b != AIR && b != TORCH
        }
    }
    private val grid = NavGrid.build(map.sizeX, map.sizeY, map.sizeZ, world)
    private val spawn = map.spawnsA.single()

    private fun reachable(): BooleanArray {
        val seen = BooleanArray(grid.nodeCount)
        val queue = IntArray(grid.nodeCount)
        var read = 0; var write = 0
        val start = grid.nodeAt(spawn.x, spawn.y, spawn.z)
        seen[start] = true; queue[write++] = start
        while (read < write) {
            val n = queue[read++]
            for (e in grid.edgeStart[n] until grid.edgeStart[n + 1]) {
                val next = grid.edgeTarget[e]
                if (!seen[next]) { seen[next] = true; queue[write++] = next }
            }
        }
        return seen
    }

    @Test
    fun everyPostHoldsItsWholeSquad() {
        val sizes = TowerDeployment(grid, spawn).poolSizes()
        for ((i, post) in OfficeTowerMap.POSTS.withIndex())
            assertTrue("Poste $post : ${sizes[i]} cases", sizes[i] >= post.size * 3)
    }

    @Test
    fun theRouteClimbsFloorByFloorToTheHelipad() {
        val start = grid.nodeAt(spawn.x, spawn.y, spawn.z)
        val goal = grid.nodeAt(18, OfficeTowerMap.feet(5) + 1, 18)
        assertTrue(start >= 0 && goal >= 0)
        val out = IntList()
        assertTrue(PathFinder(grid).findPath(start, goal, out))
        // Le chemin ne redescend jamais d'un étage : chaque escalier mène plus haut, un seul par étage.
        var level = 0
        val levels = ArrayList<Int>()
        for (i in 0 until out.size) {
            val y = grid.nodeY[out[i]]
            val l = ((y - 3) / 6).coerceAtMost(OfficeTowerMap.FLOORS)
            if ((y - 3) % 6 == 0 && l != level) { assertEquals(level + 1, l); level = l; levels += l }
        }
        assertEquals(listOf(1, 2, 3, 4, 5), levels)
        dumpPlans(out)
    }

    /** Plans ASCII de chaque niveau, avec le plus court chemin, pour relire le dessin. */
    private fun dumpPlans(path: IntList) {
        val seen = reachable()
        val onPath = HashSet<Int>()
        for (i in 0 until path.size) onPath += path[i]
        val out = StringBuilder()
        for (level in 0..OfficeTowerMap.FLOORS) {
            val feet = OfficeTowerMap.feet(level)
            out.append("=== Niveau $level (pieds y = $feet) ===\n")
            for (z in 0 until map.sizeZ) {
                for (x in 0 until map.sizeX) {
                    val post = OfficeTowerMap.POSTS.firstOrNull { it.x == x && it.z == z && (it.y - feet) in 0..5 }
                    val node = (0..5).map { grid.nodeAt(x, feet + it, z) }.firstOrNull { it >= 0 && seen[it] } ?: -1
                    val high = map.blockAt(x, feet + 1, z)
                    val lowB = map.blockAt(x, feet, z)
                    val c = when {
                        spawn.x == x && spawn.z == z && level == 0 -> '@'
                        post != null -> 'P'
                        node >= 0 && node in onPath -> '*'
                        lowB == 2406.toShort() || high == 2406.toShort() -> 'S'
                        node >= 0 && grid.nodeY[node] > feet -> '^'
                        node >= 0 -> '.'
                        high == GLASS -> '"'
                        high != AIR && high != TORCH -> '#'
                        lowB != AIR && lowB != TORCH -> '+'
                        else -> ' '
                    }
                    out.append(c)
                }
                out.append('\n')
            }
        }
        File("build/tower-plans.txt").apply { parentFile?.mkdirs() }.writeText(out.toString())
    }
}
