package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Resampler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class ResamplerTest {

    private fun tone(n: Int, freq: Double, rate: Int, amp: Float = 0.8f) =
        FloatArray(n) { (amp * sin(2 * PI * freq * it / rate)).toFloat() }

    /** Écart maximal entre la sortie et le sinus idéal à la fréquence de sortie, hors des 150 premières / dernières trames (les bords). */
    private fun maxError(out: FloatArray, freq: Double, rate: Int, amp: Float = 0.8f): Float {
        var worst = 0f
        for (m in 150 until out.size - 150) {
            val ideal = (amp * sin(2 * PI * freq * m / rate)).toFloat()
            worst = maxOf(worst, abs(out[m] - ideal))
        }
        return worst
    }

    @Test
    fun `44100 vers 48000 garde la frequence et la phase`() {
        val out = Resampler.resampleAll(arrayOf(tone(20_000, 1000.0, 44100)), 44100, 48000)[0]
        assertEquals(21768.0, out.size.toDouble(), 2.0)
        assertTrue("erreur ${maxError(out, 1000.0, 48000)}", maxError(out, 1000.0, 48000) < 2e-3f)
    }

    @Test
    fun `48000 vers 44100`() {
        val out = Resampler.resampleAll(arrayOf(tone(30_000, 3000.0, 48000)), 48000, 44100)[0]
        assertEquals(27562.5, out.size.toDouble(), 2.0)
        assertTrue("erreur ${maxError(out, 3000.0, 44100)}", maxError(out, 3000.0, 44100) < 3e-3f)
    }

    @Test
    fun `doubler la frequence`() {
        val out = Resampler.resampleAll(arrayOf(tone(10_000, 440.0, 22050)), 22050, 44100)[0]
        assertEquals(20000.0, out.size.toDouble(), 2.0)
        assertTrue(maxError(out, 440.0, 44100) < 2e-3f)
    }

    @Test
    fun `un son au dela de la nouvelle limite est coupe au lieu de se replier`() {
        // 15 kHz échantillonné à 48 kHz, ramené à 22,05 kHz : au-dessus de Nyquist (11 kHz), il doit disparaître.
        val out = Resampler.resampleAll(arrayOf(tone(30_000, 15_000.0, 48000, 1f)), 48000, 22050)[0]
        var sum = 0.0
        for (m in 200 until out.size - 200) sum += out[m] * out[m]
        val rms = sqrt(sum / (out.size - 400))
        assertTrue("rms $rms", rms < 0.01)
    }

    @Test
    fun `un son dans la bande passe presque sans perte`() {
        val out = Resampler.resampleAll(arrayOf(tone(30_000, 5000.0, 48000, 1f)), 48000, 22050)[0]
        assertTrue(maxError(out, 5000.0, 22050, 1f) < 6e-3f)
    }

    @Test
    fun `le continu reste a la meme valeur`() {
        val out = Resampler.resampleAll(arrayOf(FloatArray(5000) { 0.5f }), 44100, 48000)[0]
        for (m in 100 until out.size - 100) assertEquals(0.5f, out[m], 1e-4f)
    }

    @Test
    fun `memes frequences copie sans changement`() {
        val x = tone(100, 440.0, 44100)
        assertArrayEquals(x, Resampler.resampleAll(arrayOf(x), 44100, 44100)[0], 0f)
    }

    @Test
    fun `le stereo traite chaque voie separement`() {
        val out = Resampler.resampleAll(arrayOf(tone(10_000, 500.0, 44100), tone(10_000, 2000.0, 44100)), 44100, 48000)
        assertTrue(maxError(out[0], 500.0, 48000) < 2e-3f)
        assertTrue(maxError(out[1], 2000.0, 48000) < 2e-3f)
    }

    @Test
    fun `traiter par blocs de toute taille donne exactement la meme sortie`() {
        val x = tone(9_000, 1234.0, 44100)
        val reference = Resampler.resampleAll(arrayOf(x), 44100, 48000)[0]
        for (block in listOf(1, 7, 500, 4096)) {
            val r = Resampler(44100, 48000, 1)
            val acc = ArrayList<Float>()
            var pos = 0
            while (pos < x.size) {
                val n = minOf(block, x.size - pos)
                val chunk = arrayOf(x.copyOfRange(pos, pos + n))
                val out = arrayOf(FloatArray(r.maxOutputFor(n)))
                val m = r.process(chunk, n, out)
                for (i in 0 until m) acc.add(out[0][i])
                pos += n
            }
            val tail = arrayOf(FloatArray(r.maxOutputFor(0) + 4))
            val m = r.finish(tail)
            for (i in 0 until m) acc.add(tail[0][i])
            assertEquals("bloc $block", reference.size, acc.size)
            for (i in reference.indices) assertEquals("bloc $block trame $i", reference[i], acc[i], 1e-6f)
        }
    }
}
