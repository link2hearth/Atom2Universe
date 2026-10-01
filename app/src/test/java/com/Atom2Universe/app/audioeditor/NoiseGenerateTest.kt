package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.DspTest.RATE
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.dsp.ClickTrackReader
import com.Atom2Universe.app.audioeditor.dsp.Fft
import com.Atom2Universe.app.audioeditor.dsp.FrameReader
import com.Atom2Universe.app.audioeditor.dsp.GenerateSource
import com.Atom2Universe.app.audioeditor.dsp.MemoryReader
import com.Atom2Universe.app.audioeditor.dsp.NoiseKind
import com.Atom2Universe.app.audioeditor.dsp.NoiseProfile
import com.Atom2Universe.app.audioeditor.dsp.NoiseReader
import com.Atom2Universe.app.audioeditor.dsp.NoiseReduction
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.ToneReader
import com.Atom2Universe.app.audioeditor.dsp.ToneShape
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Random

class NoiseGenerateTest {

    private lateinit var dir: File

    @Before fun setUp() { dir = createTempDir("gen") }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun noise(n: Int, amp: Float, seed: Long) = Random(seed).let { r -> FloatArray(n) { (r.nextFloat() - 0.5f) * 2f * amp } }

    private fun readAll(r: FrameReader): FloatArray {
        val out = FloatArray(r.frames.toInt())
        val buf = arrayOf(FloatArray(777))
        var pos = 0
        while (true) {
            val n = r.read(buf, 777)
            if (n <= 0) break
            System.arraycopy(buf[0], 0, out, pos, n)
            pos += n
        }
        assertEquals(out.size, pos)
        return out
    }

    // ---- Réduction de bruit --------------------------------------------------------------

    @Test
    fun `le profil exige au moins une fenetre`() {
        assertNull(NoiseProfile.fromSamples(FloatArray(100)))
        assertNotNull(NoiseProfile.fromSamples(FloatArray(NoiseProfile.FFT_SIZE * 2)))
    }

    @Test
    fun `un bruit seul est fortement attenue`() {
        val profile = NoiseProfile.fromSamples(noise(RATE * 2, 0.05f, 1))!!
        val x = noise(RATE * 3, 0.05f, 2)
        val out = NoiseReduction(profile, reductionDb = 30f, sensitivity = 6f).processInMemory(arrayOf(x), RATE)[0]
        assertEquals(x.size, out.size)
        val before = DspTest.rms(x, RATE, RATE * 3)
        val after = DspTest.rms(out, RATE, RATE * 3)
        val dropDb = DspTest.db(before / after)
        assertTrue("réduction ${"%.1f".format(dropDb)} dB", dropDb > 12.0)
    }

    @Test
    fun `un signal au-dessus du bruit est conserve`() {
        val profile = NoiseProfile.fromSamples(noise(RATE * 2, 0.02f, 3))!!
        val tone = DspTest.sine(RATE * 3, 1000.0, 0.3f)
        val n = noise(RATE * 3, 0.02f, 4)
        val x = FloatArray(tone.size) { tone[it] + n[it] }
        val out = NoiseReduction(profile, reductionDb = 24f, sensitivity = 6f).processInMemory(arrayOf(x), RATE)[0]
        // L'amplitude du 1 kHz est restée à moins de 1 dB près.
        val bin = { sig: FloatArray ->
            val size = 16384
            val fft = Fft(size)
            val re = DoubleArray(size) { sig[RATE + it] * (0.5 - 0.5 * Math.cos(2 * Math.PI * it / size)) }
            val im = DoubleArray(size)
            fft.transform(re, im)
            val b = Math.round(1000.0 * size / RATE).toInt()
            Math.hypot(re[b], im[b])
        }
        assertEquals(0.0, DspTest.db(bin(out) / bin(x)), 1.0)
        // Et le souffle autour a baissé : on compare le résidu hors 1 kHz.
        val residualBefore = DspTest.rms(FloatArray(x.size) { x[it] - tone[it] }, RATE, RATE * 3)
        val residualAfter = DspTest.rms(FloatArray(x.size) { out[it] - tone[it] }, RATE, RATE * 3)
        assertTrue("avant $residualBefore après $residualAfter", residualAfter < residualBefore * 0.5)
    }

    @Test
    fun `la reduction de bruit garde la duree et gere deux voies`() {
        val profile = NoiseProfile.fromSamples(noise(RATE, 0.05f, 6))!!
        val a = noise(RATE, 0.05f, 7); val b = noise(RATE, 0.05f, 8)
        val out = NoiseReduction(profile).processInMemory(arrayOf(a, b), RATE)
        assertEquals(2, out.size)
        assertEquals(RATE, out[0].size); assertEquals(RATE, out[1].size)
    }

    @Test
    fun `le profil se mesure aussi depuis un lecteur`() {
        val p = NoiseProfile.fromReader(MemoryReader(arrayOf(noise(RATE, 0.1f, 9), noise(RATE, 0.1f, 10))))
        assertNotNull(p)
    }

    // ---- Générateurs ---------------------------------------------------------------------

