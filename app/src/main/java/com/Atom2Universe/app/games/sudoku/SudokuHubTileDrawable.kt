package com.Atom2Universe.app.games.sudoku

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class SudokuHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFFF2F4F8.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val cell = min(w / 5.6f, h * .32f)
        val left = -cell * .2f
        val top = -cell * .1f
        // Extrait d'une grille valide, avec cases vides et une saisie bleue.
        val digits = arrayOf(intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
            intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0), intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
            intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3))
        paint.color = 0xFFDEEBF8.toInt()
        canvas.drawRect(left + cell * 2f, 0f, left + cell * 3f, h, paint)
        paint.color = 0xFFB7D8F3.toInt()
        canvas.drawRect(left + cell * 2f, top, left + cell * 3f, top + cell, paint)
        for (row in digits.indices) for (col in digits[row].indices) {
            val digit = if (row == 0 && col == 2) 4 else digits[row][col]
            if (digit == 0) continue
            paint.color = if (row == 0 && col == 2) 0xFF2875C1.toInt() else 0xFF334155.toInt()
            paint.textSize = cell * .52f
            canvas.drawText(digit.toString(), left + (col + .5f) * cell,
                top + (row + .5f) * cell - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        for (i in 0..(w / cell).toInt() + 1) {
            paint.color = if (i % 3 == 0) 0xFF64748B.toInt() else 0xFFBDC7D3.toInt()
            paint.strokeWidth = cell * if (i % 3 == 0) .035f else .012f
            canvas.drawLine(left + i * cell, 0f, left + i * cell, h, paint)
        }
        for (i in 0..(h / cell).toInt() + 1) {
            paint.color = if (i % 3 == 0) 0xFF64748B.toInt() else 0xFFBDC7D3.toInt()
            paint.strokeWidth = cell * if (i % 3 == 0) .035f else .012f
            canvas.drawLine(0f, top + i * cell, w, top + i * cell, paint)
        }
        paint.shader = LinearGradient(0f, h * .46f, 0f, h,
            Color.TRANSPARENT, 0xEF17283F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .46f, w, h, paint)
    }
}
