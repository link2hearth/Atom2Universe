package com.Atom2Universe.app.science.bestiary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

/** Uses the same compact, offline lion plate as the animal's detailed record. */
class BestiaryHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val store = AnimalImageStore.get(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(android.graphics.Color.WHITE)
        // CachedHubArtworkDrawable invokes render on its existing background worker.
        store.loadBlocking("lion")?.let { AnimalImageStore.drawFit(canvas, it, w, h, paint) }
        ScienceTileArt.bottomShade(canvas, w, h, 0xff183429.toInt(), .62f)
    }
}
