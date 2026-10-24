package com.Atom2Universe.app.games.atomblocks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Original block constellation, kept text-free for hub and pinned shortcuts. */
class AtomBlocksHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF14263E.toInt(), 0xFF18152F.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * .5f, h * .32f, w * .6f, 0x5541D6D4, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val size = min(w * .108f, h * .14f)
        val left = (w - 7 * size) / 2
        val top = h * .08f
        val cells = arrayOf(
            intArrayOf(0,0,0,0,0,3,3), intArrayOf(1,0,0,0,0,3,3),
            intArrayOf(1,1,1,0,4,4,4), intArrayOf(2,2,0,0,0,4,0),
            intArrayOf(2,2,5,5,5,5,5)
        )
        val colors = intArrayOf(0xFF26354B.toInt(), 0xFF50D8CB.toInt(), 0xFF6C9EFF.toInt(),
            0xFFB395FF.toInt(), 0xFFFFC66A.toInt(), 0xFFFF808E.toInt())
        for (r in cells.indices) for (c in cells[r].indices) {
            val color = colors[cells[r][c]]
            val x = left + c * size; val y = top + r * size
            p.color = color
            canvas.drawRoundRect(x + 2, y + 2, x + size - 2, y + size - 2, size * .15f, size * .15f, p)
            if (cells[r][c] != 0) {
                p.color = 0x45FFFFFF
                canvas.drawRoundRect(x + size * .16f, y + size * .12f, x + size * .84f, y + size * .22f, 3f, 3f, p)
            }
        }
        for (i in 0..18) {
            p.color = if (i % 2 == 0) 0xB077FFDB.toInt() else 0x90FFFFFF.toInt()
            val x = w * (.06f + (i * 37 % 89) / 100f)
            val y = h * (.05f + (i * 19 % 58) / 100f)
            canvas.drawCircle(x, y, w * if (i % 3 == 0) .006f else .003f, p)
        }
        p.shader = LinearGradient(0f, h * .5f, 0f, h, Color.TRANSPARENT, 0xFC101629.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .5f, w, h, p)
    }
}
