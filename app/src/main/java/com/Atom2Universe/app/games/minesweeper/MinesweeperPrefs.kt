package com.Atom2Universe.app.games.minesweeper

import android.content.Context

class MinesweeperPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("minesweeper_prefs", Context.MODE_PRIVATE)

    // ── High scores ───────────────────────────────────────────────────────────

    fun getBestTime(diff: MinesweeperDifficulty): Int =
        prefs.getInt("best_${diff.name}", -1)

    fun isNewBestTime(diff: MinesweeperDifficulty, secs: Int): Boolean {
        val cur = getBestTime(diff)
        return cur < 0 || secs < cur
    }

    fun saveBestTime(diff: MinesweeperDifficulty, secs: Int) {
        prefs.edit().putInt("best_${diff.name}", secs).apply()
    }

    // ── Current game save ─────────────────────────────────────────────────────

    fun saveGame(diff: MinesweeperDifficulty, elapsedSecs: Int, game: MinesweeperGame) {
        if (game.gameState != GameState.PLAYING) { clearSavedGame(); return }
        prefs.edit()
            .putString("saved_diff", diff.name)
            .putInt("saved_elapsed", elapsedSecs)
            .putString("saved_grid", game.serialize())
            .apply()
    }

    fun loadSavedGame(diff: MinesweeperDifficulty): Pair<Int, MinesweeperGame>? {
        if (prefs.getString("saved_diff", null) != diff.name) return null
        val elapsed = prefs.getInt("saved_elapsed", 0)
        val serialized = prefs.getString("saved_grid", null) ?: return null
        val game = MinesweeperGame.deserialize(serialized) ?: return null
        return Pair(elapsed, game)
    }

    fun clearSavedGame() {
        prefs.edit().remove("saved_diff").remove("saved_elapsed").remove("saved_grid").apply()
    }
}
