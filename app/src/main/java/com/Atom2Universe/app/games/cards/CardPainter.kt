package com.Atom2Universe.app.games.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.kit.KitPalette

/**
 * Le dessin des cartes pour les jeux de cartes du kit : une face (indice en haut à gauche et en bas
 * à droite, grosse couleur au centre) et un dos (une des images de [CardBacks], tirée au hasard à
 * chaque partie par [newBack]).
 *
 * Les couleurs viennent de la palette du thème : le papier est un blanc légèrement teinté par la
 * couleur d'accent, l'encre noire et le rouge s'ajustent pour rester lisibles sur ce papier, que le
 * thème soit clair ou sombre.
 *
 * Une carte est toujours plus haute que large, dans le rapport [ASPECT] ; l'appelant donne le
 * rectangle exact.
 */
class CardPainter(
    private val context: Context,
    loadBack: Boolean = true,
    /** Les 13 repères de hauteur, As à Roi ; null : ceux de l'appli, dans la langue du moment (les tests donnent les leurs). */
    labels: Array<String>? = null,
) {

    var palette: KitPalette = KitPalette.artwork(0xFF4FC3F7.toInt())
        set(value) { field = value; refreshColors() }

    private val density = context.resources.displayMetrics.density
    private val rankLabels: Array<String> = labels ?: Array(13) { i ->
        when (i + 1) {
            PlayingCard.ACE -> context.getString(R.string.card_rank_ace)
            PlayingCard.JACK -> context.getString(R.string.card_rank_jack)
            PlayingCard.QUEEN -> context.getString(R.string.card_rank_queen)
            PlayingCard.KING -> context.getString(R.string.card_rank_king)
            else -> (i + 1).toString()
        }
    }

    private var paper = Color.WHITE
    private var inkBlack = Color.BLACK
    private var inkRed = Color.RED
    private var edge = Color.GRAY

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private var backBitmap: Bitmap? = null
    private var backShader: BitmapShader? = null
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backMatrix = Matrix()

    init {
        refreshColors()
        if (loadBack) newBack()
    }

    private fun refreshColors() {
        paper = ColorUtils.blendARGB(Color.WHITE, palette.accent, 0.05f)
        inkBlack = ColorUtils.blendARGB(0xFF14161C.toInt(), palette.accent, 0.12f)
        inkRed = KitPalette.ensureContrast(0xFFD32F2F.toInt(), paper, 4.5)
        edge = ColorUtils.blendARGB(paper, Color.BLACK, 0.38f)
    }

    /** Un nouveau dos au hasard, pour une nouvelle partie. */
    fun newBack() {
        backBitmap?.recycle()
        backBitmap = CardBacks.loadRandom(context)
        backShader = backBitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    /** Impose un dos (les tests, qui n'ont pas les images de l'appli). */
    internal fun setBack(bitmap: Bitmap?) {
        backBitmap = bitmap
        backShader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    fun rankLabel(card: PlayingCard): String = rankLabels[card.rank - 1]

    fun inkOf(suit: CardSuit): Int = if (suit.isRed) inkRed else inkBlack

    private fun radius(r: RectF) = r.width() * 0.09f

    /** Une carte retournée, dans [r]. [dimmed] la grise (carte qu'on ne peut pas jouer) ; [outline] l'entoure. */
    fun drawFace(canvas: Canvas, card: PlayingCard, r: RectF, dimmed: Boolean = false, outline: Int? = null,
                 shadow: Boolean = true) {
        val rad = radius(r)
        if (shadow) drawShadow(canvas, r, rad)
        fill.color = paper
        canvas.drawRoundRect(r, rad, rad, fill)
        line.color = edge
        line.strokeWidth = density.coerceAtLeast(1f)
        canvas.drawRoundRect(r, rad, rad, line)

        val w = r.width()
        val ink = inkOf(card.suit)
        val label = rankLabel(card)
        text.color = ink
        // Indice en haut à gauche, puis le même en bas à droite, la tête en bas.
        for (turn in 0..1) {
            canvas.save()
            if (turn == 1) canvas.rotate(180f, r.centerX(), r.centerY())
            text.textSize = if (label.length > 1) w * 0.27f else w * 0.31f
            canvas.drawText(label, r.left + w * 0.20f, r.top + w * 0.34f, text)
            text.textSize = w * 0.27f
            canvas.drawText(card.suit.symbol, r.left + w * 0.20f, r.top + w * 0.64f, text)
            canvas.restore()
        }
        // La grosse couleur du centre.
        text.textSize = if (card.rank == PlayingCard.ACE) w * 0.78f else w * 0.58f
        canvas.drawText(card.suit.symbol, r.centerX(), r.centerY() - (text.ascent() + text.descent()) / 2f + w * 0.04f, text)

        if (dimmed) {
            fill.color = palette.withAlpha(palette.background, 0.55f)
            canvas.drawRoundRect(r, rad, rad, fill)
        }
        if (outline != null) {
            line.color = outline
            line.strokeWidth = 2.5f * density
            rect.set(r.left + density, r.top + density, r.right - density, r.bottom - density)
            canvas.drawRoundRect(rect, rad, rad, line)
        }
    }

    /** Le dos d'une carte dans [r]. */
    fun drawBack(canvas: Canvas, r: RectF, shadow: Boolean = true) {
        val rad = radius(r)
        if (shadow) drawShadow(canvas, r, rad)
        val shader = backShader
        val bitmap = backBitmap
        if (shader != null && bitmap != null) {
            backMatrix.setScale(r.width() / bitmap.width, r.height() / bitmap.height)
            backMatrix.postTranslate(r.left, r.top)
            shader.setLocalMatrix(backMatrix)
            backPaint.shader = shader
            canvas.drawRoundRect(r, rad, rad, backPaint)
        } else {
            fill.color = palette.accentDim
            canvas.drawRoundRect(r, rad, rad, fill)
            line.color = palette.accent
            line.strokeWidth = 1.5f * density
            rect.set(r.left + r.width() * 0.1f, r.top + r.width() * 0.1f, r.right - r.width() * 0.1f, r.bottom - r.width() * 0.1f)
            canvas.drawRoundRect(rect, rad * 0.6f, rad * 0.6f, line)
        }
        line.color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
        line.strokeWidth = density.coerceAtLeast(1f)
        canvas.drawRoundRect(r, rad, rad, line)
    }

    private fun drawShadow(canvas: Canvas, r: RectF, rad: Float) {
        fill.color = 0x40000000
        rect.set(r.left + density, r.top + 1.5f * density, r.right + density, r.bottom + 1.5f * density)
        canvas.drawRoundRect(rect, rad, rad, fill)
    }

    /** Un emplacement vide (une pile sans carte) : un contour discret aux couleurs du thème. */
    fun drawSlot(canvas: Canvas, r: RectF) {
        line.color = palette.withAlpha(palette.text, 0.28f)
        line.strokeWidth = 1.5f * density
        canvas.drawRoundRect(r, radius(r), radius(r), line)
    }

    companion object {
        /** Largeur divisée par la hauteur d'une carte. */
        const val ASPECT = 0.70f
    }
}
