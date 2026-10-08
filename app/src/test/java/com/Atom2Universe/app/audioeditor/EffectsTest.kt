package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.DspTest.RATE
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.dsp.Amplify
import com.Atom2Universe.app.audioeditor.dsp.BlockEffect
import com.Atom2Universe.app.audioeditor.dsp.FadeEffect
import com.Atom2Universe.app.audioeditor.dsp.FrameReader
import com.Atom2Universe.app.audioeditor.dsp.Invert
import com.Atom2Universe.app.audioeditor.dsp.MemoryReader
import com.Atom2Universe.app.audioeditor.dsp.MemoryWriter
import com.Atom2Universe.app.audioeditor.dsp.Normalize
import com.Atom2Universe.app.audioeditor.dsp.RemoveDc
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.StereoToMono
import com.Atom2Universe.app.audioeditor.dsp.SwapChannels
import com.Atom2Universe.app.audioeditor.dsp.dbToLinear
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class EffectsTest {

    private val sine = DspTest.sine(8000, 440.0, 0.25f)

    @Test
    fun `amplifier de 6 dB double l'amplitude`() {
        val out = Amplify(6.0206f).processInMemory(arrayOf(sine), RATE)[0]
        for (i in sine.indices) assertEquals(sine[i] * 2f, out[i], 1e-5f)
        val half = Amplify(-6.0206f).processInMemory(arrayOf(sine), RATE)[0]
        assertEquals(sine[100] * 0.5f, half[100], 1e-5f)
    }

    @Test
    fun `inverser change le signe`() {
        val out = Invert().processInMemory(arrayOf(sine), RATE)[0]
        for (i in sine.indices) assertEquals(-sine[i], out[i], 0f)
    }

    @Test
    fun `normaliser amene la crete a la cible et garde l'equilibre stereo`() {
        val l = DspTest.sine(8000, 440.0, 0.25f)
        val r = DspTest.sine(8000, 300.0, 0.10f)
        val out = Normalize(targetDb = -1f, removeDc = false).processInMemory(arrayOf(l, r), RATE)
        val target = dbToLinear(-1f)
        assertEquals(target, DspTest.peak(out[0]), 2e-3f)
        assertEquals(target * 0.10f / 0.25f, DspTest.peak(out[1]), 2e-3f)
    }

    @Test
    fun `normaliser chaque canal pour lui-meme`() {
        val out = Normalize(targetDb = -3f, removeDc = false, independent = true)
            .processInMemory(arrayOf(DspTest.sine(8000, 440.0, 0.25f), DspTest.sine(8000, 300.0, 0.1f)), RATE)
        val target = dbToLinear(-3f)
        assertEquals(target, DspTest.peak(out[0]), 2e-3f)
        assertEquals(target, DspTest.peak(out[1]), 2e-3f)
    }

    @Test
    fun `normaliser retire aussi le decalage continu`() {
        val x = FloatArray(8000) { 0.3f + DspTest.sine(8000, 440.0, 0.2f)[it] }
        val out = Normalize(targetDb = -1f, removeDc = true).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(0.0, DspTest.mean(out), 1e-3)
        assertEquals(dbToLinear(-1f), DspTest.peak(out), 2e-3f)
    }

    @Test
    fun `normaliser en RMS ne fait pas depasser la crete`() {
        // Presque silencieux avec un seul pic : viser un RMS élevé écrêterait sans le garde-fou.
        val x = FloatArray(8000) { 0.01f }.also { it[4000] = 0.9f }
        val out = Normalize(targetDb = -6f, removeDc = false, mode = Normalize.Mode.RMS, limitPeak = true).processInMemory(arrayOf(x), RATE)[0]
        assertTrue(DspTest.peak(out) <= 1.0001f)
        val loose = Normalize(targetDb = -6f, removeDc = false, mode = Normalize.Mode.RMS, limitPeak = false).processInMemory(arrayOf(x), RATE)[0]
        assertTrue(DspTest.peak(loose) > 1f)
    }

    @Test
    fun `normaliser du silence ne produit ni NaN ni bruit`() {
        val out = Normalize().processInMemory(arrayOf(FloatArray(2000)), RATE)[0]
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun `retirer le continu`() {
        val x = FloatArray(4000) { 0.4f }
        val out = RemoveDc().processInMemory(arrayOf(x), RATE)[0]
        assertEquals(0.0, DspTest.mean(out), 1e-6)
    }

    @Test
    fun `fondu d'entree lineaire`() {
        val out = FadeEffect(fadeIn = true).processInMemory(arrayOf(FloatArray(1000) { 1f }), RATE)[0]
        assertEquals(0f, out[0], 1e-6f); assertEquals(0.5f, out[500], 1e-3f); assertEquals(0.999f, out[999], 1e-3f)
    }

    @Test
    fun `fondu de sortie lineaire`() {
        val out = FadeEffect(fadeIn = false).processInMemory(arrayOf(FloatArray(1000) { 1f }), RATE)[0]
        assertEquals(1f, out[0], 1e-6f); assertEquals(0.5f, out[500], 1e-3f); assertEquals(0.001f, out[999], 1e-3f)
    }

    @Test
    fun `le fondu en S passe par un demi au milieu`() {
        val out = FadeEffect(true, FadeShape.S_CURVE).processInMemory(arrayOf(FloatArray(1000) { 1f }), RATE)[0]
        assertEquals(0.5f, out[500], 2e-3f)
    }

    @Test
    fun `stereo vers mono moyenne les voies`() {
        val l = FloatArray(100) { 0.5f }; val r = FloatArray(100) { -0.1f }
        val fx = StereoToMono()
        assertEquals(1, fx.outputChannels(2))
        val out = fx.processInMemory(arrayOf(l, r), RATE)
        assertEquals(1, out.size)
        assertEquals(0.2f, out[0][10], 1e-6f)
        // Sur du mono, rien ne change.
        assertEquals(0.5f, fx.processInMemory(arrayOf(l), RATE)[0][10], 1e-6f)
    }

    @Test
    fun `echanger les voies`() {
        val out = SwapChannels().processInMemory(arrayOf(FloatArray(10) { 1f }, FloatArray(10) { 2f }), RATE)
        assertEquals(2f, out[0][3], 0f); assertEquals(1f, out[1][3], 0f)
    }

    // ---- Le socle -----------------------------------------------------------------------

    @Test
    fun `la taille des lectures ne change pas le resultat`() {
        val fx = { Normalize(-2f) }
        val whole = fx().processInMemory(arrayOf(sine), RATE)[0]
        for (chunk in listOf(1, 77, 4096, 5000)) {
            val part = fx().processInMemory(arrayOf(sine), RATE, chunk)[0]
            assertArrayEquals("lecture par $chunk", whole, part, 1e-6f)
        }
    }

    /** Un effet qui laisse une queue de 500 trames de silence après l'entrée. */
    private class TailEffect : BlockEffect() {
        override fun tailFrames(sampleRate: Int) = 500
        override fun start(channels: Int, sampleRate: Int) {}
        override fun process(buf: Array<FloatArray>, n: Int) {}
    }

    @Test
    fun `une queue allonge la sortie de silence`() {
        val out = TailEffect().processInMemory(arrayOf(FloatArray(1000) { 1f }), RATE)[0]
        assertEquals(1500, out.size)
        assertEquals(1f, out[999], 0f); assertEquals(0f, out[1000], 0f)
    }

    /** Un effet qui retarde le signal de 100 trames et le dit : la sortie doit rester alignée. */
    private class DelayedEffect : BlockEffect() {
        private var ring = FloatArray(0)
        private var pos = 0
        override fun latencyFrames(sampleRate: Int) = 100
        override fun start(channels: Int, sampleRate: Int) { ring = FloatArray(100); pos = 0 }
        override fun process(buf: Array<FloatArray>, n: Int) {
            for (i in 0 until n) { val o = ring[pos]; ring[pos] = buf[0][i]; buf[0][i] = o; pos = (pos + 1) % 100 }
        }
    }

    @Test
    fun `la latence est compensee`() {
        val x = FloatArray(10_000) { (it % 97) / 97f }
        val out = DelayedEffect().processInMemory(arrayOf(x), RATE)[0]
        assertEquals(x.size, out.size)
        assertArrayEquals(x, out, 0f)
    }

    @Test
    fun `l'annulation interrompt le rendu`() {
        var calls = 0
        val ctx = RunContext(RATE, isCancelled = { ++calls > 2 })
        var thrown = false
        try {
            Amplify(0f).run({ MemoryReader(arrayOf(FloatArray(100_000))) }, MemoryWriter(1), ctx)
        } catch (e: CancellationException) { thrown = true }
        assertTrue(thrown)
    }

    @Test
    fun `la progression monte jusqu'a un`() {
        val seen = ArrayList<Float>()
        val ctx = RunContext(RATE, progress = { seen.add(it) })
        Amplify(0f).run({ MemoryReader(arrayOf(FloatArray(20_000))) }, MemoryWriter(1), ctx)
        assertTrue(seen.size >= 2)
        assertEquals(1f, seen.last(), 1e-6f)
        for (i in 1 until seen.size) assertTrue(seen[i] >= seen[i - 1])
        assertFalse(seen.any { it > 1f })
    }

    @Test
    fun `un lecteur vide donne une sortie vide`() {
        val reader: () -> FrameReader = { MemoryReader(arrayOf(FloatArray(0))) }
        val w = MemoryWriter(1)
        Amplify(3f).run(reader, w, RunContext(RATE))
        assertEquals(0, w.frames)
    }
}
