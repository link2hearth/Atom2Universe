package com.Atom2Universe.app.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil

/**
 * Garde son unique enfant entier à l'écran, quelle que soit la hauteur de l'écran (petit
 * téléphone, téléphone couché…).
 *
 * Quand l'enfant tient, rien ne change : il est mesuré comme dans un cadre ordinaire, ses poids se
 * partagent la place. Sinon, les vues déclarées par [elastic] rapetissent d'abord, chacune jusqu'à
 * sa plus petite taille ; s'il dépasse encore, l'enfant est réduit d'un bloc, proportions gardées,
 * plutôt que coupé. Les touches suivent la réduction.
 *
 * L'enfant est centré dans le cadre. Avec [shrinks] à faux, le cadre se contente de le centrer :
 * pour un contenu qui défile déjà de lui-même.
 */
class FitFrame(context: Context, private val shrinks: Boolean = true) : ViewGroup(context) {
    private class Elastic(val view: View, val natural: Int, val least: Int, val square: Boolean)

    private val elastic = ArrayList<Elastic>()
    private var k = 1f

    /**
     * [view], déjà posée dans l'enfant avec une hauteur fixe, peut descendre jusqu'à [least] pixels
     * quand la place manque ; avec [square], sa largeur suit sa hauteur. Les premières déclarées
     * rapetissent les premières.
     */
    fun elastic(view: View, least: Int, square: Boolean = false) {
        elastic += Elastic(view, view.layoutParams.height, least, square)
    }

    private fun resize(e: Elastic, value: Int) {
        val lp = e.view.layoutParams
        lp.height = value
        if (e.square) lp.width = value
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (child == null) {
            setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec))
            return
        }
        val lp = child.layoutParams
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        k = 1f
        for (e in elastic) resize(e, e.natural)
        if (shrinks && h > 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            var natural = natural(child, widthMeasureSpec, w)
            var over = natural - h
            if (over > 0 && elastic.isNotEmpty()) {
                for (e in elastic) {
                    if (over <= 0) break
                    val cut = minOf(over, e.natural - e.least)
                    if (cut > 0) { resize(e, e.natural - cut); over -= cut }
                }
                natural = natural(child, widthMeasureSpec, w)
            }
            if (natural > h && natural > 0) {
                k = h.toFloat() / natural
                // Réduit, il a droit à plus de largeur, et à cette largeur il peut encore raccourcir.
                natural = natural(child, widthMeasureSpec, (w / k).toInt())
                k = (h.toFloat() / natural).coerceAtMost(1f)
                child.measure(widthSpec(widthMeasureSpec, (w / k).toInt(), lp), MeasureSpec.makeMeasureSpec(natural, MeasureSpec.EXACTLY))
            }
        }
        if (k >= 1f) {
            k = 1f
            child.measure(getChildMeasureSpec(widthMeasureSpec, 0, lp.width), getChildMeasureSpec(heightMeasureSpec, 0, lp.height))
        }
        setMeasuredDimension(
            resolveSize(ceil(child.measuredWidth * k).toInt(), widthMeasureSpec),
            resolveSize(ceil(child.measuredHeight * k).toInt(), heightMeasureSpec)
        )
    }

    /** La hauteur que l'enfant prendrait sans limite, à la largeur [width]. */
    private fun natural(child: View, parentSpec: Int, width: Int): Int {
        child.measure(widthSpec(parentSpec, width, child.layoutParams), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return child.measuredHeight
    }

    private fun widthSpec(parentSpec: Int, width: Int, lp: LayoutParams): Int =
        getChildMeasureSpec(MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(parentSpec)), 0, lp.width)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = getChildAt(0) ?: return
        val cw = child.measuredWidth
        val ch = child.measuredHeight
        val left = ((r - l - cw * k) / 2f).toInt()
        val top = ((b - t - ch * k) / 2f).toInt()
        child.layout(left, top, left + cw, top + ch)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = k
        child.scaleY = k
    }

    override fun shouldDelayChildPressedState() = false
}
