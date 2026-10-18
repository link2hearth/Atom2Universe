package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceSessionTest {
    private val track = PrototypeTrack()

    @Test
    fun reculerPuisRevenirNeDonnePasDAvanceGratuite() {
        val drive = Course()
        drive.advance(-30f, 3f)
        drive.advance(30f, 3f)
        assertEquals(0f, drive.race.playerProgress, .01f)
        assertEquals(1, drive.race.playerLap)
        assertEquals(0, drive.race.nextCheckpoint)
    }

    @Test
    fun unPassageManqueLimiteLeClassementJusquaSonRetour() {
        val drive = Course()
        drive.advance(track.length * .30f, 10f, onRoad = false)
        assertEquals(0, drive.race.nextCheckpoint)
        assertTrue(drive.race.playerProgress <= track.length * (.20f - RaceSession.START_FRACTION) + .01f)
        drive.advance(-track.length * .15f, 5f)
        drive.advance(track.length * .10f, 5f)
        assertEquals(1, drive.race.nextCheckpoint)
        assertEquals(track.length * .25f, drive.race.playerProgress, .02f)
    }

    @Test
    fun leChronoDuTourEstInterpoleAuFranchissementDeLaLigne() {
        val drive = Course()
        drive.advance(track.length * 1.01f, 50.5f)
        assertEquals(2, drive.race.playerLap)
        assertEquals(50f, drive.race.lastLapSeconds, .01f)
        assertEquals(50f, drive.race.bestLapSeconds, .01f)
        assertEquals(.5f, drive.race.lapElapsedSeconds, .01f)
        assertEquals(1, drive.race.lapSerial)
    }

    @Test
    fun leDernierTourArreteLeChronoExactementALaLigne() {
        val drive = Course()
        drive.advance(track.length * 3.01f, 150.5f)
        assertEquals(RacePhase.FINISHED, drive.race.phase)
        assertEquals(150f, drive.race.raceSeconds, .02f)
        assertEquals(50f, drive.race.lapElapsedSeconds, .02f)
        assertEquals(track.length * 3f, drive.race.playerProgress, .02f)
        assertEquals(1, drive.race.finishPosition)
    }

    @Test
    fun leDepannageRestaureLesPassagesSansEffacerLeTempsPerdu() {
        val drive = Course()
        drive.advance(track.length * .95f, 47.5f)
        drive.race.rememberRescuePoint()
        val anchorDistance = drive.distance
        drive.advance(track.length * .08f, 4f)
        assertEquals(2, drive.race.playerLap)
        drive.race.restoreRescuePoint(anchorDistance)
        drive.distance = anchorDistance
        drive.race.updateRace(1.65f, anchorDistance, false, emptyList(), recovering = true)
        assertEquals(1, drive.race.playerLap)
        assertEquals(4, drive.race.nextCheckpoint)
        assertEquals(0f, drive.race.lastLapSeconds, 0f)
        assertEquals(0f, drive.race.bestLapSeconds, 0f)
        assertEquals(53.15f, drive.race.lapElapsedSeconds, .02f)
    }

    @Test
    fun leContresensDemandeUnReculMaintenuEtSEffaceALArret() {
        val drive = Course()
        drive.advance(-1f, .4f)
        assertFalse(drive.race.wrongWay)
        drive.advance(-1f, .4f)
        assertTrue(drive.race.wrongWay)
        drive.race.updateRace(.5f, drive.distance, true, emptyList())
        assertFalse(drive.race.wrongWay)
    }

    @Test
    fun unTempsInvalideNeCorromptNiLeCompteAReboursNiLaCourse() {
        val race = RaceSession(track)
        race.reset(0f)
        race.updateCountdown(Float.NaN, 0f)
        assertTrue(race.countdownSeconds.isFinite())
        race.updateCountdown(4f, 0f)
        race.updateRace(-1f, 0f, true, emptyList())
        race.updateRace(Float.NaN, 0f, true, emptyList())
        assertEquals(0f, race.raceSeconds, 0f)
    }

    private inner class Course {
        val race = RaceSession(track)
        var distance = track.length * RaceSession.START_FRACTION

        init {
            race.reset(distance)
            race.updateCountdown(4f, distance)
        }

        fun advance(meters: Float, seconds: Float, onRoad: Boolean = true) {
            val steps = ceil(abs(meters) / (track.length * .035f)).toInt().coerceAtLeast(1)
            repeat(steps) {
                distance = track.wrapDistance(distance + meters / steps)
                race.updateRace(seconds / steps, distance, onRoad, emptyList())
            }
        }
    }
}
