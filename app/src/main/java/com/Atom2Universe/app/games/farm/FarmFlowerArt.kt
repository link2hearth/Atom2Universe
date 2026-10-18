package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Native flower silhouettes shared by the greenhouse, journal and garden decoration. */
class FarmFlowerArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leaf = Color.rgb(85, 133, 78)
    fun draw(canvas: Canvas, flower: FarmFlower, target: RectF, growth: Float = 1f, muted: Boolean = false, sway: Float = 0f) {
        canvas.save(); canvas.translate(target.left, target.top); canvas.scale(target.width() / 64f, target.height() / 80f)
        canvas.rotate(sway, 32f, 69f)
        paint.color = if (muted) Color.rgb(159, 158, 146) else leaf
        paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        val height = 18f + growth.coerceIn(0f, 1f) * 28f
        val top = 69f - height
        canvas.drawLine(32f, 69f, 32f, top, paint)
        canvas.drawOval(18f, 49f, 33f, 57f, paint); canvas.drawOval(31f, 39f, 47f, 48f, paint)
        if (growth < .65f) {
            canvas.drawOval(28f, top - 4f, 36f, top + 6f, paint)
        } else {
            paint.color = if (muted) Color.rgb(164, 160, 149) else flower.color or -0x1000000
            when (flower.family) {
                0 -> {
                    for (i in 0..7) {
                        canvas.save(); canvas.rotate(i * 45f, 32f, top)
                        canvas.drawOval(28f, top - 19f, 36f, top - 3f, paint); canvas.restore()
                    }
                    paint.color = if (muted) Color.rgb(139, 137, 124) else Color.rgb(201, 157, 53)
                    canvas.drawCircle(32f, top, 6f, paint)
                }
                1 -> {
                    canvas.drawPath(Path().apply {
                        moveTo(17f, top - 17f); lineTo(26f, top - 12f); lineTo(32f, top - 23f)
                        lineTo(38f, top - 12f); lineTo(47f, top - 17f)
                        cubicTo(49f, top + 9f, 15f, top + 9f, 17f, top - 17f); close()
                    }, paint)
                }
                else -> {
                    for (i in 0..2) {
                        canvas.save(); canvas.rotate(i * 120f, 32f, top)
                        canvas.drawOval(26f, top - 23f, 38f, top + 3f, paint)
                        canvas.drawOval(18f, top, 34f, top + 14f, paint); canvas.restore()
                    }
                    paint.color = Color.rgb(225, 187, 74); canvas.drawCircle(32f, top, 4f, paint)
                }
            }
        }
        canvas.restore()
    }
}

class FarmFlowerPreview(context: Context, private val flower: FarmFlower, private val muted: Boolean = false) : View(context) {
    private val art = FarmFlowerArt()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = minOf(width.toFloat(), height * .8f)
        art.draw(canvas, flower, RectF((width - w) / 2, 0f, (width + w) / 2, height.toFloat()), muted = muted)
    }
}
