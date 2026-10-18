package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

class BilliardPlannerTest {
    private fun length(path: List<V3>)=path.zipWithNext().sumOf { (a,b) -> (b-a).length() }

    @Test fun shortGuideReservesAnExitAfterALongApproachToTheCushion() {
        val table=BilliardTable(TableFamily.CAROM)
        val world=BilliardWorld(table,mutableListOf(table.ball(0,.35,table.width/2)))
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,8.0))
        val path=guide.paths.getValue(0)
        assertEquals(1,guide.events.count { it.kind==EventKind.CUSHION })
        val impact=path.indexOf(checkNotNull(guide.events.first { it.kind==EventKind.CUSHION }.position))
        assertTrue(impact>=0)
        assertEquals(1.2,length(path.drop(impact)),1e-6)
        assertTrue(length(path)<=4.2+1e-8)
        assertTrue(guide.events.last().time-world.time<6.0)
    }

    @Test fun withoutAContactTheGuideStillStopsAtThreeMetres() {
        val table=BilliardTable(TableFamily.SNOOKER)
        val world=BilliardWorld(table,mutableListOf(table.ball(0,.3,table.width/2)))
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,8.0))
        assertEquals(3.0,length(guide.paths.getValue(0)),1e-6)
        assertFalse(guide.events.any { it.kind==EventKind.CUSHION || it.kind==EventKind.BALL })
    }

    @Test fun bothBallsKeepTheirOwnFirstCushionExit() {
        val table=BilliardTable(TableFamily.CAROM)
        val world=BilliardWorld(table,mutableListOf(table.ball(0,.35,.4),table.ball(1,2.1,.425)))
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,4.0))
        val cushions=guide.events.filter { it.kind==EventKind.CUSHION }
        assertEquals(setOf(0,1),cushions.map { it.ball }.toSet())
        for(hit in cushions) {
            val path=guide.paths.getValue(hit.ball)
            val impact=path.indexOf(checkNotNull(hit.position))
            assertTrue(impact>=0)
            assertTrue("Ball ${hit.ball} needs a visible exit",length(path.drop(impact))>.35)
        }
        assertEquals(setOf(0,1),guide.paths.keys)
        assertTrue(length(guide.paths.getValue(0))<=4.2+1e-8)
        assertTrue(length(guide.paths.getValue(1))<=2.1+1e-8)
    }

    @Test fun firstCollisionShowsOnlyTheTwoShortOutgoingBranches() {
        val table=BilliardTable(TableFamily.CAROM)
        val world=BilliardWorld(table,mutableListOf(
            table.ball(0,.35,table.width/2),table.ball(1,1.05,table.width/2+.025)))
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,3.0))
        assertEquals(setOf(0,1),guide.paths.keys)
        val hit=guide.events.first { it.kind==EventKind.BALL }
        val cue=guide.paths.getValue(0)
        val impact=cue.indexOf(checkNotNull(hit.position))
        assertTrue(impact>=0)
        fun branchLimit(id: Int)=if(guide.events.any { it.kind==EventKind.CUSHION && it.ball==id }) 2.1 else .9
        assertTrue(length(cue.drop(impact)) in .05..(branchLimit(0)+1e-8))
        assertTrue(length(guide.paths.getValue(1)) in .05..(branchLimit(1)+1e-8))
        assertTrue(length(cue)<=4.2+1e-8)
    }

    @Test fun guideStopsWhenTheObjectBallReachesAThirdBall() {
        val table=BilliardTable(TableFamily.CAROM)
        val world=BilliardWorld(table,mutableListOf(
            table.ball(0,.35,table.width/2),table.ball(1,1.0,table.width/2),
            table.ball(2,1.35,table.width/2)))
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,4.0))
        assertEquals(setOf(0,1),guide.paths.keys)
        assertEquals(1,guide.events.count { it.kind==EventKind.BALL })
        assertTrue(guide.paths.getValue(1).last().x<=1.35-2*table.radius+1e-6)
        assertTrue(length(guide.paths.getValue(1))<.4)
    }

    @Test fun guideNeverMutatesTheLiveWorld() {
        val table=BilliardTable(TableFamily.POOL)
        val world=BilliardWorld(table,table.rack(Discipline.EIGHT)).also { it.time=42.0 }
        val before=world.balls.map { it.copyDeep() }
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,8.0))
        assertEquals(42.0,guide.startTime,0.0)
        assertEquals(42.0,world.time,0.0)
        assertTrue(world.events.isEmpty())
        before.zip(world.balls).forEach { (a,b) ->
            assertEquals(a.p,b.p); assertEquals(a.v,b.v); assertEquals(a.w,b.w)
            assertEquals(a.motion,b.motion); assertArrayEquals(a.q,b.q,0.0)
        }
    }

    @Test fun cancellationCanStopBeforeAndDuringCalculation() {
        val table=BilliardTable(TableFamily.CAROM)
        val world=BilliardWorld(table,mutableListOf(table.ball(0,.35,table.width/2)))
        assertTrue(BilliardPlanner.preview(world,0,Shot(0.0,8.0)) { true }.paths.isEmpty())
        var checks=0
        val guide=BilliardPlanner.preview(world,0,Shot(0.0,8.0)) { ++checks>=5 }
        assertEquals(5,checks)
        assertTrue(length(guide.paths.getValue(0))<.15)
    }
}
