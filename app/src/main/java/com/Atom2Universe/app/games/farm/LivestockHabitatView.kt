package com.Atom2Universe.app.games.farm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.*

/** One herd per screen, with bounded horizontal paging instead of map panning. */
class LivestockHabitatView(context: Context, sprites: FarmSprites, state: LivestockState) : View(context) {
    private val scene = LivestockScene(context, sprites, state)
    var selected = LivestockKind.CHICKENS
        private set
    var onPageChanged: ((LivestockKind) -> Unit)? = null
    var onOpen: ((LivestockKind) -> Unit)? = null
    var dismissBubble: (() -> Boolean)? = null
    private var shift = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var consumed = false
    private var downTime = 0L
    private var lastFrame = 0L
    private var animator: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    init { isClickable = true }
    fun select(kind: LivestockKind, animate: Boolean = true) {
        if (kind == selected && shift == 0f) return
        animator?.cancel()
        if (!animate || width == 0) { selected = kind; shift = 0f; onPageChanged?.invoke(kind); invalidate(); return }
        val direction = (kind.ordinal - selected.ordinal).sign
        settle(if (direction == 0) selected else LivestockKind.entries[selected.ordinal + direction])
    }
    private fun settle(next: LivestockKind) {
        val destination = when { next.ordinal > selected.ordinal -> -width.toFloat(); next.ordinal < selected.ordinal -> width.toFloat(); else -> 0f }
        animator = ValueAnimator.ofFloat(shift, destination).apply {
            duration = 230
            addUpdateListener { shift = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) { selected = next; shift = 0f; onPageChanged?.invoke(next); invalidate() }
                }
            }); start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.0 else ((now - lastFrame) / 1000.0).coerceIn(0.0, .06)
        lastFrame = now
        canvas.drawColor(Color.rgb(161, 202, 139))
        if (width > 0 && height > 0) {
            val scale = min(width / 320f, height / 330f)
            val logicalHeight = (height / scale).toInt().coerceIn(330, 800)
            fun page(kind: LivestockKind, offset: Float) {
                canvas.save(); canvas.clipRect(offset, 0f, offset + width, height.toFloat())
                canvas.translate(offset + (width - 320 * scale) / 2, (height - logicalHeight * scale) / 2)
                canvas.scale(scale, scale); scene.drawPage(canvas, kind, logicalHeight, dt); canvas.restore()
            }
            page(selected, shift)
            if (shift < 0) LivestockKind.entries.getOrNull(selected.ordinal + 1)?.let { page(it, shift + width) }
            if (shift > 0) LivestockKind.entries.getOrNull(selected.ordinal - 1)?.let { page(it, shift - width) }
        }
        if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility); lastFrame = 0
        if (visibility == VISIBLE) invalidate()
    }
    override fun onDetachedFromWindow() { animator?.cancel(); lastFrame = 0; super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); onOpen?.invoke(selected); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel(); consumed = dismissBubble?.invoke() == true
                downX = event.x - shift; downY = event.y; downTime = event.eventTime; dragged = false
                parent?.requestDisallowInterceptTouchEvent(true); return true
            }
            MotionEvent.ACTION_MOVE -> if (!consumed) {
                val dx = event.x - downX
                if (abs(dx) > slop && abs(dx) > abs(event.y - downY)) dragged = true
                if (dragged) {
                    val edge = (selected.ordinal == 0 && dx > 0) || (selected.ordinal == LivestockKind.entries.lastIndex && dx < 0)
                    shift = (if (edge) dx * .2f else dx).coerceIn(-width.toFloat(), width.toFloat()); invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!consumed) {
                    if (dragged) {
                        val fast = abs(shift) > slop * 3 && event.eventTime - downTime < 280
                        val step = if (abs(shift) > width * .18f || fast) { if (shift < 0) 1 else -1 } else 0
                        settle(LivestockKind.entries[(selected.ordinal + step).coerceIn(0, LivestockKind.entries.lastIndex)])
                    } else performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); settle(selected) }
        }
        return true
    }
}
