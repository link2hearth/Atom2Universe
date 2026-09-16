package com.Atom2Universe.app.games.flappycat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class JumpingCatHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val appContext = context.applicationContext

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = CatArt(appContext)
        val unit = min(w, h)
        canvas.save()
        canvas.scale(w / 420f, h / 560f)
        art.background(canvas, 120f, .8f)
        art.pole(canvas, 355f, 260f)
        canvas.restore()
        // Personnages à échelle uniforme, indépendamment du format de la tuile.
        canvas.save()
        canvas.translate(w * .46f, h * .32f)
        val scale = unit * .008f
        canvas.scale(scale, scale)
        art.cat(canvas, 0f, 0f, .8f, -180f, .25f, false, false)
        canvas.restore()
        canvas.save()
        canvas.translate(w * .76f, h * .23f)
        canvas.scale(unit * .004f, unit * .004f)
        art.bird(canvas, 0f, 0f, .8f, 0)
        canvas.restore()
        val paint = Paint().apply {
            shader = LinearGradient(0f, h * .52f, 0f, h,
                Color.TRANSPARENT, 0xE025243D.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .52f, w, h, paint)
    }
}
