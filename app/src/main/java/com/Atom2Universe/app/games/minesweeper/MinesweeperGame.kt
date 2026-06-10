package com.Atom2Universe.app.games.minesweeper

import kotlin.random.Random

enum class MinesweeperDifficulty(val cols: Int, val mines: Int) {
    EASY(9, 10),
    NORMAL(9, 20),
    MEDIUM(12, 40),
    HARD(16, 80)
}

enum class CellState { HIDDEN, REVEALED, FLAGGED }
enum class GameState { IDLE, PLAYING, WON, LOST }

data class Cell(
    var isMine: Boolean = false,
    var state: CellState = CellState.HIDDEN,
    var adjacentMines: Int = 0
)

class MinesweeperGame(val cols: Int, val rows: Int, val mineCount: Int) {

    val grid: Array<Array<Cell>> = Array(rows) { Array(cols) { Cell() } }
    var gameState: GameState = GameState.IDLE
    var flagsPlaced: Int = 0
    var revealedCount: Int = 0
    private val safeCells get() = cols * rows - mineCount

    fun reset() {
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = Cell()
        gameState = GameState.IDLE
        flagsPlaced = 0
        revealedCount = 0
    }

    private fun placeMines(safeRow: Int, safeCol: Int) {
        val forbidden = mutableSetOf<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            val nr = safeRow + dr; val nc = safeCol + dc
            if (nr in 0 until rows && nc in 0 until cols) forbidden.add(nr * cols + nc)
        }
        var placed = 0
        while (placed < mineCount) {
            val idx = Random.nextInt(rows * cols)
            if (idx !in forbidden && !grid[idx / cols][idx % cols].isMine) {
                grid[idx / cols][idx % cols].isMine = true
                placed++
            }
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!grid[r][c].isMine) grid[r][c].adjacentMines = countAdjacentMines(r, c)
        }
    }

    private fun countAdjacentMines(row: Int, col: Int): Int {
        var count = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr; val nc = col + dc
            if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc].isMine) count++
        }
        return count
    }

    fun reveal(row: Int, col: Int) {
        val cell = grid[row][col]
        if (cell.state != CellState.HIDDEN) return
        if (gameState == GameState.IDLE) {
            placeMines(row, col)
            gameState = GameState.PLAYING
        }
        if (gameState != GameState.PLAYING) return

        if (cell.isMine) {
            cell.state = CellState.REVEALED
            gameState = GameState.LOST
            revealAllMines()
            return
        }

        floodReveal(row, col)
        if (revealedCount == safeCells) gameState = GameState.WON
    }

    private fun floodReveal(row: Int, col: Int) {
        if (row !in 0 until rows || col !in 0 until cols) return
        val cell = grid[row][col]
        if (cell.state != CellState.HIDDEN || cell.isMine) return
        cell.state = CellState.REVEALED
        revealedCount++
        if (cell.adjacentMines == 0) {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr != 0 || dc != 0) floodReveal(row + dr, col + dc)
            }
        }
    }

    fun toggleFlag(row: Int, col: Int) {
        if (gameState != GameState.PLAYING && gameState != GameState.IDLE) return
        val cell = grid[row][col]
        when (cell.state) {
            CellState.HIDDEN -> { cell.state = CellState.FLAGGED; flagsPlaced++ }
            CellState.FLAGGED -> { cell.state = CellState.HIDDEN; flagsPlaced-- }
            else -> {}
        }
    }

    fun chordReveal(row: Int, col: Int) {
        val cell = grid[row][col]
        if (cell.state != CellState.REVEALED || cell.adjacentMines == 0) return
        var flagCount = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr; val nc = col + dc
            if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc].state == CellState.FLAGGED) flagCount++
        }
        if (flagCount == cell.adjacentMines) {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = row + dr; val nc = col + dc
                if (nr in 0 until rows && nc in 0 until cols) reveal(nr, nc)
            }
        }
    }

    private fun revealAllMines() {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (grid[r][c].isMine && grid[r][c].state == CellState.HIDDEN) grid[r][c].state = CellState.REVEALED
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────
    // Format: "cols,rows,mineCount,gameStateOrdinal,flagsPlaced,revealedCount|cell0,cell1,..."
    // Cell encoding: isMine*100 + state.ordinal*10 + adjacentMines

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("$cols,$rows,$mineCount,${gameState.ordinal},$flagsPlaced,$revealedCount|")
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                val v = (if (cell.isMine) 100 else 0) + cell.state.ordinal * 10 + cell.adjacentMines
                sb.append(v)
                if (r < rows - 1 || c < cols - 1) sb.append(",")
            }
        }
        return sb.toString()
    }

    companion object {
        fun deserialize(s: String): MinesweeperGame? {
            return try {
                val parts = s.split("|")
                val h = parts[0].split(",")
                val cols = h[0].toInt(); val rows = h[1].toInt(); val mines = h[2].toInt()
                val game = MinesweeperGame(cols, rows, mines)
                game.gameState = GameState.entries[h[3].toInt()]
                game.flagsPlaced = h[4].toInt()
                game.revealedCount = h[5].toInt()
                val cells = parts[1].split(",")
                var idx = 0
                for (r in 0 until rows) for (c in 0 until cols) {
                    val v = cells[idx++].toInt()
                    game.grid[r][c].isMine = v >= 100
                    val rem = v % 100
                    game.grid[r][c].state = CellState.entries[rem / 10]
                    game.grid[r][c].adjacentMines = rem % 10
                }
                game
            } catch (e: Exception) { null }
        }
    }
}
