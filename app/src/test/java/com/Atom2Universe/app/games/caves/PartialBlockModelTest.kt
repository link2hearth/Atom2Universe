package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.world.PartialBlockModel
import org.junit.Assert.*
import org.junit.Test

class PartialBlockModelTest {
    private val dx = intArrayOf(0, 1, 0, -1)
    private val dz = intArrayOf(1, 0, -1, 0)

    @Test
    fun cornersWorkInEveryDirectionAndForUpsideDownStairs() {
        for (half in listOf(0, 4)) for (direction in 0..3) for (turn in listOf(1, 3)) {
            val meta = (half + direction).toByte()
            val neighbor = (half + (direction + turn) % 4).toByte()
            val outer = PartialBlockModel.connectedMask(meta) { x, z ->
                if (x == dx[direction] && z == dz[direction]) neighbor else null
            }
            val inner = PartialBlockModel.connectedMask(meta) { x, z ->
                if (x == -dx[direction] && z == -dz[direction]) neighbor else null
            }
            assertEquals(5, PartialBlockModel.boxes(meta, mask = outer).size)
            assertEquals(7, PartialBlockModel.boxes(meta, mask = inner).size)
            assertEquals(6, PartialBlockModel.boxes(meta,
                mask = PartialBlockModel.connectedMask(meta) { _, _ -> null }).size)
            // Collision and visible surface agree at every quarter-cell centre.
            for (x in 0..1) for (y in 0..1) for (z in 0..1) {
                val cells = PartialBlockModel.boxes(meta, mask = outer)
                val occupied = cells.any { it.x == x * .5f && it.y == y * .5f && it.z == z * .5f }
                val hit = PartialBlockModel.intersect(meta, x * .5 + .25, y * .5 + .25,
                    z * .5 + .25, 0.0, 1.0, 0.0, .01, mask = outer)
                assertEquals(occupied, hit != null)
            }
        }
    }

    @Test
    fun adjacentRowsStayStraightAndOppositeHalvesDoNotConnect() {
        val guarded = PartialBlockModel.connectedMask(0) { x, z ->
            when {
                x == 0 && z == 1 -> 1.toByte()
                x == -1 && z == 0 -> 0.toByte()
                else -> null
            }
        }
        assertEquals(6, PartialBlockModel.boxes(0, mask = guarded).size)
        val oppositeHalf = PartialBlockModel.connectedMask(0) { _, _ -> 5.toByte() }
        assertEquals(6, PartialBlockModel.boxes(0, mask = oppositeHalf).size)
    }

    @Test
    fun slabLightingUsesTheEmptyHalfWithoutPassingThroughTheSolidRoof() {
        val lower = PartialBlockModel.boxes(0, slab = true)
        val upper = PartialBlockModel.boxes(4, slab = true)
        assertTrue(PartialBlockModel.hasOpenBoundary(lower, 0))
        assertFalse(PartialBlockModel.hasOpenBoundary(lower, 1))
        assertFalse(PartialBlockModel.hasOpenBoundary(upper, 0))
        assertTrue(PartialBlockModel.hasOpenBoundary(upper, 1))
        for (face in 2..5) {
            assertTrue(PartialBlockModel.hasOpenBoundary(lower, face))
            assertTrue(PartialBlockModel.hasOpenBoundary(upper, face))
        }
        for (meta in 0..7) {
            val cells = PartialBlockModel.boxes(meta.toByte())
            assertFalse(PartialBlockModel.hasOpenBoundary(cells, if (meta < 4) 1 else 0))
        }
    }

    private val shaft = com.Atom2Universe.app.games.caves.node.BlockDef(9804, "shaft", "", "", "", null, null, null, null,
        0, 1f, false, true, false, false, false, 0, "", "functional", 0f, 0f, shaft = true)

    @Test
    fun shaftIsAThinRodAlongItsLogAxis() {
        // Same meta as logs: 0 = vertical, 1 = along X, 2 = along Z.
        for ((meta, axis) in listOf(0 to 1, 1 to 0, 2 to 2)) {
            assertEquals(axis, PartialBlockModel.shaftAxis(meta.toByte()))
            val rod = PartialBlockModel.boxes(shaft, meta.toByte()).single()
            val size = floatArrayOf(rod.width, rod.height, rod.depth)
            val low = floatArrayOf(rod.x, rod.y, rod.z)
            for (a in 0..2) {
                assertEquals(if (a == axis) 1f else 6f / 16f, size[a], 1e-6f)
                assertEquals(if (a == axis) 0f else 5f / 16f, low[a], 1e-6f)
            }
            assertEquals(6, PartialBlockModel.faces(shaft, meta.toByte()).size)
        }
        // A ray through the centre hits the rod; one through a corner of the cell passes beside it.
        assertNotNull(PartialBlockModel.intersect(shaft, 1, .5, 1.5, .5, 0.0, -1.0, 0.0, 2.0))
        assertNull(PartialBlockModel.intersect(shaft, 1, .1, 1.5, .1, 0.0, -1.0, 0.0, 2.0))
        assertTrue(shaft.partial)
    }
}
