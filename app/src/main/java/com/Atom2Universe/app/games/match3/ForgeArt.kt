package com.Atom2Universe.app.games.match3

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Vector artwork in a 100-unit square; no bitmap assets. */
class ForgeArt {
    val colors = intArrayOf(0xffb8d4dc.toInt(), 0xffbc955b.toInt(), 0xffed885e.toInt(), 0xff62d9ed.toInt(), 0xffffcf68.toInt(), 0xffecfaff.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun polygon(canvas: Canvas, points: Int, outer: Float, inner: Float = outer) {
        path.reset()
        repeat(points) { i ->
            val angle = (i * Math.PI * 2 / points - Math.PI / 2).toFloat()
            val r = if (i % 2 == 0) outer else inner
            val x = 50 + cos(angle) * r; val y = 50 + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, paint)
    }

    fun piece(canvas: Canvas, type: Int, x: Float, y: Float, size: Float, alpha: Int = 255, deliveryEdge: Int = 0) {
        if (type == ForgeGame.RIVET) { rivet(canvas, x, y, size); return }
        if (type !in colors.indices) return
        canvas.save(); canvas.translate(x, y); canvas.scale(size / 100, size / 100)
        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; paint.alpha = alpha / 2
        canvas.drawOval(14f, 72f, 86f, 93f, paint)
        paint.color = colors[type]; paint.alpha = alpha
        when (type) {
            0 -> { polygon(canvas, 6, 39f); paint.color = 0xff293e47.toInt(); paint.alpha = alpha; canvas.drawCircle(50f, 50f, 17f, paint) }
            1 -> { polygon(canvas, 24, 42f, 33f); paint.color = 0xff4e392b.toInt(); paint.alpha = alpha; canvas.drawCircle(50f, 50f, 14f, paint) }
            2 -> {
                canvas.drawRoundRect(18f, 18f, 82f, 82f, 18f, 18f, paint)
                paint.color = 0xff713f32.toInt(); paint.alpha = alpha; paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                for (j in 0..3) canvas.drawOval(27f + j * 5, 28f, 56f + j * 5, 72f, paint)
            }
            3 -> { polygon(canvas, 4, 44f); paint.color = 0xffb8f8ff.toInt(); paint.alpha = alpha / 2; path.reset(); path.moveTo(50f, 6f); path.lineTo(50f, 76f); path.lineTo(6f, 50f); path.close(); canvas.drawPath(path, paint) }
            4 -> { path.reset(); path.moveTo(25f, 24f); path.lineTo(75f, 24f); path.lineTo(90f, 76f); path.lineTo(10f, 76f); path.close(); canvas.drawPath(path, paint); paint.color = 0xff9c692e.toInt(); paint.alpha = alpha; canvas.drawRoundRect(32f, 39f, 68f, 55f, 4f, 4f, paint) }
            5 -> { polygon(canvas, 8, 43f); paint.color = 0xff226773.toInt(); paint.alpha = alpha; canvas.drawCircle(50f, 50f, 29f, paint); paint.color = 0xff8bffff.toInt(); paint.alpha = alpha; polygon(canvas, 4, 21f) }
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Color.WHITE; paint.alpha = alpha / 2
        canvas.drawLine(32f, 23f, 64f, 23f, paint)
        paint.style = Paint.Style.FILL; paint.alpha = 255
        if (type == ForgeGame.CORE && deliveryEdge in 0..3) {
            canvas.rotate(when (deliveryEdge) { 1 -> 90f; 2 -> -90f; 3 -> 180f; else -> 0f }, 50f, 50f)
            paint.color = 0xffef6262.toInt(); paint.alpha = alpha
            canvas.drawRoundRect(23f, 7f, 77f, 14f, 3f, 3f, paint)
            canvas.drawRoundRect(7f, 23f, 14f, 77f, 3f, 3f, paint)
            canvas.drawRoundRect(86f, 23f, 93f, 77f, 3f, 3f, paint)
            paint.color = 0xff78ffa9.toInt(); paint.alpha = alpha
            canvas.drawRoundRect(23f, 86f, 77f, 94f, 3f, 3f, paint)
            path.reset(); path.moveTo(43f, 27f); path.lineTo(57f, 27f)
            path.lineTo(57f, 54f); path.lineTo(72f, 54f); path.lineTo(50f, 79f)
            path.lineTo(28f, 54f); path.lineTo(43f, 54f); path.close()
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.color = Color.WHITE; paint.alpha = alpha
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL; paint.alpha = 255
        }
        canvas.restore()
    }

    private fun rivet(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.save(); canvas.translate(x, y); canvas.scale(size / 100f, size / 100f)
        paint.style = Paint.Style.FILL; paint.color = 0xff0c161b.toInt(); paint.alpha = 255
        canvas.drawRoundRect(4f, 8f, 96f, 98f, 9f, 9f, paint)
        paint.color = 0xff53616a.toInt(); canvas.drawRoundRect(4f, 3f, 96f, 91f, 9f, 9f, paint)
        paint.color = 0xff35434b.toInt(); canvas.drawRoundRect(15f, 15f, 85f, 80f, 5f, 5f, paint)
        paint.color = 0xffa2afb0.toInt()
        for (bx in floatArrayOf(15f, 85f)) for (by in floatArrayOf(14f, 80f)) {
            canvas.drawCircle(bx, by, 6f, paint)
        }
        paint.color = 0xffd6a866.toInt(); paint.strokeWidth = 8f
        canvas.drawLine(34f, 34f, 66f, 64f, paint); canvas.drawLine(66f, 34f, 34f, 64f, paint)
        canvas.restore()
    }
}
