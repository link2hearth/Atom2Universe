package com.Atom2Universe.app.notes.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Kind
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Token

/**
 * Les mises en forme posées par l'éditeur. Toutes portent la marque [MdSpan] : l'éditeur les
 * retire et les repose quand une ligne change, sans jamais toucher à autre chose.
 */
interface MdSpan {
    /** Dessine exactement comme [other] : même sorte, mêmes réglages. Une ligne déjà juste n'est pas reposée. */
    fun same(other: MdSpan): Boolean = other.javaClass == javaClass
}

class MdStyle(style: Int) : StyleSpan(style), MdSpan {
    override fun same(other: MdSpan) = other is MdStyle && other.style == style
}
class MdSize(size: Float) : RelativeSizeSpan(size), MdSpan {
    override fun same(other: MdSpan) = other is MdSize && other.sizeChange == sizeChange
}
class MdColor(color: Int) : ForegroundColorSpan(color), MdSpan {
    override fun same(other: MdSpan) = other is MdColor && other.foregroundColor == foregroundColor
}
class MdBackground(color: Int) : BackgroundColorSpan(color), MdSpan {
    override fun same(other: MdSpan) = other is MdBackground && other.backgroundColor == backgroundColor
}
class MdUnderline : UnderlineSpan(), MdSpan
class MdStrike : StrikethroughSpan(), MdSpan
class MdMono : TypefaceSpan("monospace"), MdSpan

/** Les couleurs de l'éditeur, dérivées de la couleur du texte et de l'accent. */
data class MdPalette(val text: Int, val accent: Int, val onAccent: Int) {
    val marker = (text and 0x00FFFFFF) or 0x55000000
    val dim = (text and 0x00FFFFFF) or 0x99000000.toInt()
    val codeBackground = (text and 0x00FFFFFF) or 0x1A000000
}

/**
 * Un préfixe de ligne remplacé par un dessin (puce, case, barre de citation). Le texte d'origine
 * reste dans la note : seul l'affichage change, et le préfixe garde sa largeur pour que le
 * curseur ne saute pas.
 */
abstract class PrefixSpan : ReplacementSpan(), MdSpan {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) paint.getFontMetricsInt(fm)
        return width(paint, text, start, end)
    }

    protected open fun width(paint: Paint, text: CharSequence, start: Int, end: Int): Int =
        paint.measureText(text, start, end).toInt()
}

class BulletSpan(private val color: Int, private val indent: Int) : PrefixSpan() {
    override fun same(other: MdSpan) = other is BulletSpan && other.color == color && other.indent == indent

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val p = Paint(paint).apply { this.color = this@BulletSpan.color; style = Paint.Style.FILL; isAntiAlias = true }
        val textH = paint.textSize
        val pad = paint.measureText(text, start, start + indent)
        val r = textH * 0.14f
        // Rond plein au premier niveau, cercle au second, carré ensuite : comme un traitement de texte.
        val cx = x + pad + r + textH * 0.1f
        val cy = y - textH * 0.33f
        when ((indent / 2) % 3) {
            0 -> canvas.drawCircle(cx, cy, r, p)
            1 -> { p.style = Paint.Style.STROKE; p.strokeWidth = textH * 0.08f; canvas.drawCircle(cx, cy, r * 0.9f, p) }
            else -> canvas.drawRect(cx - r, cy - r, cx + r, cy + r, p)
        }
    }
}

class CheckboxSpan(private val checked: Boolean, private val palette: MdPalette, private val indent: Int) : PrefixSpan() {

    override fun same(other: MdSpan) = other is CheckboxSpan && other.checked == checked && other.palette == palette && other.indent == indent

    override fun width(paint: Paint, text: CharSequence, start: Int, end: Int): Int {
        val pad = paint.measureText(text, start, start + indent)
        return maxOf(paint.measureText(text, start, end), pad + paint.textSize * 1.55f).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val size = paint.textSize * 1.05f
        val pad = paint.measureText(text, start, start + indent)
        val left = x + pad + paint.textSize * 0.05f
        val boxTop = y - paint.textSize * 0.83f
        val r = RectF(left, boxTop, left + size, boxTop + size)
        val corner = size * 0.24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (checked) {
            p.color = palette.accent
            canvas.drawRoundRect(r, corner, corner, p)
            p.color = palette.onAccent
            p.style = Paint.Style.STROKE
            p.strokeWidth = size * 0.13f
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            val path = android.graphics.Path().apply {
                moveTo(r.left + size * 0.24f, r.top + size * 0.52f)
                lineTo(r.left + size * 0.43f, r.top + size * 0.70f)
                lineTo(r.left + size * 0.77f, r.top + size * 0.32f)
            }
            canvas.drawPath(path, p)
        } else {
            p.color = palette.dim
            p.style = Paint.Style.STROKE
            p.strokeWidth = size * 0.1f
            r.inset(p.strokeWidth / 2, p.strokeWidth / 2)
            canvas.drawRoundRect(r, corner, corner, p)
        }
    }
}

