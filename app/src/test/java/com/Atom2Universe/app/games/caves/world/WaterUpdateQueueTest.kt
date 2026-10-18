package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.*
import org.junit.Test

class WaterUpdateQueueTest {
    @Test fun localEditPassesTerrainBacklogAndPromotesAnExistingEntryOnlyOnce() {
        val queue = WaterUpdateQueue()
        repeat(10_000) { queue.offer(it.toLong(), it, 0, 0, false) }
        repeat(4) { queue.offer(9999, 9999, 0, 0, true) }
        assertEquals(1, queue.size(true))
        assertEquals(9999, queue.size(false))
        assertEquals(9999, queue.poll(true)!![0])
        assertNull(queue.poll(true))
        assertEquals(0, queue.poll(false)!![0])
        // Reawakened downstream work can follow the local edit, without another terrain backlog.
        queue.offer(42, 42, 0, 0, true)
        assertEquals(42, queue.poll(true)!![0])
    }

    @Test fun backgroundDiscoveryNeverDemotesPendingLocalWork() {
        val queue = WaterUpdateQueue()
        queue.offer(1, 4, 8, 12, true)
        queue.offer(1, 4, 8, 12, false)
        assertEquals(0, queue.size(false))
        assertArrayEquals(intArrayOf(4, 8, 12), queue.poll(true))
    }
}
