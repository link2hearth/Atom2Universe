package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.View

/** Small native illustrations remain crisp at any density; crop previews reuse the game art. */
class FarmArtView(context: Context, private val kind: Kind, private val sprites: FarmSprites? = null) : View(context) {
    enum class Kind { SHOP, SEEDS, MAP, BACK, CLOSE, COIN, CROP, WATER }
    var crop: FarmCrop? = null
        set(value) { field = value; invalidate() }
    var stock: Int? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 3f) {
        paint.color = color; paint.style = Paint.Style.FILL
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, stroke: Float = 3f) {
        paint.color = color; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, x2, y2, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 48f
        canvas.save(); canvas.translate((width - 48 * scale) / 2, (height - 48 * scale) / 2); canvas.scale(scale, scale)
        val cream = Color.rgb(255, 242, 205)
        val brown = Color.rgb(108, 76, 51)
        val green = Color.rgb(93, 134, 81)
        val rose = Color.rgb(206, 114, 91)
        when (kind) {
            Kind.SHOP -> {
                rect(canvas, 6f, 39f, 44f, 43f, 0x22745233)
                rect(canvas, 10f, 17f, 40f, 40f, brown)
                rect(canvas, 12f, 18f, 38f, 38f, cream)
                rect(canvas, 27f, 25f, 35f, 39f, green)
                rect(canvas, 15f, 25f, 24f, 32f, Color.rgb(162, 210, 201))
                rect(canvas, 8f, 9f, 42f, 16f, rose)
                for (i in 0..4) rect(canvas, 7f + i * 7, 14f, 14f + i * 7, 23f, if (i % 2 == 0) rose else cream)
                rect(canvas, 13f, 33f, 25f, 39f, Color.rgb(185, 139, 81))
                paint.color = green; canvas.drawOval(14f, 28f, 20f, 35f, paint)
                paint.color = Color.rgb(231, 151, 64); canvas.drawCircle(22f, 33f, 3f, paint)
                paint.color = cream; canvas.drawCircle(33f, 32f, 1f, paint)
            }
            Kind.SEEDS -> {
                canvas.rotate(-7f, 24f, 24f)
                rect(canvas, 10f, 8f, 38f, 42f, brown)
                rect(canvas, 12f, 9f, 36f, 40f, cream)
                rect(canvas, 12f, 9f, 36f, 14f, rose)
                crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(13f, 14f, 35f, 35f)) }
                line(canvas, 18f, 37f, 30f, 37f, green, 2f)
                canvas.rotate(7f, 24f, 24f)
            }
            Kind.MAP -> {
                rect(canvas, 6f, 10f, 42f, 39f, brown)
                rect(canvas, 8f, 11f, 40f, 37f, cream)
                rect(canvas, 11f, 14f, 21f, 23f, green)
                rect(canvas, 25f, 14f, 37f, 27f, Color.rgb(149, 174, 107))
                rect(canvas, 11f, 27f, 21f, 34f, Color.rgb(190, 148, 92))
                line(canvas, 24f, 12f, 24f, 35f, Color.rgb(225, 201, 145), 2f)
                paint.color = rose; canvas.drawCircle(32f, 30f, 4f, paint)
                paint.color = cream; canvas.drawCircle(32f, 30f, 1.5f, paint)
            }
            Kind.BACK -> {
                rect(canvas, 9f, 9f, 39f, 39f, cream, 12f)
                line(canvas, 30f, 24f, 17f, 24f, brown)
                line(canvas, 17f, 24f, 24f, 17f, brown)
                line(canvas, 17f, 24f, 24f, 31f, brown)
            }
            Kind.CLOSE -> {
                line(canvas, 18f, 18f, 30f, 30f, brown)
                line(canvas, 30f, 18f, 18f, 30f, brown)
            }
            Kind.COIN -> {
                paint.color = Color.rgb(175, 115, 40); canvas.drawCircle(24f, 25f, 15f, paint)
                paint.color = Color.rgb(247, 194, 78); canvas.drawCircle(24f, 23f, 14f, paint)
                paint.color = Color.rgb(255, 222, 126); canvas.drawCircle(24f, 23f, 10f, paint)
                line(canvas, 24f, 17f, 24f, 29f, Color.rgb(201, 143, 46), 3f)
            }
            Kind.CROP -> crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(0f, 0f, 48f, 46f)) }
            Kind.WATER -> {
                val blue = Color.rgb(118, 200, 232)
                canvas.rotate(-14f, 24f, 26f)
                rect(canvas, 12f, 20f, 34f, 40f, brown, 8f)
                rect(canvas, 14f, 22f, 32f, 38f, blue, 6f)
                rect(canvas, 30f, 14f, 40f, 20f, brown, 4f)
                line(canvas, 20f, 16f, 12f, 10f, brown, 4f)
                canvas.rotate(14f, 24f, 26f)
                paint.color = blue
                canvas.drawCircle(15f, 44f, 2.2f, paint)
                canvas.drawCircle(21f, 46f, 1.6f, paint)
            }
        }
        stock?.let {
            rect(canvas, 27f, 32f, 47f, 47f, green, 6f)
            paint.color = Color.WHITE; paint.textSize = if (it > 99) 8f else 10f
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(it.toString(), 37f, 43f, paint)
        }
        canvas.restore()
    }
}
