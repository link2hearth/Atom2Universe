package com.Atom2Universe.app.pixelart.io

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Écrit un PNG RGBA 8 bits à partir de pixels ARGB **non prémultipliés**.
 *
 * `Bitmap.compress` prémultiplie puis dé-prémultiplie : un pixel très transparent perd sa couleur.
 * Ici, chaque pixel ressort tel qu'il est entré, et l'agrandissement (plus proche voisin) se fait
 * ligne par ligne, sans jamais construire l'image agrandie en mémoire.
 */
object PngWriter {

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun encode(pixels: IntArray, w: Int, h: Int, scale: Int = 1): ByteArray {
        val bos = ByteArrayOutputStream(w * h / 2 + 1024)
        write(bos, pixels, w, h, scale)
        return bos.toByteArray()
    }

    fun write(out: OutputStream, pixels: IntArray, w: Int, h: Int, scale: Int = 1) {
        require(w > 0 && h > 0 && scale >= 1)
        val ow = w * scale
        val oh = h * scale
        out.write(SIGNATURE)

        val header = ByteArray(13)
        putInt(header, 0, ow)
        putInt(header, 4, oh)
        header[8] = 8   // profondeur
        header[9] = 6   // RGBA
        chunk(out, "IHDR", header)

        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(6)
        DeflaterOutputStream(compressed, deflater, 1 shl 16).use { z ->
            val row = ByteArray(1 + ow * 4)       // octet de filtre (0) + pixels de la ligne agrandie
            for (y in 0 until h) {
                var p = 1
                for (x in 0 until w) {
                    val c = pixels[y * w + x]
                    val r = (c shr 16).toByte()
                    val g = (c shr 8).toByte()
                    val b = c.toByte()
                    val a = (c ushr 24).toByte()
                    for (s in 0 until scale) {
                        row[p++] = r; row[p++] = g; row[p++] = b; row[p++] = a
                    }
                }
                for (s in 0 until scale) z.write(row)
            }
        }
        deflater.end()
        chunk(out, "IDAT", compressed.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        out.flush()
    }

    private fun chunk(out: OutputStream, type: String, data: ByteArray) {
        val len = ByteArray(4)
        putInt(len, 0, data.size)
        out.write(len)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val c = ByteArray(4)
        putInt(c, 0, crc.value.toInt())
        out.write(c)
    }

    private fun putInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 24).toByte()
        b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte()
        b[off + 3] = v.toByte()
    }
}
