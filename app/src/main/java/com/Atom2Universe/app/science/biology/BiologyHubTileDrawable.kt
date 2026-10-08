package com.Atom2Universe.app.science.biology

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

/** Vignette rendue depuis le même maillage anatomique que l'atlas. */
class BiologyHubTileDrawable(private val context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xff102a32.toInt())
        ScienceTileArt.glow(canvas, w * .52f, h * .38f, w * .65f, 0xff47c7b7.toInt(), 85)
        context.assets.open("science/biology/skeleton_tile.png").use { stream ->
            BitmapFactory.decodeStream(stream)?.let { image ->
                canvas.drawBitmap(image, null, RectF(0f, 0f, w, h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                image.recycle()
            }
        }
        ScienceTileArt.bottomShade(canvas, w, h, 0xff102a32.toInt(), .6f)
    }
}
