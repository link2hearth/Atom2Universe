package com.Atom2Universe.app.games.farm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/** Four friendly wildlife silhouettes; all coordinates are native canvas art. */
class FarmVisitorArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun oval(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color; canvas.drawOval(l, t, r, b, paint)
    }
    fun draw(canvas: Canvas, visitor: FarmVisitor, target: RectF, time: Float = 0f) {
        val phase = if (ValueAnimator.areAnimatorsEnabled()) time else 0f
        val hop = if (visitor == FarmVisitor.ROBIN) kotlin.math.abs(sin(phase * 1.7f)) * 3f else sin(phase * 1.3f) * 1.2f
        canvas.save(); canvas.translate(target.left, target.top); canvas.scale(target.width() / 80f, target.height() / 80f)
        oval(canvas, 15f, 67f, 67f, 75f, Color.argb(55, 66, 84, 46))
        canvas.save(); canvas.translate(0f, -hop)
        val brown = Color.rgb(110, 79, 54); val cream = Color.rgb(245, 226, 185)
        when (visitor) {
            FarmVisitor.ROBIN -> {
                paint.color = Color.rgb(149, 120, 70)
                canvas.drawPath(Path().apply { moveTo(28f, 51f); lineTo(8f, 43f); lineTo(21f, 63f); close() }, paint)
                oval(canvas, 20f, 29f, 60f, 68f, brown)
                oval(canvas, 39f, 36f, 63f, 66f, Color.rgb(213, 126, 78))
                oval(canvas, 35f, 17f, 64f, 42f, brown)
                oval(canvas, 51f, 23f, 56f, 28f, Color.rgb(39, 44, 37))
                paint.color = Color.rgb(222, 178, 72)
                canvas.drawPath(Path().apply { moveTo(62f, 29f); lineTo(73f, 33f); lineTo(61f, 36f); close() }, paint)
                oval(canvas, 23f, 39f + sin(phase * 2) * 2, 44f, 61f, Color.rgb(147, 118, 73))
                paint.color = brown; paint.strokeWidth = 2f
                canvas.drawLine(39f, 64f, 37f, 73f, paint); canvas.drawLine(49f, 64f, 51f, 73f, paint)
            }
            FarmVisitor.BUTTERFLY -> {
                val wing = .72f + kotlin.math.abs(sin(phase * 5f)) * .28f
                canvas.save(); canvas.scale(wing, 1f, 40f, 42f)
                oval(canvas, 8f, 13f, 40f, 46f, Color.rgb(218, 168, 94))
                oval(canvas, 41f, 13f, 73f, 46f, Color.rgb(218, 168, 94))
                oval(canvas, 15f, 40f, 40f, 66f, Color.rgb(198, 122, 105))
                oval(canvas, 41f, 40f, 66f, 66f, Color.rgb(198, 122, 105))
                for (x in listOf(23f, 57f)) oval(canvas, x - 5, 23f, x + 5, 33f, cream)
                canvas.restore()
                oval(canvas, 37f, 23f, 44f, 61f, brown)
                paint.color = brown; paint.strokeWidth = 2f
                canvas.drawLine(39f, 27f, 32f, 15f, paint); canvas.drawLine(42f, 27f, 49f, 15f, paint)
            }
            FarmVisitor.SQUIRREL -> {
                canvas.save(); canvas.rotate(sin(phase * 1.6f) * 5, 28f, 58f)
                oval(canvas, 6f, 11f, 32f, 66f, Color.rgb(182, 125, 70))
                oval(canvas, 11f, 19f, 25f, 54f, Color.rgb(209, 158, 93)); canvas.restore()
                oval(canvas, 22f, 35f, 60f, 69f, Color.rgb(182, 125, 70))
                oval(canvas, 39f, 38f, 59f, 66f, cream)
                oval(canvas, 41f, 19f, 68f, 45f, Color.rgb(182, 125, 70))
                oval(canvas, 43f, 10f, 53f, 28f, brown)
                oval(canvas, 58f, 26f, 63f, 31f, Color.rgb(39, 44, 37))
                oval(canvas, 65f, 33f, 71f, 38f, brown)
                oval(canvas, 49f, 48f, 65f, 60f, brown)
                oval(canvas, 50f, 46f, 64f, 51f, Color.rgb(147, 168, 88))
            }
            FarmVisitor.RABBIT -> {
                oval(canvas, 40f, 5f, 50f, 36f, Color.rgb(207, 193, 163))
                canvas.save(); canvas.rotate(sin(phase * 1.2f) * 7, 55f, 30f)
                oval(canvas, 51f, 3f, 63f, 34f, Color.rgb(207, 193, 163))
                oval(canvas, 55f, 8f, 59f, 27f, Color.rgb(211, 165, 148)); canvas.restore()
                oval(canvas, 17f, 39f, 61f, 70f, Color.rgb(207, 193, 163))
                oval(canvas, 38f, 27f, 69f, 54f, Color.rgb(222, 209, 177))
                oval(canvas, 11f, 49f, 25f, 65f, cream)
                oval(canvas, 56f, 36f, 61f, 41f, Color.rgb(39, 44, 37))
                oval(canvas, 64f, 45f, 71f, 49f, Color.rgb(178, 132, 118))
                oval(canvas, 34f, 62f, 65f, 73f, Color.rgb(192, 175, 145))
            }
        }
        canvas.restore(); canvas.restore()
    }
}

class FarmVisitorPreview(context: Context, private val visitor: FarmVisitor) : View(context) {
    private val art = FarmVisitorArt()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        art.draw(canvas, visitor, RectF((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2),
            (android.os.SystemClock.uptimeMillis() % 120_000) / 1000f)
        if (isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()) postInvalidateOnAnimation()
    }
}
