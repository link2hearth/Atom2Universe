package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterFlowRoutingTest {
    private fun route(holes: Set<Pair<Int, Int>> = emptySet(),
                      walls: Set<Pair<Int, Int>> = emptySet(), remaining: Int = 8): Int =
        WaterFlowRouting.directions(0, 1, 0, remaining) { x, y, z ->
            if (y == 0) { if (x to z in holes) AIR else STONE }
            else if (x to z in walls) STONE else AIR
        }

    @Test fun closestDropWins() {
        assertEquals(1, route(setOf(1 to 0, -3 to 0)))
    }

    @Test fun equalDropsSplitFlow() {
        assertEquals(3, route(setOf(2 to 0, -2 to 0)))
    }

    @Test fun wallRequiresShortDetour() {
        // Le trou à l'est est accessible en quatre pas par le nord ou le sud.
        assertEquals(12, route(setOf(2 to 0), setOf(1 to 0)))
    }

    @Test fun flatGroundKeepsSpreading() {
        assertEquals(15, route())
    }

    @Test fun gravityAndExhaustedReachPreventSidewaysFlow() {
        assertEquals(0, route(setOf(0 to 0)))
        assertEquals(0, route(remaining = 0))
    }

    @Test fun unreachableDropDoesNotSteerLastFlowCell() {
        assertEquals(15, route(setOf(2 to 0), remaining = 1))
    }

    @Test fun unloadedCellsAreNotHoles() {
        assertEquals(14, WaterFlowRouting.directions(0, 1, 0, 8) { x, y, _ ->
            if (x > 0) null else if (y == 0) STONE else AIR
        })
    }

    @Test fun searchRemainsLocalAndBounded() {
        var reads = 0
        WaterFlowRouting.directions(0, 1, 0, 8) { x, y, z ->
            reads++
            assertTrue(kotlin.math.abs(x) + kotlin.math.abs(z) <= 4)
            assertTrue(y == 0 || y == 1)
            if (y == 0) STONE else AIR
        }
        assertTrue("Unexpected search cost: $reads reads", reads <= 650)
    }
}
