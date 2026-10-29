package com.Atom2Universe.app.games.draughts.ai

import com.Atom2Universe.app.R
import androidx.annotation.StringRes
import com.Atom2Universe.app.games.draughts.DraughtsDifficulty
import com.Atom2Universe.app.games.draughts.DraughtsGame
import com.Atom2Universe.app.games.draughts.DraughtsMove
import kotlinx.coroutines.CancellationException
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
        fun onAIError(@StringRes messageRes: Int)
    }

    private var searchJob: Job? = null
    private var searchEngine: DraughtsEngine? = null

    private val timeLimitMs = when (difficulty) {
        DraughtsDifficulty.TRAINING -> 1_000L
        DraughtsDifficulty.STANDARD -> 3_000L
        DraughtsDifficulty.EXPERT -> 8_000L
        DraughtsDifficulty.TWO_PLAYER -> 0L
    }

    fun findMove(game: DraughtsGame, listener: AIListener, scope: CoroutineScope) {
        cancel()
        // Un moteur neuf par recherche : une ancienne recherche qui finit ne touche pas la nouvelle
        val engine = DraughtsEngine()
        searchEngine = engine
        val position = game.clone()
        searchJob = scope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) { listener.onAIThinking() }
                val start = System.currentTimeMillis()
                val move = engine.findBestMove(position, difficulty.depth, timeLimitMs)
                val elapsed = System.currentTimeMillis() - start
                withContext(Dispatchers.Main) {
                    if (move != null) listener.onAIMoveFound(move, elapsed)
                    else listener.onAIError(R.string.draughts_ai_no_move)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { listener.onAIError(R.string.games_ai_error) }
            }
        }
    }

    /** Arrête vraiment le calcul : le moteur lit ce drapeau pendant sa recherche. */
    fun cancel() {
        searchEngine?.cancelled = true
        searchEngine = null
        searchJob?.cancel()
        searchJob = null
    }
}
