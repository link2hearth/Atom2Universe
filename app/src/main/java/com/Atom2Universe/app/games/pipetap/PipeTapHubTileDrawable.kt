package com.Atom2Universe.app.games.pipetap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.roundToInt

/** Aperçu fixe du vrai plateau, au même format illustré que Sokoban. */
class PipeTapHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val appContext = context.applicationContext

    override fun render(canvas: Canvas, w: Float, h: Float) {
        // Un circuit continu part du manomètre et serpente parmi les conduites sèches.
        val preview = PipeTapGame().apply {
            difficulty = PipeTapDifficulty.MEDIUM
            source = 1 to 1
            grid = arrayOf(
                intArrayOf(6, 10, 6, 12, 5),
                intArrayOf(5, 2, 9, 5, 12),
                intArrayOf(2, 12, 6, 9, 5),
                intArrayOf(3, 3, 9, 6, 9),
                intArrayOf(10, 12, 3, 9, 10)
            )
        }
        // Dessin hors écran, jamais attaché : aucune animation ni boucle active dans le hub.
        // Réutiliser la vue conserve exactement les raccords et le style du jeu.
        val margin = 16f * appContext.resources.displayMetrics.density
        val side = (500f + margin * 2).roundToInt()
        val view = PipeTapView(appContext).apply {
            animationsActive = false
            game = preview
            layout(0, 0, side, side)
            refresh()
        }
        val cell = maxOf(w / 4.3f, h / 4.3f)
        val scale = cell / ((side - margin * 2) / 5f)
        canvas.save()
        canvas.translate(-.35f * cell - margin * scale, -.45f * cell - margin * scale)
        canvas.scale(scale, scale)
        view.draw(canvas)
        canvas.restore()

        val shade = Paint().apply {
            shader = LinearGradient(0f, h * .55f, 0f, h,
                Color.TRANSPARENT, 0xEE09171E.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, h * .55f, w, h, shade)
    }
}
