package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class VertigoCourseTest {
    private val holes = VertigoCourse.holes
    private var closestFailure = ""

    private fun play(h: ClassicHole, from: GolfPoint, target: GolfPoint, club: GolfClub, power: Float): ClassicGame {
        val g = ClassicGame(h).apply {
            restore(from, 0); windX = 0f; windZ = 0f; this.club = club
            aimAngle = atan2(target.x - ball.x, target.z - ball.z)
            hit(power)
        }
        var steps = 0
        while (g.state in listOf(GolfState.FLYING, GolfState.ROLLING) && steps++ < 2400) g.update(1f / 60f)
        return g
    }

    private fun canStop(h: ClassicHole, from: GolfPoint, target: GolfPoint, club: GolfClub, onGreen: Boolean): Boolean {
        var nearest = Float.POSITIVE_INFINITY
        for (percent in 50..100) {
            val g = play(h, from, target, club, percent / 100f)
            val gap = hypot(g.ball.x - target.x, g.ball.z - target.z)
            if (gap < nearest) {
                nearest = gap
                closestFailure = "$percent%: ${g.lie}, ${g.ball.x}/${g.ball.z}, gap=$gap"
            }
            if (g.lastPenalty == 0 && g.state in listOf(GolfState.READY, GolfState.HOLED) &&
                if (onGreen) g.lie == GolfLie.GREEN || g.state == GolfState.HOLED
                else g.lie == GolfLie.FAIRWAY && hypot(g.ball.x - target.x, g.ball.z - target.z) < 18f)
                return true
        }
        return false
    }

    private fun point(h: ClassicHole, x: Float, z: Float) = GolfPoint(x, h.heightAt(x, z), z)

    @Test fun expertRoundHasEighteenDistinctHolesAndLongDryDetours() {
        assertEquals((1..18).toList(), holes.map { it.number })
        assertEquals(72, holes.sumOf { it.par })
        for (h in holes) {
            assertEquals(GolfLie.TEE, h.lieAt(0f, 0f))
            assertEquals(GolfLie.GREEN, h.lieAt(h.cup.x, h.cup.z))
            for (nodes in listOf(h.route) + h.alternateRoutes) {
                assertTrue("Node order ${h.number}", nodes.zipWithNext().all { (a, b) -> b.z > a.z })
                assertEquals(h.length, nodes.last().z, 0f)
                assertEquals(h.finishX, nodes.last().x, 0f)
            }
            for (z in h.fairwayStart.toInt()..h.length.toInt() step 4) {
                val x = h.fairwayCenter(z.toFloat())
                assertTrue("Hazard on safe route ${h.number}/$z", h.hazards.all { it.signedDistance(x, z.toFloat()) > 3f })
                assertTrue(h.heightAt(x, z.toFloat()).isFinite())
            }
            if (h.par == 3) continue
            val attack = h.attackLandings.distinctBy { it.z }
            val attackPoints = listOf(h.tee) + attack.map { point(h, it.x, it.z) } + h.cup
            val attackLength = attackPoints.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.z - a.z).toDouble() }
            val outsideLength = h.route.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.z - a.z).toDouble() }
            assertTrue("Insufficient detour ${h.number}: $outsideLength / $attackLength", outsideLength > attackLength * 1.5)
            assertTrue("Small green ${h.number}", h.greenRadius < 14f)
        }
    }

    @Test fun everyIsolatedReceptionWorksWithBothAdvertisedClubs() {
        val failed = mutableListOf<String>()
        for (h in holes) for (a in h.attackLandings) {
            val target = point(h, a.x, a.z)
            val from = point(h, a.fromX, a.fromZ)
            val expected = if (h.par == 3) GolfLie.GREEN else GolfLie.FAIRWAY
            assertEquals("Target ${h.number}", expected, h.lieAt(a.x, a.z))
            assertTrue("Hazard clearance ${h.number}", h.hazards.all { it.signedDistance(a.x, a.z) > 5f })
            if (!canStop(h, from, target, a.club, h.par == 3)) failed += "${h.number}: ${a.club} -> ${a.x}/${a.z} ($closestFailure)"
        }
        assertTrue("Unplayable club options: $failed", failed.isEmpty())
    }

    @Test fun pocketsHaveRealUnmownGapsAndAreMuchNarrowerThanTheSafeFairway() {
        for (h in holes.filter { it.par > 3 }) for (a in h.attackLandings.distinctBy { it.z }) {
            assertTrue("Reception too broad ${h.number}", h.fairwaySignedDistance(a.x + 15f, a.z) > 0f)
            assertTrue("Connected pocket front ${h.number}", h.fairwaySignedDistance(a.x, a.z - 50f) > 5f)
            assertTrue("Connected pocket rear ${h.number}", h.fairwaySignedDistance(a.x, a.z + 50f) > 5f)
            assertTrue("Safe fairway too narrow ${h.number}", h.fairwayWidth(h.landingZ) >= 45f)
        }
    }

    @Test fun finalAttackApproachesHaveAtLeastTwoPlayableClubs() {
        val failed = mutableListOf<String>()
        for (h in holes.filter { it.par > 3 }) {
            val a = h.attackLandings.maxBy { it.z }
            val from = point(h, a.x, a.z)
            val distance = hypot(h.cup.x - from.x, h.cup.z - from.z)
            val choices = GolfClub.entries.filter {
                it != GolfClub.PUTTER && it.carry >= distance * .85f && it.carry <= distance * 1.6f
            }.count { canStop(h, from, h.cup, it, true) }
            if (choices < 2) failed += "${h.number}: $choices"
        }
        assertTrue("No two-club approach: $failed", failed.isEmpty())
    }

    @Test fun safeOpeningsAndShortHoleBailoutsRemainPlayable() {
        val failed = mutableListOf<String>()
        for (h in holes) {
            val target = if (h.par == 3) h.route.first { it.width >= 45f }.let { point(h, it.x, it.z) }
                else h.openingTarget
            assertEquals(GolfLie.FAIRWAY, h.lieAt(target.x, target.z))
            if (!GolfClub.entries.filter { it != GolfClub.PUTTER && it.carry >= hypot(target.x, target.z) * .85f }
                    .any { canStop(h, h.tee, target, it, false) }) failed += "${h.number}: opening"
            if (h.par == 3 && !GolfClub.entries.filter { it != GolfClub.PUTTER }
                    .any { canStop(h, target, h.cup, it, true) }) failed += "${h.number}: bailout approach"
        }
        assertTrue("Unplayable safe route: $failed", failed.isEmpty())
    }

    @Test fun safeRouteCannotEraseTheExtraShotWithDriverRollout() {
        for (h in holes.filter { it.par > 3 }) {
            val safe = if (h.par == 4) h.openingTarget else h.route.first { it.z == 335f }.let { point(h, it.x, it.z) }
            assertTrue(hypot(h.cup.x - safe.x, h.cup.z - safe.z) > 315f)
            for (percent in 85..100) {
                val g = play(h, safe, h.cup, GolfClub.DRIVER, percent / 100f)
                assertFalse("Safe shortcut ${h.number}/$percent", g.lie == GolfLie.GREEN || g.state == GolfState.HOLED)
            }
        }
    }
}
