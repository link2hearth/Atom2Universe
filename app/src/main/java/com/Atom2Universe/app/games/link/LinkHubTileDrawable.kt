package com.Atom2Universe.app.games.link

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/**
 * Tuile du hub : un bout de grille quantique peint avec les pièces du jeu. Des atomes à
 * divers niveaux d'énergie, et deux paires intriquées reliées par leur fil d'onde.
 */
class LinkHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    /** Énergies d'une grille 5 × 4. */
    private val energies = intArrayOf(
        1, 3, 0, 2, 1,
        2, 4, 1, 0, 3,
        0, 2, 3, 1, 2,
        1, 0, 1, 2, 0
    )

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val cols = 5; val rows = 4
        val cell = min(w / (cols + 0.2f), h / (rows + 0.8f))
        QuantumPainter.backdrop(canvas, w, h, 23, cell)
        val ox = (w - cell * cols) / 2f
        val oy = cell * 0.2f
        fun cx(i: Int) = ox + (i % cols + 0.5f) * cell
        fun cy(i: Int) = oy + (i / cols + 0.5f) * cell

        val pairs = intArrayOf(1, 13, 5, 9)
        for (k in 0 until pairs.size / 2) {
            QuantumPainter.entanglement(canvas, cx(pairs[k * 2]), cy(pairs[k * 2]), cx(pairs[k * 2 + 1]), cy(pairs[k * 2 + 1]),
                cell, QuantumPainter.PAIR_COLORS[k], 0.6f + k * 1.3f)
        }
        for (i in energies.indices) {
            QuantumPainter.atom(canvas, cx(i), cy(i), cell, energies[i], 0.8f, i * 2.399f)
        }
        for (k in pairs.indices) QuantumPainter.pairRing(canvas, cx(pairs[k]), cy(pairs[k]), cell, QuantumPainter.PAIR_COLORS[k / 2])

        val fade = Paint()
        fade.shader = LinearGradient(0f, h * 0.56f, 0f, h, Color.TRANSPARENT, 0xEF0B0A24.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.56f, w, h, fade)
    }
}
