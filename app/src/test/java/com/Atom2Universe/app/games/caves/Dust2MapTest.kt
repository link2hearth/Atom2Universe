package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.Dust2Deployment
import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.world.A2Map
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.CaveDecorScene
import com.Atom2Universe.app.games.caves.world.Dust2Map
import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.TORCH
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class Dust2MapTest {
    private val map = Dust2Map.create()
    private val scene = CaveDecorScene(map.decor)
    private val solid = SolidGrid { x, y, z ->
        map.blockAt(x, y, z).let { it != AIR && it != TORCH } || scene.occupied(x, y, z)
    }
    private val grid by lazy { NavGrid.build(map.sizeX, map.sizeY, map.sizeZ, solid) }

    private fun reachable(from: MapPoint, allowed: (Int) -> Boolean = { true }): BooleanArray {
        val seen = BooleanArray(grid.nodeCount)
        val start = grid.nodeAt(from.x, from.y, from.z)
        assertTrue("Départ sans sol libre : $from", start >= 0)
        val queue = IntArray(grid.nodeCount)
        var read = 0
        var write = 1
        queue[0] = start
        seen[start] = true
        while (read < write) {
            val n = queue[read++]
            for (e in grid.edgeStart[n] until grid.edgeStart[n + 1]) {
                val next = grid.edgeTarget[e]
                if (!seen[next] && allowed(next)) {
                    seen[next] = true
                    queue[write++] = next
                }
            }
        }
        return seen
    }

    /** Une autre voie ne doit pas masquer un escalier cassé dans le passage testé. */
    private fun passage(a: MapPoint, b: MapPoint, xs: IntRange, zs: IntRange) {
        fun allowed(n: Int) = grid.nodeX[n] in xs && grid.nodeZ[n] in zs && grid.nodeY[n] <= 9
        for ((start, end) in listOf(a to b, b to a)) {
            val seen = reachable(start, ::allowed)
            val target = grid.nodeAt(end.x, end.y, end.z)
            assertTrue("Passage fermé : $start → $end", target >= 0 && seen[target])
        }
    }

    @Test
    fun allThreeApproachesAndTheirStairsWorkInBothDirections() {
        passage(MapPoint(23, 5, 100), MapPoint(23, 7, 74), 17..30, 70..101)
        passage(MapPoint(23, 7, 74), MapPoint(23, 5, 36), 19..25, 35..75)
        passage(MapPoint(23, 7, 76), MapPoint(54, 5, 76), 22..55, 72..80)
        passage(MapPoint(67, 6, 101), MapPoint(76, 6, 60), 64..79, 60..102)
        passage(MapPoint(76, 6, 60), MapPoint(76, 9, 42), 72..79, 41..61)
        passage(MapPoint(91, 5, 113), MapPoint(114, 5, 85), 88..120, 84..114)
        passage(MapPoint(116, 3, 103), MapPoint(116, 5, 89), 113..120, 89..104)
        passage(MapPoint(116, 5, 50), MapPoint(116, 9, 28), 113..120, 27..51)
        passage(MapPoint(54, 5, 54), MapPoint(54, 5, 40), 49..62, 39..55)
        passage(MapPoint(35, 5, 26), MapPoint(47, 5, 26), 35..48, 24..28)
        passage(MapPoint(84, 5, 33), MapPoint(96, 5, 33), 83..98, 30..36)
        passage(MapPoint(96, 5, 33), MapPoint(110, 9, 33), 95..111, 30..36)
    }

    @Test
    fun spawnsAndEveryGarrisonCanReachThePlayerWithoutUsingRoofs() {
        val seen = reachable(Dust2Map.PLAYER_SPAWN) { grid.nodeY[it] <= Dust2Map.SITE_A_HEIGHT }
        for (p in map.spawnsA + map.spawnsB) {
            val n = grid.nodeAt(p.x, p.y, p.z)
            assertTrue("Spawn inaccessible : $p", n >= 0 && seen[n])
        }
        val deployment = Dust2Deployment(grid, Dust2Map.PLAYER_SPAWN)
        repeat(32) { seed ->
            val squads = deployment.chooseSquads(4, Random(seed))
            assertEquals(3, squads.size)
            assertTrue(squads.all { it.size == 4 })
            val soldiers = squads.flatMap { it.toList() }
            assertEquals(12, soldiers.toSet().size)
            assertTrue(soldiers.all { seen[it] })
            assertEquals(setOf(0, 1, 2), squads.map { squad ->
                Dust2Map.DEFENSE_SECTORS.indexOfFirst { sector ->
                    squad.all { sector.contains(grid.nodeX[it], grid.nodeY[it], grid.nodeZ[it]) }
                }
            }.toSet())
        }
    }

    @Test
    fun theSpawnScreenBlocksImmediateMidShotsAndTheUnderpassHasHeadroom() {
        val p = Dust2Map.PLAYER_SPAWN
        assertFalse(LineOfSight.isClear(p.x + .5, p.y + 1.62, p.z + .5,
            56.5, 6.62, 40.5, solid))
        for (x in 89..98) for (z in 31..35) {
            for (y in 5..7) assertEquals(AIR, map.blockAt(x, y, z))
            assertTrue(map.blockAt(x, 8, z) != AIR)
        }
    }

    @Test
    fun mapExportPreservesGeometryStairsAndSpawns() {
        val bytes = ByteArrayOutputStream().also { map.write(it) }.toByteArray()
        val restored = A2Map.read(ByteArrayInputStream(bytes))
        assertEquals(Dust2Map.ID, restored.name)
        assertEquals(map.spawnsA, restored.spawnsA)
        assertEquals(map.spawnsB, restored.spawnsB)
        assertEquals(map.decor, restored.decor)
        assertArrayEquals(map.blocks, restored.blocks)
        assertArrayEquals(map.meta, restored.meta)
    }
}
