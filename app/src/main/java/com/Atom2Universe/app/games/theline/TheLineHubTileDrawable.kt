package com.Atom2Universe.app.games.theline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/**
 * Tuile du hub : un coin de carte électronique, avec les mêmes pièces que le jeu. Une
 * piste sous tension serpente entre deux puces, de la borne 1 jusqu'à la LED allumée.
 */
class TheLineHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    /** La piste, en cases d'une grille de 6 × 5. */
    private val track = intArrayOf(0, 0, 1, 0, 2, 0, 2, 1, 1, 1, 0, 1, 0, 2, 0, 3, 1, 3, 2, 3, 3, 3, 3, 2, 4, 2, 5, 2, 5, 1, 5, 0)

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val cols = 6; val rows = 5
        val cell = min(w / (cols + 0.3f), h / (rows + 0.6f))
        CircuitPainter.backdrop(canvas, w, h, 31, cell)
        val ox = (w - cell * cols) / 2f
        val oy = cell * 0.35f
        val m = cell * 0.32f
        CircuitPainter.plate(canvas, ox - m, oy - m, ox + cell * cols + m, oy + cell * rows + m, cell)

        fun cx(x: Int) = ox + (x + 0.5f) * cell
        fun cy(y: Int) = oy + (y + 0.5f) * cell
        val onTrack = HashSet<Int>()
        for (k in 0 until track.size / 2) onTrack.add(track[k * 2 + 1] * cols + track[k * 2])
        // Deux composants au milieu : une grosse puce (2 × 2) et un condensateur.
        val blocked = setOf(1 * cols + 3, 1 * cols + 4, 0 * cols + 3, 0 * cols + 4, 2 * cols + 1)
        for (y in 0 until rows) for (x in 0 until cols) {
            val i = y * cols + x
            if (i !in onTrack && i !in blocked) CircuitPainter.via(canvas, cx(x), cy(y), cell)
        }
        CircuitPainter.components(canvas, cols, rows, blocked, ox, oy, cell)

        val path = Path()
        for (k in 0 until track.size / 2) {
            val x = cx(track[k * 2]); val y = cy(track[k * 2 + 1])
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        CircuitPainter.copper(canvas, path, cell)
        CircuitPainter.powered(canvas, path, cell)

        val pads = listOf(0 to 0, 0 to 3, 5 to 2, 5 to 0)
        pads.forEachIndexed { i, (x, y) ->
            if (i == pads.lastIndex) {
                CircuitPainter.ledRing(canvas, cx(x), cy(y), cell)
                CircuitPainter.glow(canvas, cx(x), cy(y), cell * 1.2f, CircuitPainter.LED, 0.85f)
            }
            CircuitPainter.pad(canvas, cx(x), cy(y), cell, i + 1, true)
        }

        val fade = Paint()
        fade.shader = LinearGradient(0f, h * 0.56f, 0f, h, Color.TRANSPARENT, 0xEF041A12.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.56f, w, h, fade)
    }
}
