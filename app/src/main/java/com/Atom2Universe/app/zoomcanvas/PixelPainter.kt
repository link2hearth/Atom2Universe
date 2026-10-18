package com.Atom2Universe.app.zoomcanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.Atom2Universe.app.zoomcanvas.core.PixelLayer
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Trace la couche de pixels : chaque tuile (64 × 64 cases) est une petite image, posée à l'écran sans
 * lissage (des cases nettes) quand on est zoomé, avec lissage quand une case fait moins d'un pixel.
 * Les images sont gardées d'une image à l'autre et refaites quand leur tuile change ([PixelLayer.Tile.version]).
 *
 * Les bords des tuiles sont arrondis au pixel d'écran depuis la même formule : deux tuiles voisines
 * se touchent sans trait ni recouvrement.
 */
class PixelPainter {

    private class Cached(val bitmap: Bitmap, val version: Int)

    /** Les images des tuiles, la moins récemment tracée en tête : au-delà de [MAX_CACHED], on recycle les plus anciennes. */
    private val cache = object : LinkedHashMap<Long, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Cached>): Boolean {
            if (size <= MAX_CACHED) return false
            eldest.value.bitmap.recycle()
            return true
        }
    }

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val src = Rect(0, 0, PixelLayer.SIZE, PixelLayer.SIZE)
    private val dst = RectF()

    fun clear() {
        for (c in cache.values) c.bitmap.recycle()
        cache.clear()
    }

    /** Trace [layer] vue de la caméra ([camX], [camY], [zoom] : pixels par case) dans une fenêtre de [w] × [h] pixels. */
    fun draw(canvas: Canvas, layer: PixelLayer, camX: Double, camY: Double, zoom: Double, w: Double, h: Double) {
        if (layer.isEmpty) return
        val size = PixelLayer.SIZE
        val left = camX - w / 2 / zoom
        val right = camX + w / 2 / zoom
        val top = camY - h / 2 / zoom
        val bottom = camY + h / 2 / zoom
        val tx0 = floor(left / size).toLong().coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()
        val tx1 = floor(right / size).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val ty0 = floor(top / size).toLong().coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()
        val ty1 = floor(bottom / size).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        paint.isFilterBitmap = zoom < 1.0
        var drawn = 0
        val range = (tx1 - tx0 + 1).toLong() * (ty1 - ty0 + 1).toLong()
        if (range <= layer.tileCount) {
            // Peu de tuiles à l'écran : on les demande une à une.
            for (ty in ty0..ty1) for (tx in tx0..tx1) {
                val t = layer.tile(tx, ty) ?: continue
                if (drawn++ >= MAX_DRAWN) return
                drawTile(canvas, t, camX, camY, zoom, w, h)
            }
        } else {
            // Beaucoup de place à l'écran, peu de tuiles : on parcourt celles qui existent.
            for (t in layer.allTiles) {
                if (t.tx < tx0 || t.tx > tx1 || t.ty < ty0 || t.ty > ty1) continue
                if (drawn++ >= MAX_DRAWN) return
                drawTile(canvas, t, camX, camY, zoom, w, h)
            }
        }
    }

    private fun drawTile(canvas: Canvas, t: PixelLayer.Tile, camX: Double, camY: Double, zoom: Double, w: Double, h: Double) {
        val key = PixelLayer.tileKey(t.tx, t.ty)
        var c = cache[key]
        if (c == null || c.version != t.version) {
            val bmp = c?.bitmap ?: Bitmap.createBitmap(PixelLayer.SIZE, PixelLayer.SIZE, Bitmap.Config.ARGB_8888)
            bmp.setPixels(t.px, 0, PixelLayer.SIZE, 0, 0, PixelLayer.SIZE, PixelLayer.SIZE)
            c = Cached(bmp, t.version)
            cache[key] = c
        }
        val size = PixelLayer.SIZE
        dst.left = edge(t.tx.toDouble() * size, camX, zoom, w)
        dst.right = edge((t.tx + 1).toDouble() * size, camX, zoom, w)
        dst.top = edge(t.ty.toDouble() * size, camY, zoom, h)
        dst.bottom = edge((t.ty + 1).toDouble() * size, camY, zoom, h)
        canvas.drawBitmap(c.bitmap, src, dst, paint)
    }

    /** Un bord de tuile à l'écran, arrondi au pixel. */
    private fun edge(world: Double, cam: Double, zoom: Double, extent: Double): Float =
        ((world - cam) * zoom + extent / 2).roundToInt().toFloat()

    private companion object {
        /** Images de tuiles gardées : 1024 × 16 Ko = 16 Mo. */
        const val MAX_CACHED = 1024
        /** Au-delà, on ne trace plus (dézoomé très loin sur un grand tableau, des milliers de tuiles d'un pixel). */
        const val MAX_DRAWN = 4000
    }
}
