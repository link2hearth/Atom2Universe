package com.Atom2Universe.app.games.farm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.floor

/** Static, pixel-aligned quality markers: leaf = manure, star = critical. */
internal object FarmGrowthBar {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val outline = Color.rgb(78, 59, 85)
    private val track = Color.rgb(65, 76, 57)
    private val mint = Color.rgb(145, 211, 166)
    private val peach = Color.rgb(249, 184, 134)
    private val lilac = Color.rgb(199, 164, 230)
    private val cream = Color.rgb(255, 242, 199)
    private val normal = Color.rgb(95, 201, 222)
    private val ripeOutline = Color.rgb(118, 66, 176)

    /**
     * The ripe shimmer: a violet glint sliding along the bar, over the colour it grew in. One
     * repeating gradient, shifted with time, drawn in a single rect per bar - a parcel full of ripe
     * plants costs no more than one. It lives in world units, so the wave crosses a whole row of bars
     * from left to right instead of every bar flashing on its own.
     *
     * The clear stops are violet at zero alpha, not Color.TRANSPARENT: that one is transparent BLACK,
     * and the gradient would drag a grey fringe through the violet on its way out.
     */
    private const val SHIMMER_PERIOD = 120f
    private const val SHIMMER_SPEED = 70f
    private val shimmerMatrix = Matrix()
    private val shimmer = Paint().apply {
        val clear = Color.argb(0, 168, 96, 255)
        val violet = Color.argb(175, 168, 96, 255)
        shader = LinearGradient(0f, 0f, SHIMMER_PERIOD, 0f,
            intArrayOf(clear, clear, violet, Color.argb(235, 243, 222, 255), violet, clear, clear),
            floatArrayOf(0f, .30f, .44f, .5f, .56f, .70f, 1f), Shader.TileMode.REPEAT)
    }

    private fun icon(rows: List<String>, fill: Int): Bitmap {
        val w = rows[0].length + 2
        val h = rows.size + 2
        val pixels = IntArray(w * h)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, c ->
            if (c != '.') for (dy in -1..1) for (dx in -1..1)
                pixels[(y + 1 + dy) * w + x + 1 + dx] = outline
        } }
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, c ->
            if (c != '.') pixels[(y + 1) * w + x + 1] = if (c == '+') cream else fill
        } }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
    private val star by lazy { icon(listOf(
        "....#....", "....+....", "...#+#...", "#########", 
        ".#######.", "..#####..", "..#####..", ".###.###.", ".#.....#."
    ), peach) }
    private val leaf by lazy { icon(listOf(
        "....##", "..##+#", ".##+##", "##+##.", "#+##..", "#+....", "#....."
    ), mint) }

    /** [time] drives the ripe shimmer, in seconds; pass the map's wind clock, which never stops. */
    fun draw(canvas: Canvas, cell: RectF, progress: Float, rich: Boolean, critical: Boolean, time: Float = 0f) {
        val p = progress.coerceIn(0f, 1f)
        val ripe = p >= 1f
        val left = floor(cell.left + 8f)
        val right = floor(cell.right - 8f)
        val top = floor(cell.bottom - 4f)
        // A ripe bar keeps the colour it grew in - it still tells manure and luck apart - and the
        // shimmer below is what says "ready". It used to turn plain yellow and lose that.
        val fill = when {
            rich && critical -> lilac
            critical -> peach
            rich -> mint
            else -> normal
        }
        paint.color = when {
            ripe -> ripeOutline
            rich || critical -> outline
            else -> track
        }
        canvas.drawRect(left, top, right, top + 6f, paint)
        paint.color = track
        canvas.drawRect(left + 1f, top + 1f, right - 1f, top + 5f, paint)
        val end = left + 1f + floor((right - left - 2f).coerceAtLeast(0f) * p)
        if (end > left + 1f) {
            paint.color = fill
            canvas.drawRect(left + 1f, top + 1f, end, top + 5f, paint)
            paint.color = Color.rgb(
                (Color.red(fill) + 255) / 2, (Color.green(fill) + 255) / 2, (Color.blue(fill) + 255) / 2)
            canvas.drawRect(left + 1f, top + 1f, end, top + 2f, paint)
            if (ripe) {
                shimmerMatrix.setTranslate(time * SHIMMER_SPEED, 0f)
                shimmer.shader.setLocalMatrix(shimmerMatrix)
                canvas.drawRect(left + 1f, top + 1f, end, top + 5f, shimmer)
            }
        }
        // Symbols sit on the ends of the bar, below the foliage, and do not rotate or pulse.
        if (rich) canvas.drawBitmap(leaf, null, RectF(left - 3f, top - 2f, left + 5f, top + 7f), paint)
        if (critical) canvas.drawBitmap(star, null, RectF(right - 7f, top - 3f, right + 4f, top + 8f), paint)
    }
}
