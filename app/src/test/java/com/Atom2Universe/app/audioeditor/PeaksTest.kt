package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.PeakBuilder
import com.Atom2Universe.app.audioeditor.core.PeakData
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Random

class PeaksTest {

    private lateinit var dir: File

    @Before fun setUp() { dir = createTempDir("peaks") }
    @After fun tearDown() { dir.deleteRecursively() }

    private val q = 1f / 127f

    private fun noise(n: Int, seed: Long = 1) = Random(seed).let { r -> FloatArray(n) { (r.nextFloat() * 2f - 1f) * 0.9f } }

    @Test
    fun `chaque bloc englobe le vrai min et le vrai max`() {
        val l = noise(5000, 1); val r = noise(5000, 2)
        val b = PeakBuilder(2, 256)
        b.add(arrayOf(l, r), 5000)
        val p = b.finish()
        assertEquals(5000L, p.frames)
        assertEquals(20, p.blocks) // 19 blocs pleins + 1 partiel
        for (blk in 0 until p.blocks) {
            val a = blk * 256; val e = minOf(a + 256, 5000)
            val trueMin = l.slice(a until e).minOrNull()!!; val trueMax = l.slice(a until e).maxOrNull()!!
            assertTrue(p.min(blk, 0) <= trueMin + 1e-6f); assertTrue(p.max(blk, 0) >= trueMax - 1e-6f)
            assertEquals(trueMin, p.min(blk, 0), q); assertEquals(trueMax, p.max(blk, 0), q)
        }
    }

    @Test
    fun `ajouter par morceaux de taille quelconque donne les memes crêtes`() {
        val x = noise(3000)
        val whole = PeakBuilder(1).also { it.add(arrayOf(x), 3000) }.finish()
        val parts = PeakBuilder(1)
        var pos = 0
        for (n in listOf(1, 255, 257, 1000, 1487)) { parts.add(arrayOf(x), n, pos); pos += n }
        val p = parts.finish()
        assertEquals(whole.blocks, p.blocks)
        for (b in 0 until p.blocks) { assertEquals(whole.min(b, 0), p.min(b, 0), 0f); assertEquals(whole.max(b, 0), p.max(b, 0), 0f) }
    }

    @Test
    fun `colonnes de dessin comparees au calcul direct`() {
        val x = noise(100_000, 7)
        val p = PeakBuilder(1).also { it.add(arrayOf(x), x.size) }.finish()
        val pixels = 100
        val mn = FloatArray(pixels); val mx = FloatArray(pixels)
        p.columns(0, 0, 1000.0, pixels, mn, mx)
        for (i in 0 until pixels) {
            val seg = x.slice(i * 1000 until (i + 1) * 1000)
            assertEquals("min $i", seg.minOrNull()!!, mn[i], 2 * q)
            assertEquals("max $i", seg.maxOrNull()!!, mx[i], 2 * q)
        }
    }

    @Test
    fun `hors de la source les colonnes sont nulles`() {
        val p = PeakBuilder(1).also { it.add(arrayOf(FloatArray(1000) { 0.5f }), 1000) }.finish()
        val mn = FloatArray(4); val mx = FloatArray(4)
        p.columns(0, -4000, 1000.0, 4, mn, mx)
        assertEquals(0f, mx[0], 0f); assertEquals(0f, mx[3], 0f)
        p.columns(0, 5000, 1000.0, 4, mn, mx)
        assertEquals(0f, mx[0], 0f)
        p.columns(0, -1000, 1000.0, 4, mn, mx)
        assertEquals(0f, mx[0], 0f); assertEquals(0.5f, mx[1], q)
    }

    @Test
    fun `colonnes depuis les echantillons bruts`() {
        val x = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 0.25f, 0f, 0f)
        val mn = FloatArray(4); val mx = FloatArray(4)
        PeakData.columnsFromSamples(x, 0, 0, 2.0, 4, mn, mx)
        assertEquals(0f, mn[0], 0f); assertEquals(0.5f, mx[0], 0f)
        assertEquals(-0.5f, mn[1], 0f); assertEquals(1f, mx[1], 0f)
        assertEquals(-1f, mn[2], 0f); assertEquals(0.25f, mx[2], 0f)
        // Un décalage de départ : l'échantillon 0 du tableau est la trame 100.
        PeakData.columnsFromSamples(x, 100, 102, 1.0, 2, mn, mx)
        assertEquals(-0.5f, mn[0], 0f); assertEquals(1f, mx[1], 0f)
    }

    @Test
    fun `un signal depassant un est ramene a plus ou moins un pour le dessin`() {
        val p = PeakBuilder(1).also { it.add(arrayOf(floatArrayOf(3f, -3f)), 2) }.finish()
        assertEquals(1f, p.max(0, 0), 0f); assertEquals(-1f, p.min(0, 0), 0f)
    }

    @Test
    fun `aller-retour dans un flux`() {
        val p = PeakBuilder(2).also { it.add(arrayOf(noise(700, 3), noise(700, 4)), 700) }.finish()
        val bytes = ByteArrayOutputStream().also { p.write(it) }.toByteArray()
        val back = PeakData.read(ByteArrayInputStream(bytes))
        assertEquals(p.frames, back.frames); assertEquals(p.channels, back.channels); assertEquals(p.blocks, back.blocks)
        for (b in 0 until p.blocks) for (c in 0..1) { assertEquals(p.min(b, c), back.min(b, c), 0f); assertEquals(p.max(b, c), back.max(b, c), 0f) }
    }

    @Test(expected = IOException::class)
    fun `un flux tronque est refuse`() {
        val p = PeakBuilder(1).also { it.add(arrayOf(noise(1000)), 1000) }.finish()
        val bytes = ByteArrayOutputStream().also { p.write(it) }.toByteArray()
        PeakData.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3)))
    }

    @Test
    fun `calcul depuis un fichier wav puis relecture du fichier de crêtes`() {
        val wav = File(dir, "s.wav")
        val x = noise(40_000, 9)
        WavWriter(wav, 1, 44100, float = true).use { it.write(arrayOf(x), x.size) }
        val peaksFile = File(dir, "s.peaks")
        val built = PeakFiles.loadOrBuild(wav, peaksFile)
        assertTrue(peaksFile.isFile)
        assertEquals(40_000L, built.frames)
        val again = PeakFiles.load(peaksFile)
        assertNotNull(again)
        assertEquals(built.max(10, 0), again!!.max(10, 0), 0f)
        // Un fichier de crêtes abîmé se recalcule au lieu de planter.
        peaksFile.writeBytes(ByteArray(10))
        assertNull(PeakFiles.load(peaksFile))
        assertEquals(built.blocks, PeakFiles.loadOrBuild(wav, peaksFile).blocks)
    }
}
