package com.Atom2Universe.app.games.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * L'illustration de tuile d'un jeu de cartes : un éventail de vraies cartes, dessinées par le même
 * [CardPainter] que la table, sur le fond sombre commun aux tuiles du kit, puis le dégradé du bas qui
 * laisse lire le titre posé par le hub. Cuit hors du fil d'interface comme toutes les illustrations.
 */
abstract class CardArtwork(protected val context: Context) : CachedHubArtworkDrawable() {

    /** La couleur propre au jeu, qui le distingue dans la grille du hub. */
    protected abstract val accent: Int

    protected abstract fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette)

    /** Pose [cards] en éventail, de gauche à droite, ouvert de [spread] degrés entre deux cartes voisines. */
    protected fun fan(canvas: Canvas, w: Float, h: Float, cards: List<PlayingCard>, spread: Float) {
        val painter = CardPainter(context, loadBack = false)
        painter.palette = KitPalette.artwork(accent)
        val cardH = h * 0.58f
        val cardW = cardH * CardPainter.ASPECT
        val pivotX = w / 2f
        val pivotY = h * 0.98f
        val radius = cardH * 0.9f
        val rect = RectF()
        for ((i, card) in cards.withIndex()) {
            val angle = (i - (cards.size - 1) / 2f) * spread
            val rad = Math.toRadians(angle.toDouble())
            val cx = pivotX + (sin(rad) * radius).toFloat()
            val cy = pivotY - (cos(rad) * radius).toFloat()
            rect.set(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f)
            canvas.save()
            canvas.rotate(angle, cx, cy)
            painter.drawFace(canvas, card, rect)
            canvas.restore()
        }
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
