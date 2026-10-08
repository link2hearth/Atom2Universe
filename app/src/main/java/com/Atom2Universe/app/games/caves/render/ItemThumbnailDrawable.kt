package com.Atom2Universe.app.games.caves.render

import android.graphics.*
import android.graphics.drawable.Drawable

/** Preserve the silhouette even in the rectangular hotbar and detail preview. */
internal class ItemThumbnailDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint().apply { isFilterBitmap = false }
    override fun draw(canvas: Canvas) {
        val edge = minOf(bounds.width(), bounds.height()).toFloat()
        val x = bounds.exactCenterX() - edge / 2f
        val y = bounds.exactCenterY() - edge / 2f
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + edge, y + edge), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
