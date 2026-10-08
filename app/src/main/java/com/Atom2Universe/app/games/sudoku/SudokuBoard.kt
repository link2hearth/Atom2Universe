package com.Atom2Universe.app.games.sudoku

/**
 * Represents a Sudoku board state.
 * Contains the current board, the solution, and tracks which cells are fixed (clues).
 */
data class SudokuBoard(
    val cells: Array<IntArray> = Array(9) { IntArray(9) { 0 } },
    val solution: Array<IntArray> = Array(9) { IntArray(9) { 0 } },
    val fixed: Array<BooleanArray> = Array(9) { BooleanArray(9) { false } }
) {
    companion object {
        const val SIZE = 9
        const val BOX_SIZE = 3

        fun empty(): SudokuBoard = SudokuBoard()
    }

    fun getValue(row: Int, col: Int): Int = cells[row][col]

    fun setValue(row: Int, col: Int, value: Int) {
        if (!fixed[row][col]) {
            cells[row][col] = value
        }
    }

    fun isFixed(row: Int, col: Int): Boolean = fixed[row][col]

    fun getSolutionValue(row: Int, col: Int): Int = solution[row][col]

    fun isCorrect(row: Int, col: Int): Boolean {
        val value = cells[row][col]
        if (value == 0) return true
        return value == solution[row][col]
    }

    fun isComplete(): Boolean {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                if (cells[row][col] == 0) return false
            }
        }
        return true
    }

    fun isSolved(): Boolean {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                if (cells[row][col] != solution[row][col]) return false
            }
        }
        return true
    }

    fun clone(): SudokuBoard {
        return SudokuBoard(
            cells = Array(SIZE) { row -> cells[row].copyOf() },
            solution = Array(SIZE) { row -> solution[row].copyOf() },
            fixed = Array(SIZE) { row -> fixed[row].copyOf() }
        )
    }

    fun clear() {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) {
                if (!fixed[row][col]) {
                    cells[row][col] = 0
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SudokuBoard) return false
        for (i in 0 until SIZE) {
            if (!cells[i].contentEquals(other.cells[i])) return false
            if (!solution[i].contentEquals(other.solution[i])) return false
            if (!fixed[i].contentEquals(other.fixed[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = cells.contentDeepHashCode()
        result = 31 * result + solution.contentDeepHashCode()
        result = 31 * result + fixed.contentDeepHashCode()
        return result
    }
}

/**
 * Represents a position on the board with validation info.
 */
data class CellPosition(val row: Int, val col: Int) {
    fun isValid(): Boolean = row in 0..8 && col in 0..8

    fun getBoxIndex(): Int = (row / 3) * 3 + (col / 3)

    fun isInSameRow(other: CellPosition): Boolean = row == other.row

    fun isInSameCol(other: CellPosition): Boolean = col == other.col

    fun isInSameBox(other: CellPosition): Boolean = getBoxIndex() == other.getBoxIndex()

    fun isRelatedTo(other: CellPosition): Boolean =
        isInSameRow(other) || isInSameCol(other) || isInSameBox(other)
}

/**
 * Represents a conflict (duplicate value) in the board.
 */
data class SudokuConflict(
    val position: CellPosition,
    val type: ConflictType
)

enum class ConflictType {
    ROW, COLUMN, BOX
}
