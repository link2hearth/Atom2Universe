package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class HeatherCourseTest {
    @Test fun secondCourseIsADistinctModestStepUpWithRoomForHarderCourses() {
        val original = ClassicCourse.holes
        val holes = HeatherCourse.holes
        assertEquals((1..18).toList(), holes.map { it.number })
        assertEquals(72, holes.sumOf { it.par })
        assertEquals(4, holes.count { it.par == 3 })
        assertEquals(4, holes.count { it.par == 5 })
        assertEquals(18, holes.map { it.route }.distinct().size)
        assertTrue(holes.none { h -> original.any { it.route == h.route } })
        val lengthRatio = holes.sumOf { it.length.toDouble() } / original.sumOf { it.length.toDouble() }
        // Gardens was subsequently shortened into a true beginner course. Keep Heather's
        // original moderate dimensions, rather than scaling it down alongside that redesign.
        assertTrue(holes.sumOf { it.length.toDouble() } in 6000.0..6400.0)
        assertTrue("Longer than the introductory course: $lengthRatio", lengthRatio > 1.0)
        val greenRatio = holes.map { it.greenRadius }.average() / original.map { it.greenRadius }.average()
        assertTrue(holes.all { it.greenRadius in 15f..18f })
        assertTrue("Smaller than the beginner greens: $greenRatio", greenRatio < 1.0)
        val oldLanding = original.filter { it.par > 3 }.map { it.fairwayWidth(it.landingZ) }.average()
        val landing = holes.filter { it.par > 3 }.map { it.fairwayWidth(it.landingZ) }.average()
        assertTrue("Still generous, but narrower than Gardens: $landing vs $oldLanding", landing in 45.0..52.0 && landing < oldLanding)
        assertTrue(holes.filter { it.par == 4 }.count { it.length <= 345f } >= 3)
        assertTrue(holes.count { h -> h.hazards.any { it.lie == GolfLie.WATER } } <= 5)
        val report = "length=${holes.sumOf { it.length.toDouble() }}, ratio=$lengthRatio\n" +
            "mean landing width=$landing, original=$oldLanding\ngreen radius ratio=$greenRatio\n"
        File("build/reports/classic-terrain/heather").mkdirs()
        File("build/reports/classic-terrain/heather/difficulty.txt").writeText(report)
    }

    @Test fun teesOpeningShotsAndConservativeRoutesRemainPlayable() {
        for (h in HeatherCourse.holes) {
            assertEquals(GolfLie.TEE, h.lieAt(0f, 0f))
            assertTrue(h.route.zipWithNext().all { (a,b) -> a.z < b.z })
            assertEquals(h.length, h.route.last().z, 0f)
            assertEquals(h.finishX, h.route.last().x, 0f)
            for (x in listOf(-3f, 0f, 3f)) for (z in listOf(-3f, 0f, 3f))
                assertEquals(h.tee.y - ClassicHole.BALL_RADIUS, h.heightAt(x,z), .001f)
            assertTrue(h.greenSignedDistance(h.cup.x,h.cup.z) < -10f)
            assertEquals(GolfLie.GREEN,h.lieAt(h.cup.x,h.cup.z))
            val target = h.openingTarget
            if (h.par > 3) {
                assertEquals("Opening ${h.number}", GolfLie.FAIRWAY, h.lieAt(target.x,target.z))
                assertTrue("Reachable opening ${h.number}", hypot(target.x,target.z) in 165f..225f)
                assertTrue(h.fairwayWidth(target.z) >= 45f)
                assertTrue("Opening clearance ${h.number}", h.hazards.all { it.signedDistance(target.x,target.z) > 5f })
                // The conservative centre route has no compulsory water carry after the drive.
                for (z in target.z.toInt()..h.length.toInt() step 4) {
                    val x = h.fairwayCenter(z.toFloat())
                    assertTrue("Forced water ${h.number} at $z", h.hazards.filter { it.lie == GolfLie.WATER }
                        .all { it.signedDistance(x,z.toFloat()) > 4f })
                }
            }
            // Stable simulation/geometry throughout each routing, and genuinely visible relief.
            val heights = (0..h.length.toInt() step 5).map { h.heightAt(h.fairwayCenter(it.toFloat()),it.toFloat()) }
            assertTrue(heights.all { it.isFinite() })
            assertTrue(heights.max() - heights.min() > 5f)
        }
    }

    @Test fun everyLongHoleAcceptsAPlacedOpeningShotWithTheRealBallSimulation() {
        for (h in HeatherCourse.holes.filter { it.par > 3 }) {
            val target = h.openingTarget
            val aim = atan2(target.x, target.z)
            var found = false
            search@ for (club in listOf(GolfClub.WOOD3,GolfClub.WOOD5,GolfClub.HYBRID4,GolfClub.DRIVER)) {
                for (percent in 80..100 step 2) {
                    val game = ClassicGame(h).apply {
                        windX=0f; windZ=0f; this.club=club; aimAngle=aim; hit(percent / 100f)
                    }
                    var steps=0
                    while (game.state in listOf(GolfState.FLYING,GolfState.ROLLING) && steps++ < 2400)
                        game.update(1f/60f)
                    if (game.state == GolfState.READY && game.lastPenalty == 0 &&
                        game.lie == GolfLie.FAIRWAY && game.ball.z in 150f..target.z+65f) {
                        found=true
                        break@search
                    }
                }
            }
            assertTrue("No safe placed drive on hole ${h.number}",found)
        }
    }
}
