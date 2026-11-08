package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * Une rangée qui passe à la ligne quand elle manque de place : aucune commande n'est jamais cachée hors de l'écran
 * (les rangées qui défilent de côté cachaient le bouton « Octave + »). Les enfants d'une même ligne sont centrés
 * verticalement. Les marges des enfants sont respectées.
 */
class FlowRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    /** Espaces entre commandes, en pixels. */
    var hGap = 0
    var vGap = 0

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams): Boolean = p is MarginLayoutParams

    private fun outerW(c: View): Int {
        val lp = c.layoutParams as MarginLayoutParams
        return c.measuredWidth + lp.leftMargin + lp.rightMargin
    }

    private fun outerH(c: View): Int {
        val lp = c.layoutParams as MarginLayoutParams
        return c.measuredHeight + lp.topMargin + lp.bottomMargin
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        var usedW = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChildWithMargins(c, widthSpec, 0, heightSpec, 0)
            val cw = outerW(c)
            val ch = outerH(c)
            if (x > 0 && x + cw > maxW) {
                y += rowH + vGap
                x = 0
                rowH = 0
            }
            x += cw + hGap
            rowH = max(rowH, ch)
            usedW = max(usedW, x - hGap)
        }
        val width = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthSpec)
        else usedW + paddingLeft + paddingRight
        setMeasuredDimension(width, y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var i = 0
        var y = paddingTop
        while (i < childCount) {
            // Une ligne : de i jusqu'au dernier enfant qui tient
            var j = i
            var x = 0
            var rowH = 0
            while (j < childCount) {
                val c = getChildAt(j)
                if (c.visibility == GONE) {
                    j++
                    continue
                }
                val cw = outerW(c)
                if (x > 0 && x + cw > maxW) break
                x += cw + hGap
                rowH = max(rowH, outerH(c))
                j++
            }
            x = paddingLeft
            for (k in i until j) {
                val c = getChildAt(k)
                if (c.visibility == GONE) continue
                val lp = c.layoutParams as MarginLayoutParams
                val top = y + (rowH - outerH(c)) / 2 + lp.topMargin
                val left = x + lp.leftMargin
                c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
                x += outerW(c) + hGap
            }
            y += rowH + vGap
            i = if (j == i) j + 1 else j
        }
    }
}
