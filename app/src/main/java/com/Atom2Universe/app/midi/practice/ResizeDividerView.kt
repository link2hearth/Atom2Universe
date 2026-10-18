package com.Atom2Universe.app.midi.practice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

/**
 * A draggable divider view that allows users to resize the split between
 * falling notes and piano keyboard in practice mode.
 *
 * The divider shows a subtle handle indicator and can be dragged vertically.
 */
class ResizeDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callback when the user drags the divider
    var onDragListener: ((deltaY: Float) -> Unit)? = null

    // Callback when drag starts
    var onDragStartListener: (() -> Unit)? = null

    // Callback when drag ends
    var onDragEndListener: (() -> Unit)? = null

    private var lastTouchY = 0f
    private var isDragging = false

    // Paint for the handle indicator
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.midi_text_secondary)
        alpha = 128
    }

    // Paint for the divider line
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.midi_divider)
        strokeWidth = 1f
    }

    // Handle dimensions
    private val handleWidth = 40f * resources.displayMetrics.density
    private val handleHeight = 4f * resources.displayMetrics.density
    private val handleCornerRadius = 2f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // Draw a subtle line across the full width
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, linePaint)

        // Draw the handle indicator in the center
        val handleLeft = centerX - handleWidth / 2
        val handleTop = centerY - handleHeight / 2
        val handleRight = centerX + handleWidth / 2
        val handleBottom = centerY + handleHeight / 2

        // Make handle more visible when dragging
        if (isDragging) {
            handlePaint.alpha = 200
        } else {
            handlePaint.alpha = 128
        }

        canvas.drawRoundRect(
            handleLeft, handleTop, handleRight, handleBottom,
            handleCornerRadius, handleCornerRadius,
            handlePaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.rawY
                isDragging = true
                onDragStartListener?.invoke()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = event.rawY - lastTouchY
                    lastTouchY = event.rawY
                    onDragListener?.invoke(deltaY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onDragEndListener?.invoke()
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
