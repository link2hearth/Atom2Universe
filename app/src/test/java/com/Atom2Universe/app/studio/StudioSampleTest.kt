package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.export.SampleTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Le son du micro : nettoyage de l'enregistrement et lecture par la piste de percussion. */
class StudioSampleTest {

    private val rate = 48_000

    private fun tone(frames: Int, amp: Float = 0.5f, hz: Float = 440f): ShortArray =
        ShortArray(frames) { (sin(2 * Math.PI * hz * it / rate) * amp * 32767).toInt().toShort() }

    @Test
    fun silenceGivesNothing() {
        assertNull(SampleTools.clean(ShortArray(48_000)))
        assertNull(SampleTools.clean(ShortArray(48_000) { (it % 3).toShort() }))
    }

    @Test
    fun cleanTrimsNormalizesAndFades() {
        // 0,5 s de silence, 0,2 s de son doux, 1,3 s de silence
        val pcm = ShortArray(rate * 2)
        val t = tone(rate / 5, 0.1f)
        System.arraycopy(t, 0, pcm, rate / 2, t.size)
        val out = SampleTools.clean(pcm)!!
        assertTrue("le silence est coupé (${out.size})", out.size < rate / 3)
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        assertEquals(0.9f, peak, 0.05f)
        assertTrue("pas de clic au début", abs(out[0]) < 0.01f)
        assertTrue("pas de clic à la fin", abs(out[out.size - 1]) < 0.01f)
    }

    private fun renderPerc(engine: StudioEngine, frames: Int, block: Int): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun percEngine(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
        for (inst in it.allInstruments) {
            inst.reverbSend = 0f
            inst.delaySend = 0f
        }
    }

    @Test
    fun percPlaysTheRecordingAndStops() {
        val data = FloatArray(4_800) { sin(2 * Math.PI * 300 * it / rate).toFloat() * 0.5f }
        val engine = percEngine()
        engine.setPercSample(data, rate)
        engine.trigger(Inst.PERC, 60, 1f)
        val out = renderPerc(engine, 20_000, 256)
        var peak = 0f
        for (i in 0 until 4_800) peak = maxOf(peak, abs(out[i]))
        assertTrue("on entend l'enregistrement ($peak)", peak > 0.1f)
        for (i in 6_000 until 20_000) assertEquals("il s'arrête avec l'enregistrement", 0f, out[i], 1e-6f)
        assertEquals(0, engine.instrument(Inst.PERC)!!.activeVoices)
    }

    @Test
    fun percWithRecordingDoesNotDependOnBlockSize() {
        val data = FloatArray(9_000) { sin(2 * Math.PI * 500 * it / rate).toFloat() * 0.6f }
        fun run(block: Int): FloatArray {
            val e = percEngine()
            e.setPercSample(data, rate)
            e.trigger(Inst.PERC, 64, 0.8f)
            return renderPerc(e, 12_000, block)
        }
        val a = run(64)
        val b = run(1000)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-5f)
    }

    @Test
    fun noteHeightChangesTheSpeed() {
        val data = FloatArray(9_600) { 0.5f }
        val engine = percEngine()
        engine.setPercSample(data, rate)
        engine.trigger(Inst.PERC, 72, 1f) // une octave plus haut : deux fois plus vite, donc moitié moins long
        val out = renderPerc(engine, 12_000, 256)
        var last = 0
        for (i in out.indices) if (abs(out[i]) > 1e-4f) last = i
        assertTrue("durée ~4 800 ($last)", last in 4_000..5_600)
    }

    @Test
    fun removingTheRecordingBringsBackTheBell() {
        val engine = percEngine()
        engine.setPercSample(FloatArray(2_000) { 0.3f }, rate)
        engine.setPercSample(null, rate)
        engine.trigger(Inst.PERC, 60, 1f)
        val out = renderPerc(engine, 24_000, 256)
        // la cloche dure bien plus que 2 000 échantillons
        assertTrue(abs(out[10_000]) > 1e-5f || abs(out[8_000]) > 1e-5f || abs(out[6_000]) > 1e-5f)
        assertNotNull(out)
    }
}
