package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

/** Feed measured-event equivalents into the referee to test rules independently of aim. */
class BilliardRulesTest {
    private fun event(kind: EventKind,ball: Int,other: Int,time: Double)=BilliardEvent(kind,ball,other,1.0,time)
    private fun play(s: BilliardSession,events: List<BilliardEvent>) {
        if(s.match.decision!=ShotDecision.NONE) s.decide(true)
        if(s.needsCall && s.nominated<0) {
            val pot=events.firstOrNull { it.kind==EventKind.POCKET && it.ball!=s.cueId }
            s.nominated=pot?.ball ?: s.legalTargets().first().id; s.calledPocket=pot?.other ?: 0
        }
        if(s.needsCall && s.discipline==Discipline.BANK && s.bankRails.isEmpty()) s.bankRails=listOf(0)
        assertTrue(s.shoot(Shot(0.0,.05)))
        s.world.balls.forEach { it.stop() }
        events.filter { it.kind==EventKind.POCKET || it.kind==EventKind.OFF_TABLE }.forEach { e -> s.world.balls.first { it.id==e.ball }.motion=Motion.POCKETED }
        events.filter { it.kind==EventKind.PIN }.forEach { e -> s.world.pins[e.other].down=true }
        s.world.events.addAll(events); s.update(0.0)
    }
    @Test fun caromCountsContactsAndUsesOtherCueOnMiss() {
        val s=BilliardSession(Discipline.FREE,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,2,.1),event(EventKind.BALL,0,1,.2)))
        assertEquals(1,s.match.score0); assertEquals(0,s.match.player); assertEquals(0,s.cueId)
        play(s,listOf(event(EventKind.BALL,0,1,.1)))
        assertEquals(1,s.match.player); assertEquals(1,s.cueId)
    }
    @Test fun threeCushionsMustPrecedeSecondObjectContact() {
        val s=BilliardSession(Discipline.THREE_CUSHION,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.CUSHION,0,0,.2),
            event(EventKind.CUSHION,0,1,.3),event(EventKind.BALL,0,2,.4),event(EventKind.CUSHION,0,2,.5)))
        assertEquals(0,s.match.score0)
    }
    @Test fun nineOnCombinationWinsAfterLowestBallContact() {
        val s=BilliardSession(Discipline.NINE,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.BALL,1,9,.2),event(EventKind.POCKET,9,0,.3)))
        assertEquals(0,s.match.winner)
    }
    @Test fun earlyEightLosesEvenIfAnotherBallWasPotted() {
        val s=BilliardSession(Discipline.EIGHT,PlayMode.LOCAL)
        s.restore(s.snapshot().copy(match=MatchState(group0=1,breaking=false)))
        s.nominated=8; s.calledPocket=0
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,0,.2),event(EventKind.POCKET,8,0,.3)))
        assertEquals(1,s.match.winner)
    }
    @Test fun tenOnBreakIsRespotted() {
        val s=BilliardSession(Discipline.TEN,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,10,0,.3)))
        assertEquals(-1,s.match.winner)
        assertEquals(Motion.STILL,s.world.balls.first { it.id==10 }.motion)
    }
    @Test fun ownCueKnockingPinAwardsOpponent() {
        val s=BilliardSession(Discipline.FIVE_PINS,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.PIN,0,1,.2)))
        assertEquals(Foul.OWN_PIN,s.match.foul)
        assertEquals(2,s.match.score1); assertEquals(0,s.match.score0)
        assertFalse(s.match.ballInHand)
    }
    @Test fun snookerAlternatesRedAndColourAndRespotsColour() {
        val s=BilliardSession(Discipline.SIX_RED,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,0,.2)))
        assertEquals(1,s.match.score0); assertTrue(s.match.snookerColor)
        s.nominated=21
        play(s,listOf(event(EventKind.BALL,0,21,.1),event(EventKind.POCKET,21,0,.2)))
        assertEquals(8,s.match.score0); assertFalse(s.match.snookerColor)
        assertEquals(Motion.STILL,s.world.balls.first { it.id==21 }.motion)
    }
    @Test fun scratchRespotsCueAndGrantsBallInHand() {
        val s=BilliardSession(Discipline.NINE,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,0,0,.2)))
        assertEquals(Foul.SCRATCH,s.match.foul); assertTrue(s.match.ballInHand)
        assertNotNull(s.cue); assertEquals(1,s.match.player)
    }
    @Test fun practiceDoesNotApplyMatchFoulsOrMoveOpponent() {
        val s=BilliardSession(Discipline.THREE_CUSHION,PlayMode.PRACTICE)
        play(s,emptyList())
        assertEquals(Foul.NONE,s.match.foul); assertEquals(0,s.match.player)
        assertTrue(s.moveBall(2,V3(.2,.2)))
    }
    @Test fun snapshotResumesCollisionClock() {
        val s=BilliardSession(Discipline.FREE,PlayMode.LOCAL)
        s.world.time=15.5
        val copy=BilliardSession(Discipline.FREE,PlayMode.LOCAL); copy.restore(s.snapshot())
        assertEquals(15.5,copy.world.time,0.0)
    }
    @Test fun replayPreservesScoreAndUndoRestoresPreviousTurn() {
        val s=BilliardSession(Discipline.FREE,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,2,.1),event(EventKind.BALL,0,1,.2)))
        assertEquals(1,s.match.score0); assertTrue(s.replay())
        repeat(2000) { if(s.replaying) s.update(.02) }
        assertFalse(s.replaying); assertEquals(1,s.match.score0)
        assertTrue(s.undo()); assertEquals(0,s.match.score0)
    }
    @Test fun allRacksStayInsideAndDoNotOverlap() {
        Discipline.entries.forEach { d ->
            val table=BilliardTable(d.family,discipline=d); val balls=table.rack(d)
            assertEquals(d.name,balls.size,balls.map { it.id }.distinct().size)
            balls.forEach { assertTrue(d.name,table.inside(it.p)) }
            for(i in balls.indices) for(j in i+1 until balls.size)
                assertTrue("$d: ${balls[i].id}/${balls[j].id}",(balls[i].p-balls[j].p).length()>=balls[i].radius+balls[j].radius-1e-8)
        }
    }
    @Test fun poolRackConstraintsAndNineOnFootSpot() {
        for(d in listOf(Discipline.EIGHT,Discipline.HEYBALL)) repeat(12) {
            val table=BilliardTable(d.family); val balls=table.rack(d).filter { it.id!=0 }
            assertEquals(table.footSpot.x,balls.minOf { it.p.x },1e-8)
            val base=balls.filter { it.p.x==balls.maxOf { b -> b.p.x } }.sortedBy { it.p.y }
            assertNotEquals(base.first().id in 1..7,base.last().id in 1..7)
            assertEquals(8,balls.sortedWith(compareBy<Ball> { it.p.x }.thenBy { it.p.y })[4].id)
        }
        val table=BilliardTable(TableFamily.POOL)
        val nine=table.rack(Discipline.NINE)
        assertEquals(table.footSpot,nine.first { it.id==9 }.p)
        assertEquals(1,nine.filter { it.id!=0 }.minBy { it.p.x }.id)
    }
    @Test fun snookerUsesMeasuredDimensionsAndSpots() {
        val t=BilliardTable(TableFamily.SNOOKER)
        assertEquals(3.569,t.length,0.0); assertEquals(1.778,t.width,0.0)
        assertEquals(.737,t.headLine,0.0); assertEquals(.292,t.dRadius,0.0)
        assertEquals(t.length-.324,t.colorSpot(21).x,1e-9)
        assertEquals(t.length*.75,t.colorSpot(20).x,1e-9)
        val s=BilliardSession(Discipline.SNOOKER,PlayMode.LOCAL)
        assertTrue(s.match.ballInHand); assertTrue(s.inHandRegion(s.cue!!.p))
        assertFalse(s.moveBall(0,V3(1.0,.5)))
    }
    @Test fun tenCombinationIsRespottedEvenWhenCalled() {
        val s=BilliardSession(Discipline.TEN,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false; s.nominated=10; s.calledPocket=0
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,10,0,.2)))
        assertEquals(-1,s.match.winner); assertEquals(Motion.STILL,s.world.balls.first { it.id==10 }.motion)
        assertEquals(0,s.match.player)
    }
    @Test fun calledFinalTenWins() {
        val s=BilliardSession(Discipline.TEN,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        s.world.balls.filter { it.id in 1..9 }.forEach { it.motion=Motion.POCKETED }
        s.nominated=10; s.calledPocket=3
        play(s,listOf(event(EventKind.BALL,0,10,.1),event(EventKind.POCKET,10,3,.2)))
        assertEquals(0,s.match.winner)
    }
    @Test fun blackOnBreakReracksEvenWithScratch() {
        val s=BilliardSession(Discipline.BLACKBALL,PlayMode.LOCAL)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,8,0,.2),event(EventKind.POCKET,0,0,.3)))
        assertEquals(Foul.NONE,s.match.foul); assertTrue(s.match.breaking); assertEquals(0,s.match.player)
        assertEquals(16,s.world.balls.count { it.motion!=Motion.POCKETED }); assertEquals(-1,s.match.winner)
    }
    @Test fun blackballFreeShotExpiresAfterOneShotEvenWhenPotted() {
        val s=BilliardSession(Discipline.BLACKBALL,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false; s.match.group0=1; s.match.freeShots=1
        play(s,listOf(event(EventKind.BALL,0,9,.1),event(EventKind.POCKET,9,0,.2)))
        assertEquals(Foul.NONE,s.match.foul); assertEquals(0,s.match.freeShots); assertEquals(0,s.match.player)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.CUSHION,1,0,.2)))
        assertEquals(1,s.match.player)
    }
    @Test fun heyballDoesNotRequireAnyCall() {
        val s=BilliardSession(Discipline.HEYBALL,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        assertFalse(s.needsCall)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,2,.2)))
        assertEquals(1,s.match.group0); assertEquals(0,s.match.player)
    }
    @Test fun pushOutSuspendsContactButCannotWin() {
        val s=BilliardSession(Discipline.NINE,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false; s.match.pushOutAvailable=true; s.pushOut=true
        play(s,listOf(event(EventKind.POCKET,9,0,.1)))
        assertEquals(Foul.NONE,s.match.foul); assertEquals(-1,s.match.winner)
        assertEquals(ShotDecision.PUSH_OUT,s.match.decision); assertFalse(s.ready)
        assertTrue(s.decide(false)); assertEquals(0,s.match.player); assertTrue(s.ready)
    }
    @Test fun threeFoulsAreTrackedPerPlayer() {
        val s=BilliardSession(Discipline.NINE,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        repeat(2) {
            play(s,emptyList())
            play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.CUSHION,1,0,.2)))
        }
        assertEquals(2,s.match.fouls0); assertEquals(0,s.match.fouls1)
        play(s,emptyList()); assertEquals(1,s.match.winner); assertEquals(Foul.THREE_FOULS,s.match.foul)
    }
    @Test fun snookerClearanceDoesNotReturnToFreeColourPhase() {
        val s=BilliardSession(Discipline.SIX_RED,PlayMode.LOCAL)
        s.world.balls.filter { it.id in 1..15 }.forEach { it.motion=Motion.POCKETED }
        s.match.breaking=false; s.match.ballInHand=false
        play(s,listOf(event(EventKind.BALL,0,16,.1),event(EventKind.POCKET,16,0,.2)))
        assertFalse(s.match.snookerColor); assertEquals(listOf(17),s.legalTargets().map { it.id })
        assertEquals(Motion.POCKETED,s.world.balls.first { it.id==16 }.motion)
    }
    @Test fun missedBlackNominationCostsSeven() {
        val s=BilliardSession(Discipline.SIX_RED,PlayMode.LOCAL)
        s.match.snookerColor=true; s.match.breaking=false; s.nominated=21
        play(s,emptyList()); assertEquals(7,s.match.score1)
        assertEquals(ShotDecision.SNOOKER_FOUL,s.match.decision)
    }
    @Test fun straightPoolCountsBonusPotsAndLeavesCueOnOrdinaryFoul() {
        val s=BilliardSession(Discipline.STRAIGHT,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false; s.nominated=1; s.calledPocket=0
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,0,.2),event(EventKind.POCKET,2,3,.3)))
        assertEquals(2,s.match.score0)
        play(s,emptyList()); assertEquals(1,s.match.score0); assertFalse(s.match.ballInHand)
    }
    @Test fun onePocketUsesBothFootCornersAndRepaysDebt() {
        val s=BilliardSession(Discipline.ONE_POCKET,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false; s.match.score0=-1
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,2,.2)))
        assertEquals(0,s.match.score0); assertEquals(Motion.POCKETED,s.world.balls.first { it.id==1 }.motion)
        assertEquals(listOf(1),s.match.pendingSpots)
        play(s,listOf(event(EventKind.BALL,0,2,.1),event(EventKind.POCKET,2,3,.2)))
        assertEquals(1,s.match.score1); assertEquals(1,s.match.player)
        assertEquals(Motion.STILL,s.world.balls.first { it.id==1 }.motion); assertTrue(s.match.pendingSpots.isEmpty())
    }
    @Test fun gorizianaHasCrossAndDoublesIndirectCentralPin() {
        val s=BilliardSession(Discipline.NINE_PINS,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        assertTrue(s.world.pins.all { it.p.x==s.table.length/2 || it.p.y==s.table.width/2 })
        play(s,listOf(event(EventKind.CUSHION,0,0,.1),event(EventKind.BALL,0,1,.2),event(EventKind.PIN,1,0,.3)))
        assertEquals(60,s.match.score0)
    }
    @Test fun dynamicPyramidCannotScoreAnotherInOffFromHand() {
        val s=BilliardSession(Discipline.PYRAMID_DYNAMIC,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=true; s.match.handRegion=HandRegion.ANYWHERE
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,0,0,.2)))
        assertEquals(Foul.SCRATCH,s.match.foul); assertEquals(1,s.match.score1); assertEquals(0,s.match.score0)
        assertTrue(s.match.ballInHand)
    }
    @Test fun savedSnapshotKeepsPendingDecisionsAndAnnouncements() {
        val s=BilliardSession(Discipline.TEN,PlayMode.LOCAL)
        s.nominated=5; s.calledPocket=3; s.match.decision=ShotDecision.TEN_OPTION; s.match.fouls0=2
        val copy=BilliardSession(Discipline.TEN,PlayMode.LOCAL); copy.restore(s.snapshot())
        assertEquals(5,copy.nominated); assertEquals(3,copy.calledPocket)
        assertEquals(ShotDecision.TEN_OPTION,copy.match.decision); assertEquals(2,copy.match.fouls0)
    }
    @Test fun cueCannotBeStruckFromOutsideTheD() {
        val s=BilliardSession(Discipline.SNOOKER,PlayMode.LOCAL)
        s.cue!!.p=V3(1.0,.4,s.table.radius)
        assertFalse(s.cuePlacementValid); assertFalse(s.shoot(Shot(0.0)))
    }
    @Test fun blackDrivenOffTableIsSpottedWithoutRackLoss() {
        val s=BilliardSession(Discipline.BLACKBALL,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.OFF_TABLE,8,-1,.2)))
        assertEquals(-1,s.match.winner); assertEquals(Foul.OFF_TABLE,s.match.foul)
        assertEquals(Motion.STILL,s.world.balls.first { it.id==8 }.motion); assertEquals(1,s.match.freeShots)
    }
    @Test fun restrictedPinHandProvidesALegalPositionForTheNextCue() {
        val s=BilliardSession(Discipline.FIVE_PINS,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        s.world.balls.first { it.id==0 }.p=V3(.8,.3,s.table.radius)
        s.world.balls.first { it.id==1 }.p=V3(1.0,.7,s.table.radius)
        play(s,emptyList())
        assertEquals(1,s.match.player); assertTrue(s.match.ballInHand)
        assertTrue(s.cuePlacementValid); assertTrue(s.cue!!.p.x>s.table.length/2)
    }
    @Test fun straightPoolReracksFourteenWithoutMovingTheBreakBall() {
        val s=BilliardSession(Discipline.STRAIGHT,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        s.world.balls.filter { it.id in 3..15 }.forEach { it.motion=Motion.POCKETED }
        val survivor=s.world.balls.first { it.id==2 }
        survivor.p=V3(1.0,.3,s.table.radius); val location=survivor.p
        s.nominated=1; s.calledPocket=0
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.POCKET,1,0,.2)))
        assertEquals(15,s.world.balls.count { it.id!=0 && it.motion!=Motion.POCKETED })
        assertEquals(location,survivor.p)
        assertFalse(s.world.balls.any { it.id!=0 && (it.p-s.table.footSpot).length()<s.table.radius })
    }
    @Test fun cadreOneRequiresAnObjectToLeaveOnEveryScoringShot() {
        val s=BilliardSession(Discipline.CADRE_47_1,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        s.world.balls[1].p=V3(.15,.15,s.table.radius); s.world.balls[2].p=V3(.30,.15,s.table.radius)
        play(s,listOf(event(EventKind.BALL,0,1,.1),event(EventKind.BALL,0,2,.2)))
        assertEquals(0,s.match.score0); assertEquals(1,s.match.player)
    }
    @Test fun cadreTwoAllowsOnePointBeforeAnObjectMustLeave() {
        val s=BilliardSession(Discipline.CADRE_47_2,PlayMode.LOCAL)
        s.match.breaking=false; s.match.ballInHand=false
        s.world.balls[1].p=V3(.15,.15,s.table.radius); s.world.balls[2].p=V3(.30,.15,s.table.radius)
        val contacts=listOf(event(EventKind.BALL,0,1,.1),event(EventKind.BALL,0,2,.2))
        play(s,contacts); assertEquals(1,s.match.score0)
        play(s,contacts); assertEquals(1,s.match.score0); assertEquals(1,s.match.player)
    }
    @Test fun englishRedMovesToCentreAfterTwoUnaccompaniedSpotPots() {
        val s=BilliardSession(Discipline.ENGLISH,PlayMode.LOCAL)
        val pot=listOf(event(EventKind.BALL,0,2,.1),event(EventKind.POCKET,2,0,.2))
        play(s,pot); play(s,pot)
        assertEquals(6,s.match.score0); assertEquals(s.table.colorSpot(19),s.world.balls.first { it.id==2 }.p)
    }
    @Test fun englishSixteenthHazardIsAFoulAndOffersReset() {
        val s=BilliardSession(Discipline.ENGLISH,PlayMode.LOCAL)
        s.match.englishHazards=15
        play(s,listOf(event(EventKind.BALL,0,2,.1),event(EventKind.POCKET,2,0,.2)))
        assertEquals(0,s.match.score0); assertEquals(2,s.match.score1)
        assertEquals(ShotDecision.ENGLISH_RESET,s.match.decision)
        assertTrue(s.decide(false)); assertEquals(1,s.match.player)
        assertEquals(s.table.colorSpot(19),s.world.balls.first { it.id==0 }.p)
        assertTrue(s.match.ballInHand); assertTrue(s.cuePlacementValid)
    }
}
