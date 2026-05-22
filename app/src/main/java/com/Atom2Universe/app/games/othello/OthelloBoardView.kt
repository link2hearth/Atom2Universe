package com.Atom2Universe.app.games.othello

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class OthelloBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCellClickListener {
        fun onCellClick(row: Int, col: Int)
    }

    var game: OthelloGame? = null
    var cellClickListener: OnCellClickListener? = null
    var showValidMoves = true

    private val boardPaint = Paint().apply { color = Color.parseColor("#1B5E20") }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#145214")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111111") }
    private val blackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0F0F0") }
    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#60FFFFFF") }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D3D14") }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val n = OthelloGame.BOARD_SIZE
        val cellSize = width.toFloat() / n

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardPaint)

        for (i in 0..n) {
            val x = i * cellSize
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            canvas.drawLine(0f, x, width.toFloat(), x, gridPaint)
        }

        // Standard Reversi corner markers
        val dotR = cellSize * 0.07f
        for (dr in intArrayOf(2, 6)) for (dc in intArrayOf(2, 6)) {
            canvas.drawCircle(dc * cellSize, dr * cellSize, dotR, dotPaint)
        }

        val discR = cellSize * 0.42f
        val hintR = cellSize * 0.18f

        for (row in 0 until n) {
            for (col in 0 until n) {
                val cx = (col + 0.5f) * cellSize
                val cy = (row + 0.5f) * cellSize
                when (g.board[row][col]) {
                    OthelloGame.BLACK -> {
                        canvas.drawCircle(cx, cy, discR, blackPaint)
                        canvas.drawCircle(cx, cy, discR, blackBorderPaint)
                    }
                    OthelloGame.WHITE -> {
                        canvas.drawCircle(cx, cy, discR, whitePaint)
                        canvas.drawCircle(cx, cy, discR, whiteBorderPaint)
                    }
                    else -> if (showValidMoves && g.validMoves.containsKey(Pair(row, col))) {
                        canvas.drawCircle(cx, cy, hintR, hintPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val g = game ?: return true
        if (g.gameOver) return true
        val cellSize = width.toFloat() / OthelloGame.BOARD_SIZE
        val row = (event.y / cellSize).toInt().coerceIn(0, OthelloGame.BOARD_SIZE - 1)
        val col = (event.x / cellSize).toInt().coerceIn(0, OthelloGame.BOARD_SIZE - 1)
        cellClickListener?.onCellClick(row, col)
        return true
    }
}
