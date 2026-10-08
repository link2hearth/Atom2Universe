package com.Atom2Universe.app.games.chess.ai

import android.content.Context
import com.Atom2Universe.app.games.chess.ChessDifficulty
import com.Atom2Universe.app.games.chess.ChessGame
import com.Atom2Universe.app.games.chess.Move
import kotlinx.coroutines.*

/**
 * Coordinateur IA pour le jeu d'échecs
 * Utilise le moteur Karballo (niveau ~2400 Elo)
 * Gère la recherche de coups sur un thread séparé
 */
class ChessAI(
    private val game: ChessGame,
    private val difficulty: ChessDifficulty,
    context: Context? = null
) {
    // Adapter vers le moteur Karballo
    private val karballoAdapter = KarballoAdapter(context)
    private var searchJob: Job? = null

    /**
     * Interface pour les callbacks de l'IA
     */
    interface AIListener {
        /**
         * Appelé quand l'IA commence à réfléchir
         */
        fun onAIThinking()

        /**
         * Appelé quand l'IA a trouvé un coup
         * @param move Le coup trouvé
         * @param thinkTimeMs Temps de réflexion en millisecondes
         */
        fun onAIMoveFound(move: Move, thinkTimeMs: Long)

        /**
         * Appelé en cas d'erreur
         * @param error Message d'erreur
         */
        fun onAIError(error: String)
    }

    /**
     * Lance la recherche du meilleur coup
     * @param listener Écouteur pour les callbacks
     * @param scope CoroutineScope pour gérer le threading
     */
    fun findMove(
        listener: AIListener,
        scope: CoroutineScope
    ) {
        // Annuler toute recherche en cours
        cancel()

        searchJob = scope.launch(Dispatchers.Default) {
            try {
                listener.onAIThinking()

                val startTime = System.currentTimeMillis()

                // Utiliser Karballo pour trouver le meilleur coup
                // Le temps est le seul limiteur (la profondeur n'est pas utilisée)
                val bestMove = karballoAdapter.findBestMove(
                    game = game,
                    depth = 0, // Non utilisé - Karballo utilise seulement le temps
                    timeLimitMs = getKarballoTime()
                )

                val thinkTime = System.currentTimeMillis() - startTime

                // Revenir au thread principal pour le callback
                withContext(Dispatchers.Main) {
                    if (bestMove != null) {
                        listener.onAIMoveFound(bestMove, thinkTime)
                    } else {
                        listener.onAIError("Aucun coup légal trouvé")
                    }
                }
            } catch (e: CancellationException) {
                // Recherche annulée, ne rien faire
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener.onAIError("Erreur IA: ${e.message}")
                }
            }
        }
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
        karballoAdapter.cancel()
        searchJob?.cancel()
        searchJob = null
    }

    /**
     * Vide la table de transposition
     */
    fun clearTranspositionTable() {
        karballoAdapter.clear()
    }

    /**
     * Retourne le niveau de difficulté actuel
     */
    fun getDifficulty(): ChessDifficulty = difficulty
}
