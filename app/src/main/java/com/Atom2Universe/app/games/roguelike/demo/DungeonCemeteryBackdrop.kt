package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin
import kotlin.math.roundToInt

/** Pierres fixes en cache ; corbeau articulé, lanternes et brume au fond de la scène. */
internal object DungeonCemeteryBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private fun tone(night: Boolean, day: Long, dark: Long) = (if (night) dark else day).toInt()
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    fun draw(c: Canvas, floor: Float, height: Float, night: Boolean) {
        val sky = tone(night, 0xFFADBFC0, 0xFF202A48)
        c.drawColor(sky)
        rect(c, 0f, floor * .45f, 240f, floor * .55f, tone(night, 0xFFCCD2BF, 0xFF3B4563))
        if (night) {
            for (i in 0..21) rect(c, (i * 47 % 237 + 1).toFloat(),
                2f + i * 11 % (floor * .42f).toInt().coerceAtLeast(1), 1f, 1f, 0xFFAFBDD5.toInt())
            polygon(c, 0xFFE1E4CF.toInt(), 177f, 10f, 188f, 10f, 192f, 14f,
                192f, 24f, 188f, 28f, 177f, 28f, 173f, 24f, 173f, 14f)
            rect(c, 178f, 14f, 4f, 3f, 0xFFBDC9C6.toInt())
            rect(c, 185f, 21f, 3f, 4f, 0xFFCBD2C6.toInt())
        } else {
            rect(c, 178f, 14f, 13f, 13f, 0xFFE7DDB5.toInt())
        }
        for (i in 0..3) {
            rect(c, 9f + i * 65, 19f + i % 2 * 8, 35f, 3f, tone(night, 0xFFDDE0CE, 0xFF46506B))
        }
        polygon(c, tone(night, 0xFF8B9D90, 0xFF394B59), 0f, floor - 13,
            39f, floor - 23, 80f, floor - 13, 147f, floor - 27, 203f, floor - 14,
            240f, floor - 24, 240f, floor + 3, 0f, floor + 3)
        // Cyprès lointains : silhouettes en étages, sans cacher le mausolée.
        for (i in 0..7) {
            val x = if (i < 4) 3f + i * 22 else 163f + (i - 4) * 23
            val top = floor - 25 - i % 3 * 7
            polygon(c, tone(night, 0xFF587A70, 0xFF2A414B), x, top,
                x + 4, top + 8, x + 3, top + 8, x + 7, top + 18,
                x + 5, top + 18, x + 9, floor, x - 8, floor,
                x - 5, top + 18, x - 6, top + 18, x - 3, top + 8, x - 4, top + 8)
        }
        rect(c, 0f, floor, 240f, height, tone(night, 0xFF7D9180, 0xFF374C52))
        for (i in 0..150) {
            val x = (i * 61 % 237).toFloat()
            val y = floor + 6 + i * 31 % (height - floor - 8).toInt().coerceAtLeast(1)
            rect(c, x, y, 3f, 1f, tone(night, 0xFF899B88, 0xFF41565B))
        }
        // Grilles basses derrière les combattants, interrompues par le mausolée.
        val iron = tone(night, 0xFF44545A, 0xFF263442)
        for (side in 0..1) {
            val start = if (side == 0) 0f else 154f
            rect(c, start, floor - 12, 86f, 2f, iron)
            rect(c, start, floor - 3, 86f, 2f, iron)
            for (i in 0..10) {
                val x = start + i * 8
                rect(c, x, floor - 18, 1f, 22f, iron)
                polygon(c, iron, x - 2, floor - 17, x + .5f, floor - 22, x + 3, floor - 17)
            }
        }
        // Réserve la silhouette entière du toit et de son oiseau sur les écrans peu hauts.
        c.save()
        c.translate(0f, floor)
        c.scale(1f, (floor / 84f).coerceAtMost(1f))
        c.translate(0f, -floor)
        val stone = tone(night, 0xFF9FA7A0, 0xFF617183)
        val shade = tone(night, 0xFF74858A, 0xFF425668)
        val light = tone(night, 0xFFC6C8AE, 0xFF8E9DA6)
        rect(c, 91f, floor - 32, 57f, 35f, stone)
        rect(c, 137f, floor - 32, 11f, 35f, shade)
        polygon(c, shade, 86f, floor - 31, 119f, floor - 48, 153f, floor - 31)
        polygon(c, light, 91f, floor - 33, 119f, floor - 46, 148f, floor - 33,
            141f, floor - 33, 119f, floor - 42, 98f, floor - 33)
        rect(c, 89f, floor - 32, 61f, 3f, light)
        rect(c, 106f, floor - 22, 26f, 25f, shade)
        polygon(c, tone(night, 0xFF384B50, 0xFF1D2C40), 110f, floor + 3,
            110f, floor - 17, 114f, floor - 23, 124f, floor - 23,
            128f, floor - 17, 128f, floor + 3)
        for (x in intArrayOf(96, 135)) {
            rect(c, x.toFloat(), floor - 28, 5f, 30f, light)
            rect(c, x + 4f, floor - 26, 2f, 28f, shade)
            rect(c, x - 2f, floor, 9f, 3f, stone)
        }
        rect(c, 86f, floor + 3, 67f, 3f, shade)
        rect(c, 82f, floor + 6, 75f, 3f, stone)
        rect(c, 82f, floor + 6, 75f, 1f, light)
        // Socle du corbeau, nettement au-dessus du combat.
        rect(c, 117f, floor - 56, 4f, 12f, stone)
        rect(c, 113f, floor - 53, 12f, 3f, light)
        c.restore()
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            // La végétation précède les pierres pour rester derrière les sépultures.
            for (i in 0..9) {
                val y = floor + 18 + i * (height - floor - 22) / 10
                rect(c, 1f + i % 3 * 4, y, 2f, 4f, tone(night, 0xFF456F60, 0xFF254649))
                if (i % 3 == 0) {
                    rect(c, 4f + i % 2 * 4, y - 2, 3f, 2f, tone(night, 0xFFCAA7BC, 0xFF8A8AAA))
                }
            }
            grave(c, 25f, floor + 6, night, false)
            grave(c, 54f, floor - 1, night, true)
            grave(c, 5f, floor + 50, night, true)
            grave(c, 2f, height - 16, night, false)
            c.restore()
        }
        for (x in intArrayOf(80, 159)) {
            rect(c, x - 2f, floor - 18, 6f, 18f, shade)
            rect(c, x - 3f, floor - 20, 8f, 3f, light)
            rect(c, x - 2f, floor - 28, 6f, 8f, iron)
            rect(c, x - 3f, floor - 30, 8f, 2f, iron)
            rect(c, x.toFloat(), floor - 33, 2f, 3f, iron)
            rect(c, x - 1f, floor - 27, 4f, 5f, tone(night, 0xFFC1B793, 0xFFBF935E))
        }
    }

    private fun grave(c: Canvas, x: Float, y: Float, night: Boolean, cross: Boolean) {
        val stone = tone(night, 0xFFB5BBB0, 0xFF7D8D9A)
        val shade = tone(night, 0xFF75888B, 0xFF455F71)
        rect(c, x - 2, y, 19f, 3f, shade)
        if (cross) {
            rect(c, x + 5, y - 23, 5f, 23f, stone)
            rect(c, x, y - 18, 16f, 5f, stone)
            rect(c, x + 9, y - 13, 2f, 13f, shade)
        } else {
            polygon(c, stone, x, y, x, y - 15, x + 4, y - 20,
                x + 11, y - 20, x + 15, y - 15, x + 15, y)
            rect(c, x + 12, y - 14, 3f, 14f, shade)
            rect(c, x + 4, y - 12, 6f, 1f, shade)
            rect(c, x + 3, y - 8, 7f, 1f, shade)
            rect(c, x + 5, y - 5, 4f, 1f, shade)
        }
        rect(c, x + 1, y - 2, 6f, 3f, tone(night, 0xFF668C6C, 0xFF42685D))
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        val tick = (clock / 160f).toInt()
        // Brume fine au fond uniquement ; elle ne voile pas les sprites ni le sol de combat.
        for (i in 0..3) {
            val drift = (sin(clock / 2600f + i) * 9).roundToInt()
            rect(c, 10f + i * 58 + drift, floor - 4 + i % 2 * 6f, 35f, 2f,
                if (night) 0x357FA5B9 else 0x30D2DCCC)
            rect(c, 18f + i * 58 + drift, floor - 5 + i % 2 * 6f, 17f, 1f,
                if (night) 0x257FA5B9 else 0x20D2DCCC)
        }
        if (night) for (x in intArrayOf(80, 159)) {
            rect(c, x - 6f, floor - 31, 14f, 13f, 0x20EAC17A)
            rect(c, x.toFloat(), floor - 26 - tick % 2, 2f, 4f, 0xFFE9BD73.toInt())
            rect(c, x.toFloat(), floor - 24, 1f, 2f, 0xFFFFE4AB.toInt())
        }
        // Corps et pattes stables ; tête, bec, aile et queue ont leurs propres poses.
        c.save()
        c.translate(0f, floor)
        c.scale(1f, (floor / 84f).coerceAtMost(1f))
        c.translate(0f, -floor)
        val x = 117f
        val y = floor - 57
        val ink = 0xFF192635.toInt()
        val feather = tone(night, 0xFF3F5265, 0xFF586D88)
        rect(c, x - 1, y - 1, 1f, 3f, ink)
        rect(c, x + 3, y - 1, 1f, 3f, ink)
        polygon(c, ink, x - 5, y - 6, x - 2, y - 11, x + 5, y - 10,
            x + 7, y - 5, x + 3, y, x - 2, y)
        val tail = if (tick % 19 in 12..14) 2f else 0f
        polygon(c, feather, x - 3, y - 5, x - 10, y - 4 - tail,
            x - 8, y - 1 - tail, x - 2, y - 1)
        val head = if (tick % 27 in 18..21) 1f else 0f
        rect(c, x + 3, y - 14 + head, 6f, 6f, ink)
        rect(c, x + 4, y - 14 + head, 3f, 1f, feather)
        rect(c, x + 9, y - 11 + head, 3f, 2f, tone(night, 0xFF8E947E, 0xFF9AA896))
        if (tick % 23 != 0) rect(c, x + 7, y - 12 + head, 1f, 1f, 0xFFE2D6AA.toInt())
        if (tick % 31 in 22..24) {
            polygon(c, feather, x - 2, y - 9, x - 9, y - 15, x - 7, y - 8, x + 2, y - 3)
        } else {
            polygon(c, feather, x - 3, y - 9, x + 3, y - 8, x + 1, y - 3, x - 4, y - 2)
            rect(c, x - 2, y - 6, 3f, 1f, tone(night, 0xFF65768A, 0xFF8193AA))
        }
        c.restore()
    }
}
