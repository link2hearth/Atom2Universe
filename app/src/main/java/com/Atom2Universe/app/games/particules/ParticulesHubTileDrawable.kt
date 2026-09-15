package com.Atom2Universe.app.games.particules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Instantané du casse-briques, avec le même atelier de dessin que le jeu. */
class ParticulesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = ParticulesArt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = min(w, h)
        canvas.drawColor(0xFF081321.toInt())
        art.arena(canvas, w, h, h * .05f)
        val colors = intArrayOf(0xFF57DDE4.toInt(), 0xFFFFBF69.toInt(), 0xFFA994F5.toInt())
        for (row in 0..2) for (col in 0..5) {
            if (row == 2 && col in 2..3) continue
            val left = w * (.075f + col * .143f)
            val top = h * (.08f + row * .12f)
            art.brick(canvas, RectF(left, top, left + w * .13f, top + h * .105f),
                ParticulesView.Shape.HEX,
                if (row == 0 && col == 3) ParticulesView.BType.BONUS else ParticulesView.BType.SIMPLE,
                colors[row], 1, 1, 0f)
        }
        // Traînée figée et balle lumineuse entre les briques et la raquette.
        repeat(5) { i ->
            paint.color = Color.argb(30 + i * 35, 140, 240, 255)
            canvas.drawCircle(w * (.43f + i * .018f), h * (.56f - i * .023f), unit * (.008f + i * .002f), paint)
        }
        paint.color = Color.WHITE
        canvas.drawCircle(w * .52f, h * .445f, unit * .022f, paint)
        art.paddle(canvas, RectF(w * .34f, h * .57f, w * .67f, h * .63f), 0xFF70EFF0.toInt())
    }
}
