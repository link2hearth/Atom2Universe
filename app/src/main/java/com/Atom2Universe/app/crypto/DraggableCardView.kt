package com.Atom2Universe.app.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.card.MaterialCardView
import kotlin.math.hypot
import kotlin.math.max

/**
 * Card that can be dragged from anywhere, its buttons included: a tap still presses them, a slide moves the card.
 * A finger that wobbles a little while pressing is still a tap (and a long press): only a real slide is a drag.
 */
class DraggableCardView(context: Context, attrs: AttributeSet?) : MaterialCardView(context, attrs) {
    private val slop = max(ViewConfiguration.get(context).scaledTouchSlop * 2.5f, resources.displayMetrics.density * 20f)
    private var downX = 0f
    private var downY = 0f
    private var dx = 0f
    private var dy = 0f

    private fun grab(event: MotionEvent) {
        downX = event.rawX
        downY = event.rawY
        dx = translationX - event.rawX
        dy = translationY - event.rawY
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> grab(event)
            MotionEvent.ACTION_MOVE -> if (hypot(event.rawX - downX, event.rawY - downY) > slop) {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                grab(event)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                translationX = event.rawX + dx
                translationY = event.rawY + dy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                keepFloatingWidgetOnScreen()
            }
        }
        return true
    }
}
