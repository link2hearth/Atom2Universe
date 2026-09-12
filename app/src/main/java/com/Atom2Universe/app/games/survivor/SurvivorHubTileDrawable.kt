package com.Atom2Universe.app.games.survivor

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
import kotlin.math.min

/** Illustration du hub composée avec le rendu réel du jeu, calculée une fois par taille. */
class SurvivorHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : Drawable() {
    private val art = SurvivorArt()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedBitmap: Bitmap? = null

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val w = bounds.width()
        val h = bounds.height()
        val bitmap = cachedBitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                render(Canvas(it), w.toFloat(), h.toFloat())
                cachedBitmap = it
            }
        canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), bitmapPaint)
    }

    private fun render(canvas: Canvas, w: Float, h: Float) {
        art.terrain(canvas, 120f, 80f, 1.4f)
        backdrop.shader = LinearGradient(0f, 0f, w, h,
            0x403A887D, 0x00213A39, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, backdrop)
        backdrop.shader = null
        val size = min(w, h)
        val heroX = w * 0.5f
        val heroY = h * 0.4f

        fun creature(type: EnemyType, x: Float, y: Float, radius: Float, variant: Int, palette: Int = 0) {
            val enemy = SEnemy(x, y, 1f, 1f, 0f, 0f, 0f, radius / 1.65f, type).apply {
                visualVariant = variant
                visualPalette = palette
            }
            art.enemy(canvas, enemy, x, y, 1.4f, heroX, heroY)
        }

        // Les grandes silhouettes encadrent le gardien ; le bas reste sombre pour les libellés.
        creature(EnemyType.MINI_BOSS, w * 0.24f, h * 0.25f, size * 0.17f, 1)
        creature(EnemyType.MINI_BOSS, w * 0.77f, h * 0.25f, size * 0.16f, 2)
        creature(EnemyType.ZOMBIE, w * 0.13f, h * 0.53f, size * 0.105f, 1)
        creature(EnemyType.FAST, w * 0.86f, h * 0.53f, size * 0.10f, 1)
        creature(EnemyType.SHOOTER, w * 0.34f, h * 0.62f, size * 0.095f, 1)
        creature(EnemyType.ORBITER, w * 0.67f, h * 0.62f, size * 0.09f, 1)
        art.player(canvas, heroX, heroY, size * 0.12f, 1.4f, false)

        backdrop.shader = LinearGradient(0f, h * 0.28f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xB008171A.toInt(), 0xF008171A.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.28f, w, h, backdrop)
        backdrop.shader = null
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
