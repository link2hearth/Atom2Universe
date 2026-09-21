package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Crypte souterraine : pierre froide, cierges chauds et lustre animé au-dessus du combat. */
internal object DungeonCryptBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Long) {
        paint.color = color.toInt()
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun polygon(c: Canvas, color: Long, vararg points: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        paint.color = color.toInt()
        c.drawPath(path, paint)
    }
    private fun upperSpace(c: Canvas, floor: Float) {
        c.translate(0f, floor)
        c.scale(1f, (floor / 84f).coerceAtMost(1f))
        c.translate(0f, -84f)
    }

    fun draw(c: Canvas, floor: Float, height: Float) {
        c.drawColor(0xFF25303F.toInt())
        for (row in 0..(floor / 9).toInt()) for (col in -1..11) {
            val x = col * 23f + row % 2 * 11
            rect(c, x, row * 9f, 22f, 8f, if ((row + col) % 3 == 0) 0xFF394957 else 0xFF32414F)
            rect(c, x + 1, row * 9f, 20f, 1f, 0xFF455360)
        }
        // Tous les éléments hauts partagent le même repère, même sur une vue peu haute.
        c.save()
        upperSpace(c, floor)
        for (center in intArrayOf(39, 120, 201)) {
            polygon(c, 0xFF172432, center - 22f, 84f, center - 22f, 39f,
                center - 16f, 27f, center.toFloat(), 17f, center + 16f, 27f,
                center + 22f, 39f, center + 22f, 84f)
            polygon(c, 0xFF627080, center - 26f, 41f, center - 20f, 25f,
                center.toFloat(), 12f, center + 20f, 25f, center + 26f, 41f,
                center + 21f, 41f, center + 16f, 29f, center.toFloat(), 19f,
                center - 16f, 29f, center - 21f, 41f)
            rect(c, center - 26f, 41f, 5f, 43f, 0xFF526373)
            rect(c, center + 21f, 41f, 5f, 43f, 0xFF405363)
            for (j in 0..3) {
                rect(c, center - 26f, 43f + j * 10, 5f, 1f, 0xFF2A3949)
                rect(c, center + 21f, 43f + j * 10, 5f, 1f, 0xFF263748)
            }
            rect(c, center - 28f, 79f, 9f, 5f, 0xFF71808B)
            rect(c, center + 19f, 79f, 9f, 5f, 0xFF596D7B)
        }
        // Tombeau sculpté au fond ; silhouette de gisant sur son couvercle.
        rect(c, 99f, 72f, 43f, 10f, 0xFF566371)
        rect(c, 97f, 69f, 47f, 4f, 0xFF9A9E94)
        rect(c, 100f, 70f, 41f, 1f, 0xFFC0BAA1)
        rect(c, 105f, 76f, 6f, 3f, 0xFF303F50)
        rect(c, 130f, 76f, 6f, 3f, 0xFF303F50)
        rect(c, 118f, 74f, 3f, 7f, 0xFF899287)
        rect(c, 114f, 76f, 11f, 2f, 0xFF899287)
        rect(c, 105f, 64f, 7f, 5f, 0xFFAEB3A0)
        polygon(c, 0xFF82938E, 113f, 64f, 126f, 64f, 136f, 67f,
            138f, 69f, 112f, 69f)
        rect(c, 116f, 65f, 15f, 1f, 0xFFBCC1A7)
        // Ossuaires latéraux, rangées de crânes sous les arcs.
        for (center in intArrayOf(39, 201)) {
            for (row in 0..1) {
                rect(c, center - 17f, 58f + row * 14, 34f, 3f, 0xFF576775)
                for (i in 0..3) skull(c, center - 15f + i * 8, 51f + row * 14)
            }
        }
        // Cierges de chaque côté du tombeau ; flammes dessinées dans atmosphere.
        for (x in intArrayOf(88, 151)) {
            rect(c, x - 4f, 78f, 12f, 3f, 0xFF807153)
            rect(c, x.toFloat(), 66f, 3f, 13f, 0xFFB09B66)
            rect(c, x - 5f, 65f, 13f, 2f, 0xFFB09B66)
            for (j in 0..2) {
                val y = 58f - j % 2 * 3
                rect(c, x - 4f + j * 5, y, 2f, 65f - y, 0xFFDED0A2)
            }
        }
        c.restore()
        rect(c, 0f, floor + 2, 240f, height, 0xFF344350)
        for (row in 0..((height - floor) / 17).toInt()) for (col in -1..7) {
            val x = col * 35f + row % 2 * 17
            val y = floor + 4 + row * 17
            rect(c, x, y, 33f, 15f, if ((row + col) % 3 == 0) 0xFF425362 else 0xFF3C4D5B)
            rect(c, x + 1, y, 31f, 1f, 0xFF4B5B68)
            if ((row * 3 + col) % 7 == 0) {
                rect(c, x + 9, y + 7, 4f, 1f, 0xFF2F404D)
                rect(c, x + 12, y + 8, 1f, 3f, 0xFF2F404D)
            }
        }
        // Sarcophages étroits sur les marges : aucun volume dans les emplacements de combat.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            sarcophagus(c, 1f, floor + 18)
            sarcophagus(c, -3f, height - 44)
            for (i in 0..4) {
                val y = floor + 72 + i * 13f
                if (y < height - 48) {
                    rect(c, 2f, y, 7f, 2f, 0xFFA3AD9B)
                    rect(c, 1f, y - 1, 2f, 4f, 0xFFBCC3AA)
                    rect(c, 8f, y - 1, 2f, 4f, 0xFFBCC3AA)
                }
            }
            c.restore()
        }
    }

    private fun skull(c: Canvas, x: Float, y: Float) {
        rect(c, x, y, 6f, 5f, 0xFFACAFA0)
        rect(c, x + 1, y + 5, 4f, 2f, 0xFF858F87)
        rect(c, x + 1, y + 2, 1f, 2f, 0xFF283743)
        rect(c, x + 4, y + 2, 1f, 2f, 0xFF283743)
    }

    private fun sarcophagus(c: Canvas, x: Float, y: Float) {
        polygon(c, 0xFF202F3F, x, y + 2, x + 16, y + 4, x + 19, y + 35,
            x + 13, y + 40, x, y + 39)
        polygon(c, 0xFF647885, x, y, x + 12, y, x + 16, y + 7,
            x + 13, y + 34, x + 1, y + 35)
        polygon(c, 0xFF8C9A9D, x + 1, y + 2, x + 10, y + 2, x + 13, y + 8,
            x + 10, y + 31, x + 2, y + 32)
        rect(c, x + 5, y + 7, 4f, 5f, 0xFFC0C1AA)
        rect(c, x + 4, y + 13, 6f, 12f, 0xFFB0B9A6)
        rect(c, x + 3, y + 16, 8f, 2f, 0xFF647B7D)
        rect(c, x + 6, y + 13, 1f, 17f, 0xFF647B7D)
        rect(c, x + 3, y + 32, 8f, 1f, 0xFFCED0B5)
    }

    private fun flame(c: Canvas, x: Float, y: Float, tick: Int) {
        val h = 4f + tick % 3
        rect(c, x - 3, y - 6, 8f, 9f, 0x20E8AE64)
        rect(c, x - 1, y - h + 1, 4f, h, 0xFFBA784A)
        rect(c, x, y - h, 2f, h, 0xFFF0BF69)
        rect(c, x, y - 2, 1f, 3f, 0xFFFFE7B0)
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float) {
        val tick = (clock / 130f).toInt()
        c.save()
        upperSpace(c, floor)
        for (x in intArrayOf(88, 151)) for (j in 0..2) {
            flame(c, x - 4f + j * 5, 57f - j % 2 * 3, tick + j)
        }
        // Lustre suspendu : chaîne ancrée au plafond, oscillation lente autour de l'attache.
        c.save()
        c.rotate(sin(clock / 1900f) * 3f, 120f, 0f)
        for (j in 0..5) {
            rect(c, 119f + j % 2, j * 4f, 2f, 3f, 0xFF9A987F)
        }
        polygon(c, 0xFF887958, 102f, 34f, 119f, 23f, 121f, 23f, 138f, 34f,
            135f, 34f, 120f, 27f, 105f, 34f)
        rect(c, 101f, 35f, 38f, 3f, 0xFF554E44)
        rect(c, 103f, 35f, 34f, 1f, 0xFFB59D66)
        rect(c, 106f, 38f, 28f, 2f, 0xFF807255)
        rect(c, 118f, 40f, 4f, 4f, 0xFFB59D66)
        for (i in 0..4) {
            val x = 104f + i * 7
            val y = 30f - i % 2 * 2
            rect(c, x, y, 2f, 35f - y, 0xFFE0CDA2)
            flame(c, x, y - 1, tick + i)
        }
        c.restore()
        c.restore()
    }
}
