package com.Atom2Universe.app.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.ceil

/**
 * Garde une rangée de boutons (bande du haut, barre d'outils) entière à l'écran sur un téléphone
 * étroit. Son unique enfant est la rangée, un LinearLayout horizontal.
 *
 * Quand tout tient, rien ne change. Sinon (les boutons de droite sortiraient de l'écran, ou le
 * titre à poids n'aurait plus de place), la rangée entière est réduite d'un bloc, proportions
 * gardées, juste assez pour que ses vues fixes tiennent et que chaque vue à poids garde au moins
 * [FLEX_DP] (ou sa largeur naturelle, si elle est plus petite). Les touches suivent la réduction.
 *
 * S'utilise en XML (`<com.Atom2Universe.app.util.FitRow>` autour de la rangée) ou en code.
 */
class FitRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var k = 1f

    /** La place, en dp, qu'une vue à poids (le titre, une bourse…) garde au moins. */
    var flexDp = FLEX_DP

    init {
        // L'ombre portée de la rangée (élévation) déborde du cadre : elle ne doit pas être rognée.
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (child == null || child.visibility == View.GONE || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            k = 1f
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val lp = child.layoutParams as MarginLayoutParams
        val w = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - lp.leftMargin - lp.rightMargin
        val need = needed(child)
        k = if (need > w && need > 0) w.toFloat() / need else 1f
        val height = if (lp.height >= 0) MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
        else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val sides = paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin
        if (k < 1f) {
            child.measure(MeasureSpec.makeMeasureSpec((w / k).toInt(), MeasureSpec.EXACTLY), height)
        } else {
            // Tout tient : la rangée garde sa largeur à elle (toute la place, ou juste la sienne).
            child.measure(getChildMeasureSpec(widthMeasureSpec, sides, lp.width), height)
        }
        val ownWidth = ceil(child.measuredWidth * k).toInt() + sides
        val ownHeight = ceil(child.measuredHeight * k).toInt() + paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin
        setMeasuredDimension(resolveSize(ownWidth, widthMeasureSpec), resolveSize(ownHeight, heightMeasureSpec))
    }

    /** La largeur dont la rangée a besoin : ses vues fixes en entier, ses vues à poids un peu. */
    private fun needed(row: View): Int {
        if (row !is LinearLayout || row.orientation != LinearLayout.HORIZONTAL) return 0
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val flex = (flexDp * resources.displayMetrics.density).toInt()
        var sum = row.paddingLeft + row.paddingRight
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c.visibility == View.GONE) continue
            val p = c.layoutParams as LinearLayout.LayoutParams
            val width = when {
                p.width >= 0 && p.weight == 0f -> p.width
                else -> {
                    val hSpec = if (p.height >= 0) MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY) else free
                    c.measure(free, hSpec)
                    if (p.weight > 0f) minOf(c.measuredWidth, flex) else c.measuredWidth
                }
            }
            sum += width + p.leftMargin + p.rightMargin
        }
        return sum
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val lp = child.layoutParams as MarginLayoutParams
        val x = paddingLeft + lp.leftMargin
        val y = paddingTop + lp.topMargin
        child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = k
        child.scaleY = k
    }

    private companion object {
        /** Par défaut, la place qu'une vue à poids (le titre) garde au moins, en dp. */
        const val FLEX_DP = 64f
    }
}
