package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.mode.AssaultMatch
import com.Atom2Universe.app.games.caves.mode.AssaultMatch.Event
import com.Atom2Universe.app.games.caves.mode.AssaultMatch.Phase
import com.Atom2Universe.app.games.caves.mode.AssaultMatch.RoundEnd
import org.junit.Assert.assertEquals
import org.junit.Test

class AssaultMatchTest {

    private fun match() = AssaultMatch(targetsPerRound = 8, roundSeconds = 60f, pauseSeconds = 4f)

    @Test
    fun `la premiere mise a jour lance la manche 1`() {
        val m = match()
        assertEquals(Event.ROUND_STARTED, m.update(0.016f))
        val s = m.status()
        assertEquals(1, s.round)
        assertEquals(Phase.PLAYING, s.phase)
        assertEquals(60, s.secondsLeft)
        assertEquals(0, s.targetsDown)
    }

    @Test
    fun `abattre toutes les cibles termine la manche avec un bonus de temps`() {
        val m = match()
        m.update(0.016f)
        m.update(10f)   // il reste un peu moins de 50 s, arrondi à 50
        repeat(7) { i -> assertEquals(Event.NONE, m.onTargetDown(headshot = i < 2)) }
        assertEquals(Event.ROUND_ENDED, m.onTargetDown(headshot = false))

        val s = m.status()
        assertEquals(Phase.BETWEEN_ROUNDS, s.phase)
        assertEquals(RoundEnd.CLEARED, s.lastEnd)
        assertEquals(500, s.lastTimeBonus)
        assertEquals(2, s.headshots)
        assertEquals(8 * 100 + 2 * 50 + 500, s.score)
    }

    @Test
    fun `apres la pause la manche suivante repart de zero mais garde le score`() {
        val m = match()
        m.update(0.016f)
        repeat(8) { m.onTargetDown(headshot = false) }
        val scoreAfterRound1 = m.score

        assertEquals(Event.NONE, m.update(3f))
        assertEquals(Event.ROUND_STARTED, m.update(1.5f))
        val s = m.status()
        assertEquals(2, s.round)
        assertEquals(0, s.targetsDown)
        assertEquals(60, s.secondsLeft)
        assertEquals(scoreAfterRound1, s.score)
        assertEquals(null, s.lastEnd)
    }

    @Test
    fun `le chrono ecoule termine la manche sans bonus`() {
        val m = match()
        m.update(0.016f)
        m.onTargetDown(headshot = true)
        assertEquals(Event.ROUND_ENDED, m.update(61f))
        val s = m.status()
        assertEquals(RoundEnd.TIME_UP, s.lastEnd)
        assertEquals(0, s.lastTimeBonus)
        assertEquals(0, s.secondsLeft)
        assertEquals(100 + 50, s.score)
    }

    @Test
    fun `mourir perd la manche sans bonus mais garde les points`() {
        val m = match()
        m.update(0.016f)
        m.onTargetDown(headshot = false)
        assertEquals(Event.ROUND_ENDED, m.onPlayerDied())
        val s = m.status()
        assertEquals(RoundEnd.DIED, s.lastEnd)
        assertEquals(0, s.lastTimeBonus)
        assertEquals(100, s.score)
        assertEquals(Event.NONE, m.onPlayerDied())   // déjà en pause : rien de plus
    }

    @Test
    fun `une cible abattue pendant la pause ne compte pas`() {
        val m = match()
        m.update(0.016f)
        m.update(61f)
        val score = m.score
        assertEquals(Event.NONE, m.onTargetDown(headshot = true))
        assertEquals(score, m.score)
    }
}
