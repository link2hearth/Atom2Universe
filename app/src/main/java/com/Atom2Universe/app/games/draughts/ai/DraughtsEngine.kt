package com.Atom2Universe.app.games.draughts.ai

import com.Atom2Universe.app.games.draughts.DraughtsGame
import com.Atom2Universe.app.games.draughts.DraughtsMove
import com.Atom2Universe.app.games.draughts.DraughtsPieceColor
import kotlin.math.abs

class DraughtsEngine {

    companion object {
        private const val NEG_INF = -200_000
        private const val POS_INF = 200_000
        private const val MATE_SCORE = 100_000
    }

    private var startTimeMs = 0L
    private var timeLimitMs = 10_000L
    private var timedOut = false
    private var nodeCount = 0

    fun findBestMove(game: DraughtsGame, depth: Int, timeLimitMs: Long = 10_000L): DraughtsMove? {
        this.startTimeMs = System.currentTimeMillis()
        this.timeLimitMs = timeLimitMs
        this.timedOut = false
        this.nodeCount = 0

        val moves = game.getLegalMoves()
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves[0]

        val isMaximizing = game.currentTurn == DraughtsPieceColor.WHITE
        var bestMove = moves[0]
        var bestScore = if (isMaximizing) NEG_INF else POS_INF

        for (move in moves) {
            if (isTimedOut()) break
            val copy = game.clone()
            copy.makeMove(move)
            val score = minimax(copy, depth - 1, NEG_INF, POS_INF, !isMaximizing)
            // Ne pas enregistrer le résultat si le timeout est survenu pendant cette recherche
            if (timedOut) break
            if (isMaximizing && score > bestScore) { bestScore = score; bestMove = move }
            else if (!isMaximizing && score < bestScore) { bestScore = score; bestMove = move }
        }
        return bestMove
    }

    private fun minimax(game: DraughtsGame, depth: Int, alpha: Int, beta: Int, maximizing: Boolean): Int {
        if (isTimedOut()) {
            timedOut = true
            return 0
        }
        if (game.isGameOver) return if (game.winner == DraughtsPieceColor.WHITE) MATE_SCORE else -MATE_SCORE
        if (depth == 0) return evaluate(game)

        val moves = game.getLegalMoves()
        var alphaVar = alpha; var betaVar = beta

        if (maximizing) {
            var best = NEG_INF
            for (move in moves) {
                if (timedOut) break
                val copy = game.clone()
                copy.makeMove(move)
                best = maxOf(best, minimax(copy, depth - 1, alphaVar, betaVar, false))
                alphaVar = maxOf(alphaVar, best)
                if (betaVar <= alphaVar) break
            }
            return best
        } else {
            var best = POS_INF
            for (move in moves) {
                if (timedOut) break
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
                DraughtsPieceColor.WHITE -> MATE_SCORE
                DraughtsPieceColor.BLACK -> -MATE_SCORE
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

                    // Bonus de bord (moins exposé aux captures)
                    if (c == 0 || c == 9) score += sign * 5
                } else {
                    // Les dames en position centrale sont plus puissantes
                    val centerDist = abs(r - 4) + abs(c - 4)
                    score += sign * (8 - centerDist) * 3
                }
            }
        }

        return score
    }

    // Check time every 2048 nodes to amortize the syscall overhead across the minimax tree.
    private fun isTimedOut(): Boolean {
        if (++nodeCount and 2047 != 0) return false
        return System.currentTimeMillis() - startTimeMs >= timeLimitMs
    }
}
