package com.Atom2Universe.app.games.starbridges

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/**
 * Tuile du hub : un bout de ciel nocturne et deux constellations tracées à l'or, avec les
 * mêmes étoiles et les mêmes traits que dans le jeu.
 */
class StarBridgesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    /** Deux figures en coordonnées de tuile (0..1) : x, y, nombre de liens. */
    private val figures = listOf(
        floatArrayOf(0.12f, 0.20f, 1f, 0.30f, 0.12f, 3f, 0.46f, 0.26f, 2f, 0.34f, 0.40f, 2f, 0.20f, 0.46f, 1f) to
            intArrayOf(0, 1, 1, 2, 1, 3, 3, 4),
        floatArrayOf(0.62f, 0.10f, 1f, 0.76f, 0.22f, 4f, 0.92f, 0.14f, 1f, 0.88f, 0.40f, 2f, 0.66f, 0.38f, 2f, 0.58f, 0.52f, 1f) to
            intArrayOf(0, 1, 1, 2, 1, 3, 1, 4, 4, 5)
    )

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val density = min(w, h) / 180f
        NightSky.paint(canvas, w, h, 1977, density)
        val unit = min(w, h)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((stars, links) in figures) {
            for (k in 0 until links.size / 2) {
                val a = links[k * 2]; val b = links[k * 2 + 1]
                val x1 = stars[a * 3] * w; val y1 = stars[a * 3 + 1] * h
                val x2 = stars[b * 3] * w; val y2 = stars[b * 3 + 1] * h
                line.color = (NightSky.LINE and 0xFFFFFF) or (0x40 shl 24); line.strokeWidth = unit * 0.022f
                canvas.drawLine(x1, y1, x2, y2, line)
                line.color = (NightSky.LINE and 0xFFFFFF) or (0xE0 shl 24); line.strokeWidth = unit * 0.007f
                canvas.drawLine(x1, y1, x2, y2, line)
            }
            for (i in 0 until stars.size / 3) {
                val x = stars[i * 3] * w; val y = stars[i * 3 + 1] * h
                val n = stars[i * 3 + 2].toInt()
                val r = unit * (0.012f + n * 0.004f)
                glow.shader = NightSky.glowShader(n)
                canvas.save(); canvas.translate(x, y); canvas.scale(r * 4f, r * 4f)
                canvas.drawCircle(0f, 0f, 1f, glow)
                canvas.restore()
                fill.color = NightSky.starColor(n)
                canvas.drawCircle(x, y, r, fill)
            }
        }
        val fade = Paint()
        fade.shader = LinearGradient(0f, h * 0.56f, 0f, h, Color.TRANSPARENT, 0xEF060A1C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.56f, w, h, fade)
    }
}