class QuoteBarSpan(private val color: Int) : PrefixSpan() {
    override fun same(other: MdSpan) = other is QuoteBarSpan && other.color == color

    override fun width(paint: Paint, text: CharSequence, start: Int, end: Int): Int = (paint.textSize * 0.9f).toInt()

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@QuoteBarSpan.color }
        val w = paint.textSize * 0.18f
        canvas.drawRoundRect(RectF(x + w * 0.5f, top.toFloat(), x + w * 1.5f, bottom.toFloat()), w / 2, w / 2, p)
    }
}

/** Pose sur [text] (de [offset] à [offset] + longueur de ligne) les mises en forme d'une ligne lue. */
object MarkdownStyler {

    const val FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    private val HEADING_SIZES = floatArrayOf(1f, 1.6f, 1.35f, 1.15f)

    /** Une mise en forme à poser : le dessin et sa plage dans le texte. */
    class Placed(val span: MdSpan, val start: Int, val end: Int)

    fun styleLine(text: Spannable, offset: Int, line: MarkdownSyntax.Line, palette: MdPalette) {
        for (p in lineSpans(offset, line, palette)) text.setSpan(p.span, p.start, p.end, FLAGS)
    }

    /** Les mises en forme d'une ligne lue, dans l'ordre où [styleLine] les pose, sans les poser. */
    fun lineSpans(offset: Int, line: MarkdownSyntax.Line, palette: MdPalette): List<Placed> {
        val out = ArrayList<Placed>()
        for (t in line.tokens) add(out, offset, t, line, palette)
        return out
    }

    private fun add(out: MutableList<Placed>, offset: Int, t: Token, line: MarkdownSyntax.Line, palette: MdPalette) {
        val s = offset + t.start
        val e = offset + t.end
        if (e <= s) return
        fun set(span: MdSpan) { out += Placed(span, s, e) }
        when (t.kind) {
            Kind.MARKER -> set(MdColor(palette.marker))
            Kind.HEADING -> { set(MdSize(HEADING_SIZES[t.level.coerceIn(1, 3)])); set(MdStyle(Typeface.BOLD)) }
            Kind.BOLD -> set(MdStyle(Typeface.BOLD))
            Kind.ITALIC -> set(MdStyle(Typeface.ITALIC))
            Kind.UNDERLINE -> set(MdUnderline())
            Kind.STRIKE -> set(MdStrike())
            Kind.CODE -> { set(MdMono()); set(MdBackground(palette.codeBackground)) }
            Kind.LINK, Kind.WIKI -> { set(MdColor(palette.accent)); set(MdUnderline()) }
            Kind.BULLET -> set(BulletSpan(palette.accent, line.indent))
            Kind.CHECK -> set(CheckboxSpan(t.level == 1, palette, line.indent))
            Kind.DONE -> { set(MdStrike()); set(MdColor(palette.dim)) }
            Kind.NUMBER -> { set(MdColor(palette.accent)); set(MdStyle(Typeface.BOLD)) }
            Kind.QUOTE_BAR -> set(QuoteBarSpan(palette.accent))
            Kind.QUOTE -> { set(MdStyle(Typeface.ITALIC)); set(MdColor(palette.dim)) }
        }
    }

    /** Le rendu sans marques (aperçus de la bibliothèque) en texte mis en forme. */
    fun styledPreview(rendered: MarkdownSyntax.Rendered, palette: MdPalette): CharSequence {
        val out = android.text.SpannableString(rendered.text)
        for (t in rendered.tokens) {
            if (t.end <= t.start) continue
            fun set(span: Any) = out.setSpan(span, t.start, t.end, FLAGS)
            when (t.kind) {
                Kind.HEADING -> { set(MdStyle(Typeface.BOLD)); set(MdColor(palette.text)) }
                Kind.BOLD -> set(MdStyle(Typeface.BOLD))
                Kind.ITALIC, Kind.QUOTE -> set(MdStyle(Typeface.ITALIC))
                Kind.UNDERLINE -> set(MdUnderline())
                Kind.STRIKE -> set(MdStrike())
                Kind.CODE -> set(MdMono())
                Kind.LINK, Kind.WIKI -> set(MdColor(palette.accent))
                Kind.CHECK -> set(MdColor(if (t.level == 1) palette.accent else palette.dim))
                Kind.DONE -> { set(MdStrike()); set(MdColor(palette.marker)) }
                else -> Unit
            }
        }
        return out
    }
}
