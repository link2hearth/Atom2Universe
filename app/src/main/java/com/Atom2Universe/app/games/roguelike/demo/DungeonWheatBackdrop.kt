package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Champ moissonné : sol calme au centre, blé sur les marges, moulin vivant au fond. */
internal object DungeonWheatBackdrop {
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
        val sky = tone(night, 0xFF8DBBC6, 0xFF192940)
        c.drawColor(sky)
        rect(c, 0f, floor * .45f, 240f, floor * .55f, tone(night, 0xFFC7D4BD, 0xFF30445A))
        if (night) {
            for (i in 0..25) rect(c, (i * 47 % 236 + 2).toFloat(),
                (i * 13 % (floor * .55f).toInt().coerceAtLeast(1)).toFloat(), 1f, 1f, 0xFFB5CCD3.toInt())
            rect(c, 35f, 12f, 11f, 11f, 0xFFE8E3B8.toInt())
            rect(c, 39f, 10f, 10f, 10f, sky)
        } else {
            rect(c, 34f, 13f, 13f, 13f, 0xFFF8E0A0.toInt())
            for (i in 0..2) {
                val x = 8f + i * 81
                rect(c, x, 8f + i * 6, 29f, 4f, 0xFFE5E5CB.toInt())
                rect(c, x + 7, 5f + i * 6, 16f, 4f, 0xFFE5E5CB.toInt())
            }
        }
        polygon(c, tone(night, 0xFF819B75, 0xFF344C53),
            0f, floor - 16, 30f, floor - 24, 75f, floor - 12, 119f, floor - 22,
            172f, floor - 16, 218f, floor - 28, 240f, floor - 20, 240f, floor + 4, 0f, floor + 4)
        rect(c, 0f, floor - 9, 240f, 18f, tone(night, 0xFFC0A359, 0xFF686951))
        // Bande d'épis lointains ; aucune grande tige dans les emplacements des acteurs.
        for (i in 0..79) {
            val x = i * 3f
            val y = floor - 10 + i * 7 % 5
            rect(c, x, y, 1f, 10f, tone(night, 0xFF9E793E, 0xFF494F46))
            rect(c, x - 1, y, 3f, 3f, tone(night, 0xFFE5C578, 0xFF8A8C69))
        }
        rect(c, 0f, floor + 7, 240f, height, tone(night, 0xFFB39C68, 0xFF495351))
        for (i in 0..159) {
            val x = (i * 67 % 238).toFloat()
            val y = floor + 12 + i * 31 % (height - floor - 13).toInt().coerceAtLeast(1)
            rect(c, x, y, 2f + i % 3, 1f, tone(night, 0xFFC0AA76, 0xFF55605C))
        }
        // Meules et clôtures restent derrière la ligne de combat.
        for (x in intArrayOf(24, 199)) {
            polygon(c, tone(night, 0xFFAA773F, 0xFF535342), x - 12f, floor + 4,
                x - 10f, floor - 10, x - 5f, floor - 18, x + 4f, floor - 20,
                x + 11f, floor - 11, x + 14f, floor + 4)
            for (j in 0..4) rect(c, x - 7f + j * 4, floor - 9 + j % 2 * 3,
                2f, 11f - j % 2 * 3, tone(night, 0xFFE0B866, 0xFF858266))
            rect(c, x - 11f, floor - 3, 24f, 2f, tone(night, 0xFF846442, 0xFF394441))
        }
        for (side in 0..1) {
            val x = if (side == 0) 0f else 213f
            rect(c, x, floor + 3, 27f, 2f, tone(night, 0xFF856B4D, 0xFF354347))
            for (j in 0..2) rect(c, x + j * 12, floor - 3, 2f, 13f, tone(night, 0xFFAA8B5D, 0xFF66726A))
            for (i in 0..((height - floor) / 6).toInt()) {
                val y = floor + 18 + i * 6
                for (j in 0..3) ear(c, if (side == 0) j * 4f else 239f - j * 4,
                    y + j % 2 * 3, night, 0f)
            }
        }
        // Tour en pierre, toit de tuiles et fenêtre chaude en version nocturne.
        polygon(c, tone(night, 0xFFB6A685, 0xFF65716F), 104f, floor + 4,
            109f, floor - 39, 128f, floor - 39, 134f, floor + 4)
        polygon(c, tone(night, 0xFFD9C6A1, 0xFF84918A), 109f, floor - 39,
            118f, floor - 39, 118f, floor + 4, 104f, floor + 4)
        for (j in 0..5) rect(c, 110f + j % 2 * 7, floor - 31 + j * 6, 6f, 1f,
            tone(night, 0xFF9D9077, 0xFF536363))
        polygon(c, tone(night, 0xFF925E49, 0xFF3F424E), 104f, floor - 38,
            118f, floor - 51, 132f, floor - 38)
        rect(c, 111f, floor - 40, 18f, 2f, tone(night, 0xFFC48B5D, 0xFF667077))
        rect(c, 115f, floor - 8, 7f, 12f, tone(night, 0xFF625747, 0xFF29383E))
        rect(c, 122f, floor - 22, 5f, 7f, tone(night, 0xFF526970, 0xFFE6B968))
        rect(c, 124f, floor - 22, 1f, 7f, tone(night, 0xFFAC9872, 0xFF94734E))
    }

    private fun ear(c: Canvas, x: Float, y: Float, night: Boolean, bend: Float) {
        val stem = tone(night, 0xFF8D793F, 0xFF4F6055)
        val grain = tone(night, 0xFFE1BD66, 0xFF939B77)
        rect(c, x, y - 6, 1f, 7f, stem)
        rect(c, x + bend, y - 12, 1f, 7f, stem)
        for (j in 0..2) rect(c, x + bend - 1 + j % 2, y - 12 + j * 2, 3f, 2f, grain)
        rect(c, x + bend, y - 15, 1f, 3f, grain)
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        // Rotation quantifiée : silhouettes nettes, sans allocations ni recalcul du décor fixe.
        c.save()
        c.translate(118f, floor - 32)
        c.rotate((clock / 120f).toInt() * 2f % 360f)
        for (i in 0..3) {
            c.rotate(90f)
            rect(c, -1f, -26f, 2f, 27f, tone(night, 0xFF69533E, 0xFF35444C))
            rect(c, 1f, -25f, 7f, 18f, tone(night, 0xFFF0DBA8, 0xFFA0AEA0))
            rect(c, 7f, -25f, 1f, 18f, tone(night, 0xFFA78B5D, 0xFF657972))
            for (j in 0..3) rect(c, 1f, -24f + j * 5, 7f, 1f,
                tone(night, 0xFFC1A474, 0xFF72877E))
        }
        c.restore()
        rect(c, 116f, floor - 34, 5f, 5f, tone(night, 0xFF786044, 0xFF43565A))
        rect(c, 117f, floor - 33, 2f, 2f, tone(night, 0xFFD9BA7C, 0xFFA2B2A1))
        for (i in 0..11) {
            val x = if (i < 6) i * 4f + 2 else 238f - (i - 6) * 4
            val bend = sin(clock / 650f + i * .6f).let { if (it > .3f) 1f else if (it < -.3f) -1f else 0f }
            ear(c, x, floor + 10, night, bend)
        }
    }
}
