package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GolfCupFunnelTest {
    private fun game(radius:Float,arrival:Float,offset:Float,angle:Float=0f):Pair<ClassicGame,Float> {
        val hole=ClassicHole(1,4,300f,cupRadius=radius,greenShape=GolfGreenShape(slopeX=0f,slopeZ=0f))
        val g=ClassicGame(hole)
        g.restore(GolfPoint(hole.cup.x-sin(angle)*1.2f+cos(angle)*offset,0f,
            hole.cup.z-cos(angle)*1.2f-sin(angle)*offset),0)
        g.club=GolfClub.PUTTER;g.aimAngle=angle
        val power=(1.2f+arrival*arrival/(2f*GolfBallPhysics.rolling(GolfLie.GREEN)))/hole.puttRange
        return g to power
    }

    private fun finish(g:ClassicGame,dt:Float=1f/120f) {
        var frames=0
        while(g.state==GolfState.ROLLING || g.state==GolfState.FLYING) {
            assertTrue("Shot must finish",frames++<4000)
            g.update(dt)
        }
    }

    @Test fun gentleGrazesFallFromEverySideForDifferentCupSizes() {
        for(radius in listOf(.054f,.108f,.162f,.22f)) for(speed in listOf(.25f,.8f,1.5f)) for(side in 0..3) {
            val (g,power)=game(radius,speed,radius+ClassicHole.BALL_RADIUS*.65f,side*PI.toFloat()/2f)
            g.hit(power);finish(g)
            assertEquals("radius $radius speed $speed side $side",GolfState.HOLED,g.state)
            assertTrue(g.ball.y<g.hole.cup.y-ClassicHole.BALL_RADIUS)
            assertTrue(g.distanceToCup<radius)
            assertEquals(1,g.strokes)
        }
    }

    @Test fun aWideMissAndAFastGrazeAreNotPulledIntoTheCup() {
        for((speed,offset) in listOf(.5f to .11f,3.5f to .065f)) {
            val (g,power)=game(.054f,speed,offset)
            g.hit(power);finish(g)
            assertEquals(GolfState.READY,g.state)
            assertTrue(g.ball.z>g.hole.cup.z)
        }
    }

    @Test fun funnelIsContinuousAndIndependentOfFrameGroupingIncludingFastForward() {
        val finals=listOf(1f/120f,1f/60f,1f/30f,.15f).map { dt ->
            val (g,power)=game(.054f,1.2f,.066f)
            g.hit(power)
            var frames=0
            while(g.state==GolfState.ROLLING || g.state==GolfState.FLYING) {
                assertTrue(frames++<4000)
                val previous=g.ball
                g.update(dt)
                assertTrue("No teleport",hypot(g.ball.x-previous.x,g.ball.z-previous.z)<3f*dt+.002f)
            }
            assertEquals(GolfState.HOLED,g.state)
            g.ball
        }
        finals.forEach { assertEquals(finals.first().x,it.x,.00001f);assertEquals(finals.first().z,it.z,.00001f) }
    }

    @Test fun guideFinishesInTheCupForAssistedEdgeContacts() {
        val (g,power)=game(.054f,.8f,.065f)
        val guide=g.preview(power)
        assertEquals(g.hole.cup.x,guide.landing!!.x,.00001f)
        assertEquals(g.hole.cup.z,guide.landing!!.z,.00001f)
        g.hit(power);finish(g)
        assertEquals(GolfState.HOLED,g.state)
    }
}
