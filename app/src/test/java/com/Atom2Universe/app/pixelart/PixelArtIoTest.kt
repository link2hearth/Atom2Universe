package com.Atom2Universe.app.pixelart

import com.Atom2Universe.app.pixelart.io.GifWriter
import com.Atom2Universe.app.pixelart.io.PngWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.Inflater

/**
 * Les encodeurs PNG et GIF sont relus par de petits décodeurs écrits ici, d'après la spécification et
 * indépendamment des encodeurs : ce qui sort doit être exactement ce qui est entré.
 */
class PixelArtIoTest {

    private class Decoded(val w: Int, val h: Int, val px: IntArray) {
        fun at(x: Int, y: Int) = px[y * w + x]
    }

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun decodePng(bytes: ByteArray): Decoded {
        assertEquals(0x89, bytes[0].toInt() and 0xFF)
        var o = 8
        var w = 0
        var h = 0
        val idat = ByteArrayOutputStream()
        while (o < bytes.size) {
            val len = be32(bytes, o)
            val type = String(bytes, o + 4, 4, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> { w = be32(bytes, o + 8); h = be32(bytes, o + 12); assertEquals(6, bytes[o + 8 + 9].toInt()) }
                "IDAT" -> idat.write(bytes, o + 8, len)
            }
            o += 12 + len
        }
        val inf = Inflater()
        inf.setInput(idat.toByteArray())
        val raw = ByteArray(h * (1 + w * 4))
        var got = 0
        while (got < raw.size && !inf.finished()) got += inf.inflate(raw, got, raw.size - got)
        assertEquals(raw.size, got)
        val px = IntArray(w * h)
        for (y in 0 until h) {
            assertEquals("filtre", 0, raw[y * (1 + w * 4)].toInt())
            for (x in 0 until w) {
                val i = y * (1 + w * 4) + 1 + x * 4
                px[y * w + x] = ((raw[i + 3].toInt() and 0xFF) shl 24) or ((raw[i].toInt() and 0xFF) shl 16) or
                    ((raw[i + 1].toInt() and 0xFF) shl 8) or (raw[i + 2].toInt() and 0xFF)
            }
        }
        return Decoded(w, h, px)
    }

    private fun decodeGif(b: ByteArray): List<Decoded> {
        assertEquals("GIF89a", String(b, 0, 6, Charsets.US_ASCII))
        val packed = b[10].toInt() and 0xFF
        var o = 13
        if (packed and 0x80 != 0) o += 3 * (1 shl ((packed and 7) + 1))
        val frames = ArrayList<Decoded>()
        var transIndex = -1
        while (true) {
            when (val marker = b[o++].toInt() and 0xFF) {
                0x3B -> return frames
                0x21 -> {
                    val label = b[o++].toInt() and 0xFF
                    if (label == 0xF9) {
                        val flags = b[o + 1].toInt() and 0xFF
                        transIndex = if (flags and 1 != 0) b[o + 4].toInt() and 0xFF else -1
                    }
                    while (true) { val n = b[o++].toInt() and 0xFF; if (n == 0) break; o += n }
                }
                0x2C -> {
                    val fw = (b[o + 4].toInt() and 0xFF) or ((b[o + 5].toInt() and 0xFF) shl 8)
                    val fh = (b[o + 6].toInt() and 0xFF) or ((b[o + 7].toInt() and 0xFF) shl 8)
                    val lp = b[o + 8].toInt() and 0xFF
                    o += 9
                    assertTrue("palette locale attendue", lp and 0x80 != 0)
                    val n = 1 shl ((lp and 7) + 1)
                    val table = IntArray(n) { i ->
                        ((b[o + i * 3].toInt() and 0xFF) shl 16) or ((b[o + i * 3 + 1].toInt() and 0xFF) shl 8) or
                            (b[o + i * 3 + 2].toInt() and 0xFF)
                    }
                    o += 3 * n
                    val mcs = b[o++].toInt() and 0xFF
                    val data = ByteArrayOutputStream()
                    while (true) { val len = b[o++].toInt() and 0xFF; if (len == 0) break; data.write(b, o, len); o += len }
                    val idx = lzwDecode(data.toByteArray(), mcs, fw * fh)
                    assertEquals("nombre de pixels décodés", fw * fh, idx.size)
                    val px = IntArray(fw * fh) { i ->
                        val c = idx[i].toInt() and 0xFF
                        if (c == transIndex) 0 else (0xFF shl 24) or table[c]
                    }
                    frames.add(Decoded(fw, fh, px))
                }
                else -> error("bloc inattendu $marker à $o")
            }
        }
    }

