package com.Atom2Universe.app.games.roguelike

import android.graphics.Canvas
import android.graphics.Paint

/** Layered shadow, supported by hardware Canvas without a software rendering layer. */
internal object DungeonTimingShadow {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        radius: Float, unit: Float) {
        for (layer in 3 downTo 1) {
            val spread = layer * unit
            paint.color = when (layer) {
                3 -> 0x35111729
                2 -> 0x70111729
                else -> 0xE6111729.toInt()
            }
            canvas.drawRoundRect(left - spread, top - spread, right + spread, bottom + spread,
                radius + spread, radius + spread, paint)
        }
    }
}
