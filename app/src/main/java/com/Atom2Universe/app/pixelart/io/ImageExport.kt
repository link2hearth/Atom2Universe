package com.Atom2Universe.app.pixelart.io

import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.PixelColor
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Fabrique les octets des fichiers exportés (PNG, GIF, planche de sprites) à partir d'un [Document]. */
object ImageExport {

    enum class GifDirection { FORWARD, REVERSE, PING_PONG }

    class GifOptions(
        val scale: Int = 1,
        /** 0 = sans fin, 1 = une fois, n = n fois. */
        val loops: Int = 0,
        val direction: GifDirection = GifDirection.FORWARD,
        /** Fond sur lequel aplatir ; null = transparent. */
        val background: Int? = null,
        /** Vitesse en pour cent de celle réglée (100 = telle quelle). */
        val speedPercent: Int = 100,
    )

    /** Côté maximal d'un fichier exporté : au-delà, les tampons explosent la mémoire. */
    const val MAX_EXPORT_SIDE = 8192

    fun maxScaleFor(w: Int, h: Int): Int = (MAX_EXPORT_SIDE / maxOf(w, h)).coerceIn(1, 64)

    fun flatten(doc: Document, frameIndex: Int): IntArray =
        Compositor.compositeFrame(doc, doc.frames[frameIndex.coerceIn(0, doc.frames.size - 1)].id)

    fun encodePng(doc: Document, frameIndex: Int, scale: Int): ByteArray {
        val s = scale.coerceIn(1, maxScaleFor(doc.width, doc.height))
        return PngWriter.encode(flatten(doc, frameIndex), doc.width, doc.height, s)
    }

    fun encodeGif(doc: Document, opt: GifOptions, out: OutputStream) {
        val s = opt.scale.coerceIn(1, maxScaleFor(doc.width, doc.height))
        val w = doc.width * s
        val h = doc.height * s
        val order = frameOrder(doc.frames.size, opt.direction)
        val gif = GifWriter(out, w, h, opt.loops)
        // Une même image (ping-pong) n'est aplatie et agrandie qu'une fois.
        val cache = HashMap<Int, IntArray>()
        for (i in order) {
            val px = cache.getOrPut(i) {
                var flat = flatten(doc, i)
                opt.background?.let { bg -> flat = flattenOver(flat, bg) }
                if (s > 1) scaleNearest(flat, doc.width, doc.height, s) else flat
            }
            val ms = (doc.frames[i].durationMs * 100 / opt.speedPercent.coerceIn(10, 1000))
            gif.addFrame(px, ms)
        }
        gif.finish()
    }

    fun encodeGifBytes(doc: Document, opt: GifOptions): ByteArray =
        ByteArrayOutputStream().also { encodeGif(doc, opt, it) }.toByteArray()

    fun frameOrder(n: Int, d: GifDirection): List<Int> = when (d) {
        GifDirection.FORWARD -> (0 until n).toList()
        GifDirection.REVERSE -> (n - 1 downTo 0).toList()
        GifDirection.PING_PONG -> if (n <= 2) (0 until n).toList() else (0 until n).toList() + (n - 2 downTo 1).toList()
    }

    private fun flattenOver(px: IntArray, bg: Int): IntArray {
        val solid = bg or (0xFF shl 24)
        return IntArray(px.size) { PixelColor.over(solid, px[it]) }
    }

    fun scaleNearest(px: IntArray, w: Int, h: Int, scale: Int): IntArray {
        val ow = w * scale
        val out = IntArray(ow * h * scale)
        for (y in 0 until h) {
            val row = y * scale * ow
            for (x in 0 until w) {
                val c = px[y * w + x]
                for (k in 0 until scale) out[row + x * scale + k] = c
            }
            for (k in 1 until scale) System.arraycopy(out, row, out, row + k * ow, ow)
        }
        return out
    }

    class Sheet(val pixels: IntArray, val w: Int, val h: Int, val columns: Int, val rows: Int)

    /** Toutes les images à la suite, [columns] par ligne (0 = une seule ligne), [padding] pixels entre elles. */
    fun sheet(doc: Document, columns: Int, padding: Int = 0): Sheet {
        val n = doc.frames.size
        val cols = if (columns <= 0) n else columns.coerceIn(1, n)
        val rows = (n + cols - 1) / cols
        val pad = padding.coerceAtLeast(0)
        val sw = cols * doc.width + (cols - 1) * pad
        val sh = rows * doc.height + (rows - 1) * pad
        val out = IntArray(sw * sh)
        for (i in 0 until n) {
            val flat = flatten(doc, i)
            val ox = (i % cols) * (doc.width + pad)
            val oy = (i / cols) * (doc.height + pad)
            for (y in 0 until doc.height) System.arraycopy(flat, y * doc.width, out, (oy + y) * sw + ox, doc.width)
        }
        return Sheet(out, sw, sh, cols, rows)
    }

    fun encodeSheetPng(doc: Document, columns: Int, padding: Int, scale: Int): ByteArray {
        val s = sheet(doc, columns, padding)
        return PngWriter.encode(s.pixels, s.w, s.h, scale.coerceIn(1, maxScaleFor(s.w, s.h)))
    }

    /**
     * Découpe une planche de sprites en images de `cellW × cellH`. Retourne les cases dans l'ordre de
     * lecture, ou une liste vide si la planche n'est pas un multiple de la case.
     */
    fun splitSheet(px: IntArray, w: Int, h: Int, cols: Int, rows: Int): List<IntArray> {
        if (cols < 1 || rows < 1 || w % cols != 0 || h % rows != 0) return emptyList()
        val cw = w / cols
        val ch = h / rows
        val out = ArrayList<IntArray>(cols * rows)
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = IntArray(cw * ch)
            for (y in 0 until ch) System.arraycopy(px, (r * ch + y) * w + c * cw, cell, y * cw, cw)
            out.add(cell)
        }
        return out
    }

    /** Diviseurs de [n] compris entre 1 et [max] : les découpages possibles d'une planche. */
    fun divisors(n: Int, max: Int = 64): List<Int> = (1..minOf(n, max)).filter { n % it == 0 }
}