    @Test
    fun `une tonalite sinus a la bonne frequence et la bonne amplitude`() {
        val x = readAll(ToneReader(ToneShape.SINE, 1000.0, 0.5f, RATE.toLong(), RATE))
        assertEquals(RATE, x.size)
        assertEquals(0.5f, DspTest.peak(x), 1e-3f)
        assertEquals(1000.0, DspTest.zeroCrossFreq(x), 15.0)
        assertEquals(0f, x[0], 1e-6f) // le fondu de départ évite le claquement
    }

    @Test
    fun `carre scie et triangle ont la bonne periode`() {
        for (shape in listOf(ToneShape.SQUARE, ToneShape.SAW, ToneShape.TRIANGLE)) {
            val x = readAll(ToneReader(shape, 500.0, 0.8f, RATE.toLong(), RATE))
            assertEquals("$shape", 500.0, DspTest.zeroCrossFreq(x), 12.0)
            assertTrue("$shape crête ${DspTest.peak(x)}", DspTest.peak(x) in 0.7f..0.95f)
        }
    }

    @Test
    fun `le bruit blanc est borne et sans continu`() {
        val x = readAll(NoiseReader(NoiseKind.WHITE, 0.5f, RATE.toLong(), seed = 3))
        assertTrue(DspTest.peak(x) <= 0.5f)
        assertEquals(0.0, DspTest.mean(x), 0.01)
        assertEquals(0.5 / Math.sqrt(3.0), DspTest.rms(x), 0.01) // écart-type d'une loi uniforme
    }

    @Test
    fun `le bruit rose et le bruit brun penchent vers les graves`() {
        fun lowOverHigh(kind: NoiseKind): Double {
            val x = readAll(NoiseReader(kind, 0.5f, RATE.toLong() * 2, seed = 4))
            val size = 8192
            val fft = Fft(size)
            var low = 0.0; var high = 0.0
            for (seg in 0 until 8) {
                val re = DoubleArray(size) { x[seg * size + it] * (0.5 - 0.5 * Math.cos(2 * Math.PI * it / size)) }
                val im = DoubleArray(size)
                fft.transform(re, im)
                for (b in 10 until 90) low += re[b] * re[b] + im[b] * im[b]        // ~50–480 Hz
                for (b in 1500 until 1580) high += re[b] * re[b] + im[b] * im[b]   // ~8–8,5 kHz
            }
            return low / high
        }
        val white = lowOverHigh(NoiseKind.WHITE)
        val pink = lowOverHigh(NoiseKind.PINK)
        val brown = lowOverHigh(NoiseKind.BROWN)
        assertTrue("blanc $white rose $pink", pink > white * 5)
        assertTrue("rose $pink brun $brown", brown > pink * 5)
    }

    @Test
    fun `le bruit est reproductible avec la meme graine`() {
        val a = readAll(NoiseReader(NoiseKind.PINK, 0.3f, 5000, seed = 7))
        val b = readAll(NoiseReader(NoiseKind.PINK, 0.3f, 5000, seed = 7))
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `le metronome clique a chaque temps avec un accent`() {
        val bpm = 120f // un temps toutes les 22 050 trames
        val x = readAll(ClickTrackReader(bpm, 4, 0.8f, RATE.toLong() * 4, RATE))
        val beat = RATE / 2
        for (b in 0 until 8) {
            val peak = DspTest.peak(x, b * beat, b * beat + 2000)
            assertTrue("temps $b crête $peak", peak > 0.3f)
        }
        // Entre deux clics, le silence.
        assertEquals(0f, DspTest.peak(x, beat / 2, beat - 100), 0f)
        // Le premier temps de chaque mesure est plus aigu (plus de passages par zéro dans le clic).
        fun crossings(from: Int) = (from + 1 until from + 1500).count { x[it - 1] < 0f && x[it] >= 0f }
        assertTrue(crossings(0) > crossings(beat))
        assertTrue(crossings(4 * beat) > crossings(3 * beat))
    }

    @Test
    fun `generer pose un clip sur une nouvelle piste`() {
        val reader = ToneReader(ToneShape.SINE, 440.0, 0.5f, 22050, RATE)
        val res = GenerateSource.apply(Project(), dir, reader, trackId = null, at = 1000, name = "Tonalité", trackName = "Piste", ctx = RunContext(RATE))
        val p = res.project
        assertEquals(1, p.tracks.size)
        val clip = p.tracks[0].clips.single()
        assertEquals(1000L, clip.start); assertEquals(22050L, clip.length)
        assertTrue(File(dir, res.newSources.single().file).isFile)
        FileSampleProvider(dir).use { prov ->
            val l = FloatArray(1000); val r = FloatArray(1000)
            Mixer(prov).render(p, 1000, 1000, l, r)
            assertEquals(0.5f, DspTest.peak(l), 0.02f)
        }
    }

    @Test
    fun `generer sur une piste existante`() {
        val first = GenerateSource.apply(Project(), dir, ToneReader(ToneShape.SINE, 440.0, 0.5f, 1000, RATE), null, 0, "a", "Piste", RunContext(RATE))
        val tid = first.project.tracks[0].id
        val second = GenerateSource.apply(first.project, dir, NoiseReader(NoiseKind.WHITE, 0.2f, 500, 1), tid, 2000, "b", "x", RunContext(RATE))
        assertEquals(1, second.project.tracks.size)
        assertEquals(2, second.project.tracks[0].clips.size)
    }
}
