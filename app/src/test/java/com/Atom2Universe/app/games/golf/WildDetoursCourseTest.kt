package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class WildDetoursCourseTest {
    @Test fun attackShelvesMatchEquipmentAndStayClearOfHazards() {
        for(h in WildDetoursCourse.holes) {
            assertEquals(GolfLandscapeStyle.AUTUMN_HIGHLANDS,h.landscapeStyle)
            if(h.par>3) assertTrue("No attack on ${h.number}",h.attackLandings.isNotEmpty())
            for(a in h.attackLandings) {
                val distance=hypot(a.x-a.fromX,a.z-a.fromZ)
                assertTrue("Carry ${h.number}: $distance / ${a.club}",distance in a.club.carry*.70f..a.club.carry)
                assertEquals("Reception ${h.number}",GolfLie.FAIRWAY,h.lieAt(a.x,a.z))
                assertTrue("Hazard margin ${h.number}",h.hazards.all { it.signedDistance(a.x,a.z)>5f })
                if(a.fromZ==0f && a.x!=0f) {
                    val attack=hypot(h.finishX-a.x,h.length-a.z)
                    val safe=h.openingTarget
                    assertTrue("No shortcut benefit ${h.number}",attack+10f<hypot(h.finishX-safe.x,h.length-safe.z))
                }
            }
        }
        for(h in ClassicCourse.holes+HeatherCourse.holes) assertEquals(GolfLandscapeStyle.PARKLAND,h.landscapeStyle)
    }

    @Test fun realShotsCanStopOnEveryAttackShelfWithoutWind() {
        for(h in WildDetoursCourse.holes) for(a in h.attackLandings) {
            val angle=atan2(a.x-a.fromX,a.z-a.fromZ)
            var found=false
            for(percent in 65..100) {
                val g=ClassicGame(h).apply {
                    restore(GolfPoint(a.fromX,h.heightAt(a.fromX,a.fromZ),a.fromZ),0)
                    windX=0f;windZ=0f;club=a.club;aimAngle=angle;hit(percent/100f)
                }
                var steps=0
                while(g.state in listOf(GolfState.FLYING,GolfState.ROLLING) && steps++<2400) g.update(1f/60f)
                if(g.state==GolfState.READY && g.lastPenalty==0 && g.lie==GolfLie.FAIRWAY &&
                    hypot(g.ball.x-a.x,g.ball.z-a.z)<18f) { found=true;break }
            }
            assertTrue("Unplayable attack ${h.number}: ${a.club} to ${a.x}/${a.z}",found)
        }
    }

    @Test fun fullAdvancedRoundHasDryPrimaryRoutesAndReachableOpeningTargets() {
        val holes = WildDetoursCourse.holes
        assertEquals((1..18).toList(), holes.map { it.number })
        assertEquals(72, holes.sumOf { it.par })
        assertEquals(4, holes.count { it.par == 3 })
        assertEquals(4, holes.count { it.par == 5 })
        for (h in holes) {
            assertEquals(GolfLie.TEE, h.lieAt(0f, 0f))
            assertEquals(GolfLie.GREEN, h.lieAt(h.cup.x, h.cup.z))
            assertEquals(ClassicHole.CUP_RADIUS, h.cupRadius, 0f)
            for (nodes in listOf(h.route) + h.alternateRoutes) {
                assertTrue(nodes.zipWithNext().all { (a,b) -> b.z > a.z })
                assertEquals(h.length, nodes.last().z, 0f)
                assertEquals(h.finishX, nodes.last().x, 0f)
            }
            if (h.par == 3) continue
            val target = h.openingTarget
            assertEquals("Opening ${h.number}", GolfLie.FAIRWAY, h.lieAt(target.x, target.z))
            assertTrue("Range ${h.number}", hypot(target.x, target.z) < GolfClub.DRIVER.carry)
            for (z in h.fairwayStart.toInt()..h.length.toInt() step 4) {
                val x = h.fairwayCenter(z.toFloat())
                assertTrue("Forced water ${h.number}/$z", h.hazards.filter { it.lie == GolfLie.WATER }
                    .all { it.signedDistance(x, z.toFloat()) > 3f })
                assertTrue(h.heightAt(x, z.toFloat()).isFinite())
            }
            for (tree in h.plantedTrees) {
                assertEquals("Grove ${h.number}", GolfLie.ROUGH, h.lieAt(tree.x, tree.z))
                assertTrue(h.fairwaySignedDistance(tree.x, tree.z) > tree.radius)
            }
        }
    }

    @Test fun forkReallyHasTwoMownSidesAndAnUnmownWoodedCentre() {
        val h = WildDetoursCourse.holes.first()
        assertEquals(GolfLie.FAIRWAY, h.lieAt(-110f, 175f))
        assertEquals(GolfLie.FAIRWAY, h.lieAt(56f, 225f))
        assertEquals(GolfLie.ROUGH, h.lieAt(0f, 190f))
        val withoutBranch = h.copy(alternateRoutes=emptyList())
        assertEquals(GolfLie.ROUGH, withoutBranch.lieAt(56f, 225f))
        assertTrue(h.heightAt(0f, 190f) > h.heightAt(-110f, 175f) + 7f)
        // Branches must not change the geometry of the two existing courses.
        for (old in ClassicCourse.holes + HeatherCourse.holes) {
            assertTrue(old.alternateRoutes.isEmpty())
            assertEquals(old.fairwaySignedDistance(40f, 100f),
                old.copy(alternateRoutes=emptyList()).fairwaySignedDistance(40f, 100f), 0f)
        }
    }

    @Test fun causewaysAreFairwayWithWaterOnBothSidesAndIslandGapsRemainWet() {
        for ((number, x, z) in listOf(Triple(2,-10f,290f),Triple(18,-7f,310f))) {
            val h = WildDetoursCourse.holes[number - 1]
            assertEquals(GolfLie.FAIRWAY, h.lieAt(x, z))
            assertEquals(GolfLie.WATER, h.lieAt(x - 25f, z))
            assertEquals(GolfLie.WATER, h.lieAt(x + 25f, z))
        }
        val islands = WildDetoursCourse.holes[5]
        assertEquals(GolfLie.FAIRWAY, islands.lieAt(-45f, 215f))
        assertEquals(GolfLie.WATER, islands.lieAt(-46f, 268f))
        assertEquals(GolfLie.FAIRWAY, islands.lieAt(-65f, 425f))
    }

    @Test fun realClubsCanLandSafeOpeningShotsOnEveryLongHole() {
        for (h in WildDetoursCourse.holes.filter { it.par > 3 }) {
            val aim = atan2(h.openingTarget.x, h.openingTarget.z)
            var found = false
            search@ for (club in listOf(GolfClub.WOOD3, GolfClub.WOOD5, GolfClub.HYBRID4, GolfClub.DRIVER)) {
                for (percent in 70..100 step 3) {
                    val g = ClassicGame(h).apply {
                        windX=0f; windZ=0f; this.club=club; aimAngle=aim; hit(percent / 100f)
                    }
                    var steps = 0
                    while (g.state in listOf(GolfState.FLYING, GolfState.ROLLING) && steps++ < 2400) g.update(1f/60f)
                    if (g.state == GolfState.READY && g.lastPenalty == 0 && g.lie == GolfLie.FAIRWAY &&
                        g.ball.z in 140f..h.openingTarget.z + 65f) { found = true; break@search }
                }
            }
            assertTrue("No safe opening on ${h.number}", found)
        }
    }

    @Test fun outsideRouteNeedsAnExtraApproachWhileAttackCanReachTheGreen() {
        val shortcuts = mutableListOf<String>()
        for (h in WildDetoursCourse.holes.filter { it.par > 3 }) {
            // Even the longest club plus generous rollout cannot cover the outside approach.
            // For par 5s this is the second broad shelf, after two conservative shots.
            val safe = if (h.par == 4) h.openingTarget else h.route.filter { it.z in 300f..380f }.single()
                .let { GolfPoint(it.x, h.heightAt(it.x, it.z), it.z) }
            val remaining = hypot(h.cup.x - safe.x, h.cup.z - safe.z) - h.greenRadius
            assertTrue("Outside route skips the extra shot on ${h.number}: $remaining",
                remaining > GolfClub.DRIVER.carry + 15f)
            val setup = h.recommendedLanding(safe)
            assertTrue("Caddie skips the detour ${h.number}", setup != h.cup)
            assertEquals("Caddie setup ${h.number}", GolfLie.FAIRWAY, h.lieAt(setup.x, setup.z))
            val direct = ClassicGame(h).apply {
                restore(safe, 1); windX = 0f; windZ = 0f; club = GolfClub.DRIVER
                aimAngle = atan2(h.cup.x - ball.x, h.cup.z - ball.z)
            }
            for (percent in 85..100) {
                val preview = direct.preview(percent / 100f)
                val end = preview.roll.lastOrNull() ?: preview.landing ?: continue
                if (h.greenSignedDistance(end.x, end.z) <= 0f) shortcuts += "${h.number}/$percent"
            }

            for (a in h.attackLandings.filter { h.par == 4 || it.fromZ > 0f }) {
                assertTrue("Attack still too far from green ${h.number}",
                    hypot(h.cup.x - a.x, h.cup.z - a.z) < GolfClub.DRIVER.carry)
                var reaches = false
                search@ for (club in GolfClub.entries.filter { it != GolfClub.PUTTER }) {
                    val distance = hypot(h.cup.x - a.x, h.cup.z - a.z)
                    if (club.carry < distance * .8f || club.carry > distance * 1.5f) continue
                    for (percent in 65..100 step 2) {
                        val g = ClassicGame(h).apply {
                            restore(GolfPoint(a.x, 0f, a.z), 1)
                            windX = 0f; windZ = 0f; this.club = club
                            aimAngle = atan2(h.cup.x - ball.x, h.cup.z - ball.z)
                            hit(percent / 100f)
                        }
                        var steps = 0
                        while (g.state in listOf(GolfState.FLYING, GolfState.ROLLING) && steps++ < 2400)
                            g.update(1f / 60f)
                        if (g.lastPenalty == 0 && (g.lie == GolfLie.GREEN || g.state == GolfState.HOLED)) {
                            reaches = true; break@search
                        }
                    }
                }
                assertTrue("No real approach from attack shelf ${h.number}: ${a.x}/${a.z}", reaches)
            }
        }
        assertTrue("Outside routes reach green in one approach: $shortcuts", shortcuts.isEmpty())
    }

    @Test fun shortHolesHaveBroadDryBailoutsThatLeaveAChip() {
        for (h in WildDetoursCourse.holes.filter { it.par == 3 }) {
            val apron = h.route.first { it.width >= 40f }
            assertEquals(GolfLie.FAIRWAY, h.lieAt(apron.x, apron.z))
            assertTrue(h.hazards.all { it.signedDistance(apron.x, apron.z) > 5f })
            assertTrue(hypot(h.cup.x - apron.x, h.cup.z - apron.z) in 55f..90f)
        }
    }
}
