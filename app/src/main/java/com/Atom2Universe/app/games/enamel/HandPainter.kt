package com.Atom2Universe.app.games.enamel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Main dessinée en code, en unités de planche : le bout de l'index est sur le point touché. */
class HandPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val index = RectF(-.19f, 0f, .19f, 1.1f)
    private val palm = RectF(-.24f, .8f, .86f, 1.9f)
    private val folded = arrayOf(RectF(.15f, .62f, .45f, 1.05f), RectF(.41f, .7f, .69f, 1.1f), RectF(.65f, .82f, .89f, 1.16f))

    /** [pressed] : l'index appuie (anneau doré autour du point de [ring] de rayon) ; sinon la main survole. */
    fun draw(c: Canvas, x: Float, y: Float, pressed: Boolean, alpha: Int, ring: Float) {
        if (alpha <= 0) return
        if (pressed) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .06f
            paint.color = EnamelPainter.GOLD; paint.alpha = alpha
            c.drawCircle(x, y, ring, paint)
        }
        c.saveLayerAlpha(x - 1.4f, y - 1.4f, x + 1.7f, y + 2.9f, alpha)
        c.translate(x, y + if (pressed) .2f else .05f)
        if (!pressed) c.scale(1.08f, 1.08f)
        // Contour épais d'abord, puis le remplissage par-dessus : les traits intérieurs disparaissent.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .1f; paint.color = EnamelPainter.INK
        c.drawRoundRect(palm, .3f, .3f, paint)
        folded.forEach { c.drawRoundRect(it, .14f, .14f, paint) }
        c.drawRoundRect(index, .19f, .19f, paint)
        paint.style = Paint.Style.FILL; paint.color = SKIN
        c.drawRoundRect(palm, .3f, .3f, paint)
        for (finger in folded) {
            paint.style = Paint.Style.FILL; paint.color = SKIN; c.drawRoundRect(finger, .14f, .14f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .035f; paint.color = EnamelPainter.INK
            c.drawRoundRect(finger, .14f, .14f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = SKIN; c.drawRoundRect(index, .19f, .19f, paint)
        paint.color = NAIL; c.drawRoundRect(-.12f, .05f, .12f, .32f, .1f, .1f, paint)
        c.restore()
    }

    private companion object {
        val SKIN = 0xFFF7E2CF.toInt()
        val NAIL = 0xFFF1CDB9.toInt()
    }
}
