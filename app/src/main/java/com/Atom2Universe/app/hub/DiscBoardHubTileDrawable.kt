package com.Atom2Universe.app.hub

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.min

/** Plateaux plein cadre : rendu statique commun aux dames et à Reversi. */
abstract class DiscBoardHubTileDrawable(private val reversi: Boolean) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cell = min(w / 3.6f, h * .43f)
        val left = -cell * .20f
        val top = -cell * .12f
        val cols = ((w - left) / cell).toInt()
        val rows = ((h - top) / cell).toInt()
        for (row in 0..rows) for (col in 0..cols) {
            paint.color = if (reversi) 0xFF237A3B.toInt()
                else if ((row + col) % 2 == 0) 0xFFF5DEB3.toInt() else 0xFF8B6343.toInt()
            val x = left + col * cell
            val y = top + row * cell
            canvas.drawRect(x, y, x + cell, y + cell, paint)
            if (reversi) {
                paint.color = 0xFF123F20.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = cell * .022f
                canvas.drawRect(x, y, x + cell, y + cell, paint)
                paint.style = Paint.Style.FILL
            }
        }
        fun disc(col: Int, row: Int, white: Boolean, king: Boolean = false) {
            val x = left + (col + .5f) * cell
            val y = top + (row + .5f) * cell
            val radius = cell * .37f
            paint.color = 0x65000000
            canvas.drawCircle(x + cell * .025f, y + cell * .06f, radius, paint)
            val light = if (white) 0xFFF8F4E8.toInt() else 0xFF585858.toInt()
            val dark = if (white) 0xFFD5D2C8.toInt() else 0xFF111111.toInt()
            paint.shader = LinearGradient(x, y - radius, x, y + radius, light, dark, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cell * .022f
            paint.color = if (white) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
            canvas.drawCircle(x, y, radius, paint)
            if (!reversi) {
                // Le double cercle distingue les pions de dames ; l'or reprend la dame du jeu.
                paint.color = if (king) 0xFFD7AF00.toInt() else if (white) 0xFFB5ADA0.toInt() else 0xFF858585.toInt()
                canvas.drawCircle(x, y, radius * .67f, paint)
                if (king) canvas.drawCircle(x, y, radius * .48f, paint)
            }
            paint.style = Paint.Style.FILL
        }
        if (reversi) {
            disc(1, 0, true)
            disc(2, 0, false)
            disc(1, 1, false)
            disc(2, 1, true)
            disc(3, 1, false)
        } else {
            // Tous les pions sont sur les cases foncées.
            disc(1, 0, false)
            disc(3, 0, false)
            disc(0, 1, true)
            disc(2, 1, true, king = true)
        }
        paint.shader = LinearGradient(0f, h * .5f, 0f, h,
            Color.TRANSPARENT, 0xCE101820.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .5f, w, h, paint)
    }
}
