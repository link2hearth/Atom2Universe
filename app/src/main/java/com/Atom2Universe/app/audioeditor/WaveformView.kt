package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that displays audio waveform with playback position indicator.
 * Supports zoom (pinch), scroll (pan), and selection with draggable handles.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val HANDLE_WIDTH = 24f
        private const val HANDLE_TOUCH_SLOP = 40f
    }

    /**
     * Color modes for waveform display
     */
    enum class ColorMode {
        NORMAL,           // Classic green
        FREQUENCY,        // Colored by frequency content
        GRADIENT,         // Gradient from left to right
        RAINBOW,          // Rainbow colors
        MIRROR            // Mirror style (bars from center)
    }

    // Color mode
    private var colorMode: ColorMode = ColorMode.NORMAL
    private var frequencyColors: IntArray? = null

    // Waveform data - protected by dataLock for thread-safe access
    private val dataLock = Any()
    @Volatile
    private var waveformData: WaveformData? = null
    private var playbackProgress: Float = 0f // 0.0 to 1.0

    // Selection (in progress units 0.0 to 1.0)
    private var selectionStart: Float = -1f
    private var selectionEnd: Float = -1f

    // Selection dragging state - isDragging tracks active drag for cleanup
    private enum class DragMode { NONE, LEFT_HANDLE, RIGHT_HANDLE, CREATE_SELECTION, SCROLL }
    @Volatile
    private var dragMode = DragMode.NONE
    private var dragStartX = 0f
    private var dragStartProgress = 0f

    // Zoom and scroll
    private var zoomLevel: Float = 1f
    private var scrollOffset: Float = 0f

    // Paints
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt()
        style = Paint.Style.FILL
    }

    private val waveformSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#81C784".toColorInt()
        style = Paint.Style.FILL
    }

    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF5722".toColorInt()
        strokeWidth = 3f
        style = Paint.Style.FILL_AND_STROKE
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2196F3".toColorInt()
        alpha = 50
        style = Paint.Style.FILL
    }

    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2196F3".toColorInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2196F3".toColorInt()
        style = Paint.Style.FILL
    }

    private val handleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1976D2".toColorInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val backgroundPaint = Paint().apply {
        color = "#1E1E1E".toColorInt()
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#333333".toColorInt()
        strokeWidth = 1f
    }

    // Gesture detectors
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // Callbacks
    var onSeekListener: ((Float) -> Unit)? = null
    var onSelectionChangedListener: ((Float, Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Center line
        val centerY = height / 2f
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, centerLinePaint)

        // Bug 2.15: Synchronized access to waveform data for thread-safe blitting
        val data: WaveformData?
        synchronized(dataLock) {
            data = waveformData
        }

        // Draw waveform
        data?.let {
            drawWaveform(canvas, it, centerY)
        }

        // Draw selection
        if (hasSelection()) {
            drawSelection(canvas)
            drawSelectionHandles(canvas)
        }

        // Draw playhead
        drawPlayhead(canvas)
    }

    private fun drawWaveform(canvas: Canvas, data: WaveformData, centerY: Float) {
        val amplitudes = data.amplitudes
        if (amplitudes.isEmpty()) return

        val visibleStart = scrollOffset
        val visibleEnd = min(1f, scrollOffset + 1f / zoomLevel)

        val startIndex = (visibleStart * amplitudes.size).toInt().coerceIn(0, amplitudes.size - 1)
        val endIndex = (visibleEnd * amplitudes.size).toInt().coerceIn(0, amplitudes.size)

        val barWidth = width.toFloat() / ((endIndex - startIndex).coerceAtLeast(1))

        for (i in startIndex until endIndex) {
            val amplitude = amplitudes[i]
            val barHeight = amplitude * centerY * 0.9f
            val x = (i - startIndex) * barWidth
            val progress = i.toFloat() / amplitudes.size
            val normalizedX = (i - startIndex).toFloat() / (endIndex - startIndex).coerceAtLeast(1)

            // Get color based on mode
            val barColor = getBarColor(i, normalizedX)

            // Choose paint (selected or normal)
            val paint = if (hasSelection() && progress >= selectionStart && progress <= selectionEnd) {
                waveformSelectedPaint.also { it.color = lightenColor(barColor) }
            } else {
                waveformPaint.also { it.color = barColor }
            }

            when (colorMode) {
                ColorMode.MIRROR -> {
                    // Draw bars from center with gap
                    val gap = 2f
                    val topRect = RectF(x, centerY - barHeight, x + barWidth - 1f, centerY - gap)
                    val bottomRect = RectF(x, centerY + gap, x + barWidth - 1f, centerY + barHeight)
                    canvas.drawRect(topRect, paint)
                    canvas.drawRect(bottomRect, paint)
                }
                else -> {
                    val rect = RectF(x, centerY - barHeight, x + barWidth - 1f, centerY + barHeight)
                    canvas.drawRect(rect, paint)
                }
            }
        }
    }

    private fun getBarColor(index: Int, normalizedX: Float): Int {
        return when (colorMode) {
            ColorMode.NORMAL -> "#4CAF50".toColorInt()
            ColorMode.FREQUENCY -> {
                frequencyColors?.getOrNull(index) ?: "#4CAF50".toColorInt()
            }
            ColorMode.GRADIENT -> {
                // Blue to Purple gradient
                blendColors(
                    "#2196F3".toColorInt(),
                    "#9C27B0".toColorInt(),
                    normalizedX
                )
            }
            ColorMode.RAINBOW -> {
                // Rainbow based on position
                val hue = normalizedX * 300f  // 0 to 300 (red to violet)
                Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
            }
            ColorMode.MIRROR -> {
                // Cyan to Pink gradient for mirror mode
                blendColors(
                    "#00BCD4".toColorInt(),
                    "#E91E63".toColorInt(),
                    normalizedX
                )
            }
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * r).toInt(),
            (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * r).toInt(),
            (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * r).toInt()
        )
    }

    private fun lightenColor(color: Int): Int {
        val factor = 1.3f
        return Color.rgb(
            min(255, (Color.red(color) * factor).toInt()),
            min(255, (Color.green(color) * factor).toInt()),
            min(255, (Color.blue(color) * factor).toInt())
        )
    }

    private fun drawSelection(canvas: Canvas) {
        val startX = progressToX(selectionStart)
        val endX = progressToX(selectionEnd)

        if (startX < 0 && endX < 0) return

        val left = max(0f, startX)
        val right = min(width.toFloat(), endX)

        // Selection background
        canvas.drawRect(left, 0f, right, height.toFloat(), selectionPaint)

        // Selection borders
        if (startX >= 0) {
            canvas.drawLine(startX, 0f, startX, height.toFloat(), selectionBorderPaint)
        }
        if (endX >= 0 && endX <= width) {
            canvas.drawLine(endX, 0f, endX, height.toFloat(), selectionBorderPaint)
        }
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        val startX = progressToX(selectionStart)
        val endX = progressToX(selectionEnd)

        // Left handle
        if (startX >= 0 && startX <= width) {
            drawHandle(canvas, startX, true)
        }

        // Right handle
        if (endX >= 0 && endX <= width) {
            drawHandle(canvas, endX, false)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, isLeft: Boolean) {
        val handleHeight = 40f
        val handleWidth = HANDLE_WIDTH

        // Handle body (rounded rectangle at top)
        val left = if (isLeft) x - handleWidth else x
        val rect = RectF(left, 0f, left + handleWidth, handleHeight)
        canvas.drawRoundRect(rect, 8f, 8f, handlePaint)

        // Handle line going down
        canvas.drawLine(x, handleHeight, x, height.toFloat(), handleLinePaint)

        // Arrow indicator
        // Bug 2.33: Use local Paint copy to avoid mutating shared handleLinePaint
        val arrowPaint = Paint(handleLinePaint).apply {
            style = Paint.Style.FILL
        }
        val path = Path()
        if (isLeft) {
            path.moveTo(left + handleWidth - 6f, handleHeight / 2f - 6f)
            path.lineTo(left + 6f, handleHeight / 2f)
            path.lineTo(left + handleWidth - 6f, handleHeight / 2f + 6f)
        } else {
            path.moveTo(left + 6f, handleHeight / 2f - 6f)
            path.lineTo(left + handleWidth - 6f, handleHeight / 2f)
            path.lineTo(left + 6f, handleHeight / 2f + 6f)
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawPlayhead(canvas: Canvas) {
        val x = progressToX(playbackProgress)
        if (x < 0 || x > width) return

        // Playhead line
        canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)

        // Triangle at top
        val path = Path()
        path.moveTo(x - 8f, 0f)
        path.lineTo(x + 8f, 0f)
        path.lineTo(x, 12f)
        path.close()
        canvas.drawPath(path, playheadPaint)
    }

    private fun progressToX(progress: Float): Float {
        val visibleStart = scrollOffset
        val visibleEnd = min(1f, scrollOffset + 1f / zoomLevel)
        if (progress < visibleStart || progress > visibleEnd) return -1f

        val normalizedProgress = (progress - visibleStart) / (visibleEnd - visibleStart)
        return normalizedProgress * width
    }

    private fun xToProgress(x: Float): Float {
        val visibleStart = scrollOffset
        val visibleEnd = min(1f, scrollOffset + 1f / zoomLevel)
        val visibleRange = visibleEnd - visibleStart

        val normalizedX = x / width
        return (visibleStart + normalizedX * visibleRange).coerceIn(0f, 1f)
    }

    private fun hasSelection(): Boolean = selectionStart >= 0 && selectionEnd >= 0

    // Touch handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let scale detector handle pinch zoom
        scaleGestureDetector.onTouchEvent(event)

        // Always pass events to gesture detector for tap detection
        gestureDetector.onTouchEvent(event)

        try {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.x
                    dragStartProgress = xToProgress(event.x)

                    // Check if touching a handle
                    if (hasSelection()) {
                        val startX = progressToX(selectionStart)
                        val endX = progressToX(selectionEnd)

                        when {
                            abs(event.x - startX) < HANDLE_TOUCH_SLOP && event.y < 50f -> {
                                dragMode = DragMode.LEFT_HANDLE
                                return true
                            }
                            abs(event.x - endX) < HANDLE_TOUCH_SLOP && event.y < 50f -> {
                                dragMode = DragMode.RIGHT_HANDLE
                                return true
                            }
                        }
                    }

                    dragMode = DragMode.NONE
                }

                MotionEvent.ACTION_MOVE -> {
                    val currentProgress = xToProgress(event.x)
                    val dx = abs(event.x - dragStartX)

                    when (dragMode) {
                        DragMode.LEFT_HANDLE -> {
                            val newStart = currentProgress.coerceIn(0f, selectionEnd - 0.001f)
                            setSelection(newStart, selectionEnd)
                            return true
                        }
                        DragMode.RIGHT_HANDLE -> {
                            val newEnd = currentProgress.coerceIn(selectionStart + 0.001f, 1f)
                            setSelection(selectionStart, newEnd)
                            return true
                        }
                        DragMode.CREATE_SELECTION -> {
                            val start = min(dragStartProgress, currentProgress)
                            val end = max(dragStartProgress, currentProgress)
                            setSelection(start, end)
                            return true
                        }
                        DragMode.SCROLL -> {
                            if (zoomLevel > 1f) {
                                val delta = (dragStartX - event.x) / width / zoomLevel
                                scrollOffset = (scrollOffset + delta).coerceIn(0f, max(0f, 1f - 1f / zoomLevel))
                                dragStartX = event.x
                                invalidate()
                            }
                            return true
                        }
                        DragMode.NONE -> {
                            // Check if moved enough to start a drag action
                            if (dx > 25f) {
                                if (zoomLevel > 1f) {
                                    dragMode = DragMode.SCROLL
                                } else {
                                    dragMode = DragMode.CREATE_SELECTION
                                }
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Bug 2.14: Always reset drag state on UP/CANCEL
                    dragMode = DragMode.NONE
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        performClick()
                    }
                }
            }
        } catch (e: Exception) {
            // Bug 2.14: Reset drag state on any error to prevent stuck state
            dragMode = DragMode.NONE
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // Public API
    fun setWaveformData(data: WaveformData) {
        // Bug 2.15: Synchronized write for thread-safe access
        synchronized(dataLock) {
            this.waveformData = data
        }
        zoomLevel = 1f
        scrollOffset = 0f
        clearSelection()
        invalidate()
    }

    fun setPlaybackProgress(progress: Float) {
        this.playbackProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    fun setSelection(start: Float, end: Float) {
        selectionStart = start.coerceIn(0f, 1f)
        selectionEnd = end.coerceIn(0f, 1f)
        if (selectionStart > selectionEnd) {
            val temp = selectionStart
            selectionStart = selectionEnd
            selectionEnd = temp
        }
        onSelectionChangedListener?.invoke(selectionStart, selectionEnd)
        invalidate()
    }

    fun clearSelection() {
        selectionStart = -1f
        selectionEnd = -1f
        onSelectionChangedListener?.invoke(-1f, -1f)
        invalidate()
    }

    fun getSelection(): Pair<Float, Float>? {
        return if (hasSelection()) {
            Pair(selectionStart, selectionEnd)
        } else null
    }

    fun selectAll() {
        setSelection(0f, 1f)
    }

    fun setZoom(level: Float) {
        zoomLevel = level.coerceIn(1f, 50f)
        // Bug 2.16: Guard against division by zero when zoomLevel is extremely small
        val maxScroll = if (zoomLevel > 0.001f) max(0f, 1f - 1f / zoomLevel) else 0f
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        invalidate()
    }

    @Suppress("unused")
    fun getZoom(): Float = zoomLevel

    @Suppress("unused")
    fun setWaveformColor(color: Int) {
        waveformPaint.color = color
        invalidate()
    }

    @Suppress("unused")
    fun setColorMode(mode: ColorMode) {
        colorMode = mode
        invalidate()
    }

    @Suppress("unused")
    fun getColorMode(): ColorMode = colorMode

    @Suppress("unused")
    fun setFrequencyColors(colors: IntArray?) {
        frequencyColors = colors
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            setZoom(zoomLevel * scaleFactor)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Single tap: seek to position (and clear selection if any)
            if (hasSelection()) {
                clearSelection()
            }
            val progress = xToProgress(e.x)
            onSeekListener?.invoke(progress)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Double tap to select all
            selectAll()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Long press to start selection at this point
            val progress = xToProgress(e.x)
            setSelection(progress, (progress + 0.1f).coerceAtMost(1f))
        }
    }
}
