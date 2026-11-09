package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La longueur de la boucle : 16, 32 ou 64 pas, le motif se répète en s'allongeant. */
class StudioStepCountTest {

    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.fromStyle(Styles.HOUSE))

    @Test
    fun sixtyFourStepsRepeatThePatternFourTimes() {
        val s = session()
        s.addNote(2, 60, 3)
        s.setStepCount(64)
        assertEquals(64, s.project.stepCount)
        val starts = s.project.pattern.notes.filter { it.pitch == 60 && it.length == 3 }.map { it.step }.toSet()
        assertTrue(listOf(2, 18, 34, 50).all { it in starts })
        val kick = s.project.pattern.lane(Inst.KICK)!!
        assertEquals(64, kick.velocity.size)
        assertEquals(kick.velocity[0], kick.velocity[48], 0f)
    }

    @Test
    fun goingBackKeepsTheStartAndOtherLengthsAreRefused() {
        val s = session()
        s.setStepCount(64)
        s.setStepCount(16)
        assertEquals(16, s.project.stepCount)
        s.setStepCount(48)
        assertEquals(16, s.project.stepCount)
    }
}
