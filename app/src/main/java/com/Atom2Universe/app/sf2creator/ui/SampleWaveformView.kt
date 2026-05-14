package com.Atom2Universe.app.sf2creator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that displays audio waveform with trim and loop point markers.
 * Designed for the SF2 Creator sample editor.
 *
 * Features:
 * - Waveform visualization
 * - Trim start/end markers (draggable) - defines sample boundaries
 * - Loop start/end markers (draggable) - defines sustain loop region
 * - Playback position indicator
 * - Visual feedback for trimmed/loop regions
 * - Switchable edit modes (TRIM vs LOOP)
 */
class SampleWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MARKER_TOUCH_SLOP = 40f
    }

    /**
     * Edit mode determines which markers are draggable.
     */
    enum class EditMode {
        TRIM,  // Editing trim start/end
        LOOP   // Editing loop start/end
    }

    // Sample data
    private var samples: ShortArray? = null
    private var waveformPath = Path()
    private var waveformComputed = false

    // Trim points (as fraction 0.0 to 1.0)
    private var trimStartFraction: Float = 0f
    private var trimEndFraction: Float = 1f

    // Loop points (as fraction 0.0 to 1.0, relative to trimmed region)
    private var loopStartFraction: Float = 0f
    private var loopEndFraction: Float = 1f
    private var hasLoop: Boolean = false

    // Playback position (0.0 to 1.0)
    private var playbackPosition: Float = 0f

    // Edit mode
    private var editMode: EditMode = EditMode.TRIM

    // Dragging state
    private enum class DragTarget { NONE, TRIM_START, TRIM_END, LOOP_START, LOOP_END }
    private var dragTarget = DragTarget.NONE
    private var isDragging = false

    // Listeners
    var onLoopChangedListener: ((loopStart: Int, loopEnd: Int) -> Unit)? = null
    var onTrimChangedListener: ((trimStart: Int, trimEnd: Int) -> Unit)? = null

    // Paints
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = getThemeAccentColor()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val trimmedRegionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#000000".toColorInt()  // Black overlay for trimmed parts
        style = Paint.Style.FILL
        alpha = 180
    }

    private val loopRegionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = getDarkerAccentColor()
        style = Paint.Style.FILL
        alpha = 80
    }

    private val trimMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2196F3".toColorInt()  // Blue for trim markers
        style = Paint.Style.FILL
    }

    private val trimMarkerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2196F3".toColorInt()  // Blue
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val loopMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FFC107".toColorInt()  // Amber for loop markers
        style = Paint.Style.FILL
    }

    private val loopMarkerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FFC107".toColorInt()  // Amber
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#F44336".toColorInt()  // Red
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val backgroundPaint = Paint().apply {
        color = "#1E1E1E".toColorInt()
        style = Paint.Style.FILL
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#333333".toColorInt()
        strokeWidth = 1f
    }

    /**
     * Set the audio samples to display.
     */
    fun setSamples(samples: ShortArray) {
        this.samples = samples
        waveformComputed = false
        invalidate()
    }

    /**
     * Set trim points by sample index.
     */
    fun setTrimPoints(trimStart: Int, trimEnd: Int) {
        val sampleCount = samples?.size ?: 1
        this.trimStartFraction = trimStart.toFloat() / sampleCount
        this.trimEndFraction = trimEnd.toFloat() / sampleCount
        invalidate()
    }

    /**
     * Set loop points by sample index.
     */
    fun setLoopPoints(loopStart: Int, loopEnd: Int, hasLoop: Boolean) {
        val sampleCount = samples?.size ?: 1
        this.loopStartFraction = loopStart.toFloat() / sampleCount
        this.loopEndFraction = loopEnd.toFloat() / sampleCount
        this.hasLoop = hasLoop
        invalidate()
    }

    /**
     * Set playback position (0.0 to 1.0).
     */
    @Suppress("unused")
    fun setPlaybackPosition(position: Float) {
        playbackPosition = position.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Set the current edit mode (TRIM or LOOP).
     */
    fun setEditMode(mode: EditMode) {
        editMode = mode
        invalidate()
    }

    /**
     * Get current edit mode.
     */
    fun getEditMode(): EditMode = editMode

    /**
     * Get trim start as sample index.
     */
    fun getTrimStartSample(): Int {
        val sampleCount = samples?.size ?: 0
        return (trimStartFraction * sampleCount).toInt()
    }

    /**
     * Get trim end as sample index.
     */
    fun getTrimEndSample(): Int {
        val sampleCount = samples?.size ?: 0
        return (trimEndFraction * sampleCount).toInt()
    }

    /**
     * Get total sample count.
     */
    @Suppress("unused")
    fun getSampleCount(): Int = samples?.size ?: 0

    /**
     * Get loop start as sample index.
     */
    fun getLoopStartSample(): Int {
        val sampleCount = samples?.size ?: 0
        return (loopStartFraction * sampleCount).toInt()
    }

    /**
     * Get loop end as sample index.
     */
    fun getLoopEndSample(): Int {
        val sampleCount = samples?.size ?: 0
        return (loopEndFraction * sampleCount).toInt()
    }

    /**
     * Get the trimmed samples (samples between trim start and trim end).
     */
    fun getTrimmedSamples(): ShortArray? {
        val sampleArray = samples ?: return null
        val start = getTrimStartSample()
        val end = getTrimEndSample()
        if (start >= end || start < 0 || end > sampleArray.size) return null
        return sampleArray.copyOfRange(start, end)
    }

    /**
     * Get the duration in milliseconds of the trimmed region.
     */
    fun getTrimmedDurationMs(sampleRate: Int = 44100): Long {
        val sampleCount = getTrimEndSample() - getTrimStartSample()
        return (sampleCount * 1000L / sampleRate)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        waveformComputed = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw background
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        // Draw center line
        canvas.drawLine(0f, h / 2, w, h / 2, centerLinePaint)

        val sampleArray = samples
        if (sampleArray == null || sampleArray.isEmpty()) {
            // No data - draw placeholder
            textPaint.textSize = 32f
            canvas.drawText("No audio loaded", w / 2, h / 2, textPaint)
            return
        }

        // Compute waveform path if needed
        if (!waveformComputed) {
            computeWaveformPath(sampleArray, w.toInt(), h.toInt())
            waveformComputed = true
        }

        // Draw loop region background (if has loop and in loop mode or always)
        if (hasLoop) {
            val loopStartX = loopStartFraction * w
            val loopEndX = loopEndFraction * w
            canvas.drawRect(loopStartX, 0f, loopEndX, h, loopRegionPaint)
        }

        // Draw waveform
        canvas.drawPath(waveformPath, waveformPaint)

        // Draw trimmed regions (darkened overlay)
        val trimStartX = trimStartFraction * w
        val trimEndX = trimEndFraction * w
        canvas.drawRect(0f, 0f, trimStartX, h, trimmedRegionPaint)
        canvas.drawRect(trimEndX, 0f, w, h, trimmedRegionPaint)

        // Draw markers based on edit mode
        if (editMode == EditMode.TRIM) {
            // Draw trim markers (blue)
            drawTrimMarker(canvas, trimStartX, h, "◄", isStart = true)
            drawTrimMarker(canvas, trimEndX, h, "►", isStart = false)

            // Draw loop markers dimmed (if has loop)
            if (hasLoop) {
                loopMarkerLinePaint.alpha = 100
                loopMarkerPaint.alpha = 100
                drawLoopMarker(canvas, loopStartFraction * w, h, "S", isStart = true)
                drawLoopMarker(canvas, loopEndFraction * w, h, "E", isStart = false)
                loopMarkerLinePaint.alpha = 255
                loopMarkerPaint.alpha = 255
            }
        } else {
            // Draw loop markers (amber) if has loop
            if (hasLoop) {
                drawLoopMarker(canvas, loopStartFraction * w, h, "S", isStart = true)
                drawLoopMarker(canvas, loopEndFraction * w, h, "E", isStart = false)
            }

            // Draw trim markers dimmed (blue)
            trimMarkerLinePaint.alpha = 100
            trimMarkerPaint.alpha = 100
            drawTrimMarker(canvas, trimStartX, h, "◄", isStart = true)
            drawTrimMarker(canvas, trimEndX, h, "►", isStart = false)
            trimMarkerLinePaint.alpha = 255
            trimMarkerPaint.alpha = 255
        }

        // Draw playhead
        if (playbackPosition > 0f) {
            val playheadX = playbackPosition * w
            canvas.drawLine(playheadX, 0f, playheadX, h, playheadPaint)
        }
    }

    /**
     * Compute the waveform path from samples.
     */
    private fun computeWaveformPath(samples: ShortArray, width: Int, height: Int) {
        waveformPath.reset()

        if (samples.isEmpty() || width <= 0 || height <= 0) return

        val centerY = height / 2f
        val maxAmplitude = height / 2f - 4f  // Leave some padding

        waveformPath.moveTo(0f, centerY)

        for (x in 0 until width) {
            val sampleStart = (x * samples.size / width).coerceIn(0, samples.size - 1)
            val sampleEnd = ((x + 1) * samples.size / width).coerceIn(sampleStart, samples.size)

            // Find min and max in this range
            var minSample = Short.MAX_VALUE.toInt()
            var maxSample = Short.MIN_VALUE.toInt()

            for (i in sampleStart until sampleEnd) {
                val sample = samples[i].toInt()
                if (sample < minSample) minSample = sample
                if (sample > maxSample) maxSample = sample
            }

            // Convert to Y coordinates
            val minY = centerY - (maxSample.toFloat() / Short.MAX_VALUE) * maxAmplitude
            val maxY = centerY - (minSample.toFloat() / Short.MAX_VALUE) * maxAmplitude

            // Draw vertical line for this pixel
            waveformPath.moveTo(x.toFloat(), minY)
            waveformPath.lineTo(x.toFloat(), maxY)
        }
    }

    /**
     * Draw a trim marker with label.
     */
    private fun drawTrimMarker(canvas: Canvas, x: Float, height: Float, label: String, isStart: Boolean) {
        // Draw vertical line
        canvas.drawLine(x, 0f, x, height, trimMarkerLinePaint)

        // Draw handle at top
        val handleWidth = 28f
        val handleHeight = 36f
        val handleX = if (isStart) x else x - handleWidth

        canvas.drawRect(
            handleX,
            0f,
            handleX + handleWidth,
            handleHeight,
            trimMarkerPaint
        )

        // Draw label (arrow character)
        textPaint.textSize = 18f
        canvas.drawText(label, handleX + handleWidth / 2, handleHeight - 10, textPaint)

        // Draw handle at bottom
        canvas.drawRect(
            handleX,
            height - handleHeight,
            handleX + handleWidth,
            height,
            trimMarkerPaint
        )

        // Draw label at bottom
        canvas.drawText(label, handleX + handleWidth / 2, height - 12, textPaint)
    }

    /**
     * Draw a loop marker with label.
     */
    private fun drawLoopMarker(canvas: Canvas, x: Float, height: Float, label: String, isStart: Boolean) {
        // Draw vertical line
        canvas.drawLine(x, 0f, x, height, loopMarkerLinePaint)

        // Draw handle at top
        val handleWidth = 24f
        val handleHeight = 32f
        val handleX = if (isStart) x else x - handleWidth

        canvas.drawRect(
            handleX,
            0f,
            handleX + handleWidth,
            handleHeight,
            loopMarkerPaint
        )

        // Draw label
        textPaint.textSize = 20f
        canvas.drawText(label, handleX + handleWidth / 2, handleHeight - 8, textPaint)

        // Draw handle at bottom
        canvas.drawRect(
            handleX,
            height - handleHeight,
            handleX + handleWidth,
            height,
            loopMarkerPaint
        )
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val w = width.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touching a marker based on current edit mode
                val trimStartX = trimStartFraction * w
                val trimEndX = trimEndFraction * w
                val loopStartX = loopStartFraction * w
                val loopEndX = loopEndFraction * w

                dragTarget = if (editMode == EditMode.TRIM) {
                    when {
                        abs(x - trimStartX) < MARKER_TOUCH_SLOP -> DragTarget.TRIM_START
                        abs(x - trimEndX) < MARKER_TOUCH_SLOP -> DragTarget.TRIM_END
                        else -> DragTarget.NONE
                    }
                } else if (hasLoop) {
                    when {
                        abs(x - loopStartX) < MARKER_TOUCH_SLOP -> DragTarget.LOOP_START
                        abs(x - loopEndX) < MARKER_TOUCH_SLOP -> DragTarget.LOOP_END
                        else -> DragTarget.NONE
                    }
                } else {
                    DragTarget.NONE
                }

                isDragging = dragTarget != DragTarget.NONE
                return isDragging
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val newFraction = (x / w).coerceIn(0f, 1f)

                    when (dragTarget) {
                        DragTarget.TRIM_START -> {
                            // Ensure start is before end (with minimum gap)
                            trimStartFraction = min(newFraction, trimEndFraction - 0.02f).coerceAtLeast(0f)
                            // Ensure loop markers stay within trim region
                            loopStartFraction = loopStartFraction.coerceIn(trimStartFraction, trimEndFraction)
                            loopEndFraction = loopEndFraction.coerceIn(trimStartFraction, trimEndFraction)
                        }
                        DragTarget.TRIM_END -> {
                            // Ensure end is after start (with minimum gap)
                            trimEndFraction = max(newFraction, trimStartFraction + 0.02f).coerceAtMost(1f)
                            // Ensure loop markers stay within trim region
                            loopStartFraction = loopStartFraction.coerceIn(trimStartFraction, trimEndFraction)
                            loopEndFraction = loopEndFraction.coerceIn(trimStartFraction, trimEndFraction)
                        }
                        DragTarget.LOOP_START -> {
                            // Ensure start is before end and within trim region
                            loopStartFraction = min(newFraction, loopEndFraction - 0.01f)
                                .coerceIn(trimStartFraction, trimEndFraction)
                        }
                        DragTarget.LOOP_END -> {
                            // Ensure end is after start and within trim region
                            loopEndFraction = max(newFraction, loopStartFraction + 0.01f)
                                .coerceIn(trimStartFraction, trimEndFraction)
                        }
                        else -> {}
                    }
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false

                    // Notify appropriate listener
                    when (dragTarget) {
                        DragTarget.TRIM_START, DragTarget.TRIM_END -> {
                            onTrimChangedListener?.invoke(getTrimStartSample(), getTrimEndSample())
                        }
                        DragTarget.LOOP_START, DragTarget.LOOP_END -> {
                            onLoopChangedListener?.invoke(getLoopStartSample(), getLoopEndSample())
                        }
                        else -> {}
                    }

                    dragTarget = DragTarget.NONE
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Set the waveform color.
     */
    @Suppress("unused")
    fun setWaveformColor(color: Int) {
        waveformPaint.color = color
        invalidate()
    }

    /**
     * Set the trim marker color.
     */
    @Suppress("unused")
    fun setTrimMarkerColor(color: Int) {
        trimMarkerPaint.color = color
        trimMarkerLinePaint.color = color
        invalidate()
    }

    /**
     * Set the loop marker color.
     */
    @Suppress("unused")
    fun setLoopMarkerColor(color: Int) {
        loopMarkerPaint.color = color
        loopMarkerLinePaint.color = color
        invalidate()
    }

    /**
     * Reset all markers to default positions.
     */
    @Suppress("unused")
    fun resetMarkers() {
        trimStartFraction = 0f
        trimEndFraction = 1f
        loopStartFraction = 0f
        loopEndFraction = 1f
        invalidate()
    }

    /**
     * Get the theme accent color.
     */
    private fun getThemeAccentColor(): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)) {
            typedValue.data
        } else {
            "#4CAF50".toColorInt() // Fallback
        }
    }

    /**
     * Get a darker version of the accent color for loop region.
     */
    private fun getDarkerAccentColor(): Int {
        val accent = getThemeAccentColor()
        // Darken the color by reducing RGB values
        val r = ((accent shr 16) and 0xFF) * 0.6f
        val g = ((accent shr 8) and 0xFF) * 0.6f
        val b = (accent and 0xFF) * 0.6f
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }
}
