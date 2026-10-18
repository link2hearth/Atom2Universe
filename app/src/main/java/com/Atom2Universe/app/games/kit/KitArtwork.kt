package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable

/**
 * L'illustration de tuile d'un jeu du kit : un morceau de son vrai plateau, dessiné par sa propre
 * vue avec une palette sombre fixe et la couleur du jeu, puis le dégradé du bas qui laisse lire
 * le titre posé par le hub. Cuit hors du fil d'interface comme toutes les illustrations.
 */
abstract class KitArtwork(protected val context: Context) : CachedHubArtworkDrawable() {

    /** La couleur propre au jeu, qui le distingue dans la grille du hub. */
    protected abstract val accent: Int

    protected abstract fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette)

    /** Le plateau déborde un peu de la tuile : on n'en voit qu'un morceau, plus lisible. */
    protected fun boardRect(w: Float, h: Float, zoom: Float = 1.12f, lift: Float = 0.06f): RectF {
        val bw = w * zoom
        val bh = h * zoom
        return RectF((w - bw) / 2f, (h - bh) / 2f - h * lift, (w + bw) / 2f, (h + bh) / 2f - h * lift)
    }

    /** Dessine [state] avec la vue du jeu, comme à l'écran. */
    protected fun <S : Any> drawWith(view: PuzzleView<S>, state: S, canvas: Canvas, rect: RectF, palette: KitPalette) {
        view.palette = palette
        view.drawBoard(canvas, state, rect)
    }

    final override fun render(canvas: Canvas, w: Float, h: Float) {
        val palette = KitPalette.artwork(accent)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(palette.background)
        paint.shader = RadialGradient(w * 0.25f, h * 0.1f, maxOf(w, h) * 0.9f,
            palette.withAlpha(accent, 0.30f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint(canvas, w, h, palette)
        paint.shader = LinearGradient(0f, h * 0.42f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xD9081018.toInt(), 0xFF081018.toInt()),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.42f, w, h, paint)
    }
}
