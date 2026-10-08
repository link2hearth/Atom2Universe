package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.DspTest.RATE
import com.Atom2Universe.app.audioeditor.dsp.BassTreble
import com.Atom2Universe.app.audioeditor.dsp.Compressor
import com.Atom2Universe.app.audioeditor.dsp.FilterEffect
import com.Atom2Universe.app.audioeditor.dsp.GraphicEq
import com.Atom2Universe.app.audioeditor.dsp.Limiter
import com.Atom2Universe.app.audioeditor.dsp.NoiseGate
import com.Atom2Universe.app.audioeditor.dsp.dbToLinear
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class FiltersDynamicsTest {

    // ---- Filtres -------------------------------------------------------------------------

    @Test
    fun `passe-bas a 12 dB par octave`() {
        val fx = { FilterEffect(FilterEffect.Kind.LOW_PASS, 1000f, slopeDb = 12) }
        assertEquals(0.0, DspTest.gainDb(fx(), 100.0), 0.3)
        assertEquals(-3.0, DspTest.gainDb(fx(), 1000.0), 0.4)           // la coupure est à −3 dB
        assertTrue(DspTest.gainDb(fx(), 8000.0) < -30.0)                // trois octaves plus haut
    }

    @Test
    fun `une pente plus raide coupe plus fort`() {
        val gentle = DspTest.gainDb(FilterEffect(FilterEffect.Kind.LOW_PASS, 1000f, slopeDb = 12), 2000.0)
        val steep = DspTest.gainDb(FilterEffect(FilterEffect.Kind.LOW_PASS, 1000f, slopeDb = 48), 2000.0)
        assertTrue("12 dB: $gentle, 48 dB: $steep", steep < gentle - 20.0)
        assertEquals(0.0, DspTest.gainDb(FilterEffect(FilterEffect.Kind.LOW_PASS, 1000f, slopeDb = 48), 300.0), 0.3)
    }

    @Test
    fun `passe-haut`() {
        val fx = { FilterEffect(FilterEffect.Kind.HIGH_PASS, 1000f, slopeDb = 24) }
        assertEquals(0.0, DspTest.gainDb(fx(), 8000.0), 0.3)
        assertEquals(-3.0, DspTest.gainDb(fx(), 1000.0), 0.5)
        assertTrue(DspTest.gainDb(fx(), 100.0) < -40.0)
    }

    @Test
    fun `coupe-bande elimine la frequence visee`() {
        val fx = { FilterEffect(FilterEffect.Kind.NOTCH, 1000f, q = 10f) }
        assertTrue(DspTest.gainDb(fx(), 1000.0) < -40.0)
        assertEquals(0.0, DspTest.gainDb(fx(), 100.0), 0.2)
        assertEquals(0.0, DspTest.gainDb(fx(), 5000.0), 0.2)
    }

    @Test
    fun `passe-bande laisse passer son centre`() {
        val fx = { FilterEffect(FilterEffect.Kind.BAND_PASS, 1000f, q = 2f) }
        assertEquals(0.0, DspTest.gainDb(fx(), 1000.0), 0.3)
        assertTrue(DspTest.gainDb(fx(), 100.0) < -15.0)
        assertTrue(DspTest.gainDb(fx(), 10000.0) < -15.0)
    }

    @Test
    fun `les facteurs de qualite de Butterworth`() {
        val q2 = FilterEffect.butterworthQs(2)
        assertEquals(listOf(0.7071), q2.map { Math.round(it * 10000) / 10000.0 })
        val q4 = FilterEffect.butterworthQs(4)
        assertEquals(0.5412, q4[0], 1e-4); assertEquals(1.3066, q4[1], 1e-4)
    }

    @Test
    fun `chaque voie a son propre etat de filtre`() {
        val fx = FilterEffect(FilterEffect.Kind.LOW_PASS, 500f)
        val out = fx.processInMemory(arrayOf(DspTest.sine(8000, 200.0), FloatArray(8000)), RATE)
        assertTrue(DspTest.peak(out[0]) > 0.1f)
        assertEquals(0f, DspTest.peak(out[1]), 0f)
    }

    // ---- Égalisation ---------------------------------------------------------------------

    @Test
    fun `une bande de l'egaliseur graphique est reglee a plus ou moins son gain`() {
        val gains = FloatArray(10).also { it[5] = 12f } // 1 kHz
        assertEquals(12.0, DspTest.gainDb(GraphicEq(gains), 1000.0), 0.5)
        assertEquals(0.0, DspTest.gainDb(GraphicEq(gains), 80.0), 1.0)
        assertEquals(0.0, DspTest.gainDb(GraphicEq(gains), 12000.0), 1.0)
        val cut = FloatArray(10).also { it[5] = -9f }
        assertEquals(-9.0, DspTest.gainDb(GraphicEq(cut), 1000.0), 0.5)
    }

    @Test
    fun `un egaliseur a plat ne change rien`() {
        val x = DspTest.sine(4000, 700.0)
        val out = GraphicEq(FloatArray(10)).processInMemory(arrayOf(x), RATE)[0]
        for (i in x.indices) assertEquals(x[i], out[i], 0f)
    }

    @Test
    fun `graves et aigus`() {
        assertEquals(10.0, DspTest.gainDb(BassTreble(10f, 0f), 40.0), 1.2)
        assertEquals(0.0, DspTest.gainDb(BassTreble(10f, 0f), 8000.0), 0.5)
        assertEquals(10.0, DspTest.gainDb(BassTreble(0f, 10f), 14000.0), 1.2)
        assertEquals(0.0, DspTest.gainDb(BassTreble(0f, 10f), 100.0), 0.5)
    }

    // ---- Dynamique -----------------------------------------------------------------------

    @Test
    fun `le compresseur ramene un son fort selon le ratio`() {
        // −8 dBFS en entrée, seuil −20, ratio 4 : 12 dB au-dessus → 3 dB au-dessus en sortie = −17 dBFS.
        val fx = Compressor(thresholdDb = -20f, ratio = 4f, attackMs = 1f, releaseMs = 50f, kneeDb = 0f)
        val amp = dbToLinear(-8f)
        val out = fx.processInMemory(arrayOf(DspTest.sine(RATE, 440.0, amp)), RATE)[0]
        val outDb = DspTest.db(DspTest.peak(out, RATE / 2, RATE).toDouble())
        assertEquals(-17.0, outDb, 0.8)
    }

    @Test
    fun `sous le seuil le compresseur ne touche a rien`() {
        val fx = Compressor(thresholdDb = -20f, ratio = 4f, attackMs = 1f, releaseMs = 50f, kneeDb = 0f)
        assertEquals(0.0, DspTest.gainDb(fx, 440.0, amp = dbToLinear(-40f)), 0.2)
    }

    @Test
    fun `le gain de compensation s'ajoute`() {
        val fx = Compressor(thresholdDb = 0f, ratio = 2f, makeupDb = 6f, kneeDb = 0f)
        assertEquals(6.0, DspTest.gainDb(fx, 440.0, amp = dbToLinear(-30f)), 0.3)
    }

    @Test
    fun `la courbe du compresseur est continue au coude`() {
        val c = Compressor(thresholdDb = -20f, ratio = 4f, kneeDb = 6f)
        assertEquals(0f, c.gainReductionDb(-40f), 1e-6f)
        var prev = c.gainReductionDb(-30f)
        var lv = -30f
        while (lv < 0f) {
            lv += 0.25f
            val g = c.gainReductionDb(lv)
            assertTrue("rupture à $lv", kotlin.math.abs(g - prev) < 0.6f)
            assertTrue(g <= 1e-6f)
            prev = g
        }
    }

    @Test
    fun `le limiteur ne laisse rien depasser le plafond`() {
        val ceiling = dbToLinear(-3f)
        val out = Limiter(ceilingDb = -3f, releaseMs = 50f).processInMemory(arrayOf(DspTest.sine(RATE, 220.0, 2.0f)), RATE)[0]
        assertEquals(RATE, out.size)
        assertTrue("crête ${DspTest.peak(out)}", DspTest.peak(out) <= ceiling + 1e-3f)
        // Il reste du signal : le limiteur n'a pas simplement tout éteint.
        assertTrue(DspTest.peak(out, RATE / 2, RATE) > ceiling * 0.8f)
    }

    @Test
    fun `une impulsion isolee est plafonnee sans trainer`() {
        val x = FloatArray(10_000).also { it[5000] = 4f }
        val out = Limiter(ceilingDb = -1f, releaseMs = 20f).processInMemory(arrayOf(x), RATE)[0]
        assertTrue(DspTest.peak(out) <= dbToLinear(-1f) + 1e-3f)
        assertTrue(DspTest.peak(out) > 0.5f)
    }

    @Test
    fun `sous le plafond le limiteur laisse le signal intact et aligne`() {
        val x = DspTest.sine(20_000, 330.0, 0.3f)
        val out = Limiter(ceilingDb = -1f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(x.size, out.size)
        for (i in x.indices) assertEquals("trame $i", x[i], out[i], 1e-6f)
    }

    @Test
    fun `la porte coupe le bruit de fond et laisse passer le signal`() {
        val rnd = Random(3)
        val n = RATE
        val x = FloatArray(n) { i ->
            if (i < n / 2) (rnd.nextFloat() - 0.5f) * 0.002f else DspTest.sine(n, 440.0, 0.5f)[i]
        }
        val out = NoiseGate(thresholdDb = -40f, reductionDb = -80f, attackMs = 2f, holdMs = 20f, releaseMs = 30f).processInMemory(arrayOf(x), RATE)[0]
        // Après le temps de fermeture, le bruit est écrasé.
        assertTrue("bruit ${DspTest.rms(out, 4000, 20000)}", DspTest.rms(out, 4000, 20000) < DspTest.rms(x, 4000, 20000) * 0.05)
        // Le signal franc passe presque intact une fois la porte ouverte.
        assertEquals(1.0, DspTest.rms(out, n * 3 / 4, n) / DspTest.rms(x, n * 3 / 4, n), 0.03)
    }
}
