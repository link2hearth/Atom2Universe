package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

class FarmHubTileDrawable(private val context: Context) : Drawable() {
    private val sprites by lazy { FarmSprites(context) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var cachedBitmap: Bitmap? = null
    private var cachedWidth = 0
    private var cachedHeight = 0

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val bitmap = bitmapFor(b.width(), b.height())
        canvas.drawBitmap(bitmap, b.left.toFloat(), b.top.toFloat(), bitmapPaint)
    }

    private fun bitmapFor(width: Int, height: Int): Bitmap {
        cachedBitmap?.let { bitmap ->
            if (cachedWidth == width && cachedHeight == height && !bitmap.isRecycled) return bitmap
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            cachedBitmap?.recycle()
            cachedBitmap = bitmap
            cachedWidth = width
            cachedHeight = height
            render(Canvas(bitmap), width.toFloat(), height.toFloat())
        }
    }

    private fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.save()

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(124, 190, 117), Color.rgb(96, 160, 84), Color.rgb(72, 130, 64)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val tile = max(34f, w / 4.5f)
        var row = 0
        var y = -tile * 0.2f
        while (y < h) {
            var col = 0
            var x = -tile * 0.35f
            while (x < w) {
                rect.set(x, y, x + tile, y + tile)
                sprites.grass(canvas, rect, col, row)
                x += tile * 0.92f
                col++
            }
            y += tile * 0.78f
            row++
        }

        // Grass and plants only: the plants stand straight in the meadow, no strips of bare soil.
        drawFruits(canvas, w, h)
        drawCrops(canvas, w, h)

        canvas.restore()
    }

    private fun drawCrops(canvas: Canvas, w: Float, h: Float) {
        val crops = arrayOf(FarmCrop.CARROT, FarmCrop.LETTUCE, FarmCrop.PUMPKIN, FarmCrop.STRAWBERRY, FarmCrop.CORN)
        crops.forEachIndexed { i, crop ->
            val x = w * (0.13f + i * 0.18f)
            val y = h * (0.35f + (i % 3) * 0.13f)
            val size = h * if (crop == FarmCrop.PUMPKIN) 0.28f else 0.24f
            rect.set(x - size * 0.5f, y - size, x + size * 0.5f, y)
            sprites.crop(canvas, crop, i % 2, 4, rect)
        }
    }

    /**
     * The back row, where the flowers used to stand: fruit this time, drawn by the same native art as
     * the game. Drawn before the vegetables so the front row overlaps it, as it would in a real bed.
     */
    private fun drawFruits(canvas: Canvas, w: Float, h: Float) {
        val fruits = arrayOf(FarmCrop.TOMATO, FarmCrop.RASPBERRY, FarmCrop.WATERMELON, FarmCrop.GRAPE, FarmCrop.PINEAPPLE)
        fruits.forEachIndexed { i, crop ->
            val size = h * if (crop == FarmCrop.WATERMELON) 0.22f else 0.2f
            val x = w * (0.1f + i * 0.2f)
            val y = h * (0.22f + (i % 2) * 0.07f)
            rect.set(x - size * 0.5f, y - size, x + size * 0.5f, y)
            sprites.crop(canvas, crop, (i + 1) % 4, 4, rect)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
