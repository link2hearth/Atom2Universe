package com.example.atom2univers

import android.content.Context
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.util.contains

/**
 * Custom WebView that normalises multi-touch input for the clicker gameplay.
 *
 * The stock WebView aggressively interprets simultaneous touches as zoom or
 * complex gestures. When the player performs rapid taps with several fingers,
 * Android can cancel the underlying stream which breaks both tapping and page
 * scrolling. This view decomposes MotionEvents so that every finger is treated
 * independently while keeping scrolling responsive.
 */
class GameWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private data class PointerState(
        val downTime: Long,
        var lastX: Float,
        var lastY: Float
    )

    private val pointerStates = SparseArray<PointerState>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex

        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                registerPointer(event, 0, event.downTime)
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                registerPointer(event, actionIndex, event.eventTime)
                val handled = dispatchPointerEvent(event, actionIndex, MotionEvent.ACTION_DOWN)
                return if (handled) {
                    true
                } else {
                    super.onTouchEvent(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    var handled = false
                    for (i in 0 until event.pointerCount) {
                        handled = dispatchPointerEvent(event, i, MotionEvent.ACTION_MOVE) || handled
                    }
                    return if (handled) {
                        true
                    } else {
                        super.onTouchEvent(event)
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val handled = dispatchPointerEvent(event, actionIndex, MotionEvent.ACTION_UP)
                unregisterPointer(event, actionIndex)
                return if (handled) {
                    true
                } else {
                    super.onTouchEvent(event)
                }
            }

            MotionEvent.ACTION_UP -> {
                val handled = super.onTouchEvent(event)
                unregisterPointer(event, actionIndex)
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                sendCancelForActivePointers(event)
                pointerStates.clear()
                updateParentInterception()
                return super.onTouchEvent(event)
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun registerPointer(event: MotionEvent, pointerIndex: Int, downTime: Long) {
        val pointerId = event.getPointerId(pointerIndex)
        if (!pointerStates.contains(pointerId)) {
            pointerStates.put(
                pointerId,
                PointerState(
                    downTime = downTime,
                    lastX = event.getX(pointerIndex),
                    lastY = event.getY(pointerIndex)
                )
            )
            updateParentInterception()
        }
    }

    private fun unregisterPointer(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        pointerStates.remove(pointerId)
        updateParentInterception()
    }

    private fun dispatchPointerEvent(event: MotionEvent, pointerIndex: Int, action: Int): Boolean {
        val pointerId = event.getPointerId(pointerIndex)
        val state = pointerStates[pointerId]
            ?: PointerState(event.downTime, event.getX(pointerIndex), event.getY(pointerIndex))
        val pointerProperties = MotionEvent.PointerProperties().apply {
            id = pointerId
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val pointerCoords = MotionEvent.PointerCoords().apply {
            event.getPointerCoords(pointerIndex, this)
            state.lastX = x
            state.lastY = y
        }

        pointerStates.put(pointerId, state)

        val singlePointerEvent = MotionEvent.obtain(
            state.downTime,
            event.eventTime,
            action,
            1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            event.source,
            event.flags
        )

        if (event.historySize > 0) {
            for (h in 0 until event.historySize) {
                val historicalCoords = MotionEvent.PointerCoords().apply {
                    event.getHistoricalPointerCoords(pointerIndex, h, this)
                }
                singlePointerEvent.addBatch(
                    event.getHistoricalEventTime(h),
                    arrayOf(historicalCoords),
                    event.metaState
                )
            }
        }

        val handled = super.onTouchEvent(singlePointerEvent)
        singlePointerEvent.recycle()
        return handled
    }

    private fun sendCancelForActivePointers(reference: MotionEvent) {
        for (i in 0 until pointerStates.size()) {
            val pointerId = pointerStates.keyAt(i)
            val state = pointerStates.valueAt(i)
            val pointerProperties = MotionEvent.PointerProperties().apply {
                id = pointerId
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val pointerCoords = MotionEvent.PointerCoords().apply {
                x = state.lastX
                y = state.lastY
            }
            val cancelEvent = MotionEvent.obtain(
                state.downTime,
                reference.eventTime,
                MotionEvent.ACTION_CANCEL,
                1,
                arrayOf(pointerProperties),
                arrayOf(pointerCoords),
                reference.metaState,
                reference.buttonState,
                reference.xPrecision,
                reference.yPrecision,
                reference.deviceId,
                reference.edgeFlags,
                reference.source,
                reference.flags
            )
            super.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
        }
    }

    private fun updateParentInterception() {
        parent?.requestDisallowInterceptTouchEvent(pointerStates.size() > 0)
    }
}
