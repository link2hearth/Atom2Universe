package com.Atom2Universe.app.pixelart.io

import java.io.OutputStream

/**
 * Encodeur GIF animé, sans Android. Chaque image a sa propre palette : le pixel art, qui compte
 * presque toujours moins de 256 couleurs, ressort **exact** ; au-delà, quantification par coupe
 * médiane. Un pixel d'alpha < 128 devient transparent (le GIF n'a pas de demi-transparence :
 * l'appelant aplatit sur un fond s'il n'en veut pas).
 */
class GifWriter(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    /** 0 = boucle sans fin, 1 = une seule lecture, n = n lectures. */
    loops: Int = 0,
) {
    private var finished = false

    init {
        require(width in 1..65535 && height in 1..65535)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width); short(height)
        out.write(0x70)   // pas de palette globale, 8 bits par couche
        out.write(0)      // couleur de fond
        out.write(0)      // ratio de pixel
        if (loops != 1) {
            out.write(0x21); out.write(0xFF); out.write(11)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(3); out.write(1)
            short(if (loops == 0) 0 else (loops - 1).coerceIn(0, 65535))
            out.write(0)
        }
    }

    fun addFrame(pixels: IntArray, delayMs: Int) = addFrame(pixels, width, height, 1, delayMs)

    /**
     * Ajoute une image de `srcW × srcH` agrandie [scale] fois (plus proche voisin) : la palette se calcule
     * sur l'image d'origine et chaque indice est répété à l'écriture, sans jamais fabriquer l'image agrandie.
     */
    fun addFrame(pixels: IntArray, srcW: Int, srcH: Int, scale: Int, delayMs: Int) {
        check(!finished)
        require(scale >= 1 && srcW * scale == width && srcH * scale == height && pixels.size == srcW * srcH)
        val q = quantize(pixels)
        val bits = bitsFor(q.palette.size)
        val tableSize = 1 shl bits
        val hasTransparency = q.transparentIndex >= 0

        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(((if (hasTransparency) 2 else 1) shl 2) or (if (hasTransparency) 1 else 0))
        short(((delayMs + 5) / 10).coerceIn(2, 65535))
        out.write(if (hasTransparency) q.transparentIndex else 0)
        out.write(0)

        out.write(0x2C)
        short(0); short(0); short(width); short(height)
        out.write(0x80 or (bits - 1))
        for (i in 0 until tableSize) {
            val c = if (i < q.palette.size) q.palette[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        lzw(q.indices, srcW, scale, maxOf(2, bits))
    }

    fun finish() {
        if (finished) return
        finished = true
        out.write(0x3B)
        out.flush()
    }

    private fun short(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun bitsFor(n: Int): Int {
        var b = 1
        while ((1 shl b) < n) b++
        return b
    }

    // ---- Quantification --------------------------------------------------------------------

    private class Quantized(val palette: IntArray, val indices: ByteArray, val transparentIndex: Int)

    private class Entry(val rgb: Int, var count: Int)

    /**
     * Table couleur (0xRRGGBB) -> entier, à adressage ouvert : aucun objet par pixel, contrairement à une
     * HashMap<Int, …> qui emballe chaque clé.
     */
    private class IntTable(expected: Int) {
        private var keys = IntArray(capacityFor(expected)) { EMPTY }
        private var values = IntArray(keys.size)
        var size = 0
            private set

        private fun capacityFor(n: Int): Int {
            var c = 16
            while (c < n * 2) c = c shl 1
            return c
        }

        private fun slot(k: Int, ks: IntArray): Int {
            val mask = ks.size - 1
            var h = (k * -1640531535 ushr 7) and mask
            while (ks[h] != EMPTY && ks[h] != k) h = (h + 1) and mask
            return h
        }

        /** Valeur de [k], ou -1. */
        fun get(k: Int): Int {
            val h = slot(k, keys)
            return if (keys[h] == k) values[h] else -1
        }

        fun put(k: Int, v: Int) {
            if ((size + 1) * 2 > keys.size) grow()
            val h = slot(k, keys)
            if (keys[h] != k) { keys[h] = k; size++ }
            values[h] = v
        }

        /** Ajoute 1 au compte de [k]. */
        fun increment(k: Int) {
            if ((size + 1) * 2 > keys.size) grow()
            val h = slot(k, keys)
            if (keys[h] != k) { keys[h] = k; values[h] = 0; size++ }
            values[h]++
        }

        fun forEach(f: (Int, Int) -> Unit) {
            for (i in keys.indices) if (keys[i] != EMPTY) f(keys[i], values[i])
        }

        private fun grow() {
            val oldK = keys
            val oldV = values
            keys = IntArray(oldK.size * 2) { EMPTY }
            values = IntArray(keys.size)
            for (i in oldK.indices) if (oldK[i] != EMPTY) {
                val h = slot(oldK[i], keys)
                keys[h] = oldK[i]; values[h] = oldV[i]
            }
        }

        companion object {
            /** Aucune couleur 0xRRGGBB ne vaut -1 (les 8 bits du haut sont toujours à 0). */
            const val EMPTY = -1
        }
    }

    private fun quantize(pixels: IntArray): Quantized {
        val counts = IntTable(256)
        var transparent = false
        for (c in pixels) {
            if (c ushr 24 < 128) { transparent = true; continue }
            counts.increment(c and 0xFFFFFF)
        }
        val reserved = if (transparent) 1 else 0
        val maxColors = 256 - reserved
        val exact = counts.size <= maxColors
        val chosen: IntArray = if (exact) {
            val out = IntArray(counts.size)
            var n = 0
            counts.forEach { rgb, _ -> out[n++] = rgb }
            out
        } else {
            // Au-delà de 256 couleurs seulement : une entrée par couleur distincte (jamais par pixel).
            val entries = ArrayList<Entry>(counts.size)
            counts.forEach { rgb, n -> entries.add(Entry(rgb, n)) }
            medianCut(entries, maxColors)
        }

        val palette = IntArray(chosen.size + reserved)
        for (i in chosen.indices) palette[i + reserved] = chosen[i]
        val indices = ByteArray(pixels.size)
        val cache = IntTable(if (exact) chosen.size else 1024)
        if (exact) for (i in chosen.indices) cache.put(chosen[i], i + reserved)
        for (i in pixels.indices) {
            val c = pixels[i]
            if (c ushr 24 < 128) { indices[i] = 0; continue }
            val rgb = c and 0xFFFFFF
            var idx = cache.get(rgb)
            if (idx < 0) { idx = nearest(palette, reserved, rgb); cache.put(rgb, idx) }
            indices[i] = idx.toByte()
        }
        return Quantized(if (palette.isEmpty()) intArrayOf(0, 0) else palette, indices, if (transparent) 0 else -1)
    }

    private fun nearest(palette: IntArray, from: Int, rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        var best = from
        var bestD = Int.MAX_VALUE
        for (i in from until palette.size) {
            val p = palette[i]
            val dr = r - ((p shr 16) and 0xFF); val dg = g - ((p shr 8) and 0xFF); val db = b - (p and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun medianCut(entries: List<Entry>, target: Int): IntArray {
        val boxes = ArrayList<MutableList<Entry>>()
        boxes.add(entries.toMutableList())
        while (boxes.size < target) {
            // La boîte la plus étendue (sur son meilleur canal) est coupée en deux au médian pondéré.
            var bi = -1
            var bestRange = 0
            var bestCh = 0
            for ((i, box) in boxes.withIndex()) {
                if (box.size < 2) continue
                for (ch in 0..2) {
                    val shift = 16 - 8 * ch
                    var lo = 255; var hi = 0
                    for (e in box) { val v = (e.rgb shr shift) and 0xFF; if (v < lo) lo = v; if (v > hi) hi = v }
                    if (hi - lo > bestRange) { bestRange = hi - lo; bi = i; bestCh = ch }
                }
            }
            if (bi < 0) break
            val box = boxes[bi]
            val shift = 16 - 8 * bestCh
            box.sortBy { (it.rgb shr shift) and 0xFF }
            val total = box.sumOf { it.count.toLong() }
            var acc = 0L
            var cut = 1
            for (i in box.indices) {
                acc += box[i].count
                if (acc * 2 >= total) { cut = (i + 1).coerceIn(1, box.size - 1); break }
            }
            boxes[bi] = box.subList(0, cut).toMutableList()
            boxes.add(box.subList(cut, box.size).toMutableList())
        }
        return IntArray(boxes.size) { i ->
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (e in boxes[i]) {
                r += ((e.rgb shr 16) and 0xFF).toLong() * e.count
                g += ((e.rgb shr 8) and 0xFF).toLong() * e.count
                b += (e.rgb and 0xFF).toLong() * e.count
                n += e.count
            }
            if (n == 0L) 0 else (((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt())
        }
    }

    // ---- LZW -------------------------------------------------------------------------------

    private val block = ByteArray(255)
    private var blockLen = 0
    private var bitBuf = 0
    private var bitCount = 0

    private fun putByte(b: Int) {
        block[blockLen++] = b.toByte()
        if (blockLen == 255) flushBlock()
    }

    private fun flushBlock() {
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }
    }

    /** Compresse les indices de l'image d'origine ([srcW] de large), chacun répété [scale] fois en largeur et en hauteur. */
    private fun lzw(indices: ByteArray, srcW: Int, scale: Int, minCodeSize: Int) {
        out.write(minCodeSize)
        blockLen = 0; bitBuf = 0; bitCount = 0

        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var maxCode = (1 shl codeSize) - 1
        var next = eoi + 1
        val keys = IntArray(HASH_SIZE) { -1 }
        val vals = IntArray(HASH_SIZE)

        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                putByte(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
            // L'entrée qui va être ajoutée après ce code aura le numéro [next] : si elle ne tient
            // plus dans [codeSize] bits, le décodeur passera à un bit de plus, donc nous aussi.
            if (next > maxCode && codeSize < 12) {
                codeSize++
                maxCode = (1 shl codeSize) - 1
            }
        }

        fun reset() {
            java.util.Arrays.fill(keys, -1)
            codeSize = minCodeSize + 1
            maxCode = (1 shl codeSize) - 1
            next = eoi + 1
        }

        emit(clear)
        if (indices.isEmpty()) {
            emit(eoi)
        } else {
            var prefix = -1
            for (y in 0 until height) {
                val row = (y / scale) * srcW
                for (x in 0 until width) {
                    val c = indices[row + x / scale].toInt() and 0xFF
                    if (prefix < 0) { prefix = c; continue }
                    val key = (prefix shl 8) or c
                    var h = (key * -1640531535 ushr 19) and (HASH_SIZE - 1)
                    while (keys[h] != -1 && keys[h] != key) h = (h + 1) and (HASH_SIZE - 1)
                    if (keys[h] == key) {
                        prefix = vals[h]
                    } else {
                        emit(prefix)
                        if (next < 4096) {
                            keys[h] = key
                            vals[h] = next++
                        } else {
                            emit(clear)
                            reset()
                        }
                        prefix = c
                    }
                }
            }
            emit(prefix)
            emit(eoi)
        }
        if (bitCount > 0) { putByte(bitBuf and 0xFF); bitBuf = 0; bitCount = 0 }
        flushBlock()
        out.write(0)
    }

    private companion object {
        const val HASH_SIZE = 1 shl 13
    }
}
