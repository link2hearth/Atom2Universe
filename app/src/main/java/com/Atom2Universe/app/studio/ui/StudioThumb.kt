package com.Atom2Universe.app.studio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.studio.StudioColors
import com.Atom2Universe.app.studio.data.StudioProject

/** La vignette d'un morceau dans la bibliothèque : sa grille de rythme en miniature, une rangée par piste visible. */
object StudioThumb {

    fun render(project: StudioProject, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF151826.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val tracks = project.tracks.filter { !it.hidden }
        if (tracks.isEmpty()) return bmp
        val steps = project.stepCount
        val pattern = project.pattern
        val margin = height * 0.1f
        val rowH = (height - 2 * margin) / tracks.size.coerceAtLeast(1)
        val cellW = (width - 2 * margin) / steps
        val rect = RectF()
        for ((r, t) in tracks.withIndex()) {
            val color = StudioColors.of(t)
            val lane = if (t.melodic) null else pattern.lane(t.id)
            for (s in 0 until steps) {
                val on = if (t.melodic) pattern.notes.any { it.track == t.id && s >= it.step && s < it.step + it.length }
                else (lane?.velocity?.getOrNull(s) ?: 0f) > 0f
                val x = margin + s * cellW
                val y = margin + r * rowH
                rect.set(x + cellW * 0.12f, y + rowH * 0.15f, x + cellW * 0.88f, y + rowH * 0.85f)
                paint.color = if (on) color else 0xFF2A2E40.toInt()
                canvas.drawRoundRect(rect, cellW * 0.25f, cellW * 0.25f, paint)
            }
        }
        return bmp
    }
}
