package com.Atom2Universe.app.games.enamel

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

class EnamelHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val art = EnamelPainter()
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(EnamelPainter.INK)
        canvas.save()
        canvas.translate(w * .08f, -h * .18f)
        canvas.scale(w * .084f, h * .068f)
        art.board(canvas)
        art.puck(canvas, 4, 3.2f, 5.2f, 1.18f, -.2f)
        art.puck(canvas, 2, 6.6f, 5.5f, .85f, .2f)
        art.puck(canvas, 7, 6.8f, 8.4f, 1.48f, -.1f)
        art.puck(canvas, 2, 4.6f, 10.6f, .85f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EnamelPainter.INK; strokeWidth = .07f; alpha = 110 }
        canvas.drawLine(4.6f, 11.8f, 5.9f, 7.5f, p)
        canvas.restore()
        val veil = Paint().apply {
            shader = LinearGradient(0f, h * .5f, 0f, h, 0x00173E40, 0xFF173E40.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .5f, w, h, veil)
    }
}
