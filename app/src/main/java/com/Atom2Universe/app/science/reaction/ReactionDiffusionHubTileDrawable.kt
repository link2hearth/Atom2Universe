package com.Atom2Universe.app.science.reaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Illustration de la tuile Réaction-diffusion : le labyrinthe de Turing que fait naître le
 * module, avec sa palette « océan » (nuit, bleu profond, cyan, écume).
 *
 * Le motif n'est pas simulé : une somme d'ondes de même longueur, dans toutes les directions,
 * seuillée en douceur, donne exactement ce dessin de méandres — pour une fraction du coût
 * d'une vraie simulation, qui gèlerait le hub à l'ouverture.
 */
class ReactionDiffusionHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    private companion object {
        const val GRID = 160
        const val WAVES = 22

        /** La palette « océan » du module. */
        val STOPS = floatArrayOf(0f, 0.40f, 0.70f, 1f)
        val COLORS = intArrayOf(0xFF02071A.toInt(), 0xFF053B7A.toInt(), 0xFF1CA7C4.toInt(), 0xFFE8FFFF.toInt())
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val cols = GRID
        val rows = (GRID * h / w).toInt().coerceAtLeast(1)
        val random = Random(90210)
        val frequency = 2 * PI / 9.0 // une longueur d'onde de 9 cases
        val kx = DoubleArray(WAVES)
        val ky = DoubleArray(WAVES)
        val phase = DoubleArray(WAVES)
        for (i in 0 until WAVES) {
            val angle = random.nextDouble() * PI
            kx[i] = cos(angle) * frequency
            ky[i] = sin(angle) * frequency
            phase[i] = random.nextDouble() * 2 * PI
        }
        val pixels = IntArray(cols * rows)
        for (y in 0 until rows) for (x in 0 until cols) {
            var sum = 0.0
            for (i in 0 until WAVES) sum += cos(kx[i] * x + ky[i] * y + phase[i])
            // Seuil adouci : des bandes nettes, bordées d'un dégradé comme dans le module.
            val v = (0.5 + sum / WAVES * 2.2).coerceIn(0.0, 1.0).toFloat()
            val shaped = v * v * (3 - 2 * v)
            pixels[y * cols + x] = palette(shaped)
        }
        val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()

        ScienceTileArt.bottomShade(canvas, w, h, COLORS[0], from = 0.5f)
    }

    private fun palette(t: Float): Int {
        var k = 0
        while (k < STOPS.size - 2 && t > STOPS[k + 1]) k++
        val f = ((t - STOPS[k]) / (STOPS[k + 1] - STOPS[k])).coerceIn(0f, 1f)
        val a = COLORS[k]
        val b = COLORS[k + 1]
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }
}
