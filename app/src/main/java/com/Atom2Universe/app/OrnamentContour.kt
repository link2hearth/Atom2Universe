package com.Atom2Universe.app

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/** A closed, symmetric silhouette, shared by backgrounds, card clipping and their borders. */
internal class OrnamentContour {
    val outer = Path()
    val inner = Path()

    fun resize(bounds: RectF, density: Float, variant: Int) {
        // The ornament occupies the existing edge/margins, never the centre of a control.
        val unit = min(density, min(bounds.width(), bounds.height()) / 44f)
        trace(outer, RectF(bounds).apply { inset(1.5f * unit, 1.5f * unit) }, unit, variant)
        trace(inner, RectF(bounds).apply { inset(4.1f * unit, 4.1f * unit) }, unit * .72f, variant)
    }

    private fun trace(path: Path, b: RectF, u: Float, variant: Int) {
        path.rewind()
        if (b.width() <= 0f || b.height() <= 0f) return
        val cx = b.centerX()
        val cy = b.centerY()
        val r = b.right
        val t = b.top
        val shoulder = min(22f * u, min(b.width(), b.height()) * .23f)
        val crest = min(18f * u, b.width() * .14f)
        val edge = 3f * u
        val segments = ArrayList<FloatArray>(8)
        var x = cx
        var y = t
        fun curve(x1: Float, y1: Float, x2: Float, y2: Float, ex: Float, ey: Float) {
            segments += floatArrayOf(x, y, x1, y1, x2, y2, ex, ey)
            x = ex
            y = ey
        }
        fun line(ex: Float, ey: Float) = curve(x, y, ex, ey, ex, ey)

        when (Math.floorMod(variant, 4)) {
            0 -> { // Cartouche: a pointed crown, convex shoulders and an inward waist.
                curve(cx + crest * .3f, t, cx + crest * .3f, t + edge, cx + crest, t + edge)
                line(r - shoulder, t + edge)
                curve(r - shoulder * .35f, t + edge, r - edge, t, r - edge, t + shoulder * .5f)
                curve(r - edge, t + shoulder, r - shoulder * .35f, t + shoulder * .65f,
                    r - shoulder * .35f, t + shoulder * 1.15f)
                curve(r - shoulder * .35f, cy - shoulder * .3f, r, cy - shoulder * .3f, r, cy)
            }
            1 -> { // Scroll: S-shaped shoulders flow into a narrow side lobe.
                curve(cx + crest * .3f, t + edge * 2f, cx + crest * .7f, t + edge, cx + crest, t + edge)
                line(r - shoulder * 1.2f, t + edge)
                curve(r - shoulder * .3f, t + edge, r - shoulder * .9f, t,
                    r - shoulder * .4f, t)
                curve(r + shoulder * .08f, t, r - shoulder * .05f, t + shoulder * .75f,
                    r - shoulder * .42f, t + shoulder * .7f)
                curve(r - shoulder * .75f, t + shoulder * .7f, r - edge, t + shoulder * 1.3f,
                    r - edge, cy - shoulder * .45f)
                curve(r - edge, cy - shoulder * .2f, r, cy - shoulder * .2f, r, cy)
            }
            2 -> { // Interlace: angular shoulders meet curved rails; the inner rail repeats it.
                line(cx + crest * .28f, t + edge)
                line(r - shoulder, t + edge)
                line(r - shoulder * .65f, t)
                line(r - shoulder * .28f, t + shoulder * .28f)
                line(r, t + shoulder * .65f)
                line(r - edge, t + shoulder)
                curve(r - shoulder * .33f, cy - shoulder * .5f, r - edge, cy - shoulder * .22f, r, cy)
            }
            else -> { // Accolade: recessed corners and a broad, pointed side bracket.
                curve(cx + crest * .3f, t + edge * 2f, cx + crest * .6f, t + edge, cx + crest, t + edge)
                line(r - shoulder, t + edge)
                curve(r - shoulder * .85f, t + shoulder * .85f, r - edge, t + shoulder * .7f,
                    r - edge, t + shoulder)
                curve(r - edge, cy - shoulder * .22f, r - shoulder * .3f, cy - shoulder * .22f, r, cy)
            }
        }

        // Reflect the same quarter in both axes; reverse two quarters to form one closed path.
        fun append(sx: Int, sy: Int, reverse: Boolean) {
            fun px(v: Float) = cx + sx * (v - cx)
            fun py(v: Float) = cy + sy * (v - cy)
            for (index in segments.indices) {
                val s = segments[if (reverse) segments.lastIndex - index else index]
                if (reverse) path.cubicTo(px(s[4]), py(s[5]), px(s[2]), py(s[3]), px(s[0]), py(s[1]))
                else path.cubicTo(px(s[2]), py(s[3]), px(s[4]), py(s[5]), px(s[6]), py(s[7]))
            }
        }
        path.moveTo(cx, t)
        append(1, 1, false)
        append(1, -1, true)
        append(-1, -1, false)
        append(-1, 1, true)
        path.close()
    }
}
