package com.Atom2Universe.app.science.solarsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Illustration de la tuile Système solaire : le Soleil dans le coin, les orbites vues de
 * biais, et quatre planètes posées dessus, peintes avec les textures du module. Chacune est
 * éclairée du côté du Soleil.
 */
class SolarSystemHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {

    private val appContext = context.applicationContext

    private class Body(val texture: String, val fallback: Int, val orbit: Float, val angle: Float,
                       val radius: Float, val rings: Boolean = false)

    private val bodies = listOf(
        Body("textures/planets/mars.jpg", 0xFFC1440E.toInt(), 0.30f, 55f, 0.028f),
        Body("textures/earth.jpg", 0xFF4A8EBA.toInt(), 0.46f, 38f, 0.042f),
        Body("textures/planets/jupiter.jpg", 0xFFC88B3A.toInt(), 0.66f, 20f, 0.085f),
        Body("textures/planets/saturn.jpg", 0xFFE4D191.toInt(), 0.92f, 44f, 0.068f, rings = true)
    )

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(Color.BLACK)
        ScienceTileArt.stars(canvas, w, h, 70, 1969)
        val unit = min(w, h)

        // Le Soleil, en haut à gauche, à moitié hors cadre.
        val sunX = w * 0.08f
        val sunY = h * 0.12f
        ScienceTileArt.glow(canvas, sunX, sunY, unit * 0.55f, 0xFFFFB347.toInt(), 110)
        ScienceTileArt.glow(canvas, sunX, sunY, unit * 0.24f, 0xFFFFE08A.toInt(), 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFFFF4D6.toInt()
        canvas.drawCircle(sunX, sunY, unit * 0.13f, paint)

        // Les orbites : des ellipses aplaties centrées sur le Soleil.
        val squash = 0.55f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * 0.004f
        paint.color = Color.argb(60, 200, 215, 255)
        bodies.forEach { b ->
            val rx = unit * b.orbit * 1.25f
            canvas.drawOval(RectF(sunX - rx, sunY - rx * squash, sunX + rx, sunY + rx * squash), paint)
        }
        paint.style = Paint.Style.FILL

        bodies.forEach { b ->
            val rx = unit * b.orbit * 1.25f
            // L'angle est compté depuis l'horizontale, vers le bas : les planètes descendent en diagonale.
            val rad = Math.toRadians(b.angle.toDouble())
            val x = sunX + rx * cos(rad).toFloat()
            val y = sunY + rx * squash * sin(rad).toFloat()
            val r = unit * b.radius
            val lightX = sunX - x
            val lightY = sunY - y
            val texture = ScienceTileArt.loadTexture(appContext, b.texture)
            if (b.rings) ring(canvas, x, y, r, back = true)
            ScienceTileArt.planet(canvas, texture, b.fallback, x, y, r, lightX, lightY, spin = 0.1f)
            if (b.rings) ring(canvas, x, y, r, back = false)
        }

        ScienceTileArt.bottomShade(canvas, w, h, Color.BLACK, from = 0.58f)
    }

    /** L'anneau de Saturne en deux moitiés : l'arrière passe sous la planète, l'avant dessus. */
    private fun ring(canvas: Canvas, x: Float, y: Float, r: Float, back: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r * 0.28f
            color = 0xCCC8B08C.toInt()
        }
        canvas.save()
        canvas.rotate(-18f, x, y)
        val oval = RectF(x - r * 2.0f, y - r * 0.55f, x + r * 2.0f, y + r * 0.55f)
        canvas.drawArc(oval, if (back) 180f else 0f, 180f, false, paint)
        canvas.restore()
    }
}
