package com.Atom2Universe.app.audioeditor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Versatile audio visualization view supporting multiple render modes.
 * More efficient than computing full spectrograms.
 */
class AudioVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Visualization modes available
     */
    enum class VisualizationMode {
        FREQUENCY_BARS,      // Equalizer-style vertical bars
        COLORED_WAVEFORM,    // Waveform colored by frequency content
        MIRROR_SPECTRUM,     // Symmetric bars from center
        SMOOTH_CURVE         // Smooth frequency curve
    }

    // Current mode
    private var mode: VisualizationMode = VisualizationMode.FREQUENCY_BARS

    // Frequency data (averaged into bands)
    private var frequencyBands: FloatArray? = null
    private var waveformAmplitudes: FloatArray? = null
    private var frequencyColors: IntArray? = null  // Color per waveform sample

    // Playback position
    private var playbackProgress: Float = 0f

    // Number of frequency bands to display
    private val numBands = 32

    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
    }

    // Gradient colors for bars
    private val gradientColors = intArrayOf(
        Color.parseColor("#4CAF50"),  // Green (low freq)
        Color.parseColor("#FFEB3B"),  // Yellow (mid freq)
        Color.parseColor("#FF5722")   // Orange/Red (high freq)
    )

    // Path for smooth curve
    private val curvePath = Path()

    /**
     * Set visualization mode
     */
    fun setMode(newMode: VisualizationMode) {
        mode = newMode
        invalidate()
    }

    fun getMode(): VisualizationMode = mode

    /**
     * Set frequency bands data (0.0 to 1.0 per band)
     */
    fun setFrequencyBands(bands: FloatArray) {
        frequencyBands = bands
        invalidate()
    }

    /**
     * Set waveform data with frequency-based colors
     */
    fun setColoredWaveform(amplitudes: FloatArray, colors: IntArray) {
        waveformAmplitudes = amplitudes
        frequencyColors = colors
        invalidate()
    }

    /**
     * Set playback progress (0.0 to 1.0)
     */
    fun setProgress(progress: Float) {
        playbackProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Clear all data
     */
    fun clear() {
        frequencyBands = null
        waveformAmplitudes = null
        frequencyColors = null
        playbackProgress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        when (mode) {
            VisualizationMode.FREQUENCY_BARS -> drawFrequencyBars(canvas)
            VisualizationMode.COLORED_WAVEFORM -> drawColoredWaveform(canvas)
            VisualizationMode.MIRROR_SPECTRUM -> drawMirrorSpectrum(canvas)
            VisualizationMode.SMOOTH_CURVE -> drawSmoothCurve(canvas)
        }

        // Draw playhead
        if (playbackProgress > 0f) {
            val x = width * playbackProgress
            canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)
        }
    }

    private fun drawFrequencyBars(canvas: Canvas) {
        val bands = frequencyBands ?: return
        if (bands.isEmpty()) return

        val barCount = min(bands.size, numBands)
        val barWidth = width.toFloat() / barCount
        val gap = barWidth * 0.15f
        val actualBarWidth = barWidth - gap

        for (i in 0 until barCount) {
            val amplitude = bands[i].coerceIn(0f, 1f)
            val barHeight = height * amplitude * 0.9f

            val left = i * barWidth + gap / 2
            val top = height - barHeight
            val right = left + actualBarWidth
            val bottom = height.toFloat()

            // Color based on frequency band position
            val colorRatio = i.toFloat() / barCount
            barPaint.color = interpolateColor(colorRatio)

            canvas.drawRoundRect(left, top, right, bottom, 4f, 4f, barPaint)
        }
    }

    private fun drawColoredWaveform(canvas: Canvas) {
        val amplitudes = waveformAmplitudes ?: return
        val colors = frequencyColors ?: return
        if (amplitudes.isEmpty()) return

        val centerY = height / 2f
        val sampleWidth = width.toFloat() / amplitudes.size

        for (i in amplitudes.indices) {
            val amplitude = amplitudes[i].coerceIn(0f, 1f)
            val waveHeight = (height * 0.45f) * amplitude

            val x = i * sampleWidth
            barPaint.color = colors.getOrElse(i) { Color.GREEN }

            canvas.drawLine(x, centerY - waveHeight, x, centerY + waveHeight, barPaint)
        }
    }

    private fun drawMirrorSpectrum(canvas: Canvas) {
        val bands = frequencyBands ?: return
        if (bands.isEmpty()) return

        val barCount = min(bands.size, numBands)
        val barWidth = width.toFloat() / barCount
        val gap = barWidth * 0.1f
        val actualBarWidth = barWidth - gap
        val centerY = height / 2f

        for (i in 0 until barCount) {
            val amplitude = bands[i].coerceIn(0f, 1f)
            val barHeight = (height * 0.45f) * amplitude

            val left = i * barWidth + gap / 2
            val right = left + actualBarWidth

            // Color based on frequency band
            val colorRatio = i.toFloat() / barCount
            barPaint.color = interpolateColor(colorRatio)

            // Draw top bar (mirror up)
            canvas.drawRoundRect(left, centerY - barHeight, right, centerY - 2f, 3f, 3f, barPaint)
            // Draw bottom bar (mirror down)
            canvas.drawRoundRect(left, centerY + 2f, right, centerY + barHeight, 3f, 3f, barPaint)
        }
    }

    private fun drawSmoothCurve(canvas: Canvas) {
        val bands = frequencyBands ?: return
        if (bands.size < 2) return

        curvePath.reset()

        val barCount = min(bands.size, numBands)
        val stepX = width.toFloat() / (barCount - 1)

        // Create smooth curve using quadratic bezier
        curvePath.moveTo(0f, height - (bands[0] * height * 0.9f))

        for (i in 1 until barCount) {
            val x = i * stepX
            val y = height - (bands[i].coerceIn(0f, 1f) * height * 0.9f)

            val prevX = (i - 1) * stepX
            val prevY = height - (bands[i - 1].coerceIn(0f, 1f) * height * 0.9f)

            val midX = (prevX + x) / 2
            val midY = (prevY + y) / 2

            curvePath.quadTo(prevX, prevY, midX, midY)
        }

        // Final point
        val lastIdx = barCount - 1
        curvePath.lineTo(lastIdx * stepX, height - (bands[lastIdx].coerceIn(0f, 1f) * height * 0.9f))

        // Draw gradient fill under curve
        val shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.parseColor("#804CAF50"),
                Color.parseColor("#2000FF00")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        // Fill path
        val fillPath = Path(curvePath)
        fillPath.lineTo(width.toFloat(), height.toFloat())
        fillPath.lineTo(0f, height.toFloat())
        fillPath.close()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = shader
        }
        canvas.drawPath(fillPath, fillPaint)

        // Draw curve line
        curvePaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            gradientColors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(curvePath, curvePaint)
    }

    private fun interpolateColor(ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return when {
            r < 0.5f -> {
                // Green to Yellow
                val t = r * 2
                blendColors(gradientColors[0], gradientColors[1], t)
            }
            else -> {
                // Yellow to Red
                val t = (r - 0.5f) * 2
                blendColors(gradientColors[1], gradientColors[2], t)
            }
        }
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        return Color.rgb(
            (r1 + (r2 - r1) * r).toInt(),
            (g1 + (g2 - g1) * r).toInt(),
            (b1 + (b2 - b1) * r).toInt()
        )
    }
}
