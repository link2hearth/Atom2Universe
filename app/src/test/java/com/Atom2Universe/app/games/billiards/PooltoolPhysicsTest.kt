package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PooltoolPhysicsTest {
    @Test fun centerStrikeMatchesPooltoolClosedForm() {
        val b=Ball(0,V3(z=.028575)); val s=Shot(0.0,speed=2.0)
        PooltoolPhysics.strike(b,s)
        assertEquals(4/(1+b.mass/s.cueMass),b.v.x,1e-10)
        assertEquals(0.0,b.w.length(),1e-10)
    }
    @Test fun slidingEndsAtFiveSeventhsOfInitialSpeed() {
        val b=Ball(0,V3(z=.028575),v=V3(1.0),motion=Motion.SLIDING)
        val c=Cloth(); val t=PooltoolPhysics.transitionTime(b,c)
        PooltoolPhysics.evolve(b,c,t)
        assertEquals(5.0/7,b.v.x,1e-10)
        assertEquals(b.v.x/b.radius,b.w.y,1e-9)
        assertEquals(Motion.ROLLING,b.motion)
    }
    @Test fun motionAcrossTransitionsIsIndependentOfStepSize() {
        val a=Ball(0,V3(z=.028575),v=V3(1.0,.3),w=V3(2.0,-8.0,15.0),motion=Motion.SLIDING)
        val b=a.copyDeep(); val c=Cloth()
        PooltoolPhysics.evolve(a,c,12.0)
        repeat(1200) { PooltoolPhysics.evolve(b,c,.01) }
        assertEquals(a.p.x,b.p.x,1e-8); assertEquals(a.p.y,b.p.y,1e-8)
        assertEquals(Motion.STILL,a.motion); assertEquals(Motion.STILL,b.motion)
    }
    @Test fun contactConservesMomentumAndDoesNotCreateEnergy() {
        for(k in 0..60) {
            val a=Ball(0,V3(0.0,0.0,.028575),v=V3(2.0,.2),w=V3(3.0,-20.0,k-30.0))
            val b=Ball(1,V3(.05715,0.0,.028575),v=V3(-.2,.3),w=V3(-2.0,5.0,8.0))
            val momentum=a.v+b.v; val energy=a.energy()+b.energy()
            PooltoolPhysics.collide(a,b,.95)
            assertTrue(a.energy()+b.energy() <= energy+1e-10)
            assertEquals(momentum.x,(a.v+b.v).x,1e-10)
            assertEquals(momentum.y,(a.v+b.v).y,1e-10)
        }
    }
    @Test fun fastBallsCannotPassThroughOneAnother() {
        val t=BilliardTable(TableFamily.CAROM)
        val a=t.ball(0,.3,t.width/2).also { it.v=V3(12.0); it.classify() }
        val b=t.ball(1,.7,t.width/2)
        val w=BilliardWorld(t,mutableListOf(a,b)); w.advance(.08)
        assertTrue(w.events.any { it.kind==EventKind.BALL })
        assertTrue(b.v.x>1.0)
    }
    @Test fun allRacksFitWithoutOverlap() {
        Discipline.entries.forEach { d ->
            val t=BilliardTable(d.family); val balls=t.rack(d)
            balls.forEach { assertTrue(t.inside(it.p)) }
            for(i in balls.indices) for(j in i+1 until balls.size)
                assertTrue("$d: ${balls[i].id}/${balls[j].id}", (balls[i].p-balls[j].p).length() >= t.radius*2-1e-8)
        }
    }
    @Test fun energyDecaysOnClothIncludingAngularEnergy() {
        val b=Ball(0,V3(z=.028575),v=V3(3.0,1.0),w=V3(15.0,-50.0,40.0),motion=Motion.SLIDING)
        repeat(2000) { val old=b.energy(); PooltoolPhysics.evolve(b,Cloth(),.01); assertTrue(b.energy() <= old+1e-10) }
        assertEquals(Motion.STILL,b.motion)
    }
    @Test fun hanCushionIsPassiveAndMirrorSymmetric() {
        for(k in 1..89) {
            val angle=k*PI/180
            val a=Ball(0,V3(z=.028575),v=V3(cos(angle),sin(angle)),motion=Motion.SLIDING)
            val b=a.copyDeep().apply { v=v.copy(y=-v.y) }
            val energy=a.energy()
            PooltoolPhysics.cushion(a,V3(1.0),Cloth()); PooltoolPhysics.cushion(b,V3(1.0),Cloth())
            assertTrue(a.energy()<=energy+1e-10)
            assertEquals(a.v.x,b.v.x,1e-10); assertEquals(a.v.y,-b.v.y,1e-10)
        }
    }
    @Test fun landingDoesNotCreateTotalEnergy() {
        val b=Ball(0,V3(z=.028575),v=V3(2.0,.4,-3.0),w=V3(15.0,-40.0,20.0),motion=Motion.AIRBORNE)
        val before=b.energy(); PooltoolPhysics.land(b)
        assertTrue(b.energy()<=before+1e-10)
        assertTrue(b.v.z>=0); assertEquals(b.radius,b.p.z,1e-12)
    }
    @Test fun drawAndFollowHaveOppositeInitialSpin() {
        val draw=Ball(0,V3(z=.028575)); val follow=draw.copyDeep()
        PooltoolPhysics.strike(draw,Shot(0.0,top=-.5))
        PooltoolPhysics.strike(follow,Shot(0.0,top=.5))
        assertTrue(draw.w.y<0); assertTrue(follow.w.y>0)
    }
}
