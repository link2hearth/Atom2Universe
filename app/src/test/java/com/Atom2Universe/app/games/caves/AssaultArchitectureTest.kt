package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.world.A2Map
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.BuiltinMaps
import com.Atom2Universe.app.games.caves.world.PartialBlockModel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AssaultArchitectureTest {
    @Test
    fun pitHasContinuousSupportedRingsAndEightInnerCorners() {
        val map = BuiltinMaps.arena()
        for ((left, north, y) in listOf(Triple(62, 18, 3), Triple(63, 19, 2))) {
            val right = 139 - left
            val south = 119 - north
            for (z in north..south) for (x in left..right) {
                if (x != left && x != right && z != north && z != south) continue
                val bridgeAbutment = y == 3 && z in 57..62 && (x == left || x == right)
                if (!bridgeAbutment) assertEquals(2406.toShort(), map.blockAt(x, y, z))
                assertTrue("Unsupported step at $x,$y,$z", map.blockAt(x, y - 1, z) != AIR)
            }
            for (x in listOf(left, right)) for (z in listOf(north, south)) {
                val mask = PartialBlockModel.connectedMask(map.metaAt(x, y, z)) { dx, dz ->
                    if (map.blockAt(x + dx, y, z + dz) == 2406.toShort())
                        map.metaAt(x + dx, y, z + dz) else null
                }
                assertEquals("Inner corner at $x,$y,$z", 3, Integer.bitCount(mask))
                assertEquals(7, PartialBlockModel.boxes(map.metaAt(x, y, z), mask = mask).size)
            }
        }
    }
    @Test
    fun stairsKeepTheirOrientationAfterMapExport() {
        val map = BuiltinMaps.arena()
        val bytes = ByteArrayOutputStream().also { map.write(it) }.toByteArray()
        val restored = A2Map.read(ByteArrayInputStream(bytes))
        for ((point, direction) in listOf(
            Triple(44, 4, 29) to 0, Triple(54, 4, 55) to 2,
            Triple(59, 4, 59) to 1, Triple(80, 4, 59) to 3,
        )) {
            val (x, y, z) = point
            assertEquals(2406.toShort(), restored.blockAt(x, y, z))
            assertEquals(direction.toByte(), restored.metaAt(x, y, z))
        }
    }

    @Test
    fun firingSlitPassesCrouchedEyesAndMuzzleButProtectsTheStandingHead() {
        val map = BuiltinMaps.arena()
        val world = object : SolidGrid {
            override fun isSolid(x: Int, y: Int, z: Int) = map.blockAt(x, y, z) != AIR
            override fun blocksSight(x: Int, y: Int, z: Int,
                x0: Double, y0: Double, z0: Double, dx: Double, dy: Double, dz: Double): Boolean {
                val block = map.blockAt(x, y, z)
                if (block == AIR) return false
                if (block != 2506.toShort() && block != 2406.toShort()) return true
                return PartialBlockModel.intersect(map.metaAt(x, y, z),
                    x0 - x, y0 - y, z0 - z, dx, dy, dz, 1.0, block == 2506.toShort()) != null
            }
        }
        // Firing ledge top = 4.5; crouched eyes = feet + 1.02, muzzle = eyes - .05.
        val eye = 4.5 + 1.62 - .6
        fun clear(y: Double) = LineOfSight.isClear(32.5, y, 40.5, 34.5, y, 40.5, world)
        assertEquals(2506.toShort(), map.blockAt(32, 4, 40))
        assertTrue(clear(eye))
        assertTrue(clear(eye - .05))
        assertFalse(clear(4.5 + 1.62))
        assertFalse(clear(4.8))
        // The cap stops shots in its lower half, but leaves the upper half empty.
        assertTrue(clear(6.75))
        // Visibility is reciprocal through the slit.
        assertTrue(LineOfSight.isClear(34.5, 5.7, 40.5, 32.5, eye, 40.5, world))
    }
}