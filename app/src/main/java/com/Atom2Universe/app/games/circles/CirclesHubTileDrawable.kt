package com.Atom2Universe.app.games.circles

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

class CirclesHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFF10172A.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = min(w * .48f, h * .42f)
        val cx = w * .5f
        val cy = h * .34f
        val path = Path()
        val offsets = floatArrayOf(-30f, 30f, -90f, 90f)
        // Même palette et secteurs de 60° que le puzzle, avec rotations décalées.
        for (ring in 0..3) {
            val outer = radius * (1f - ring * .23f)
            val inner = outer - radius * .185f
            for (segment in 0 until CirclesGame.SEGMENTS) {
                val start = offsets[ring] + segment * 60f
                path.rewind()
                path.arcTo(RectF(cx - outer, cy - outer, cx + outer, cy + outer), start, 60f, true)
                path.arcTo(RectF(cx - inner, cy - inner, cx + inner, cy + inner), start + 60f, -60f, false)
                path.close()
                paint.color = CirclesGame.COLORS[segment]
                canvas.drawPath(path, paint)
            }
        }
        paint.shader = LinearGradient(0f, h * .54f, 0f, h,
            Color.TRANSPARENT, 0xEF10172A.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .54f, w, h, paint)
    }
}
