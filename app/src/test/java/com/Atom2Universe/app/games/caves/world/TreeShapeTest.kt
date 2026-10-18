package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TreeShapeTest {
    private fun tree(type: String, seed: Int): Map<Triple<Int, Int, Int>, Short> {
        val blocks = linkedMapOf<Triple<Int, Int, Int>, Short>()
        TreeShape.generate(type, Random(seed)) { x, y, z, id, onlyAir ->
            val p = Triple(x, y, z)
            if (!onlyAir || p !in blocks) blocks[p] = id
        }
        return blocks
    }

    @Test fun crownsStayWithinChunkReplayBoundsAndHaveSupport() {
        for (type in listOf("oak", "pink", "birch", "sapin", "redwood", "jungle", "darkwood") + GrandTrees.types) {
            repeat(30) { seed ->
                val blocks = tree(type, seed)
                for ((p, id) in blocks) {
                    assertTrue(p.first in -TreeShape.REACH..TreeShape.REACH)
                    assertTrue(p.third in -TreeShape.REACH..TreeShape.REACH)
                    assertTrue(p.second in 1..TreeShape.HEIGHT)
                    if (isLeaf(id)) assertTrue("Unsupported $type/$seed at $p",
                        LeafSupport.supported(p.first, p.second, p.third) { x, y, z -> blocks[Triple(x, y, z)] ?: AIR })
                }
            }
        }
    }

    @Test fun giantTrunksAreSolidAndBaobabRoomHasAnEntrance() {
        for (type in listOf("giant_redwood", "giant_pine", "broad_oak")) {
            val blocks = tree(type, 17)
            for (y in 1..3) for (z in 0..1) for (x in 0..1)
                assertTrue(isWood(blocks[Triple(x, y, z)] ?: AIR))
        }
        repeat(30) { seed ->
            val blocks = tree("baobab", seed)
            for (y in 1..2) {
                assertFalse(blocks.containsKey(Triple(0, y, -2)))
                for (z in -1..1) for (x in -1..1)
                    assertFalse(blocks.containsKey(Triple(x, y, z)))
                assertTrue(isWood(blocks[Triple(2, y, 0)] ?: AIR))
            }
        }
    }
    @Test fun leafPathsStopAtSixFacesAndRespectUnloadedNeighbors() {
        val line = (0..6).associate { Triple(it, 0, 0) to if (it == 6) WOOD else LEAVES }
        assertTrue(LeafSupport.supported(0, 0, 0) { x, y, z -> line[Triple(x, y, z)] ?: AIR })
        assertFalse(LeafSupport.supported(-1, 0, 0) { x, y, z -> line[Triple(x, y, z)] ?: AIR })
        assertFalse(LeafSupport.supported(0, 0, 0) { _, _, _ -> AIR })
        assertTrue(LeafSupport.supported(0, 0, 0) { _, _, _ -> null })
    }

    @Test fun replayIsDeterministicAndFruitBelongsToFruitTrees() {
        assertEquals(tree("oak", 17), tree("oak", 17))
        assertTrue(tree("pink", 4).values.contains(TreeShape.CHERRY_LEAF))
        assertFalse(tree("sapin", 4).values.any { it in TreeShape.APPLE_LEAF..TreeShape.CHERRY_LEAF })
        val withoutWood = tree("oak", 9).filterValues { !isWood(it) }
        for ((p, _) in withoutWood) assertFalse(LeafSupport.supported(p.first, p.second, p.third) { x, y, z ->
            withoutWood[Triple(x, y, z)] ?: AIR
        })
    }
}
