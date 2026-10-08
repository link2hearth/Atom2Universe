package com.Atom2Universe.app.games.kit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La vue commune des grilles dont on fait tourner lignes et colonnes (Seize, Netslide) : des
 * flèches tout autour décalent d'un cran, un glissé décale d'autant de cases que le doigt en a
 * parcouru, et la ligne suit le doigt pendant le geste.
 */
abstract class SlideGridView<S : Any>(context: Context) : PuzzleView<S>(context) {
    protected val grid = GridGeometry()
    private val r = RectF()
    private val arrow = Path()
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragDx = 0f
    private var dragDy = 0f
    private var dragging = false

    protected abstract fun cols(state: S): Int
    protected abstract fun rows(state: S): Int
    protected abstract fun shiftRow(state: S, row: Int, by: Int): S
    protected abstract fun shiftCol(state: S, col: Int, by: Int): S
    protected abstract fun drawTile(canvas: Canvas, state: S, index: Int, r: RectF, cell: Float)
    protected open fun drawUnder(canvas: Canvas, state: S) {}

    override fun drawBoard(canvas: Canvas, state: S, area: RectF) {
        val w = cols(state); val h = rows(state)
        grid.fit(area, w, h, 0.7f, 0.7f, 0.7f, 0.7f)
        val cell = grid.cell
        val gap = cell * 0.04f
        fill.color = palette.raised
        canvas.drawRoundRect(grid.left - gap, grid.top - gap, grid.right + gap, grid.bottom + gap, cell * 0.12f, cell * 0.12f, fill)
        drawUnder(canvas, state)
        val horizontal = dragging && abs(dragDx) >= abs(dragDy)
        val dragRow = if (dragging) grid.rowAt(dragStartY) else -1
        val dragCol = if (dragging) grid.colAt(dragStartX) else -1
        canvas.save()
        canvas.clipRect(grid.left, grid.top, grid.right, grid.bottom)
        for (i in 0 until w * h) {
            val x = i % w; val y = i / w
            var ox = 0f; var oy = 0f
            if (horizontal && y == dragRow) ox = dragDx
            if (!horizontal && dragging && x == dragCol) oy = dragDy
            for (wrap in -1..1) {
                if (wrap != 0 && ox == 0f && oy == 0f) continue
                val wx = if (horizontal) wrap * w * cell else 0f
                val wy = if (!horizontal && dragging) wrap * h * cell else 0f
                r.set(grid.x(x) + ox + wx + gap, grid.y(y) + oy + wy + gap, grid.x(x + 1) + ox + wx - gap, grid.y(y + 1) + oy + wy - gap)
                drawTile(canvas, state, i, r, cell)
            }
        }
        canvas.restore()
        fill.color = palette.secondary
        val a = cell * 0.18f
        for (y in 0 until h) {
            drawArrow(canvas, grid.left - cell * 0.35f, grid.cy(y), a, -1, 0)
            drawArrow(canvas, grid.right + cell * 0.35f, grid.cy(y), a, 1, 0)
        }
        for (x in 0 until w) {
            drawArrow(canvas, grid.cx(x), grid.top - cell * 0.35f, a, 0, -1)
            drawArrow(canvas, grid.cx(x), grid.bottom + cell * 0.35f, a, 0, 1)
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, s: Float, dx: Int, dy: Int) {
        arrow.reset()
        arrow.moveTo(cx + dx * s, cy + dy * s)
        arrow.lineTo(cx - dx * s * 0.6f + dy * s * 0.8f, cy - dy * s * 0.6f + dx * s * 0.8f)
        arrow.lineTo(cx - dx * s * 0.6f - dy * s * 0.8f, cy - dy * s * 0.6f - dx * s * 0.8f)
        arrow.close()
        canvas.drawPath(arrow, fill)
    }

    override fun onTap(x: Float, y: Float) {
        val s = state ?: return
        val c = grid.colAt(x)
        val row = grid.rowAt(y)
        val next = when {
            row >= 0 && x < grid.left -> shiftRow(s, row, -1)
            row >= 0 && x >= grid.right -> shiftRow(s, row, 1)
            c >= 0 && y < grid.top -> shiftCol(s, c, -1)
            c >= 0 && y >= grid.bottom -> shiftCol(s, c, 1)
            else -> null
        } ?: return
        tick()
        move(next)
    }

    override fun wantsDrag(x: Float, y: Float) = grid.cellAt(x, y) >= 0

    override fun onDragStart(x: Float, y: Float) {
        dragStartX = x; dragStartY = y; dragDx = 0f; dragDy = 0f; dragging = true
    }

    override fun onDragMove(x: Float, y: Float) {
        dragDx = x - dragStartX
        dragDy = y - dragStartY
        invalidate()
    }

    override fun onDragEnd(x: Float, y: Float) {
        val s = state
        dragging = false
        if (s == null) { invalidate(); return }
        val horizontal = abs(dragDx) >= abs(dragDy)
        val steps = ((if (horizontal) dragDx else dragDy) / grid.cell).roundToInt()
        if (steps == 0) { invalidate(); return }
        tick()
        move(if (horizontal) shiftRow(s, grid.rowAt(dragStartY), steps) else shiftCol(s, grid.colAt(dragStartX), steps))
    }

    override fun onDragCancel() {
        dragging = false
        invalidate()
    }
}
