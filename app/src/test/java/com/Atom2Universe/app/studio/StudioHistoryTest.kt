package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioHistory
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Annuler et rétablir : jusqu'à 100 actions, le mixage n'est pas touché, un trait forme une seule action. */
class StudioHistoryTest {

    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 0 }

    @Test
    fun undoAndRedoWalkThroughEachAction() {
        val s = session()
        s.setStep(Inst.KICK, 0, true)
        s.setStep(Inst.KICK, 4, true)
        s.setStep(Inst.KICK, 8, true)
        assertTrue(s.canUndo)
        assertFalse(s.canRedo)
        s.undo()
        assertFalse(s.isOn(Inst.KICK, 8))
        assertTrue(s.isOn(Inst.KICK, 4))
        s.undo()
        assertFalse(s.isOn(Inst.KICK, 4))
        s.redo()
        assertTrue(s.isOn(Inst.KICK, 4))
        assertTrue(s.canRedo)
        // une nouvelle action efface ce qu'on pouvait rétablir
        s.setStep(Inst.KICK, 12, true)
        assertFalse(s.canRedo)
    }

    @Test
    fun keepsOnlyTheLastHundredActions() {
        val s = session()
        for (i in 0 until 130) s.setStep(Inst.KICK, i % 16, i % 32 < 16)
        var n = 0
        while (s.canUndo) {
            s.undo()
            n++
        }
        assertEquals(StudioHistory.MAX, n)
    }

    @Test
    fun undoDoesNotRevertTempoOrVolume() {
        val s = session()
        s.setStep(Inst.KICK, 0, true)
        s.setBpm(150)
        s.updateTrack(Inst.KICK) { it.copy(level = 0.3f) }
        s.undo()
        assertFalse(s.isOn(Inst.KICK, 0))
        assertEquals(150, s.project.bpm)
        assertEquals(0.3f, s.mixOf(Inst.KICK).level, 1e-6f)
    }

    @Test
    fun actionsCloseTogetherCountAsOne() {
        val s = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 60_000 }
        for (step in 0 until 8) s.setStep(Inst.KICK, step, true)
        s.undo()
        assertEquals(0, (0 until 8).count { s.isOn(Inst.KICK, it) })
        assertFalse(s.canUndo)
    }
}
