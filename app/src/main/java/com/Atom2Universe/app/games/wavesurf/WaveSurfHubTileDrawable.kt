package com.Atom2Universe.app.games.wavesurf

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Instantané du surf : grandes ondes et particule lumineuse, sans boucle de jeu. */
class WaveSurfHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val unit = min(w, h)
        paint.shader = LinearGradient(0f, 0f, w, h,
            0xFF080E24.toInt(), 0xFF142C49.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = null
        repeat(23) { i ->
            val x = ((i * 37 + 11) % 101) / 100f * w
            val y = ((i * 19 + 7) % 47) / 100f * h
            paint.color = Color.argb(80 + i % 4 * 35, 196, 225, 255)
            canvas.drawCircle(x, y, unit * if (i % 5 == 0) .006f else .003f, paint)
        }

        // Onde lointaine, discrète pour garder la trajectoire principale lisible.
        path.moveTo(0f, h * .43f)
        path.cubicTo(w * .17f, h * .18f, w * .27f, h * .22f, w * .43f, h * .43f)
        path.cubicTo(w * .64f, h * .71f, w * .84f, h * .21f, w, h * .29f)
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        paint.shader = LinearGradient(0f, h * .2f, 0f, h,
            0xFF3A4277.toInt(), 0xFF101831.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)

        // Long creux suivi d'une bosse plus haute, comme le relief du jeu.
        path.rewind()
        path.moveTo(-w * .05f, h * .40f)
        path.cubicTo(w * .10f, h * .40f, w * .10f, h * .63f, w * .28f, h * .63f)
        path.cubicTo(w * .47f, h * .63f, w * .53f, h * .32f, w * .74f, h * .32f)
        path.cubicTo(w * .89f, h * .32f, w * .89f, h * .58f, w * 1.05f, h * .58f)
        val ridge = Path(path)
        path.lineTo(w * 1.05f, h); path.lineTo(-w * .05f, h); path.close()
        paint.shader = LinearGradient(0f, 0f, w, 0f,
            intArrayOf(0xFF7152CE.toInt(), 0xFF297ECC.toInt(), 0xFF25B5AA.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = LinearGradient(0f, h * .32f, 0f, h,
            Color.TRANSPARENT, 0xED081329.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = unit * .025f
        paint.color = 0x304FCFFF
        canvas.drawPath(ridge, paint)
        paint.strokeWidth = unit * .008f
        paint.color = 0xFFA5F4FF.toInt()
        canvas.drawPath(ridge, paint)
        paint.style = Paint.Style.FILL

        // La traînée suit exactement la courbe de la montée.
        val radius = unit * .043f
        fun point(t: Float): Pair<Float, Float> {
            val s = 1f - t
            val x = s * s * s * .28f + 3f * s * s * t * .47f + 3f * s * t * t * .53f + t * t * t * .74f
            val y = s * s * s * .63f + 3f * s * s * t * .63f + 3f * s * t * t * .32f + t * t * t * .32f
            return x * w to y * h - radius * .85f
        }
        repeat(13) { i ->
            val progress = i / 13f
            val (x, y) = point(.10f + progress * .64f)
            paint.color = Color.argb((25 + progress * 130).toInt(), 126, 229, 255)
            canvas.drawCircle(x, y, radius * (.15f + progress * .55f), paint)
        }
        val (bx, by) = point(.78f)
        paint.shader = RadialGradient(bx, by, radius * 3f,
            intArrayOf(0xB05ADCFF.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, radius * 3f, paint)
        paint.shader = RadialGradient(bx - radius * .3f, by - radius * .3f, radius * 1.3f,
            intArrayOf(Color.WHITE, 0xFF8FECFF.toInt(), 0xFF3C9CFF.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, radius, paint)

        // Même réserve sombre pour le titre en bas que les autres tuiles illustrées.
        paint.shader = LinearGradient(0f, h * .58f, 0f, h,
            Color.TRANSPARENT, 0xDC071020.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .58f, w, h, paint)
    }
}
