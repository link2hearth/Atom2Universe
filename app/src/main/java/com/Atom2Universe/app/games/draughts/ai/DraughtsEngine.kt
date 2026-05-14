package com.Atom2Universe.app.games.draughts.ai

import com.Atom2Universe.app.games.draughts.DraughtsGame
import com.Atom2Universe.app.games.draughts.DraughtsMove
import com.Atom2Universe.app.games.draughts.DraughtsPieceColor

class DraughtsEngine {

    private var startTimeMs = 0L
    private var timeLimitMs = 10_000L

    fun findBestMove(game: DraughtsGame, depth: Int, timeLimitMs: Long = 10_000L): DraughtsMove? {
        this.startTimeMs = System.currentTimeMillis()
        this.timeLimitMs = timeLimitMs
        val moves = game.getLegalMoves()
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves[0]

        val isMaximizing = game.currentTurn == DraughtsPieceColor.WHITE
        var bestMove = moves[0]
        var bestScore = if (isMaximizing) Int.MIN_VALUE else Int.MAX_VALUE

        for (move in moves) {
            if (isTimedOut()) break
            val copy = game.clone()
            copy.makeMove(move)
            val score = minimax(copy, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, !isMaximizing)
            if (isMaximizing && score > bestScore) { bestScore = score; bestMove = move }
            else if (!isMaximizing && score < bestScore) { bestScore = score; bestMove = move }
        }
        return bestMove
    }

    private fun minimax(game: DraughtsGame, depth: Int, alpha: Int, beta: Int, maximizing: Boolean): Int {
        if (isTimedOut()) return evaluate(game)
        if (game.isGameOver) return if (game.winner == DraughtsPieceColor.WHITE) 100_000 else -100_000
        if (depth == 0) return evaluate(game)

        val moves = game.getLegalMoves()
        if (moves.isEmpty()) return if (maximizing) -100_000 else 100_000

        var alphaVar = alpha; var betaVar = beta

        if (maximizing) {
            var best = Int.MIN_VALUE
            for (move in moves) {
                if (isTimedOut()) break
                val copy = game.clone()
                copy.makeMove(move)
                best = maxOf(best, minimax(copy, depth - 1, alphaVar, betaVar, false))
                alphaVar = maxOf(alphaVar, best)
                if (betaVar <= alphaVar) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (move in moves) {
                if (isTimedOut()) break
                val copy = game.clone()
                copy.makeMove(move)
                best = minOf(best, minimax(copy, depth - 1, alphaVar, betaVar, true))
                betaVar = minOf(betaVar, best)
                if (betaVar <= alphaVar) break
            }
            return best
        }
    }

    private fun evaluate(game: DraughtsGame): Int {
        if (game.isGameOver) {
            return when (game.winner) {
                DraughtsPieceColor.WHITE -> 100_000
                DraughtsPieceColor.BLACK -> -100_000
                null -> 0
            }
        }

        var score = 0

        for (r in 0..9) {
            for (c in 0..9) {
                val piece = game.board[r][c] ?: continue
                val sign = if (piece.color == DraughtsPieceColor.WHITE) 1 else -1
                val pieceValue = if (piece.isKing()) 300 else 100
                score += sign * pieceValue

                if (!piece.isKing()) {
                    // Bonus d'avancement vers la promotion
                    val advancement = if (piece.color == DraughtsPieceColor.WHITE) 9 - r else r
                    score += sign * advancement * 4

                    // Bonus de position (bords moins exposés)
                    if (c == 0 || c == 9) score += sign * 5
                } else {
                    // Les dames en position centrale sont plus puissantes
                    val centerDist = Math.abs(r - 4) + Math.abs(c - 4)
                    score += sign * (8 - centerDist) * 3
                }
            }
        }

        // Bonus de mobilité (coups disponibles)
        val whiteMoves = game.getLegalMovesForColor(DraughtsPieceColor.WHITE).size
        val blackMoves = game.getLegalMovesForColor(DraughtsPieceColor.BLACK).size
        score += (whiteMoves - blackMoves) * 2

        return score
    }

    private fun isTimedOut() = System.currentTimeMillis() - startTimeMs >= timeLimitMs
}
