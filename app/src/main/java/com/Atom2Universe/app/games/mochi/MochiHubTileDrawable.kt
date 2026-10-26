package com.Atom2Universe.app.games.mochi

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

class MochiHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val context = context.applicationContext
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = MochiArt(context)
        canvas.save()
        canvas.scale(w / 420f, h / 420f)
        art.background(canvas, 0f)
        art.flower(canvas, 105f, 260f, true, false, 0f)
        art.flower(canvas, 300f, 100f, false, false, 0f)
        art.sweet(canvas, 240f, 125f, 0f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA588C7C.toInt() }
        for (i in 0..5) art.sparkle(canvas, 125f + i * 35f,
            155f + kotlin.math.sin(i * 2f) * 65f, 4f + i % 3, 0xFFFFF3BE.toInt())
        canvas.save()
        canvas.translate(200f, 205f)
        canvas.scale(2.5f, 2.5f)
        art.body(canvas, 0f, 0f, 1.07f, -12f, 0f, hero = true)
        canvas.restore()
        paint.shader = LinearGradient(0f, 280f, 0f, 420f, Color.TRANSPARENT,
            0xF0234540.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 280f, 420f, 420f, paint)
        canvas.restore()
    }
}
