package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.Kick
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les réglages d'un pas : force, longueur et hauteur, lus et modifiés par la session, sauvegardés, et entendus. */
class StudioStepEditTest {

    private fun session() = StudioSession(StudioEngine(48_000), StudioProject.blank()).apply { coalesceMs = 0 }

    @Test
    fun aDrumStepKeepsItsStrengthAndPitch() {
        val s = session()
        s.setStep(Inst.KICK, 4, true)
        s.editStep(Inst.KICK, 4, velocity = 0.55f, pitch = 64)
        val info = s.stepInfo(Inst.KICK, 4)!!
        assertEquals(0.55f, info.velocity, 1e-6f)
        assertEquals(64, info.pitch)
        assertEquals(1, info.length)
        // Hors des limites : on reste entre 48 et 72
        s.editStep(Inst.KICK, 4, pitch = 100)
        assertEquals(72, s.stepInfo(Inst.KICK, 4)!!.pitch)
        // Un pas éteint n'a rien à régler
        assertNull(s.stepInfo(Inst.KICK, 5))
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(72, back.pattern.lane(Inst.KICK)!!.note[4])
        assertEquals(60, back.pattern.lane(Inst.KICK)!!.note[5])
    }

    @Test
    fun aNoteStepCanBeLengthenedAndTransposed() {
        val s = session()
        s.addNote(2, 60, 1)
        s.addNote(3, 60, 1)
        // Allonger la première note recouvre la suivante, à la même hauteur
        s.editStep(Inst.SYNTH, 2, length = 3)
        val notes = s.project.pattern.notes.filter { it.track == Inst.SYNTH }
        assertEquals(1, notes.size)
        assertEquals(3, notes.single().length)
        s.editStep(Inst.SYNTH, 2, pitch = 67, velocity = 0.4f)
        val n = s.project.pattern.notes.single { it.track == Inst.SYNTH }
        assertEquals(67, n.pitch)
        assertEquals(0.4f, n.velocity, 1e-6f)
        assertEquals(3, n.length)
        // La longueur ne dépasse pas la fin de la boucle
        s.editStep(Inst.SYNTH, 2, length = 99)
        assertEquals(s.project.stepCount - 2, s.project.pattern.notes.single { it.track == Inst.SYNTH }.length)
        assertNotNull(s.stepInfo(Inst.SYNTH, 2))
    }

    @Test
    fun theTailOfALongNoteIsNotAFreeStep() {
        val s = session()
        s.addNote(2, 60, 5)
        assertTrue(s.isTail(Inst.SYNTH, 4))
        assertTrue(!s.isTail(Inst.SYNTH, 2))
        assertTrue(!s.isTail(Inst.SYNTH, 7))
        // Toucher le prolongement ne fait rien : la note se règle par sa bulle
        s.setStep(Inst.SYNTH, 4, true)
        val notes = s.project.pattern.notes.filter { it.track == Inst.SYNTH }
        assertEquals(1, notes.size)
        assertEquals(5, notes.single().length)
    }

    @Test
    fun aDrumHitCanLastLongerAndTheDurationIsSaved() {
        val s = session()
        s.setStep(Inst.SNARE, 4, true)
        s.setStep(Inst.SNARE, 8, true)
        s.editStep(Inst.SNARE, 4, hold = 150)
        assertEquals(150, s.stepInfo(Inst.SNARE, 4)!!.hold)
        assertEquals(100, s.stepInfo(Inst.SNARE, 8)!!.hold)
        s.editStep(Inst.SNARE, 4, hold = 900)
        assertEquals(200, s.stepInfo(Inst.SNARE, 4)!!.hold)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(2f, back.pattern.lane(Inst.SNARE)!!.hold[4], 1e-6f)
        assertEquals(1f, back.pattern.lane(Inst.SNARE)!!.hold[8], 1e-6f)
    }

