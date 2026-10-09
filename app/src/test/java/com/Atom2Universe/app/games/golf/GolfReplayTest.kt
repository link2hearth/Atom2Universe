package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.*
import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GolfReplayTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun shot()=GolfReplay(name="Approche réussie",course="gardens",hole=1,club=GolfClub.IRON7,
        aim=.5f,stroke=2,easy=true,holed=true,samples=listOf(
            GolfReplaySample(0f,GolfPoint(1f,2f,3f),10f),
            GolfReplaySample(1f,GolfPoint(5f,6f,7f),11f),
            GolfReplaySample(3f,GolfPoint(9f,2f,11f),13f)))

    @Test fun seekingBackwardsHasNoHistoryAndInterpolatesObstacleClock() {
        val replay=shot()
        val before=replay.sample(.5f)
        assertEquals(GolfPoint(3f,4f,5f),before.ball)
        assertEquals(10.5f,before.clock,0f)
        replay.sample(2.9f)
        assertEquals(before,replay.sample(.5f))
        assertEquals(replay.samples.first(),replay.sample(-50f))
        assertEquals(replay.samples.last(),replay.sample(500f))
    }

    @Test fun favouritesSurviveReopeningAndDeletingOnlyRemovesChosenShot() {
        val dir=temporary.newFolder()
        val first=shot();val second=shot().copy(name="Deuxième")
        GolfReplayStore(dir).apply { save(first);save(first);save(second) }
        val reopened=GolfReplayStore(dir)
        assertEquals(2,reopened.entries().size)
        assertEquals(first,reopened.load(first.id))
        reopened.delete(first.id)
        assertEquals(second,reopened.load(second.id))
        assertEquals(listOf(second.id),reopened.entries().map{it.id})
    }

    @Test fun interruptedOrCorruptFilesDoNotHideOtherFavourites() {
        val dir=temporary.newFolder();val replay=shot();val store=GolfReplayStore(dir)
        store.save(replay)
        File(dir,"unfinished.tmp").writeBytes(byteArrayOf(1,2,3))
        File(dir,"broken.golf").writeBytes(byteArrayOf(0,0,0,1))
        assertEquals(listOf(replay.id),store.entries().map{it.id})
        assertEquals(replay,store.load(replay.id))
        assertTrue(runCatching{store.load("../outside")}.isFailure)
    }

    @Test fun recordingARealShotAndReviewingItNeverChangesTheGame() {
        val hole=ClassicHole(1,5,1000f,width=2000f,fairwayBaseWidth=1900f)
        val game=ClassicGame(hole)
        game.hit(.4f)
        val capture=GolfReplayRecorder(shot().copy(club=game.club,holed=false,
            samples=listOf(GolfReplaySample(0f,game.ball,game.clock))))
        var iterations=0
        while(game.state==GolfState.FLYING||game.state==GolfState.ROLLING) {
            assertTrue(iterations++<2400)
            game.update(1f/60f);capture.append(1f/60f,game.ball,game.clock)
        }
        val replay=capture.finish(false)!!
        val finalBall=game.ball;val strokes=game.strokes
        for(i in 100 downTo 0)replay.sample(replay.duration*i/100f)
        assertEquals(finalBall,replay.samples.last().ball)
        assertEquals(finalBall,game.ball);assertEquals(strokes,game.strokes)
    }

    @Test fun automaticCameraChangesAngleAndAlwaysStaysAboveTerrain() {
        val hole=ClassicHole(1,4,300f)
        val ball=hole.tee
        val views=listOf(0f,4f,8f).map{t->ShotCamera.pose(hole,ball,ball,0f,GolfClub.DRIVER,t,2,ShotCameraMode.AUTO)}
        assertEquals(3,views.map{it.cut}.distinct().size)
        views.forEach { pose ->
            assertEquals(ball,pose.target)
            assertTrue(pose.eye.y>hole.heightAt(pose.eye.x,pose.eye.z))
            assertTrue(pose.eye.x.isFinite() && pose.eye.y.isFinite() && pose.eye.z.isFinite())
        }
    }
}
