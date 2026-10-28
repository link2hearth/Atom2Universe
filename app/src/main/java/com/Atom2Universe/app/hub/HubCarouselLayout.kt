package com.Atom2Universe.app.hub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Deux files de tuiles côte à côte : un balayage horizontal passe de l'une à l'autre, et comme il
 * n'y en a que deux, on revient toujours à la première en continuant dans le même sens (carrousel
 * infini). Les deux premiers enfants sont les deux listes ; la seconde reste en place mais invisible
 * tant qu'on ne balaie pas, pour que sa grille soit déjà mesurée.
 *
 * Pendant qu'une tuile est tenue au doigt, deux bandes sur les bords de l'écran proposent de la
 * déposer dans l'autre file.
 */
class HubCarouselLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Appelé quand la file affichée change (0 ou 1). */
    var onPaneChanged: ((Int) -> Unit)? = null

    var current = 0
        private set

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = 700f * resources.displayMetrics.density
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var animator: ValueAnimator? = null
    private var offset = 0f

    private val edgeLeft = edgeView(leftSide = true)
    private val edgeRight = edgeView(leftSide = false)

    private fun pane(i: Int): View = getChildAt(i)

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 1) pane(1).visibility = INVISIBLE
        addView(edgeLeft, LayoutParams((EDGE_DP * resources.displayMetrics.density).toInt(), LayoutParams.MATCH_PARENT, android.view.Gravity.START))
        addView(edgeRight, LayoutParams((EDGE_DP * resources.displayMetrics.density).toInt(), LayoutParams.MATCH_PARENT, android.view.Gravity.END))
    }

    private fun edgeView(leftSide: Boolean) = View(context).apply {
        val accent = 0xFF4ADE80.toInt()
        val faded = 0x004ADE80
        background = GradientDrawable(
            if (leftSide) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(accent, faded)
        )
        visibility = GONE
        alpha = 0f
    }

    /** Une tuile est tenue au doigt : montre les bandes ; `hot` quand elle est dessus. */
    fun setEdgeDrag(isDragging: Boolean, hot: Boolean) {
        val target = when {
            !isDragging -> 0f
            hot -> 0.9f
            else -> 0.3f
        }
        for (e in listOf(edgeLeft, edgeRight)) {
            if (target > 0f) e.visibility = VISIBLE
            e.animate().alpha(target).setDuration(120).withEndAction {
                if (target == 0f) e.visibility = GONE
            }.start()
        }
    }

    // ── Balayage ──────────────────────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (childCount < 4) return false
        if (animator?.isRunning == true) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                if (!dragging) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy) * 1.6f) {
                        dragging = true
                        downX = ev.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.recycle()
                velocity = null
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (childCount < 4) return false
        if (animator?.isRunning == true) return true
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) setOffset(ev.x - downX)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    velocity?.computeCurrentVelocity(1000)
                    val vx = velocity?.xVelocity ?: 0f
                    val goesNext = abs(offset) > width * 0.3f ||
                        (abs(vx) > minFling && (vx < 0) == (offset < 0) && offset != 0f)
                    settle(if (goesNext && ev.actionMasked == MotionEvent.ACTION_UP) 1 else 0)
                }
                dragging = false
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }

    /** Place la file affichée à `off` pixels de sa position, et l'autre juste à côté. */
    private fun setOffset(off: Float) {
        val w = width.toFloat()
        offset = off.coerceIn(-w, w)
        val shown = pane(current)
        val other = pane(1 - current)
        shown.translationX = offset
        if (offset == 0f) {
            other.visibility = INVISIBLE
            other.translationX = 0f
        } else {
            other.visibility = VISIBLE
            other.translationX = if (offset < 0) offset + w else offset - w
        }
    }

    /** `1` pour aller jusqu'à l'autre file, `0` pour revenir où on était. */
    private fun settle(change: Int) {
        val w = width.toFloat()
        val target = if (change == 1) (if (offset < 0) -w else w) else 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(offset, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { setOffset(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    if (change == 1) flip()
                }
            })
            start()
        }
    }

    /** L'autre file devient la file affichée. */
    private fun flip() {
        val old = pane(current)
        current = 1 - current
        old.translationX = 0f
        old.visibility = INVISIBLE
        pane(current).translationX = 0f
        pane(current).visibility = VISIBLE
        offset = 0f
        onPaneChanged?.invoke(current)
    }

    /** Montre la file demandée, avec ou sans glissement. */
    fun showPane(index: Int, animated: Boolean) {
        if (childCount < 4 || index == current) return
        if (!animated) {
            flip()
            return
        }
        offset = 0f
        val w = width.toFloat()
        // Le glissement va dans le sens habituel du balayage : la nouvelle file arrive par la droite.
        pane(1 - current).visibility = VISIBLE
        pane(1 - current).translationX = w
        settleFromZero()
    }

    private fun settleFromZero() {
        val w = width.toFloat()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, -w).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { setOffset(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                // Annulé (une autre animation le remplace) : onAnimationEnd suit quand même
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) flip()
                }
            })
            start()
        }
    }

    private companion object {
        const val EDGE_DP = 56
    }
}
