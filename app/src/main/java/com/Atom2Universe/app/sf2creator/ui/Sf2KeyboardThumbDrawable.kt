package com.Atom2Universe.app.sf2creator.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * Project card picture: a piano keyboard whose keys that play a sound are coloured. It shows
 * the 88 piano keys, widened when the project has sounds outside them.
 */
class Sf2KeyboardThumbDrawable(
    private val covered: BooleanArray,
    private val accent: Int
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33000000
    }
    private val rect = RectF()

    private val firstKey: Int
    private val lastKey: Int

    init {
        val used = covered.indices.filter { covered[it] }
        val low = used.minOrNull()
        firstKey = if (low == null || low >= 21) 21 else low - low % 12
        lastKey = maxOf(108, used.maxOrNull() ?: 108)
    }

    private fun isBlack(note: Int) = note % 12 in BLACK

    override fun draw(canvas: Canvas) {
        val b = bounds
        fill.color = PAPER
        canvas.drawRect(b, fill)

        val whites = (firstKey..lastKey).filter { !isBlack(it) }
        if (whites.isEmpty()) return
        val margin = b.width() * 0.06f
        val left = b.left + margin
        val width = b.width() - 2 * margin
        val keyWidth = width / whites.size
        val height = minOf(b.height() * 0.62f, keyWidth * 9f)
        val top = b.exactCenterY() - height / 2
        line.strokeWidth = (keyWidth * 0.12f).coerceIn(0.5f, 2f)

        for ((i, note) in whites.withIndex()) {
            rect.set(left + i * keyWidth, top, left + (i + 1) * keyWidth, top + height)
            fill.color = if (covered.getOrElse(note) { false }) accent else Color.WHITE
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, line)
        }
        val blackWidth = keyWidth * 0.62f
        val blackAccent = ColorUtils.blendARGB(accent, Color.BLACK, 0.35f)
        for ((i, note) in whites.withIndex()) {
            val sharp = note + 1
            if (sharp > lastKey || !isBlack(sharp)) continue
            val x = left + (i + 1) * keyWidth
            rect.set(x - blackWidth / 2, top, x + blackWidth / 2, top + height * 0.6f)
            fill.color = if (covered.getOrElse(sharp) { false }) blackAccent else INK
            canvas.drawRect(rect, fill)
        }
        line.color = 0x55000000
        rect.set(left, top, left + width, top + height)
        canvas.drawRect(rect, line)
        line.color = 0x33000000
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        val BLACK = setOf(1, 3, 6, 8, 10)
        const val PAPER = 0xFFFAF8F3.toInt()
        const val INK = 0xFF2B2B2B.toInt()
    }
}
