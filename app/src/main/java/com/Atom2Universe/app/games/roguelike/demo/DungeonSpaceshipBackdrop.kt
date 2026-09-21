package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin
import kotlin.math.cos

/** Coursive en cache ; antenne extérieure articulée et écrans actifs à l'arrière. */
internal object DungeonSpaceshipBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
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
    fun draw(c: Canvas, floor: Float, height: Float) {
        c.drawColor(0xFF26354A.toInt())
        c.save(); upper(c, floor)
        for (i in 0..7) {
            rect(c, i * 30f + 1, 1f, 28f, 82f, 0xFF405168)
            rect(c, i * 30f + 3, 3f, 24f, 2f, 0xFF6B8091)
        }
        // Baie panoramique avec encadrement en métal et verre sombre.
        poly(c, 0xFF8C9EAB, 68f, 13f, 77f, 6f, 166f, 6f, 176f, 15f,
            176f, 56f, 166f, 64f, 77f, 64f, 68f, 55f)
        poly(c, 0xFF111F36, 73f, 16f, 80f, 11f, 163f, 11f, 171f, 18f,
            171f, 53f, 163f, 59f, 80f, 59f, 73f, 52f)
        for (i in 0..28) {
            val x = 78f + i * 29 % 88
            val y = 15f + i * 17 % 39
            rect(c, x, y, 1f, 1f, if (i % 3 == 0) 0xFFC9D1B7 else 0xFF748DAF)
        }
        poly(c, 0xFF4C789A, 88f, 19f, 102f, 16f, 112f, 21f, 117f, 30f,
            114f, 40f, 105f, 47f, 93f, 45f, 84f, 37f, 83f, 26f)
        poly(c, 0xFF75AEAF, 87f, 24f, 96f, 20f, 108f, 23f, 103f, 28f,
            95f, 29f, 94f, 36f, 88f, 34f)
        poly(c, 0xFF395B82, 108f, 23f, 117f, 30f, 114f, 40f, 105f, 47f,
            96f, 45f, 105f, 37f, 109f, 30f)
        rect(c, 90f, 21f, 10f, 1f, 0xFFBCD9CC)
        rect(c, 84f, 31f, 17f, 1f, 0xFF94BABC)
        rect(c, 97f, 41f, 11f, 1f, 0xFF87A9B2)
        // Antenne sur une portion de coque extérieure ; sa parabole sera animée.
        poly(c, 0xFF536C84, 129f, 59f, 140f, 50f, 166f, 50f, 171f, 54f, 165f, 59f)
        rect(c, 148f, 39f, 3f, 14f, 0xFF9AB1BA)
        rect(c, 140f, 53f, 21f, 2f, 0xFF839EAF)
        // Petits hublots latéraux et gaines techniques.
        for (side in 0..1) {
            val x = if (side == 0) 12f else 190f
            rect(c, x, 16f, 36f, 30f, 0xFF71899B)
            rect(c, x + 3, 19f, 30f, 24f, 0xFF172D48)
            for (j in 0..5) rect(c, x + 6 + j * 4, 22f + j * 7 % 17, 1f, 1f, 0xFFA5BECD)
            rect(c, x + 2, 46f, 32f, 2f, 0xFF243B51)
            rect(c, x + 39, 11f, 4f, 48f, 0xFF22384B)
            rect(c, x + 40, 12f, 1f, 45f, 0xFF89B8BD)
        }
        // Consoles basses : écrans graphiques sans texte ni faux libellés.
        for (x in intArrayOf(17, 185)) {
            poly(c, 0xFF8194A2, x.toFloat(), 65f, x + 5f, 52f, x + 35f, 52f,
                x + 41f, 65f, x + 41f, 70f, x.toFloat(), 70f)
            rect(c, x + 7f, 54f, 26f, 9f, 0xFF1E465A)
            rect(c, x + 9f, 55f, 13f, 1f, 0xFF81C9C6)
            for (j in 0..4) rect(c, x + 9f + j * 4, 58f, 2f, 2f + j % 3, 0xFF4E9DB2)
            rect(c, x + 4f, 70f, 33f, 14f, 0xFF344B60)
            rect(c, x + 7f, 73f, 27f, 1f, 0xFF5B7484)
            for (j in 0..5) rect(c, x + 4f + j * 6, 66f, 3f, 1f,
                if (j % 2 == 0) 0xFFE1BC7B else 0xFF7AC6B5)
        }
        rect(c, 77f, 68f, 88f, 15f, 0xFF34495E)
        rect(c, 82f, 70f, 78f, 2f, 0xFF718B9A)
        for (i in 0..10) rect(c, 86f + i * 6, 76f, 3f, 4f, 0xFF22374B)
        c.restore()
        rect(c, 0f, floor + 2, 240f, height, 0xFF293E52)
        for (row in 0..((height - floor) / 22).toInt()) for (col in -1..5) {
            val x = col * 49f + row % 2 * 24
            val y = floor + 4 + row * 22
            rect(c, x, y, 47f, 20f, 0xFF3C5163)
            rect(c, x + 2, y + 1, 43f, 1f, 0xFF516678)
            rect(c, x + 3, y + 16, 8f, 1f, 0xFF2C4053)
            rect(c, x + 3, y + 4, 1f, 1f, 0xFF8C9D9D)
        }
        // Éclairage et caissons limités aux bords de la coursive.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            rect(c, 1f, floor + 4, 3f, height - floor, 0xFF1B3043)
            for (i in 0..((height - floor) / 25).toInt()) {
                rect(c, 2f, floor + 8 + i * 25, 1f, 13f, 0xFF82C9CE)
            }
            for (y in floatArrayOf(floor + 33, height - 26)) {
                rect(c, 5f, y, 11f, 19f, 0xFF22394C)
                rect(c, 5f, y, 10f, 14f, 0xFF657C86)
                rect(c, 6f, y + 1, 8f, 2f, 0xFF98ACA9)
                rect(c, 7f, y + 6, 6f, 2f, 0xFFD3B378)
                rect(c, 9f, y + 6, 2f, 2f, 0xFF415B69)
            }
            c.restore()
        }
    }
    fun atmosphere(c: Canvas, floor: Float, clock: Float) {
        c.save(); upper(c, floor)
        // Rotation de la parabole en projection : mât fixe, réflecteur et bras mobiles.
        val turn = clock / 2100f
        val span = 3f + kotlin.math.abs(cos(turn)) * 9
        val face = sin(turn) * 3
        poly(c, 0xFF94B7C4, 149f - span, 32f, 149f - span * .7f, 38f,
            149f, 41f, 149f + span * .7f, 38f, 149f + span, 32f,
            149f + span * .55f, 35f + face, 149f, 37f + face, 149f - span * .55f, 35f + face)
        rect(c, 148f, 31f, 2f, 8f, 0xFF526F87)
        rect(c, 147f, 30f, 4f, 2f, 0xFFD1D9BD)
        val tick = (clock / 200f).toInt()
        for (x in intArrayOf(17, 185)) {
            rect(c, x + 7f + tick % 25, 54f, 1f, 9f, 0xFF9CDDD0)
            rect(c, x + 34f, 66f, 3f, 1f, if (tick % 8 < 5) 0xFF7AD5C3 else 0xFF497E88)
        }
        c.restore()
    }
}
