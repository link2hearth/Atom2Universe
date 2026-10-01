package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.DspTest.RATE
import com.Atom2Universe.app.audioeditor.dsp.ChangePitch
import com.Atom2Universe.app.audioeditor.dsp.ChangeSpeed
import com.Atom2Universe.app.audioeditor.dsp.ChangeTempo
import com.Atom2Universe.app.audioeditor.dsp.PaulStretch
import com.Atom2Universe.app.audioeditor.dsp.TruncateSilence
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TimePitchTest {

    private val tone = DspTest.sine(RATE * 3, 440.0, 0.5f)

    @Test
    fun `la vitesse double raccourcit de moitie et monte d'une octave`() {
        val out = ChangeSpeed(2f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(RATE * 1.5, out.size.toDouble(), 4.0)
        assertEquals(880.0, DspTest.zeroCrossFreq(out), 12.0)
    }

    @Test
    fun `la demi-vitesse allonge et descend d'une octave`() {
        val out = ChangeSpeed(0.5f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(RATE * 6.0, out.size.toDouble(), 4.0)
        assertEquals(220.0, DspTest.zeroCrossFreq(out), 6.0)
        assertEquals(DspTest.rms(tone), DspTest.rms(out), 0.01)
    }

    @Test
    fun `le tempo accelere sans changer la hauteur`() {
        val out = ChangeTempo(1.5f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(RATE * 2.0, out.size.toDouble(), RATE * 0.04)
        assertEquals(440.0, DspTest.zeroCrossFreq(out), 6.0)
        assertEquals(DspTest.rms(tone), DspTest.rms(out), DspTest.rms(tone) * 0.1)
    }

    @Test
    fun `le tempo ralentit sans changer la hauteur`() {
        val out = ChangeTempo(0.5f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(RATE * 6.0, out.size.toDouble(), RATE * 0.06)
        assertEquals(440.0, DspTest.zeroCrossFreq(out), 6.0)
    }

    @Test
    fun `un tempo de un redonne le signal`() {
        val out = ChangeTempo(1f).processInMemory(arrayOf(tone), RATE)[0]
        // Hors des bords (demi-fenêtre au début / à la fin), la reconstruction est exacte.
        for (i in 2000 until RATE * 3 - 2000) assertEquals("trame $i", tone[i], out[i], 2e-3f)
    }

    @Test
    fun `le tempo garde un son complexe sans le deteriorer`() {
        // Une voix de synthèse : fondamentale + harmoniques, avec un vibrato lent.
        var phase = 0.0
        val voice = FloatArray(RATE * 2) { i ->
            val f = 180.0 * (1 + 0.01 * Math.sin(2 * Math.PI * 4 * i / RATE))
            phase += 2 * Math.PI * f / RATE // la phase est intégrée : la fréquence instantanée reste 180 Hz ± 1 %
            (0.3 * Math.sin(phase) + 0.15 * Math.sin(2 * phase) + 0.08 * Math.sin(3 * phase)).toFloat()
        }
        val out = ChangeTempo(1.3f).processInMemory(arrayOf(voice), RATE)[0]
        assertEquals(RATE * 2 / 1.3, out.size.toDouble(), RATE * 0.04)
        assertEquals(DspTest.rms(voice), DspTest.rms(out), DspTest.rms(voice) * 0.15)
        assertEquals(180.0, DspTest.dominantFreq(out, 20_000), 5.0)
    }

    @Test
    fun `le tempo traite les deux voies avec le meme decalage`() {
        val r = DspTest.sine(RATE * 2, 660.0, 0.4f)
        val l = DspTest.sine(RATE * 2, 440.0, 0.4f)
        val out = ChangeTempo(1.2f).processInMemory(arrayOf(l, r), RATE)
        assertEquals(out[0].size, out[1].size)
        assertEquals(440.0, DspTest.zeroCrossFreq(out[0]), 6.0)
        assertEquals(660.0, DspTest.zeroCrossFreq(out[1]), 9.0)
    }

    @Test
    fun `la hauteur monte d'une octave sans changer la duree`() {
        val out = ChangePitch(12f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(tone.size.toDouble(), out.size.toDouble(), RATE * 0.05)
        assertEquals(880.0, DspTest.zeroCrossFreq(out), 12.0)
        assertEquals(DspTest.rms(tone), DspTest.rms(out), DspTest.rms(tone) * 0.12)
    }

    @Test
    fun `la hauteur descend d'une quinte`() {
        val out = ChangePitch(-7f).processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(tone.size.toDouble(), out.size.toDouble(), RATE * 0.05)
        assertEquals(440.0 * Math.pow(2.0, -7.0 / 12), DspTest.zeroCrossFreq(out), 8.0)
    }

    @Test
    fun `paulstretch etire en gardant la hauteur`() {
        val x = DspTest.sine(RATE * 2, 440.0, 0.5f)
        val out = PaulStretch(stretch = 4f, windowSec = 0.25f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(RATE * 8.0, out.size.toDouble(), RATE * 0.5)
        assertEquals(440.0, DspTest.dominantFreq(out, RATE * 3), 6.0)
        assertTrue(DspTest.peak(out) < 1f)
        assertEquals(DspTest.rms(x), DspTest.rms(out, RATE, out.size - RATE), DspTest.rms(x) * 0.3)
    }

    @Test
    fun `paulstretch conserve le niveau d'un bruit`() {
        val rnd = Random(5)
        val noise = FloatArray(RATE * 2) { (rnd.nextFloat() - 0.5f) * 0.4f }
        val out = PaulStretch(stretch = 3f).processInMemory(arrayOf(noise), RATE)[0]
        assertEquals(DspTest.rms(noise), DspTest.rms(out, RATE / 2, out.size - RATE / 2), DspTest.rms(noise) * 0.2)
    }

    @Test
    fun `paulstretch est reproductible`() {
        val x = DspTest.sine(RATE, 330.0, 0.5f)
        val a = PaulStretch(3f, seed = 9).processInMemory(arrayOf(x), RATE)[0]
        val b = PaulStretch(3f, seed = 9).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(a.size, b.size)
        for (i in a.indices step 97) assertEquals(a[i], b[i], 0f)
    }

    @Test
    fun `les longs silences sont raccourcis et les courts respectes`() {
        // 0,5 s de son, 1 s de silence, 0,5 s de son, 0,1 s de silence, 0,5 s de son.
        val seg = { n: Double, amp: Float -> DspTest.sine((n * RATE).toInt(), 440.0, amp) }
        val x = seg(0.5, 0.5f) + FloatArray(RATE) + seg(0.5, 0.5f) + FloatArray(RATE / 10) + seg(0.5, 0.5f)
        val out = TruncateSilence(thresholdDb = -50f, minSilenceMs = 200f, keepMs = 50f).processInMemory(arrayOf(x), RATE)[0]
        // Le silence d'une seconde passe à 50 ms ; celui de 100 ms (sous le minimum) reste.
        // (±3 trames : le premier échantillon d'une tonalité vaut exactement 0 et se range dans le silence qui précède.)
        assertEquals((x.size - (RATE - RATE / 20)).toDouble(), out.size.toDouble(), 3.0)
        assertEquals(DspTest.peak(x), DspTest.peak(out), 1e-6f)
    }

    @Test
    fun `un silence final long est raccourci`() {
        val x = DspTest.sine(RATE, 440.0, 0.5f) + FloatArray(RATE * 2)
        val out = TruncateSilence(minSilenceMs = 100f, keepMs = 20f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(RATE + RATE / 50, out.size)
    }

    @Test
    fun `sans silence rien ne change`() {
        val out = TruncateSilence().processInMemory(arrayOf(tone), RATE)[0]
        assertEquals(tone.size, out.size)
        for (i in tone.indices step 101) assertEquals(tone[i], out[i], 0f)
    }
}
