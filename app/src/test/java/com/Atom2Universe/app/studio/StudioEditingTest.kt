package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.StudioRegister
import com.Atom2Universe.app.studio.music.TapTempo
import com.Atom2Universe.app.studio.ui.gridStep
import org.junit.Assert.*
import org.junit.Test

class StudioEditingTest {
    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.blank())

    @Test fun scrollAndRevealCoverEveryMidiPitchOnPhoneAndTablet() {
        for (width in listOf(360, 870)) {
            val r = StudioRegister(width, 800)
            for (p in (0..127) + (127 downTo 0)) {
                r.reveal(p)
                assertTrue(p in r.low..r.high)
                assertEquals(r.whiteCount, StudioRegister.whites.count { it in r.low..r.high })
            }
            r.shift(1000)
            assertEquals(127, r.high)
            r.shift(-1000)
            assertEquals(0, r.low)
        }
    }

    @Test fun focusIncludesAnEntireChordWhenPossibleDespiteAStrayHighNote() {
        val r = StudioRegister(360, 740)
        r.focus(listOf(60, 63, 67, 119).map { Note(1, it) })
        for (p in listOf(60, 63, 67)) assertTrue(p in r.low..r.high)
        r.reveal(119)
        assertTrue(119 in r.low..r.high)
    }

    @Test fun ghostAndWrittenHitsShareAnchorSpacingAndTrackMapping() {
        val s = session()
        s.setStep(Inst.KICK, 3, true)
        s.setStep(Inst.SNARE, 6, true)
        s.editStep(Inst.KICK, 3, velocity = 0.6f, pitch = 68)
        val box = s.copyRange(setOf(Inst.KICK, Inst.SNARE), 0, 7)
        assertEquals(3, box.firstStep)
        val c = box.alignedToFirstEvent()
        assertEquals(4, c.span)
        s.paste(c, 12, trackMap = mapOf(Inst.KICK to Inst.SNARE, Inst.SNARE to Inst.CLAP))
        assertEquals(0.6f, s.velocity(Inst.SNARE, 12), 1e-6f)
        assertEquals(68, s.stepInfo(Inst.SNARE, 12)!!.pitch)
        assertTrue(s.isOn(Inst.CLAP, 15))
        assertFalse(s.isOn(Inst.KICK, 12))
    }

    @Test fun draggingOneChordNoteKeepsItsVelocityAndCanBeUndone() {
        val s = session()
        s.addNote(1, 60, 3, 0.7f)
        s.addNote(1, 64, 2)
        s.addNote(9, 67, 2)
        s.flush()
        val n = s.noteAt(1, 60)!!
        s.editNote(n, 8, 67)
        assertNull(s.noteAt(1, 60))
        assertTrue(s.isOn(Inst.SYNTH, 1))
        assertFalse(s.isTail(Inst.SYNTH, 3))
        assertTrue(s.isTail(Inst.SYNTH, 10))
        assertEquals(0.7f, s.noteAt(9, 67)!!.velocity, 1e-6f)
        assertEquals(2, s.notesOf(Inst.SYNTH).size)
        s.undo()
        assertNotNull(s.noteAt(1, 60))
        assertEquals(9, s.noteAt(9, 67)!!.step)
    }

    @Test fun gridGapsSnapToNearestVisibleStepAfterScrolling() {
        assertEquals(3, gridStep(43f, 10f, 8f, 64))
        assertEquals(4, gridStep(45f, 10f, 8f, 64))
        assertEquals(40, gridStep(480f, 10f, 8f, 64))
        assertEquals(63, gridStep(900f, 10f, 8f, 64))
    }

    @Test fun tappedTempoRejectsAPauseAndSmoothsAnUnevenBeat() {
        val tempo = TapTempo()
        assertNull(tempo.tap(1000))
        assertEquals(120, tempo.tap(1500))
        tempo.tap(2004)
        assertEquals(120, tempo.tap(2499))
        assertNull(tempo.tap(5000))
        assertEquals(100, tempo.tap(5600))
    }
}
