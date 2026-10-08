package com.Atom2Universe.app.games.roulette

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

class RouletteHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val assets = context.applicationContext.assets

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(0xFF060D20.toInt())
        val random = Random(777)
        repeat(60) {
            paint.color = Color.argb(55 + random.nextInt(120), 210, 226, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, min(w, h) * .003f, paint)
        }
        val size = min(w * .24f, h * .43f)
        val spacing = size * 1.15f
        val start = w / 2f - spacing - size / 2f
        val top = h * .14f
        val symbols = arrayOf(RouletteSymbol.STAR, RouletteSymbol.EARTH, RouletteSymbol.MOON)
        paint.style = Paint.Style.STROKE
        paint.color = 0xBFEBC883.toInt()
        paint.strokeWidth = min(w, h) * .008f
        canvas.drawRoundRect(start - size * .14f, top - size * .10f,
            start + spacing * 2f + size * 1.14f, top + size * 1.1f, size * .14f, size * .14f, paint)
        paint.style = Paint.Style.FILL
        symbols.forEachIndexed { index, symbol ->
            val x = start + index * spacing
            paint.color = 0xFF101D36.toInt()
            canvas.drawRoundRect(x, top, x + size, top + size, size * .10f, size * .10f, paint)
            val path = requireNotNull(symbol.assetPath)
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, info) }
            var sample = 1
            while (min(info.outWidth, info.outHeight) / (sample * 2) >= size) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: return@forEachIndexed
            try {
                val scale = size * .90f / maxOf(bitmap.width, bitmap.height)
                val bw = bitmap.width * scale
                val bh = bitmap.height * scale
                paint.color = Color.WHITE
                canvas.drawBitmap(bitmap, null, RectF(x + (size - bw) / 2f, top + (size - bh) / 2f,
                    x + (size + bw) / 2f, top + (size + bh) / 2f), paint)
            } finally { bitmap.recycle() }
        }
    }
}
