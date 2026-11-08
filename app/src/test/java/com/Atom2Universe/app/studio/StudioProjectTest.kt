package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Styles, Hasard, sauvegarde JSON et modifications du projet. */
class StudioProjectTest {

    @Test
    fun everyStyleBuildsAPlayablePattern() {
        for (style in Styles.ALL) for (steps in listOf(16, 32)) {
            val p = Styles.generate(style, steps, 42L)
            assertEquals(Inst.DRUM_COUNT, p.lanes.size)
            for ((i, lane) in p.lanes.withIndex()) {
                assertEquals(i, lane.instrument)
                assertEquals(steps, lane.velocity.size)
                for (v in lane.velocity) assertTrue(v in 0f..1f)
            }
            for (n in p.notes) {
                assertTrue(n.step in 0 until steps)
                assertTrue(n.length >= 1 && n.step + n.length <= steps)
                assertTrue(n.pitch in 0..127)
            }
            val kicks = p.lanes[Inst.KICK].velocity.count { it > 0f }
            assertTrue("${style.id} : grosse caisse absente", kicks >= 1)
            // jamais de charleston fermé sur un pas où l'ouvert joue
            for (s in 0 until steps) {
                assertFalse(p.lanes[Inst.HAT].velocity[s] > 0f && p.lanes[Inst.OPEN_HAT].velocity[s] > 0f)
            }
        }
    }

    @Test
    fun sameSeedSamePatternAndDifferentSeedDiffers() {
        val a = Styles.generate(Styles.HOUSE, 16, 1L)
        val b = Styles.generate(Styles.HOUSE, 16, 1L)
        val c = Styles.generate(Styles.HOUSE, 16, 2L)
        for (i in 0 until Inst.DRUM_COUNT) assertArrayEquals(a.lanes[i].velocity, b.lanes[i].velocity, 0f)
        var differs = false
        for (i in 0 until Inst.DRUM_COUNT) if (!a.lanes[i].velocity.contentEquals(c.lanes[i].velocity)) differs = true
        assertTrue(differs)
    }

    @Test
    fun houseKeepsItsFourOnTheFloor() {
        val kick = Styles.generate(Styles.HOUSE, 16, 12345L).lanes[Inst.KICK].velocity
        for (s in listOf(0, 4, 8, 12)) assertTrue("temps $s", kick[s] > 0f)
    }

