package com.Atom2Universe.app.games.mochi

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class MochiFlightTest {
    @Test fun freeFlightRetainsTheExactBallisticArc() {
        val start = MochiFlight.State(140f, 250f, 110f, 510f)
        val dt = 1f / 120f
        val result = MochiFlight.step(start, dt, emptyList())
        assertEquals(start.x + start.vx * dt, result.state.x, .0001f)
        assertEquals(start.y + start.vy * dt - .5f * 760f * dt * dt, result.state.y, .0001f)
        assertEquals(start.vy - 760f * dt, result.state.vy, .0001f)
        assertTrue(result.hits.isEmpty())
        assertEquals(1, result.segments.size)
        assertEquals(0f, result.segments.first().startFraction, 0f)
        assertEquals(1f, result.segments.last().endFraction, 0f)
    }

    @Test fun fastShotDoesNotTunnelThroughAThinWall() {
        val wall = MochiWall(7, 200f, 100f, 203f, 300f)
        val result = MochiFlight.step(MochiFlight.State(100f, 200f, 2000f, 0f), .05f,
            listOf(wall), gravity = 0f)
        assertEquals(1, result.hits.size)
        assertEquals(7, result.hits.single().wallId)
        assertEquals(183f, result.hits.single().x, .0001f)
        assertTrue(result.state.vx < 0)
        assertTrue(result.state.x < 183f)
        assertFalse(MochiFlight.overlaps(wall, result.state.x, result.state.y))
        assertEquals(2, result.segments.size)
        assertEquals(result.segments[0].endFraction, result.segments[1].startFraction, 0f)
        assertEquals(result.hits.single().fraction, result.segments[0].endFraction, 0f)
    }

    @Test fun anOverhangReflectsUpwardMotionAndItsTopHasTheOppositeNormal() {
        val roof = MochiWall(8, 80f, 200f, 260f, 215f)
        val underside = MochiFlight.step(MochiFlight.State(170f, 150f, 0f, 500f), .1f,
            listOf(roof), gravity = 0f)
        assertEquals(-1f, underside.hits.single().ny, 0f)
        assertEquals(183f, underside.hits.single().y, .0001f)
        assertTrue(underside.state.vy < 0)
        val top = MochiFlight.step(MochiFlight.State(170f, 260f, 0f, -600f), .1f,
            listOf(roof), gravity = 0f)
        assertEquals(1f, top.hits.single().ny, 0f)
        assertEquals(232f, top.hits.single().y, .0001f)
        assertTrue(top.state.vy > 0)
    }

    @Test fun cornersAreRoundRatherThanInvisibleSquareExtensions() {
        val wall = MochiWall(9, 200f, 200f, 240f, 300f)
        val clear = MochiFlight.step(MochiFlight.State(170f, 170f, 300f, 300f), .05f,
            listOf(wall), gravity = 0f)
        assertTrue("Le coin élargi en carré bloquerait à tort ce tir", clear.hits.isEmpty())
        assertEquals(185f, clear.state.x, .0001f)
        val hit = MochiFlight.step(MochiFlight.State(165f, 165f, 400f, 400f), .08f,
            listOf(wall), gravity = 0f)
        val corner = hit.hits.single()
        assertTrue(corner.nx < -.6f && corner.ny < -.6f)
        assertEquals(17f, hypot(corner.x - 200f, corner.y - 200f), .0001f)
        assertTrue(hit.state.vx < 0f && hit.state.vy < 0f)
        assertFalse(MochiFlight.overlaps(wall, hit.state.x, hit.state.y))
    }

    @Test fun reboundsLoseNormalEnergyWithoutSlowingTheTangent() {
        for (material in MochiWall.Material.entries) {
            val wall = MochiWall(10, 200f, 100f, 210f, 400f, material)
            val initial = MochiFlight.State(170f, 200f, 600f, 70f)
            val result = MochiFlight.step(initial, .05f, listOf(wall), gravity = 0f)
            assertEquals(-600f * material.restitution, result.state.vx, .001f)
            assertEquals(70f, result.state.vy, .001f)
            assertTrue(hypot(result.state.vx, result.state.vy) <= hypot(initial.vx, initial.vy))
        }
    }

    @Test fun movingAwayFromContactDoesNotProduceAnotherImpact() {
        val wall = MochiWall(11, 200f, 100f, 215f, 300f)
        val result = MochiFlight.step(MochiFlight.State(183f, 200f, -350f, 0f), 1f / 120f,
            listOf(wall), gravity = 0f)
        assertTrue(result.hits.isEmpty())
        assertTrue(result.state.x < 183f)
        assertEquals(-350f, result.state.vx, 0f)
    }

    @Test fun narrowCorridorSupportsSeveralOrderedReboundsInOneStep() {
        val left = MochiWall(12, 80f, 100f, 100f, 400f, MochiWall.Material.RUBBER)
        val right = MochiWall(13, 150f, 100f, 170f, 400f, MochiWall.Material.RUBBER)
        val initial = MochiFlight.State(125f, 200f, 1200f, 0f)
        val result = MochiFlight.step(initial, .04f, listOf(left, right), gravity = 0f)
        assertTrue(result.hits.size >= 2)
        assertTrue(result.hits.zipWithNext().all { (a, b) -> a.fraction <= b.fraction })
        assertTrue(result.segments.zipWithNext().all { (a, b) -> a.endFraction == b.startFraction })
        assertFalse(MochiFlight.overlaps(left, result.state.x, result.state.y))
        assertFalse(MochiFlight.overlaps(right, result.state.x, result.state.y))
        assertTrue(hypot(result.state.vx, result.state.vy) <= hypot(initial.vx, initial.vy))
        assertEquals(result, MochiFlight.step(initial, .04f, listOf(left, right), gravity = 0f))
    }

    @Test fun aPassageMustFitTheWholeCircleRatherThanOnlyItsCentre() {
        val initial = MochiFlight.State(210f, 150f, 0f, 500f)
        val tooNarrow = listOf(MochiWall(14, 0f, 200f, 195f, 215f),
            MochiWall(15, 225f, 200f, 420f, 215f))
        val blocked = MochiFlight.step(initial, .12f, tooNarrow, gravity = 0f)
        assertTrue(blocked.hits.isNotEmpty())
        assertTrue("Un passage de 30 ne laisse pas traverser un diamètre de 34", blocked.state.y < 200f)
        assertTrue(tooNarrow.none { MochiFlight.overlaps(it, blocked.state.x, blocked.state.y) })
        val open = listOf(tooNarrow[0].copy(right = 184f), tooNarrow[1].copy(left = 236f))
        val passed = MochiFlight.step(initial, .2f, open, gravity = 0f)
        assertTrue(passed.hits.isEmpty())
        assertEquals(250f, passed.state.y, .0001f)
    }
}
