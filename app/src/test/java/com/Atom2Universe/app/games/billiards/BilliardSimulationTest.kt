package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import org.junit.Assert.*
import org.junit.Test

class BilliardSimulationTest {
    private fun assertVector(expected: V3, actual: V3) {
        assertEquals(expected.x,actual.x,1e-9)
        assertEquals(expected.y,actual.y,1e-9)
        assertEquals(expected.z,actual.z,1e-9)
    }

    private fun finish(session: BilliardSession) {
        var steps=0
        while(session.world.moving && steps++<6000) session.update(.02)
        assertFalse("Simulation must settle before being compared",session.world.moving)
    }

    @Test fun headlessTrialsPreserveContactsBallStatesAndRefereeResults() {
        for(discipline in listOf(Discipline.EIGHT,Discipline.NINE,Discipline.SIX_RED,Discipline.THREE_CUSHION)) {
            val visible=BilliardSession(discipline,PlayMode.COMPUTER)
            val headless=BilliardSession(discipline,PlayMode.COMPUTER,trackVisualRotation=false)
            val snapshot=visible.snapshot()
            // Also check reuse: no previous shot's referee state may leak into a trial.
            for(shot in listOf(Shot(.04,2.0),Shot(-.08,2.7,side=.2,top=.1))) {
                visible.restore(snapshot); headless.restore(snapshot)
                assertTrue(visible.shoot(shot)); assertTrue(headless.shoot(shot))
                finish(visible); finish(headless)
                assertEquals(visible.match,headless.match)
                assertEquals(visible.world.events,headless.world.events)
                assertEquals(visible.cueId,headless.cueId)
                visible.world.balls.zip(headless.world.balls).forEach { (a,b) ->
                    assertEquals(a.id,b.id); assertEquals(a.motion,b.motion)
                    assertVector(a.p,b.p); assertVector(a.v,b.v); assertVector(a.w,b.w)
                }
            }
        }
    }

    @Test fun analyticSpinTailPreservesPhysicsWithoutAddingContacts() {
        val table=BilliardTable(TableFamily.CAROM)
        val ball=table.ball(0,.4,.4).also { it.w=V3(z=12.0); it.classify() }
        val visible=BilliardWorld(table,mutableListOf(ball.copyDeep()))
        val headless=BilliardWorld(table,mutableListOf(ball.copyDeep()),trackVisualRotation=false)
        visible.advance(10.0); headless.advance(10.0)
        assertFalse(visible.moving); assertFalse(headless.moving)
        assertVector(visible.balls[0].p,headless.balls[0].p)
        assertVector(visible.balls[0].w,headless.balls[0].w)
        assertTrue(headless.events.isEmpty())
    }

    @Test fun plannerPotsTheClearBallWhenTheOtherTargetIsBlockedByTheEight() {
        val session=BilliardSession(Discipline.EIGHT,PlayMode.COMPUTER)
        val table=session.table
        val cue=table.ball(0,table.length/2,table.width*.8)
        val blocked=table.ball(1,.35,.35)
        val clear=table.ball(2,table.length/2,table.width*.25)
        val obstacle=table.ball(8,(cue.p.x+blocked.p.x)/2,(cue.p.y+blocked.p.y)/2)
        session.restore(session.snapshot().copy(balls=listOf(cue,blocked,clear,obstacle),
            match=MatchState(group0=1,breaking=false)))
        val before=session.snapshot()
        val choice=checkNotNull(BilliardPlanner.choose(session,2) { false })
        assertEquals(2,choice.ball)
        assertEquals(before.match,session.match)
        before.balls.zip(session.world.balls).forEach { (a,b) -> assertVector(a.p,b.p) }
        choice.position?.let { assertTrue(session.moveBall(session.cueId,it)) }
        session.nominated=choice.ball; session.calledPocket=choice.pocket; session.bankRails=choice.rails
        assertTrue(session.shoot(choice.shot)); finish(session)
        assertEquals(Foul.NONE,session.match.foul)
        assertTrue(session.match.score0>0)
        assertEquals(-1,session.match.winner)
    }

    @Test fun plannerCorrectsCollisionThrowOnATightPyramidCut() {
        // A pyramid corner accepts about 5 mm; throw alone misses it by 15 from here.
        val session=BilliardSession(Discipline.PYRAMID_DYNAMIC,PlayMode.COMPUTER)
        val table=session.table
        val target=table.ball(1,.60,.40)
        val line=(table.opening(table.pockets[0])-target.p).planar().unit()
        val ghost=target.p-line*(2*table.radius)
        val start=ghost-line.rotate(Math.toRadians(30.0))*.5
        val cue=table.ball(0,start.x,start.y)
        session.restore(session.snapshot().copy(balls=listOf(cue,target),match=MatchState(breaking=false)))
        // A perfect stroke isolates the aim correction: with its own stroke error, the
        // robot may rightly judge this cut too risky and play safe instead.
        val choice=checkNotNull(BilliardPlanner.choose(session,2,skill=BilliardSkill(0.0,0.0)) { false })
        choice.position?.let { assertTrue(session.moveBall(session.cueId,it)) }
        session.nominated=choice.ball; session.calledPocket=choice.pocket; session.bankRails=choice.rails
        assertTrue(session.shoot(choice.shot)); finish(session)
        assertTrue(session.world.events.any { it.kind==EventKind.POCKET && it.ball==1 })
    }

