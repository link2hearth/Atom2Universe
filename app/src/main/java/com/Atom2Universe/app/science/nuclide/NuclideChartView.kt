package com.Atom2Universe.app.science.nuclide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class NuclideChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onNuclideSelected: ((Nuclide?) -> Unit) = {}

    private val paintStable = Paint().apply { color = Color.parseColor("#000000"); style = Paint.Style.FILL }
    private val paintAlpha = Paint().apply { color = Color.parseColor("#FFCC00"); style = Paint.Style.FILL }
    private val paintBetaMinus = Paint().apply { color = Color.parseColor("#4FC3F7"); style = Paint.Style.FILL }
    private val paintBetaPlus = Paint().apply { color = Color.parseColor("#EF5350"); style = Paint.Style.FILL }
    private val paintFission = Paint().apply { color = Color.parseColor("#AB47BC"); style = Paint.Style.FILL }
    private val paintOther = Paint().apply { color = Color.parseColor("#78909C"); style = Paint.Style.FILL }
    private val paintSelected = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val paintText = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val paintAxisLabel = Paint().apply {
        color = Color.parseColor("#AAAAAA"); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val paintAxisLine = Paint().apply {
        color = Color.parseColor("#444444"); strokeWidth = 1f; style = Paint.Style.STROKE
    }
    private val paintGrid = Paint().apply {
        color = Color.parseColor("#222222"); strokeWidth = 0.5f; style = Paint.Style.STROKE
    }
    private val paintBg = Paint().apply { color = Color.parseColor("#121212") }
    private val cellRect = RectF()

    private val nuclideMap = mutableMapOf<Pair<Int,Int>, Nuclide>()
    private var maxZ = 118
    private var maxN = 180

    private var cellSize = 0f  // 0 = not yet initialized
    private val margin = 60f

    private var translateX = 0f
    private var translateY = 0f

    private var selectedNuclide: Nuclide? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newCell = (cellSize * factor).coerceIn(4f, 150f)
            val focusX = detector.focusX
            val focusY = detector.focusY
            translateX = focusX - (focusX - translateX) * (newCell / cellSize)
            translateY = focusY - (focusY - translateY) * (newCell / cellSize)
            cellSize = newCell
            constrainTranslation()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            translateX -= dx
            translateY -= dy
            constrainTranslation()
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val col = ((e.x - translateX - margin) / cellSize).toInt()
            val rowInv = ((e.y - translateY - margin) / cellSize).toInt()
            val Z = maxZ - rowInv
            val N = col
            val hit = nuclideMap[Z to N]
            selectedNuclide = if (hit == selectedNuclide) null else hit
            onNuclideSelected(selectedNuclide)
            invalidate()
            return true
        }
    })

    fun loadNuclides(nuclides: List<Nuclide>) {
        nuclideMap.clear()
        nuclides.forEach { nuclideMap[it.Z to it.N] = it }
        maxZ = nuclides.maxOfOrNull { it.Z } ?: 118
        maxN = nuclides.maxOfOrNull { it.N } ?: 180
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cellSize == 0f && w > 0 && h > 0) {
            val fitCell = minOf(
                (w - margin * 2) / (maxN + 2),
                (h - margin * 2) / (maxZ + 2)
            )
            cellSize = fitCell.coerceIn(4f, 150f)
            translateX = 0f
            translateY = 0f
        }
    }

    private fun constrainTranslation() {
        val totalW = (maxN + 2) * cellSize + margin * 2
        val totalH = (maxZ + 2) * cellSize + margin * 2
        translateX = translateX.coerceIn(-(totalW - width).coerceAtLeast(0f), 0f)
        translateY = translateY.coerceIn(-(totalH - height).coerceAtLeast(0f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        if (cellSize == 0f) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        val tx = translateX + margin
        val ty = translateY + margin

        paintAxisLabel.textSize = cellSize.coerceAtLeast(8f)
        paintText.textSize = (cellSize * 0.45f).coerceAtLeast(5f)

        // Axis labels every 10
        for (n in 0..maxN step 10) {
            val x = tx + n * cellSize + cellSize / 2
            if (x in 0f..width.toFloat()) {
                canvas.drawText("$n", x, margin - 4f, paintAxisLabel)
                canvas.drawLine(x, margin, x, ty + (maxZ + 1) * cellSize, paintGrid)
            }
        }
        for (z in 0..maxZ step 10) {
            val y = ty + (maxZ - z) * cellSize + cellSize / 2
            if (y in 0f..height.toFloat()) {
                canvas.drawText("$z", margin / 2, y + paintAxisLabel.textSize / 3, paintAxisLabel)
                canvas.drawLine(margin, y, tx + (maxN + 1) * cellSize, y, paintGrid)
            }
        }

        // Nuclides
        for ((key, nuclide) in nuclideMap) {
            val (Z, N) = key
            val left = tx + N * cellSize
            val top = ty + (maxZ - Z) * cellSize
            val right = left + cellSize - 1
            val bottom = top + cellSize - 1

            if (right < 0 || left > width || bottom < 0 || top > height) continue

            cellRect.set(left, top, right, bottom)
            val paint = when (nuclide.decayType) {
                DecayType.STABLE -> paintStable
                DecayType.ALPHA -> paintAlpha
                DecayType.BETA_MINUS -> paintBetaMinus
                DecayType.BETA_PLUS -> paintBetaPlus
                DecayType.FISSION -> paintFission
                DecayType.OTHER -> paintOther
            }
            canvas.drawRect(cellRect, paint)

            if (nuclide == selectedNuclide) {
                canvas.drawRect(cellRect, paintSelected)
            }

            if (cellSize >= 10f) {
                canvas.drawText(nuclide.symbol, left + cellSize / 2, top + cellSize * 0.65f, paintText)
            }
        }

        // Axis titles
        val savedCount = canvas.save()
        canvas.rotate(-90f, 16f, height / 2f)
        paintAxisLabel.textSize = 13f
        canvas.drawText("Z (protons)", 16f, height / 2f, paintAxisLabel)
        canvas.restoreToCount(savedCount)
        paintAxisLabel.textSize = 13f
        canvas.drawText("N (neutrons)", width / 2f, 16f, paintAxisLabel)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
