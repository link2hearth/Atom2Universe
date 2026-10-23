package com.Atom2Universe.app.games.enamel

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

class RicochetPuzzleHubDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val art = EnamelPainter()
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(EnamelPainter.INK)
        canvas.save(); canvas.translate(w * .07f, -h * .28f); canvas.scale(w * .086f, h * .085f)
        art.board(canvas, RicochetPuzzleRules.HEIGHT)
        for (x in floatArrayOf(2f, 7.5f)) {
            art.puck(canvas, 2, x, 4f, .75f)
            art.puck(canvas, 3, x + .5f, 7.6f, .75f)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EnamelPainter.INK }
        canvas.drawRoundRect(3.2f, 5.8f, 6.8f, 6.3f, .12f, .12f, p)
        p.color = EnamelPainter.GOLD; p.strokeWidth = .04f
        canvas.drawLine(3.4f, 5.9f, 6.6f, 5.9f, p)
        canvas.restore()
        p.shader = LinearGradient(0f, h * .5f, 0f, h, 0x00173E40, EnamelPainter.INK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .5f, w, h, p)
    }
}