    @Test
    fun projectSurvivesJsonRoundTrip() {
        val original = StudioProject.fromStyle(Styles.LOFI, 32, 7L).let { p -> p.copy(tracks = p.tracks.map { t -> t.copy(muted = t.id in setOf(3, 5)) }, bpm = 91, swing = 0.17f) }
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(original))
        assertNotNull(back)
        back!!
        assertEquals("lofi", back.styleId)
        assertEquals(91, back.bpm)
        assertEquals(0.17f, back.swing, 1e-6f)
        assertEquals(setOf(3, 5), back.muted)
        assertEquals(32, back.stepCount)
        for (i in 0 until Inst.DRUM_COUNT) {
            assertArrayEquals(original.pattern.lanes[i].velocity, back.pattern.lanes[i].velocity, 0f)
        }
        assertEquals(original.pattern.notes.size, back.pattern.notes.size)
        for (i in original.pattern.notes.indices) {
            val a = original.pattern.notes[i]
            val b = back.pattern.notes[i]
            assertEquals(a.step, b.step)
            assertEquals(a.pitch, b.pitch)
            assertEquals(a.length, b.length)
            assertEquals(a.velocity, b.velocity, 1e-6f)
        }
        assertEquals(original.track(Inst.SYNTH).synth, back.track(Inst.SYNTH).synth)
        assertEquals(original.muted, back.muted)
        assertEquals(original.keyPc, back.keyPc)
        assertEquals(original.scaleId, back.scaleId)
        assertEquals(original.track(Inst.SYNTH).mix, back.track(Inst.SYNTH).mix)
    }

    @Test
    fun damagedJsonGivesNullNotACrash() {
        assertNull(StudioProjectCodec.decode(null))
        assertNull(StudioProjectCodec.decode(""))
        assertNull(StudioProjectCodec.decode("pas du json"))
        assertNull(StudioProjectCodec.decode("{\"version\":1}"))
        val good = StudioProjectCodec.encode(StudioProject.fromStyle(Styles.HOUSE))
        assertNull(StudioProjectCodec.decode(good.replace("\"steps\":16", "\"steps\":17")))
        assertNull(StudioProjectCodec.decode(good.substring(0, good.length / 2)))
    }

    @Test
    fun sessionEditsPatternAndPushesItToTheEngine() {
        val engine = StudioEngine(48_000)
        val session = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        assertTrue(session.isOn(Inst.KICK, 0))
        session.setStep(Inst.KICK, 0, false)
        assertFalse(session.isOn(Inst.KICK, 0))
        assertEquals(0f, engine.pattern.lanes[Inst.KICK].velocity[0], 0f)
        session.toggleStep(Inst.TOM, 3)
        assertTrue(session.isOn(Inst.TOM, 3))
        // fort -> moyen -> doux -> fort
        assertEquals(0.7f, session.cycleVelocity(Inst.TOM, 3), 0f)
        assertEquals(0.4f, session.cycleVelocity(Inst.TOM, 3), 0f)
        assertEquals(1.0f, session.cycleVelocity(Inst.TOM, 3), 0f)
        assertEquals(0f, session.cycleVelocity(Inst.TOM, 4), 0f) // pas éteint : rien ne change
    }

    @Test
    fun stepCountDoublesByRepeatingAndKeepsTheStartWhenShrinking() {
        val engine = StudioEngine(48_000)
        val session = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        val before = session.project.pattern.lanes[Inst.KICK].velocity.copyOf()
        session.setStepCount(32)
        val doubled = session.project.pattern.lanes[Inst.KICK].velocity
        assertEquals(32, doubled.size)
        for (i in 0 until 32) assertEquals(before[i % 16], doubled[i], 0f)
        session.setStepCount(16)
        assertArrayEquals(before, session.project.pattern.lanes[Inst.KICK].velocity, 0f)
    }

    @Test
    fun styleRandomClearAndUndo() {
        val engine = StudioEngine(48_000)
        val session = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        assertFalse(session.canUndo)
        val house = session.project.pattern.lanes[Inst.KICK].velocity.copyOf()

        session.clear()
        assertTrue(session.canUndo)
        assertEquals(0, session.project.pattern.lanes.sumOf { l -> l.velocity.count { it > 0f } })
        assertEquals(0, session.project.pattern.notes.size)
        session.undo()
        assertArrayEquals(house, session.project.pattern.lanes[Inst.KICK].velocity, 0f)
        assertFalse(session.canUndo)

        session.applyStyle(Styles.HIPHOP)
        assertEquals("hiphop", session.project.styleId)
        assertEquals(88, session.project.bpm)
        assertEquals(88f, engine.bpm, 0f)
        assertEquals(0.22f, engine.swing, 1e-6f)

        val bpm = session.project.bpm
        session.randomize(99L)
        assertEquals("le Hasard ne change pas le tempo", bpm, session.project.bpm)
        assertEquals("hiphop", session.project.styleId)
    }

    @Test
    fun muteAndEffectAmountsReachTheEngine() {
        val engine = StudioEngine(48_000)
        val session = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        session.setMuted(Inst.SNARE, true)
        assertTrue(engine.instrument(Inst.SNARE)!!.muted)
        assertTrue(session.isMuted(Inst.SNARE))
        session.setMuted(Inst.SNARE, false)
        assertFalse(engine.instrument(Inst.SNARE)!!.muted)
        session.setSpace(0.7f)
        session.setEcho(0.3f)
        assertEquals(0.7f, engine.spaceAmount, 0f)
        assertEquals(0.3f, engine.echoAmount, 0f)
        session.setBpm(500)
        assertEquals(220f, engine.bpm, 0f)
    }
}
