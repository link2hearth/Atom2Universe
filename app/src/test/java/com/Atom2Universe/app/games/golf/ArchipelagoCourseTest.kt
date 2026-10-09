package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.ClassicLandscape
import com.Atom2Universe.app.games.golf.classic.render.GroundBuilder
import com.Atom2Universe.app.games.golf.classic.render.GroundMesh
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ArchipelagoCourseTest {
    private val holes = ArchipelagoCourse.holes
    private fun reaches(h: ClassicHole, fromX: Float, fromZ: Float, x: Float, z: Float, club: GolfClub, green: Boolean): Boolean {
        for (percent in 50..100) {
            val g = ClassicGame(h).apply {
                restore(GolfPoint(fromX, 0f, fromZ), 0); windX = 0f; windZ = 0f; this.club = club
                aimAngle = atan2(x - ball.x, z - ball.z); hit(percent / 100f)
            }
            var steps = 0
            while (g.state in listOf(GolfState.FLYING, GolfState.ROLLING) && steps++ < 2400) g.update(1f / 60f)
            if (g.lastPenalty == 0 && g.state in listOf(GolfState.READY, GolfState.HOLED) &&
                if (green) g.lie == GolfLie.GREEN || g.state == GolfState.HOLED
                else g.lie == GolfLie.FAIRWAY && hypot(g.ball.x - x, g.ball.z - z) < 18f) return true
        }
        return false
    }

    @Test fun everyGreenAndReceptionIsRealLandSurroundedByWater() {
        assertEquals((1..18).toList(), holes.map { it.number })
        assertEquals(72, holes.sumOf { it.par })
        for (h in holes) {
            assertTrue(abs(h.finishX) <= 22f)
            assertEquals(GolfLie.TEE, h.lieAt(0f, 0f))
            assertEquals(GolfLie.GREEN, h.lieAt(h.cup.x, h.cup.z))
            assertTrue(h.alternateRoutes.isEmpty())
            for (island in h.islands) {
                assertNotEquals(GolfLie.WATER, h.lieAt(island.x, island.z))
                for (i in 0 until 16) {
                    val angle = i * PI.toFloat() / 8f
                    val r = max(island.rx, island.rz) * 1.5f
                    val x = island.x + cos(angle) * r; val z = island.z + sin(angle) * r
                    if (h.islandSignedDistance(x, z) >= 0f) {
                        val expected = if (abs(x) > h.width * .5f || z < -20f || z > h.length + 55f)
                            GolfLie.OUT else GolfLie.WATER
                        assertEquals("Wet shore ${h.number}", expected, h.lieAt(x, z))
                        assertEquals(h.islandWaterLevel, h.heightAt(x, z), 0f)
                    }
                }
            }
            // Full-width cross section between tee and first island has no land bridge.
            for (x in -170..170 step 10) assertEquals(GolfLie.WATER, h.lieAt(x.toFloat(), 65f))
        }
    }

    @Test fun everyAdvertisedIslandCarryHasTwoWorkingClubs() {
        val failed = mutableListOf<String>()
        for (h in holes) for (a in h.attackLandings) {
            if (!reaches(h, a.fromX, a.fromZ, a.x, a.z, a.club, h.par == 3))
                failed += "${h.number}: ${a.club} -> ${a.x}/${a.z}"
        }
        assertTrue("Unplayable islands: $failed", failed.isEmpty())
    }

    @Test fun everyLandingChoiceLeadsToAPlayableNextStageAndGreen() {
        val failed = mutableListOf<String>()
        for (h in holes.filter { it.par > 3 }) {
            val first = h.attackLandings.filter { it.fromZ == 0f }.distinctBy { it.z to it.x }
            val last = if (h.par == 4) first else h.attackLandings.filter { it.fromZ > 0f }.distinctBy { it.z to it.x }
            for (a in first) if (h.par == 5) {
                val onward = last.any { b -> hypot(b.x - a.x, b.z - a.z) <= 220f &&
                    listOf(GolfClub.WOOD3, GolfClub.DRIVER, GolfClub.WOOD5).any {
                        reaches(h, a.x, a.z, b.x, b.z, it, false)
                    } }
                if (!onward) failed += "${h.number}: dead end ${a.x}/${a.z}"
            }
            for (a in last) {
                val distance = hypot(h.cup.x - a.x, h.cup.z - a.z)
                val clubs = GolfClub.entries.filter { it != GolfClub.PUTTER && it.carry >= distance * .85f }
                if (!clubs.any { reaches(h, a.x, a.z, h.cup.x, h.cup.z, it, true) })
                    failed += "${h.number}: green from ${a.x}/${a.z}"
            }
            val target = h.openingTarget
            assertEquals("Caddie targets land ${h.number}", GolfLie.FAIRWAY, h.lieAt(target.x, target.z))
        }
        assertTrue("Dead-end choices: $failed", failed.isEmpty())
    }

    @Test fun aMissInTheLakeCostsAPenaltyAndReturnsToTheStartingIsland() {
        val h = holes.first()
        val g = ClassicGame(h).apply { windX = 0f; windZ = 0f; aimAngle = .6f; club = GolfClub.IRON8; hit(1f) }
        var steps = 0
        while (g.state in listOf(GolfState.FLYING, GolfState.ROLLING) && steps++ < 2400) g.update(1f / 60f)
        assertEquals(1, g.lastPenalty)
        assertEquals(2, g.strokes)
        assertEquals(h.tee, g.ball)
    }

    @Test fun renderedLakeIsFlatWaterAndLandHasNoFloatingEstateBuildings() {
        val h = holes[2]
        val landscape = ClassicLandscape(h)
        val mesh = landscape.terrain()
        val data = GroundMesh::class.java.getDeclaredField("vertices").apply { isAccessible = true }.get(mesh) as FloatArray
        var waterVertices = 0
        var landVertices = 0
        for (i in data.indices step GroundBuilder.STRIDE) {
            assertTrue(data[i + 1].isFinite())
            assertEquals("Shore distance must also continue across the distant apron",
                h.islandSignedDistance(data[i],data[i+2])+ClassicHole.ISLAND_ROUGH_WIDTH,data[i+10],.001f)
            if (h.islandSignedDistance(data[i], data[i + 2]) > 2f) {
                assertEquals("Lake height", h.islandWaterLevel, data[i + 1], .001f)
                assertEquals("Water texture", 1f, data[i + 9], .001f)
                waterVertices++
            } else if (h.islandSignedDistance(data[i], data[i + 2]) < -8f) landVertices++
        }
        assertTrue(waterVertices > 1000)
        assertTrue(landVertices > 100)
        assertTrue("Coastal scenery is batched by island", landscape.scenery().size >= h.islands.size + 1)
    }
}
