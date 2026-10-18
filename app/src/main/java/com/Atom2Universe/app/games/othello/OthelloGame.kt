package com.Atom2Universe.app.games.othello

class OthelloGame {

    companion object {
        const val BOARD_SIZE = 8
        const val EMPTY = 0
        const val BLACK = 1
        const val WHITE = -1

        private val DIRECTIONS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
            intArrayOf(0, -1),                      intArrayOf(0, 1),
            intArrayOf(1, -1),  intArrayOf(1, 0),   intArrayOf(1, 1)
        )

        private val WEIGHTS = arrayOf(
            intArrayOf(120, -20, 20, 5, 5, 20, -20, 120),
            intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
            intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
            intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
            intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
            intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
            intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
            intArrayOf(120, -20, 20, 5, 5, 20, -20, 120)
        )
    }

    val board = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
    var currentPlayer = WHITE
    var gameOver = false
    var validMoves: Map<Pair<Int, Int>, List<Pair<Int, Int>>> = emptyMap()
        private set

    init {
        resetBoard()
    }

    fun resetBoard() {
        for (r in 0 until BOARD_SIZE) board[r].fill(EMPTY)
        val mid = BOARD_SIZE / 2
        board[mid - 1][mid - 1] = WHITE
        board[mid][mid] = WHITE
        board[mid - 1][mid] = BLACK
        board[mid][mid - 1] = BLACK
        currentPlayer = WHITE
        gameOver = false
        validMoves = computeValidMoves(WHITE)
    }

    fun computeValidMoves(player: Int): Map<Pair<Int, Int>, List<Pair<Int, Int>>> {
        val moves = mutableMapOf<Pair<Int, Int>, List<Pair<Int, Int>>>()
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                if (board[r][c] != EMPTY) continue
                val flips = collectFlips(r, c, player)
                if (flips.isNotEmpty()) moves[Pair(r, c)] = flips
            }
        }
        return moves
    }

    private fun collectFlips(row: Int, col: Int, player: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for (dir in DIRECTIONS) result.addAll(collectLine(row, col, dir[0], dir[1], player))
        return result
    }

    private fun collectLine(row: Int, col: Int, dr: Int, dc: Int, player: Int): List<Pair<Int, Int>> {
        val line = mutableListOf<Pair<Int, Int>>()
        var r = row + dr
        var c = col + dc
        while (r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE) {
            val v = board[r][c]
            if (v == EMPTY) return emptyList()
            if (v == player) return if (line.isNotEmpty()) line else emptyList()
            line.add(Pair(r, c))
            r += dr
            c += dc
        }
        return emptyList()
    }

    fun placeDisc(row: Int, col: Int): Boolean {
        val flips = validMoves[Pair(row, col)] ?: return false
        board[row][col] = currentPlayer
        for ((r, c) in flips) board[r][c] = currentPlayer
        advanceTurn()
        return true
    }

    private fun advanceTurn() {
        val next = -currentPlayer
        val nextMoves = computeValidMoves(next)
        if (nextMoves.isNotEmpty()) {
            currentPlayer = next
            validMoves = nextMoves
            return
        }
        // Next player has no moves — try keeping current player
        val currentMoves = computeValidMoves(currentPlayer)
        if (currentMoves.isNotEmpty()) {
            validMoves = currentMoves
            return
        }
        // Both players have no moves — game over
        gameOver = true
        validMoves = emptyMap()
    }

    fun getScore(): Pair<Int, Int> {
        var black = 0; var white = 0
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            when (board[r][c]) { BLACK -> black++; WHITE -> white++ }
        }
        return Pair(black, white)
    }

    fun getWinner(): Int {
        val (black, white) = getScore()
        return when {
            black > white -> BLACK
            white > black -> WHITE
            else -> EMPTY
        }
    }

    fun chooseAIMove(): Pair<Int, Int>? {
        if (validMoves.isEmpty()) return null
        var bestMove: Pair<Int, Int>? = null
        var bestScore = Int.MIN_VALUE
        for ((pos, flips) in validMoves) {
            val score = WEIGHTS[pos.first][pos.second] + flips.size * 10
            if (score > bestScore) { bestScore = score; bestMove = pos }
        }
        return bestMove
    }

    fun serialize(): Map<String, Any> {
        val flat = IntArray(BOARD_SIZE * BOARD_SIZE)
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) flat[r * BOARD_SIZE + c] = board[r][c]
        return mapOf("board" to flat.toList(), "player" to currentPlayer, "over" to gameOver)
    }

    fun deserialize(data: Map<String, Any>): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST")
            val flat = (data["board"] as? List<*>)
                ?.mapNotNull { (it as? Int) ?: (it as? Number)?.toInt() }
                ?: return false
            if (flat.size != BOARD_SIZE * BOARD_SIZE) return false
            for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
                val v = flat[r * BOARD_SIZE + c]
                board[r][c] = if (v == BLACK || v == WHITE) v else EMPTY
            }
            val cp = (data["player"] as? Int) ?: (data["player"] as? Number)?.toInt() ?: return false
            if (cp != BLACK && cp != WHITE) return false
            currentPlayer = cp
            gameOver = data["over"] as? Boolean ?: false
            validMoves = if (gameOver) emptyMap() else computeValidMoves(currentPlayer)
            true
        } catch (e: Exception) {
            false
        }
    }
}
