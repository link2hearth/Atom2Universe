package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.Clap
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.dsp.Hat
import com.Atom2Universe.app.studio.dsp.Instrument
import com.Atom2Universe.app.studio.dsp.Kick
import com.Atom2Universe.app.studio.dsp.Perc
import com.Atom2Universe.app.studio.dsp.Snare
import com.Atom2Universe.app.studio.dsp.Tom
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le son d'une piste de batterie : variante, hauteur et durée changent vraiment le son, et se sauvegardent. */
class StudioDrumVoiceTest {

    private val rate = 48_000

    /** Frappe l'instrument une fois à pleine vélocité et rend [frames] trames (mono, gauche). */
    private fun hit(ins: Instrument, frames: Int = rate): FloatArray {
        ins.noteOn(60, 1f)
        val l = FloatArray(frames)
        val r = FloatArray(frames)
        val bus = FloatArray(frames)
        ins.render(l, r, bus, FloatArray(frames), 0, frames)
        return l
    }

    private fun energy(a: FloatArray, from: Int, to: Int): Double {
        var e = 0.0
        for (i in from until to) e += a[i].toDouble() * a[i]
        return e
    }

    private fun crossings(a: FloatArray, to: Int): Int {
        var n = 0
        for (i in 1 until to) if ((a[i - 1] < 0f) != (a[i] < 0f)) n++
        return n
    }

    private fun allDrums(): List<() -> Instrument> = listOf(
        { Kick(rate) }, { Snare(rate) }, { Clap(rate) }, { Hat(rate, false) }, { Hat(rate, true) }, { Tom(rate) }, { Perc(rate) },
    )

    @Test
    fun theOriginalVoiceIsUnchangedByTheDefaultSettings() {
        for (make in allDrums()) {
            val plain = hit(make())
            val configured = make().also { it.setVoice(0, 0, 100) }
            assertArrayEquals(plain, hit(configured), 0f)
        }
    }

    @Test
    fun everyKitSoundsDifferent() {
        for (make in allDrums()) {
            val sounds = (0 until 3).map { kit -> hit(make().also { it.setVoice(kit, 0, 100) }) }
            for (a in 0 until 3) for (b in a + 1 until 3) {
                assertTrue("kits $a and $b are identical", !sounds[a].contentEquals(sounds[b]))
            }
        }
    }

    @Test
    fun aLongerLengthKeepsTheTailAlive() {
        for (make in allDrums()) {
            val short = hit(make().also { it.setVoice(0, 0, 50) })
            val long = hit(make().also { it.setVoice(0, 0, 200) })
            // Entre 150 et 300 ms : la queue de la version longue est plus forte
            assertTrue(energy(long, 7_200, 14_400) > energy(short, 7_200, 14_400))
        }
    }

    @Test
    fun aHigherTuneRaisesTheKick() {
        val low = hit(Kick(rate).also { it.setVoice(0, -12, 100) })
        val high = hit(Kick(rate).also { it.setVoice(0, 12, 100) })
        assertTrue(crossings(high, 24_000) > crossings(low, 24_000))
    }

    @Test
    fun theSettingsAreClampedAndSaved() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        s.setDrumVoice(Inst.SNARE, kit = 9, tune = -40, length = 500)
        val t = s.project.track(Inst.SNARE)
        assertEquals(2, t.kit)
        assertEquals(-12, t.tune)
        assertEquals(200, t.length)
        s.setDrumVoice(Inst.KICK, 1, 5, 80)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(s.project.tracks, back.tracks)
        assertEquals(Triple(1, 5, 80), back.track(Inst.KICK).let { Triple(it.kit, it.tune, it.length) })
    }

    @Test
    fun aSynthTrackIgnoresDrumSettings() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        s.setDrumVoice(Inst.SYNTH, 2, 3, 120)
        assertEquals(0, s.project.track(Inst.SYNTH).kit)
    }

    private class FakeKit : ExternalSynth {
        val notes = ArrayList<Int>()
        override fun noteOn(note: Int, velocity: Int) {
            notes += note
        }

        override fun noteOff(note: Int) {}
        override fun allOff() {}
        override fun setProgram(bank: Int, program: Int) {}
        override fun render(left: FloatArray, right: FloatArray, count: Int) {}
        override val activeVoices: Int get() = 0
    }

    @Test
    fun aSoundfontKitPlaysTheGmDrumNote() {
        val engine = StudioEngine(rate)
        val created = ArrayList<Pair<Int, Int>>()
        val sources = ArrayList<FakeKit>()
        engine.sf2Factory = { _, bank, program ->
            created += bank to program
            FakeKit().also { sources += it }
        }
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.setDrumSoundfont(Inst.SNARE, 5)
        assertEquals(listOf(128 to 5), created)
        engine.trigger(Inst.SNARE, 60, 1f)
        engine.render(FloatArray(256), FloatArray(256), 256)
        assertEquals(listOf(38), sources.single().notes)

        // Le kit survit à la sauvegarde ; « aucun » rend le son fabriqué
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(5, back.track(Inst.SNARE).sfKit)
        s.setDrumSoundfont(Inst.SNARE, -1)
        assertEquals(-1, s.project.track(Inst.SNARE).sfKit)
    }
}
