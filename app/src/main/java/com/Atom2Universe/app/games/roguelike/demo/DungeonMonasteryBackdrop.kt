package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Cour enneigée et cloître en cache ; cloche articulée dans le campanile. */
internal object DungeonMonasteryBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private fun tone(night: Boolean, day: Long, dark: Long) = if (night) dark else day
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Long) {
        paint.color = color.toInt(); c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun poly(c: Canvas, color: Long, vararg p: Float) {
        path.reset(); path.moveTo(p[0], p[1])
        for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1])
        path.close(); paint.color = color.toInt(); c.drawPath(path, paint)
    }
    private fun upper(c: Canvas, floor: Float) {
        c.translate(0f, floor); c.scale(1f, (floor / 84f).coerceAtMost(1f)); c.translate(0f, -84f)
    }
    fun draw(c: Canvas, floor: Float, height: Float, night: Boolean) {
        val snow = tone(night, 0xFFE0E5DB, 0xFF9CAFC0)
        val stone = tone(night, 0xFF89999D, 0xFF52677D)
        val light = tone(night, 0xFFBDC7C3, 0xFF8297A8)
        val dark = tone(night, 0xFF536D7B, 0xFF2C415D)
        c.drawColor(tone(night, 0xFFA5C5CE, 0xFF23314F).toInt())
        c.save(); upper(c, floor)
        if (night) {
            for (i in 0..22) rect(c, (i * 47 % 238).toFloat(), 3f + i * 13 % 32, 1f, 1f, 0xFFB8CDD9)
            rect(c, 181f, 9f, 12f, 12f, 0xFFE2E5CC)
            rect(c, 186f, 7f, 9f, 11f, 0xFF23314F)
        } else rect(c, 181f, 9f, 13f, 13f, 0xFFF2E4B8)
        poly(c, tone(night, 0xFF799BAE, 0xFF3F5775), 0f, 62f, 34f, 18f, 73f, 59f,
            96f, 32f, 135f, 65f, 179f, 24f, 220f, 62f, 240f, 42f, 240f, 84f, 0f, 84f)
        poly(c, snow, 19f, 38f, 34f, 18f, 53f, 39f, 40f, 34f, 34f, 39f, 29f, 32f)
        poly(c, snow, 166f, 38f, 179f, 24f, 197f, 44f, 182f, 38f, 177f, 43f)
        // Arcades latérales, sous une corniche chargée de neige.
        rect(c, 0f, 49f, 240f, 35f, stone)
        for (x in intArrayOf(8, 43, 78, 149, 184, 219)) {
            poly(c, dark, x.toFloat(), 84f, x.toFloat(), 65f, x + 5f, 58f,
                x + 15f, 58f, x + 20f, 65f, x + 20f, 84f)
            rect(c, x - 3f, 62f, 3f, 22f, light)
            rect(c, x + 20f, 62f, 3f, 22f, light)
        }
        rect(c, 0f, 46f, 240f, 5f, dark)
        rect(c, 0f, 44f, 240f, 4f, snow)
        for (i in 0..17) rect(c, i * 14f + 2, 48f, 2f, 2f + i % 4, snow)
        // Tour centrale : ouverture sombre réservée à la cloche animée.
        rect(c, 103f, 20f, 35f, 64f, stone)
        rect(c, 104f, 21f, 4f, 62f, light)
        rect(c, 132f, 21f, 6f, 63f, dark)
        poly(c, dark, 108f, 47f, 108f, 29f, 113f, 23f, 127f, 23f, 132f, 29f, 132f, 47f)
        poly(c, tone(night, 0xFF5D6C80, 0xFF33445F), 97f, 22f, 120f, 5f, 144f, 22f)
        poly(c, snow, 96f, 21f, 120f, 3f, 145f, 21f, 135f, 19f, 120f, 9f, 105f, 20f)
        rect(c, 101f, 48f, 39f, 4f, light)
        poly(c, dark, 110f, 84f, 110f, 64f, 116f, 57f, 124f, 57f, 130f, 64f, 130f, 84f)
        rect(c, 113f, 66f, 14f, 18f, tone(night, 0xFF6A5D56, 0xFF414453))
        rect(c, 119f, 66f, 1f, 18f, 0xFF293947)
        for (x in intArrayOf(94, 145)) {
            rect(c, x - 2f, 64f, 6f, 10f, dark)
            rect(c, x - 1f, 66f, 4f, 6f, tone(night, 0xFFBFAC7F, 0xFFE0B774))
            rect(c, x - 3f, 62f, 8f, 3f, snow)
        }
        c.restore()
        rect(c, 0f, floor + 2, 240f, height, tone(night, 0xFFA8B9BB, 0xFF5E7489))
        for (row in 0..((height - floor) / 16).toInt()) for (col in -1..6) {
            val x = col * 41f + row % 2 * 20
            val y = floor + 4 + row * 16
            rect(c, x, y, 39f, 14f, tone(night, 0xFFB8C5C3, 0xFF6B8093))
            rect(c, x + 1, y, 37f, 1f, tone(night, 0xFFCFD7CE, 0xFF7C92A1))
        }
        // Congères et bancs le long du cloître, sans mobilier au centre.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            for (i in 0..5) {
                val y = floor + 10 + i * (height - floor) / 6
                poly(c, snow, 0f, y - 5, 9f, y - 8, 19f, y - 3, 23f, y + 4,
                    12f, y + 8, 0f, y + 12)
            }
            rect(c, 2f, floor + 33, 16f, 6f, dark)
            rect(c, 3f, floor + 39, 3f, 9f, stone)
            rect(c, 13f, floor + 39, 3f, 9f, stone)
            rect(c, 1f, floor + 31, 18f, 3f, snow)
            c.restore()
        }
    }
    fun atmosphere(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        c.save(); upper(c, floor)
        c.save(); c.rotate(sin(clock / 950f) * 12f, 120f, 27f)
        rect(c, 119f, 26f, 2f, 5f, 0xFF967A52)
        poly(c, tone(night, 0xFFB2965E, 0xFFA99267), 114f, 31f, 125f, 31f,
            128f, 41f, 131f, 43f, 109f, 43f, 112f, 40f)
        rect(c, 114f, 32f, 3f, 9f, 0xFFE0C48B)
        rect(c, 110f, 43f, 20f, 2f, 0xFF7D694D)
        c.restore()
        c.save(); c.rotate(-sin(clock / 950f + .5f) * 17f, 120f, 32f)
        rect(c, 119f, 34f, 2f, 10f, 0xFF574C45)
        rect(c, 118f, 44f, 4f, 3f, 0xFFCFB17A)
        c.restore()
        if (night) for (x in intArrayOf(94, 145)) {
            rect(c, x - 5f, 63f, 12f, 12f, 0x20F3C582)
            rect(c, x.toFloat(), 67f + (clock / 160).toInt() % 2, 2f, 4f, 0xFFFFD898)
        }
        // Quelques flocons dans la partie haute seulement.
        for (i in 0..17) {
            val y = (i * 17 + clock / 160f) % 80
            val x = (i * 47 + sin(clock / 1300f + i) * 3 + 240) % 240
            rect(c, x, y, 1f, 1f, tone(night, 0xFFDDE8E2, 0xFFA8BDCF))
        }
        c.restore()
    }
}
