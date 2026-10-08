package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

/**
 * Rangée de 9 cases chiffres (1–9) visuellement identique à une ligne de SudokuGridView.
 * Utilisé comme pavé numérique compact dans le widget Sudoku.
 */
class SudokuDigitRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onDigitTapped: ((Int) -> Unit)? = null  // 1–9

    private var cellSize = 0f
    private var rowWidth = 0f

    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sudoku_cell_background)
        style = Paint.Style.FILL
    }
    private val thinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sudoku_border_thin)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val thickBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sudoku_border_thick)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Paint par chiffre (index 1–9)
    private val digitPaints: Array<Paint> = Array(10) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (i) {
                1 -> ContextCompat.getColor(context, R.color.sudoku_number_1)
                2 -> ContextCompat.getColor(context, R.color.sudoku_number_2)
                3 -> ContextCompat.getColor(context, R.color.sudoku_number_3)
                4 -> ContextCompat.getColor(context, R.color.sudoku_number_4)
                5 -> ContextCompat.getColor(context, R.color.sudoku_number_5)
                6 -> ContextCompat.getColor(context, R.color.sudoku_number_6)
                7 -> ContextCompat.getColor(context, R.color.sudoku_number_7)
                8 -> ContextCompat.getColor(context, R.color.sudoku_number_8)
                9 -> ContextCompat.getColor(context, R.color.sudoku_number_9)
                else -> ContextCompat.getColor(context, R.color.sudoku_text_user)
            }
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    // Fond de la cellule pressée
    private val pressedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sudoku_cell_selected_background)
        style = Paint.Style.FILL
    }
    private var pressedCol = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cellSz = if (w > 0) w / 9f else 20f * resources.displayMetrics.density
        setMeasuredDimension(w, cellSz.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rowWidth = w.toFloat()
        cellSize = w / 9f
        val textSize = cellSize * 0.55f
        digitPaints.forEach { it.textSize = textSize }
    }

    override fun onDraw(canvas: Canvas) {
        if (cellSize <= 0f) return

        // Fond des cellules
        for (col in 0..8) {
            val left = col * cellSize
            val paint = if (col == pressedCol) pressedBgPaint else cellBgPaint
            canvas.drawRect(left, 0f, left + cellSize, cellSize, paint)
        }

        // Lignes fines verticales internes
        for (i in 1..8) {
            val x = i * cellSize
            canvas.drawLine(x, 0f, x, cellSize, thinBorderPaint)
        }

        // Lignes horizontales haut et bas (lignes fines)
        canvas.drawLine(0f, 0f, rowWidth, 0f, thinBorderPaint)
        canvas.drawLine(0f, cellSize, rowWidth, cellSize, thinBorderPaint)

        // Bordure extérieure épaisse
        canvas.drawRect(0f, 0f, rowWidth, cellSize, thickBorderPaint)

        // Chiffres 1–9
        for (col in 0..8) {
            val digit = col + 1
            val cx = col * cellSize + cellSize / 2f
            val paint = digitPaints[digit]
            val textY = cellSize / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(digit.toString(), cx, textY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val col = (event.x / cellSize).toInt().coerceIn(0, 8)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedCol = col
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (col == pressedCol) {
                    onDigitTapped?.invoke(col + 1)
                    performClick()
                }
                pressedCol = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCol = -1
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
