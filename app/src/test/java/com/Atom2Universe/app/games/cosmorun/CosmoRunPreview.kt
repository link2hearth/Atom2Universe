package com.Atom2Universe.app.games.cosmorun

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Peint sur PC la même scène que le jeu (mêmes faces, même ordre), pour la regarder sans appareil. */
object CosmoRunPreview {
    class Image(val w: Int, val h: Int) {
        val px = IntArray(w * h)
        /** Mélange alpha, comme le Canvas. */
        fun blend(x: Int, y: Int, argb: Int) {
            if (x < 0 || y < 0 || x >= w || y >= h) return
            val a = (argb ushr 24) / 255f
            if (a >= .999f) { px[y * w + x] = argb or (0xFF shl 24); return }
            val d = px[y * w + x]
            fun mix(s: Int, t: Int) = (s * a + t * (1 - a)).toInt()
            px[y * w + x] = (0xFF shl 24) or (mix(argb shr 16 and 255, d shr 16 and 255) shl 16) or
                (mix(argb shr 8 and 255, d shr 8 and 255) shl 8) or mix(argb and 255, d and 255)
        }
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 255) * (1 - t) + (b shr shift and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Polygone convexe par découpage en éventail ; test du centre de pixel. */
    private fun fill(img: Image, xy: FloatArray, count: Int, color: Int) {
        for (i in 1 until count - 1) {
            val x0 = xy[0]; val y0 = xy[1]; val x1 = xy[i * 2]; val y1 = xy[i * 2 + 1]
            val x2 = xy[i * 2 + 2]; val y2 = xy[i * 2 + 3]
            val area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (kotlin.math.abs(area) < 1e-4f) continue
            val minX = max(0, floor(min(x0, min(x1, x2))).toInt()); val maxX = min(img.w - 1, ceil(max(x0, max(x1, x2))).toInt())
            val minY = max(0, floor(min(y0, min(y1, y2))).toInt()); val maxY = min(img.h - 1, ceil(max(y0, max(y1, y2))).toInt())
            for (y in minY..maxY) for (x in minX..maxX) {
                val fx = x + .5f; val fy = y + .5f
                val a = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) / area
                val b = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) / area
                val c = 1f - a - b
                // Tolérance d'un demi-pixel de bord pour éviter les fines fentes entre faces voisines.
                val e = -.5f / kotlin.math.sqrt(kotlin.math.abs(area))
                if (a >= e && b >= e && c >= e) img.blend(x, y, color)
            }
        }
    }

    fun render(game: CosmoRunGame, width: Int, height: Int, clock: Float = 0f, demo: Boolean = false,
               suit: Int = 0): Image {
        val scene = CosmoRunScene().also { it.suit = suit; it.bottomInset = height * .12f }
        val img = Image(width, height)
        scene.frame(width, height, game, clock)
        val top = when (scene.theme) { 1 -> 0xFF21182E.toInt(); 2 -> 0xFF191637.toInt(); else -> 0xFF071729.toInt() }
        val hz = scene.horizon
        for (y in 0 until height) {
            val c = if (y < hz) lerp(CosmoRunScene.INK, top, y / hz)
            else lerp(0xFF25314A.toInt(), CosmoRunScene.INK, (y - hz) / (height - hz))
            for (x in 0 until width) img.px[y * width + x] = c
        }
        val travel = if (demo) clock * 10f else game.distance
        for (layer in 0..1) {
            scene.begin()
            if (layer == 0) scene.buildDeck(travel) else scene.buildWorld(game, demo, travel)
            scene.sortFarFirst()
            for (face in scene.visible) fill(img, face.xy, face.count, face.color)
        }
        return img
    }

    fun save(img: Image, name: String) {
        val dir = File("build/cosmo-preview").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(png(img))
    }

    private fun png(img: Image): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until img.h) {
                z.write(0)
                for (x in 0 until img.w) {
                    val c = img.px[y * img.w + x]
                    z.write(c shr 16 and 255); z.write(c shr 8 and 255); z.write(c and 255)
                }
            }
        }
        val out = ByteArrayOutputStream(); val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream()
        DataOutputStream(hdr).apply { writeInt(img.w); writeInt(img.h); write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", hdr.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}
