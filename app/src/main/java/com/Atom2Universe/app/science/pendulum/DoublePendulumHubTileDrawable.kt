package com.Atom2Universe.app.science.pendulum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Illustration de la tuile Double pendule : la trace chaotique du bob inférieur, peinte avec la
 * palette du module, et le pendule lui-même à la fin de sa course.
 *
 * La trace est une vraie simulation (mêmes équations, masses et bras égaux) lancée depuis un
 * angle fixe : le dessin est donc identique à chaque rendu, et c'est bien le chaos du module
 * qu'on voit, pas une courbe inventée.
 */
class DoublePendulumHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    private companion object {
        const val BACKGROUND = 0xFF0D0D1A.toInt()
        const val ROD = 0xFF888899.toInt()
        const val STEPS = 2600
        const val DT = 0.006
        const val G = 9.81

        /** Les couleurs des pendules du module, dans l'ordre où la trace les traverse. */
        val PALETTE = intArrayOf(
            0xFF4D96FF.toInt(), 0xFF00F5D4.toInt(), 0xFF6BCB77.toInt(), 0xFFFFD93D.toInt(),
            0xFFFF9F1C.toInt(), 0xFFFF6B6B.toInt(), 0xFFC77DFF.toInt()
        )
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(BACKGROUND)
        val unit = min(w, h)
        val arm = unit * 0.19f
        val pivotX = w * 0.5f
        val pivotY = h * 0.40f

        // Intégration (Runge-Kutta 4) du double pendule, bras de longueur 1, masses 1.
        // L'état : angle 1, angle 2, vitesse 1, vitesse 2.
        fun derive(s: DoubleArray): DoubleArray {
            val d = s[0] - s[1]
            val den = 3 - cos(2 * d)
            val a1 = (-3 * G * sin(s[0]) - G * sin(s[0] - 2 * s[1]) -
                2 * sin(d) * (s[3] * s[3] + s[2] * s[2] * cos(d))) / den
            val a2 = (2 * sin(d) * (2 * s[2] * s[2] + 2 * G * cos(s[0]) + s[3] * s[3] * cos(d))) / den
            return doubleArrayOf(s[2], s[3], a1, a2)
        }
        fun plus(s: DoubleArray, k: DoubleArray, f: Double) = DoubleArray(4) { s[it] + k[it] * f }
        var state = doubleArrayOf(2.4, 2.9, 0.0, 0.0)
        val xs = FloatArray(STEPS)
        val ys = FloatArray(STEPS)
        for (i in 0 until STEPS) {
            val k1 = derive(state)
            val k2 = derive(plus(state, k1, DT / 2))
            val k3 = derive(plus(state, k2, DT / 2))
            val k4 = derive(plus(state, k3, DT))
            state = DoubleArray(4) { state[it] + (k1[it] + 2 * k2[it] + 2 * k3[it] + k4[it]) * DT / 6 }
            xs[i] = pivotX + arm * (sin(state[0]) + sin(state[1])).toFloat()
            ys[i] = pivotY + arm * (cos(state[0]) + cos(state[1])).toFloat()
        }
        val t1 = state[0]

        // La trace : plus ancienne = plus pâle, la couleur glisse le long de la palette.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = unit * 0.006f
        }
        for (i in 1 until STEPS) {
            val t = i / STEPS.toFloat()
            val pos = t * (PALETTE.size - 1)
            val k = pos.toInt().coerceAtMost(PALETTE.size - 2)
            val color = blend(PALETTE[k], PALETTE[k + 1], pos - k)
            paint.color = (color and 0x00FFFFFF) or ((40 + 200 * t).toInt() shl 24)
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint)
        }

        // Le pendule à la fin de la trace.
        val x1 = pivotX + arm * sin(t1).toFloat()
        val y1 = pivotY + arm * cos(t1).toFloat()
        val x2 = xs[STEPS - 1]
        val y2 = ys[STEPS - 1]
        paint.color = ROD
        paint.strokeWidth = unit * 0.012f
        canvas.drawLine(pivotX, pivotY, x1, y1, paint)
        canvas.drawLine(x1, y1, x2, y2, paint)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROD }
        canvas.drawCircle(pivotX, pivotY, unit * 0.014f, fill)
        ScienceTileArt.glow(canvas, x2, y2, unit * 0.09f, PALETTE.last(), 120)
        fill.color = Color.WHITE
        canvas.drawCircle(x1, y1, unit * 0.028f, fill)
        canvas.drawCircle(x2, y2, unit * 0.032f, fill)

        ScienceTileArt.bottomShade(canvas, w, h, BACKGROUND, from = 0.58f)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}
