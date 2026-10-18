package com.Atom2Universe.app.games.gameoflife

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class NeighborCounterTest {

    private fun NeighborCounter.toMap(): Map<Long, Int> = buildMap {
        for (i in 0 until capacity) {
            val key = keyAt(i)
            if (key != NeighborCounter.EMPTY) put(key, countAt(i))
        }
    }

    @Test
    fun countsMatchHashMapAcrossGrowthAndClear() {
        val counter = NeighborCounter()
        val random = Random(42)
        repeat(3) { round ->
            counter.clear()
            val expected = HashMap<Long, Int>()
            // Assez de clés distinctes pour forcer plusieurs agrandissements de la table (4096 au départ).
            repeat(40_000 + round * 10_000) {
                val x = random.nextInt(-300, 300)
                val y = random.nextInt(-300, 300)
                val key = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
                counter.increment(key)
                expected[key] = (expected[key] ?: 0) + 1
            }
            assertEquals(expected, counter.toMap())
        }
    }

    @Test
    fun negativeCoordinatesDoNotCollideWithEmpty() {
        val counter = NeighborCounter()
        val key = (-1L shl 32) or (0L and 0xFFFFFFFFL)
        counter.increment(key)
        counter.increment(key)
        assertEquals(mapOf(key to 2), counter.toMap())
    }
}
