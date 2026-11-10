package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les motifs A à D affichés à la suite : une vue longue qu'on édite comme un seul motif, redécoupée dans les motifs. */
class StudioViewTest {

    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.blank())

    @Test
    fun spreadKeepsThePulseCountAndEvenSpacingAtEveryRotation() {
        for (hits in 1..16) for (offset in 0..15) {
            val mask = com.Atom2Universe.app.studio.music.BarRhythm.steps(hits, offset)
            val positions = mask.indices.filter { mask[it] }
            assertEquals(hits, positions.size)
            assertTrue(mask[offset])
            val velocity = FloatArray(16) { if (mask[it]) 0.4f else 0f }
            val recovered = com.Atom2Universe.app.studio.music.BarRhythm.offsetOf(velocity, hits)
            assertTrue(mask.contentEquals(com.Atom2Universe.app.studio.music.BarRhythm.steps(hits, recovered)))
            val gaps = (positions + (positions.first() + 16)).zipWithNext { a, b -> b - a }
            assertTrue(gaps.max() - gaps.min() <= 1)
        }
    }

    @Test
    fun spreadingADrumOnlyChangesTheVisibleSlotsAndUndoesInOneAction() {
        val s = session()
        s.setView(listOf(0, 2))
        s.setStep(Inst.KICK, 0, true)
        s.editStep(Inst.KICK, 0, velocity = 0.36f, hold = 140, pitch = 63)
        s.setStep(Inst.KICK, 2, true)
        s.setStep(Inst.SNARE, 5, true)
        assertTrue(s.spreadDrum(Inst.KICK, 4, 0))
        assertEquals(listOf(0, 4, 8, 12, 16, 20, 24, 28), (0..31).filter { s.isOn(Inst.KICK, it) })
        val kept = s.stepInfo(Inst.KICK, 0)!!
        assertEquals(0.36f, kept.velocity, 0f)
        assertEquals(140, kept.hold)
        assertEquals(63, kept.pitch)
        assertTrue(s.isOn(Inst.SNARE, 5))
        assertTrue(s.isPatternEmpty(1))
        assertTrue(s.isPatternEmpty(3))
        assertTrue(!s.spreadDrum(Inst.KICK, 4, 0))
        s.undo()
        assertEquals(listOf(0, 2), (0..31).filter { s.isOn(Inst.KICK, it) })
        s.redo()
        assertTrue(s.isOn(Inst.KICK, 28))
    }

    @Test
    fun twoPatternsShownTogetherFormOneLongPattern() {
        val s = session()
        s.setView(listOf(0, 1))
        assertEquals(16, s.project.baseSteps)
        assertEquals(32, s.project.stepCount)
        // Le pas 20 de la vue est le pas 4 du motif B
        s.setStep(Inst.KICK, 20, true)
        assertTrue(s.project.patterns[1].lane(Inst.KICK)!!.velocity[4] > 0f)
        assertEquals(0f, s.project.patterns[0].lane(Inst.KICK)!!.velocity.sum(), 0f)
        assertNotNull(s.stepInfo(Inst.KICK, 20))
    }

    @Test
    fun aNoteThatCrossesTheEdgeIsCutAtTheEndOfItsPattern() {
        val s = session()
        s.setView(listOf(0, 1))
        s.addNote(14, 60, 5)
        assertEquals(2, s.project.patterns[0].notes.single().length)
        assertTrue(s.project.patterns[1].notes.isEmpty())
    }

    @Test
    fun backToOnePatternGivesTheUsualView() {
        val s = session()
        s.setView(listOf(1, 2, 3))
        assertEquals(1, s.project.current)
        assertEquals(48, s.project.stepCount)
        s.setView(listOf(2))
        assertEquals(2, s.project.current)
        assertEquals(16, s.project.stepCount)
        assertEquals(listOf(2), s.project.viewSlots)
        // Il en reste toujours un
        s.setView(emptyList())
        assertEquals(listOf(2), s.project.viewSlots)
    }

    @Test
    fun resizingActsOnTheLengthOfEachPattern() {
        val s = session()
        s.setView(listOf(0, 1))
        s.setStepCount(32)
        assertEquals(32, s.project.baseSteps)
        assertEquals(64, s.project.stepCount)
        assertEquals(32, s.project.patterns[3].stepCount)
    }

    @Test
    fun theSavedFileKeepsTheLengthOfOnePattern() {
        val s = session()
        s.setStep(Inst.SNARE, 4, true)
        s.setView(listOf(0, 1))
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(16, back.baseSteps)
        assertEquals(16, back.stepCount)
        assertTrue(back.patterns[0].lane(Inst.SNARE)!!.velocity[4] > 0f)
    }

    @Test
    fun joiningThenSplittingLosesNothing() {
        val s = session()
        s.setStep(Inst.KICK, 3, true)
        s.editStep(Inst.KICK, 3, velocity = 0.6f, pitch = 66, hold = 130)
        s.addNote(5, 64, 3)
        val a = s.project.patterns[0]
        val c = StudioProject.blank().patterns[2]
        val joined = StepPattern.concat(listOf(a, c))
        assertEquals(32, joined.stepCount)
        val parts = StepPattern.split(joined, 2, 16)
        val kick = parts[0].lane(Inst.KICK)!!
        assertEquals(0.6f, kick.velocity[3], 1e-6f)
        assertEquals(66, kick.note[3])
        assertEquals(1.3f, kick.hold[3], 1e-6f)
        assertEquals(listOf(Triple(5, 64, 3)), parts[0].notes.map { Triple(it.step, it.pitch, it.length) })
        assertTrue(parts[1].notes.isEmpty())
    }
}
