package com.Atom2Universe.app.science.nuclide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import com.Atom2Universe.app.science.ScienceTileArt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Illustration de la tuile Carte des nucléides : la bande des noyaux connus, qui monte en se
 * courbant (neutrons en largeur, protons en hauteur), avec les couleurs de désintégration de
 * [NuclideChartView] : les stables au milieu, bêta− côté riche en neutrons, bêta+ de l'autre
 * côté, alpha puis fission au sommet des noyaux lourds.
 *
 * La bande est calculée, pas lue dans les données : une vallée de stabilité approchée
 * (N ≈ Z + 0,006·Z²) suffit pour le dessin et reste identique à chaque rendu.
 */
class NuclideHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {

    private companion object {
        const val STABLE = 0xFF10151C.toInt()
        const val STABLE_EDGE = 0xFFB9C3D2.toInt()
        const val ALPHA = 0xFFFFCC00.toInt()
        const val BETA_MINUS = 0xFF4FC3F7.toInt()
        const val BETA_PLUS = 0xFFEF5350.toInt()
        const val FISSION = 0xFFAB47BC.toInt()
        const val BACKGROUND = 0xFF121212.toInt()
        const val MAX_Z = 100
        const val MAX_N = 150
    }

    override fun render(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(BACKGROUND)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = STABLE_EDGE }

        // La carte occupe la tuile, le coin haut-droit et le bas restent plus calmes.
        val cell = min(w * 0.94f / MAX_N, h * 0.80f / MAX_Z)
        val left = w * 0.03f
        val bottom = h * 0.86f
        edge.strokeWidth = cell * 0.18f
        for (z in 1..MAX_Z) {
            val stable = z + 0.006f * z * z
            // La bande s'élargit avec la masse : plus de noyaux instables connus chez les lourds.
            val below = (3 + z * 0.16f).roundToInt()
            val above = (4 + z * 0.22f).roundToInt()
            val center = stable.roundToInt()
            for (n in (center - below)..(center + above)) {
                if (n < 0 || n > MAX_N) continue
                val offset = n - stable
                val isStable = z <= 82 && kotlin.math.abs(offset) < 0.9f + z * 0.012f
                paint.color = when {
                    isStable -> STABLE
                    z >= 94 && offset > -2 -> FISSION
                    z >= 84 || (z >= 60 && offset < -below * 0.55f) -> ALPHA
                    offset > 0 -> BETA_MINUS
                    else -> BETA_PLUS
                }
                val x = left + n * cell
                val y = bottom - z * cell
                canvas.drawRect(x, y, x + cell * 0.88f, y + cell * 0.88f, paint)
                if (isStable) canvas.drawRect(x, y, x + cell * 0.88f, y + cell * 0.88f, edge)
            }
        }
        ScienceTileArt.bottomShade(canvas, w, h, BACKGROUND, from = 0.6f)
    }
}
