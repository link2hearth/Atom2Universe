package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Replays measured shots through hit(), with the real wind, cup and equipment. No search. */
class GardensScoringTest {
    private data class Shot(val hole: Int, val route: String, val club: GolfClub,
        val aim: Float, val power: Float, val spin: Float)
    private val shots = checkNotNull(javaClass.getResourceAsStream("/gardens-scoring.csv"))
        .bufferedReader().useLines { lines -> lines.drop(1).map { line ->
            val c=line.split(',')
            Shot(c[0].toInt(),c[1],GolfClub.valueOf(c[3]),c[4].toFloat(),c[5].toFloat(),c[6].toFloat())
        }.toList() }

    private fun hit(g: ClassicGame, s: Shot, aimError: Float=0f, powerError: Float=0f, timing: Float=0f) {
        g.club=s.club;g.aimAngle=s.aim+aimError;g.setSpin(0f,s.spin)
        assertTrue(g.hit((s.power+powerError).coerceIn(.01f,1f),timing))
        var frames=0
        while(g.state in listOf(GolfState.FLYING,GolfState.ROLLING) && frames++<2400)g.update(1f/60f)
        assertTrue("Shot must settle on ${g.hole.number}",g.state in listOf(GolfState.READY,GolfState.HOLED))
    }

    @Test fun everyHoleOffersABirdieAndAllParFivesPlusThirteenOfferEagles() {
        for(h in ClassicCourse.holes) {
            val g=ClassicGame(h)
            val route=shots.filter { it.hole==h.number && it.route=="score" }
            assertTrue(route.isNotEmpty())
            for(s in route) {
                hit(g,s)
                assertEquals("Penalty on ${h.number}",0,g.lastPenalty)
                assertTrue("Playable route ${h.number}",g.lie in listOf(GolfLie.FAIRWAY,GolfLie.GREEN))
            }
            assertEquals("Must actually hole, not merely approach: ${h.number}",GolfState.HOLED,g.state)
            val target=h.par-if(h.par==5 || h.number==13)2 else 1
            assertTrue("Birdie/eagle target ${h.number}: ${g.strokes} > $target",g.strokes<=target)
        }
    }

    @Test fun allFourParThreesCanBeHoledInOneWithNormalIronsOrWedges() {
        val aces=shots.filter { it.route=="ace" }
        assertEquals(ClassicCourse.holes.filter { it.par==3 }.map { it.number },aces.map { it.hole })
        for(s in aces) {
            assertTrue(s.club.ordinal in GolfClub.IRON5.ordinal..GolfClub.LW.ordinal)
            val g=ClassicGame(ClassicCourse.holes[s.hole-1])
            hit(g,s)
            assertEquals("Ace ${s.hole}",GolfState.HOLED,g.state)
            assertEquals(1,g.strokes)
            assertEquals(0,g.lastPenalty)
        }
    }

