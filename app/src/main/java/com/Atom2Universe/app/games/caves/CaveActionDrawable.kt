package com.Atom2Universe.app.games.caves

import android.graphics.*
import android.graphics.drawable.Drawable

/** Line icons sized independently from translated labels and Android button padding. */
internal class CaveActionDrawable(private val kind: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CaveUiStyle.TEXT; style = Paint.Style.STROKE; strokeWidth = 1.6f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width()/32f, bounds.height()/32f)
        when (kind) {
            "map" -> {
                val path = Path().apply { moveTo(5f,8f); lineTo(12f,5f); lineTo(20f,8f); lineTo(27f,5f); lineTo(27f,24f); lineTo(20f,27f); lineTo(12f,24f); lineTo(5f,27f); close(); moveTo(12f,5f); lineTo(12f,24f); moveTo(20f,8f); lineTo(20f,27f) }
                canvas.drawPath(path, paint)
            }
            "camera" -> {
                canvas.drawRoundRect(5f,10f,27f,26f,4f,4f,paint); canvas.drawCircle(16f,18f,5f,paint)
                canvas.drawLine(10f,10f,12f,6f,paint); canvas.drawLine(12f,6f,20f,6f,paint); canvas.drawLine(20f,6f,22f,10f,paint)
            }
            "sun" -> {
                canvas.drawCircle(16f,16f,6f,paint)
                repeat(8) { canvas.save(); canvas.rotate(it*45f,16f,16f); canvas.drawLine(16f,3f,16f,6f,paint); canvas.restore() }
            }
            "combat" -> {
                val path = Path().apply { moveTo(8f,25f); lineTo(23f,6f); lineTo(27f,5f); lineTo(27f,9f); lineTo(12f,28f); close(); moveTo(7f,19f); lineTo(18f,27f) }
                canvas.drawPath(path,paint)
            }
            "bag" -> {
                canvas.drawRoundRect(8f,10f,24f,27f,4f,4f,paint)
                canvas.drawRoundRect(12f,5f,20f,12f,3f,3f,paint)
                canvas.drawRoundRect(11f,18f,21f,24f,2f,2f,paint)
            }
            "place" -> {
                val path = Path().apply { moveTo(16f,5f); lineTo(26f,11f); lineTo(26f,23f); lineTo(16f,29f); lineTo(6f,23f); lineTo(6f,11f); close(); moveTo(6f,11f); lineTo(16f,17f); lineTo(26f,11f); moveTo(16f,17f); lineTo(16f,29f) }
                canvas.drawPath(path, paint)
            }
            "aim" -> {
                canvas.drawCircle(16f,16f,8f,paint)
                canvas.drawLine(16f,3f,16f,10f,paint); canvas.drawLine(16f,22f,16f,29f,paint)
                canvas.drawLine(3f,16f,10f,16f,paint); canvas.drawLine(22f,16f,29f,16f,paint)
            }
            else -> {
                if (kind == "down") { canvas.rotate(180f,16f,16f) }
                canvas.drawLine(16f,25f,16f,7f,paint)
                canvas.drawLine(7f,16f,16f,7f,paint); canvas.drawLine(25f,16f,16f,7f,paint)
                canvas.drawLine(7f,28f,25f,28f,paint)
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
