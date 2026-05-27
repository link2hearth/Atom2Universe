package com.Atom2Universe.app.games.theline

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TheLineBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: TheLineGame? = null
    var onPathCompleted: ((pathId: Int) -> Unit)? = null
    var onBoardChanged: (() -> Unit)? = null

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    private val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E293B")
    }
    private val paintCellBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#334155")
        strokeWidth = 2f
    }
    private val paintBlocked = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F172A")
    }
    private val paintPath = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintEndpoint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Touch state
    private var activePathId: Int? = null
    private var lastTouchedCoord: TLCoord? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcLayout()
    }

    private fun recalcLayout() {
        val g = game ?: return
        val p = g.puzzle ?: return
        val cols = p.width; val rows = p.height
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        cellSize = minOf(cellW, cellH)
        offsetX = (width - cellSize * cols) / 2f
        offsetY = (height - cellSize * rows) / 2f
        paintText.textSize = cellSize * 0.38f
        paintLine.strokeWidth = cellSize * 0.38f
    }

    fun loadGame(g: TheLineGame) {
        game = g
        activePathId = null
        lastTouchedCoord = null
        recalcLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val p = g.puzzle ?: return

        val occupied = g.getOccupiedColors()
        val endpointCoords = buildEndpointMap(p)

        for (y in 0 until p.height) {
            for (x in 0 until p.width) {
                val coord = TLCoord(x, y)
                val cx = offsetX + x * cellSize
                val cy = offsetY + y * cellSize
                val r = cellSize * 0.06f

                if (g.isBlocked(x, y)) {
                    canvas.drawRoundRect(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2, r, r, paintBlocked)
                } else {
                    canvas.drawRoundRect(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2, r, r, paintCell)
                    canvas.drawRoundRect(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2, r, r, paintCellBorder)

                    val color = occupied[coord]
                    if (color != null) {
                        paintPath.color = (color and 0x00FFFFFF) or (180 shl 24)
                        canvas.drawRoundRect(cx + 2, cy + 2, cx + cellSize - 2, cy + cellSize - 2, r, r, paintPath)
                    }
                }
            }
        }

        // Draw path lines
        for ((_, path) in g.paths) {
            if (path.sequence.size < 2) continue
            paintLine.color = path.colorValue
            val linePath = Path()
            val first = path.sequence[0]
            linePath.moveTo(cellCenterX(first.x), cellCenterY(first.y))
            for (i in 1 until path.sequence.size) {
                linePath.lineTo(cellCenterX(path.sequence[i].x), cellCenterY(path.sequence[i].y))
            }
            canvas.drawPath(linePath, paintLine)
        }

        // Draw endpoints
        for ((coord, colorValue) in endpointCoords) {
            val cx = cellCenterX(coord.x)
            val cy = cellCenterY(coord.y)
            val rad = cellSize * 0.35f
            paintEndpoint.color = colorValue
            canvas.drawCircle(cx, cy, rad, paintEndpoint)

            val isStart = g.isStartEndpoint(coord)
            canvas.drawText(if (isStart) "S" else "F", cx, cy + paintText.textSize * 0.35f, paintText)
        }
    }

    private fun buildEndpointMap(p: TheLinePuzzle): Map<TLCoord, Int> {
        val map = mutableMapOf<TLCoord, Int>()
        if (p.mode == TheLineMode.MULTI) {
            for (seg in p.segments) {
                map[seg.start] = seg.colorValue
                map[seg.end] = seg.colorValue
            }
        } else {
            val ep = p.endpoints ?: return map
            map[ep.first] = THE_LINE_SINGLE_COLOR
            map[ep.second] = THE_LINE_SINGLE_COLOR
        }
        return map
    }

    private fun cellCenterX(x: Int) = offsetX + x * cellSize + cellSize / 2f
    private fun cellCenterY(y: Int) = offsetY + y * cellSize + cellSize / 2f

    private fun coordFromTouch(tx: Float, ty: Float): TLCoord? {
        val g = game ?: return null
        val p = g.puzzle ?: return null
        val gx = ((tx - offsetX) / cellSize).toInt()
        val gy = ((ty - offsetY) / cellSize).toInt()
        if (gx < 0 || gx >= p.width || gy < 0 || gy >= p.height) return null
        return TLCoord(gx, gy)
    }

    private fun areAdjacent(a: TLCoord, b: TLCoord): Boolean {
        val dx = Math.abs(a.x - b.x); val dy = Math.abs(a.y - b.y)
        return (dx == 1 && dy == 0) || (dx == 0 && dy == 1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        g.puzzle ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val coord = coordFromTouch(event.x, event.y) ?: return true
                if (g.isBlocked(coord.x, coord.y)) return true

                val pathId = g.getPathIndexForEndpoint(coord)
                if (pathId != null) {
                    g.clearPath(pathId)
                    g.addToPath(pathId, coord)
                    activePathId = pathId
                    lastTouchedCoord = coord
                    invalidate(); onBoardChanged?.invoke()
                } else if (g.puzzle?.mode == TheLineMode.SINGLE) {
                    val existingPath = g.getPathForCoord(coord)
                    if (existingPath != null) {
                        val seq = g.paths[existingPath]?.sequence ?: return true
                        val idx = seq.indexOf(coord)
                        if (idx >= 0 && idx < seq.size - 1) {
                            val toRemove = seq.size - 1 - idx
                            repeat(toRemove) { g.removeLastFromPath(existingPath) }
                            g.setPathComplete(existingPath, false)
                            activePathId = existingPath
                            lastTouchedCoord = coord
                            invalidate(); onBoardChanged?.invoke()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pathId = activePathId ?: return true
                val coord = coordFromTouch(event.x, event.y) ?: return true
                val last = lastTouchedCoord ?: return true
                if (coord == last) return true
                if (!areAdjacent(coord, last)) return true
                if (g.isBlocked(coord.x, coord.y)) return true

                val path = g.paths[pathId] ?: return true
                if (path.complete) return true

                val existingInSamePath = path.sequence.indexOf(coord)
                if (existingInSamePath >= 0) {
                    if (existingInSamePath == path.sequence.size - 2) {
                        g.removeLastFromPath(pathId)
                        lastTouchedCoord = coord
                        invalidate(); onBoardChanged?.invoke()
                    }
                    return true
                }

                val occupiedBy = g.getPathForCoord(coord)
                if (occupiedBy != null && occupiedBy != pathId) return true

                val epColor = g.getEndpointColor(coord)
                if (epColor != null && epColor != path.colorValue) return true

                g.addToPath(pathId, coord)
                lastTouchedCoord = coord

                val ep = path.endpoints
                if (ep.size == 2) {
                    val startCell = path.sequence.firstOrNull()
                    val otherEnd = if (ep[0] == startCell) ep[1] else ep[0]
                    if (coord == otherEnd) {
                        val isComplete = if (g.puzzle?.mode == TheLineMode.SINGLE)
                            g.cellsRemaining == 0
                        else true
                        if (isComplete) {
                            g.setPathComplete(pathId, true)
                            activePathId = null
                            lastTouchedCoord = null
                            invalidate(); onBoardChanged?.invoke()
                            onPathCompleted?.invoke(pathId)
                            return true
                        }
                    }
                }
                invalidate(); onBoardChanged?.invoke()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pathId = activePathId
                val p2 = g.puzzle
                if (pathId != null && p2?.mode == TheLineMode.MULTI) {
                    val path = g.paths[pathId]
                    if (path != null && !path.complete) {
                        g.clearPath(pathId)
                        invalidate(); onBoardChanged?.invoke()
                    }
                }
                activePathId = null
                lastTouchedCoord = null
                return true
            }
        }
        return false
    }
}
