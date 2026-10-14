package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/** Garde-fous des terrains de combat. Les planches sont dans le banc `HamletApercuTest.decors`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HamletSceneTest {
    /** Téléphone, petit écran, tablette en paysage et carte d'aventure. */
    private val sizes = listOf(1080 to 1150, 720 to 735, 2560 to 768, 1080 to 472)

    /** Chacun a sa place, dans l'écran, sous l'horizon, de son côté ; l'avant est plus près du centre. */
    @Test fun chacunASaPlace() {
        val p = PointF()
        for ((w, h) in sizes) {
            val size = HamletArena.size(w, h)
            for (enemy in listOf(false, true)) {
                val spots = ArrayList<PointF>()
                fun check(depth: Float, label: String) {
                    val half = size * depth * 24f / 56f
                    assertTrue("$label ${w}x$h dans l'écran", p.x - half >= 0 && p.x + half <= w)
                    assertTrue("$label ${w}x$h sur le sol", p.y > h * HamletArena.HORIZON && p.y < h)
                    assertTrue("$label ${w}x$h de son côté", if (enemy) p.x > w / 2f else p.x < w / 2f)
                    assertTrue("$label ${w}x$h libre", spots.all { hypot(it.x - p.x, it.y - p.y) > size * .3f })
                    spots.add(PointF(p.x, p.y))
                }
                for (slot in 0..5) check(HamletArena.place(enemy, slot, -1, w, h, p), "rang $slot")
                for (v in 0..1) check(HamletArena.place(enemy, 1, v, w, h, p), "invocation $v")
                if (!enemy) check(HamletArena.pet(w, h, p), "familier")
                val front = (0..1).map { HamletArena.place(enemy, it, -1, w, h, p); kotlin.math.abs(p.x - w / 2f) }
                val back = (2..5).map { HamletArena.place(enemy, it, -1, w, h, p); kotlin.math.abs(p.x - w / 2f) }
                assertTrue("${w}x$h avant devant", front.max() < back.min())
            }
            assertTrue(HamletArena.reach(w, h) <= w / 2f)
        }
    }

    /** Six terrains différents, qui couvrent tout l'écran et bougent avec le temps. */
    @Test fun sixTerrainsAnimes() {
        val w = 540; val h = 575
        val looks = HashSet<Int>()
        for (region in 0..5) {
            val scene = HamletScene(region).apply { layout(w, h) }
            fun frame(t: Float): IntArray {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                scene.drawBack(c, t); scene.drawFront(c, t)
                return IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            }
            val a = frame(0f); val b = frame(1.7f)
            assertEquals("terrain $region couvert", 0, a.count { it ushr 24 != 0xFF })
            assertTrue("terrain $region animé", a.indices.count { a[it] != b[it] } > w * h / 400)
            var r = 0L; var g = 0L; var bl = 0L
            for (px in a) { r += (px shr 16) and 255; g += (px shr 8) and 255; bl += px and 255 }
            looks.add(((r / a.size / 24) shl 16 or (g / a.size / 24 shl 8) or (bl / a.size / 24)).toInt())
        }
        assertEquals(6, looks.size)
    }
}
