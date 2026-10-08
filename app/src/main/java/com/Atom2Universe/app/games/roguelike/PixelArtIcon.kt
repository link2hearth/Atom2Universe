package com.Atom2Universe.app.games.roguelike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Preserve square source pixels, with whole-pixel enlargement whenever space permits. */
internal class PixelArtIcon(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    override fun draw(canvas: Canvas) = draw(canvas, bitmap, RectF(bounds), paint)
    override fun getIntrinsicWidth() = bitmap.width
    override fun getIntrinsicHeight() = bitmap.height
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** [dst] : un Rect réutilisé par l'appelant qui dessine à chaque image, pour ne rien allouer. */
        fun draw(canvas: Canvas, bitmap: Bitmap, bounds: RectF, paint: Paint, dst: Rect = Rect()) {
            val available = min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            if (available <= 0f) return
            val scale = if (available >= 1f) floor(available) else available
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val x = (bounds.centerX() - w / 2f).roundToInt()
            val y = (bounds.centerY() - h / 2f).roundToInt()
            paint.isFilterBitmap = false
            paint.isAntiAlias = false
            dst.set(x, y, x + w, y + h)
            canvas.drawBitmap(bitmap, null, dst, paint)
        }
    }
}
