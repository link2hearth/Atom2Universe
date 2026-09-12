package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.random.Random

/**
 * Illustration de la tuile Clicker : quelques atomes stylises de "Atom low", eclates sur un fond
 * bleu profond parseme d'etoiles. Calculee une fois par taille, comme les autres tuiles illustrees :
 * la tuile du hub et le raccourci du hub principal n'ont pas la meme forme, chacun a son rendu.
 */
class ClickerHubTileDrawable(private val context: Context) : Drawable() {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var cachedBitmap: Bitmap? = null

    /** Position en fraction de la tuile, taille en fraction de son plus petit cote, angle en degres. */
    private class Placement(val file: String, val x: Float, val y: Float, val size: Float, val angle: Float)

    // Cinq atomes seulement, pas toute la serie : ceux dont les couleurs se distinguent le mieux
    // sur le bleu. Tailles et angles differents, pour qu'ils aient l'air eparpilles et pas alignes.
    // La bande du milieu reste libre : c'est la que le hub pose le titre et la description.
    private val placements = listOf(
        Placement("Atom4.png", 0.22f, 0.26f, 0.48f, -14f),
        Placement("Atom0.png", 0.78f, 0.22f, 0.40f, 18f),
        Placement("Atom8.png", 0.52f, 0.90f, 0.30f, -8f),
        Placement("Atom3.png", 0.12f, 0.82f, 0.28f, 26f),
        Placement("Atom7.png", 0.88f, 0.80f, 0.26f, -24f)
    )

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val w = bounds.width()
        val h = bounds.height()
        val bitmap = cachedBitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                render(Canvas(it), w.toFloat(), h.toFloat())
                cachedBitmap?.recycle()
                cachedBitmap = it
            }
        canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), bitmapPaint)
    }

    private fun render(canvas: Canvas, w: Float, h: Float) {
        // Le bleu de la tuile Clicker (#01579B) au centre, plus clair en haut, plus profond en bas.
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(2, 119, 189), Color.rgb(1, 87, 155), Color.rgb(10, 36, 88)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

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

            val atom = loadAtom(p.file) ?: return@forEach
            canvas.save()
            canvas.rotate(p.angle, cx, cy)
            rect.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawBitmap(atom, null, rect, atomPaint)
            canvas.restore()
            atom.recycle()
        }
    }

    private fun loadAtom(file: String): Bitmap? = runCatching {
        context.assets.open("Assets/Image/Atom low/$file").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

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
