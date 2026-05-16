package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

data class GameStats(
    val solitairePlayed: Int = 0,
    val solitaireWon: Int = 0,
    val colorStackHardPlayed: Int = 0,
    val colorStackHardWon: Int = 0,
    val colorStackHardBestMs: Long = 0L,
    val sudokuPlayed: Int = 0,
    val sudokuWon: Int = 0,
    val chessPlayed: Int = 0,
    val chessWon: Int = 0,
    val draughtsPlayed: Int = 0,
    val draughtsWon: Int = 0,
    val game2048Played: Int = 0,
    val game2048Won: Int = 0,
    val blackjackPlayed: Int = 0,
    val blackjackWon: Int = 0,
    val pipeTapHardWon: Int = 0
)

class GameStatsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("game_stats", Context.MODE_PRIVATE)

    fun load() = GameStats(
        solitairePlayed      = prefs.getInt("solitaire_played", 0),
        solitaireWon         = prefs.getInt("solitaire_won", 0),
        colorStackHardPlayed = prefs.getInt("colorstack_hard_played", 0),
        colorStackHardWon    = prefs.getInt("colorstack_hard_won", 0),
        colorStackHardBestMs = prefs.getLong("colorstack_hard_best_ms", 0L),
        sudokuPlayed         = prefs.getInt("sudoku_played", 0),
        sudokuWon            = prefs.getInt("sudoku_won", 0),
        chessPlayed          = prefs.getInt("chess_played", 0),
        chessWon             = prefs.getInt("chess_won", 0),
        draughtsPlayed       = prefs.getInt("draughts_played", 0),
        draughtsWon          = prefs.getInt("draughts_won", 0),
        game2048Played       = prefs.getInt("game2048_played", 0),
        game2048Won          = prefs.getInt("game2048_won", 0),
        blackjackPlayed      = prefs.getInt("blackjack_played", 0),
        blackjackWon         = prefs.getInt("blackjack_won", 0)
    )

    fun recordSolitaireStarted() = increment("solitaire_played")
    fun recordSolitaireWon()     = increment("solitaire_won")

    fun recordColorStackHardStarted() = increment("colorstack_hard_played")
    fun recordColorStackHardWon()     = increment("colorstack_hard_won")

    /** Enregistre le temps si c'est un nouveau meilleur record (ms). */
    fun recordColorStackHardBestTime(ms: Long) {
        val current = prefs.getLong("colorstack_hard_best_ms", 0L)
        if (current == 0L || ms < current) {
            prefs.edit { putLong("colorstack_hard_best_ms", ms) }
        }
    }

    fun recordSudokuStarted() = increment("sudoku_played")
    fun recordSudokuWon()     = increment("sudoku_won")

    fun recordChessStarted() = increment("chess_played")
    fun recordChessWon()     = increment("chess_won")

    fun recordDraughtsPlayed() = increment("draughts_played")
    fun recordDraughtsWon()    = increment("draughts_won")

    fun recordGame2048Started() = increment("game2048_played")
    fun recordGame2048Won()     = increment("game2048_won")

    fun recordBlackjackStarted() = increment("blackjack_played")
    fun recordBlackjackWon()     = increment("blackjack_won")

    fun recordPipeTapHardWon() = increment("pipetap_hard_won")

    fun reset() {
        prefs.edit { clear() }
    }

    private fun increment(key: String) {
        prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
    }
}
