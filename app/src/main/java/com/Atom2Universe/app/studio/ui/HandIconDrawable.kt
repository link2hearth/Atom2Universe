package com.Atom2Universe.app.studio.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Une main ouverte, tracée au pinceau : l'icône du bouton « Sélection ». Elle est dessinée en blanc ; le bouton qui la porte
 * la teinte (couleur du texte, accent quand la sélection est ouverte).
 */
class HandIconDrawable : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = minOf(b.width(), b.height()).toFloat()
        val u = size / 24f
        val x0 = b.exactCenterX() - 12f * u
        val y0 = b.exactCenterY() - 12f * u
        stroke.strokeWidth = 2.8f * u

        fun line(ax: Float, ay: Float, bx: Float, by: Float) =
            canvas.drawLine(x0 + ax * u, y0 + ay * u, x0 + bx * u, y0 + by * u, stroke)

        // Quatre doigts levés, de longueurs différentes, le pouce sur le côté
        line(8.5f, 12f, 8.5f, 5.5f)
        line(11.8f, 12f, 11.8f, 3.5f)
        line(15.1f, 12f, 15.1f, 4.5f)
        line(18.2f, 13f, 18.2f, 7f)
        line(6f, 15f, 3f, 11.5f)
        // La paume
        rect.set(x0 + 6.2f * u, y0 + 11f * u, x0 + 19.6f * u, y0 + 22f * u)
        canvas.drawRoundRect(rect, 4f * u, 4f * u, fill)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 72

    override fun getIntrinsicHeight(): Int = 72
}
