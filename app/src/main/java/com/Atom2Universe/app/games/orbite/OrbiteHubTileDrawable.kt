package com.Atom2Universe.app.games.orbite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Scène fixe reprenant le noyau, l'électron et les dangers du jeu. */
class OrbiteHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        val cx = w * .5f
        val cy = h * .38f
        val orbit = unit * .285f
        p.shader = RadialGradient(cx, cy, maxOf(w, h) * .8f,
            0xFF193954.toInt(), 0xFF060914.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val stars = Random(42)
        repeat(65) {
            p.color = Color.argb(stars.nextInt(55, 160), 200, 220, 255)
            canvas.drawCircle(stars.nextFloat() * w, stars.nextFloat() * h,
                unit * (.0015f + stars.nextFloat() * .002f), p)
        }
        fun glow(x: Float, y: Float, radius: Float, color: Int) {
            p.shader = RadialGradient(x, y, radius,
                color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, p)
            p.shader = null
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = unit * .007f
        p.color = 0xFF2A5778.toInt()
        canvas.drawCircle(cx, cy, orbit, p)
        p.style = Paint.Style.FILL

        // Traînée courbe, puis électron au premier plan.
        repeat(32) { i ->
            val t = i / 31f
            val a = -2.3f + t * 1.7f
            p.color = Color.argb((t * 165).toInt(), 54, 225, 255)
            canvas.drawCircle(cx + cos(a) * orbit, cy + sin(a) * orbit,
                unit * (.003f + t * .012f), p)
        }
        val ex = cx + cos(-.6f) * orbit
        val ey = cy + sin(-.6f) * orbit
        glow(ex, ey, unit * .085f, 0xB036E1FF.toInt())
        p.color = 0xFF36E1FF.toInt()
        canvas.drawCircle(ex, ey, unit * .026f, p)
        p.color = Color.WHITE
        canvas.drawCircle(ex, ey, unit * .012f, p)

        val nucleus = unit * .085f
        glow(cx, cy, nucleus * 2.8f, 0xB0FF9D47.toInt())
        p.color = 0xFFFFB347.toInt()
        canvas.drawCircle(cx, cy, nucleus, p)
        p.color = 0xFFFF6B35.toInt()
        canvas.drawCircle(cx, cy, nucleus * .64f, p)
        p.color = 0xFFFFE6C8.toInt()
        repeat(5) { i ->
            val a = i * 1.2566f + .4f
            canvas.drawCircle(cx + cos(a) * nucleus * .46f, cy + sin(a) * nucleus * .46f,
                nucleus * .15f, p)
        }
        for (angle in floatArrayOf(.55f, 2.1f, 3.6f)) {
            val x = cx + cos(angle) * orbit
            val y = cy + sin(angle) * orbit
            glow(x, y, unit * .055f, 0x807CFFCB.toInt())
            p.color = 0xFF7CFFCB.toInt()
            val r = unit * .015f
            canvas.save()
            canvas.rotate(45f, x, y)
            canvas.drawRect(x - r, y - r, x + r, y + r, p)
            canvas.restore()
        }
        for (angle in floatArrayOf(-2.6f, .2f)) {
            val x = cx + cos(angle) * orbit * 1.4f
            val y = cy + sin(angle) * orbit * 1.4f
            val r = unit * .027f
            p.color = 0x60FF5A6E
            p.strokeWidth = r * .9f
            p.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, y, x + cos(angle) * r * 4, y + sin(angle) * r * 4, p)
            glow(x, y, r * 2.5f, 0x80FF5A6E.toInt())
            p.color = 0xFFFF5A6E.toInt()
            canvas.drawCircle(x, y, r, p)
            p.color = 0xFFAB374F.toInt()
            canvas.drawCircle(x + r * .3f, y - r * .2f, r * .36f, p)
        }
        p.shader = LinearGradient(0f, h * .58f, 0f, h,
            Color.TRANSPARENT, 0xEF060914.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .58f, w, h, p)
    }
}
