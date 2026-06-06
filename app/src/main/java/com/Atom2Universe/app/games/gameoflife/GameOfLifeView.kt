package com.Atom2Universe.app.games.gameoflife

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class GameOfLifeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val BASE_CELL_SIZE = 20f
        private const val MIN_CELL_SIZE = 4f
        private const val MAX_CELL_SIZE = 60f
    }

    private var cols = 0
    private var rows = 0
    private var grid = Array(0) { BooleanArray(0) }
    private var buffer = Array(0) { BooleanArray(0) }

    private var cellSize = BASE_CELL_SIZE
    private var offsetX = 0f
    private var offsetY = 0f

    // Dessin d'un seul doigt
    private var isDrawing = false
    private var hadMultiTouch = false
    private var drawValue = true
    private var lastTouchCol = -1
    private var lastTouchRow = -1
    // Mémorise l'état original de chaque cellule touchée pour annulation si 2ème doigt
    private val strokeChanges = mutableListOf<Triple<Int, Int, Boolean>>() // row, col, wasAlive

    // Pan à 2 doigts
    private var lastPanX = 0f
    private var lastPanY = 0f

    var onCellCountChanged: ((alive: Int) -> Unit)? = null

    private val cellPaint = Paint().apply {
        color = Color.parseColor("#7B8CDE")
        isAntiAlias = false
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.parseColor("#252540")
        strokeWidth = 0.5f
        isAntiAlias = false
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            val worldX = (focusX - offsetX) / cellSize
            val worldY = (focusY - offsetY) / cellSize
            cellSize = (cellSize * detector.scaleFactor).coerceIn(MIN_CELL_SIZE, MAX_CELL_SIZE)
            offsetX = focusX - worldX * cellSize
            offsetY = focusY - worldY * cellSize
            invalidate()
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cols == 0) initGrid(w, h)
    }

    private fun initGrid(w: Int, h: Int) {
        cols = max(80, (w / BASE_CELL_SIZE).toInt() + 20)
        rows = max(80, (h / BASE_CELL_SIZE).toInt() + 20)
        grid = Array(rows) { BooleanArray(cols) }
        buffer = Array(rows) { BooleanArray(cols) }
        centerView(w, h)
    }

    private fun centerView(w: Int, h: Int) {
        offsetX = (w - cols * cellSize) / 2f
        offsetY = (h - rows * cellSize) / 2f
    }

    fun randomize(density: Float = 0.3f) {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = Math.random() < density
        notifyCount()
        invalidate()
    }

    fun clear() {
        for (r in 0 until rows) grid[r].fill(false)
        notifyCount()
        invalidate()
    }

    fun step() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val n = countNeighbors(r, c)
                buffer[r][c] = if (grid[r][c]) n in 2..3 else n == 3
            }
        }
        val tmp = grid; grid = buffer; buffer = tmp
        notifyCount()
        invalidate()
    }

    private fun countNeighbors(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            if (grid[(r + dr + rows) % rows][(c + dc + cols) % cols]) count++
        }
        return count
    }

    fun countAlive(): Int {
        var count = 0
        for (r in 0 until rows) for (c in 0 until cols) if (grid[r][c]) count++
        return count
    }

    private fun notifyCount() {
        post { onCellCountChanged?.invoke(countAlive()) }
    }

    fun placePattern(pattern: Array<IntArray>) {
        val startR = rows / 2 - pattern.size / 2
        val startC = cols / 2 - (pattern.maxOfOrNull { it.size } ?: 0) / 2
        for ((r, row) in pattern.withIndex()) for ((c, cell) in row.withIndex()) {
            grid[(startR + r).coerceIn(0, rows - 1)][(startC + c).coerceIn(0, cols - 1)] = cell == 1
        }
        notifyCount()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gridPaint)

        val startC = max(0, ((-offsetX) / cellSize).toInt() - 1)
        val endC = min(cols - 1, ((width - offsetX) / cellSize).toInt() + 1)
        val startR = max(0, ((-offsetY) / cellSize).toInt() - 1)
        val endR = min(rows - 1, ((height - offsetY) / cellSize).toInt() + 1)

        if (cellSize > 8f) {
            for (c in startC..endC) canvas.drawLine(offsetX + c * cellSize, 0f, offsetX + c * cellSize, height.toFloat(), linePaint)
            for (r in startR..endR) canvas.drawLine(0f, offsetY + r * cellSize, width.toFloat(), offsetY + r * cellSize, linePaint)
        }

        for (r in startR..endR) for (c in startC..endC) {
            if (grid[r][c]) {
                val left = offsetX + c * cellSize + 1f
                val top = offsetY + r * cellSize + 1f
                canvas.drawRect(left, top, left + cellSize - 2f, top + cellSize - 2f, cellPaint)
            }
        }
    }

    private fun drawCell(x: Float, y: Float) {
        val col = ((x - offsetX) / cellSize).toInt()
        val row = ((y - offsetY) / cellSize).toInt()
        if (col in 0 until cols && row in 0 until rows && (col != lastTouchCol || row != lastTouchRow)) {
            strokeChanges.add(Triple(row, col, grid[row][col]))
            grid[row][col] = drawValue
            lastTouchCol = col
            lastTouchRow = row
            notifyCount()
            invalidate()
        }
    }

    private fun revertStroke() {
        for ((r, c, was) in strokeChanges) grid[r][c] = was
        strokeChanges.clear()
        notifyCount()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hadMultiTouch = false
                strokeChanges.clear()
                isDrawing = true
                val col = ((event.x - offsetX) / cellSize).toInt()
                val row = ((event.y - offsetY) / cellSize).toInt()
                if (col in 0 until cols && row in 0 until rows) {
                    drawValue = !grid[row][col]
                    strokeChanges.add(Triple(row, col, grid[row][col]))
                    grid[row][col] = drawValue
                    lastTouchCol = col
                    lastTouchRow = row
                    notifyCount()
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 2ème doigt : annule le stroke en cours et passe en mode pan/zoom
                if (isDrawing) revertStroke()
                isDrawing = false
                hadMultiTouch = true
                lastTouchCol = -1
                lastTouchRow = -1
                if (event.pointerCount >= 2) {
                    lastPanX = (event.getX(0) + event.getX(1)) / 2f
                    lastPanY = (event.getY(0) + event.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    if (!scaleDetector.isInProgress) {
                        offsetX += midX - lastPanX
                        offsetY += midY - lastPanY
                        invalidate()
                    }
                    lastPanX = midX
                    lastPanY = midY
                } else if (isDrawing && !hadMultiTouch) {
                    drawCell(event.x, event.y)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Retour à 1 doigt : mémoriser sa position pour éviter un saut de pan
                if (event.pointerCount == 2) {
                    val remainingIdx = if (event.actionIndex == 0) 1 else 0
                    lastPanX = event.getX(remainingIdx)
                    lastPanY = event.getY(remainingIdx)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                hadMultiTouch = false
                strokeChanges.clear()
                lastTouchCol = -1
                lastTouchRow = -1
            }
        }
        return true
    }
}
