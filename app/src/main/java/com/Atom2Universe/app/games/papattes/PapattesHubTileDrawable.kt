package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** La tuile du hub : l'illustration du chat sur échasses, recadrée au carré. */
class PapattesHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val context = context.applicationContext
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val bitmap = context.assets.open("papattes/hub.webp").use { BitmapFactory.decodeStream(it) } ?: return
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        canvas.drawBitmap(bitmap, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }
}
