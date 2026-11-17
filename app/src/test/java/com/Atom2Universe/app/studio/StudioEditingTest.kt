package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.StudioRegister
import com.Atom2Universe.app.studio.music.TapTempo
import com.Atom2Universe.app.studio.ui.gridStep
import org.junit.Assert.*
import org.junit.Test

class StudioEditingTest {
    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.blank())

    @Test fun middleCAnchorsTheKeyboardAndTheFirstNotesOfAnEmptyStudioTrack() {
        val register = StudioRegister(360, 740)
        assertEquals(60, register.low)
        for (key in listOf(0, 6, 9)) {
            val s = session()
            s.setKey(Inst.SYNTH, key)
            val rhythm = s.rhythmEntryPitch(Inst.SYNTH)
            assertEquals(60 + key, rhythm)
            assertEquals(rhythm, s.padPitches(Inst.SYNTH).first())
            s.setStep(Inst.SYNTH, 3, true)
            assertEquals(rhythm, s.notesOf(Inst.SYNTH).single().pitch)
        }
    }

    @Test fun explicitFocusUsesTheReferenceRegisterOfEachChipInstrument() {
        val piano = Track.synth(7).copy(synthBank = SynthBank.SCC)
        val bass = piano.copy(synthBank = SynthBank.FAMICOM, program = 32)
        val r = StudioRegister(360, 740)
        for ((track, expectedC) in listOf(piano to 60, bass to 36)) {
            val octave = StudioRegister.chipOctave(track)
            r.setLow(108)
            r.focus(emptyList(), octave)
            for (key in 0..11) {
                val tonic = expectedC + key
                assertTrue(tonic in r.low..r.high)
            }
        }
        r.focus(emptyList(), StudioRegister.chipOctave(Track.synth(7)))
        assertEquals(36, r.low)
    }

    @Test fun explicitFocusShowsExistingHighNotesEvenForABassInstrument() {
        val notes = listOf(Note(0, 124), Note(4, 127))
        val r = StudioRegister(360, 740)
        r.focus(notes, 36)
        assertTrue(notes.all { it.pitch in r.low..r.high })
    }

    @Test fun padUsesTheSelectedTrackOctaveAcrossPatternsSoundsAndMidiLimits() {
        val s = session()
        val synth = Inst.SYNTH
        val second = s.addTrack(TrackKind.SF2)
        for (t in listOf(synth, second)) {
            s.setKey(t, 9)
            s.setScale(t, Scales.MAJOR.id)
        }
        s.addNote(0, 45, 1)
        s.setSynthBank(synth, SynthBank.FAMICOM)
        s.setChipProgram(synth, 32)
        val major = intArrayOf(69, 71, 73, 74, 76, 78, 80, 81)
        assertArrayEquals(major, s.padPitches(synth))
        s.setRhythmOctave(synth, 3)
        s.selectPattern(1)
        assertArrayEquals(major.map { it - 12 }.toIntArray(), s.padPitches(synth))
        assertArrayEquals(major, s.padPitches(second))
        s.setKey(synth, 7)
        s.setRhythmOctave(synth, 9)
        assertArrayEquals(intArrayOf(127), s.padPitches(synth))
        s.setKey(synth, 0)
        s.setRhythmOctave(synth, -1)
        assertEquals(0, s.padPitches(synth).first())
    }

    /** Signalé le 17/11 : changer la tonique ou la gamme d'une case des pads changeait celles de tous les claviers. */
    @Test fun keyAndScaleBelongToEachTrack() {
        val s = session().apply { coalesceMs = 0 }
        val synth = Inst.SYNTH
        val second = s.addTrack(TrackKind.SF2)
        s.addNote(0, 60, 1, track = second)
        s.setKey(second, 2)
        s.setScale(second, Scales.MAJOR.id)
        s.setScaleLocked(second, true)
        val first = s.project.track(synth)
        assertEquals(StudioProject.blank().keyPc, first.keyPc)
        assertEquals(StudioProject.blank().scaleId, first.scaleId)
        assertFalse(first.scaleLocked)
        assertEquals(2, s.project.track(second).keyPc)
        assertEquals(Scales.MAJOR.id, s.project.track(second).scaleId)
        assertTrue(s.project.track(second).scaleLocked)
        // Seules les notes de la piste changée se transposent
        assertEquals(60 + (2 - StudioProject.blank().keyPc).let { if (it > 6) it - 12 else if (it < -5) it + 12 else it },
            s.notesOf(second).single().pitch)
        // Une nouvelle piste part de la dernière tonalité et de la dernière gamme choisies
        val third = s.addTrack(TrackKind.SYNTH)
        assertEquals(2, s.project.track(third).keyPc)
        assertEquals(Scales.MAJOR.id, s.project.track(third).scaleId)
        // Sauvegarde et annulation
        val back = com.Atom2Universe.app.studio.data.StudioProjectCodec.decode(
            com.Atom2Universe.app.studio.data.StudioProjectCodec.encode(s.project))!!
        assertEquals(2, back.track(second).keyPc)
        assertEquals(Scales.MAJOR.id, back.track(second).scaleId)
        assertTrue(back.track(second).scaleLocked)
        assertEquals(first.keyPc, back.track(synth).keyPc)
        s.undo() // la nouvelle piste
        s.undo() // la gamme
        assertEquals(StudioProject.blank().scaleId, s.project.track(second).scaleId)
        s.undo() // la tonique
        assertEquals(StudioProject.blank().keyPc, s.project.track(second).keyPc)
        assertEquals(60, s.notesOf(second).single().pitch)
    }

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
