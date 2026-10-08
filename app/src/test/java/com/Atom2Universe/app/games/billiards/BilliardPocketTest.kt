package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

class BilliardPocketTest {
    private val families=TableFamily.entries.filter { it!=TableFamily.CAROM }

    private fun roll(ball: Ball,velocity: V3) {
        ball.v=velocity
        ball.w=V3(-velocity.y/ball.radius,velocity.x/ball.radius)
        ball.motion=Motion.ROLLING
    }

    @Test fun allEightCornerFacingsReflectBackIntoTheOpening() {
        for(family in families) {
            val table=BilliardTable(family)
            val mouth=table.mouth
            val nose=V3(mouth,0.0)
            val contact=nose+(V3(.015,-mouth*.8)-nose)*.15
            // Approach the rubber from the open throat, independently of rail.normal.
            val towardRubber=V3(mouth*.8,.015-mouth).unit()
            for(swap in listOf(false,true)) for(flipX in listOf(false,true)) for(flipY in listOf(false,true)) {
                fun point(p: V3): V3 {
                    val q=if(swap) V3(p.y,p.x) else p
                    return V3(if(flipX) table.length-q.x else q.x,if(flipY) table.width-q.y else q.y)
                }
                fun direction(v: V3): V3 {
                    val q=if(swap) V3(v.y,v.x) else v
                    return V3(if(flipX) -q.x else q.x,if(flipY) -q.y else q.y)
                }
                val start=point(contact-towardRubber*(table.radius+.001))
                val incoming=direction(towardRubber)
                val ball=table.ball(1,start.x,start.y)
                roll(ball,incoming)
                val energy=ball.energy()
                val world=BilliardWorld(table,mutableListOf(ball))
                world.advance(.006)
                val description="$family / swap=$swap / x=$flipX / y=$flipY"
                assertTrue(description,world.events.any { it.kind==EventKind.CUSHION })
                assertTrue(description,ball.v.dot(incoming)<0.0)
                assertTrue(description,ball.energy()<=energy+1e-9)
                assertFalse(description,world.events.any { it.kind==EventKind.OFF_TABLE })
            }
        }
    }

    @Test fun centeredShotsFallIntoEveryPocketAtLowAndHighSpeeds() {
        for(family in families) {
            val table=BilliardTable(family)
            for(pocket in table.pockets) for(speed in listOf(.8,4.0,12.0)) {
                val ball=table.ball(1,table.length/2,table.width/2)
                // Not pocket.center: it lies behind the corner, so from the middle
                // of a 2:1 table that line hits the long cushion just before the
                // point. Such a rail-first shot only drops when slow, as on a real table.
                roll(ball,(table.opening(pocket)-ball.p).planar().unit()*speed)
                val world=BilliardWorld(table,mutableListOf(ball))
                world.advance(8.0)
                val description="$family / pocket=${pocket.id} / speed=$speed"
                assertEquals(description,Motion.POCKETED,ball.motion)
                assertEquals(description,listOf(pocket.id),world.events.filter { it.kind==EventKind.POCKET }.map { it.other })
                assertFalse(description,world.events.any { it.kind==EventKind.OFF_TABLE })
            }
        }
    }

    @Test fun everyOpeningLiesHalfwayBetweenItsTwoCushionPoints() {
        for(size in BilliardTableSize.entries.filter { it.family!=TableFamily.CAROM }) {
            val table=BilliardTable(size.family,size=size)
            val points=table.cushionPaths.flatMap { listOf(it[1],it[2]) }
            for(pocket in table.pockets) {
                val nearest=points.sortedBy { (it-pocket.center).planar().length() }.take(2)
                val middle=(nearest[0]+nearest[1])*.5
                assertEquals("$size / pocket=${pocket.id}",0.0,(table.opening(pocket)-middle).length(),1e-12)
            }
        }
    }

    @Test fun aBallAtRestInsideTheDropIsCapturedOnce() {
        val table=BilliardTable(TableFamily.BLACKBALL)
        val pocket=table.pockets[1]
        val ball=table.ball(1,pocket.center.x,pocket.center.y+pocket.radius*.9)
        val world=BilliardWorld(table,mutableListOf(ball))
        world.advance(.01); world.advance(.01)
        assertEquals(Motion.POCKETED,ball.motion)
        assertEquals(listOf(EventKind.POCKET),world.events.map { it.kind })
        assertEquals(pocket.id,world.events.single().other)
    }

    @Test fun aJumpLandingOnTheWoodCannotRollInsideTheCabinet() {
        val table=BilliardTable(TableFamily.BLACKBALL)
        val ball=table.ball(1,table.length*.25,-.08).apply {
            p=p.copy(z=radius*3); v=V3(z=-.4); motion=Motion.AIRBORNE
        }
        val world=BilliardWorld(table,mutableListOf(ball))
        world.advance(.3)
        assertEquals(Motion.POCKETED,ball.motion)
        assertEquals(1,world.events.count { it.kind==EventKind.OFF_TABLE })
        assertFalse(world.events.any { it.kind==EventKind.POCKET })
    }

    @Test fun restoringAnEmbeddedBallPreservesScoresAndAvoidsOtherBalls() {
        val session=BilliardSession(Discipline.BLACKBALL,PlayMode.LOCAL)
        val table=session.table
        session.match.apply { score0=2; score1=7; group0=1; breaking=false; ballInHand=false }
        session.world.balls.first { it.id==1 }.p=V3(table.length*.25,-.08,table.radius)
        session.world.balls.first { it.id==2 }.p=V3(table.length*.25,table.radius+.001,table.radius)
        session.restore(session.snapshot())
        val ball=session.world.balls.first { it.id==1 }
        assertTrue(table.canPlace(ball.p,session.world.balls,ball.id))
        assertEquals(Motion.STILL,ball.motion)
        assertEquals(2,session.match.score0); assertEquals(7,session.match.score1)
        assertEquals(1,session.match.group0)
        assertTrue(session.world.events.isEmpty())
    }
}
