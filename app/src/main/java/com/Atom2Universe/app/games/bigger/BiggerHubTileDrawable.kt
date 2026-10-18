package com.Atom2Universe.app.games.bigger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min
import kotlin.random.Random

/**
 * Tuile du hub : un tas d'astres au fond du bocal, cuits par [AccretionSprites] — les
 * mêmes que dans la partie — et une étoile qui tombe dessus.
 */
class BiggerHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val art = AccretionSprites(context)

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF05060F.toInt(), 0xFF140A28.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.2f, h * 0.25f, w * 0.8f, 0x403A1C6E, 0x003A1C6E, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val rnd = Random(2024)
        repeat(70) {
            paint.color = Color.argb(60 + rnd.nextInt(160), 215, 220, 255)
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, min(w, h) * (0.002f + rnd.nextFloat() * 0.004f), paint)
        }

        // Le tas, posé sur une ligne à 62 % de la hauteur ; le titre occupe le bas.
        val u = min(w, h * 1.1f) / 10f
        val base = h * 0.62f
        fun body(tier: Int, x: Float, rise: Float) {
            val r = Accretion.RADIUS[tier] * u
            draw(canvas, tier, x, base - rise * u - r, r)
        }
        body(7, w * 0.26f, 0f)
        body(2, w * 0.5f, 0f)
        body(5, w * 0.72f, 0f)
        body(3, w * 0.5f, 1.05f)
        body(0, w * 0.93f, 0f)
        body(6, w * 0.9f, 1.3f)
        body(1, w * 0.07f, 0f)
        // L'étoile qui arrive, avec sa traînée.
        val sx = w * 0.56f; val sy = h * 0.14f
        paint.shader = LinearGradient(sx - u * 1.6f, sy - u * 1.6f, sx, sy, 0x00FFD54A, 0x80FFD54A.toInt(), Shader.TileMode.CLAMP)
        paint.strokeWidth = u * 0.6f; paint.strokeCap = Paint.Cap.ROUND; paint.style = Paint.Style.STROKE
        canvas.drawLine(sx - u * 1.6f, sy - u * 1.6f, sx, sy, paint)
        paint.shader = null; paint.style = Paint.Style.FILL
        draw(canvas, 9, sx, sy, u * 0.75f)

        paint.shader = LinearGradient(0f, h * 0.56f, 0f, h,
            Color.TRANSPARENT, 0xEF05060F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.56f, w, h, paint)
    }

    private fun draw(canvas: Canvas, tier: Int, x: Float, y: Float, r: Float) {
        val bmp = art.composite(tier, r)
        canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f, null)
        bmp.recycle()
    }
}
