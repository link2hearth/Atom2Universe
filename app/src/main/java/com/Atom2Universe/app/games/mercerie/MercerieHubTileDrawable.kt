package com.Atom2Universe.app.games.mercerie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** La tuile du hub : quatre pièces de feutrine posées sur la table de couture. */
class MercerieHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val context = context.applicationContext

    override fun render(canvas: Canvas, w: Float, h: Float) {
        // Fond sombre : le titre de la tuile s'écrit en blanc par-dessus.
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = 0xFF7A4B3A.toInt() })
        val quilt = Path()
        val step = min(w, h) * .3f
        var d = -h
        while (d < w) {
            quilt.moveTo(d, 0f); quilt.lineTo(d + h, h)
            quilt.moveTo(d + h, 0f); quilt.lineTo(d, h)
            d += step
        }
        Felt.stitches(canvas, quilt, 0x40FFF6E6, min(w, h) * .5f, .03f)
        val size = (min(w, h) * .44f).toInt().coerceAtLeast(8)
        val sprites = MercerieSprites(context.assets, size)
        val gap = size * .5f
        val cx = w / 2
        val cy = h * .46f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val spots = listOf(0 to (-1 to -1), 2 to (1 to -1), 4 to (-1 to 1), 1 to (1 to 1))
        for ((color, at) in spots) {
            canvas.drawBitmap(sprites.of(Kind.NORMAL, color), cx + at.first * gap - size / 2f, cy + at.second * gap - size / 2f, paint)
        }
    }
}
