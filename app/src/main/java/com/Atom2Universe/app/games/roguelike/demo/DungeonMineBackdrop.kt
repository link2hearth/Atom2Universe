package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Roche et matériel en cache ; lanterne suspendue et eau dans la galerie arrière. */
internal object DungeonMineBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
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
        c.drawColor(0xFF303942.toInt())
        c.save(); upper(c, floor)
        for (row in 0..5) for (col in -1..8) {
            val x = col * 31f + row % 2 * 15
            val y = row * 16f
            poly(c, if ((row + col) % 3 == 0) 0xFF46545B else 0xFF3B484E,
                x, y + 4, x + 8, y, x + 27, y + 2, x + 30, y + 12,
                x + 19, y + 16, x + 3, y + 13)
            rect(c, x + 9, y + 2, 13f, 1f, 0xFF536165)
        }
        // Galerie profonde et deux portiques imbriqués.
        poly(c, 0xFF15232D, 84f, 84f, 88f, 27f, 101f, 17f, 144f, 17f,
            160f, 30f, 165f, 84f)
        for (i in 0..1) {
            val left = 90f + i * 14
            val right = 152f - i * 12
            val top = 26f + i * 18
            val color = if (i == 0) 0xFF715D4B else 0xFF3C4240
            rect(c, left, top, 5f, 84f - top, color)
            rect(c, right, top, 5f, 84f - top, color)
            rect(c, left - 2, top, right - left + 9, 5f, color)
        }
        for (y in 0..4) rect(c, 109f - y * 3, 61f + y * 5, 27f + y * 6, 2f, 0xFF5A5547)
        poly(c, 0xFF7F9395, 117f, 59f, 119f, 59f, 107f, 84f, 103f, 84f)
        poly(c, 0xFF617B84, 130f, 59f, 132f, 59f, 148f, 84f, 144f, 84f)
        // Gros bois de soutènement, renforts métalliques et chevilles.
        for (x in intArrayOf(15, 218)) {
            rect(c, x.toFloat(), 5f, 9f, 79f, 0xFF6D5546)
            rect(c, x + 1f, 6f, 2f, 77f, 0xFFAD8559)
            for (j in 0..4) rect(c, x + 5f, 15f + j * 14, 1f, 9f, 0xFF473F39)
            for (y in intArrayOf(20, 65)) {
                rect(c, x - 1f, y.toFloat(), 11f, 4f, 0xFF4C626B)
                rect(c, x + 2f, y + 1f, 1f, 1f, 0xFFB1B8A2)
                rect(c, x + 7f, y + 1f, 1f, 1f, 0xFFB1B8A2)
            }
        }
        rect(c, 9f, 5f, 224f, 10f, 0xFF725943)
        rect(c, 10f, 5f, 222f, 2f, 0xFFA08159)
        for (i in 0..8) rect(c, 21f + i * 24, 10f, 14f, 1f, 0xFF4A423A)
        poly(c, 0xFF917251, 24f, 17f, 52f, 17f, 24f, 43f, 24f, 34f, 42f, 17f)
        poly(c, 0xFF917251, 218f, 17f, 190f, 17f, 218f, 43f, 218f, 34f, 200f, 17f)
        // Wagonnet abandonné, roues et minerai à gauche de la galerie.
        poly(c, 0xFF25343C, 34f, 64f, 76f, 64f, 71f, 79f, 40f, 79f)
        poly(c, 0xFF78848A, 35f, 62f, 74f, 62f, 69f, 75f, 40f, 75f)
        rect(c, 33f, 61f, 44f, 3f, 0xFFA1A89B)
        for (i in 0..4) {
            poly(c, if (i % 2 == 0) 0xFF4C636B else 0xFF657878,
                36f + i * 7, 61f, 40f + i * 7, 54f - i % 2 * 3,
                46f + i * 7, 61f)
        }
        for (x in intArrayOf(43, 65)) {
            rect(c, x - 3f, 76f, 8f, 7f, 0xFF26343C)
            rect(c, x - 1f, 78f, 4f, 3f, 0xFF8B9896)
        }
        rect(c, 42f, 68f, 3f, 2f, 0xFFBDC3B1)
        rect(c, 64f, 68f, 3f, 2f, 0xFFBDC3B1)
        // Eau sous une fissure, à droite du tunnel.
        poly(c, 0xFF162D39, 179f, 28f, 182f, 38f, 178f, 43f, 180f, 49f,
            177f, 44f, 180f, 37f)
        rect(c, 169f, 80f, 26f, 3f, 0xFF315C68)
        rect(c, 174f, 80f, 15f, 1f, 0xFF6BA1A6)
        crystal(c, 195f, 79f, 1f)
        crystal(c, 60f, 37f, .7f)
        c.restore()
        rect(c, 0f, floor + 2, 240f, height, 0xFF4A504C)
        for (i in 0..170) {
            val x = (i * 67 % 237).toFloat()
            val y = floor + 6 + i * 37 % (height - floor - 8).toInt().coerceAtLeast(1)
            rect(c, x, y, 3f + i % 3, 1f, if (i % 3 == 0) 0xFF5C6055 else 0xFF414B48)
        }
        // Rails noyés dans le sol : faible contraste dans la zone des acteurs.
        for (i in 0..((height - floor) / 22).toInt()) {
            val y = floor + 10 + i * 22
            val spread = 20f + (y - floor) * .16f
            rect(c, 124f - spread, y, spread * 2, 3f, 0xFF555247)
        }
        poly(c, 0xFF626D68, 104f, floor + 2, 107f, floor + 2,
            107f - (height - floor) * .16f, height, 104f - (height - floor) * .16f, height)
        poly(c, 0xFF626D68, 144f, floor + 2, 147f, floor + 2,
            147f + (height - floor) * .16f, height, 144f + (height - floor) * .16f, height)
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            for (i in 0..4) {
                val y = floor + 27 + i * (height - floor - 36) / 5
                poly(c, 0xFF65706C, 0f, y, 8f, y - 5, 16f, y - 1, 13f, y + 5, 0f, y + 7)
                rect(c, 5f, y - 2, 5f, 1f, 0xFF889184)
            }
            crystal(c, 7f, floor + 42, .7f)
            crystal(c, 6f, height - 15, .8f)
            c.restore()
        }
    }
    private fun crystal(c: Canvas, x: Float, y: Float, scale: Float) {
        c.save(); c.translate(x, y); c.scale(scale, scale)
        poly(c, 0xFF43858C, -8f, 0f, -12f, -13f, -8f, -18f, -3f, -10f, -1f, 0f)
        poly(c, 0xFF6BA9B1, -3f, 0f, -5f, -20f, 0f, -27f, 5f, -20f, 3f, 0f)
        poly(c, 0xFF9AD4CA, -3f, -19f, 0f, -24f, 0f, -3f, -2f, -2f)
        poly(c, 0xFF576F9C, 3f, 0f, 5f, -13f, 10f, -17f, 12f, -10f, 8f, 0f)
        rect(c, 7f, -11f, 1f, 7f, 0xFFA4BAD4)
        c.restore()
    }
    fun atmosphere(c: Canvas, floor: Float, clock: Float) {
        c.save(); upper(c, floor)
        val tick = (clock / 150f).toInt()
        // Lanterne oscillant autour du crochet ; la chaîne suit le boîtier.
        c.save(); c.rotate(sin(clock / 1700f) * 5f, 122f, 15f)
        for (i in 0..4) rect(c, 121f + i % 2, 15f + i * 3, 2f, 2f, 0xFF9C9D85)
        rect(c, 113f, 31f, 19f, 19f, 0x24D5AA66)
        rect(c, 117f, 32f, 11f, 14f, 0xFF293C46)
        rect(c, 119f, 34f, 7f, 10f, 0xFFBA975C)
        rect(c, 120f, 35f + tick % 2, 5f, 7f, 0xFFE2BE76)
        rect(c, 122f, 37f, 1f, 5f, 0xFFFFE3A0)
        rect(c, 121f, 33f, 1f, 11f, 0xFF73684E)
        rect(c, 115f, 31f, 15f, 3f, 0xFF829084)
        rect(c, 116f, 45f, 13f, 3f, 0xFF687D7C)
        c.restore()
        val drop = (clock % 1800f) / 1800f
        if (drop < .75f) rect(c, 179f, 48f + drop / .75f * 30, 1f, 3f, 0xFF8BB9BD)
        else {
            val spread = (drop - .75f) * 24
            rect(c, 178f - spread, 80f, 2f, 1f, 0xFF8BB9BD)
            rect(c, 181f + spread, 80f, 2f, 1f, 0xFF8BB9BD)
        }
        c.restore()
    }
}
