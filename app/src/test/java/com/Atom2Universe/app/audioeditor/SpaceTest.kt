package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.DspTest.RATE
import com.Atom2Universe.app.audioeditor.dsp.BitCrusher
import com.Atom2Universe.app.audioeditor.dsp.Chorus
import com.Atom2Universe.app.audioeditor.dsp.Distortion
import com.Atom2Universe.app.audioeditor.dsp.DistortionKind
import com.Atom2Universe.app.audioeditor.dsp.Echo
import com.Atom2Universe.app.audioeditor.dsp.Flanger
import com.Atom2Universe.app.audioeditor.dsp.Phaser
import com.Atom2Universe.app.audioeditor.dsp.Reverb
import com.Atom2Universe.app.audioeditor.dsp.Tremolo
import com.Atom2Universe.app.audioeditor.dsp.Vibrato
import com.Atom2Universe.app.audioeditor.dsp.dbToLinear
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceTest {

    private fun impulse(n: Int) = FloatArray(n).also { it[0] = 1f }
    private fun finite(x: FloatArray) = x.all { it.isFinite() }

    // ---- Écho ----------------------------------------------------------------------------

    @Test
    fun `l'echo repete l'impulsion au bon retard et au bon niveau`() {
        val out = Echo(delayMs = 100f, decay = 0.5f).processInMemory(arrayOf(impulse(20_000)), RATE)[0]
        val d = 4410
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(0.5f, out[d], 1e-6f)
        assertEquals(0.25f, out[2 * d], 1e-6f)
        assertEquals(0.125f, out[3 * d], 1e-6f)
        assertEquals(0f, out[d + 1], 0f)
    }

    @Test
    fun `la queue de l'echo dure jusqu'a moins soixante dB`() {
        // Une impulsion sur la dernière trame de l'entrée : toute sa descendance est dans la queue.
        val x = FloatArray(20_000).also { it[19_999] = 1f }
        val out = Echo(delayMs = 100f, decay = 0.5f).processInMemory(arrayOf(x), RATE)[0]
        // 10 répétitions pour passer sous 0,001 (0,5^10 ≈ 0,00098).
        assertEquals(20_000 + 10 * 4410, out.size)
        assertEquals(Math.pow(0.5, 10.0).toFloat(), out[19_999 + 10 * 4410], 1e-6f)
        assertTrue(Math.pow(0.5, 10.0) < 0.001)
    }

    @Test
    fun `sans retour il n'y a pas d'echo ni de queue`() {
        val x = DspTest.sine(5000, 440.0)
        val out = Echo(delayMs = 100f, decay = 0f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(5000, out.size)
        for (i in x.indices) assertEquals(x[i], out[i], 0f)
    }

    @Test
    fun `l'echo traite chaque voie separement`() {
        val out = Echo(50f, 0.5f).processInMemory(arrayOf(impulse(10_000), FloatArray(10_000)), RATE)
        assertEquals(0.5f, out[0][2205], 1e-6f)
        assertEquals(0f, DspTest.peak(out[1]), 0f)
    }

    // ---- Réverbération -------------------------------------------------------------------

    @Test
    fun `la reverberation est stable et sa queue decroit`() {
        val out = Reverb(roomSize = 0.8f, damping = 0.5f, wet = 0.5f, dry = 0f).processInMemory(arrayOf(impulse(2000)), RATE)[0]
        assertTrue(finite(out))
        assertTrue(DspTest.peak(out) < 2f)
        assertTrue("queue trop courte : ${out.size}", out.size > RATE * 2)
        val early = DspTest.rms(out, RATE / 5, RATE * 7 / 10)
        val late = DspTest.rms(out, RATE * 2, RATE * 12 / 5)
        assertTrue("early $early late $late", early > late * 2)
        assertTrue(early > 1e-4)
    }

    @Test
    fun `une piece plus grande a une queue plus longue`() {
        val small = Reverb(roomSize = 0.2f, wet = 0.5f, dry = 0f).processInMemory(arrayOf(impulse(1000)), RATE)[0].size
        val big = Reverb(roomSize = 0.9f, wet = 0.5f, dry = 0f).processInMemory(arrayOf(impulse(1000)), RATE)[0].size
        assertTrue("petite $small grande $big", big > small * 2)
    }

    @Test
    fun `sans signal humide la reverberation ne change rien`() {
        val x = DspTest.sine(6000, 500.0)
        val out = Reverb(wet = 0f, dry = 1f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(6000, out.size)
        for (i in x.indices) assertEquals(x[i], out[i], 1e-6f)
    }

    @Test
    fun `largeur zero donne le meme signal humide a gauche et a droite`() {
        val out = Reverb(roomSize = 0.6f, wet = 0.5f, dry = 0f, width = 0f).processInMemory(arrayOf(impulse(3000), FloatArray(3000)), RATE)
        for (i in 0 until 3000) assertEquals(out[0][i], out[1][i], 1e-6f)
        val wide = Reverb(roomSize = 0.6f, wet = 0.5f, dry = 0f, width = 1f).processInMemory(arrayOf(impulse(3000), FloatArray(3000)), RATE)
        assertTrue((0 until 3000).any { kotlin.math.abs(wide[0][it] - wide[1][it]) > 1e-4f })
    }

    @Test
    fun `la reverberation garde mono et stereo`() {
        assertEquals(1, Reverb().processInMemory(arrayOf(impulse(500)), RATE).size)
        assertEquals(2, Reverb().processInMemory(arrayOf(impulse(500), impulse(500)), RATE).size)
    }

    // ---- Modulations ---------------------------------------------------------------------

    @Test
    fun `chorus flanger phaser sans melange ne changent rien`() {
        val x = DspTest.sine(8000, 440.0)
        for (fx in listOf(Chorus(mix = 0f), Flanger(mix = 0f), Phaser(mix = 0f))) {
            val out = fx.processInMemory(arrayOf(x), RATE)[0]
            assertEquals(x.size, out.size)
            for (i in x.indices) assertEquals(x[i], out[i], 1e-6f)
        }
    }

    @Test
    fun `chorus flanger phaser et vibrato modifient le son sans l'emballer`() {
        val x = DspTest.sine(RATE, 440.0, 0.5f)
        val effects = listOf(
            Chorus(mix = 1f), Flanger(feedback = 0.9f, mix = 1f), Phaser(feedback = 0.8f, mix = 1f), Vibrato(),
        )
        for (fx in effects) {
            val out = fx.processInMemory(arrayOf(x), RATE)[0]
            assertEquals(x.size, out.size)
            assertTrue(finite(out))
            assertTrue("${fx::class.simpleName} crête ${DspTest.peak(out)}", DspTest.peak(out) < 2f)
            assertTrue("${fx::class.simpleName} identique", (0 until x.size).any { kotlin.math.abs(out[it] - x[it]) > 0.01f })
        }
    }

    @Test
    fun `le chorus elargit le stereo`() {
        val x = DspTest.sine(RATE, 440.0)
        val out = Chorus(mix = 1f).processInMemory(arrayOf(x, x.copyOf()), RATE)
        assertTrue((RATE / 2 until RATE).any { kotlin.math.abs(out[0][it] - out[1][it]) > 0.01f })
    }

    @Test
    fun `le tremolo module entre le silence et le plein niveau`() {
        val out = Tremolo(rateHz = 5f, depth = 1f).processInMemory(arrayOf(FloatArray(RATE) { 1f }), RATE)[0]
        assertEquals(1f, out[0], 1e-4f)
        assertTrue(out.min() < 0.01f)
        assertTrue(out.max() <= 1.0001f)
        // Profondeur nulle : rien ne bouge.
        val flat = Tremolo(rateHz = 5f, depth = 0f).processInMemory(arrayOf(FloatArray(1000) { 0.7f }), RATE)[0]
        assertEquals(0.7f, flat[500], 1e-6f)
    }

    // ---- Distorsion ----------------------------------------------------------------------

    @Test
    fun `l'ecretage net plafonne exactement`() {
        val out = Distortion(driveDb = 20f, kind = DistortionKind.HARD, outputDb = 0f).processInMemory(arrayOf(DspTest.sine(2000, 440.0, 0.8f)), RATE)[0]
        assertEquals(1f, DspTest.peak(out), 1e-6f)
        val attenuated = Distortion(driveDb = 20f, kind = DistortionKind.HARD, outputDb = -6f).processInMemory(arrayOf(DspTest.sine(2000, 440.0, 0.8f)), RATE)[0]
        assertEquals(dbToLinear(-6f), DspTest.peak(attenuated), 1e-5f)
    }

    @Test
    fun `la saturation douce est bornee et monotone`() {
        val ramp = FloatArray(2001) { (it - 1000) / 1000f }
        val out = Distortion(driveDb = 18f, kind = DistortionKind.SOFT, outputDb = 0f).processInMemory(arrayOf(ramp), RATE)[0]
        assertTrue(DspTest.peak(out) <= 1.0001f)
        for (i in 1 until out.size) assertTrue("trame $i", out[i] >= out[i - 1] - 1e-6f)
        assertEquals(0f, out[1000], 1e-6f)
    }

    @Test
    fun `la distorsion a lampe est asymetrique`() {
        val out = Distortion(driveDb = 12f, kind = DistortionKind.TUBE, outputDb = 0f).processInMemory(arrayOf(DspTest.sine(4000, 200.0, 0.9f)), RATE)[0]
        assertTrue(kotlin.math.abs(out.max() + out.min()) > 0.03f)
    }

    // ---- Bit-crusher ---------------------------------------------------------------------

    @Test
    fun `le bit-crusher reduit le nombre de niveaux`() {
        val out = BitCrusher(bits = 3).processInMemory(arrayOf(DspTest.sine(4000, 300.0, 0.95f)), RATE)[0]
        assertTrue("niveaux ${out.toSet().size}", out.toSet().size <= 8)
    }

    @Test
    fun `le sous-echantillonnage tient chaque valeur`() {
        val out = BitCrusher(bits = 16, downsample = 4).processInMemory(arrayOf(DspTest.sine(400, 300.0, 0.5f)), RATE)[0]
        for (i in 0 until 400 step 4) for (k in 1..3) assertEquals(out[i], out[i + k], 0f)
    }
}
