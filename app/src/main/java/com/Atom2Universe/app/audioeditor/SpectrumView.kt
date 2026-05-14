package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View for displaying audio spectrogram.
 * Shows frequency (Y-axis) vs time (X-axis) with color representing intensity.
 * Supports zoom (pinch), scroll (pan), and selection with draggable handles.
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val HANDLE_WIDTH = 24f
        private const val HANDLE_TOUCH_SLOP = 40f
    }

    // Spectrogram data - protected by dataLock for thread-safe access
    private val dataLock = Any()
    // Bug 2.17: Added bitmapLock for atomic bitmap check-then-use operations
    private val bitmapLock = Any()
    @Volatile
    private var spectrogramData: Array<FloatArray>? = null
    @Volatile
    private var spectrogramBitmap: Bitmap? = null

    // Playback position indicator
    private var playbackProgress: Float = 0f

    // Color palette
    private var colorPalette: ColorPalette = ColorPalette.HEAT

    // Selection (in progress units 0.0 to 1.0)
    private var selectionStart: Float = -1f
    private var selectionEnd: Float = -1f

    // Selection dragging state
    private enum class DragMode { NONE, LEFT_HANDLE, RIGHT_HANDLE, CREATE_SELECTION, SCROLL }
    private var dragMode = DragMode.NONE
    private var dragStartX = 0f
    private var dragStartProgress = 0f

    // Zoom and scroll
    private var zoomLevel: Float = 1f
    private var scrollOffset: Float = 0f

    // Paints
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF5722".toColorInt()
        strokeWidth = 3f
        style = Paint.Style.FILL_AND_STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#888888".toColorInt()
        textSize = 24f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#333333".toColorInt()
        strokeWidth = 1f
        style = Paint.Style.STROKE
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

    // Processing - use SupervisorJob for proper lifecycle management
    private val fftProcessor = FFTProcessor()
    private val viewScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

    // Labels
    private var showLabels = true
    private var sampleRate = 44100
    private var fftSize = FFTProcessor.FFT_SIZE_1024

    // Source rectangle for bitmap drawing
    private val srcRect = Rect()
    private val dstRect = Rect()

    // Gesture detectors
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // Callbacks
    var onSeekListener: ((Float) -> Unit)? = null
    var onSelectionChangedListener: ((Float, Float) -> Unit)? = null

    // Interactive mode (set to false for read-only display)
    private var isInteractive: Boolean = true

    /**
     * Set spectrogram data directly.
     */
    @Suppress("unused")
    fun setSpectrogramData(data: Array<FloatArray>) {
        setSpectrogramData(data, sampleRate, fftSize)
    }

    /**
     * Set spectrogram data with metadata.
     */
    @Suppress("unused")
    fun setSpectrogramData(
        data: Array<FloatArray>,
        sampleRate: Int,
        fftSize: Int = FFTProcessor.FFT_SIZE_1024
    ) {
        this.sampleRate = sampleRate
        this.fftSize = fftSize
        synchronized(dataLock) {
            spectrogramData = data
        }
        zoomLevel = 1f
        scrollOffset = 0f
        clearSelection()
        generateBitmap()
        invalidate()
    }

    /**
     * Generate spectrogram from raw audio samples.
     */
    fun generateFromSamples(
        samples: FloatArray,
        sampleRate: Int = 44100,
        fftSize: Int = FFTProcessor.FFT_SIZE_1024
    ) {
        this.sampleRate = sampleRate
        this.fftSize = fftSize

        processingJob?.cancel()
        processingJob = viewScope.launch {
            val spectrogram = fftProcessor.computeSpectrogram(
                samples = samples,
                fftSize = fftSize,
                hopSize = fftSize / 4
            )

            withContext(Dispatchers.Main) {
                spectrogramData = spectrogram
                zoomLevel = 1f
                scrollOffset = 0f
                clearSelection()
                generateBitmap()
                invalidate()
            }
        }
    }

    /**
     * Set playback progress (0.0 to 1.0).
     */
    fun setProgress(progress: Float) {
        playbackProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Alias for setProgress, for API consistency with WaveformView.
     */
    fun setPlaybackProgress(progress: Float) {
        setProgress(progress)
    }

    /**
     * Set color palette for spectrogram display.
     */
    @Suppress("unused")
    fun setColorPalette(palette: ColorPalette) {
        colorPalette = palette
        generateBitmap()
        invalidate()
    }

    /**
     * Toggle frequency labels display.
     */
    @Suppress("unused")
    fun setShowLabels(show: Boolean) {
        showLabels = show
        invalidate()
    }

    /**
     * Enable or disable interactive mode.
     * When disabled, the view is read-only (no selection, no handles, no gestures).
     */
    fun setInteractive(enabled: Boolean) {
        isInteractive = enabled
        if (!enabled) {
            clearSelection()
        }
        invalidate()
    }

    /**
     * Clear the spectrogram.
     */
    fun clear() {
        synchronized(dataLock) {
            spectrogramData = null
        }
        // Bug 2.17: Synchronized bitmap clear
        synchronized(bitmapLock) {
            spectrogramBitmap?.recycle()
            spectrogramBitmap = null
        }
        playbackProgress = 0f
        zoomLevel = 1f
        scrollOffset = 0f
        clearSelection()
        invalidate()
    }

    // ==================== Selection API ====================

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

    private fun hasSelection(): Boolean = selectionStart >= 0 && selectionEnd >= 0

    // ==================== Zoom API ====================

    fun setZoom(level: Float) {
        zoomLevel = level.coerceIn(1f, 50f)
        scrollOffset = scrollOffset.coerceIn(0f, max(0f, 1f - 1f / zoomLevel))
        invalidate()
    }

    @Suppress("unused")
    fun getZoom(): Float = zoomLevel

    // ==================== Coordinate conversion ====================

    private fun getLabelWidth(): Int = if (showLabels) 50 else 0

    private fun progressToX(progress: Float): Float {
        val labelWidth = getLabelWidth()
        val drawWidth = width - labelWidth
        val visibleStart = scrollOffset
        val visibleEnd = min(1f, scrollOffset + 1f / zoomLevel)
        if (progress < visibleStart || progress > visibleEnd) return -1f

        val normalizedProgress = (progress - visibleStart) / (visibleEnd - visibleStart)
        return labelWidth + normalizedProgress * drawWidth
    }

    private fun xToProgress(x: Float): Float {
        val labelWidth = getLabelWidth()
        val drawWidth = width - labelWidth
        val visibleStart = scrollOffset
        val visibleEnd = min(1f, scrollOffset + 1f / zoomLevel)
        val visibleRange = visibleEnd - visibleStart

        val normalizedX = (x - labelWidth) / drawWidth
        return (visibleStart + normalizedX * visibleRange).coerceIn(0f, 1f)
    }

    // ==================== Touch handling ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Ignore touch events if not interactive
        if (!isInteractive) return false

        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartProgress = xToProgress(event.x)

                // Check if touching a handle
                if (hasSelection()) {
                    val startX = progressToX(selectionStart)
                    val endX = progressToX(selectionEnd)

                    when {
                        startX >= 0 && abs(event.x - startX) < HANDLE_TOUCH_SLOP && event.y < 50f -> {
                            dragMode = DragMode.LEFT_HANDLE
                            return true
                        }
                        endX >= 0 && abs(event.x - endX) < HANDLE_TOUCH_SLOP && event.y < 50f -> {
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
                            val drawWidth = width - getLabelWidth()
                            val delta = (dragStartX - event.x) / drawWidth / zoomLevel
                            scrollOffset = (scrollOffset + delta).coerceIn(0f, max(0f, 1f - 1f / zoomLevel))
                            dragStartX = event.x
                            invalidate()
                        }
                        return true
                    }
                    DragMode.NONE -> {
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

            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ==================== Drawing ====================

    private fun generateBitmap() {
        // Synchronized access to spectrogramData for thread safety
        val data: Array<FloatArray>
        val timeFrames: Int
        val freqBins: Int
        synchronized(dataLock) {
            val localData = spectrogramData ?: return
            if (localData.isEmpty() || localData[0].isEmpty()) return
            // Make a shallow copy of references for safe iteration outside the lock
            data = localData
            timeFrames = data.size
            freqBins = data[0].size
        }

        // Cap bitmap dimensions to avoid OOM - reduced for better memory safety
        val maxBitmapWidth = 2048
        val maxBitmapHeight = 256

        val bitmapWidth = timeFrames.coerceAtMost(maxBitmapWidth)
        val bitmapHeight = freqBins.coerceAtMost(maxBitmapHeight)

        // Compute downsampling ratios
        val timeStep = timeFrames.toFloat() / bitmapWidth
        val freqStep = freqBins.toFloat() / bitmapHeight

        // Recycle old bitmap safely with synchronization
        val oldBitmap: Bitmap?
        synchronized(bitmapLock) {
            oldBitmap = spectrogramBitmap
            spectrogramBitmap = null
        }
        oldBitmap?.recycle()

        // Create new bitmap with capped dimensions - wrapped in try-catch for OOM
        val bitmap: Bitmap
        try {
            bitmap = createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("SpectrumView", "OOM while creating spectrogram bitmap", e)
            return
        }

        // Fill pixels with downsampling (take max intensity in each bin)
        // Note: We iterate over the captured 'data' reference which won't change during iteration
        val colors = colorPalette.colors
        for (bx in 0 until bitmapWidth) {
            val tStart = (bx * timeStep).toInt()
            val tEnd = ((bx + 1) * timeStep).toInt().coerceAtMost(timeFrames)
            for (by in 0 until bitmapHeight) {
                val fStart = (by * freqStep).toInt()
                val fEnd = ((by + 1) * freqStep).toInt().coerceAtMost(freqBins)

                // Find max intensity in this region
                var maxIntensity = 0f
                for (t in tStart until tEnd) {
                    for (f in fStart until fEnd) {
                        val v = data[t][f]
                        if (v > maxIntensity) maxIntensity = v
                    }
                }
                maxIntensity = maxIntensity.coerceIn(0f, 1f)

                val colorIndex = (maxIntensity * (colors.size - 1)).toInt()
                // Flip Y axis (low frequencies at bottom)
                bitmap[bx, bitmapHeight - 1 - by] = colors[colorIndex]
            }
        }

        // Bug 2.17: Synchronized write for thread safety
        synchronized(bitmapLock) {
            spectrogramBitmap = bitmap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Bug 2.17: Synchronized access for atomic bitmap check-then-use
        val bitmap: Bitmap?
        val bitmapWidth: Int
        val bitmapHeight: Int
        synchronized(bitmapLock) {
            bitmap = spectrogramBitmap
            if (bitmap == null || bitmap.isRecycled) {
                // Draw empty state
                drawEmptyState(canvas)
                return
            }
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
        }

        // Calculate drawing area (leave space for labels if enabled)
        val labelWidth = getLabelWidth()
        val drawLeft = labelWidth
        val drawTop = paddingTop
        val drawRight = width
        val drawBottom = height - paddingBottom

        // Calculate visible portion of bitmap based on zoom/scroll
        val visibleStartFraction = scrollOffset
        val visibleEndFraction = min(1f, scrollOffset + 1f / zoomLevel)
        val visibleStartPx = (visibleStartFraction * bitmapWidth).toInt().coerceIn(0, bitmapWidth)
        val visibleEndPx = (visibleEndFraction * bitmapWidth).toInt().coerceIn(0, bitmapWidth)

        // Draw spectrogram bitmap (zoomed portion)
        srcRect.set(visibleStartPx, 0, visibleEndPx, bitmapHeight)
        dstRect.set(drawLeft, drawTop, drawRight, drawBottom)

        // Bug 2.17: Final check before drawing
        synchronized(bitmapLock) {
            val currentBitmap = spectrogramBitmap
            if (currentBitmap != null && !currentBitmap.isRecycled) {
                canvas.drawBitmap(currentBitmap, srcRect, dstRect, bitmapPaint)
            }
        }

        // Draw frequency labels
        if (showLabels) {
            drawFrequencyLabels(canvas, drawLeft, drawTop, drawBottom)
        }

        // Draw grid lines
        drawGrid(canvas, drawLeft, drawTop, drawRight, drawBottom)

        // Draw selection (only in interactive mode)
        if (isInteractive && hasSelection()) {
            drawSelection(canvas)
            drawSelectionHandles(canvas)
        }

        // Draw playhead (only in interactive mode)
        if (isInteractive) {
            drawPlayhead(canvas)
        }
    }

    private fun drawEmptyState(canvas: Canvas) {
        canvas.drawColor("#1A1A1A".toColorInt())
        val text = "No spectrum data"
        val textWidth = labelPaint.measureText(text)
        canvas.drawText(
            text,
            (width - textWidth) / 2,
            height / 2f,
            labelPaint
        )
    }

    private fun drawFrequencyLabels(canvas: Canvas, left: Int, top: Int, bottom: Int) {
        val maxFreq = sampleRate / 2f // Nyquist frequency

        // Draw labels at key frequencies
        val freqLabels = listOf(100, 500, 1000, 2000, 5000, 10000, 20000)
            .filter { it <= maxFreq }

        for (freq in freqLabels) {
            val normalizedFreq = freq / maxFreq
            val y = bottom - (bottom - top) * normalizedFreq

            // Format label
            val label = if (freq >= 1000) "${freq / 1000}k" else "$freq"

            canvas.drawText(label, 4f, y + labelPaint.textSize / 3, labelPaint)
            canvas.drawLine(left.toFloat(), y, left + 8f, y, gridPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        // Horizontal grid lines at octave intervals
        val maxFreq = sampleRate / 2f
        val octaves = listOf(125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
            .filter { it <= maxFreq }

        for (freq in octaves) {
            val normalizedFreq = freq / maxFreq
            val y = bottom - (bottom - top) * normalizedFreq
            canvas.drawLine(left.toFloat(), y, right.toFloat(), y, gridPaint)
        }
    }

    private fun drawSelection(canvas: Canvas) {
        val startX = progressToX(selectionStart)
        val endX = progressToX(selectionEnd)

        if (startX < 0 && endX < 0) return

        val labelWidth = getLabelWidth()
        val left = max(labelWidth.toFloat(), startX)
        val right = min(width.toFloat(), endX)

        // Selection background
        canvas.drawRect(left, paddingTop.toFloat(), right, (height - paddingBottom).toFloat(), selectionPaint)

        // Selection borders
        if (startX >= labelWidth) {
            canvas.drawLine(startX, paddingTop.toFloat(), startX, (height - paddingBottom).toFloat(), selectionBorderPaint)
        }
        if (endX >= 0 && endX <= width) {
            canvas.drawLine(endX, paddingTop.toFloat(), endX, (height - paddingBottom).toFloat(), selectionBorderPaint)
        }
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        val startX = progressToX(selectionStart)
        val endX = progressToX(selectionEnd)

        val labelWidth = getLabelWidth()

        // Left handle
        if (startX >= labelWidth && startX <= width) {
            drawHandle(canvas, startX, true)
        }

        // Right handle
        if (endX >= labelWidth && endX <= width) {
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
        canvas.drawLine(x, handleHeight + paddingTop, x, (height - paddingBottom).toFloat(), handleLinePaint)

        // Arrow indicator
        // Bug fix: Use local Paint copy to avoid mutating shared handleLinePaint
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
        canvas.drawLine(x, paddingTop.toFloat(), x, (height - paddingBottom).toFloat(), playheadPaint)

        // Triangle at top
        val path = Path()
        path.moveTo(x - 8f, paddingTop.toFloat())
        path.lineTo(x + 8f, paddingTop.toFloat())
        path.lineTo(x, paddingTop + 12f)
        path.close()
        canvas.drawPath(path, playheadPaint)
    }

    // ==================== Gesture listeners ====================

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            setZoom(zoomLevel * scaleFactor)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (hasSelection()) {
                clearSelection()
            }
            val progress = xToProgress(e.x)
            onSeekListener?.invoke(progress)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            selectAll()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val progress = xToProgress(e.x)
            setSelection(progress, (progress + 0.1f).coerceAtMost(1f))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel processing job first to ensure it's stopped
        processingJob?.cancel()
        processingJob = null
        // Cancel all coroutines in the view scope
        viewScope.cancel()
        // Bug 2.17: Safely recycle bitmap with synchronization
        synchronized(bitmapLock) {
            val bitmap = spectrogramBitmap
            spectrogramBitmap = null
            bitmap?.recycle()
        }
    }

    /**
     * Color palettes for spectrogram display.
     */
    @Suppress("unused")
    enum class ColorPalette(private val generator: () -> IntArray) {
        // Heat map: black -> red -> yellow -> white
        HEAT({
            val size = 256
            IntArray(size) { i ->
                val t = i / (size - 1f)
                when {
                    t < 0.33f -> {
                        val r = (t / 0.33f * 255).toInt()
                        Color.rgb(r, 0, 0)
                    }
                    t < 0.66f -> {
                        val g = ((t - 0.33f) / 0.33f * 255).toInt()
                        Color.rgb(255, g, 0)
                    }
                    else -> {
                        val b = ((t - 0.66f) / 0.34f * 255).toInt()
                        Color.rgb(255, 255, b)
                    }
                }
            }
        }),

        // Cool: black -> blue -> cyan -> white
        COOL({
            val size = 256
            IntArray(size) { i ->
                val t = i / (size - 1f)
                when {
                    t < 0.5f -> {
                        val b = (t / 0.5f * 255).toInt()
                        Color.rgb(0, 0, b)
                    }
                    else -> {
                        val g = ((t - 0.5f) / 0.5f * 255).toInt()
                        Color.rgb(0, g, 255)
                    }
                }
            }
        }),

        // Grayscale: black -> white
        GRAYSCALE({
            val size = 256
            IntArray(size) { i ->
                Color.rgb(i, i, i)
            }
        }),

        // Rainbow: full spectrum
        RAINBOW({
            val size = 256
            IntArray(size) { i ->
                val hue = (i / (size - 1f)) * 270f // 0 to 270 (red to violet)
                Color.HSVToColor(floatArrayOf(hue, 1f, if (i < 20) i / 20f else 1f))
            }
        });

        // Lazy initialize colors to avoid regenerating each time
        val colors: IntArray by lazy { generator() }
    }
}
