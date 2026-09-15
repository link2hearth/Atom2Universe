package com.Atom2Universe.app.hub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.min
import kotlin.math.roundToInt

/** Décors fixes : cache commun borné, réutilisé après recréation du hub, sans activité retenue. */
abstract class CachedHubArtworkDrawable : Drawable() {
    private data class Key(val type: Class<*>, val width: Int, val height: Int)
    companion object {
        private val images = object : LruCache<Key, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
        }
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    final override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = min(1f, 512f / maxOf(bounds.width(), bounds.height()))
        val w = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val h = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val key = Key(javaClass, w, h)
        val bitmap = images.get(key) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            render(Canvas(it), w.toFloat(), h.toFloat())
            images.put(key, it)
        }
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    protected abstract fun render(canvas: Canvas, w: Float, h: Float)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