    private fun lzwDecode(data: ByteArray, mcs: Int, expected: Int): ByteArray {
        val clear = 1 shl mcs
        val eoi = clear + 1
        var codeSize = mcs + 1
        var next = eoi + 1
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        for (i in 0 until clear) suffix[i] = i.toByte()
        val out = ByteArrayOutputStream(expected)
        var bitPos = 0
        var prev = -1
        val tmp = ByteArray(4097)
        // Écrit la chaîne du code à l'envers dans tmp et retourne sa longueur.
        fun expand(code: Int): Int {
            var n = 0
            var c = code
            while (c >= clear) { tmp[n++] = suffix[c]; c = prefix[c] }
            tmp[n++] = c.toByte()
            return n
        }
        while (true) {
            var code = 0
            for (i in 0 until codeSize) {
                val byteIdx = (bitPos + i) shr 3
                if (byteIdx >= data.size) return out.toByteArray()
                if ((data[byteIdx].toInt() shr ((bitPos + i) and 7)) and 1 != 0) code = code or (1 shl i)
            }
            bitPos += codeSize
            if (code == clear) { codeSize = mcs + 1; next = eoi + 1; prev = -1; continue }
            if (code == eoi) break
            if (prev == -1) { out.write(code); prev = code; continue }
            val first: Byte
            if (code < next) {
                val n = expand(code)
                first = tmp[n - 1]
                for (i in n - 1 downTo 0) out.write(tmp[i].toInt())
            } else {
                val pn = expand(prev)
                first = tmp[pn - 1]
                for (i in pn - 1 downTo 0) out.write(tmp[i].toInt())
                out.write(first.toInt())
            }
            if (next < 4096) {
                prefix[next] = prev
                suffix[next] = first
                next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            prev = code
        }
        return out.toByteArray()
    }

    @Test
    fun pngRestitueChaquePixelDontLesDemiTransparents() {
        val px = intArrayOf(
            0xFFFF0000.toInt(), 0x8000FF00.toInt(), 0x010000FF,
            0x00000000, 0xFF123456.toInt(), 0x7F808080,
        )
        val img = decodePng(PngWriter.encode(px, 3, 2))
        assertEquals(3, img.w)
        assertEquals(2, img.h)
        assertTrue(px.contentEquals(img.px))
    }

    @Test
    fun pngAgrandiRepeteLesPixels() {
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        val img = decodePng(PngWriter.encode(px, 2, 1, scale = 4))
        assertEquals(8, img.w)
        assertEquals(4, img.h)
        for (y in 0 until 4) for (x in 0 until 8) {
            assertEquals(if (x < 4) px[0] else px[1], img.at(x, y))
        }
    }

    @Test
    fun gifPeuDeCouleursSortExact() {
        val a = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        )
        val b = a.reversedArray()
        val bos = ByteArrayOutputStream()
        val gif = GifWriter(bos, 4, 2, loops = 0)
        gif.addFrame(a, 100)
        gif.addFrame(b, 100)
        gif.finish()
        val frames = decodeGif(bos.toByteArray())
        assertEquals(2, frames.size)
        assertTrue(a.contentEquals(frames[0].px))
        assertTrue(b.contentEquals(frames[1].px))
    }

    @Test
    fun gifGardeLaTransparence() {
        val px = intArrayOf(0xFFFF0000.toInt(), 0x00000000, 0x20FFFFFF, 0xFF00FF00.toInt())
        val bos = ByteArrayOutputStream()
        val gif = GifWriter(bos, 2, 2)
        gif.addFrame(px, 50)
        gif.finish()
        val img = decodeGif(bos.toByteArray())[0]
        assertEquals(0xFFFF0000.toInt(), img.at(0, 0))
        assertEquals(0, img.at(1, 0))
        assertEquals(0, img.at(0, 1))
        assertEquals(0xFF00FF00.toInt(), img.at(1, 1))
    }

    @Test
    fun gifBruitAleatoireFaitTournerLaTableLZW() {
        // 200 couleurs tirées au hasard sur 200×200 pixels : la table LZW se remplit et se remet à zéro.
        val rnd = Random(42)
        val palette = IntArray(200) { 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF) }
        val px = IntArray(200 * 200) { palette[rnd.nextInt(palette.size)] }
        val bos = ByteArrayOutputStream()
        val gif = GifWriter(bos, 200, 200)
        gif.addFrame(px, 100)
        gif.finish()
        val img = decodeGif(bos.toByteArray())[0]
        var wrong = 0
        for (i in px.indices) if (img.px[i] != px[i]) wrong++
        assertEquals(0, wrong)
    }

    @Test
    fun gifUneADeuxTroisCouleurs() {
        for (colors in 1..3) {
            val px = IntArray(50) { 0xFF000000.toInt() or ((it % colors) * 0x555555) }
            val bos = ByteArrayOutputStream()
            val gif = GifWriter(bos, 10, 5)
            gif.addFrame(px, 100)
            gif.finish()
            assertTrue("$colors couleur(s)", px.contentEquals(decodeGif(bos.toByteArray())[0].px))
        }
    }

    @Test
    fun gifDegradeQuantifieSansErreurGrave() {
        val w = 64
        val h = 64
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            0xFF000000.toInt() or ((x * 4) shl 16) or ((y * 4) shl 8) or ((x + y) * 2 and 0xFF)
        }
        val bos = ByteArrayOutputStream()
        val gif = GifWriter(bos, w, h)
        gif.addFrame(px, 100)
        gif.finish()
        val img = decodeGif(bos.toByteArray())[0]
        var total = 0L
        for (i in px.indices) {
            val a = px[i]
            val c = img.px[i]
            total += Math.abs(((a shr 16) and 0xFF) - ((c shr 16) and 0xFF)) +
                Math.abs(((a shr 8) and 0xFF) - ((c shr 8) and 0xFF)) +
                Math.abs((a and 0xFF) - (c and 0xFF))
        }
        val mean = total / (px.size * 3.0)
        assertTrue("erreur moyenne $mean", mean < 12.0)
    }
}
