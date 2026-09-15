package com.Atom2Universe.app.games.memory

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.ceil

class MemoryHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val assets = context.applicationContext.assets

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(0xFF040F25.toInt())
        val columns = if (w / h > 1.6f) 6 else 4
        val gap = w * .018f
        val cw = (w - gap * (columns + 1)) / columns
        val ch = cw * 1.5f
        val rows = ceil(h / (ch + gap)).toInt()
        val ids = intArrayOf(1, 3, 8, 20, 12, 25)
        val order = intArrayOf(0, 1, 2, 3, 4, 5, 3, 2, 5, 0, 1, 4)
        // Évite de décoder les originaux 1024 × 1536 : chaque image ne sert qu'à une petite carte.
        for (index in ids.indices) {
            val path = "Assets/Cartes/bonus/images (${ids[index]}).jpg"
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, info) }
            var sample = 1
            while (info.outWidth / (sample * 2) >= cw && info.outHeight / (sample * 2) >= ch) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: continue
            try {
                for (row in 0 until rows) for (col in 0 until columns) {
                    if (order[(row * columns + col) % order.size] != index) continue
                    val x = gap + col * (cw + gap)
                    val y = gap + row * (ch + gap)
                    val rect = RectF(x, y, x + cw, y + ch)
                    val radius = cw * .09f
                    val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                    val save = canvas.save()
                    canvas.clipPath(clip)
                    paint.color = Color.WHITE
                    canvas.drawBitmap(bitmap, null, rect, paint)
                    canvas.restoreToCount(save)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = .8f
                    paint.color = 0x705C9DD0
                    canvas.drawRoundRect(rect, radius, radius, paint)
                    paint.style = Paint.Style.FILL
                }
            } finally {
                bitmap.recycle()
            }
        }
        paint.shader = LinearGradient(0f, h * .48f, 0f, h,
            Color.TRANSPARENT, 0xEF040F25.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .48f, w, h, paint)
    }
}
