package com.Atom2Universe.app.games.sudoku

import kotlin.random.Random

/**
 * Core Sudoku game logic: puzzle generation, validation, and solving.
 */
object SudokuGame {

    /**
     * Generates a new Sudoku puzzle with the specified difficulty.
     */
    fun generatePuzzle(difficulty: SudokuDifficulty): SudokuBoard {
        // On garde la première grille qui demande exactement le niveau voulu ; à défaut, la plus
        // proche. Une grille facile se trouve du premier coup, une difficile en quelques essais.
        var best: Pair<Array<IntArray>, Array<IntArray>>? = null
        var bestLevel = 0
        for (attempt in 0 until MAX_ATTEMPTS) {
            val solution = generateFullSolution()
            val puzzle = createPuzzleFromSolution(solution, difficulty)
            val level = SudokuGrader.grade(flatten(puzzle), difficulty.level)
            if (level > bestLevel) {
                best = solution to puzzle
                bestLevel = level
            }
            if (level == difficulty.level) break
        }
        val (solution, puzzle) = best!!

        return SudokuBoard(
            cells = puzzle.map { it.copyOf() }.toTypedArray(),
            solution = solution,
            fixed = puzzle.map { row ->
                BooleanArray(9) { col -> row[col] != 0 }
            }.toTypedArray()
        )
    }

    /**
     * Generates a complete valid Sudoku solution using backtracking with bitmask constraints.
     */
    private fun generateFullSolution(): Array<IntArray> {
        val board = Array(9) { IntArray(9) { 0 } }
        // All bitmasks start at 0 (no digits used)
        fillBoard(board, IntArray(9), IntArray(9), IntArray(9), 0)
        return board
    }

    // rowUsed[r] / colUsed[c] / boxUsed[b]: bitmask of digits present (bit k = digit k is used)
    private fun fillBoard(
        board: Array<IntArray>,
        rowUsed: IntArray,
        colUsed: IntArray,
        boxUsed: IntArray,
        index: Int
    ): Boolean {
        if (index == 81) return true
        val row = index / 9
        val col = index % 9
        val box = (row / 3) * 3 + col / 3

        // Bits 1..9 that are free in this row, column, and box
        val available = 0x3FE and (rowUsed[row] or colUsed[col] or boxUsed[box]).inv()
        if (available == 0) return false

        // Collect free digits into an unboxed IntArray for Fisher-Yates shuffle
        val digits = IntArray(Integer.bitCount(available))
        var i = 0; var mask = available
        while (mask != 0) {
            val lsb = mask and (-mask)
            digits[i++] = Integer.numberOfTrailingZeros(lsb)
            mask = mask xor lsb
        }
        // Fisher-Yates in-place shuffle — no boxing, no extra allocation
        for (j in digits.size - 1 downTo 1) {
            val k = Random.nextInt(j + 1)
            val tmp = digits[j]; digits[j] = digits[k]; digits[k] = tmp
        }

        for (digit in digits) {
            val bit = 1 shl digit
            board[row][col] = digit
            rowUsed[row] = rowUsed[row] or bit
            colUsed[col] = colUsed[col] or bit
            boxUsed[box] = boxUsed[box] or bit

            if (fillBoard(board, rowUsed, colUsed, boxUsed, index + 1)) return true

            board[row][col] = 0
            rowUsed[row] = rowUsed[row] xor bit
            colUsed[col] = colUsed[col] xor bit
            boxUsed[box] = boxUsed[box] xor bit
        }
        return false
    }

    private const val MAX_ATTEMPTS = 60

    private fun flatten(grid: Array<IntArray>) = IntArray(81) { grid[it / 9][it % 9] }

    /**
     * Retire des indices un par un, dans un ordre au hasard, tant que la grille reste faisable
     * avec les techniques du niveau voulu — ce qui garantit aussi une solution unique — et sans
     * passer sous le plancher d'indices de la difficulté.
     */
    private fun createPuzzleFromSolution(solution: Array<IntArray>, difficulty: SudokuDifficulty): Array<IntArray> {
        val puzzle = solution.map { it.copyOf() }.toTypedArray()
        val positions = (0 until 81).shuffled()
        var clues = 81

        for (pos in positions) {
            if (clues <= difficulty.minClues) break
            val row = pos / 9
            val col = pos % 9
            val backup = puzzle[row][col]
            if (backup == 0) continue

            puzzle[row][col] = 0
            if (SudokuGrader.grade(flatten(puzzle), difficulty.level) < 0) {
                puzzle[row][col] = backup
            } else {
                clues--
            }
        }
        return puzzle
    }

    /**
     * Checks if placing a digit at the given position is valid.
     */
    fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, digit: Int): Boolean {
        for (c in 0 until 9) { if (board[row][c] == digit) return false }
        for (r in 0 until 9) { if (board[r][col] == digit) return false }
        val boxRow = (row / 3) * 3; val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (board[r][c] == digit) return false
            }
        }
        return true
    }

    /**
     * Finds all conflicts (duplicate values) in the current board.
     * Uses IntArray instead of HashMap for tracking seen values (9 possible values = no need for hashing).
     */
    fun findConflicts(board: SudokuBoard): Set<CellPosition> {
        val conflicts = mutableSetOf<CellPosition>()
        val seen = IntArray(10)  // reused across rows/cols; seen[value] = position index or -1

        // Check rows
        for (row in 0 until 9) {
            seen.fill(-1)
            for (col in 0 until 9) {
                val value = board.getValue(row, col)
                if (value == 0) continue
                val prev = seen[value]
                if (prev >= 0) {
                    conflicts.add(CellPosition(row, col))
                    conflicts.add(CellPosition(row, prev))
                } else {
                    seen[value] = col
                }
            }
        }

        // Check columns
        for (col in 0 until 9) {
            seen.fill(-1)
            for (row in 0 until 9) {
                val value = board.getValue(row, col)
                if (value == 0) continue
                val prev = seen[value]
                if (prev >= 0) {
                    conflicts.add(CellPosition(row, col))
                    conflicts.add(CellPosition(prev, col))
                } else {
                    seen[value] = row
                }
            }
        }

        // Check 3x3 boxes
        val boxSeen = arrayOfNulls<CellPosition>(10)
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                boxSeen.fill(null)
                for (r in 0 until 3) {
                    for (c in 0 until 3) {
                        val row = boxRow * 3 + r
                        val col = boxCol * 3 + c
                        val value = board.getValue(row, col)
                        if (value == 0) continue
                        val prev = boxSeen[value]
                        if (prev != null) {
                            conflicts.add(CellPosition(row, col))
                            conflicts.add(prev)
                        } else {
                            boxSeen[value] = CellPosition(row, col)
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

        for (c in 0 until 9) { if (c != col) related.add(CellPosition(row, c)) }
        for (r in 0 until 9) { if (r != row) related.add(CellPosition(r, col)) }

        val boxRow = (row / 3) * 3; val boxCol = (col / 3) * 3
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
