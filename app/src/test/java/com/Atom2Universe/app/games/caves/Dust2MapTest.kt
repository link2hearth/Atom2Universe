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
import com.Atom2Universe.app.games.caves.world.TorchModel
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
        fun allowed(n: Int) = grid.nodeX[n] in xs && grid.nodeZ[n] in zs &&
            grid.nodeY[n] <= Dust2Map.SPAWN_HEIGHT
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
        passage(MapPoint(67, 8, 101), MapPoint(76, 8, 60), 64..79, 60..102)
        passage(MapPoint(76, 8, 60), MapPoint(76, 9, 42), 72..79, 41..61)
        passage(MapPoint(91, 8, 113), MapPoint(114, 5, 85), 88..120, 84..114)
        passage(MapPoint(116, 3, 103), MapPoint(116, 5, 89), 113..120, 89..104)
        passage(MapPoint(116, 5, 50), MapPoint(116, 9, 28), 113..120, 27..51)
        passage(MapPoint(54, 5, 54), MapPoint(54, 5, 40), 49..62, 39..55)
        passage(MapPoint(35, 5, 26), MapPoint(47, 5, 26), 35..48, 24..28)
        passage(MapPoint(84, 5, 33), MapPoint(96, 5, 33), 83..98, 30..36)
        passage(MapPoint(96, 5, 33), MapPoint(110, 9, 33), 95..111, 30..36)
    }

    @Test
    fun raisedSpawnAndSlopingMidHaveContinuousReturnRoutes() {
        passage(MapPoint(52, 11, 126), MapPoint(52, 9, 108), 49..53, 107..127)
        passage(MapPoint(47, 11, 123), MapPoint(25, 5, 123), 24..50, 118..124)
        passage(MapPoint(80, 11, 119), MapPoint(84, 5, 106), 76..101, 106..119)
        passage(MapPoint(55, 9, 108), MapPoint(55, 5, 60), 49..62, 59..109)
        passage(MapPoint(67, 9, 108), MapPoint(67, 8, 101), 64..71, 100..109)
        passage(MapPoint(58, 6, 82), MapPoint(67, 8, 82), 58..68, 81..83)
    }

    @Test
    fun workshopShortcutConnectsMidToLongInBothDirections() {
        // Le rectangle interdit tout détour par A ou par les portes longues.
        passage(MapPoint(58, 5, 70), MapPoint(112, 5, 70), 58..112, 69..71)
        for (x in 63..108) {
            val feet = when (x) {
                63 -> 6
                64 -> 7
                in 65..92 -> 8
                in 93..94 -> 7
                in 95..96 -> 6
                else -> 5
            }
            for (z in 69..71) {
                assertTrue("Sol du raccourci : $x,$feet,$z", solid.isSolid(x, feet - 1, z))
                for (y in feet..feet + 2)
                    assertFalse("Raccourci obstrué : $x,$y,$z", solid.isSolid(x, y, z))
            }
        }
        for ((x, y, facing) in listOf(Triple(63, 5, 1), Triple(64, 6, 1), Triple(65, 7, 1),
                Triple(92, 7, 3), Triple(94, 6, 3), Triple(96, 5, 3))) {
            assertEquals(2406.toShort(), map.blockAt(x, y, 70))
            assertEquals(facing.toByte(), map.metaAt(x, y, 70))
        }
    }

    @Test
    fun everyWallTorchStillHasItsSupportInTheFinishedMap() {
        var count = 0
        for (y in 0 until map.sizeY) for (z in 0 until map.sizeZ) for (x in 0 until map.sizeX) {
            if (map.blockAt(x, y, z) != TORCH) continue
            count++
            val (nx, nz) = TorchModel.normal(map.metaAt(x, y, z))
            assertTrue("Orientation murale manquante : $x,$y,$z", nx != 0 || nz != 0)
            val support = map.blockAt(x - nx, y, z - nz)
            assertTrue("Torche sans mur : $x,$y,$z", support != AIR && support != TORCH)
            assertFalse("Torche masquée par un meuble : $x,$y,$z", scene.occupied(x, y, z))
        }
        assertEquals(10, count)
        // Les quatre anciennes positions suspendues sont à présent vides.
        for (z in listOf(55, 67, 79, 87)) assertEquals(AIR, map.blockAt(20, 10, z))
    }

    @Test
    fun southernTunnelTorchUsesTheWallBeforeTheOpenCourtyard() {
        // La cour a déjà creusé x=16 à partir de z=86 ; z=87 ne peut pas servir d'appui.
        assertEquals(AIR, map.blockAt(16, 10, 87))
        assertEquals(AIR, map.blockAt(17, 10, 87))
        assertTrue(map.blockAt(16, 10, 85) != AIR)
        assertEquals(TORCH, map.blockAt(17, 10, 85))
        assertEquals(1.toByte(), map.metaAt(17, 10, 85))
    }

    @Test
    fun theHeightDifferenceIsInTheWalkableTerrain() {
        fun floor(x: Int, feet: Int, z: Int) {
            assertTrue("Sol manquant : $x,$feet,$z", solid.isSolid(x, feet - 1, z))
            assertFalse("Pieds obstrués : $x,$feet,$z", solid.isSolid(x, feet, z))
            assertFalse("Tête obstruée : $x,$feet,$z", solid.isSolid(x, feet + 1, z))
        }
        floor(62, 11, 129)
        floor(55, 9, 108)
        floor(55, 8, 98)
        floor(55, 7, 90)
        floor(55, 6, 82)
        floor(55, 5, 68)
        floor(67, 8, 82)
        floor(116, 3, 103)
        // Terrasse et auvent ont suivi le nouveau sol ; les objets ne restent pas enterrés.
        for (p in map.decor.filter { it.z > 125f }) {
            val bottom = p.y + p.placement().model.bounds.bottom * p.scale
            assertTrue("Décor sous le départ : ${p.modelId}", bottom >= Dust2Map.SPAWN_HEIGHT - .0001f)
        }
    }

    @Test
    fun spawnsAndEveryGarrisonCanReachThePlayerWithoutUsingRoofs() {
        val seen = reachable(Dust2Map.PLAYER_SPAWN) { grid.nodeY[it] <= Dust2Map.SPAWN_HEIGHT }
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
