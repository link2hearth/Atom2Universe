package com.Atom2Universe.app.games.game2048

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class Game2048HubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFFBBADA0.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val cell = min(w / 4.1f, h * .37f)
        val gap = cell * .08f
        val left = (w - cell * 4f) / 2f
        val values = intArrayOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 0)
        val colors = intArrayOf(0xFFEEE4DA.toInt(), 0xFFEDE0C8.toInt(), 0xFFF2B179.toInt(),
            0xFFF59563.toInt(), 0xFFF67C5F.toInt(), 0xFFF65E3B.toInt(), 0xFFEDCF72.toInt(),
            0xFFEDCC61.toInt(), 0xFFEDC850.toInt(), 0xFFEDC53F.toInt(), 0xFFEDC22E.toInt(), 0xFFCDC1B4.toInt())
        for (row in 0..2) for (col in 0..3) {
            val i = row * 4 + col
            val x = left + col * cell + gap
            val y = h * .035f + row * cell
            paint.color = colors[i]
            canvas.drawRoundRect(x, y, x + cell - 2f * gap, y + cell - gap, cell * .06f, cell * .06f, paint)
            if (values[i] == 0) continue
            paint.color = if (values[i] <= 4) 0xFF776E65.toInt() else 0xFFF9F6F2.toInt()
            paint.textSize = cell * if (values[i] >= 1024) .25f else if (values[i] >= 128) .31f else .43f
            val baseline = y + (cell - gap) / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(values[i].toString(), x + (cell - 2f * gap) / 2f, baseline, paint)
        }
        paint.shader = LinearGradient(0f, h * .46f, 0f, h,
            Color.TRANSPARENT, 0xED40352B.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .46f, w, h, paint)
    }
}
