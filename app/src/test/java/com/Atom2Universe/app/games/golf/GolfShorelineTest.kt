package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GolfShorelineTest {
    private val courses = listOf(ClassicCourse.holes, HeatherCourse.holes, WildDetoursCourse.holes,
        VertigoCourse.holes, ArchipelagoCourse.holes, SnowPeaksCourse.holes)

    @Test fun baysAreSmoothPeriodicAndStayInsideTheirAuthoredFootprint() {
        for (shape in GolfShoreline.entries) {
            val radii = (0..1440).map { shape.radius(it * PI.toFloat() / 720f) }
            assertTrue(radii.all { it in .5999f..1.0001f })
            assertTrue("A visible bay, not another oval: $shape", radii.max() - radii.min() > .29f)
            assertEquals(radii.first(), radii.last(), .00001f)
            for (i in 0..16) {
                val angle = i * PI.toFloat() / 8f
                val e = .0005f
                val left = (shape.radius(angle) - shape.radius(angle - e)) / e
                val right = (shape.radius(angle + e) - shape.radius(angle)) / e
                assertEquals("No corner in $shape at $i", left, right, .02f)
            }
        }
    }

    @Test fun allLakeAndIslandContoursHaveOneContinuousShoreAndMatchTheirLies() {
        for ((course, holes) in courses.withIndex()) for (hole in holes) {
            val shores = hole.islands + hole.hazards.filter { it.lie == GolfLie.WATER }
            for (shore in shores) {
                // Meandering lakes need a spine/connectivity test, not a single radial exit.
                if (shore.watercourse != null) continue
                assertNotNull(shore.shoreline)
                assertEquals(shore, shore.copy())
                for (i in 0 until 96) {
                    val angle = i * PI.toFloat() / 48f
                    val dx = cos(angle); val dz = sin(angle)
                    var lo = 0f; var hi = max(shore.rx, shore.rz) * 1.5f
                    repeat(24) {
                        val mid = (lo + hi) * .5f
                        if (shore.contains(shore.x + dx * mid, shore.z + dz * mid)) lo = mid else hi = mid
                    }
                    val x = shore.x + dx * lo; val z = shore.z + dz * lo
                    val label = "$course/${hole.number} shore ${shore.x}/${shore.z} angle $i"
                    assertTrue(label, shore.contains(x - dx * .02f, z - dz * .02f))
                    assertFalse(label, shore.contains(x + dx * .02f, z + dz * .02f))
                    // Water has no height discontinuity at a bank outside the green terrace.
                    if (hole.greenSignedDistance(x, z) > 10f) {
                        assertEquals(label, hole.heightAt(x - dx * .001f, z - dz * .001f),
                            hole.heightAt(x + dx * .001f, z + dz * .001f), .015f)
                    }
                    if (shore.lie == GolfLie.WATER && hole.greenSignedDistance(x, z) > 0f &&
                        hole.hazards.none { it.lie == GolfLie.BUNKER && it.contains(x, z) } &&
                        abs(x) < hole.width * .5f && z in -19f..hole.length + 54f) {
                        assertEquals(label, GolfLie.WATER, hole.lieAt(x - dx * .02f, z - dz * .02f))
                    }
                }
            }
        }
    }

    @Test fun islandsKeepDryLandingCoresAndCompleteGreenAprons() {
        for (hole in ArchipelagoCourse.holes) {
            for (island in hole.islands.drop(1).dropLast(1)) {
                for (i in 0 until 32) {
                    val angle = i * PI.toFloat() / 16f
                    assertTrue("Landing core ${hole.number}",
                        hole.islandSignedDistance(island.x + cos(angle) * 8f, island.z + sin(angle) * 8f) < -6f)
                }
            }
            for (i in 0 until 96) {
                val angle = i * PI.toFloat() / 48f
                var lo = 0f; var hi = hole.greenRadius * 2f
                repeat(24) {
                    val mid = (lo + hi) * .5f
                    if (hole.greenSignedDistance(hole.cup.x + cos(angle) * mid,
                            hole.cup.z + sin(angle) * mid) < 8f) lo = mid else hi = mid
                }
                assertTrue("Dry green apron ${hole.number}, angle $i", hole.islandSignedDistance(
                    hole.cup.x + cos(angle) * lo, hole.cup.z + sin(angle) * lo) < -1f)
            }
        }
    }
}
