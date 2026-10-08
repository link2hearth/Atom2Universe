package com.Atom2Universe.app.games.draughts.ai

import com.Atom2Universe.app.games.draughts.DraughtsDifficulty
import com.Atom2Universe.app.games.draughts.DraughtsGame
import com.Atom2Universe.app.games.draughts.DraughtsMove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DraughtsAI(
    private val difficulty: DraughtsDifficulty
) {
    interface AIListener {
        fun onAIThinking()
        fun onAIMoveFound(move: DraughtsMove, thinkTimeMs: Long)
        fun onAIError(error: String)
    }

    private val engine = DraughtsEngine()
    private var searchJob: Job? = null

    private val timeLimitMs = when (difficulty) {
        DraughtsDifficulty.TRAINING -> 1_000L
        DraughtsDifficulty.STANDARD -> 3_000L
        DraughtsDifficulty.EXPERT -> 8_000L
        DraughtsDifficulty.TWO_PLAYER -> 0L
    }

    fun findMove(game: DraughtsGame, listener: AIListener, scope: CoroutineScope) {
        searchJob?.cancel()
        searchJob = scope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) { listener.onAIThinking() }
                val start = System.currentTimeMillis()
                val move = engine.findBestMove(game.clone(), difficulty.depth, timeLimitMs)
                val elapsed = System.currentTimeMillis() - start
                withContext(Dispatchers.Main) {
                    if (move != null) listener.onAIMoveFound(move, elapsed)
                    else listener.onAIError("Aucun coup trouvé")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { listener.onAIError(e.message ?: "Erreur IA") }
            }
        }
    }

    fun cancel() { searchJob?.cancel() }
}
