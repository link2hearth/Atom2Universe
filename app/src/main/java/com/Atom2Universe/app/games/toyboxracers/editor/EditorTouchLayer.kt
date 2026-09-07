package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

internal class EditorTouchLayer(context: Context) : View(context) {
    var onMoveAxesChanged: ((strafe: Float, forward: Float) -> Unit)? = null
    var onLookAxesChanged: ((yaw: Float, pitch: Float) -> Unit)? = null
    var onObjectDrag: ((dx: Float, dy: Float) -> Unit)? = null
    var onObjectLongPress: ((x: Float, y: Float) -> Unit)? = null
    var onTap: ((Float, Float) -> Unit)? = null
    var onHandleDown: ((Float, Float) -> Boolean)? = null
    var onHandleMove: ((Float, Float) -> Unit)? = null
    var onHandleEnd: ((Boolean) -> Unit)? = null
    var handles: (() -> List<Pair<Float, Float>>)? = null
    var selectedHandle = -1
    private var handleDragging = false
    private var moved = false

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x663B4055 }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xA8FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD8FFFFFF.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 26f
        isFakeBoldText = true
    }

    private var movePointer = MotionEvent.INVALID_POINTER_ID
    private var lookPointer = MotionEvent.INVALID_POINTER_ID
    private var dragPointer = MotionEvent.INVALID_POINTER_ID
    private var moveBaseX = 0f
    private var moveBaseY = 0f
    private var lookBaseX = 0f
    private var lookBaseY = 0f
    private var moveX = 0f
    private var moveY = 0f
    private var lookX = 0f
    private var lookY = 0f
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var longPressX = 0f
    private var longPressY = 0f
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        if (dragPointer != MotionEvent.INVALID_POINTER_ID && !longPressTriggered) {
            longPressTriggered = true
            onObjectLongPress?.invoke(longPressX, longPressY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        handles?.invoke()?.forEachIndexed { index, point ->
            knobPaint.color = if (index == selectedHandle) 0xFFFFC857.toInt() else Color.WHITE
            canvas.drawCircle(point.first, point.second, resources.displayMetrics.density * 12f, basePaint)
            canvas.drawCircle(point.first, point.second, resources.displayMetrics.density * 8f, knobPaint)
        }
        if (visibility == VISIBLE) postInvalidateOnAnimation()
        if (movePointer != MotionEvent.INVALID_POINTER_ID) drawJoystick(canvas, moveBaseX, moveBaseY, moveX, moveY, "MOVE")
        if (lookPointer != MotionEvent.INVALID_POINTER_ID) drawJoystick(canvas, lookBaseX, lookBaseY, lookX, lookY, "VIEW")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> startPointer(event.actionIndex, event)
            MotionEvent.ACTION_MOVE -> updatePointers(event)
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val i = event.actionIndex
                if (event.getPointerId(i) == dragPointer) {
                    if (handleDragging) onHandleEnd?.invoke(false)
                    else if (!moved && !longPressTriggered) onTap?.invoke(event.getX(i), event.getY(i))
                    handleDragging = false
                }
                stopPointer(event.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> resetAll()
        }
        return true
    }

    private fun startPointer(index: Int, event: MotionEvent) {
        val pointerId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val joystickZoneTop = height * 0.62f
        if (dragPointer == MotionEvent.INVALID_POINTER_ID && onHandleDown?.invoke(x, y) == true) {
            dragPointer = pointerId
            handleDragging = true
            return
        }
        when {
            x < width * 0.34f && y > joystickZoneTop && movePointer == MotionEvent.INVALID_POINTER_ID -> {
                movePointer = pointerId
                moveBaseX = x
                moveBaseY = y
                updateMove(x, y)
            }
            x > width * 0.66f && y > joystickZoneTop && lookPointer == MotionEvent.INVALID_POINTER_ID -> {
                lookPointer = pointerId
                lookBaseX = x
                lookBaseY = y
                updateLook(x, y)
            }
            dragPointer == MotionEvent.INVALID_POINTER_ID -> {
                dragPointer = pointerId
                dragLastX = x
                dragLastY = y
                longPressX = x
                longPressY = y
                longPressTriggered = false
                moved = false
                postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
        }
        invalidate()
    }

    private fun updatePointers(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            when (event.getPointerId(i)) {
                movePointer -> updateMove(event.getX(i), event.getY(i))
                lookPointer -> updateLook(event.getX(i), event.getY(i))
                dragPointer -> updateDrag(event.getX(i), event.getY(i))
            }
        }
        invalidate()
    }

    private fun stopPointer(pointerId: Int) {
        when (pointerId) {
            movePointer -> {
                movePointer = MotionEvent.INVALID_POINTER_ID
                moveX = 0f
                moveY = 0f
                onMoveAxesChanged?.invoke(0f, 0f)
            }
            lookPointer -> {
                lookPointer = MotionEvent.INVALID_POINTER_ID
                lookX = 0f
                lookY = 0f
                onLookAxesChanged?.invoke(0f, 0f)
            }
            dragPointer -> {
                dragPointer = MotionEvent.INVALID_POINTER_ID
                removeCallbacks(longPressRunnable)
            }
        }
        invalidate()
    }

    private fun resetAll() {
        if (handleDragging) onHandleEnd?.invoke(true)
        handleDragging = false
        movePointer = MotionEvent.INVALID_POINTER_ID
        lookPointer = MotionEvent.INVALID_POINTER_ID
        dragPointer = MotionEvent.INVALID_POINTER_ID
        moveX = 0f
        moveY = 0f
        lookX = 0f
        lookY = 0f
        removeCallbacks(longPressRunnable)
        onMoveAxesChanged?.invoke(0f, 0f)
        onLookAxesChanged?.invoke(0f, 0f)
        invalidate()
    }

    private fun updateMove(x: Float, y: Float) {
        val axes = axesFrom(moveBaseX, moveBaseY, x, y)
        moveX = axes.first
        moveY = axes.second
        onMoveAxesChanged?.invoke(moveX, -moveY)
    }

    private fun updateLook(x: Float, y: Float) {
        val axes = axesFrom(lookBaseX, lookBaseY, x, y)
        lookX = axes.first
        lookY = axes.second
        onLookAxesChanged?.invoke(lookX, -lookY)
    }

    private fun updateDrag(x: Float, y: Float) {
        if (handleDragging) {
            onHandleMove?.invoke(x, y)
            return
        }
        val dx = x - dragLastX
        val dy = y - dragLastY
        if (hypot(x - longPressX, y - longPressY) > touchSlop) {
            moved = true
            removeCallbacks(longPressRunnable)
        }
        dragLastX = x
        dragLastY = y
        if (!longPressTriggered) onObjectDrag?.invoke(dx, dy)
    }

    private fun axesFrom(baseX: Float, baseY: Float, x: Float, y: Float): Pair<Float, Float> {
        val radius = joystickRadius()
        val dx = x - baseX
        val dy = y - baseY
        val length = hypot(dx, dy).coerceAtLeast(0.0001f)
        val scale = minOf(1f, length / radius)
        return dx / length * scale to dy / length * scale
    }

    private fun drawJoystick(canvas: Canvas, baseX: Float, baseY: Float, x: Float, y: Float, label: String) {
        val radius = joystickRadius()
        canvas.drawCircle(baseX, baseY, radius, basePaint)
        canvas.drawCircle(baseX, baseY, radius, ringPaint)
        canvas.drawCircle(baseX + x * radius, baseY + y * radius, radius * 0.34f, knobPaint)
        canvas.drawText(label, baseX, baseY + textPaint.textSize * 0.35f, textPaint)
    }

    private fun joystickRadius() = minOf(width, height) * 0.095f

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) resetAll()
    }

    private val touchSlop get() = resources.displayMetrics.density * 12f

    companion object {
        private const val LONG_PRESS_MS = 420L
    }
}
