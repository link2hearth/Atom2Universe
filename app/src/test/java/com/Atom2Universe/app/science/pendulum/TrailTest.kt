package com.Atom2Universe.app.science.pendulum

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailTest {

    private fun Trail.xs() = (0 until size).map { x(it) }

    @Test
    fun keepsPointsInOrderBeforeWrapping() {
        val trail = Trail(capacity = 4)
        trail.add(1f, 10f); trail.add(2f, 20f); trail.add(3f, 30f)
        assertEquals(listOf(1f, 2f, 3f), trail.xs())
        assertEquals(30f, trail.y(2))
    }

    @Test
    fun dropsOldestOnceFull() {
        val trail = Trail(capacity = 3)
        for (i in 1..7) trail.add(i.toFloat(), -i.toFloat())
        assertEquals(3, trail.size)
        assertEquals(listOf(5f, 6f, 7f), trail.xs())
        assertEquals(-5f, trail.y(0))
    }

    @Test
    fun clearEmptiesAndRestarts() {
        val trail = Trail(capacity = 3)
        for (i in 1..5) trail.add(i.toFloat(), 0f)
        trail.clear()
        assertEquals(0, trail.size)
        trail.add(9f, 0f)
        assertEquals(listOf(9f), trail.xs())
    }
}
