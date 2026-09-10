package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.R

class LivestockIcon(context: Context, private val sprites: FarmSprites, private val id: String) : View(context) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        sprites.livestock(canvas, id, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}

class LivestockScene(private val context: Context, private val sprites: FarmSprites, private val state: LivestockState) {
    companion object {
        val pens = listOf(RectF(80f, 150f, 760f, 700f), RectF(840f, 170f, 1520f, 720f),
            RectF(80f, 800f, 760f, 1350f), RectF(840f, 820f, 1520f, 1370f))
        fun hit(x: Float, y: Float) = pens.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { LivestockKind.entries[it] }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas, visible: RectF) {
        paint.color = Color.rgb(198, 176, 124)
        canvas.drawRect(778f, 90f, 822f, 1460f, paint)
        for (kind in LivestockKind.entries) {
            val pen = pens[kind.ordinal]
            if (!RectF.intersects(pen, visible)) continue
            paint.color = Color.rgb(132, 163, 90)
            canvas.drawRoundRect(pen, 20f, 20f, paint)
            sprites.livestock(canvas, kind.shelter, RectF(pen.left + 35, pen.top + 10, pen.left + 315, pen.top + 235))
            sprites.livestock(canvas, "feed_trough", RectF(pen.left + 340, pen.top + 90, pen.left + 485, pen.top + 215))
            sprites.livestock(canvas, "water_trough", RectF(pen.left + 495, pen.top + 90, pen.right - 20, pen.top + 215))
            sprites.environment(canvas, 2, 3, RectF(pen.right - 90, pen.top + 20, pen.right - 20, pen.top + 85))
            state.animals.filter { it.kind == kind }.forEachIndexed { i, animal ->
                val x = pen.left + 20 + i % 8 * 80
                val y = pen.top + 235 + i / 8 * 45f
                sprites.livestock(canvas, animal.sprite, RectF(x, y, x + 78, y + 75))
            }
            for (i in 0..7) {
                val x = pen.left + i * pen.width() / 8
                sprites.environment(canvas, 0, 2, RectF(x, pen.top - 15, x + pen.width() / 8, pen.top + 25))
                sprites.environment(canvas, 0, if (i == 3) 3 else 2, RectF(x, pen.bottom - 25, x + pen.width() / 8, pen.bottom + 15))
                val y = pen.top + i * pen.height() / 8
                for (side in listOf(pen.left, pen.right)) sprites.environment(canvas, 1, 2, RectF(side - 10, y, side + 10, y + pen.height() / 8))
            }
            if (!state.available(kind)) {
                paint.color = Color.argb(140, 40, 55, 35); canvas.drawRoundRect(pen, 20f, 20f, paint)
            }
            paint.color = Color.rgb(255, 242, 205); paint.textAlign = Paint.Align.CENTER; paint.textSize = 25f
            canvas.drawText(context.getString(kind.label), pen.centerX(), pen.top - 25, paint)
            val label = if (state.available(kind)) context.getString(R.string.farm_herd_count, state.count(kind), LivestockState.CAPACITY)
                else context.getString(R.string.farm_locked_price, kind.landPrice)
            canvas.drawText(label, pen.centerX(), pen.bottom - 35, paint)
        }
    }
}
