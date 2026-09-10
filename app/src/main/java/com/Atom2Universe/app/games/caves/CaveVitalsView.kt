package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.Atom2Universe.app.R

/** Small pixel hearts above the quickbar; no full-width background covers the world. */
internal class CaveVitalsView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var hp = 20; private var maxHp = 20
    private var shield = 0; private var maxShield = 0
    private var sprinting = false
    fun health(value: Int, maximum: Int) { hp = value; maxHp = maximum.coerceAtLeast(1); invalidate() }
    fun shield(value: Int, maximum: Int) { shield = value; maxShield = maximum; invalidate() }
    fun sprint(value: Boolean) { sprinting = value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(density, density)
        val totalWidth = width / density
        val step = 15f; val unit = 1.6f
        paint.setShadowLayer(2f, 0f, 1f, 0xCC18291F.toInt())
        for (i in 0..9) {
            val x = i * step; val y = 8f
            val heart = Path().apply {
                moveTo(x, y + unit); lineTo(x + unit, y); lineTo(x + 3*unit, y)
                lineTo(x + 4*unit, y + unit); lineTo(x + 5*unit, y); lineTo(x + 7*unit, y)
                lineTo(x + 8*unit, y + unit); lineTo(x + 8*unit, y + 3*unit)
                lineTo(x + 4*unit, y + 7*unit); lineTo(x, y + 3*unit); close()
            }
            paint.color = 0xCC37443B.toInt(); paint.style = Paint.Style.FILL; canvas.drawPath(heart, paint)
            val filled = (hp.toFloat() / maxHp * 10 - i).coerceIn(0f, 1f)
            if (filled > 0) {
                canvas.save(); canvas.clipRect(x, y, x + 8*unit*filled, y + 8*unit)
                paint.color = 0xFFF0A895.toInt(); canvas.drawPath(heart, paint); canvas.restore()
            }
            paint.color = 0xFFECD7C6.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = .65f
            canvas.drawPath(heart, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = CaveUiStyle.TEXT; paint.textSize = 10f
        canvas.drawText("$hp / $maxHp", 155f, 19f, paint)
        if (maxShield > 0) {
            val start = totalWidth - 145f
            paint.color = 0xCC374B4C.toInt(); canvas.drawRoundRect(start, 10f, start+92f, 17f, 3f, 3f, paint)
            paint.color = 0xFFA5D1DB.toInt(); canvas.drawRoundRect(start, 10f, start+92f*(shield.toFloat()/maxShield).coerceIn(0f,1f), 17f, 3f, 3f, paint)
            paint.color = CaveUiStyle.TEXT; canvas.drawText("$shield", start + 98f, 19f, paint)
        } else if (sprinting) {
            paint.color = CaveUiStyle.ACCENT; paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(context.getString(R.string.cave_ui_sprint), totalWidth, 19f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        paint.clearShadowLayer(); canvas.restore()
    }
}
