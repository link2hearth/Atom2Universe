package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ColdLandscapeTest {
    private fun recipe(kind: Int, seed: Int): Map<Triple<Int, Int, Int>, Pair<Short, Byte>> {
        val blocks = linkedMapOf<Triple<Int, Int, Int>, Pair<Short, Byte>>()
        ColdLandscape.generate(kind, Random(seed), { _, _ -> 80 }) { x, y, z, id, meta ->
            blocks[Triple(x, y, z)] = id to meta
        }
        return blocks
    }

    @Test fun recipesAreBoundedAndReplayIdentically() {
        for (kind in 0..7) repeat(20) { seed ->
            val result = recipe(kind, seed)
            assertEquals(result, recipe(kind, seed))
            assertTrue(result.isNotEmpty())
            for ((p, block) in result) {
                assertTrue(p.first in -7..7 && p.third in -7..7)
                assertTrue(p.second in 79..90)
                assertNotEquals(AIR, block.first)
                assertNotEquals(WATER, block.first)
            }
        }
    }

    @Test fun pondHasAContinuousBedAndFrozenCenter() {
        val blocks = recipe(3, 42)
        for (z in -2..2) for (x in -2..2) {
            assertEquals(CLAY, blocks[Triple(x, 79, z)]?.first)
            assertTrue(blocks[Triple(x, 80, z)]?.first in listOf(ICE, ColdLandscape.CRACKED_ICE))
        }
    }

    @Test fun fallenTrunkHasASplitRoofAndHorizontalGrain() {
        val blocks = recipe(1, 17)
        assertFalse(blocks.containsKey(Triple(0, 82, 0)))
        assertTrue(blocks[Triple(0, 81, 0)]?.second in listOf(1.toByte(), 2.toByte()))
        assertFalse(blocks.containsKey(Triple(0, 83, 0)))
    }
}
