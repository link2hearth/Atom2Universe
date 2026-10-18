package com.Atom2Universe.app.games.golf

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** A frame of the actual golf scene, generated on the hub artwork worker. */
class GolfHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val app = context.applicationContext
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val bitmap = GolfSnapshots.load(app, "gardens")
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val bw = bitmap.width * scale; val bh = bitmap.height * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, RectF((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2), paint)
        paint.shader = LinearGradient(0f, h*.6f, 0f, h, Color.TRANSPARENT, 0xDB102C27.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
