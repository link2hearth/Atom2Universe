package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.io.PcmReader
import com.Atom2Universe.app.audioeditor.io.PcmStream
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.SourceImporter
import com.Atom2Universe.app.audioeditor.io.WavFile
import com.Atom2Universe.app.audioeditor.io.WavPcmStream
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

class SourceImporterTest {

    private lateinit var dir: File

    @Before fun setUp() { dir = createTempDir("import") }
    @After fun tearDown() { dir.deleteRecursively() }

    /** Un flux en mémoire qui rend ses trames par morceaux de [chunk] (les décodeurs ne remplissent pas toujours le bloc demandé). */
    private class ArrayStream(
        override val channels: Int,
        override val sampleRate: Int,
        private val data: Array<FloatArray>,
        private val chunk: Int = Int.MAX_VALUE,
        override val estimatedFrames: Long = data[0].size.toLong(),
    ) : PcmStream {
        private var pos = 0
        var closed = false
        var throwAt = -1
        override fun read(dst: Array<FloatArray>, n: Int): Int {
            if (throwAt in 0..pos) throw IOException("décodage cassé")
            val m = minOf(n, chunk, data[0].size - pos)
            if (m <= 0) return 0
            for (c in 0 until channels) System.arraycopy(data[c], pos, dst[c], 0, m)
            pos += m
            return m
        }
        override fun close() { closed = true }
    }

    private fun readAll(file: File): Array<FloatArray> = PcmReader(file).use { r ->
        val out = Array(r.info.channels) { FloatArray(r.info.frames.toInt()) }
        r.read(0, r.info.frames.toInt(), out, 0)
        out
    }

    @Test
    fun `le mono a la bonne frequence est copie a la quantification pres`() {
        val x = DspTest.sine(10_000, 440.0, 0.5f)
        val s = SourceImporter.import(ArrayStream(1, 44100, arrayOf(x)), dir, 44100, RunContext(44100)) { "m1" }
        assertEquals("m1", s.id); assertEquals("sources/m1.wav", s.file)
        assertEquals(1, s.channels); assertEquals(10_000L, s.frames); assertEquals(44100, s.sampleRate); assertFalse(s.float)
        val info = WavFile.readInfo(File(dir, s.file))
        assertEquals(16, info.bitsPerSample); assertEquals(10_000L, info.frames)
        val y = readAll(File(dir, s.file))[0]
        for (i in x.indices) assertEquals(x[i], y[i], 1f / 32768)
        // Les crêtes sont là et décrivent la même longueur.
        val peaks = PeakFiles.load(File(dir, "sources/m1.peaks"))
        assertNotNull(peaks)
        assertEquals(10_000L, peaks!!.frames)
    }

    @Test
    fun `la taille des morceaux rendus par le decodeur ne change pas le resultat`() {
        val x = DspTest.sine(50_000, 330.0, 0.4f)
        val a = SourceImporter.import(ArrayStream(1, 44100, arrayOf(x)), dir, 44100, RunContext(44100)) { "a" }
        val b = SourceImporter.import(ArrayStream(1, 44100, arrayOf(x), chunk = 997), dir, 44100, RunContext(44100)) { "b" }
        assertEquals(a.frames, b.frames)
        assertEquals(File(dir, a.file).readBytes().toList(), File(dir, b.file).readBytes().toList())
    }

    @Test
    fun `48 kHz devient 44,1 kHz en gardant la hauteur et la duree`() {
        val n = 96_000
        val x = DspTest.sine(n, 1000.0, 0.5f, 48000)
        val s = SourceImporter.import(ArrayStream(1, 48000, arrayOf(x), chunk = 5000), dir, 44100, RunContext(44100)) { "r" }
        assertEquals(44100, s.sampleRate)
        val expected = n * 44100L / 48000
        assertTrue("trames : ${s.frames} / $expected", kotlin.math.abs(s.frames - expected) <= 2)
        val y = readAll(File(dir, s.file))[0]
        val f = DspTest.dominantFreq(y, 8192, 16384, 44100)
        assertEquals(1000.0, f, 5.0)
        assertEquals(0.5, DspTest.peak(y, 4000, y.size - 4000).toDouble(), 0.02)
        assertEquals(s.frames, WavFile.readInfo(File(dir, s.file)).frames)
        assertEquals(s.frames, PeakFiles.load(File(dir, "sources/r.peaks"))!!.frames)
    }

