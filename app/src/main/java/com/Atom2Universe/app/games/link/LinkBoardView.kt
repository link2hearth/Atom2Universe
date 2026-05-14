package com.Atom2Universe.app.games.link

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

class LinkBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onMoveApplied(affectedCoords: List<LinkGame.Coord>)
        fun onMoveFailed()
        fun onVictory()
    }

    var game: LinkGame? = null
    var listener: Listener? = null

    // Touch state
    private val selectionPath = mutableListOf<LinkGame.Coord>()
    private val invalidFlash = mutableSetOf<LinkGame.Coord>()
    private var flashAnimator: ValueAnimator? = null
    private var flashAlpha = 0f
    private var activePointerId = -1

    // Drawing tools
    private val cellPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.STROKE; it.strokeWidth = 2f }
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.textAlign = Paint.Align.CENTER; it.typeface = Typeface.DEFAULT_BOLD
    }
    private val plusPaint   = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.textAlign = Paint.Align.LEFT; it.typeface = Typeface.DEFAULT_BOLD
    }
    private val selPaint    = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(80, 255, 255, 255)
    }
    private val flashPaint  = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(120, 220, 50, 50)
    }
    private val linePaint   = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(180, 255, 220, 80); it.strokeCap = Paint.Cap.ROUND; it.style = Paint.Style.STROKE
    }
    private val pairDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cellRect = RectF()

    // Computed layout
    private var cellSize = 0f
    private var offsetX  = 0f
    private var offsetY  = 0f
    private var corner   = 0f
    private var padding  = 4f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }

    private fun recalcLayout() {
        val g = game ?: return
        val sz = g.size.coerceAtLeast(1)
        val gapTotal = padding * (sz + 1)
        val maxCellW = (width  - gapTotal) / sz
        val maxCellH = (height - gapTotal) / sz
        cellSize = min(maxCellW, maxCellH).coerceAtLeast(1f)
        corner = cellSize * 0.15f
        val totalW = cellSize * sz + gapTotal
        val totalH = cellSize * sz + gapTotal
        offsetX = (width  - totalW) / 2f
        offsetY = (height - totalH) / 2f
        linePaint.strokeWidth = cellSize * 0.12f
    }

    // --- Drawing ---

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        if (g.size == 0) return
        if (cellSize <= 0f) recalcLayout()

        val selSet = selectionPath.map { "${it.row}:${it.col}" }.toSet()
        val flashSet = invalidFlash.map { "${it.row}:${it.col}" }.toSet()

        for (row in g.board.indices) {
            for (col in g.board[row].indices) {
                drawCell(canvas, g, row, col, selSet, flashSet)
            }
        }
        drawSelectionLines(canvas)
    }

    private fun drawCell(
        canvas: Canvas, g: LinkGame,
        row: Int, col: Int,
        selSet: Set<String>, flashSet: Set<String>
    ) {
        val cell = g.board[row][col]
        val key = "$row:$col"
        cellRect.set(cellLeft(col), cellTop(row), cellLeft(col) + cellSize, cellTop(row) + cellSize)

        // Background
        cellPaint.color = cellColor(cell)
        canvas.drawRoundRect(cellRect, corner, corner, cellPaint)

        // Pair color dot — coin selon pairId % 4 : 0=haut-gauche, 1=haut-droit, 2=bas-droit, 3=bas-gauche
        if (cell.pairColor != null && cell.pairId != null) {
            pairDotPaint.color = cell.pairColor
            val dotR = cellSize * 0.13f
            val margin = dotR + cellSize * 0.06f
            val corner = cell.pairId % 4
            val cx = if (corner == 1 || corner == 2) cellRect.right  - margin else cellRect.left  + margin
            val cy = if (corner == 2 || corner == 3) cellRect.bottom - margin else cellRect.top   + margin
            canvas.drawCircle(cx, cy, dotR, pairDotPaint)
        }

        // '+' badge for plus cells
        if (cell.type == LinkGame.CellType.PLUS) {
            plusPaint.color = Color.argb(180, 255, 255, 255)
            plusPaint.textSize = cellSize * 0.22f
            canvas.drawText("+", cellRect.left + cellSize * 0.52f, cellRect.top + cellSize * 0.32f, plusPaint)
        }

        // Value text
        val targetValue = if (cell.type == LinkGame.CellType.PLUS) 10 else 0
        val isTarget = cell.value == targetValue
        textPaint.color = if (isTarget) Color.argb(220, 200, 255, 200) else Color.WHITE
        textPaint.textSize = cellSize * 0.40f
        canvas.drawText(cell.value.toString(), cellRect.centerX(), cellRect.centerY() + textPaint.textSize * 0.37f, textPaint)

        // Selection overlay
        if (key in selSet) {
            canvas.drawRoundRect(cellRect, corner, corner, selPaint)
            borderPaint.color = Color.argb(200, 255, 230, 80)
            borderPaint.strokeWidth = cellSize * 0.06f
            canvas.drawRoundRect(cellRect, corner, corner, borderPaint)
        }

        // Invalid flash overlay
        if (key in flashSet && flashAlpha > 0f) {
            flashPaint.alpha = (flashAlpha * 200).toInt()
            canvas.drawRoundRect(cellRect, corner, corner, flashPaint)
        }
    }

    private fun drawSelectionLines(canvas: Canvas) {
        if (selectionPath.size < 2) return
        for (i in 1 until selectionPath.size) {
            val a = selectionPath[i - 1]
            val b = selectionPath[i]
            val ax = cellLeft(a.col) + cellSize / 2f
            val ay = cellTop(a.row) + cellSize / 2f
            val bx = cellLeft(b.col) + cellSize / 2f
            val by = cellTop(b.row) + cellSize / 2f
            canvas.drawLine(ax, ay, bx, by, linePaint)
        }
    }

    // --- Cell color: hsl(210, 68%, lerp(82%..34%, value/10)) ---

    private fun cellColor(cell: LinkGame.Cell): Int {
        val ratio = cell.value / 10f
        val startLight = 82f; val endLight = 34f
        val light = (startLight - (startLight - endLight) * ratio) / 100f
        val sat = 0.68f; val hue = 210f
        return hslToColor(hue, sat, light)
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f  -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else     -> Triple(c, 0f, x)
        }
        return Color.rgb(((r1 + m) * 255).toInt(), ((g1 + m) * 255).toInt(), ((b1 + m) * 255).toInt())
    }

    // --- Layout helpers ---

    private fun cellLeft(col: Int) = offsetX + padding + col * (cellSize + padding)
    private fun cellTop(row: Int)  = offsetY + padding + row * (cellSize + padding)

    private fun cellAt(px: Float, py: Float): LinkGame.Coord? {
        val g = game ?: return null
        val col = floor((px - offsetX - padding) / (cellSize + padding)).toInt()
        val row = floor((py - offsetY - padding) / (cellSize + padding)).toInt()
        if (row !in g.board.indices || col !in g.board[row].indices) return null
        // Check inside actual cell rect (not gap)
        val left = cellLeft(col); val top = cellTop(row)
        if (px < left || px > left + cellSize || py < top || py > top + cellSize) return null
        return LinkGame.Coord(row, col)
    }

    // --- Touch handling ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (g.isVictory) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val coord = cellAt(event.x, event.y)
                if (coord != null) startSelection(coord)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val coord = cellAt(event.getX(idx), event.getY(idx)) ?: return true
                processMove(coord)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activePointerId) {
                    finalizeOrClear()
                    activePointerId = -1
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearSelection()
                activePointerId = -1
                return true
            }
        }
        return false
    }

    private fun startSelection(coord: LinkGame.Coord) {
        selectionPath.clear()
        selectionPath.add(coord)
        invalidate()
    }

    private fun processMove(coord: LinkGame.Coord) {
        val g = game ?: return
        val required = g.linkLength
        if (selectionPath.size >= 2 && selectionPath[selectionPath.size - 2] == coord) {
            // Backtrack
            selectionPath.removeAt(selectionPath.size - 1)
            invalidate()
            return
        }
        if (selectionPath.size >= required) return
        if (selectionPath.any { it == coord }) return
        val adjacent = selectionPath.any { abs(coord.row - it.row) + abs(coord.col - it.col) == 1 }
        if (!adjacent) return
        selectionPath.add(coord)
        invalidate()
    }

    private fun finalizeOrClear() {
        val g = game ?: run { clearSelection(); return }
        val required = g.linkLength
        if (selectionPath.size != required) { clearSelection(); return }
        val path = selectionPath.toList()
        if (!g.isPatternValid(path)) {
            triggerInvalidFlash(path)
            clearSelection()
            listener?.onMoveFailed()
            return
        }
        val affected = g.applyMove(path)
        clearSelection()
        if (affected != null) {
            listener?.onMoveApplied(affected)
            if (g.isVictory) listener?.onVictory()
            invalidate()
        }
    }

    private fun clearSelection() {
        selectionPath.clear()
        invalidate()
    }

    private fun triggerInvalidFlash(path: List<LinkGame.Coord>) {
        invalidFlash.clear()
        invalidFlash.addAll(path)
        flashAnimator?.cancel()
        flashAlpha = 1f
        flashAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 350
            addUpdateListener {
                flashAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun notifyBoardChanged() {
        recalcLayout()
        invalidate()
    }
}
