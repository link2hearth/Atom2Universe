package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

class BilliardArcadeAssessmentTest {
    private fun event(kind: EventKind,ball: Int,other: Int,time: Double)=
        BilliardEvent(kind,ball,other,1.0,time)

    /** Exercise the real end-of-shot path with a recorded event sequence. */
    private fun play(session: BilliardSession,events: List<BilliardEvent>): SessionSnapshot {
        val before=session.snapshot()
        assertTrue(session.shoot(Shot(0.0,.05)))
        session.world.balls.forEach { it.stop() }
        events.filter { it.kind==EventKind.POCKET || it.kind==EventKind.OFF_TABLE }.forEach { event ->
            session.world.balls.first { it.id==event.ball }.motion=Motion.POCKETED
        }
        events.filter { it.kind==EventKind.PIN }.forEach { session.world.pins[it.other].down=true }
        session.world.events.addAll(events)
        session.update(0.0)
        return before
    }

    private fun assess(discipline: Discipline,events: List<BilliardEvent>): BilliardArcadeAssessment.Score {
        val session=BilliardSession(discipline,PlayMode.PRACTICE,trackVisualRotation=false)
        val before=play(session,events)
        return BilliardArcadeAssessment.score(before,session,PlayMode.PRACTICE,0.0)
    }

    @Test fun earlyEightLossCannotBeRewardedAsAPot() {
        val session=BilliardSession(Discipline.EIGHT,PlayMode.LOCAL,trackVisualRotation=false)
        session.restore(session.snapshot().copy(match=MatchState(group0=1,breaking=false)))
        session.nominated=8; session.calledPocket=0
        val before=play(session,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.POCKET,1,0,.2),event(EventKind.POCKET,8,0,.3)))
        val lost=BilliardArcadeAssessment.score(before,session,PlayMode.LOCAL,0.0)
        val legalMiss=BilliardArcadeAssessment.Score(0,true,0,false,0.0,1.0)
        assertEquals(1,session.match.winner)
        assertTrue(lost<legalMiss)
    }

    @Test fun practicePoolScratchCannotBenefitFromExtraPots() {
        val scratch=assess(Discipline.EIGHT,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.POCKET,1,0,.2),event(EventKind.POCKET,0,1,.3)))
        val clean=assess(Discipline.EIGHT,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.CUSHION,1,0,.2)))
        assertFalse(scratch.legal)
        assertEquals(0,scratch.netPoints)
        assertTrue(clean>scratch)
    }

    @Test fun practiceEnglishAndPyramidKeepLegalInOffs() {
        val english=assess(Discipline.ENGLISH,listOf(event(EventKind.BALL,0,2,.1),
            event(EventKind.POCKET,0,0,.2)))
        val pyramid=assess(Discipline.PYRAMID_FREE,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.POCKET,0,0,.2)))
        assertTrue(english.legal); assertEquals(3,english.netPoints)
        assertTrue(pyramid.legal); assertEquals(1,pyramid.netPoints)
    }

    @Test fun practiceThreeCushionUsesBandsBeforeSecondObject() {
        val beginning=listOf(event(EventKind.BALL,0,1,.1),event(EventKind.CUSHION,0,0,.2),
            event(EventKind.CUSHION,0,1,.3))
        val late=assess(Discipline.THREE_CUSHION,beginning+listOf(
            event(EventKind.BALL,0,2,.4),event(EventKind.CUSHION,0,2,.5)))
        val complete=assess(Discipline.THREE_CUSHION,beginning+listOf(
            event(EventKind.CUSHION,0,2,.4),event(EventKind.BALL,0,2,.5)))
        assertEquals(0,late.netPoints); assertEquals(1,complete.netPoints)
        assertTrue(complete>late)
    }

    @Test fun practicePinsUsesKnockdownEventsEvenAfterPinsWereReset() {
        val own=assess(Discipline.FIVE_PINS,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.PIN,0,1,.2)))
        val objectPin=assess(Discipline.FIVE_PINS,listOf(event(EventKind.BALL,0,1,.1),
            event(EventKind.PIN,1,1,.2)))
        assertFalse(own.legal); assertEquals(-2,own.netPoints)
        assertTrue(objectPin.legal); assertEquals(2,objectPin.netPoints)
        assertTrue(objectPin>own)
    }

    @Test fun firstContactStillUsesOriginalCueAfterAChangeOfTurn() {
        val session=BilliardSession(Discipline.FREE,PlayMode.LOCAL,trackVisualRotation=false)
        play(session,listOf(event(EventKind.BALL,0,2,.1)))
        assertEquals(1,session.cueId)
        assertEquals(2,BilliardArcadeAssessment.firstContact(session))
    }

    @Test fun artisticSuccessKeepsItsScoreAndContactAfterTheNextDrillWasCreated() {
        val session=BilliardSession(Discipline.ARTISTIC,PlayMode.LOCAL,trackVisualRotation=false)
        val events=listOf(event(EventKind.BALL,0,1,.1),event(EventKind.BALL,0,2,.2))
        val before=play(session,events)
        assertEquals(1,session.drill)
        assertTrue(session.world.events.isEmpty())
        val score=BilliardArcadeAssessment.score(before,session,PlayMode.LOCAL,.5,events)
        assertEquals(1,score.netPoints)
        assertTrue(score.legal)
        assertEquals(1,BilliardArcadeAssessment.firstContact(session,events))
    }

    @Test fun practiceCanUseACapturedLogWhenTheWorldLogWasCleared() {
        val session=BilliardSession(Discipline.FREE,PlayMode.PRACTICE,trackVisualRotation=false)
        val events=listOf(event(EventKind.BALL,0,1,.1),event(EventKind.BALL,0,2,.2))
        val before=play(session,events)
        session.world.events.clear()
        val score=BilliardArcadeAssessment.score(before,session,PlayMode.PRACTICE,.5,events)
        assertEquals(1,score.netPoints)
        assertTrue(score.legal)
    }

    @Test fun insignificantPositionGainDoesNotJustifyAFullCorrection() {
        val original=BilliardArcadeAssessment.Score(0,true,0,false,.50,0.0)
        val gratuitous=BilliardArcadeAssessment.Score(0,true,0,false,.501,1.0)
        val scoring=BilliardArcadeAssessment.Score(0,true,1,false,0.0,1.0)
        assertTrue(original>gratuitous)
        assertTrue(scoring>original)
    }
}
