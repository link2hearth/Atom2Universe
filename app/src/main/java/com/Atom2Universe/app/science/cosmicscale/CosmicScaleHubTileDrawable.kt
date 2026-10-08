package com.Atom2Universe.app.science.cosmicscale

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.min

/**
 * Illustration de la tuile Échelle cosmique : des corps du module rangés du plus petit au plus
 * grand — la Lune, Mars, la Terre, Neptune, Jupiter — et le Soleil dont on ne voit qu'un bord,
 * trop grand pour tenir dans la tuile. C'est toute l'idée du module, en une image.
 */
class CosmicScaleHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {

    private val appContext = context.applicationContext

    private class Body(val texture: String, val fallback: Int, val x: Float, val radius: Float)

    // Tailles relatives lissées : à l'échelle réelle, la Lune serait un point à côté de Jupiter.
    private val bodies = listOf(
        Body("textures/moon.jpg", 0xFFCCCCCC.toInt(), 0.07f, 0.030f),
        Body("textures/planets/mars.jpg", 0xFFC1440E.toInt(), 0.16f, 0.042f),
        Body("textures/earth.jpg", 0xFF4A8EBA.toInt(), 0.29f, 0.064f),
        Body("textures/planets/neptune.jpg", 0xFF96C4D1.toInt(), 0.47f, 0.105f),
        Body("textures/planets/jupiter.jpg", 0xFFC88B3A.toInt(), 0.73f, 0.165f)
    )

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF03040A.toInt())
        ScienceTileArt.stars(canvas, w, h, 80, 314)
        val unit = min(w, h)

        // Le bord du Soleil, à droite : un arc immense qui sort du cadre.
        val sunR = unit * 1.3f
        val sunX = w + sunR * 0.78f
        val sunY = h * 0.34f
        ScienceTileArt.glow(canvas, sunX, sunY, sunR * 1.25f, 0xFFFF9A3C.toInt(), 150)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFFFC46B.toInt()
        canvas.drawCircle(sunX, sunY, sunR, paint)
        paint.color = 0xFFFFE3A3.toInt()
        canvas.drawCircle(sunX + sunR * 0.05f, sunY, sunR * 0.93f, paint)

        // Les corps alignés sur l'horizon, posés sur la même ligne de base.
        val baseline = h * 0.52f
        bodies.forEach { b ->
            val r = unit * b.radius
            val texture = ScienceTileArt.loadTexture(appContext, b.texture)
            ScienceTileArt.planet(canvas, texture, b.fallback, w * b.x, baseline - r, r,
                lightX = 1f, lightY = -0.2f, spin = 0.15f)
        }
        // Une fine règle sous la rangée : on compare des tailles.
        paint.color = Color.argb(70, 200, 215, 255)
        paint.strokeWidth = unit * 0.004f
        canvas.drawLine(w * 0.03f, baseline + unit * 0.02f, w * 0.92f, baseline + unit * 0.02f, paint)

        ScienceTileArt.bottomShade(canvas, w, h, 0xFF03040A.toInt(), from = 0.6f)
    }
}
