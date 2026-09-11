package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
    private val path = Path()
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
            intArrayOf(Color.rgb(124, 190, 117), Color.rgb(82, 145, 74), Color.rgb(87, 68, 43)),
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

        drawSoilRows(canvas, w, h)
        drawCrops(canvas, w, h)
        drawFlowers(canvas, w, h)

        paint.color = Color.argb(168, 0, 0, 0)
        canvas.drawRect(0f, h * 0.47f, w, h, paint)
        paint.shader = LinearGradient(0f, h * 0.34f, 0f, h, Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.34f, w, h, paint)
        paint.shader = null

        canvas.restore()
    }

    private fun drawSoilRows(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        listOf(0.43f, 0.58f, 0.73f).forEachIndexed { index, y ->
            path.reset()
            path.moveTo(-w * 0.08f, h * y)
            path.quadTo(w * 0.5f, h * (y - 0.08f), w * 1.08f, h * (y + 0.02f))
            path.lineTo(w * 1.08f, h * (y + 0.12f))
            path.quadTo(w * 0.5f, h * (y + 0.03f), -w * 0.08f, h * (y + 0.1f))
            path.close()
            paint.color = if (index % 2 == 0) Color.rgb(116, 82, 50) else Color.rgb(139, 96, 55)
            canvas.drawPath(path, paint)
        }
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

    private fun drawFlowers(canvas: Canvas, w: Float, h: Float) {
        repeat(5) { i ->
            val size = h * 0.18f
            val x = w * (0.1f + i * 0.2f)
            val y = h * (0.2f + (i % 2) * 0.08f)
            rect.set(x - size * 0.5f, y - size, x + size * 0.5f, y)
            val sheet = if (i % 2 == 0) FLOWERS_A else FLOWERS_B
            sprites.flower(canvas, sheet, (i % 4) * 2, 4, rect)
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

    private companion object {
        const val FLOWERS_A = "garden_flowers_sunflower_tulip_lavender_daisy_v1.png"
        const val FLOWERS_B = "garden_flowers_rose_hydrangea_poppy_orchid_v1.png"
    }
}
