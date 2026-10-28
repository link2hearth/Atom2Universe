package com.Atom2Universe.app.crypto

import android.view.View

// Ce qui doit rester du widget dans son parent pour qu'on puisse encore l'attraper.
private const val KEEP_VISIBLE_DP = 48f

/**
 * Ramène un widget flottant glissé ou zoomé hors de l'écran : au moins [KEEP_VISIBLE_DP]
 * de lui reste dans le parent, et son haut (l'en-tête qui sert à le déplacer) jamais au-dessus.
 * À appeler en fin de glisser et de pincement.
 */
internal fun View.keepFloatingWidgetOnScreen() {
    val container = parent as? View ?: return
    if (width == 0 || height == 0 || container.width == 0 || container.height == 0) return
    val keep = KEEP_VISIBLE_DP * resources.displayMetrics.density
    val shownW = width * scaleX
    val shownH = height * scaleY
    val shownLeft = left + translationX + pivotX * (1f - scaleX)
    val shownTop = top + translationY + pivotY * (1f - scaleY)
    val minLeft = minOf(keep, shownW) - shownW
    val maxLeft = (container.width - minOf(keep, shownW)).coerceAtLeast(minLeft)
    val maxTop = (container.height - minOf(keep, shownH)).coerceAtLeast(0f)
    val dx = shownLeft.coerceIn(minLeft, maxLeft) - shownLeft
    val dy = shownTop.coerceIn(0f, maxTop) - shownTop
    if (dx != 0f) translationX += dx
    if (dy != 0f) translationY += dy
}

/** À appeler une fois à la construction : recale le widget quand son parent change de taille (rotation). */
internal fun View.keepFloatingWidgetOnScreenOnResize() {
    val onParentResized = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        // La première mise en page (ancienne taille nulle) ne déplace rien
        val resized = r - l != oldR - oldL || b - t != oldB - oldT
        if (resized && oldR > oldL && oldB > oldT) post { keepFloatingWidgetOnScreen() }
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        private var watched: View? = null
        override fun onViewAttachedToWindow(v: View) {
            watched = (v.parent as? View)?.also { it.addOnLayoutChangeListener(onParentResized) }
        }
        override fun onViewDetachedFromWindow(v: View) {
            watched?.removeOnLayoutChangeListener(onParentResized)
            watched = null
        }
    })
}
