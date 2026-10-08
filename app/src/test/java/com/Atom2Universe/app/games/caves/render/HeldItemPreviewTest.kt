package com.Atom2Universe.app.games.caves.render

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Dessine sur ordinateur ce que le joueur voit en main, avec la même projection que le jeu
 * (62°, écran 16:10). Les planches PNG atterrissent dans app/build/held-preview/ pour régler
 * les poses à l'œil sans compiler d'APK ; les assertions gardent les objets à l'écran.
 */
class HeldItemPreviewTest {
    private val w = 480; private val h = 300
    private val proj = Mat4.perspective(62f, w.toFloat() / h, .04f, 12f)
    private val look = HeldLook(0xCFD6D4, 0xE5C36A)

    private class Image(val w: Int, val h: Int) {
        val px = IntArray(w * h)
        fun setRGB(x: Int, y: Int, c: Int) { px[y * w + x] = c }
    }
    private class Shot(val image: Image, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val pixels: Int)

    private fun render(kind: HeldKind, state: HeldState, charge: Float = 0f): Shot {
        val mesh = HeldEquipmentMesh()
        HeldItemModels.fpsFrame(mesh, kind, look, HeldItemPoses.pose(kind, state, charge))
        val img = Image(w, h)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, if (y < h * .55) 0xA8DDF0 else 0x7FA86A)
        val depth = FloatArray(w * h) { Float.MAX_VALUE }
        var minX = w; var maxX = -1; var minY = h; var maxY = -1; var pixels = 0
        val v = mesh.vertices
        var i = 0
        while (i < mesh.count) {
            val sx = FloatArray(3); val sy = FloatArray(3); val sz = FloatArray(3); var behind = false
            for (k in 0..2) {
                val o = i + k * 6
                val x = v[o]; val y = v[o + 1]; val z = v[o + 2]
                val cw = -z
                if (cw < .04f) behind = true
                val cx = proj[0] * x; val cy = proj[5] * y
                sx[k] = (cx / cw * .5f + .5f) * w; sy[k] = (1f - (cy / cw * .5f + .5f)) * h; sz[k] = cw
            }
            val color = (((v[i + 3] * 255).toInt().coerceIn(0, 255)) shl 16) or
                (((v[i + 4] * 255).toInt().coerceIn(0, 255)) shl 8) or ((v[i + 5] * 255).toInt().coerceIn(0, 255))
            i += 18
            if (behind) continue
            val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
            if (kotlin.math.abs(area) < 1e-6f) continue
            val x0 = maxOf(0, minOf(sx[0], sx[1], sx[2]).toInt()); val x1 = minOf(w - 1, maxOf(sx[0], sx[1], sx[2]).toInt() + 1)
            val y0 = maxOf(0, minOf(sy[0], sy[1], sy[2]).toInt()); val y1 = minOf(h - 1, maxOf(sy[0], sy[1], sy[2]).toInt() + 1)
            for (py in y0..y1) for (px in x0..x1) {
                val fx = px + .5f; val fy = py + .5f
                val a = ((sx[1] - fx) * (sy[2] - fy) - (sx[2] - fx) * (sy[1] - fy)) / area
                val b = ((sx[2] - fx) * (sy[0] - fy) - (sx[0] - fx) * (sy[2] - fy)) / area
                val c = 1f - a - b
                if (a < 0f || b < 0f || c < 0f) continue
                val d = a * sz[0] + b * sz[1] + c * sz[2]
                val idx = py * w + px
                if (d >= depth[idx]) continue
                if (depth[idx] == Float.MAX_VALUE) {
                    pixels++
                    minX = minOf(minX, px); maxX = maxOf(maxX, px); minY = minOf(minY, py); maxY = maxOf(maxY, py)
                }
                depth[idx] = d; img.setRGB(px, py, color)
            }
        }
        // Réticule, pour juger où pointe l'objet.
        for (d in -6..6) { img.setRGB(w / 2 + d, h / 2, 0xFFFFFF); img.setRGB(w / 2, h / 2 + d, 0xFFFFFF) }
        return Shot(img, minX, maxX, minY, maxY, pixels)
    }

    private fun sheet(name: String, shots: List<Shot>) {
        val cols = 4; val rows = (shots.size + cols - 1) / cols
        val out = Image(cols * w, rows * h)
        shots.forEachIndexed { n, s ->
            val ox = (n % cols) * w; val oy = (n / cols) * h
            for (y in 0 until h) for (x in 0 until w) out.setRGB(ox + x, oy + y, s.image.px[y * w + x])
        }
        val dir = File("build/held-preview").apply { mkdirs() }
        File(dir, "$name.png").writeBytes(png(out))
    }

    /** Encodeur PNG minimal (java.awt n'existe pas sur le classpath Android des tests). */
    private fun png(img: Image): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until img.h) {
                z.write(0)
                for (x in 0 until img.w) { val c = img.px[y * img.w + x]; z.write(c shr 16 and 255); z.write(c shr 8 and 255); z.write(c and 255) }
            }
        }
        val out = ByteArrayOutputStream(); val d = DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream(); DataOutputStream(hdr).apply { writeInt(img.w); writeInt(img.h); write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", hdr.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test fun restPosesStayInTheLowerRightAndLeaveTheCrosshairFree() {
        val shots = HeldKind.values().map { kind -> kind to render(kind, HeldState()) }
        sheet("rest", shots.map { it.second })
        for ((kind, s) in shots) {
            assertTrue("$kind invisible", s.pixels > 1500)
            // Jamais sur le réticule, et pas plus de 60 % de la hauteur d'écran au repos.
            assertTrue("$kind couvre le réticule", s.minX > w / 2 + 10 || s.maxY < h / 2 - 10 || s.minY > h / 2 + 10)
            assertTrue("$kind trop grand (${s.maxY - s.minY}px)", kind == HeldKind.SPEAR || kind == HeldKind.FISHING_ROD || s.minY > h * .33f)
        }
    }

    @Test fun animationSheets() {
        for (kind in HeldKind.values()) {
            val shots = mutableListOf<Shot>()
            shots += render(kind, HeldState(equip = .35f))
            if (kind.melee) {
                shots += render(kind, HeldState(charge = 1f))
                for (p in listOf(.08f, .25f, .40f, .7f)) shots += render(kind, HeldState(attack = p), charge = 0f)
                shots += render(kind, HeldState(guard = 1f))
            } else for (p in listOf(.2f, .32f, .45f, .6f, .85f)) shots += render(kind, HeldState(use = p))
            sheet(kind.name.lowercase(), shots)
            // L'arme reste visible à chaque image du geste : sinon le coup paraît ne pas partir.
            shots.drop(1).forEachIndexed { n, s -> assertTrue("$kind image $n vide", s.pixels > 800) }
        }
    }
}