    @Test fun broadTargetsForgiveSmallDirectionPowerAndTimingErrors() {
        val report=StringBuilder("hole,stroke,samples,short_grass,no_penalty\n")
        val misses=StringBuilder("hole,stroke,angle,power,timing,x,z,lie\n")
        val failures=mutableListOf<String>()
        for(h in ClassicCourse.holes) {
            val actual=ClassicGame(h)
            for(s in shots.filter { it.hole==h.number && it.route=="score" && it.club!=GolfClub.PUTTER }) {
                var shortGrass=0;var safe=0;var samples=0
                for(angle in listOf(-2f,0f,2f)) for(power in listOf(-.04f,0f,.04f)) for(timing in listOf(-.2f,0f,.2f)) {
                    val g=ClassicGame(h).apply { restore(actual.ball,actual.strokes) }
                    hit(g,s,angle*PI.toFloat()/180f,power,timing)
                    samples++
                    if(g.lastPenalty==0) {
                        safe++
                        if(g.lie in listOf(GolfLie.FAIRWAY,GolfLie.GREEN))shortGrass++
                        else misses.append("${h.number},${actual.strokes+1},$angle,$power,$timing,${g.ball.x},${g.ball.z},${g.lie}\n")
                    }
                }
                report.append("${h.number},${actual.strokes+1},$samples,$shortGrass,$safe\n")
                if(shortGrass<samples*.8f)failures+="Forgiving target ${h.number}/${actual.strokes+1}: $shortGrass/$samples"
                if(safe!=samples)failures+="Hazard for small miss ${h.number}/${actual.strokes+1}: $safe/$samples safe"
                hit(actual,s)
            }
        }
        File("build/reports/gardens-scoring").mkdirs()
        File("build/reports/gardens-scoring/tolerance.csv").writeText(report.toString())
        File("build/reports/gardens-scoring/misses.csv").writeText(misses.toString())
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun firstCourseKeepsWideContinuousFairwaysAndLargeGentleTargets() {
        val holes=ClassicCourse.holes
        assertEquals(72,holes.sumOf{it.par})
        assertTrue(holes.sumOf{it.length.toDouble()} in 5000.0..5400.0)
        for(h in holes) {
            assertTrue(h.greenRadius>=21f)
            assertTrue(hypot(h.greenShape.slopeX,h.greenShape.slopeZ)<=.015f)
            if(h.par==3)continue
            assertTrue("Wide opening ${h.number}",h.fairwayWidth(h.landingZ)>=70f)
            for(z in 100..h.length.toInt() step 5) {
                assertTrue(h.fairwayWidth(z.toFloat())>=45f)
                assertTrue("Continuous dry centre ${h.number}",h.lieAt(h.fairwayCenter(z.toFloat()),z.toFloat()) in
                    listOf(GolfLie.FAIRWAY,GolfLie.GREEN))
            }
        }
    }

    @Test fun collectingGreensAndBanksBelongOnlyToTheFirstCourse() {
        for (h in ClassicCourse.holes) {
            assertEquals(ClassicHole.CUP_RADIUS * 2f, h.cupRadius, .000001f)
            val unbanked = h.copy(retainingBankHeight = 0f)
            var raisedEdges = 0
            for (z in 100..h.length.toInt() step 10) for (side in listOf(-1f, 1f)) {
                val x = h.fairwayCenter(z.toFloat()) + side * (h.fairwayWidth(z.toFloat()) * .5f + 8f)
                if (h.greenSignedDistance(x, z.toFloat()) < 15f ||
                    h.hazards.any { it.signedDistance(x, z.toFloat()) < 5f }) continue
                if (h.heightAt(x, z.toFloat()) - unbanked.heightAt(x, z.toFloat()) > .8f) raisedEdges++
            }
            if (h.par > 3) assertTrue("Retaining fairway banks ${h.number}", raisedEdges > 0)
            for (i in 0..15) {
                val angle = i * PI.toFloat() / 8f
                var previous = h.cup.y
                for (r in 1..16) {
                    val y = h.heightAt(h.cup.x + cos(angle) * r, h.cup.z + sin(angle) * r)
                    assertTrue("Bowl slopes toward cup ${h.number}/$i/$r", y >= previous)
                    previous = y
                }
                assertTrue(previous - h.cup.y > .15f)
            }
        }
        for (h in HeatherCourse.holes) {
            assertEquals(ClassicHole.CUP_RADIUS, h.cupRadius, 0f)
            assertEquals(0f, h.cupBowlDepth, 0f)
            assertEquals(0f, h.retainingBankHeight, 0f)
        }
    }

    @Test fun doubleWidthCupAcceptsPuttsThatMissARegulationCup() {
        fun putt(radius: Float): ClassicGame {
            val hole = ClassicHole(1, 4, 300f, cupRadius = radius,
                greenShape = GolfGreenShape(slopeX = 0f, slopeZ = 0f))
            return ClassicGame(hole).apply {
                restore(GolfPoint(.075f, 0f, 299f), 0)
                club = GolfClub.PUTTER; aimAngle = 0f
                hit((2f * GolfBallPhysics.rolling(GolfLie.GREEN) + .25f) /
                    (2f * GolfBallPhysics.rolling(GolfLie.GREEN) * GolfClub.PUTTER.carry))
                repeat(2400) { if (state == GolfState.ROLLING) update(1f / 60f) }
            }
        }
        assertEquals(GolfState.READY, putt(ClassicHole.CUP_RADIUS).state)
        assertEquals(GolfState.HOLED, putt(ClassicHole.CUP_RADIUS * 2f).state)
    }
}
