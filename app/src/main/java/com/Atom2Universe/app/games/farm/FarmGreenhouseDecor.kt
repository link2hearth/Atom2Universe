package com.Atom2Universe.app.games.farm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Decor in the same world coordinates as the existing flower beds. */
internal class FarmGreenhouseDecor {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Long, radius: Float = 0f) {
        paint.color = color.toInt()
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    private fun line(c: Canvas, x: Float, y: Float, ex: Float, ey: Float, color: Long, width: Float = 2f) {
        paint.color = color.toInt()
        paint.strokeWidth = width
        c.drawLine(x, y, ex, ey, paint)
    }

    fun draw(c: Canvas) {
        box(c, 39f, 47f, 882f, 1584f, 0x44334226, 20f)
        box(c, 25f, 25f, 875f, 1570f, 0xFF777E62, 18f)
        box(c, 33f, 30f, 867f, 1559f, 0xFFE1D7AF, 14f)
        box(c, 73f, 93f, 827f, 1531f, 0xFFC9BC91, 5f)
        // Deterministic gravel: no randomness or allocations during drawing.
        for (i in 0 until 1500) {
            paint.color = if (i % 3 == 0) 0x40938360 else 0x60F3E6BB
            c.drawCircle(78f + (i * 137 % 742), 98f + (i * 227 % 1426),
                if (i % 4 == 0) 1.8f else 0.9f, paint)
        }
        box(c, 385f, 318f, 515f, 1320f, 0xFFAA9976, 6f)
        for (row in 0..16) for (col in 0..1) {
            val x = 391f + col * 62f
            val y = 324f + row * 58f
            box(c, x, y + 3f, x + 57f, y + 54f, 0xFF998969, 5f)
            box(c, x, y, x + 57f, y + 50f,
                if ((row + col) % 3 == 0) 0xFFDED1AC else 0xFFECE0BD, 5f)
            line(c, x + 6f, y + 4f, x + 49f, y + 4f, 0x80FFF7DB)
        }
        // Glazing on the perimeter, instead of a grid across the floor.
        for (side in 0..1) {
            val x = if (side == 0) 39f else 831f
            for (row in 0..11) {
                val y = 94f + row * 120f
                box(c, x, y, x + 30f, y + 114f, 0xFF91BDB2, 3f)
                box(c, x + 4f, y + 4f, x + 26f, y + 109f, 0xFFC3DDD0, 2f)
                line(c, x + 6f, y + 70f, x + 24f, y + 27f, 0xA0F5FFF0, 4f)
                box(c, x - 4f, y + 111f, x + 34f, y + 119f, 0xFF527A68, 2f)
            }
            box(c, x - 5f, 30f, x + 4f, 1553f, 0xFF466E5C, 3f)
            line(c, x - 2f, 32f, x - 2f, 1550f, 0xFFA6BE94)
        }
        for (col in 0..7) {
            val x = 76f + col * 94f
            box(c, x, 39f, x + 87f, 84f, 0xFF8FB8AE, 3f)
            box(c, x + 4f, 42f, x + 83f, 76f, 0xFFC8DFD0, 2f)
            line(c, x + 12f, 71f, x + 41f, 45f, 0xB0F5FFF0, 5f)
        }
        box(c, 34f, 83f, 867f, 94f, 0xFF527A68, 3f)
        c.save()
        c.clipRect(76f, 96f, 824f, 1525f)
        for (i in 0..5) {
            line(c, 80f + i * 170f, 100f, -160f + i * 170f, 1530f, 0x16FFF8C9, 46f)
        }
        c.restore()
    }

    fun drawPlanter(c: Canvas, bed: RectF) {
        val l = bed.left
        val t = bed.top
        val r = bed.right
        val b = bed.bottom
        box(c, l + 7f, t + 10f, r + 9f, b + 16f, 0x353E3825, 10f)
        box(c, l, t, r, b + 7f, 0xFF684932, 8f)
        box(c, l + 3f, t + 3f, r - 3f, b - 2f, 0xFFB18450, 6f)
        box(c, l + 14f, t + 15f, r - 14f, b - 16f, 0xFF513B29, 4f)
        box(c, l + 18f, t + 23f, r - 18f, b - 20f, 0xFF685035, 3f)
        for (i in 0 until (bed.width() * bed.height() / 650f).toInt()) {
            val x = l + 21f + (i * 37 % (bed.width() - 42f).toInt())
            val y = t + 26f + (i * 53 % (bed.height() - 50f).toInt())
            paint.color = if (i % 2 == 0) 0xFF806040.toInt() else 0xFF493829.toInt()
            c.drawOval(x, y, x + 3f, y + 2f, paint)
        }
        line(c, l + 9f, t + 6f, r - 9f, t + 6f, 0xFFD5AE71, 3f)
        line(c, l + 18f, b - 8f, r - 18f, b - 8f, 0xFF88603C)
        for (side in 0..1) for (end in 0..1) {
            val x = if (side == 0) l + 4f else r - 15f
            val y = if (end == 0) t + 4f else b - 24f
            box(c, x, y, x + 11f, y + 23f, 0xFF697263, 2f)
            paint.color = 0xFFCCD0AA.toInt()
            c.drawCircle(x + 5.5f, y + 5f, 1.6f, paint)
            c.drawCircle(x + 5.5f, y + 18f, 1.6f, paint)
        }
    }

    fun drawEntrance(c: Canvas) {
        box(c, 337f, 1524f, 563f, 1600f, 0xFF7B8167, 5f)
        box(c, 348f, 1524f, 552f, 1600f, 0xFFE4D6AD, 4f)
        for (row in 0..2) line(c, 351f, 1537f + row * 25f, 549f, 1537f + row * 25f, 0xFFB4A27D, 3f)
        for (side in 0..1) {
            val x = if (side == 0) 330f else 554f
            box(c, x, 1516f, x + 16f, 1582f, 0xFF446B58, 3f)
            box(c, x + 3f, 1517f, x + 7f, 1579f, 0xFFADC29A, 2f)
        }
    }
}
