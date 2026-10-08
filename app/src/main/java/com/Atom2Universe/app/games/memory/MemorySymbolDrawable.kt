package com.Atom2Universe.app.games.memory

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.Atom2Universe.app.games.kit.KitPalette

/** Un gros caractère centré, dessiné directement à la taille de la carte. */
class MemorySymbolDrawable(private val symbol: String, private val palette: KitPalette) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val inset = width * 0.02f
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        paint.style = Paint.Style.FILL
        paint.color = palette.raised
        canvas.drawRoundRect(rect, width * 0.09f, width * 0.09f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * 0.025f
        paint.color = palette.accent
        canvas.drawRoundRect(rect, width * 0.09f, width * 0.09f, paint)
        paint.style = Paint.Style.FILL
        paint.color = palette.text
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = minOf(width * 0.85f, height * 0.65f)
        val textWidth = paint.measureText(symbol)
        if (textWidth > width * 0.8f) paint.textSize *= width * 0.8f / textWidth
        canvas.drawText(symbol, bounds.exactCenterX(),
            bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