    @Test
    fun `la stereo reste stereo`() {
        val l = DspTest.sine(2000, 440.0, 0.5f)
        val r = DspTest.sine(2000, 880.0, 0.25f)
        val s = SourceImporter.import(ArrayStream(2, 44100, arrayOf(l, r)), dir, 44100, RunContext(44100)) { "st" }
        assertEquals(2, s.channels)
        val y = readAll(File(dir, s.file))
        for (i in 0 until 2000) { assertEquals(l[i], y[0][i], 1f / 32768); assertEquals(r[i], y[1][i], 1f / 32768) }
    }

    @Test
    fun `un fichier 5,1 devient stereo, pair a gauche et impair a droite`() {
        val n = 1000
        val vals = floatArrayOf(0.6f, 0.2f, 0.3f, 0.1f, 0.0f, 0.4f)
        val data = Array(6) { c -> FloatArray(n) { vals[c] } }
        val s = SourceImporter.import(ArrayStream(6, 44100, data), dir, 44100, RunContext(44100)) { "mc" }
        assertEquals(2, s.channels)
        val y = readAll(File(dir, s.file))
        assertEquals((0.6f + 0.3f + 0.0f) / 3f, y[0][500], 1f / 16384)
        assertEquals((0.2f + 0.1f + 0.4f) / 3f, y[1][500], 1f / 16384)
    }

    @Test
    fun `un flux vide est refuse et ne laisse aucun fichier`() {
        try {
            SourceImporter.import(ArrayStream(1, 44100, arrayOf(FloatArray(0))), dir, 44100, RunContext(44100)) { "vide" }
            fail("aurait dû lever IOException")
        } catch (e: IOException) {
            // attendu
        }
        assertEquals(emptyList<String>(), File(dir, "sources").list()!!.toList())
    }

    @Test
    fun `une erreur de decodage efface le fichier en cours`() {
        val st = ArrayStream(1, 44100, arrayOf(DspTest.sine(100_000, 440.0)), chunk = 10_000).also { it.throwAt = 30_000 }
        try {
            SourceImporter.import(st, dir, 44100, RunContext(44100)) { "e" }
            fail("aurait dû lever IOException")
        } catch (e: IOException) {
            assertEquals("décodage cassé", e.message)
        }
        assertEquals(emptyList<String>(), File(dir, "sources").list()!!.toList())
    }

    @Test
    fun `l'annulation efface le fichier et la progression monte jusqu'a un`() {
        var cancel = false
        var last = 0f
        val ctx = RunContext(44100, progress = { last = it; if (it > 0.3f) cancel = true }, isCancelled = { cancel })
        try {
            SourceImporter.import(ArrayStream(1, 44100, arrayOf(DspTest.sine(200_000, 440.0)), chunk = 8000), dir, 44100, ctx) { "c" }
            fail("aurait dû être annulé")
        } catch (e: CancellationException) {
            assertTrue(last in 0.3f..0.6f)
        }
        assertEquals(emptyList<String>(), File(dir, "sources").list()!!.toList())

        var done = 0f
        SourceImporter.import(ArrayStream(1, 44100, arrayOf(DspTest.sine(50_000, 440.0))), dir, 44100, RunContext(44100, progress = { done = it })) { "ok" }
        assertEquals(1f, done)
    }

    @Test
    fun `un fichier WAV se relit comme un flux`() {
        val f = File(dir, "in.wav")
        val l = DspTest.sine(3000, 440.0, 0.5f); val r = DspTest.sine(3000, 660.0, 0.3f)
        WavWriter(f, 2, 22050, float = false).use { it.write(arrayOf(l, r), 3000) }
        WavPcmStream(f).use { st ->
            assertEquals(2, st.channels); assertEquals(22050, st.sampleRate); assertEquals(3000L, st.estimatedFrames)
            val buf = Array(2) { FloatArray(2000) }
            assertEquals(2000, st.read(buf, 2000))
            assertEquals(1000, st.read(buf, 2000))
            assertEquals(0, st.read(buf, 2000))
        }
        // Et passe tel quel dans l'import, rééchantillonné.
        val s = WavPcmStream(f).use { SourceImporter.import(it, dir, 44100, RunContext(44100)) { "w" } }
        assertEquals(44100, s.sampleRate)
        assertTrue(kotlin.math.abs(s.frames - 6000) <= 2)
    }
}
