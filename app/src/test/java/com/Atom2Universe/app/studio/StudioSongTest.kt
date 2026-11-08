package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Motifs A à D et morceau : édition indépendante, copie, enchaînement dans le moteur, sauvegarde. */
class StudioSongTest {

    private val rate = 48_000

    private fun session(): Pair<StudioEngine, StudioSession> {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.clear() // le motif A devient vide
        s.setSpace(0f)
        s.setEcho(0f)
        for (lane in 0 until Inst.COUNT) s.updateTrack(lane) { it.copy(reverbSend = 0f, delaySend = 0f) }
        return engine to s
    }

    private fun render(engine: StudioEngine, frames: Int): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(256)
        val r = FloatArray(256)
        var done = 0
        while (done < frames) {
            val n = minOf(256, frames - done)
            engine.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from))
    }

    @Test
    fun patternsAreEditedIndependently() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.selectPattern(1)
        assertFalse(s.isOn(Inst.KICK, 0))
        s.setStep(Inst.SNARE, 4, true)
        s.selectPattern(0)
        assertTrue(s.isOn(Inst.KICK, 0))
        assertFalse(s.isOn(Inst.SNARE, 4))
        assertFalse(s.isPatternEmpty(0))
        assertFalse(s.isPatternEmpty(1))
        assertTrue(s.isPatternEmpty(2))
    }

    @Test
    fun copyPatternDuplicatesIt() {
        val (_, s) = session()
        s.setStep(Inst.CLAP, 8, true)
        s.copyPattern(0, 3)
        s.selectPattern(3)
        assertTrue(s.isOn(Inst.CLAP, 8))
        s.setStep(Inst.CLAP, 8, false)
        s.selectPattern(0)
        assertTrue("la copie ne partage rien avec l'original", s.isOn(Inst.CLAP, 8))
    }

    @Test
    fun songPlaysPatternsOneAfterAnother() {
        val (engine, s) = session()
        s.setStep(Inst.KICK, 0, true) // A : une grosse caisse au début ; B reste vide
        s.chainAdd(0)
        s.chainAdd(1)
        s.chainAdd(0)
        s.setSongMode(true)
        engine.bpm = 120f
        engine.play()
        val loop = 16 * 6_000
        val out = render(engine, loop * 4)
        val win = 20_000
        val first = rms(out, 0, win)
        val second = rms(out, loop, loop + win)
        val third = rms(out, loop * 2, loop * 2 + win)
        val fourth = rms(out, loop * 3, loop * 3 + win) // le morceau A-B-A recommence : A de nouveau
        assertTrue("A sonne ($first)", first > 0.02)
        assertTrue("B est vide ($second)", second < first * 0.1)
        assertTrue("A revient ($third)", third > first * 0.5)
        assertTrue("le morceau recommence par A ($fourth)", fourth > first * 0.5)
        assertEquals("le morceau a fait deux fois le tour de ses trois rangs : retour au rang 0 au 4e tour", 0, engine.songPosition)
    }

    @Test
    fun withoutSongModeItLoopsOnTheCurrentPattern() {
        val (engine, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.chainAdd(0)
        s.chainAdd(1)
        // morceau écrit mais pas activé
        engine.bpm = 120f
        engine.play()
        val loop = 16 * 6_000
        val out = render(engine, loop * 2)
        assertTrue(rms(out, loop, loop + 20_000) > 0.02)
    }

    @Test
    fun chainEditing() {
        val (_, s) = session()
        s.chainAdd(0)
        s.chainAdd(2)
        assertEquals(listOf(0, 2), s.project.chain)
        s.setSongMode(true)
        assertTrue(s.project.songMode)
        s.chainRemoveLast()
        assertEquals(listOf(0), s.project.chain)
        s.chainRemoveLast()
        assertTrue("sans morceau, le mode s'éteint", !s.project.songMode)
        for (k in 0 until 40) s.chainAdd(1)
        assertEquals(StudioProject.MAX_CHAIN, s.project.chain.size)
        s.chainClear()
        assertTrue(s.project.chain.isEmpty())
        s.setSongMode(true)
        assertFalse("pas de morceau à jouer", s.project.songMode)
    }

    @Test
    fun stepCountChangesEveryPattern() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.copyPattern(0, 2)
        s.setStepCount(32)
        for (slot in 0 until StudioProject.SLOTS) assertEquals(32, s.project.patterns[slot].stepCount)
        s.selectPattern(2)
        assertTrue(s.isOn(Inst.KICK, 16))
        s.setStepCount(16)
        for (slot in 0 until StudioProject.SLOTS) assertEquals(16, s.project.patterns[slot].stepCount)
    }

    @Test
    fun styleOnlyReplacesTheCurrentPattern() {
        val (_, s) = session()
        s.setStep(Inst.CLAP, 8, true)
        s.selectPattern(2)
        s.setStep(Inst.TOM, 3, true)
        s.chainAdd(0)
        s.chainAdd(2)
        s.applyStyle(Styles.TECHNO)
        assertEquals(2, s.project.current)
        assertTrue("l'autre motif est intact", !s.isPatternEmpty(0))
        s.selectPattern(0)
        assertTrue(s.isOn(Inst.CLAP, 8))
        assertEquals(listOf(0, 2), s.project.chain)
    }

    @Test
    fun songSurvivesTheCodec() {
        val (_, s) = session()
        s.setStep(Inst.KICK, 0, true)
        s.copyPattern(0, 1)
        s.selectPattern(1)
        s.chainAdd(0)
        s.chainAdd(1)
        s.setSongMode(true)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(1, back.current)
        assertEquals(listOf(0, 1), back.chain)
        assertTrue(back.songMode)
        assertEquals(StudioProject.SLOTS, back.patterns.size)
        assertTrue(back.patterns[0].lanes[Inst.KICK].velocity[0] > 0f)
        assertTrue(back.patterns[2].notes.isEmpty())
    }
}
