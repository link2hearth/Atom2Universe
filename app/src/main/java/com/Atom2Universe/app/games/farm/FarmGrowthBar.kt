package com.Atom2Universe.app.games.farm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    private val ripe = Color.rgb(255, 224, 91)

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

    fun draw(canvas: Canvas, cell: RectF, progress: Float, rich: Boolean, critical: Boolean) {
        val p = progress.coerceIn(0f, 1f)
        val left = floor(cell.left + 8f)
        val right = floor(cell.right - 8f)
        val top = floor(cell.bottom - 4f)
        val fill = when {
            rich && critical -> lilac
            critical -> peach
            rich -> mint
            p >= 1f -> ripe
            else -> normal
        }
        paint.color = if (rich || critical) outline else track
        canvas.drawRect(left, top, right, top + 6f, paint)
        paint.color = track
        canvas.drawRect(left + 1f, top + 1f, right - 1f, top + 5f, paint)
        val end = left + 1f + floor((right - left - 2f).coerceAtLeast(0f) * p)
        if (end > left + 1f) {
            paint.color = fill
            canvas.drawRect(left + 1f, top + 1f, end, top + 5f, paint)
            paint.color = if (p >= 1f) cream else Color.rgb(
                (Color.red(fill) + 255) / 2, (Color.green(fill) + 255) / 2, (Color.blue(fill) + 255) / 2)
            canvas.drawRect(left + 1f, top + 1f, end, top + 2f, paint)
        }
        // Symbols sit on the ends of the bar, below the foliage, and do not rotate or pulse.
        if (rich) canvas.drawBitmap(leaf, null, RectF(left - 3f, top - 2f, left + 5f, top + 7f), paint)
        if (critical) canvas.drawBitmap(star, null, RectF(right - 7f, top - 3f, right + 4f, top + 8f), paint)
    }
}
