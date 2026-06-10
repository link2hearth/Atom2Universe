package com.Atom2Universe.app.games.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

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
    private val cellRect = RectF()

    private val paintCell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRevealed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintMine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFlag = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG)

    private val colorHidden = Color.parseColor("#BDBDBD")
    private val colorHighlight = Color.parseColor("#E0E0E0")
    private val colorShadow = Color.parseColor("#757575")
    private val colorRevealed = Color.parseColor("#9E9E9E")
    private val colorRevealedBg = Color.parseColor("#EEEEEE")
    private val colorMine = Color.parseColor("#D32F2F")
    private val colorMineHit = Color.parseColor("#FF5252")
    private val colorFlag = Color.parseColor("#F44336")
    private val colorBorder = Color.parseColor("#616161")

    private val numberColors = intArrayOf(
        0, Color.parseColor("#1565C0"), Color.parseColor("#2E7D32"),
        Color.parseColor("#C62828"), Color.parseColor("#1A237E"),
        Color.parseColor("#B71C1C"), Color.parseColor("#006064"),
        Color.parseColor("#212121"), Color.parseColor("#424242")
    )

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var longPressRow = -1
    private var longPressCol = -1
    private var isLongPressConsumed = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val (row, col) = cellAt(e.x, e.y) ?: return false
            val g = game ?: return false
            val cell = g.grid[row][col]
            when {
                cell.state == CellState.HIDDEN -> g.reveal(row, col)
                cell.state == CellState.REVEALED -> g.chordReveal(row, col)
                else -> return false
            }
            notifyState()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val (row, col) = cellAt(e.x, e.y) ?: return
            val g = game ?: return
            if (g.grid[row][col].state == CellState.REVEALED) return
            g.toggleFlag(row, col)
            listener?.onFlagsChanged(g.flagsPlaced)
            invalidate()
        }
    })

    init {
        paintText.typeface = Typeface.DEFAULT_BOLD
        paintText.textAlign = Paint.Align.CENTER
    }

    private var pendingCols = 9
    private var pendingMines = 10
    private var pendingRestore: MinesweeperGame? = null

    fun newGame(cols: Int, mines: Int) {
        pendingCols = cols
        pendingMines = mines
        pendingRestore = null
        if (width > 0 && height > 0) buildGame()
    }

    fun restoreGame(saved: MinesweeperGame) {
        pendingCols = saved.cols
        pendingMines = saved.mineCount
        pendingRestore = saved
        if (width > 0 && height > 0) applyRestore()
    }

    private fun applyRestore() {
        val g = pendingRestore ?: return
        cellSize = width.toFloat() / g.cols
        game = g
        pendingRestore = null
        listener?.onFlagsChanged(g.flagsPlaced)
        invalidate()
    }

    private fun buildGame() {
        val cs = width.toFloat() / pendingCols
        val rows = (height / cs).toInt().coerceAtLeast(1)
        cellSize = cs
        game = MinesweeperGame(pendingCols, rows, pendingMines)
        listener?.onFlagsChanged(0)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (pendingRestore != null) applyRestore() else buildGame()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (game?.gameState == GameState.WON || game?.gameState == GameState.LOST) return false
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val cs = cellSize
        if (cs <= 0f) return

        paintText.textSize = cs * 0.55f

        for (r in 0 until g.rows) {
            for (c in 0 until g.cols) {
                val left = c * cs
                val top = r * cs
                cellRect.set(left, top, left + cs, top + cs)
                drawCell(canvas, g.grid[r][c], cellRect, cs, r == g.rows - 1 && c == g.cols - 1 && g.gameState == GameState.LOST)
            }
        }
    }

    private fun drawCell(canvas: Canvas, cell: Cell, rect: RectF, cs: Float, isHitMine: Boolean) {
        val border = cs * 0.06f
        val pad = cs * 0.04f

        when (cell.state) {
            CellState.HIDDEN, CellState.FLAGGED -> {
                // Raised 3D look
                paintCell.color = colorHidden
                canvas.drawRect(rect, paintCell)

                paintHighlight.color = colorHighlight
                canvas.drawRect(rect.left, rect.top, rect.right - border, rect.top + border, paintHighlight)
                canvas.drawRect(rect.left, rect.top, rect.left + border, rect.bottom - border, paintHighlight)

                paintShadow.color = colorShadow
                canvas.drawRect(rect.left + border, rect.bottom - border, rect.right, rect.bottom, paintShadow)
                canvas.drawRect(rect.right - border, rect.top + border, rect.right, rect.bottom, paintShadow)

                paintBorder(canvas, rect, pad)

                if (cell.state == CellState.FLAGGED) drawFlag(canvas, rect, cs)
            }

            CellState.REVEALED -> {
                paintRevealed.color = if (isHitMine) colorMineHit else colorRevealedBg
                canvas.drawRect(rect, paintRevealed)
                paintBorder(canvas, rect, pad)

                if (cell.isMine) {
                    drawMine(canvas, rect, cs, isHitMine)
                } else if (cell.adjacentMines > 0) {
                    paintText.color = numberColors[cell.adjacentMines.coerceIn(1, 8)]
                    canvas.drawText(
                        cell.adjacentMines.toString(),
                        rect.centerX(), rect.centerY() + paintText.textSize * 0.38f, paintText
                    )
                }
            }
        }
    }

    private fun paintBorder(canvas: Canvas, rect: RectF, pad: Float) {
        paintCell.color = colorBorder
        paintCell.style = Paint.Style.STROKE
        paintCell.strokeWidth = pad
        canvas.drawRect(rect, paintCell)
        paintCell.style = Paint.Style.FILL
    }

    private fun drawMine(canvas: Canvas, rect: RectF, cs: Float, isHit: Boolean) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val r = cs * 0.28f
        paintMine.color = if (isHit) colorMineHit else Color.BLACK
        canvas.drawCircle(cx, cy, r, paintMine)
        // Spikes
        paintMine.strokeWidth = cs * 0.07f
        paintMine.style = Paint.Style.STROKE
        val spike = cs * 0.38f
        for (angle in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
            val rad = Math.toRadians(angle.toDouble())
            canvas.drawLine(
                cx + (r * 0.6f * Math.cos(rad)).toFloat(), cy + (r * 0.6f * Math.sin(rad)).toFloat(),
                cx + (spike * Math.cos(rad)).toFloat(), cy + (spike * Math.sin(rad)).toFloat(),
                paintMine
            )
        }
        paintMine.style = Paint.Style.FILL
        paintMine.color = Color.WHITE
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.2f, paintMine)
    }

    private fun drawFlag(canvas: Canvas, rect: RectF, cs: Float) {
        val cx = rect.centerX(); val cy = rect.centerY()
        val pole = cs * 0.55f
        val flagW = cs * 0.3f
        val flagH = cs * 0.22f
        val baseY = cy + pole * 0.38f
        val topY = cy - pole * 0.38f

        // Pole
        paintFlag.color = Color.DKGRAY
        paintFlag.strokeWidth = cs * 0.06f
        paintFlag.style = Paint.Style.STROKE
        canvas.drawLine(cx, baseY, cx, topY, paintFlag)

        // Base
        canvas.drawLine(cx - cs * 0.2f, baseY, cx + cs * 0.2f, baseY, paintFlag)
        paintFlag.style = Paint.Style.FILL

        // Flag triangle
        val path = android.graphics.Path()
        path.moveTo(cx, topY)
        path.lineTo(cx + flagW, topY + flagH * 0.5f)
        path.lineTo(cx, topY + flagH)
        path.close()
        paintFlag.color = colorFlag
        canvas.drawPath(path, paintFlag)
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val g = game ?: return null
        val cs = cellSize
        if (cs <= 0f) return null
        val col = (x / cs).toInt()
        val row = (y / cs).toInt()
        if (row !in 0 until g.rows || col !in 0 until g.cols) return null
        return Pair(row, col)
    }

    private fun notifyState() {
        val g = game ?: return
        listener?.onFlagsChanged(g.flagsPlaced)
        invalidate()
        when (g.gameState) {
            GameState.PLAYING -> listener?.onGameStarted()
            GameState.WON -> { listener?.onGameStarted(); listener?.onGameWon() }
            GameState.LOST -> { listener?.onGameStarted(); listener?.onGameLost() }
            else -> {}
        }
    }
}
