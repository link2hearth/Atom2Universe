package com.Atom2Universe.app.games.sudoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R

/**
 * Custom View for rendering the Sudoku 9x9 grid.
 * Handles cell selection, highlighting, and number display.
 */
class SudokuGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCellSelectedListener {
        fun onCellSelected(row: Int, col: Int)
    }

    var listener: OnCellSelectedListener? = null

    // Board data
    private var board: SudokuBoard = SudokuBoard.empty()
    private var selectedRow: Int = -1
    private var selectedCol: Int = -1

    // Highlighting sets
    private var relatedCells: Set<CellPosition> = emptySet()
    private var sameValueCells: Set<CellPosition> = emptySet()
    private var conflictCells: Set<CellPosition> = emptySet()
    private var mistakeCells: Set<CellPosition> = emptySet()
    private var showMistakes: Boolean = false
    private var showConflicts: Boolean = false

    // Dimensions
    private var cellSize: Float = 0f
    private var gridSize: Float = 0f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // Paints
    private val cellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fixedCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val relatedCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sameValueCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val errorCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val conflictCellBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thickBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fixedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val userTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val errorTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Number colors
    private val numberColors = IntArray(10)

    // Alphas indépendants (0-255)
    private var cellBgAlpha = 255
    private var textAlpha = 255

    init {
        setupPaints()
    }

    private fun setupPaints() {
        cellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_background)
            style = Paint.Style.FILL
        }

        fixedCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_fixed_background)
            style = Paint.Style.FILL
        }

        selectedCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_selected_background)
            style = Paint.Style.FILL
        }

        relatedCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_related_background)
            style = Paint.Style.FILL
        }

        sameValueCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_same_value_background)
            style = Paint.Style.FILL
        }

        errorCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_error_background)
            style = Paint.Style.FILL
        }

        conflictCellBackgroundPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_cell_conflict_background)
            style = Paint.Style.FILL
        }

        thinBorderPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_border_thin)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        thickBorderPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_border_thick)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        fixedTextPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_text_fixed)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        userTextPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_text_user)
            textAlign = Paint.Align.CENTER
        }

        errorTextPaint.apply {
            color = ContextCompat.getColor(context, R.color.sudoku_text_error)
            textAlign = Paint.Align.CENTER
        }

        // Number colors (index 0 unused, 1-9 for digits)
        numberColors[0] = ContextCompat.getColor(context, R.color.sudoku_text_user)
        numberColors[1] = ContextCompat.getColor(context, R.color.sudoku_number_1)
        numberColors[2] = ContextCompat.getColor(context, R.color.sudoku_number_2)
        numberColors[3] = ContextCompat.getColor(context, R.color.sudoku_number_3)
        numberColors[4] = ContextCompat.getColor(context, R.color.sudoku_number_4)
        numberColors[5] = ContextCompat.getColor(context, R.color.sudoku_number_5)
        numberColors[6] = ContextCompat.getColor(context, R.color.sudoku_number_6)
        numberColors[7] = ContextCompat.getColor(context, R.color.sudoku_number_7)
        numberColors[8] = ContextCompat.getColor(context, R.color.sudoku_number_8)
        numberColors[9] = ContextCompat.getColor(context, R.color.sudoku_number_9)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gridSize = minOf(w, h).toFloat()
        cellSize = gridSize / 9f
        offsetX = (w - gridSize) / 2f
        offsetY = (h - gridSize) / 2f

        // Update text sizes based on cell size
        val textSize = cellSize * 0.55f
        fixedTextPaint.textSize = textSize
        userTextPaint.textSize = textSize
        errorTextPaint.textSize = textSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(offsetX, offsetY)

        drawCells(canvas)
        drawGrid(canvas)
        drawNumbers(canvas)

        canvas.restore()
    }

    private fun drawCells(canvas: Canvas) {
        val rect = RectF()

        for (row in 0 until 9) {
            for (col in 0 until 9) {
                val left = col * cellSize
                val top = row * cellSize
                rect.set(left, top, left + cellSize, top + cellSize)

                val position = CellPosition(row, col)
                val isSelected = row == selectedRow && col == selectedCol
                val isRelated = relatedCells.contains(position)
                val isSameValue = sameValueCells.contains(position)
                val isConflict = showConflicts && conflictCells.contains(position)
                val isMistake = showMistakes && mistakeCells.contains(position)
                val isFixed = board.isFixed(row, col)

                // Determine cell background (priority order)
                val backgroundPaint = when {
                    isSelected -> selectedCellBackgroundPaint
                    isMistake -> errorCellBackgroundPaint
                    isConflict -> conflictCellBackgroundPaint
                    isSameValue -> sameValueCellBackgroundPaint
                    isRelated -> relatedCellBackgroundPaint
                    isFixed -> fixedCellBackgroundPaint
                    else -> cellBackgroundPaint
                }

                canvas.drawRect(rect, backgroundPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        // Draw thin lines
        for (i in 0..9) {
            val pos = i * cellSize
            canvas.drawLine(pos, 0f, pos, gridSize, thinBorderPaint)
            canvas.drawLine(0f, pos, gridSize, pos, thinBorderPaint)
        }

        // Draw thick lines for 3x3 boxes
        for (i in 0..3) {
            val pos = i * 3 * cellSize
            canvas.drawLine(pos, 0f, pos, gridSize, thickBorderPaint)
            canvas.drawLine(0f, pos, gridSize, pos, thickBorderPaint)
        }

        // Draw outer border
        canvas.drawRect(0f, 0f, gridSize, gridSize, thickBorderPaint)
    }

    private fun drawNumbers(canvas: Canvas) {
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                val value = board.getValue(row, col)
                if (value == 0) continue

                val centerX = col * cellSize + cellSize / 2
                val centerY = row * cellSize + cellSize / 2

                val isFixed = board.isFixed(row, col)

                val paint = if (isFixed) fixedTextPaint else userTextPaint

                // Couleur par chiffre + alpha texte indépendant
                paint.color = numberColors[value]
                paint.alpha = textAlpha

                // Vertical centering
                val textY = centerY - (paint.descent() + paint.ascent()) / 2

                canvas.drawText(value.toString(), centerX, textY, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - offsetX
            val y = event.y - offsetY

            if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                val col = (x / cellSize).toInt().coerceIn(0, 8)
                val row = (y / cellSize).toInt().coerceIn(0, 8)

                selectCell(row, col)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setBoard(newBoard: SudokuBoard) {
        board = newBoard
        mistakeCells = emptySet()
        showMistakes = false
        clearSelection()
        invalidate()
    }

    fun getBoard(): SudokuBoard = board

    fun selectCell(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
        updateHighlights()
        listener?.onCellSelected(row, col)
        invalidate()
    }

    fun clearSelection() {
        selectedRow = -1
        selectedCol = -1
        relatedCells = emptySet()
        sameValueCells = emptySet()
        invalidate()
    }

    fun getSelectedRow(): Int = selectedRow
    fun getSelectedCol(): Int = selectedCol

    fun hasSelection(): Boolean = selectedRow >= 0 && selectedCol >= 0

    fun setValueAtSelected(value: Int) {
        if (!hasSelection()) return
        if (board.isFixed(selectedRow, selectedCol)) return

        board.setValue(selectedRow, selectedCol, value)
        updateHighlights()
        invalidate()
    }

    fun setValueAt(row: Int, col: Int, value: Int) {
        if (board.isFixed(row, col)) return
        board.setValue(row, col, value)
        updateHighlights()
        invalidate()
    }

    private fun updateHighlights() {
        if (!hasSelection()) {
            relatedCells = emptySet()
            sameValueCells = emptySet()
            return
        }

        val position = CellPosition(selectedRow, selectedCol)
        relatedCells = SudokuGame.getRelatedCells(position)

        val value = board.getValue(selectedRow, selectedCol)
        sameValueCells = if (value > 0) {
            SudokuGame.getCellsWithSameValue(board, value)
        } else {
            emptySet()
        }
    }

    fun setConflicts(conflicts: Set<CellPosition>) {
        conflictCells = conflicts
        invalidate()
    }

    fun setMistakes(mistakes: Set<CellPosition>) {
        mistakeCells = mistakes
        invalidate()
    }

    fun setShowMistakes(show: Boolean) {
        showMistakes = show
        invalidate()
    }

    fun setShowConflicts(show: Boolean) {
        showConflicts = show
        invalidate()
    }

    fun refresh() {
        updateHighlights()
        invalidate()
    }

    /** Opacité des fonds de cases et des lignes de grille (0–100). */
    fun setBackgroundAlpha(percent: Int) {
        cellBgAlpha = (percent.coerceIn(0, 100) / 100f * 255f).toInt()
        listOf(
            cellBackgroundPaint, fixedCellBackgroundPaint,
            selectedCellBackgroundPaint, relatedCellBackgroundPaint,
            sameValueCellBackgroundPaint, errorCellBackgroundPaint,
            conflictCellBackgroundPaint, thinBorderPaint, thickBorderPaint
        ).forEach { it.alpha = cellBgAlpha }
        invalidate()
    }

    /** Opacité des chiffres uniquement (0–100). */
    fun setNumbersAlpha(percent: Int) {
        textAlpha = (percent.coerceIn(0, 100) / 100f * 255f).toInt()
        invalidate()
    }
}
