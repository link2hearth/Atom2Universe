package com.Atom2Universe.app.games.link

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * La main du niveau : les pièces à poser, en rangées. Une pièce posée s'éteint ; celle
 * que le doigt est en train de dessiner sur le plateau s'allume.
 */
class LinkHandView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: LinkGame? = null
        set(value) { field = value; requestLayout(); invalidate() }

    /** La pièce que reconnaît la sélection en cours, -1 si aucune. */
    var highlighted = -1
        set(value) { if (field != value) { field = value; invalidate() } }

    private val density = resources.displayMetrics.density
    /** Côté d'une case de pièce, et écarts. */
    private val unit = 13f * density
    private val gap = 1.5f * density
    private val spacing = 14f * density
    private val rowGap = 10f * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Positions calculées au dernier `onMeasure` : x, y du coin de chaque pièce. */
    private var slots = FloatArray(0)

    fun refresh() {
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = layoutPieces(w.toFloat())
        setMeasuredDimension(w, resolveSize(h.toInt(), heightMeasureSpec))
    }

    /** Range les pièces en lignes centrées ; rend la hauteur nécessaire. */
    private fun layoutPieces(width: Float): Float {
        val g = game ?: return 0f
        val hand = g.hand
        slots = FloatArray(hand.size * 2)
        if (hand.isEmpty()) return 0f
        val usable = width - paddingLeft - paddingRight
        val rows = mutableListOf<MutableList<Int>>()
        var rowWidth = 0f
        for (i in hand.indices) {
            val pw = pieceWidth(hand[i])
            if (rows.isEmpty() || (rowWidth + spacing + pw > usable && rows.last().isNotEmpty())) {
                rows.add(mutableListOf()); rowWidth = -spacing
            }
            rows.last().add(i); rowWidth += spacing + pw
        }
        var y = paddingTop.toFloat()
        for (row in rows) {
            val total = row.sumOf { pieceWidth(hand[it]).toDouble() }.toFloat() + spacing * (row.size - 1)
            var x = paddingLeft + (usable - total) / 2f
            val rowHeight = row.maxOf { pieceHeight(hand[it]) }
            for (i in row) {
                slots[i * 2] = x
                slots[i * 2 + 1] = y + (rowHeight - pieceHeight(hand[i])) / 2f
                x += pieceWidth(hand[i]) + spacing
            }
            y += rowHeight + rowGap
        }
        return y - rowGap + paddingBottom
    }

    private fun shapeOf(s: Int) = LinkGame.SHAPES[s].cells
    private fun pieceWidth(s: Int) = (shapeOf(s).maxOf { it.first } + 1) * unit
    private fun pieceHeight(s: Int) = (shapeOf(s).maxOf { it.second } + 1) * unit

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        if (slots.size != g.hand.size * 2) return
        for (i in g.hand.indices) {
            val used = g.isUsed(i)
            val color = when {
                i == highlighted -> 0xFFE0F7FF.toInt()
                used -> 0x2E8C9EFF
                else -> 0xFF8C9EFF.toInt()
            }
            if (i == highlighted) {
                val cx = slots[i * 2] + pieceWidth(g.hand[i]) / 2f
                val cy = slots[i * 2 + 1] + pieceHeight(g.hand[i]) / 2f
                QuantumPainter.glow(canvas, cx, cy, max(pieceWidth(g.hand[i]), pieceHeight(g.hand[i])) * 0.8f, 0xFF4FC3F7.toInt(), 0.5f)
            }
            fill.color = color
            val r = unit * 0.22f
            for ((x, y) in shapeOf(g.hand[i])) {
                val l = slots[i * 2] + x * unit + gap
                val t = slots[i * 2 + 1] + y * unit + gap
                canvas.drawRoundRect(l, t, l + unit - 2 * gap, t + unit - 2 * gap, r, r, fill)
            }
        }
    }
}