    @Test fun plannerFindsACaromThatTheCentreLineCannotMake() {
        // Recorded on the tablet (free game, Alex to play with ball 1). Aiming at a ball's
        // centre plus fixed offsets found nothing here: the contact must be swept, with spin.
        for(discipline in listOf(Discipline.FREE,Discipline.ONE_CUSHION)) {
            val session=BilliardSession(discipline,PlayMode.COMPUTER)
            val table=session.table
            session.restore(session.snapshot().copy(balls=listOf(table.ball(0,2.1169,1.3354),table.ball(1,.7100,.7100),
                table.ball(2,1.7812,.1470)),match=MatchState(player=1,breaking=false),cueId=1))
            val choice=checkNotNull(BilliardPlanner.choose(session,1,skill=BilliardSkill(0.0,0.0)) { false })
            assertTrue(session.shoot(choice.shot)); finish(session)
            assertEquals(discipline.name,1,session.match.score1)
            assertEquals(1,session.match.player)
        }
    }

    @Test fun cueRisesOverTheRailAndTheBallsBehind() {
        val session=BilliardSession(Discipline.NINE,PlayMode.COMPUTER)
        val table=session.table
        fun minimum(angle: Double,vararg balls: Ball): Int {
            session.restore(session.snapshot().copy(balls=balls.toList(),match=MatchState(breaking=false)))
            return CueReach.minimum(session.world,session.cue!!,Shot(angle))
        }
        // Middle of the table, along its length: the rail is far, a few degrees clear it.
        val middle=minimum(0.0,table.ball(0,table.length/2,table.width/2))
        assertTrue("$middle",middle in 1..4)
        // Three centimetres from a cushion, playing away from it: the cue climbs over the wood.
        val rail=minimum(kotlin.math.PI/2,table.ball(0,table.length/2,table.radius+.03))
        assertTrue("$rail",rail in 12..30)
        // A ball frozen behind the cue ball calls for a jacked-up cue, and the shot played is the raised one.
        val behind=table.ball(1,table.length/2-2*table.radius-.0005,table.width/2)
        val frozen=minimum(0.0,table.ball(0,table.length/2,table.width/2),behind)
        assertTrue("$frozen",frozen>=30)
        assertTrue(session.shoot(Shot(0.0,2.0)))
        assertEquals(frozen.toDouble(),session.lastShot!!.elevation,0.0)
    }

    @Test fun drawnCueStaysAboveTheRailAtItsMinimum() {
        val random=kotlin.random.Random(11)
        for(discipline in listOf(Discipline.NINE,Discipline.THREE_CUSHION,Discipline.SNOOKER)) {
            val session=BilliardSession(discipline,PlayMode.COMPUTER)
            val table=session.table
            repeat(200) {
                val cue=table.ball(0,table.radius+random.nextDouble()*(table.length-2*table.radius),
                    table.radius+random.nextDouble()*(table.width-2*table.radius))
                session.restore(session.snapshot().copy(balls=listOf(cue),match=MatchState(breaking=false)))
                val ball=session.cue!!
                val shot=CueReach.lift(session.world,ball,Shot(random.nextDouble()*2*kotlin.math.PI,
                    side=random.nextDouble(-.5,.5),top=random.nextDouble(-.5,.5)))
                val tip=CueReach.tip(ball,shot); val dir=CueReach.direction(shot)
                for(step in 0..270) {
                    val along=step*CueReach.LENGTH/270
                    val p=tip+dir*along
                    val radius=CueReach.TIP+(CueReach.BUTT-CueReach.TIP)*along/CueReach.LENGTH
                    val underside=p.z-radius/kotlin.math.cos(Math.toRadians(shot.elevation))
                    val outside=maxOf(-p.x,p.x-table.length,-p.y,p.y-table.width)
                    if(outside>=.035) assertTrue("$discipline ${shot.elevation}° under the rail",underside>=table.railTop)
                    else if(outside>=0) assertTrue("$discipline ${shot.elevation}° in the cushion",underside>=table.noseHeight)
                }
            }
        }
    }

    @Test fun strokeErrorFollowsTheSkillAndStaysBounded() {
        val skill=BilliardSkill.of(1); val random=kotlin.random.Random(5)
        val errors=(1..4000).map { skill.stroke(Shot(1.0,2.0),random).angle-1.0 }
        val deviation=kotlin.math.sqrt(errors.sumOf { it*it }/errors.size)
        assertEquals(skill.angle,deviation,skill.angle*.06)
        assertTrue(errors.all { kotlin.math.abs(it)<=2.5*skill.angle+1e-12 })
    }

    @Test fun plannerPrefersAPotThatForgivesItsOwnStrokeError() {
        // Recorded on the tablet (nine-ball, 7 to play). Two slams drop the 9 with the exact
        // stroke, but only 12-15 % of the time with the hard robot's error; a soft cut
        // into the corner keeps the table about 4 times in 5.
        val session=BilliardSession(Discipline.NINE,PlayMode.COMPUTER)
        val table=session.table
        session.restore(session.snapshot().copy(balls=listOf(table.ball(0,.4530,.5244),table.ball(7,2.2764,1.0950),
            table.ball(9,1.7371,.3909)),match=MatchState(player=1,breaking=false)))
        val choice=checkNotNull(BilliardPlanner.choose(session,2) { false })
        val start=session.snapshot()
        val skill=BilliardSkill.of(2); val random=kotlin.random.Random(3)
        val kept=(1..30).count {
            session.restore(start)
            session.nominated=choice.ball; session.calledPocket=choice.pocket
            assertTrue(session.shoot(skill.stroke(choice.shot,random))); finish(session)
            session.match.winner==1 || session.match.winner<0 && session.match.foul==Foul.NONE && session.match.player==1
        }
        assertTrue("$kept/30",kept>=18)
    }
}
