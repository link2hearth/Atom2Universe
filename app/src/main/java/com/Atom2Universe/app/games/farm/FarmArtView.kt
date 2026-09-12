package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.View

/** Small native illustrations remain crisp at any density; crop previews reuse the game art. */
class FarmArtView(context: Context, private val kind: Kind, private val sprites: FarmSprites? = null) : View(context) {
    enum class Kind { SHOP, SEEDS, MAP, BACK, CLOSE, COIN, CROP, WATER, MANURE, CRATE }
    var crop: FarmCrop? = null
        set(value) { field = value; invalidate() }
    var stock: Int? = null
        set(value) { field = value; invalidate() }
    /** A small "something needs you" dot, independent of [stock] - top corner, never the same spot. */
    var alert: Boolean = false
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
            Kind.CRATE -> {
                rect(canvas, 8f, 18f, 40f, 42f, Color.rgb(116, 78, 48), 5f)
                rect(canvas, 10f, 15f, 38f, 25f, Color.rgb(188, 132, 72), 4f)
                rect(canvas, 10f, 25f, 38f, 40f, Color.rgb(156, 101, 56), 4f)
                line(canvas, 13f, 29f, 35f, 37f, Color.rgb(104, 68, 42), 2f)
                line(canvas, 35f, 29f, 13f, 37f, Color.rgb(104, 68, 42), 2f)
                crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(13f, 2f, 35f, 24f)) }
                paint.color = Color.rgb(255, 224, 91)
                val star = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) 5.2f else 2.4f
                    val x = 36f + kotlin.math.cos(a).toFloat() * r
                    val y = 13f + kotlin.math.sin(a).toFloat() * r
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close(); canvas.drawPath(star, paint)
            }
            Kind.MANURE -> {
                val burlap = Color.rgb(184, 151, 99)
                val muck = Color.rgb(88, 59, 34)
                val stink = Color.rgb(150, 176, 86)
                // The smell rises behind the sack, so the squiggles never cut across the burlap.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.2f; paint.strokeCap = Paint.Cap.ROUND
                paint.color = stink
                for ((x, base) in listOf(14f to 16f, 24f to 14f, 34f to 16f)) {
                    val wisp = Path()
                    wisp.moveTo(x, base)
                    wisp.quadTo(x - 4f, base - 3f, x, base - 6f)
                    wisp.quadTo(x + 4f, base - 9f, x, base - 12f)
                    canvas.drawPath(wisp, paint)
                }
                paint.style = Paint.Style.FILL
                rect(canvas, 10f, 42f, 38f, 46f, 0x22745233, 2f)
                // The heap first: the sack body then hides its bottom half and leaves a mound showing.
                paint.color = muck; canvas.drawOval(15f, 15f, 33f, 27f, paint)
                paint.color = Color.rgb(66, 44, 26)
                canvas.drawCircle(21f, 19f, 2.2f, paint)
                canvas.drawCircle(27f, 20f, 1.6f, paint)
                rect(canvas, 12f, 23f, 36f, 43f, burlap, 8f)
                rect(canvas, 12f, 36f, 36f, 43f, Color.rgb(160, 129, 83), 8f)
                rect(canvas, 11f, 22f, 37f, 27f, Color.rgb(206, 177, 124), 4f)
                line(canvas, 19f, 30f, 19f, 40f, Color.rgb(156, 125, 80), 1.6f)
                line(canvas, 29f, 30f, 29f, 40f, Color.rgb(156, 125, 80), 1.6f)
                // Two flies, because it really does stink.
                paint.color = Color.rgb(58, 52, 40)
                canvas.drawCircle(8f, 12f, 1.2f, paint)
                canvas.drawCircle(40f, 9f, 1f, paint)
            }
            Kind.WATER -> {
                val blue = Color.rgb(112, 197, 232)
                val darkBlue = Color.rgb(55, 122, 170)
                val rim = Color.rgb(236, 250, 255)
                rect(canvas, 9f, 40f, 38f, 44f, 0x22745233, 2f)
                canvas.rotate(-10f, 24f, 26f)
                rect(canvas, 13f, 18f, 35f, 39f, darkBlue, 8f)
                rect(canvas, 15f, 20f, 33f, 37f, blue, 6f)
                rect(canvas, 16f, 18f, 32f, 23f, rim, 4f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = darkBlue
                canvas.drawArc(RectF(6f, 20f, 20f, 36f), 95f, 170f, false, paint)
                canvas.drawArc(RectF(28f, 22f, 46f, 39f), -85f, 145f, false, paint)
                line(canvas, 29f, 15f, 42f, 11f, darkBlue, 4f)
                line(canvas, 37f, 12f, 44f, 16f, darkBlue, 3f)
                paint.style = Paint.Style.FILL
                canvas.rotate(10f, 24f, 26f)
                paint.color = blue
                for ((x, y) in listOf(37f to 30f, 41f to 34f, 34f to 36f))
                    canvas.drawCircle(x, y, 2.1f, paint)
            }
        }
        stock?.let {
            rect(canvas, 27f, 32f, 47f, 47f, green, 6f)
            paint.color = Color.WHITE; paint.textSize = if (it > 99) 8f else 10f
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(it.toString(), 37f, 43f, paint)
        }
        if (alert) {
            paint.style = Paint.Style.FILL; paint.color = rose
            canvas.drawCircle(38f, 10f, 9f, paint)
            paint.color = cream; paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("i", 38f, 14.5f, paint)
        }
        canvas.restore()
    }
}
