package com.Atom2Universe.app.science.boids

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Illustration de la tuile Boids : une nuée qui serpente à travers la tuile. Mêmes flèches, même
 * fond nuit et même coloration « direction » que le module : chaque boid prend la teinte de son
 * cap, si bien que la courbe de la nuée se lit en arc-en-ciel.
 */
class BoidsHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    private companion object {
        const val TOP = 0xFF0A0E1C.toInt()
        const val BOTTOM = 0xFF101626.toInt()
        const val COUNT = 90
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, TOP, BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val unit = min(w, h)
        val size = unit * 0.028f
        val dart = Path().apply {
            moveTo(size, 0f)
            lineTo(-size * 0.75f, size * 0.55f)
            lineTo(-size * 0.4f, 0f)
            lineTo(-size * 0.75f, -size * 0.55f)
            close()
        }

        // La nuée suit une vague qui traverse la tuile de gauche à droite, un peu au-dessus du milieu.
        fun streamY(x: Float) = h * 0.36f + h * 0.16f * sin(x / w * 6.6f).toFloat()
        val random = Random(4242)
        repeat(COUNT) {
            val t = random.nextFloat()
            val x = -w * 0.05f + t * w * 1.1f
            // Plus dense au cœur de la nuée, quelques éclaireurs sur les bords.
            val spread = (random.nextFloat() - 0.5f) * (random.nextFloat() + 0.2f) * h * 0.30f
            val y = streamY(x) + spread
            val slope = (streamY(x + 1f) - streamY(x))
            val heading = atan2(slope, 1f) + (random.nextFloat() - 0.5f) * 0.5f
            val hue = ((Math.toDegrees(heading.toDouble()) + 360) % 360).toFloat()
            paint.color = Color.HSVToColor(floatArrayOf(hue, 0.72f, 1f))
            val scale = 0.75f + random.nextFloat() * 0.45f
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(Math.toDegrees(heading.toDouble()).toFloat())
            canvas.scale(scale, scale)
            canvas.drawPath(dart, paint)
            canvas.restore()
        }
        // Un léger sillage devant la nuée, comme la traînée du module.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * 0.004f
        paint.color = Color.argb(45, 255, 255, 255)
        val wake = Path()
        var first = true
        var x = 0f
        while (x <= w) {
            val y = streamY(x) + unit * 0.09f * cos(x / w * 3f).toFloat()
            if (first) wake.moveTo(x, y) else wake.lineTo(x, y)
            first = false
            x += w / 60f
        }
        canvas.drawPath(wake, paint)

        ScienceTileArt.bottomShade(canvas, w, h, BOTTOM, from = 0.58f)
    }
}
