package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CourseArchitectureTest {
    @Test fun allEighteenHolesHaveIndividualRoutesAndSafeOpeningShelves() {
        val holes = ClassicCourse.holes
        assertEquals(18, holes.size)
        assertEquals(72, holes.sumOf { it.par })
        assertEquals(18, holes.map { it.route }.distinct().size)
        holes.forEach { hole ->
            assertTrue(hole.route.size >= 5)
            assertTrue(hole.route.zipWithNext().all { (a,b) -> a.z < b.z })
            assertEquals(0f, hole.route.first().z, 0f)
            assertEquals(hole.length, hole.route.last().z, 0f)
            assertEquals(hole.cup.x, hole.fairwayCenter(hole.length), .001f)
            assertEquals(GolfLie.TEE, hole.lieAt(0f, 0f))
            assertEquals("Separate tee from main fairway ${hole.number}", GolfLie.ROUGH,
                hole.lieAt(hole.fairwayCenter(18f),18f))
            assertEquals(GolfLie.GREEN, hole.lieAt(hole.cup.x, hole.cup.z))
            assertTrue(hole.greenSignedDistance(hole.cup.x, hole.cup.z) < -9f)
            val target = hole.openingTarget
            assertTrue("Unsafe opening target ${hole.number}", hole.lieAt(target.x,target.z) in
                setOf(GolfLie.FAIRWAY, GolfLie.GREEN))
            if (hole.par > 3) {
                assertTrue(hypot(target.x, target.z) in 150f..230f)
                assertTrue(hole.fairwayWidth(target.z) >= 35f)
            }
        }
    }

    @Test fun parThreesAreCarryHolesAndDoglegsChangeDirection() {
        ClassicCourse.holes.filter { it.par == 3 }.forEach { hole ->
            val roughCarry = (25..(hole.length*.65f).toInt() step 5).count { z ->
                hole.fairwaySignedDistance(hole.fairwayCenter(z.toFloat()),z.toFloat()) > 0f
            }
            assertTrue("Par 3 ${hole.number} keeps a meadow before its broad apron", roughCarry >= 3)
        }
        val doglegs = ClassicCourse.holes.filter { it.par > 3 }.count { hole ->
            val landing = hole.openingTarget
            abs(landing.x - hole.finishX * landing.z / hole.length) > 30f
        }
        assertTrue("Keep gentle doglegs in both directions", doglegs >= 6)
        assertTrue(ClassicCourse.holes.any { h -> h.route.any { it.x < -40f } })
        assertTrue(ClassicCourse.holes.any { h -> h.route.any { it.x > 40f } })
    }

    @Test fun fairwayCurvesStayContinuousAndEveryHoleHasRealReliefAndLevelTee() {
        ClassicCourse.holes.forEach { hole ->
            val heights = (0..hole.length.toInt() step 4).map { z -> hole.heightAt(hole.fairwayCenter(z.toFloat()),z.toFloat()) }
            val minimumRelief = if (hole.cupBowlDepth > 0f) 2f else 4f
            assertTrue("Keep visible but gentle terrain at ${hole.number}", heights.max() - heights.min() > minimumRelief)
            for (x in listOf(-3f,0f,3f)) for (z in listOf(-3f,0f,3f)) {
                assertEquals("Tee must be a flat platform ${hole.number}",hole.tee.y-ClassicHole.BALL_RADIUS,hole.heightAt(x,z),.001f)
            }
            for (node in hole.route.drop(1).dropLast(1)) {
                assertEquals(hole.fairwayCenter(node.z-.01f),hole.fairwayCenter(node.z+.01f),.08f)
                assertEquals(hole.fairwayWidth(node.z-.01f),hole.fairwayWidth(node.z+.01f),.03f)
            }
            // The putting region must remain gentle around every cup despite surrounding hills.
            val x = hole.cup.x; val z = hole.cup.z
            val sx = (hole.heightAt(x+.5f,z)-hole.heightAt(x-.5f,z))
            val sz = (hole.heightAt(x,z+.5f)-hole.heightAt(x,z-.5f))
            if (hole.cupBowlDepth > 0f) assertTrue("Level bowl centre ${hole.number}", hypot(sx, sz) < .001f)
            else assertTrue("Cup slope ${hole.number}",hypot(sx,sz) in .004f.. .03f)
        }
    }

    @Test fun hazardContoursMatchCollisionAndRotationsAreNotAxisAlignedOvals() {
        val hazard = GolfHazard(2f,5f,6f,19f,GolfLie.BUNKER,.7f,.24f,1.3f)
        assertTrue(hazard.signedDistance(2f,5f)<0f)
        assertTrue(hazard.contains(2f,5f))
        assertFalse(hazard.contains(100f,100f))
        for (x in -25..25 step 2) for (z in -25..25 step 2) {
            assertEquals(hazard.signedDistance(x.toFloat(),z.toFloat())<=0f,hazard.contains(x.toFloat(),z.toFloat()))
        }
        assertTrue(ClassicCourse.holes.flatMap { it.hazards }.count { abs(it.rotation)>.2f }>=25)
        assertTrue(ClassicCourse.holes.all { it.greenShape.shape>.1f })
        ClassicCourse.holes.forEach { hole ->
            hole.hazards.filter { it.lie==GolfLie.WATER }.forEach { lake ->
                assertEquals(hole.heightAt(lake.x,lake.z),hole.heightAt(lake.x+1f,lake.z+1f),.001f)
            }
        }
    }

    @Test fun lakeBanksStayContinuousAfterGreenTerraceBlending() {
        ClassicCourse.holes.forEach { hole ->
            hole.hazards.filter { it.lie == GolfLie.WATER }.forEach { lake ->
                for (i in 0 until 24) {
                    val angle = i * PI.toFloat() / 12f
                    val dx = cos(angle); val dz = sin(angle)
                    var lo = 0f; var hi = max(lake.rx,lake.rz) * 2f
                    repeat(22) {
                        val mid = (lo+hi)*.5f
                        if (lake.contains(lake.x+dx*mid,lake.z+dz*mid)) lo=mid else hi=mid
                    }
                    val x=lake.x+dx*lo; val z=lake.z+dz*lo
                    if (hole.greenSignedDistance(x,z)>1f) {
                        assertEquals("Jagged shore at hole ${hole.number}",
                            hole.heightAt(x-dx*.01f,z-dz*.01f),
                            hole.heightAt(x+dx*.01f,z+dz*.01f),.015f)
                    }
                }
            }
        }
    }

    @Test fun greenEdgesHaveNoHeightStepBesideWater() {
        ClassicCourse.holes.filter { h -> h.hazards.any { it.lie==GolfLie.WATER } }.forEach { hole ->
            for (i in 0 until 48) {
                val angle=i*PI.toFloat()/24f
                val dx=cos(angle); val dz=sin(angle)
                var lo=0f; var hi=hole.greenRadius*2f
                repeat(22) {
                    val mid=(lo+hi)*.5f
                    if (hole.greenSignedDistance(hole.cup.x+dx*mid,hole.cup.z+dz*mid)<0f) lo=mid else hi=mid
                }
                val x=hole.cup.x+dx*lo; val z=hole.cup.z+dz*lo
                assertEquals("Green bank step at hole ${hole.number}",
                    hole.heightAt(x-dx*.001f,z-dz*.001f),
                    hole.heightAt(x+dx*.001f,z+dz*.001f),.012f)
            }
        }
    }
}
