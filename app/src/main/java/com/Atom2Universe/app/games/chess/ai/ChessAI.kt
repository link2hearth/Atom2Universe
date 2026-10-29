package com.Atom2Universe.app.games.chess.ai

import com.Atom2Universe.app.R
import androidx.annotation.StringRes
import com.Atom2Universe.app.games.chess.ChessDifficulty
import com.Atom2Universe.app.games.chess.ChessGame
import com.Atom2Universe.app.games.chess.Move
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinateur IA pour le jeu d'échecs
 * Utilise le moteur Karballo (niveau ~2400 Elo)
 * S'appelle sur le fil principal : la partie y est lue et le coup y est rendu ; seule la
 * recherche tourne sur un autre fil, sur une copie texte de la partie.
 */
class ChessAI(
    private val game: ChessGame,
    private var difficulty: ChessDifficulty
) {
    // Adapter vers le moteur Karballo (et sa table de transposition), gardé d'une partie à l'autre
    private val karballoAdapter = KarballoAdapter()
    // Une seule recherche à la fois dans le moteur : la suivante attend que l'annulée rende la main
    private val searchMutex = Mutex()
    private var searchJob: Job? = null

    @Volatile
    private var clearPending = false

    /**
     * Interface pour les callbacks de l'IA (toujours appelés sur le fil principal)
     */
    interface AIListener {
        /**
         * Appelé quand l'IA commence à réfléchir
         */
        fun onAIThinking()

        /**
         * Appelé quand l'IA a trouvé un coup
         * @param move Le coup trouvé, légal dans la position où il a été demandé
         * @param thinkTimeMs Temps de réflexion en millisecondes
         */
        fun onAIMoveFound(move: Move, thinkTimeMs: Long)

        /**
         * Appelé quand il n'y a aucun coup à jouer
         */
        fun onAIError(@StringRes messageRes: Int)
    }

    /**
     * Lance la recherche du meilleur coup (à appeler sur le fil principal)
     * @param listener Écouteur pour les callbacks
     * @param scope CoroutineScope pour gérer le threading
     */
    fun findMove(
        listener: AIListener,
        scope: CoroutineScope
    ) {
        // Annuler toute recherche en cours
        cancel()

        val legalMoves = game.getAllLegalMoves(game.currentTurn)
        if (legalMoves.isEmpty()) {
            listener.onAIError(R.string.chess_ai_no_legal_move)
            return
        }
        listener.onAIThinking()
        // Un seul coup possible : rien à chercher
        if (legalMoves.size == 1) {
            listener.onAIMoveFound(legalMoves[0], 0L)
            return
        }

        // La partie est lue ici, sur le fil principal, jamais par le fil de recherche
        val revision = game.revision
        val startFen = game.startFen
        val moves = game.movesInLongNotation()
        val currentFen = game.toFEN()
        val timeLimitMs = getKarballoTime()
        val startTime = System.currentTimeMillis()

        searchJob = scope.launch(Dispatchers.Default) {
            val bestMove = searchMutex.withLock {
                if (clearPending) {
                    clearPending = false
                    karballoAdapter.clear()
                }
                val cancelled = { !isActive }
                // Un échec du moteur : une seconde tentative
                searchOnce(startFen, moves, currentFen, timeLimitMs, cancelled)
                    ?: if (isActive) searchOnce(startFen, moves, currentFen, timeLimitMs, cancelled) else null
            }

            // Revenir au thread principal pour le callback
            withContext(Dispatchers.Main) {
                // Recherche annulée, ou partie changée depuis la demande : coup périmé
                if (!isActive || game.revision != revision) return@withContext
                // Moteur en échec deux fois : le premier coup légal, pour ne pas bloquer la partie
                val move = bestMove?.let { game.legalMoveFromLongNotation(it) }
                    ?: game.getAllLegalMoves(game.currentTurn).firstOrNull()
                if (move != null) {
                    listener.onAIMoveFound(move, System.currentTimeMillis() - startTime)
                } else {
                    listener.onAIError(R.string.chess_ai_no_legal_move)
                }
            }
        }
    }

    private fun searchOnce(
        startFen: String,
        moves: List<String>,
        currentFen: String,
        timeLimitMs: Long,
        cancelled: () -> Boolean
    ): String? = try {
        karballoAdapter.findBestMove(startFen, moves, currentFen, timeLimitMs, cancelled)
    } catch (e: Exception) {
        null
    }

    /**
     * Retourne le temps de réflexion pour Karballo selon la difficulté
     * Karballo utilise seulement le temps (pas la profondeur) pour limiter la recherche
     */
    private fun getKarballoTime(): Long {
        return when (difficulty) {
            ChessDifficulty.TRAINING -> 500L   // 0.5s - Facile, réponse rapide
            ChessDifficulty.STANDARD -> 2000L  // 2s - Niveau intermédiaire
            ChessDifficulty.EXPERT -> 5000L    // 5s - Niveau expert fort
            ChessDifficulty.TWO_PLAYER -> 0L   // Pas d'IA
        }
    }

    /**
     * Annule la recherche en cours
     */
    fun cancel() {
        searchJob?.cancel()
        searchJob = null
        karballoAdapter.cancel()
    }

    /**
     * Vide la table de transposition avant la prochaine recherche (jamais pendant l'une d'elles)
     */
    fun clearTranspositionTable() {
        clearPending = true
    }

    /**
     * Retourne le niveau de difficulté actuel
     */
    fun getDifficulty(): ChessDifficulty = difficulty

    /**
     * Change le temps de réflexion des prochaines recherches
     */
    fun setDifficulty(value: ChessDifficulty) {
        difficulty = value
    }
}
