package com.Atom2Universe.app.games.chess.ai

import karballo.Board
import karballo.Config
import karballo.search.SearchEngine
import karballo.search.SearchObserver
import karballo.search.SearchParameters
import karballo.search.SearchStatusInfo

/**
 * Adapter pour utiliser le moteur Karballo.
 * Il ne lit jamais le ChessGame : il reçoit la partie en texte (FEN de départ + coups joués)
 * et rend un coup en notation longue. Un seul fil à la fois l'utilise (ChessAI s'en charge).
 */
class KarballoAdapter {
    private val config = Config().apply {
        transpositionTableSize = 8 // 8 Mo nominaux (2^19 entrées de 18 octets)
        evaluator = "complete"
        useBook = false
    }

    private val searchEngine = SearchEngine(config)

    /** Lu par le moteur pendant la recherche : vrai quand la recherche n'est plus voulue. */
    @Volatile
    private var isCancelled: () -> Boolean = { false }

    init {
        // go() remet les limites à zéro en démarrant : un stop() arrivé juste avant serait perdu.
        // Le moteur prévient au moins une fois par itération, on le rattrape là.
        searchEngine.setObserver(object : SearchObserver {
            override fun info(info: SearchStatusInfo) {
                if (isCancelled()) searchEngine.stop()
            }

            override fun bestMove(bestMove: Int, ponder: Int) {}
        })
    }

    /**
     * Cherche le meilleur coup (appel bloquant).
     * @param startFen position de départ de la partie
     * @param moves coups joués depuis, en notation longue : le moteur voit ainsi les répétitions
     * @param currentFen position actuelle, utilisée seule si les coups ne se rejouent pas
     * @param limitElo force visée (500..2100) : le moteur fait exprès des erreurs ; null = pleine force
     * @return le coup en notation longue (« e7e5 », « b2b1q »), ou null si annulé ou introuvable
     */
    fun findBestMove(
        startFen: String,
        moves: List<String>,
        currentFen: String,
        timeLimitMs: Long,
        limitElo: Int?,
        isCancelled: () -> Boolean
    ): String? {
        loadGame(startFen, moves, currentFen)
        // L'Elo d'abord : le changer ne recalcule les erreurs que si la limite est déjà active
        if (limitElo != null) config.elo = limitElo
        config.isLimitStrength = limitElo != null
        this.isCancelled = isCancelled
        try {
            if (isCancelled()) return null
            // Ne PAS définir depth sinon le temps est ignoré par Karballo
            searchEngine.go(SearchParameters().apply { moveTime = timeLimitMs.toInt() })
            if (isCancelled()) return null
        } finally {
            this.isCancelled = { false }
        }
        val best = searchEngine.bestMove
        return if (best != karballo.Move.NONE) karballo.Move.toString(best) else null
    }

    private fun loadGame(startFen: String, moves: List<String>, currentFen: String) {
        val board = searchEngine.board
        board.resetFen(startFen)
        var replayed = true
        for (notation in moves) {
            if (board.moveNumber + HISTORY_MARGIN >= Board.MAX_MOVES ||
                !board.doMove(karballo.Move.getFromString(board, notation, true), true, false)) {
                replayed = false
                break
            }
        }
        if (replayed && samePosition(board.fen, currentFen) && board.moveNumber + HISTORY_MARGIN < Board.MAX_MOVES) return
        board.resetFen(withFirstMoveNumber(currentFen))
    }

    /** Mêmes pièces et même trait (la case en passant n'est pas notée pareil des deux côtés). */
    private fun samePosition(a: String, b: String): Boolean =
        a.split(' ').take(2) == b.split(' ').take(2)

    /** Numéro de coup ramené à 1 : l'historique du moteur part alors de zéro. */
    private fun withFirstMoveNumber(fen: String): String {
        val parts = fen.split(' ')
        return if (parts.size >= 6) (parts.take(5) + "1").joinToString(" ") else fen
    }

    /**
     * Arrête la recherche en cours
     */
    fun cancel() {
        searchEngine.stop()
    }

    /**
     * Vide la table de transposition (jamais pendant une recherche)
     */
    fun clear() {
        searchEngine.clear()
    }

    companion object {
        // Place laissée dans l'historique du moteur (Board.MAX_MOVES) : profondeur de recherche
        // et variante principale, que SearchEngine.getPv joue jusqu'à 256 demi-coups
        private const val HISTORY_MARGIN = 64 + 256 + 8
    }
}
