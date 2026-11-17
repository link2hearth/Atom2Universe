package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les défauts trouvés à la revue du 17/11/2026 : chacun a son garde-fou. */
class StudioFixesTest {

    private class SilentSynth : ExternalSynth {
        override fun noteOn(note: Int, velocity: Int) {}
        override fun noteOff(note: Int) {}
        override fun allOff() {}
        override fun setProgram(bank: Int, program: Int) {}
        override fun render(left: FloatArray, right: FloatArray, count: Int) {}
        override val activeVoices: Int get() = 0
    }

    @Test fun aMutedDrumStaysSilentWhenItSwitchesToASoundfontKit() {
        val engine = StudioEngine(48_000)
        engine.sf2Factory = { _, _, _ -> SilentSynth() }
        val s = StudioSession(engine, StudioProject.blank())
        s.setMuted(Inst.KICK, true)
        s.setDrumSoundfont(Inst.KICK, 0)
        assertTrue(engine.instrument(Inst.KICK)!!.muted)
        s.setDrumSoundfont(Inst.KICK, -1)
        assertTrue(engine.instrument(Inst.KICK)!!.muted)
    }

    @Test fun undoRestoresOnlyWhatTheActionChanged() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 0 }
        val sf = s.addTrack(TrackKind.SF2)
        val start = s.project
        s.movePad(sf, Inst.KICK)
        val moved = s.project
        val changes = StudioHistory.diff(start, moved, undoableOnly = true)
        // Un instrument choisi après le déplacement, sans passer par cette action
        val later = moved.withTrack(moved.track(sf).copy(program = 40))
        val undone = StudioHistory.apply(later, changes, forward = false)
        assertEquals(start.track(sf).padOrder, undone.track(sf).padOrder)
        assertEquals(40, undone.track(sf).program)
    }

    @Test fun aSoundfontInstrumentChoiceIsItsOwnAction() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 60_000 }
        val sf = s.addTrack(TrackKind.SF2)
        s.setSf2Program(sf, 0, 40)
        // Une seule annulation rend l'instrument, sans retirer la piste ajoutée juste avant
        s.undo()
        assertEquals(0, s.project.track(sf).program)
        assertTrue(s.project.trackOrNull(sf) != null)
    }

    @Test fun aGestureTakeStartedBeforeAViewChangeIsDroppedWithoutCrashing() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank())
        s.beginGesture()
        s.recordGesture(0.0, 0.5f, 0.5f)
        s.setView(listOf(0, 1))
        s.recordGesture(20.0, 0.5f, 0.5f)
        s.endGesture()
        assertNull(s.project.pattern.autoFilter)
    }

    @Test fun styleHitsGoToTheVisibleTrackOfEachDrumSound() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank())
        s.setHidden(Inst.KICK, true)
        val kick = s.addTrack(TrackKind.DRUM, Inst.KICK)
        s.applyStyle(Styles.HOUSE)
        assertTrue((0 until s.project.stepCount).any { s.isOn(kick, it) })
        assertTrue((0 until s.project.stepCount).none { s.isOn(Inst.KICK, it) })
    }

    @Test fun styleKeepsTheViewAndTheRecordedGesture() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank())
        s.setView(listOf(0, 1))
        s.beginGesture()
        s.recordGesture(1.0, 0.7f, 0.3f)
        s.endGesture()
        val gesture = s.project.patterns[0].autoFilter
        s.applyStyle(Styles.TECHNO)
        assertEquals(listOf(0, 1), s.project.view)
        assertTrue(gesture != null && s.project.patterns[0].autoFilter.contentEquals(gesture))
    }
}
