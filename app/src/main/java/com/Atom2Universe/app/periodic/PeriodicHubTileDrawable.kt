package com.Atom2Universe.app.periodic

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.min

/**
 * Illustration de la tuile Tableau périodique : la silhouette du tableau, chaque case à la
 * couleur de sa famille, exactement celles du module. Pas de symbole écrit : la forme du
 * tableau se reconnaît seule, et le titre se pose en bas.
 */
class PeriodicHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {

    private val appContext = context.applicationContext

    private fun color(res: Int) = ContextCompat.getColor(appContext, res)

    /** La famille d'une case (ligne 1-7, colonne 1-18), assez juste pour le dessin. */
    private fun category(row: Int, col: Int): Int = when {
        row == 1 && col == 1 -> R.color.category_nonmetal
        col == 18 -> R.color.category_noble_gas
        col == 1 -> R.color.category_alkali_metal
        col == 2 -> R.color.category_alkaline_earth_metal
        col in 3..12 -> R.color.category_transition_metal
        col == 17 -> R.color.category_halogen
        // L'escalier des métalloïdes, qui sépare métaux et non-métaux.
        (row == 2 && col == 13) || (row == 3 && col == 14) || (row == 4 && col in 14..15) ||
            (row == 5 && col in 15..16) || (row == 6 && col == 16) -> R.color.category_metalloid
        (row == 2 && col in 14..16) || (row == 3 && col in 15..16) || (row == 4 && col == 16) ->
            R.color.category_nonmetal
        else -> R.color.category_post_transition_metal
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0B1A1C.toInt(), 0xFF07100F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 18 colonnes, 7 lignes, un vide, puis les deux bandes des terres rares.
        val cell = min(w * 0.92f / 18f, h * 0.66f / 10f)
        val gap = cell * 0.14f
        val left = (w - cell * 18f) / 2f
        val top = h * 0.07f
        val rect = RectF()
        fun cellAt(x: Float, y: Float, colorRes: Int, highlight: Boolean = false) {
            rect.set(x + gap / 2, y + gap / 2, x + cell - gap / 2, y + cell - gap / 2)
            paint.color = color(colorRes)
            canvas.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, paint)
            if (highlight) {
                ScienceTileArt.glow(canvas, rect.centerX(), rect.centerY(), cell * 1.6f, paint.color, 150)
                canvas.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, paint)
            }
        }
        for (row in 1..7) for (col in 1..18) {
            val exists = when (row) {
                1 -> col == 1 || col == 18
                2, 3 -> col <= 2 || col >= 13
                else -> true
            }
            // En lignes 6 et 7, la case 3 porte les terres rares : elle reste à sa place, en creux.
            if (!exists) continue
            val colorRes = if ((row == 6 || row == 7) && col == 3)
                (if (row == 6) R.color.category_lanthanide else R.color.category_actinide)
            else category(row, col)
            // Le fer, au cœur des métaux de transition, s'allume.
            cellAt(left + (col - 1) * cell, top + (row - 1) * cell, colorRes, highlight = row == 4 && col == 8)
        }
        val rareTop = top + 7.5f * cell
        for (i in 0 until 15) {
            cellAt(left + (i + 2.5f) * cell, rareTop, R.color.category_lanthanide)
            cellAt(left + (i + 2.5f) * cell, rareTop + cell, R.color.category_actinide)
        }

        ScienceTileArt.bottomShade(canvas, w, h, 0xFF07100F.toInt(), from = 0.55f)
    }
}
