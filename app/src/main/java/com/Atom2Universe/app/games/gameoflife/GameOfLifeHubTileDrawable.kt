package com.Atom2Universe.app.games.gameoflife

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt

/**
 * Illustration de la tuile Jeu de la vie : la grille du module (fond nuit, cellules pervenche)
 * et quelques figures célèbres qu'on reconnaît d'un coup d'œil — deux planeurs en route, un
 * clignotant, un bloc, une ruche.
 */
class GameOfLifeHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    private companion object {
        const val BACKGROUND = 0xFF0D0D1A.toInt()
        const val CELL = 0xFF7B8CDE.toInt()
        const val COLUMNS = 16

        /** Les figures, en (colonne, ligne) dans une grille de [COLUMNS] de large. */
        val LIVE = listOf(
            // Planeur, en haut à gauche
            2 to 1, 3 to 2, 1 to 3, 2 to 3, 3 to 3,
            // Planeur, au milieu, un temps plus loin
            8 to 4, 9 to 5, 10 to 5, 8 to 6, 9 to 6,
            // Clignotant
            12 to 2, 13 to 2, 14 to 2,
            // Bloc
            4 to 7, 5 to 7, 4 to 8, 5 to 8,
            // Ruche
            12 to 7, 13 to 7, 11 to 8, 14 to 8, 12 to 9, 13 to 9
        )

        /** Les cases que les planeurs viennent de quitter : une trace qui s'éteint. */
        val FADING = listOf(1 to 2, 7 to 4, 7 to 5, 10 to 4)
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(BACKGROUND)
        val cell = w / COLUMNS
        val rows = (h / cell).toInt() + 1
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.argb(40, 160, 190, 255)
        paint.strokeWidth = maxOf(1f, cell * 0.04f)
        for (c in 0..COLUMNS) canvas.drawLine(c * cell, 0f, c * cell, h, paint)
        for (r in 0..rows) canvas.drawLine(0f, r * cell, w, r * cell, paint)

        val inset = cell * 0.12f
        val radius = cell * 0.22f
        FADING.forEach { (c, r) ->
            paint.color = Color.argb(70, Color.red(CELL), Color.green(CELL), Color.blue(CELL))
            canvas.drawRoundRect(c * cell + inset, r * cell + inset, (c + 1) * cell - inset, (r + 1) * cell - inset,
                radius, radius, paint)
        }
        LIVE.forEach { (c, r) ->
            ScienceTileArt.glow(canvas, (c + 0.5f) * cell, (r + 0.5f) * cell, cell * 1.1f, CELL, 70)
        }
        paint.color = CELL
        LIVE.forEach { (c, r) ->
            canvas.drawRoundRect(c * cell + inset, r * cell + inset, (c + 1) * cell - inset, (r + 1) * cell - inset,
                radius, radius, paint)
        }
        ScienceTileArt.bottomShade(canvas, w, h, BACKGROUND, from = 0.55f)
    }
}
