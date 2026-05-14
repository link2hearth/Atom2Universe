package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Real-time waveform visualization during recording.
 * Shows amplitude bars that scroll as new audio is recorded.
 */
class RecordingWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Bug fix: Use synchronized list to prevent ConcurrentModificationException
    // when addAmplitude() and onDraw() are called from different threads
    private val amplitudesLock = Any()
    private val amplitudes = mutableListOf<Float>()
    @Volatile
    private var currentAmplitude = 0f
    private var maxVisibleBars = 100

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336") // Red for recording
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#1E1E1E")
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        strokeWidth = 1f
    }

    private val currentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722") // Bright orange for current
        style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate how many bars fit on screen
        val barWidth = 6f
        val barSpacing = 2f
        maxVisibleBars = (w / (barWidth + barSpacing)).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Center line
        val centerY = height / 2f
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, centerLinePaint)

        val barWidth = 6f
        val barSpacing = 2f
        val totalBarWidth = barWidth + barSpacing

        // Bug fix: Synchronized access to amplitudes list to prevent ConcurrentModificationException
        val visibleAmplitudes: List<Float>
        synchronized(amplitudesLock) {
            if (amplitudes.isEmpty() && currentAmplitude == 0f) return

            // Draw historical amplitudes (scrolling from right to left)
            visibleAmplitudes = if (amplitudes.size > maxVisibleBars - 1) {
                amplitudes.takeLast(maxVisibleBars - 1).toList()
            } else {
                amplitudes.toList()
            }
        }

        val startX = width - (visibleAmplitudes.size + 1) * totalBarWidth

        visibleAmplitudes.forEachIndexed { index, amplitude ->
            val x = startX + index * totalBarWidth
            val barHeight = amplitude * centerY * 0.9f

            canvas.drawRect(
                x,
                centerY - barHeight,
                x + barWidth,
                centerY + barHeight,
                barPaint
            )
        }

        // Draw current amplitude bar (rightmost, animated)
        val currentX = width - totalBarWidth
        val currentBarHeight = currentAmplitude * centerY * 0.9f
        canvas.drawRect(
            currentX,
            centerY - currentBarHeight,
            currentX + barWidth,
            centerY + currentBarHeight,
            currentBarPaint
        )
    }

    /**
     * Update the current amplitude level (0.0 to 1.0).
     * Called frequently during recording for smooth animation.
     */
    fun setCurrentAmplitude(amplitude: Float) {
        currentAmplitude = amplitude.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Add a new amplitude sample to the history.
     * Called periodically (e.g., every 50ms) to build the waveform.
     */
    fun addAmplitude(amplitude: Float) {
        synchronized(amplitudesLock) {
            amplitudes.add(amplitude.coerceIn(0f, 1f))

            // Keep only visible bars plus some buffer
            if (amplitudes.size > maxVisibleBars * 2) {
                amplitudes.removeAt(0)
            }
        }

        invalidate()
    }

    /**
     * Set all amplitudes at once (for restoring state).
     */
    fun setAmplitudes(newAmplitudes: List<Float>) {
        synchronized(amplitudesLock) {
            amplitudes.clear()
            amplitudes.addAll(newAmplitudes.map { it.coerceIn(0f, 1f) })
        }
        invalidate()
    }

    /**
     * Clear all amplitude data.
     */
    fun clear() {
        synchronized(amplitudesLock) {
            amplitudes.clear()
        }
        currentAmplitude = 0f
        invalidate()
    }

    /**
     * Set the color of the waveform bars.
     */
    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    /**
     * Set the color of the current (rightmost) bar.
     */
    fun setCurrentBarColor(color: Int) {
        currentBarPaint.color = color
        invalidate()
    }
}
