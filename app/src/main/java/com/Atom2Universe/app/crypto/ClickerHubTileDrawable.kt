package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.util.LruCache
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import kotlin.math.roundToInt
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.random.Random

/**
 * Illustration de la tuile Clicker : quelques atomes dessines en Kotlin, eclates sur un fond
 * noir parseme d'etoiles. Calculee une fois par taille, comme les autres tuiles illustrees :
 * la tuile du hub et le raccourci du hub principal n'ont pas la meme forme, chacun a son rendu.
 */
class ClickerHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : Drawable() {
    companion object {
        private val images = object : LruCache<Long, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
        }
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)




    /** Position en fraction de la tuile, taille en fraction de son plus petit cote, angle en degres. */
    private class Placement(val variant: Int, val x: Float, val y: Float, val size: Float, val angle: Float)

    // Neuf atomes fixes ; le bas reste libre pour le titre agrandi.
    private val placements = listOf(
        Placement(4, 0.19f, 0.27f, 0.55f, -14f),
        Placement(0, 0.80f, 0.23f, 0.42f, 18f),
        Placement(8, 0.50f, 0.24f, 0.44f, -8f),
        Placement(3, 0.08f, 0.65f, 0.30f, 26f),
        Placement(7, 0.91f, 0.66f, 0.32f, -24f),
        Placement(2, 0.35f, 0.46f, 0.30f, 12f),
        Placement(6, 0.66f, 0.48f, 0.33f, -18f),
        Placement(1, 0.35f, 0.10f, 0.24f, 22f),
        Placement(5, 0.66f, 0.10f, 0.25f, -12f)
    )
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = min(1f, 512f / maxOf(bounds.width(), bounds.height()))
        val w = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val h = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val key = (w.toLong() shl 32) or h.toLong()
        val bitmap = images.get(key) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            render(Canvas(it), w.toFloat(), h.toFloat())
            images.put(key, it)
        }
        canvas.drawBitmap(bitmap, null, bounds, bitmapPaint)
    }

    private fun render(canvas: Canvas, w: Float, h: Float) {
        val atomRenderer = com.Atom2Universe.app.crypto.clicker.AnimatedAtomRenderer()
        canvas.drawColor(Color.BLACK)
        val unit = min(w, h)
        // Etoiles tirees d'une graine fixe : la tuile est identique a chaque rendu.
        val random = Random(2026)
        repeat(46) {
            // Petites et discretes : plus grosses, elles faisaient neige plutot que ciel etoile.
            paint.color = Color.argb(40 + random.nextInt(90), 255, 255, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h,
                unit * (0.002f + random.nextFloat() * 0.004f), paint)
        }

        placements.forEach { p ->
            val cx = w * p.x
            val cy = h * p.y
            val half = unit * p.size / 2
            // Un halo doux derriere chaque atome le detache du fond.
            paint.shader = RadialGradient(cx, cy, half * 1.1f,
                Color.argb(80, 180, 225, 255), Color.argb(0, 180, 225, 255), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, half * 1.1f, paint)
            paint.shader = null

            canvas.save()
            canvas.rotate(p.angle, cx, cy)

            atomRenderer.draw(canvas, cx, cy, half * 2f, p.variant, p.variant * 0.7f)
            canvas.restore()
        }
    }

    override fun setAlpha(alpha: Int) {
        bitmapPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
