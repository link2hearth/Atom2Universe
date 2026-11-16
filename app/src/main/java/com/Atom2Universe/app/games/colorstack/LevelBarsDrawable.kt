package com.Atom2Universe.app.games.colorstack

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * Icône de niveau : trois barres montantes dont les [level] premières sont pleines
 * (une barre = facile, trois = difficile). Les autres restent en filigrane.
 */
class LevelBarsDrawable(private val level: Int, private val color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val side = minOf(b.width(), b.height()).toFloat()
        if (side <= 0f) return
        val left0 = b.exactCenterX() - side / 2f
        val bottom = b.exactCenterY() + side / 2f
        val gap = side * 0.12f
        val barW = (side - 2f * gap) / 3f
        val radius = barW * 0.25f
        for (i in 0 until 3) {
            val h = side * (0.4f + 0.3f * i)
            val l = left0 + i * (barW + gap)
            rect.set(l, bottom - h, l + barW, bottom)
            paint.color = if (i < level) color else ColorUtils.setAlphaComponent(color, 70)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
