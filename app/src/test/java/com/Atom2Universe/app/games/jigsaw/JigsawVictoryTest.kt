package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.*
import org.junit.Test

class JigsawVictoryTest {
    @Test fun `every size settles exactly before the image fuses and the seams unwind`() {
        for (size in JigsawSize.entries) {
            val motion = JigsawVictoryMotion(size)
            for (id in 0 until size.count) {
                assertEquals(0f, motion.lift(id, 0f), 0f)
                assertEquals(0f, motion.lift(id, JigsawVictoryMotion.FUSED_AT), 0f)
                assertEquals(0f, motion.lift(id, 1f), 0f)
                assertTrue((0..36).any { motion.lift(id, it / 100f) > .9f })
            }
        }
        var previous = 0f
        for (step in 0..100) {
            val progress = step / 100f
            val unlace = JigsawVictoryMotion.unlace(progress)
            assertTrue(unlace in 0f..1f && unlace >= previous)
            if (progress <= JigsawVictoryMotion.FUSED_AT) assertEquals(0f, unlace, 0f)
            previous = unlace
        }
        assertEquals(1f, previous, 0f)
        assertEquals(0f, JigsawVictoryMotion.frameAlpha(1f), 0f)
        assertEquals(1f, JigsawVictoryMotion.camera(1f), 0f)
    }

    @Test fun `the unwinding thread follows every shared edge exactly once without breaking a row`() {
        for (size in JigsawSize.entries) for (seed in listOf(17, 42, -1)) {
            val geometry = JigsawGeometry(size, seed)
            val seams = geometry.seams()
            assertEquals(size.rows + size.columns - 2, seams.size)
            seams.forEach { line ->
                line.zipWithNext().forEach { (a, b) ->
                    assertEquals(a.end.x, b.start.x, .001f)
                    assertEquals(a.end.y, b.start.y, .001f)
                }
            }
            val actual = seams.flatten().map { canonical(it) }
            assertEquals(3 * ((size.rows - 1) * size.columns + (size.columns - 1) * size.rows), actual.size)
            assertEquals(actual.size, actual.toSet().size)
            val edges = (0 until size.count).flatMap { geometry.piece(it) }.map { canonical(it) }.groupingBy { it }.eachCount()
            val shared = edges.filterValues { it == 2 }.keys
            assertEquals(shared, actual.toSet())
        }
    }

    private fun canonical(curve: PuzzleCurve): PuzzleCurve =
        if (curve.start.x < curve.end.x || (curve.start.x == curve.end.x && curve.start.y < curve.end.y)) curve
        else curve.reversed()
}
