package com.Atom2Universe.app.games.roguelike

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin
import kotlin.math.roundToInt

/** Pantins nécromantiques dessinés dans le même repère pixel-art que les acteurs. */
internal class DungeonPuppetArt {
    private val paint = Paint()

    fun drawBolt(canvas: Canvas, from: RectF, to: RectF, progress: Float) {
        val unit = from.width() / 32f
        for (trail in 3 downTo 0) {
            val t = (progress - trail * .045f).coerceIn(0f, 1f)
            val x = from.centerX() + (to.centerX() - from.centerX()) * t
            val y = from.centerY() + (to.centerY() - from.centerY()) * t
            val radius = if (trail == 0) 2f * unit else unit
            paint.color = if (trail == 0) 0xFFE0FFE6.toInt() else 0x9980CBC4.toInt()
            canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint)
        }
    }

    fun draw(canvas: Canvas, bounds: RectF, time: Long, index: Int, hp: Float) {
        val phase = time / 420.0 + index * 2.1
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        canvas.scale(bounds.width() / 32f, bounds.height() / 36f)
        fun pixel(x: Int, y: Int, w: Int, h: Int, color: Long) {
            paint.color = color.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        // Ombre et sceau au sol, indépendants du flottement du corps.
        pixel(7, 32, 19, 3, 0x66203132)
        pixel(5, 32, 3, 1, 0xFF416B65); pixel(25, 32, 3, 1, 0xFF416B65)
        pixel(9, 34, 14, 1, 0xFF568F80)
        canvas.save()
        canvas.translate(0f, (sin(phase) * 1.5).roundToInt().toFloat())
        // Silhouette découpée, pans de tissu et doublure verte.
        pixel(11, 3, 11, 2, 0xFF14232C)
        pixel(8, 5, 17, 11, 0xFF14232C)
        pixel(6, 15, 21, 9, 0xFF14232C)
        pixel(8, 23, 17, 5, 0xFF14232C)
        pixel(8, 27, 4, 3, 0xFF14232C); pixel(15, 27, 4, 4, 0xFF14232C)
        pixel(22, 26, 3, 4, 0xFF14232C)
        pixel(10, 6, 13, 9, 0xFF344E4E)
        pixel(10, 5, 9, 2, 0xFF57776B)
        pixel(8, 16, 17, 7, 0xFF344E4E)
        pixel(10, 23, 4, 4, 0xFF46675C)
        pixel(17, 21, 5, 7, 0xFF243B40)
        pixel(9, 16, 3, 7, 0xFF658674)
        // Masque osseux tourné vers les ennemis, orbites lumineuses.
        pixel(10, 8, 11, 7, 0xFF192B30)
        pixel(11, 8, 8, 5, 0xFFBCBE9B)
        pixel(12, 13, 6, 3, 0xFF899B82)
        pixel(10, 10, 4, 2, 0xFF72EAC4)
        pixel(16, 10, 3, 2, 0xFF72EAC4)
        pixel(11, 10, 2, 1, 0xFFE0FFE6)
        pixel(14, 13, 1, 2, 0xFF263A3A)
        // Brassards, doigts et talisman relié au nécromancien.
        pixel(4, 18, 4, 6, 0xFF253B40); pixel(25, 18, 4, 6, 0xFF253B40)
        pixel(3, 23, 4, 3, 0xFFBCBE9B); pixel(26, 23, 3, 3, 0xFFBCBE9B)
        pixel(14, 17, 5, 5, 0xFF152B32)
        pixel(15, 18, 3, 3, 0xFF72EAC4)
        pixel(16, 18, 1, 1, 0xFFE0FFE6)
        // Quelques braises d'âme, déphasées entre les pantins.
        val rise = ((time / 110 + index * 5) % 15).toInt()
        pixel(5, 27 - rise, 1, 2, 0xAA80CBC4)
        pixel(27, 21 - rise, 1, 1, 0xAA80CBC4)
        canvas.restore()
        pixel(5, 35, 23, 1, 0xFF14232C)
        pixel(5, 36, 23, 3, 0xFF263238)
        pixel(5, 36, (23 * hp.coerceIn(0f, 1f)).toInt(), 2, 0xFF80CBC4)
        canvas.restore()
    }
}
