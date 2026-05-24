package com.Atom2Universe.app.games.chess.ai

import com.Atom2Universe.app.games.chess.*
import kotlin.math.max

/**
 * Moteur de recherche de coups avec algorithme Minimax et élagage alpha-beta
 */
class MoveSearch(
    private val game: ChessGame,
    private val transpositionTable: TranspositionTable
) {
    companion object {
        // Bornes sûres pour éviter l'overflow lors des négations negamax (-Int.MIN_VALUE overflow)
        private const val NEG_INF = -100_000
        private const val POS_INF = 100_000
        // Fréquence du check temporel : 1 fois toutes les 256 nœuds
        private const val TIME_CHECK_MASK = 0xFF
    }

    private var searchStartTime = 0L
    private var timeLimitMs = 0L
    private var nodesSearched = 0
    private var searchCancelled = false

    /**
     * Trouve le meilleur coup pour la position actuelle
     * @param depth Profondeur de recherche
     * @param timeLimitMs Limite de temps en millisecondes
     * @param progressCallback Callback optionnel pour progression (0-100)
     * @return Le meilleur coup trouvé, ou null si aucun
     */
    fun findBestMove(
        depth: Int,
        timeLimitMs: Long,
        progressCallback: ((Int) -> Unit)? = null
    ): Move? {
        this.searchStartTime = System.currentTimeMillis()
        this.timeLimitMs = timeLimitMs
        this.nodesSearched = 0
        this.searchCancelled = false

        var bestMove: Move? = null
        var bestScore = NEG_INF

        val legalMoves = game.getAllLegalMoves(game.currentTurn)
        if (legalMoves.isEmpty()) return null

        // Tri des coups pour optimiser alpha-beta
        val orderedMoves = orderMoves(legalMoves)

        for ((index, move) in orderedMoves.withIndex()) {
            if (isTimeExpired() || searchCancelled) break

            val gameCopy = game.cloneForSearch()
            gameCopy.makeMoveUnchecked(move)

            // bestScore sert de fenêtre alpha à la racine pour élaguer les coups frères
            val score = -alphaBeta(gameCopy, depth - 1, NEG_INF, -bestScore)

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }

            progressCallback?.invoke((index + 1) * 100 / orderedMoves.size)
        }

        return bestMove
    }

    /**
     * Algorithme Minimax avec élagage alpha-beta (negamax)
     * @return Score de la position du point de vue du joueur actuel (currentTurn)
     */
    private fun alphaBeta(
        game: ChessGame,
        depth: Int,
        alpha: Int,
        beta: Int
    ): Int {
        nodesSearched++

        if ((nodesSearched and TIME_CHECK_MASK) == 0 && (isTimeExpired() || searchCancelled)) return 0

        // Vérifier la table de transposition
        val hash = ZobristHash.compute(game)
        val ttEntry = transpositionTable.probe(hash)
        if (ttEntry != null && ttEntry.depth >= depth) {
            when (ttEntry.flag) {
                ScoreType.EXACT -> return ttEntry.score
                ScoreType.LOWER_BOUND -> if (ttEntry.score >= beta) return ttEntry.score
                ScoreType.UPPER_BOUND -> if (ttEntry.score <= alpha) return ttEntry.score
            }
        }

        if (depth == 0) {
            return quiescence(game, alpha, beta)
        }

        val legalMoves = game.getAllLegalMoves(game.currentTurn)

        // Mat ou pat : getAllLegalMoves est déjà appelé, pas besoin de updateGameStatus
        if (legalMoves.isEmpty()) {
            return if (game.isInCheck) -(20000 - depth * 100) else 0
        }

        var currentAlpha = alpha
        var bestScore = NEG_INF
        var bestMove: Move? = null

        val orderedMoves = orderMoves(legalMoves)

        for (move in orderedMoves) {
            val gameCopy = game.cloneForSearch()
            gameCopy.makeMoveUnchecked(move)

            val score = -alphaBeta(
                gameCopy,
                depth - 1,
                -beta,
                -currentAlpha
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }

            currentAlpha = max(currentAlpha, score)
            if (currentAlpha >= beta) {
                // Coupure beta
                transpositionTable.store(
                    hash, depth, score, bestMove, ScoreType.LOWER_BOUND
                )
                return score
            }
        }

        // Stocker dans la table de transposition
        val flag = if (bestScore <= alpha) {
            ScoreType.UPPER_BOUND
        } else {
            ScoreType.EXACT
        }
        transpositionTable.store(hash, depth, bestScore, bestMove, flag)

        return bestScore
    }

    /**
     * Recherche de quiescence pour éviter l'effet d'horizon
     * Continue la recherche pour les captures afin d'obtenir une position calme
     */
    private fun quiescence(
        game: ChessGame,
        alpha: Int,
        beta: Int
    ): Int {
        val allMoves = game.getAllLegalMoves(game.currentTurn)

        // Mat ou pat détecté dans la quiescence (position terminale à l'horizon)
        if (allMoves.isEmpty()) {
            return if (game.isInCheck) -20000 else 0
        }

        val standPat = BoardEvaluator.evaluate(game)

        // Convertir en score du joueur actuel : l'évaluateur retourne toujours du point de vue des blancs
        val adjustedScore = if (game.currentTurn == PieceColor.WHITE) standPat else -standPat

        if (adjustedScore >= beta) return beta
        var currentAlpha = max(alpha, adjustedScore)

        val orderedCaptures = orderMoves(allMoves.filter { it.capturedPiece != null })

        for (move in orderedCaptures) {
            if ((nodesSearched and TIME_CHECK_MASK) == 0 && (isTimeExpired() || searchCancelled)) break

            val gameCopy = game.cloneForSearch()
            gameCopy.makeMoveUnchecked(move)

            val score = -quiescence(gameCopy, -beta, -currentAlpha)

            if (score >= beta) return beta
            currentAlpha = max(currentAlpha, score)
        }

        return currentAlpha
    }

    /**
     * Tri des coups pour optimiser alpha-beta (MVV-LVA)
     * Most Valuable Victim - Least Valuable Attacker
     */
    private fun orderMoves(moves: List<Move>): List<Move> {
        return moves.sortedByDescending { move ->
            var score = 0

            // Priorité 1: Captures (MVV-LVA)
            if (move.capturedPiece != null) {
                val victimValue = move.capturedPiece.type.value
                val attackerValue = move.piece.type.value
                score += victimValue * 10 - attackerValue
            }

            // Priorité 2: Promotions
            if (move.promotionType != null) {
                score += when (move.promotionType) {
                    PieceType.QUEEN -> 9000
                    PieceType.ROOK -> 5000
                    PieceType.BISHOP -> 3000
                    PieceType.KNIGHT -> 3000
                    else -> 0
                }
            }

            // Priorité 3: Développement des pièces en début de partie
            if (move.piece.type == PieceType.KNIGHT || move.piece.type == PieceType.BISHOP) {
                if (move.from.row == 0 || move.from.row == 7) { // Rangée de départ
                    score += 30
                }
            }

            // Priorité 4: Coups centraux
            if (move.to.col in 3..4 && move.to.row in 3..4) {
                score += 20
            } else if (move.to.col in 2..5 && move.to.row in 2..5) {
                score += 10
            }

            // Priorité 5: Avancer les pions centraux
            if (move.piece.type == PieceType.PAWN) {
                if (move.to.col == 3 || move.to.col == 4) { // Colonnes d et e
                    score += 15
                }
            }

            score
        }
    }

    /**
     * Vérifie si le temps de recherche est écoulé
     */
    private fun isTimeExpired(): Boolean {
        return System.currentTimeMillis() - searchStartTime >= timeLimitMs
    }

    /**
     * Annule la recherche en cours
     */
    fun cancel() {
        searchCancelled = true
    }

    /**
     * Retourne le nombre de nœuds explorés
     */
    fun getNodesSearched(): Int = nodesSearched
}
