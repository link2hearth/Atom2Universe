package com.Atom2Universe.app.games.minesweeper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.Atom2Universe.app.R
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** A sonar chart: all effects are finite and share a single frame clock. */
class MinesweeperGridView(context: Context) : View(context) {
    interface GameEventListener {
        fun onGameStarted()
        fun onGameWon()
        fun onGameLost()
        fun onFlagsChanged(flags: Int)
    }

    var game: MinesweeperGame? = null
        private set
    var listener: GameEventListener? = null
    private var cellSize = 0f
    private var originX = 0f
    private var originY = 0f
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val flagPath = Path()
    private val navy = MinesweeperArt.navy
    private val teal = MinesweeperArt.teal
    private val amber = MinesweeperArt.amber
    private val red = MinesweeperArt.red
    private val numbers = intArrayOf(0, teal, Color.rgb(151, 197, 255), amber, Color.rgb(199, 165, 255),
        red, Color.rgb(119, 222, 240), Color.rgb(255, 178, 216), Color.WHITE)
    private val numberLabels = Array(9) { context.getString(R.string.minesweeper_number, it) }
    private var pendingCols = 9
    private var pendingMines = 10
    private var pendingRestore: MinesweeperGame? = null
    private var animator: ValueAnimator? = null
    private var effectTime = 1400f
    private var effectRow = 0
    private var effectCol = 0
    private var changed = emptySet<Int>()
    private var previousStates = emptyArray<CellState>()
    private var pressedCell: Pair<Int, Int>? = null

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val (r, c) = cellAt(e.x, e.y) ?: return false
            act(r, c, false)
            performClick()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val (r, c) = cellAt(e.x, e.y) ?: return
            act(r, c, true)
            pressedCell = null
        }
    })

    init { isClickable = true }

    private fun act(row: Int, col: Int, flag: Boolean) {
        val g = game ?: return
        if (g.gameState == GameState.WON || g.gameState == GameState.LOST) return
        val before = Array(g.rows * g.cols) { g.grid[it / g.cols][it % g.cols].state }
        val oldState = g.gameState
        if (flag) g.toggleFlag(row, col)
        else if (g.grid[row][col].state == CellState.REVEALED) g.chordReveal(row, col)
        else g.reveal(row, col)
        val updates = before.indices.filter { before[it] != g.grid[it / g.cols][it % g.cols].state }.toSet()
        if (updates.isEmpty()) return
        previousStates = before
        changed = updates
        effectRow = row
        effectCol = col
        performHapticFeedback(if (flag) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
        startEffect()
        listener?.onFlagsChanged(g.flagsPlaced)
        if (oldState == GameState.IDLE && g.gameState != GameState.IDLE) listener?.onGameStarted()
        when (g.gameState) {
            GameState.WON -> listener?.onGameWon()
            GameState.LOST -> listener?.onGameLost()
            else -> Unit
        }
    }

    private fun startEffect() {
        animator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            effectTime = 1400f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1400f).apply {
            duration = 1400L
            interpolator = LinearInterpolator()
            addUpdateListener { effectTime = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun clearEffects() {
        animator?.cancel()
        animator = null
        effectTime = 1400f
        changed = emptySet()
        pressedCell = null
    }

    fun newGame(cols: Int, mines: Int) {
        clearEffects()
        pendingCols = cols
        pendingMines = mines
        pendingRestore = null
        game = null
        if (width > 0 && height > 0) buildGame()
    }

    fun restoreGame(saved: MinesweeperGame) {
        clearEffects()
        pendingRestore = saved
        if (width > 0 && height > 0) applyRestore()
    }

    private fun applyRestore() {
        game = pendingRestore ?: return
        pendingRestore = null
        fitGrid()
        listener?.onFlagsChanged(game!!.flagsPlaced)
        invalidate()
    }

    private fun buildGame() {
        // Keep enough cells for the safe first click, even in a short landscape window.
        val minRows = (pendingMines + 9 + pendingCols - 1) / pendingCols
        val rows = (height / (width.toFloat() / pendingCols)).toInt().coerceAtLeast(minRows)
        game = MinesweeperGame(pendingCols, rows, pendingMines)
        fitGrid()
        listener?.onFlagsChanged(0)
        invalidate()
    }

    private fun fitGrid() {
        val g = game ?: return
        cellSize = min(width.toFloat() / g.cols, height.toFloat() / g.rows)
        originX = (width - g.cols * cellSize) / 2f
        originY = (height - g.rows * cellSize) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        when {
            pendingRestore != null -> applyRestore()
            game == null -> buildGame()
            else -> fitGrid()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (g.gameState == GameState.WON || g.gameState == GameState.LOST) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedCell = cellAt(event.x, event.y)
            MotionEvent.ACTION_MOVE -> if (cellAt(event.x, event.y) != pressedCell) pressedCell = null
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressedCell = null
        }
        gestures.onTouchEvent(event)
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val cs = cellSize
        if (cs <= 0f) return
        text.textSize = cs * .47f
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val cell = g.grid[r][c]
            val index = r * g.cols + c
            val delay = min(420f, hypot((r - effectRow).toFloat(), (c - effectCol).toFloat()) * 32f)
            val progress = if (index in changed) ((effectTime - delay) / 260f).coerceIn(0f, 1f) else 1f
            val state = if (progress == 0f && index < previousStates.size) previousStates[index] else cell.state
            val x = originX + c * cs
            val y = originY + r * cs
            val pad = cs * .045f
            rect.set(x + pad, y + pad, x + cs - pad, y + cs - pad)
            val save = canvas.save()
            if (progress > 0f && progress < 1f) {
                val scale = .86f + .14f * progress
                canvas.scale(scale, scale, rect.centerX(), rect.centerY())
            }
            val hidden = state != CellState.REVEALED
            fill(canvas, if (hidden) navy else Color.rgb(7, 26, 38), cs * .12f)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = resources.displayMetrics.density * .7f
            ink.color = when {
                state == CellState.FLAGGED -> amber
                pressedCell == Pair(r, c) -> teal
                hidden -> Color.rgb(43, 79, 96)
                else -> Color.rgb(20, 51, 65)
            }
            canvas.drawRoundRect(rect, cs * .12f, cs * .12f, ink)
            ink.style = Paint.Style.FILL
            when {
                state == CellState.FLAGGED -> MinesweeperArt.drawBeacon(canvas, rect, cs, ink, flagPath)
                hidden -> {
                    ink.color = Color.rgb(65, 105, 122)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), cs * .025f, ink)
                    ink.color = Color.rgb(39, 74, 91)
                    canvas.drawLine(x + cs * .2f, y + cs * .16f, x + cs * .42f, y + cs * .16f, ink)
                }
                cell.isMine -> MinesweeperArt.drawMine(canvas, rect, cs, g.detonatedCell == Pair(r, c), ink)
                cell.adjacentMines > 0 -> {
                    text.color = numbers[cell.adjacentMines.coerceIn(1, 8)]
                    canvas.drawText(numberLabels[cell.adjacentMines], rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
                }
                else -> {
                    ink.color = Color.rgb(27, 68, 77)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), cs * .025f, ink)
                }
            }
            if (index in changed && progress > 0f && progress < 1f) {
                ink.color = teal
                ink.alpha = ((1f - progress) * 70).toInt()
                canvas.drawRoundRect(rect, cs * .12f, cs * .12f, ink)
                ink.alpha = 255
            }
            canvas.restoreToCount(save)
        }
        if (effectTime < 1400f) drawSonar(canvas, g, cs)
    }

    private fun fill(canvas: Canvas, color: Int, radius: Float) {
        ink.color = color
        ink.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, ink)
    }

    private fun drawSonar(canvas: Canvas, g: MinesweeperGame, cs: Float) {
        val p = effectTime / 1400f
        val terminal = g.gameState == GameState.WON || g.gameState == GameState.LOST
        val source = if (g.gameState == GameState.LOST) g.detonatedCell else null
        val cx = originX + ((source?.second ?: effectCol) + .5f) * cs
        val cy = originY + ((source?.first ?: effectRow) + .5f) * cs
        val save = canvas.save()
        canvas.clipRect(originX, originY, originX + g.cols * cs, originY + g.rows * cs)
        ink.color = if (g.gameState == GameState.LOST) red else teal
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = cs * .04f
        for (i in 0..1) {
            val wave = (p - i * .15f).coerceAtLeast(0f)
            ink.alpha = ((1f - p) * if (terminal) 180 else 95).toInt()
            canvas.drawCircle(cx, cy, wave * hypot(width.toFloat(), height.toFloat()), ink)
        }
        ink.style = Paint.Style.FILL
        if (terminal) {
            // Sparse rising bubbles on success, radial sparks on a mine hit.
            for (i in 0 until 24) {
                val angle = i * 2.39996f
                val distance = cs * (1f + p * (3 + i % 5))
                val x = if (g.gameState == GameState.WON) originX + (i * .618034f % 1f) * g.cols * cs else cx + kotlin.math.cos(angle) * distance
                val y = if (g.gameState == GameState.WON) originY + g.rows * cs * (1f - p) + sin(angle) * cs else cy + sin(angle) * distance
                ink.alpha = ((1f - p) * 190).toInt()
                canvas.drawCircle(x, y, cs * .045f * (1 + i % 3), ink)
            }
        }
        ink.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val g = game ?: return null
        if (cellSize <= 0f || x < originX || y < originY) return null
        val c = ((x - originX) / cellSize).toInt()
        val r = ((y - originY) / cellSize).toInt()
        return if (r in 0 until g.rows && c in 0 until g.cols) Pair(r, c) else null
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) clearEffects()
    }

    override fun onDetachedFromWindow() {
        clearEffects()
        super.onDetachedFromWindow()
    }
}
