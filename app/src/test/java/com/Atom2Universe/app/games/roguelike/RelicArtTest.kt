package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Les icônes des reliques ([RelicArt]) : des grilles bien formées, une icône différente par
 * relique, et une planche PNG dans app/build/relic-preview/ pour les juger à l'œil.
 */
class RelicArtTest {
    private val size = RelicArt.SIZE

    @Test
    fun chaqueGrilleFaitSeizeSurSeizeEnCouleursConnues() {
        for (relic in Relic.entries) {
            val rows = RelicArt.sprite(relic)
            assertEquals("$relic : nombre de lignes", size, rows.size)
            rows.forEachIndexed { y, row ->
                assertEquals("$relic, ligne $y : « $row »", size, row.length)
                row.forEach { ch -> assertTrue("$relic, ligne $y : lettre « $ch » hors palette", ch == '.' || ch in RelicArt.palette) }
            }
        }
    }

    /** Le contour s'ajoute autour de la silhouette : sans pixel libre au bord, il serait coupé. */
    @Test
    fun laSilhouetteLaisseLaPlaceDuContour() {
        for (relic in Relic.entries) {
            val rows = RelicArt.sprite(relic)
            for (i in 0 until size) {
                assertEquals("$relic touche le haut", '.', rows[0][i])
                assertEquals("$relic touche le bas", '.', rows[size - 1][i])
                assertEquals("$relic touche la gauche", '.', rows[i][0])
                assertEquals("$relic touche la droite", '.', rows[i][size - 1])
            }
        }
    }

    @Test
    fun deuxReliquesNeSePartagentPasUneIcone() {
        val seen = HashMap<List<Int>, Relic>()
        for (relic in Relic.entries) {
            val pixels = RelicArt.pixels(relic).toList()
            assertTrue("$relic a la même icône que ${seen[pixels]}", seen.put(pixels, relic) == null)
            assertTrue("$relic est presque vide", pixels.count { it != 0 } > 30)
        }
    }

    /** Toutes les icônes, agrandies huit fois sur le fond des cartes de l'inventaire. */
    @Test
    fun planche() {
        val scale = 8; val gap = 8; val cols = 8
        val cell = size * scale + gap
        val rowsCount = (Relic.entries.size + cols - 1) / cols
        val w = cols * cell + gap; val h = rowsCount * cell + gap
        val background = 0x354058
        val image = IntArray(w * h) { background }
        Relic.entries.forEachIndexed { n, relic ->
            val pixels = RelicArt.pixels(relic)
            val ox = gap + (n % cols) * cell; val oy = gap + (n / cols) * cell
            for (y in 0 until size * scale) for (x in 0 until size * scale) {
                val c = pixels[(y / scale) * size + x / scale]
                if (c != 0) image[(oy + y) * w + ox + x] = c and 0xFFFFFF
            }
        }
        val dir = File("build/relic-preview").apply { mkdirs() }
        File(dir, "reliques.png").writeBytes(png(image, w, h))
    }

    private fun png(px: IntArray, w: Int, h: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until h) {
                z.write(0)
                for (x in 0 until w) { val c = px[y * w + x]; z.write(c shr 16 and 255); z.write(c shr 8 and 255); z.write(c and 255) }
            }
        }
        val out = ByteArrayOutputStream(); val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream(); DataOutputStream(hdr).apply { writeInt(w); writeInt(h); write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", hdr.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}
