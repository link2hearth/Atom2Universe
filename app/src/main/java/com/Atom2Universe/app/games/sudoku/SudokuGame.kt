package com.Atom2Universe.app.games.sudoku

/**
 * Core Sudoku game logic: puzzle generation, validation, and solving.
 */
object SudokuGame {

    /**
     * Generates a new Sudoku puzzle with the specified difficulty.
     */
    fun generatePuzzle(difficulty: SudokuDifficulty): SudokuBoard {
        val solution = generateFullSolution()
        val targetClues = difficulty.pickClueCount()
        val puzzle = createPuzzleFromSolution(solution, targetClues)

        val board = SudokuBoard(
            cells = puzzle.map { it.copyOf() }.toTypedArray(),
            solution = solution,
            fixed = puzzle.map { row ->
                BooleanArray(9) { col -> row[col] != 0 }
            }.toTypedArray()
        )
        return board
    }

    /**
     * Generates a complete valid Sudoku solution using backtracking.
     */
    private fun generateFullSolution(): Array<IntArray> {
        val board = Array(9) { IntArray(9) { 0 } }
        fillBoard(board, 0)
        return board
    }

    private fun fillBoard(board: Array<IntArray>, index: Int): Boolean {
        if (index == 81) return true

        val row = index / 9
        val col = index % 9
        val digits = (1..9).shuffled()

        for (digit in digits) {
            if (isValidPlacement(board, row, col, digit)) {
                board[row][col] = digit
                if (fillBoard(board, index + 1)) {
                    return true
                }
                board[row][col] = 0
            }
        }
        return false
    }

    /**
     * Creates a puzzle by removing cells from the solution while maintaining uniqueness.
     */
    private fun createPuzzleFromSolution(solution: Array<IntArray>, targetClues: Int): Array<IntArray> {
        val puzzle = solution.map { it.copyOf() }.toTypedArray()
        val positions = (0 until 81).shuffled().toMutableList()
        var removals = 81 - targetClues

        for (pos in positions) {
            if (removals <= 0) break

            val row = pos / 9
            val col = pos % 9
            val backup = puzzle[row][col]

            if (backup == 0) continue

            puzzle[row][col] = 0

            if (countSolutions(puzzle, 2) != 1) {
                puzzle[row][col] = backup
            } else {
                removals--
            }
        }
        return puzzle
    }

    /**
     * Counts the number of solutions for a puzzle (up to a limit).
     */
    private fun countSolutions(board: Array<IntArray>, limit: Int): Int {
        val copy = board.map { it.copyOf() }.toTypedArray()
        return countSolutionsRecursive(copy, limit)
    }

    private fun countSolutionsRecursive(board: Array<IntArray>, limit: Int): Int {
        val emptyCell = findBestEmptyCell(board) ?: return 1

        val (row, col, candidates) = emptyCell
        if (candidates.isEmpty()) return 0

        var count = 0
        for (digit in candidates) {
            board[row][col] = digit
            count += countSolutionsRecursive(board, limit)
            if (count >= limit) {
                board[row][col] = 0
                return count
            }
        }
        board[row][col] = 0
        return count
    }

    private data class EmptyCell(val row: Int, val col: Int, val candidates: List<Int>)

    private fun findBestEmptyCell(board: Array<IntArray>): EmptyCell? {
        var best: EmptyCell? = null

        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board[row][col] != 0) continue

                val candidates = getCandidates(board, row, col)
                if (candidates.isEmpty()) {
                    return EmptyCell(row, col, emptyList())
                }
                if (best == null || candidates.size < best.candidates.size) {
                    best = EmptyCell(row, col, candidates)
                    if (candidates.size == 1) return best
                }
            }
        }
        return best
    }

    private fun getCandidates(board: Array<IntArray>, row: Int, col: Int): List<Int> {
        val candidates = mutableListOf<Int>()
        for (digit in 1..9) {
            if (isValidPlacement(board, row, col, digit)) {
                candidates.add(digit)
            }
        }
        return candidates.shuffled()
    }

    /**
     * Checks if placing a digit at the given position is valid.
     */
    fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, digit: Int): Boolean {
        // Check row
        for (c in 0 until 9) {
            if (board[row][c] == digit) return false
        }

        // Check column
        for (r in 0 until 9) {
            if (board[r][col] == digit) return false
        }

        // Check 3x3 box
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (board[r][c] == digit) return false
            }
        }

        return true
    }

    /**
     * Finds all conflicts (duplicate values) in the current board.
     */
    fun findConflicts(board: SudokuBoard): Set<CellPosition> {
        val conflicts = mutableSetOf<CellPosition>()

        // Check rows
        for (row in 0 until 9) {
            val seen = mutableMapOf<Int, Int>()
            for (col in 0 until 9) {
                val value = board.getValue(row, col)
                if (value == 0) continue
                if (seen.containsKey(value)) {
                    conflicts.add(CellPosition(row, col))
                    conflicts.add(CellPosition(row, seen[value]!!))
                } else {
                    seen[value] = col
                }
            }
        }

        // Check columns
        for (col in 0 until 9) {
            val seen = mutableMapOf<Int, Int>()
            for (row in 0 until 9) {
                val value = board.getValue(row, col)
                if (value == 0) continue
                if (seen.containsKey(value)) {
                    conflicts.add(CellPosition(row, col))
                    conflicts.add(CellPosition(seen[value]!!, col))
                } else {
                    seen[value] = row
                }
            }
        }

        // Check 3x3 boxes
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val seen = mutableMapOf<Int, CellPosition>()
                for (r in 0 until 3) {
                    for (c in 0 until 3) {
                        val row = boxRow * 3 + r
                        val col = boxCol * 3 + c
                        val value = board.getValue(row, col)
                        if (value == 0) continue
                        if (seen.containsKey(value)) {
                            conflicts.add(CellPosition(row, col))
                            conflicts.add(seen[value]!!)
                        } else {
                            seen[value] = CellPosition(row, col)
                        }
                    }
                }
            }
        }

        return conflicts
    }

    /**
     * Finds all cells that don't match the solution (mistakes).
     */
    fun findMistakes(board: SudokuBoard): Set<CellPosition> {
        val mistakes = mutableSetOf<CellPosition>()
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board.isFixed(row, col)) continue
                val value = board.getValue(row, col)
                if (value != 0 && value != board.getSolutionValue(row, col)) {
                    mistakes.add(CellPosition(row, col))
                }
            }
        }
        return mistakes
    }

    /**
     * Gets all cells related to the given position (same row, column, or box).
     */
    fun getRelatedCells(position: CellPosition): Set<CellPosition> {
        val related = mutableSetOf<CellPosition>()
        val (row, col) = position

        // Same row
        for (c in 0 until 9) {
            if (c != col) related.add(CellPosition(row, c))
        }

        // Same column
        for (r in 0 until 9) {
            if (r != row) related.add(CellPosition(r, col))
        }

        // Same box
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (r != row || c != col) related.add(CellPosition(r, c))
            }
        }

        return related
    }

    /**
     * Gets all cells with the same value as the selected cell.
     */
    fun getCellsWithSameValue(board: SudokuBoard, value: Int): Set<CellPosition> {
        if (value == 0) return emptySet()
        val cells = mutableSetOf<CellPosition>()
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board.getValue(row, col) == value) {
                    cells.add(CellPosition(row, col))
                }
            }
        }
        return cells
    }
}
