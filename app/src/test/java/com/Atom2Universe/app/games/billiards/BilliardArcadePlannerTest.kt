package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BilliardArcadePlannerTest {
    private fun score(points: Int,correction: Double)=
        BilliardArcadeAssessment.Score(0,true,points,true,0.0,correction)

    @Test fun betterTimingNeverSelectsAWorseEvaluatedOutcomeAndAMissIsExact() {
        val original=Shot(1.2,3.0,side=.2,top=-.1,elevation=15.0)
        val small=original.copy(angle=1.201)
        val large=original.copy(speed=3.1)
        val foul=original.copy(top=-.11)
        val candidates=listOf(BilliardArcadePlanner.Candidate(original,0.0,score(0,0.0)),
            BilliardArcadePlanner.Candidate(small,.35,score(1,.35)),
            BilliardArcadePlanner.Candidate(large,1.0,score(2,1.0)),
            BilliardArcadePlanner.Candidate(foul,.7,BilliardArcadeAssessment.Score(0,false,10,false,1.0,.7)))
        val plan=BilliardArcadePlanner.Plan(original,candidates)
        assertEquals(original,plan.select(0.0))
        assertEquals(original,plan.select(.34))
        assertEquals(small,plan.select(.35))
        assertEquals(large,plan.select(1.0))
        var previous=candidates.first().score
        for(percent in 0..100) {
            val next=candidates.first { it.shot==plan.select(percent/100.0) }.score
            assertTrue(next>=previous); previous=next
        }
        assertEquals(original,BilliardArcadePlanner.Plan(original,emptyList()).select(1.0))
        assertEquals(original,plan.select(Double.NaN))
    }

    @Test fun timingZoneHasAnHonestPerfectWindowAndNoAssistanceOutsideIt() {
        assertEquals(1.0,billiardArcadeQuality(ARCADE_TIMING_TARGET),0.0)
        assertEquals(0.0,billiardArcadeQuality(0f),0.0)
        assertEquals(0.0,billiardArcadeQuality(1f),0.0)
        val near=billiardArcadeQuality(ARCADE_TIMING_TARGET+.1f)
        val far=billiardArcadeQuality(ARCADE_TIMING_TARGET+.2f)
        assertTrue(near>far && far>0)
        assertEquals(near,billiardArcadeQuality(ARCADE_TIMING_TARGET-.1f),1e-6)
    }

    @Test fun localVariantsPreserveCueAndElevationAndRespectPowerSpinAndAngleBounds() {
        val original=Shot(.4,8.0,side=.8,elevation=40.0,cueMass=.51,endMass=.009)
        val limits=BilliardArcadePlanner.Limits(.01,.64)
        val adjusted=BilliardArcadePlanner.variant(original,limits,1.0,1.0,1.0,0.0)
        assertEquals(original.cueMass,adjusted.cueMass,0.0)
        assertEquals(original.endMass,adjusted.endMass,0.0)
        assertEquals(original.elevation,adjusted.elevation,0.0)
        assertEquals(8.0,adjusted.speed,0.0)
        assertEquals(.8,adjusted.side,1e-9)
        assertTrue(BilliardArcadePlanner.radius(original,adjusted,limits)<=1.0+1e-9)
    }

    @Test fun cancellationPublishesNoUnfinishedResultAndDoesNotMutateTheTable() {
        val session=BilliardSession(Discipline.FREE,PlayMode.PRACTICE)
        val before=session.snapshot()
        val request=BilliardArcadePlanner.Request(session.discipline,session.mode,session.clothSpeed,
            session.table.size,before,Shot(0.0,.3))
        var publications=0
        BilliardArcadePlanner.prepare(request,{ publications++ }) { true }
        assertEquals(0,publications)
        assertEquals(before.match,session.match)
        before.balls.zip(session.world.balls).forEach { (a,b) -> assertEquals(a.p,b.p) }
    }

    @Test fun workerKeepsOnlyTheLatestPendingAimAndInvalidatesOldResults() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1); val published=CountDownLatch(1)
        val results=Collections.synchronizedList(mutableListOf<Shot>())
        val session=BilliardSession(Discipline.EIGHT,PlayMode.LOCAL)
        val request=BilliardArcadePlanner.Request(session.discipline,session.mode,session.clothSpeed,
            session.table.size,session.snapshot().copy(safety=true),Shot(0.0))
        val worker=BilliardArcadePreparation { entered.countDown(); release.await() }
        try {
            val receive: (BilliardArcadePlanner.Plan)->Unit = { results+=it.original; published.countDown() }
            worker.submit(worker.invalidate(),request,receive)
            assertTrue(entered.await(3,TimeUnit.SECONDS))
            worker.submit(worker.invalidate(),request.copy(shot=Shot(.1)),receive)
            val latest=Shot(.2)
            worker.submit(worker.invalidate(),request.copy(shot=latest),receive)
            release.countDown()
            assertTrue(published.await(3,TimeUnit.SECONDS))
            assertEquals(listOf(latest),results.toList())
        } finally { release.countDown(); worker.close() }
    }
}
