package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Rayonnages en cache ; grimoire aux pages animées et chandeliers au fond. */
internal object DungeonLibraryBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private val covers = longArrayOf(0xFF887092, 0xFF4E8580, 0xFFAD6965, 0xFFB39159, 0xFF58738E)
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Long) {
        paint.color = color.toInt()
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun poly(c: Canvas, color: Long, vararg p: Float) {
        path.reset(); path.moveTo(p[0], p[1])
        for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1])
        path.close(); paint.color = color.toInt(); c.drawPath(path, paint)
    }
    private fun upper(c: Canvas, floor: Float) {
        c.translate(0f, floor); c.scale(1f, (floor / 84f).coerceAtMost(1f)); c.translate(0f, -84f)
    }
    fun draw(c: Canvas, floor: Float, height: Float) {
        c.drawColor(0xFF302C43.toInt())
        c.save(); upper(c, floor)
        for (side in 0..1) {
            val start = if (side == 0) 5f else 157f
            rect(c, start, 5f, 78f, 78f, 0xFF4D3A3D)
            rect(c, start + 5, 10f, 68f, 70f, 0xFF252735)
            for (row in 0..3) {
                val y = 25f + row * 17
                for (i in 0..10) {
                    val x = start + 7 + i * 6
                    val h = 9f + (i * 3 + row * 7 + side) % 6
                    rect(c, x, y - h, 4f, h, covers[(i + row * 2 + side) % covers.size])
                    rect(c, x, y - h + 2, 4f, 1f, 0xFFCCB788)
                    rect(c, x + 1, y - 3, 2f, 1f, 0xFFCFBDA0)
                }
                rect(c, start + 3, y, 72f, 3f, 0xFF977455)
                rect(c, start + 3, y + 3, 72f, 2f, 0xFF48383B)
            }
            for (x in floatArrayOf(start, start + 74)) {
                rect(c, x, 4f, 4f, 79f, 0xFF6C5050)
                rect(c, x, 5f, 1f, 76f, 0xFFAD8561)
            }
            rect(c, start - 2, 3f, 82f, 4f, 0xFFB38B65)
            rect(c, start - 1, 80f, 80f, 4f, 0xFF755D50)
        }
        // Alcôve violette et vitrail géométrique derrière le pupitre.
        poly(c, 0xFF626080, 88f, 78f, 88f, 24f, 101f, 8f, 137f, 8f, 151f, 24f, 151f, 78f)
        poly(c, 0xFF343850, 93f, 78f, 93f, 26f, 104f, 13f, 134f, 13f, 146f, 26f, 146f, 78f)
        poly(c, 0xFF698C9C, 119f, 17f, 136f, 31f, 119f, 46f, 103f, 31f)
        poly(c, 0xFFA596BB, 119f, 22f, 130f, 31f, 119f, 40f, 109f, 31f)
        rect(c, 118f, 17f, 2f, 30f, 0xFFD1B885)
        rect(c, 103f, 30f, 33f, 2f, 0xFFD1B885)
        rect(c, 116f, 63f, 7f, 17f, 0xFF91755B)
        rect(c, 109f, 79f, 21f, 4f, 0xFFB4996E)
        poly(c, 0xFFB79A73, 101f, 60f, 137f, 60f, 141f, 65f, 97f, 65f)
        rect(c, 99f, 65f, 40f, 2f, 0xFF614A49)
        // Échelle appuyée contre la bibliothèque de gauche.
        for (x in intArrayOf(17, 31)) poly(c, 0xFFA38863,
            x.toFloat(), 80f, x + 8f, 34f, x + 11f, 34f, x + 3f, 80f)
        for (j in 0..5) rect(c, 20f + j * 1.4f, 75f - j * 7, 14f, 2f, 0xFFC2A57B)
        for (x in intArrayOf(91, 148)) {
            rect(c, x - 4f, 80f, 10f, 3f, 0xFFB59A64)
            rect(c, x.toFloat(), 62f, 2f, 18f, 0xFF967953)
            rect(c, x - 5f, 62f, 12f, 2f, 0xFFC9AC75)
            for (j in 0..2) rect(c, x - 4f + j * 4, 55f, 2f, 7f, 0xFFE2D5AD)
        }
        c.restore()
        // Parquet sobre ; les livres au sol restent le long des murs.
        rect(c, 0f, floor + 2, 240f, height, 0xFF4D4044)
        for (row in 0..((height - floor) / 12).toInt()) for (col in -1..4) {
            val x = col * 59f + row % 2 * 29
            val y = floor + 4 + row * 12
            rect(c, x, y, 57f, 10f, if ((row + col) % 3 == 0) 0xFF66534F else 0xFF5C4B49)
            rect(c, x + 3, y + 2, 39f, 1f, 0xFF715C53)
        }
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            for (pile in 0..2) for (j in 0..3) {
                val y = floor + 28 + pile * (height - floor - 38) / 3 - j * 4
                val x = 1f + j % 2 * 2
                rect(c, x, y, 13f - j % 2 * 2, 4f, covers[(pile + j + side) % covers.size])
                rect(c, x + 1, y + 1, 10f - j % 2 * 2, 2f, 0xFFBEB69D)
            }
            c.restore()
        }
    }
    fun atmosphere(c: Canvas, floor: Float, clock: Float) {
        c.save(); upper(c, floor)
        val tick = (clock / 140).toInt()
        for (x in intArrayOf(91, 148)) for (j in 0..2) {
            val fx = x - 4f + j * 4
            rect(c, fx - 2, 48f, 6f, 9f, 0x20EAC183)
            rect(c, fx, 50f - (tick + j) % 2, 2f, 5f, 0xFFE5B96B)
            rect(c, fx, 53f, 1f, 2f, 0xFFFFE7AD)
        }
        // Livre ouvert : couverture fixe, une page se soulève puis traverse la reliure.
        poly(c, 0xFF665078, 99f, 53f, 118f, 56f, 120f, 56f, 140f, 53f,
            140f, 62f, 120f, 65f, 98f, 62f)
        poly(c, 0xFFDFD7B1, 101f, 51f, 119f, 54f, 119f, 62f, 101f, 59f)
        poly(c, 0xFFC2CBAE, 120f, 54f, 137f, 51f, 137f, 59f, 120f, 62f)
        for (i in 0..2) {
            rect(c, 104f, 54f + i * 2, 10f, 1f, 0xFF929584)
            rect(c, 125f, 54f + i * 2, 9f, 1f, 0xFF899C93)
        }
        val phase = (clock % 4200f) / 4200f
        if (phase < .65f) {
            val turn = phase / .65f
            val edge = 137f - 36f * turn
            val lift = sin(turn * Math.PI).toFloat() * 12
            poly(c, 0xFFE9E2BC, 119f, 54f, edge, 51f - lift,
                edge, 59f - lift, 119f, 62f)
        }
        rect(c, 119f, 54f, 1f, 9f, 0xFF9A846B)
        c.restore()
    }
}
