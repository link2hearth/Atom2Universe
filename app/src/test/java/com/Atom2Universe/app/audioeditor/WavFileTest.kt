package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.PcmReader
import com.Atom2Universe.app.audioeditor.io.WavFile
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileTest {

    private lateinit var dir: File

    @Before fun setUp() { dir = createTempDir("wavtest") }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun sine(n: Int, freq: Double = 440.0, rate: Int = 44100) = FloatArray(n) { Math.sin(2 * Math.PI * freq * it / rate).toFloat() * 0.8f }

    @Test
    fun `aller-retour en 16 bits`() {
        val f = File(dir, "a.wav")
        val l = sine(1000); val r = sine(1000, 880.0)
        WavWriter(f, 2, 44100, float = false).use { it.write(arrayOf(l, r), 1000) }
        val info = WavFile.readInfo(f)
        assertEquals(2, info.channels); assertEquals(44100, info.sampleRate); assertEquals(1000L, info.frames)
        assertFalse(info.float); assertEquals(16, info.bitsPerSample)
        val out = arrayOf(FloatArray(1000), FloatArray(1000))
        PcmReader(f).use { it.read(0, 1000, out, 0) }
        for (i in 0 until 1000) { assertEquals(l[i], out[0][i], 1f / 32768); assertEquals(r[i], out[1][i], 1f / 32768) }
    }

    @Test
    fun `aller-retour en flottant garde les valeurs hors de plus ou moins un`() {
        val f = File(dir, "f.wav")
        val x = FloatArray(10) { (it - 5) * 0.7f }
        WavWriter(f, 1, 48000, float = true).use { it.write(arrayOf(x), 10) }
        val info = WavFile.readInfo(f)
        assertTrue(info.float); assertEquals(32, info.bitsPerSample); assertEquals(10L, info.frames); assertEquals(48000, info.sampleRate)
        val out = arrayOf(FloatArray(10))
        PcmReader(f).use { it.read(0, 10, out, 0) }
        for (i in 0 until 10) assertEquals(x[i], out[0][i], 0f)
    }

    @Test
    fun `le 16 bits ecrete au lieu de deborder`() {
        val f = File(dir, "c.wav")
        WavWriter(f, 1, 44100, float = false).use { it.write(arrayOf(floatArrayOf(2f, -2f)), 2) }
        val out = arrayOf(FloatArray(2))
        PcmReader(f).use { it.read(0, 2, out, 0) }
        assertEquals(32767 / 32768f, out[0][0], 1e-6f); assertEquals(-1f, out[0][1], 1e-6f)
    }

    @Test
    fun `l'ecriture en plusieurs blocs donne un seul fichier continu`() {
        val f = File(dir, "b.wav")
        val x = sine(1000)
        WavWriter(f, 1, 44100, float = false).use { w ->
            w.write(arrayOf(x.copyOfRange(0, 300)), 300)
            w.write(arrayOf(x.copyOfRange(300, 1000)), 700)
            assertEquals(1000L, w.frames)
        }
        val out = arrayOf(FloatArray(1000))
        PcmReader(f).use { it.read(0, 1000, out, 0) }
        assertEquals(x[650], out[0][650], 1f / 32768)
    }

    @Test
    fun `lire hors des bornes donne du silence`() {
        val f = File(dir, "o.wav")
        WavWriter(f, 1, 44100, float = true).use { it.write(arrayOf(FloatArray(10) { 1f }), 10) }
        val out = arrayOf(FloatArray(30) { 9f })
        PcmReader(f).use { it.read(-10, 30, out, 0) }
        for (i in 0 until 10) assertEquals(0f, out[0][i], 0f)
        for (i in 10 until 20) assertEquals(1f, out[0][i], 0f)
        for (i in 20 until 30) assertEquals(0f, out[0][i], 0f)
        // Entièrement après la fin.
        val far = arrayOf(FloatArray(5) { 9f })
        PcmReader(f).use { it.read(100, 5, far, 0) }
        assertEquals(0f, far[0][4], 0f)
    }

    @Test
    fun `lecture avec decalage dans le tampon`() {
        val f = File(dir, "d.wav")
        WavWriter(f, 1, 44100, float = true).use { it.write(arrayOf(FloatArray(10) { it.toFloat() }), 10) }
        val out = arrayOf(FloatArray(8) { -1f })
        PcmReader(f).use { it.read(3, 4, out, 2) }
        assertEquals(-1f, out[0][1], 0f); assertEquals(3f, out[0][2], 0f); assertEquals(6f, out[0][5], 0f); assertEquals(-1f, out[0][6], 0f)
    }

    @Test
    fun `un chunk inconnu avant les donnees est ignore`() {
        val f = File(dir, "l.wav")
        // Un WAV 16 bits mono écrit à la main, avec un chunk LIST de taille impaire avant « data ».
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(16384); putShort(-16384); putShort(0); putShort(32767) }.array()
        val list = byteArrayOf(1, 2, 3)
        val bb = ByteBuffer.allocate(12 + 24 + 8 + 4 + 8 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(0); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(22050); bb.putInt(44100); bb.putShort(2); bb.putShort(16)
        bb.put("LIST".toByteArray()); bb.putInt(3); bb.put(list); bb.put(0) // octet de bourrage
        bb.put("data".toByteArray()); bb.putInt(pcm.size); bb.put(pcm)
        f.writeBytes(bb.array())
        val info = WavFile.readInfo(f)
        assertEquals(22050, info.sampleRate); assertEquals(4L, info.frames)
        val out = arrayOf(FloatArray(4))
        PcmReader(f).use { it.read(0, 4, out, 0) }
        assertEquals(0.5f, out[0][0], 1e-6f); assertEquals(-0.5f, out[0][1], 1e-6f)
    }

    @Test
    fun `un fichier non ecrit jusqu'au bout se relit quand meme`() {
        val f = File(dir, "x.wav")
        val w = WavWriter(f, 1, 44100, float = false)
        w.write(arrayOf(FloatArray(100) { 0.25f }), 100)
        // Pas de close() : l'en-tête annonce 0 octet de données, comme après un plantage.
        val info = WavFile.readInfo(f)
        assertEquals(100L, info.frames)
        w.close()
    }

    @Test(expected = IOException::class)
    fun `un fichier qui n'est pas un wav est refuse`() {
        val f = File(dir, "n.wav")
        f.writeBytes(ByteArray(100) { 7 })
        WavFile.readInfo(f)
    }

    @Test
    fun `le fournisseur de fichiers lit par source`() {
        File(dir, "sources").mkdirs()
        val f = File(dir, "sources/s1.wav")
        WavWriter(f, 2, 44100, float = false).use { it.write(arrayOf(FloatArray(10) { 0.5f }, FloatArray(10) { -0.5f }), 10) }
        FileSampleProvider(dir).use { prov ->
            val out = arrayOf(FloatArray(10), FloatArray(10))
            prov.read(Source("s1", "sources/s1.wav", 2, 10, 44100), 0, 10, out, 0)
            assertEquals(0.5f, out[0][3], 1e-4f); assertEquals(-0.5f, out[1][3], 1e-4f)
        }
    }

    @Test
    fun `un en-tete d'au moins 44 octets pour le 16 bits`() {
        val f = File(dir, "h.wav")
        WavWriter(f, 1, 44100, float = false).use { it.write(arrayOf(FloatArray(5)), 5) }
        assertEquals(44L + 10, f.length())
        RandomAccessFile(f, "r").use { assertEquals('R'.code, it.read()) }
    }
}
