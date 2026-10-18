package com.Atom2Universe.app.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/** Un aperçu du dépôt orbital, avec les vrais dessins du plateau et le format de Cosmo Run. */
class SokobanHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val width = 7
        val height = 6
        val walls = (0 until width * height).filter { cell ->
            val x = cell % width
            val y = cell / width
            x == 0 || x == width - 1 || y == 0 || y == height - 1 || (x == 1 && y == 1)
        }.toSet()
        // Le robot peut pousser la caisse cuivrée à droite jusqu'au quai libre.
        val game = SokobanGame().apply {
            load(SokobanPuzzle(
                width = width,
                height = height,
                walls = walls,
                goals = setOf(1 * width + 4, 2 * width + 4),
                boxes = listOf(1 * width + 4, 2 * width + 3),
                player = 2 * width + 2,
                optimalPushes = 1
            ))
        }
        canvas.drawColor(0xFF102129.toInt())
        val cell = maxOf(w / 6f, h / 5.4f)
        SokobanRenderer().draw(canvas, game, cell,
            (w - width * cell) / 2f, -.45f * cell, facing = SokobanDir.RIGHT)

        // Même réserve sombre sous le titre que les autres tuiles illustrées.
        val shade = Paint().apply {
            shader = LinearGradient(0f, h * .55f, 0f, h,
                Color.TRANSPARENT, 0xEE102129.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .55f, w, h, shade)
    }
}
