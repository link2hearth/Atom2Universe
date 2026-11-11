package com.Atom2Universe.app.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioPersist
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioFmSessionDeviceTest {
    private val track = Inst.SYNTH

    @Test fun liveDragAddsOneUndoEntryAndSurvivesSaveAndBankChanges() {
        val initial = StudioProject.blank()
        val journal = mutableListOf<String>()
        val persist = object : StudioPersist {
            override fun append(lines: List<String>) { journal.addAll(lines) }
            override fun compact(base: StudioProject, lines: List<String>) = error("Journal court")
        }
        StudioEngine(48_000).use { engine ->
            val session = StudioSession(engine, initial, persist = persist)
            session.setSynthBank(track, SynthBank.SCC)
            session.setChipProgram(track, 73)
            session.setSynthBank(track, SynthBank.FM)
            session.setFmProgram(track, 0)
            session.flush()
            val beforeDrag = session.project
            val entries = journal.size
            repeat(40) { session.setFmPad(track, it / 40f, .75f) }
            assertSame(beforeDrag, session.project)
            session.setFmPad(track, .23f, .91f)
            session.flush() // Fermeture de l'écran même si le doigt était encore sur le pad.
            session.endFmPad()
            assertEquals(entries + 1, journal.size)
            val edited = session.project.track(track)
            assertEquals(.23f, edited.fmBrightness, 0f)
            assertEquals(.91f, edited.fmAttack, 0f)
            val replay = StudioHistory.replay(initial, journal)
            assertEquals(edited, replay.project.track(track))
            assertEquals(edited, StudioProjectCodec.decode(StudioProjectCodec.encode(session.project))!!.track(track))
            session.undo()
            assertEquals(beforeDrag.track(track), session.project.track(track))
            session.redo()
            assertEquals(edited, session.project.track(track))
            session.setFmProgram(track, 12)
            session.setFmPad(track, 0f, 1f)
            session.endFmPad()
            session.setSynthBank(track, SynthBank.STUDIO)
            session.setSynthBank(track, SynthBank.FAMICOM)
            assertEquals(73, session.project.track(track).program)
            session.setSynthBank(track, SynthBank.FM)
            assertEquals(12, session.project.track(track).fmProgram)
            session.setFmProgram(track, 0)
            assertEquals(edited, session.project.track(track))
        }
    }

    @Test fun removingAnFmTrackCommitsItsPadAndUndoRestoresAPlayableChip() {
        StudioEngine(48_000).use { engine ->
            val session = StudioSession(engine, StudioProject.blank())
            session.setSynthBank(track, SynthBank.FM)
            session.setFmProgram(track, 0)
            session.setFmPad(track, .8f, .13f)
            session.removeTrack(track)
            assertNull(engine.synthOf(track))
            session.undo()
            val restored = session.project.track(track)
            assertEquals(SynthBank.FM, restored.synthBank)
            assertEquals(.8f, restored.fmBrightness, 0f)
            assertEquals(.13f, restored.fmAttack, 0f)
            val left = FloatArray(2048)
            engine.trigger(track, 64)
            engine.render(left, FloatArray(left.size), left.size)
            assertTrue(left.any { kotlin.math.abs(it) > .001f })
        }
    }
}
