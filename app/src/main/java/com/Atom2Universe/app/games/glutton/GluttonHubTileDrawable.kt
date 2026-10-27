package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

class GluttonHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = GluttonArt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save(); canvas.scale(w / 420f, h / 420f)
        canvas.drawColor(0xFFF3D7C9.toInt())
        paint.color = 0xFFE8ADC1.toInt(); canvas.drawRoundRect(15f, 80f, 405f, 420f, 85f, 85f, paint)
        paint.color = 0xFFF8C9CD.toInt(); canvas.drawRoundRect(28f, 94f, 392f, 410f, 75f, 75f, paint)
        art.shape(canvas, Shape.SOFA, 328f, 118f, 59f, -12f)
        art.shape(canvas, Shape.DONUT, 80f, 130f, 34f, 15f)
        art.shape(canvas, Shape.CAR, 338f, 262f, 34f, 15f)
        art.shape(canvas, Shape.COOKIE, 78f, 277f, 27f, -20f)
        art.star(canvas, 134f, 75f, 15f, GluttonArt.GOLD)
        art.star(canvas, 288f, 233f, 12f, GluttonArt.GOLD)
        art.body(canvas, 202f, 194f, 100f, .4f, hungry = true, pop = .3f)
        art.shape(canvas, Shape.CHAIR, 150f, 250f, 26f, -25f)
        art.shape(canvas, Shape.DUCK, 262f, 142f, 22f, 20f)
        paint.shader = LinearGradient(0f, 300f, 0f, 420f, Color.TRANSPARENT, 0xE5695B7C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 300f, 420f, 420f, paint); canvas.restore()
    }
}
