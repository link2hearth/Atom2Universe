package com.Atom2Universe.app.games.starswar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Décor statique : rendu du jeu utilisé une fois, puis seule l'image est conservée.
 * Cache partagé entre les hubs et leurs recréations, borné à 2 Mio, sans contexte d'activité.
 */
class SpaceFightHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : Drawable() {
    companion object {
        private const val MAX_EDGE = 512
        private val images = object : LruCache<Long, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = min(1f, MAX_EDGE.toFloat() / maxOf(bounds.width(), bounds.height()))
        val w = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val h = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val key = (w.toLong() shl 32) or h.toLong()
        val bitmap = images.get(key) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            render(Canvas(it), w.toFloat(), h.toFloat())
            images.put(key, it)
        }
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    private fun render(canvas: Canvas, w: Float, h: Float) {
        val art = SpaceFightArt()
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = min(w, h)
        canvas.save()
        canvas.scale(w / 480f, h / 720f)
        art.background(canvas, 1)
        canvas.restore()

        val random = Random(7319)
        repeat(65) { i ->
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            brush.color = SpaceFightArt.alpha(SpaceFightArt.IVORY, 65 + random.nextInt(130))
            canvas.drawCircle(x, y, size * if (i % 7 == 0) .004f else .002f, brush)
        }
        art.sparkle(canvas, w * .12f, h * .19f, size * .018f, SpaceFightArt.MINT)
        art.sparkle(canvas, w * .88f, h * .62f, size * .014f, SpaceFightArt.GOLD)

        // Les silhouettes encadrent le titre central, y compris sur les raccourcis étroits.
        art.enemy(canvas, 1, w * .20f, h * .23f, size * .25f, .5f, 0f, 0f)
        art.enemy(canvas, 0, w * .73f, h * .20f, size * .22f, 1f, 0f, 0f)
        art.enemy(canvas, 4, w * .86f, h * .39f, size * .19f, 2f, 0f, 0f)
        art.player(canvas, w * .43f, h * .28f, size * .43f, -16f, true)

        // Bande douce derrière le libellé : aucun texte n'est intégré au bitmap.
        brush.shader = LinearGradient(0f, h * .35f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xC0141C31.toInt(), 0xE0141C31.toInt()),
            floatArrayOf(0f, .40f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .35f, w, h, brush)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