    @Test
    fun aLongerHitKeepsRingingInEveryDrum() {
        val makers = listOf<() -> com.Atom2Universe.app.studio.dsp.Instrument>(
            { com.Atom2Universe.app.studio.dsp.Kick(48_000) }, { com.Atom2Universe.app.studio.dsp.Snare(48_000) },
            { com.Atom2Universe.app.studio.dsp.Clap(48_000) }, { com.Atom2Universe.app.studio.dsp.Hat(48_000, false) },
            { com.Atom2Universe.app.studio.dsp.Hat(48_000, true) }, { com.Atom2Universe.app.studio.dsp.Tom(48_000) },
            { com.Atom2Universe.app.studio.dsp.Perc(48_000) },
        )
        fun tail(make: () -> com.Atom2Universe.app.studio.dsp.Instrument, hold: Float): Double {
            val ins = make()
            ins.hold = hold
            ins.noteOn(60, 1f)
            val l = FloatArray(24_000)
            ins.render(l, FloatArray(l.size), FloatArray(l.size), FloatArray(l.size), 0, l.size)
            var e = 0.0
            for (i in 7_200 until 14_400) e += l[i].toDouble() * l[i]
            return e
        }
        for (make in makers) assertTrue(tail(make, 2f) > tail(make, 0.5f))
    }

    @Test
    fun copyAndPasteKeepsStrengthPitchAndDuration() {
        val s = session()
        s.setStep(Inst.KICK, 0, true)
        s.setStep(Inst.KICK, 4, true)
        s.setStep(Inst.SNARE, 4, true)
        s.editStep(Inst.KICK, 4, velocity = 0.5f, pitch = 64, hold = 150)
        val clip = s.copyRange(setOf(Inst.KICK, Inst.SNARE), 0, 7)
        assertEquals(3, clip.hits.size)
        s.paste(clip, 8)
        assertNotNull(s.stepInfo(Inst.KICK, 8))
        val moved = s.stepInfo(Inst.KICK, 12)!!
        assertEquals(0.5f, moved.velocity, 1e-6f)
        assertEquals(64, moved.pitch)
        assertEquals(150, moved.hold)
        assertNotNull(s.stepInfo(Inst.SNARE, 12))
        // Ce qui sortirait de la boucle (16 pas) est ignoré
        s.paste(clip, 14)
        assertNotNull(s.stepInfo(Inst.KICK, 14))
        assertEquals(16, s.project.pattern.lane(Inst.KICK)!!.velocity.size)
    }

    @Test
    fun cuttingRemovesTheStepsAndUndoBringsThemBack() {
        val s = session()
        s.setStep(Inst.KICK, 2, true)
        s.setStep(Inst.KICK, 9, true)
        val clip = s.copyRange(setOf(Inst.KICK), 0, 4)
        assertEquals(1, clip.hits.size)
        s.clearRange(setOf(Inst.KICK), 0, 4)
        assertNull(s.stepInfo(Inst.KICK, 2))
        assertNotNull(s.stepInfo(Inst.KICK, 9))
        s.undo()
        assertNotNull(s.stepInfo(Inst.KICK, 2))
    }

    @Test
    fun pastedNotesAreTransposedAndLandOnTheChosenTrack() {
        val s = session()
        s.addNote(0, 60, 2)
        s.addNote(2, 64, 1)
        s.addNote(3, 72, 1)
        val clip = s.copyRange(setOf(Inst.SYNTH), 0, 3, 60, 64)
        assertEquals(2, clip.notes.size)
        assertEquals(60, clip.lowPitch)
        val second = s.addTrack(com.Atom2Universe.app.studio.engine.TrackKind.SYNTH)
        s.paste(clip, 8, pitchShift = 5, noteTrack = second)
        val got = s.project.pattern.notes.filter { it.track == second }.map { Triple(it.step, it.pitch, it.length) }.toSet()
        assertEquals(setOf(Triple(8, 65, 2), Triple(10, 69, 1)), got)
        // La note de 72 n'était pas dans le cadre : elle n'a pas été copiée
        assertTrue(s.project.pattern.notes.none { it.track == second && it.pitch == 77 })
    }

    @Test
    fun aKickStepPitchRaisesTheSound() {
        fun crossings(note: Int): Int {
            val k = Kick(48_000)
            k.noteOn(note, 1f)
            val l = FloatArray(24_000)
            k.render(l, FloatArray(l.size), FloatArray(l.size), FloatArray(l.size), 0, l.size)
            var n = 0
            for (i in 1 until l.size) if ((l[i - 1] < 0f) != (l[i] < 0f)) n++
            return n
        }
        assertTrue(crossings(72) > crossings(60))
        assertTrue(crossings(60) > crossings(48))
    }
}
