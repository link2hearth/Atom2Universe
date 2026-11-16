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
    /**
     * Couché, la grille s'affiche tournée d'un quart : les lignes de la partie vont de gauche à
     * droite. La partie elle-même ne change pas (mêmes cases, mêmes voisins) quand on tourne l'écran.
     */
    private var turned = false
    private fun cellX(r: Int, c: Int) = originX + (if (turned) r else c) * cellSize
    private fun cellY(r: Int, c: Int) = originY + (if (turned) c else r) * cellSize
    private fun shownCols(g: MinesweeperGame) = if (turned) g.rows else g.cols
    private fun shownRows(g: MinesweeperGame) = if (turned) g.cols else g.rows
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
    // Cases changées par le dernier coup, par indice : pas de boîte ni de Pair par case et par image.
    private var changed = BooleanArray(0)
    private var previousStates = emptyArray<CellState>()
    private var pressedCell = -1
    // Premier appui : la disposition des mines se calcule sur un fil de fond (jusqu'à 0,35 s).
    private var layingOut = false
    private var layoutToken = 0

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val g = game ?: return false
            val i = cellIndexAt(e.x, e.y)
            if (i < 0) return false
            act(i / g.cols, i % g.cols, false)
            performClick()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val g = game ?: return
            val i = cellIndexAt(e.x, e.y)
            if (i < 0) return
            act(i / g.cols, i % g.cols, true)
            pressedCell = -1
        }
    })

    init { isClickable = true }

    private fun act(row: Int, col: Int, flag: Boolean) {
        val g = game ?: return
        if (layingOut) return
        if (g.gameState == GameState.WON || g.gameState == GameState.LOST) return
        if (!flag && g.needsLayout && g.grid[row][col].state == CellState.HIDDEN) {
            layOutThenReveal(g, row, col)
            return
        }
        val count = g.rows * g.cols
        val before = Array(count) { g.grid[it / g.cols][it % g.cols].state }
        val oldState = g.gameState
        if (flag) g.toggleFlag(row, col)
        else if (g.grid[row][col].state == CellState.REVEALED) g.chordReveal(row, col)
        else g.reveal(row, col)
        val updates = BooleanArray(count)
        var any = false
        for (i in 0 until count) {
            if (before[i] != g.grid[i / g.cols][i % g.cols].state) { updates[i] = true; any = true }
        }
        if (!any) return
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

    /** Calcule la disposition hors du fil de l'interface, puis rejoue l'appui. */
    private fun layOutThenReveal(g: MinesweeperGame, row: Int, col: Int) {
        layingOut = true
        val token = ++layoutToken
        val cols = g.cols
        val rows = g.rows
        val mines = g.mineCount
        Thread({
            val layout = MinesweeperGame.layoutMines(cols, rows, mines, row * cols + col, kotlin.random.Random)
            post {
                if (token != layoutToken || game !== g) return@post
                layingOut = false
                g.applyLayout(layout)
                act(row, col, false)
            }
        }, "MinesweeperLayout").start()
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
        changed = BooleanArray(0)
        pressedCell = -1
        // Une disposition encore en calcul est abandonnée : l'appui sera à refaire.
        layoutToken++
        layingOut = false
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
        // Les colonnes de la difficulté vont sur le petit côté de l'écran, qu'il soit debout ou couché.
        val rows = (maxOf(width, height) / (minOf(width, height).toFloat() / pendingCols)).toInt().coerceAtLeast(minRows)
        game = MinesweeperGame(pendingCols, rows, pendingMines)
        fitGrid()
        listener?.onFlagsChanged(0)
        invalidate()
    }

    private fun fitGrid() {
        val g = game ?: return
        turned = width > height
        cellSize = min(width.toFloat() / shownCols(g), height.toFloat() / shownRows(g))
        originX = (width - shownCols(g) * cellSize) / 2f
        originY = (height - shownRows(g) * cellSize) / 2f
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
        val wasPressed = pressedCell
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedCell = cellIndexAt(event.x, event.y)
            MotionEvent.ACTION_MOVE -> if (cellIndexAt(event.x, event.y) != pressedCell) pressedCell = -1
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressedCell = -1
        }
        gestures.onTouchEvent(event)
        // Un coup joué se redessine par son effet ; ici, seulement le liseré de la case appuyée.
        if (pressedCell != wasPressed) invalidate()
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
            val isChanged = index < changed.size && changed[index]
            val progress = if (isChanged) ((effectTime - delay) / 260f).coerceIn(0f, 1f) else 1f
            val state = if (progress == 0f && index < previousStates.size) previousStates[index] else cell.state
            val x = cellX(r, c)
            val y = cellY(r, c)
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
                pressedCell == index -> teal
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
                cell.isMine -> MinesweeperArt.drawMine(canvas, rect, cs,
                    g.detonatedCell.let { it != null && it.first == r && it.second == c }, ink)
                cell.adjacentMines > 0 -> {
                    text.color = numbers[cell.adjacentMines.coerceIn(1, 8)]
                    canvas.drawText(numberLabels[cell.adjacentMines], rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
                }
                else -> {
                    ink.color = Color.rgb(27, 68, 77)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), cs * .025f, ink)
                }
            }
            if (isChanged && progress > 0f && progress < 1f) {
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
        val cx = cellX(source?.first ?: effectRow, source?.second ?: effectCol) + cs / 2
        val cy = cellY(source?.first ?: effectRow, source?.second ?: effectCol) + cs / 2
        val shownW = shownCols(g) * cs
        val shownH = shownRows(g) * cs
        val save = canvas.save()
        canvas.clipRect(originX, originY, originX + shownW, originY + shownH)
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
                val x = if (g.gameState == GameState.WON) originX + (i * .618034f % 1f) * shownW else cx + kotlin.math.cos(angle) * distance
                val y = if (g.gameState == GameState.WON) originY + shownH * (1f - p) + sin(angle) * cs else cy + sin(angle) * distance
                ink.alpha = ((1f - p) * 190).toInt()
                canvas.drawCircle(x, y, cs * .045f * (1 + i % 3), ink)
            }
        }
        ink.alpha = 255
        canvas.restoreToCount(save)
    }

    /** Indice de la case sous le doigt (ligne × colonnes + colonne), -1 hors de la grille. */
    private fun cellIndexAt(x: Float, y: Float): Int {
        val g = game ?: return -1
        if (cellSize <= 0f || x < originX || y < originY) return -1
        val across = ((x - originX) / cellSize).toInt()
        val down = ((y - originY) / cellSize).toInt()
        val c = if (turned) down else across
        val r = if (turned) across else down
        return if (r in 0 until g.rows && c in 0 until g.cols) r * g.cols + c else -1
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
