package com.Atom2Universe.app.games.cosmorun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** La tuile utilise les mêmes modèles que la course, mis en cache par le hub. */
class CosmoRunHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val game = CosmoRunGame().apply {
            enterHangar()
            entities.add(CosmoRunGame.Entity(CosmoRunGame.EntityType.CARGO, 0, 8f))
            entities.add(CosmoRunGame.Entity(CosmoRunGame.EntityType.LASER, 4, 15f))
            entities.add(CosmoRunGame.Entity(CosmoRunGame.EntityType.HURDLE, 1, 22f))
            for (i in 0..5) entities.add(CosmoRunGame.Entity(CosmoRunGame.EntityType.ATOM, 2, 5f + i * 4f))
        }
        CosmoRunRenderer().draw(canvas, w.toInt(), h.toInt(), game, .3f, true)
        val shade = Paint().apply {
            shader = LinearGradient(0f, h * .55f, 0f, h, Color.TRANSPARENT,
                0xE0091124.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .55f, w, h, shade)
    }
}
