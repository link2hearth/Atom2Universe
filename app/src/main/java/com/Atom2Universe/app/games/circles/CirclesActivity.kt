package com.Atom2Universe.app.games.circles

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class CirclesActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "circles_save"
        private const val KEY_SAVE   = "save_json"
        /** Laisse l'atome résonner avant d'afficher le panneau de victoire. */
        private const val WIN_PANEL_DELAY_MS = 1100L
    }

    private lateinit var gameView:    CirclesView
    private lateinit var tvMoves:     TextView
    private lateinit var tvReward:    TextView
    private lateinit var winOverlay:  FrameLayout
    private lateinit var tvWinMsg:    TextView
    private lateinit var btnRestart:  MaterialButton
    private lateinit var prefs:       SharedPreferences

    private val game = CirclesGame()
    private var solutionMap: Map<String, CirclesGame.SolutionEntry>? = null
    private val diffChips = mutableMapOf<CirclesDifficulty, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_circles)
        enableImmersiveMode()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        gameView   = findViewById(R.id.circles_game_view)
        tvMoves    = findViewById(R.id.circles_tv_moves)
        tvReward   = findViewById(R.id.circles_tv_reward)
        winOverlay = findViewById(R.id.circles_win_overlay)
        tvWinMsg   = findViewById(R.id.circles_tv_win_message)
        btnRestart = findViewById(R.id.circles_btn_restart)

        gameView.onRotationEnd = { solved ->
            updateStats()
            if (solved) onPuzzleSolved() else saveGame()
        }

        findViewById<ImageButton>(R.id.circles_back_button)
            .setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.circles_btn_new)
            .setOnClickListener { startNewGame(game.difficulty) }
        btnRestart.setOnClickListener { restartGame() }
        findViewById<MaterialButton>(R.id.circles_btn_hint)
            .setOnClickListener { applyHint() }
        findViewById<MaterialButton>(R.id.circles_btn_again).setOnClickListener {
            startNewGame(game.difficulty)
        }
        findViewById<MaterialButton>(R.id.circles_btn_next).setOnClickListener {
            val next = CirclesDifficulty.entries
                .getOrElse(game.difficulty.ordinal + 1) { game.difficulty }
            startNewGame(next)
        }

        buildDiffChips()

        if (!loadGame()) game.newGame(CirclesDifficulty.EASY)
        gameView.game = game
        gameView.refresh()
        updateStats()
        updateDiffChips()
        scheduleSolutionMap()

        if (game.solved) showWinOverlay()
    }

    override fun onPause() {
        super.onPause()
        saveGame()
    }

    // ── Difficulty chips ──────────────────────────────────────────────────────────

    private fun buildDiffChips() {
        val row = findViewById<LinearLayout>(R.id.circles_diff_row)
        val dp4 = (4 * resources.displayMetrics.density).toInt()
        for (diff in CirclesDifficulty.entries) {
            val chip = MaterialButton(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = diffLabel(diff)
                textSize = 12f
                cornerRadius = (20 * resources.displayMetrics.density).toInt()
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { lp -> lp.setMargins(dp4, 0, dp4, 0) }
                setOnClickListener { startNewGame(diff) }
            }
            row.addView(chip)
            diffChips[diff] = chip
        }
    }

    private fun updateDiffChips() {
        for ((diff, chip) in diffChips) com.Atom2Universe.app.games.kit.GameControlStyle.choice(chip, diff == game.difficulty)
    }

    private fun diffLabel(diff: CirclesDifficulty) = when (diff) {
        CirclesDifficulty.EASY   -> getString(R.string.circles_diff_easy)
        CirclesDifficulty.MEDIUM -> getString(R.string.circles_diff_medium)
        CirclesDifficulty.HARD   -> getString(R.string.circles_diff_hard)
    }

    // ── Game flow ─────────────────────────────────────────────────────────────────

    private fun startNewGame(diff: CirclesDifficulty) {
        hideWinOverlay()
        game.newGame(diff, Random.Default)
        gameView.game = game
        gameView.refresh()
        updateStats()
        updateDiffChips()
        saveGame()
        scheduleSolutionMap()
    }

    private fun restartGame() {
        hideWinOverlay()
        game.restart()
        gameView.refresh()
        updateStats()
        saveGame()
    }

    private fun onPuzzleSolved() {
        if (!game.rewardClaimed) {
            game.rewardClaimed = true
            awardReward()
            saveRecord()
        }
        updateStats()
        gameView.celebrate()
        winOverlay.postDelayed(showWinRunnable, WIN_PANEL_DELAY_MS)
        saveGame()
    }

    /** Puzzles résolus et meilleur nombre de coups, par difficulté. */
    private fun saveRecord() {
        val diff = game.difficulty
        val movesKey = "best_moves_" + diff.name
        val solvedKey = "solved_" + diff.name
        val bestMoves = prefs.getInt(movesKey, 0)
        prefs.edit {
            if (bestMoves == 0 || game.moves < bestMoves) putInt(movesKey, game.moves)
            putInt(solvedKey, prefs.getInt(solvedKey, 0) + 1)
        }
    }

    // ── Hint ──────────────────────────────────────────────────────────────────────

    private fun applyHint() {
        if (game.solved) return
        val map = solutionMap ?: return
        val entry = map[game.currentKey()] ?: return
        if (entry.distance <= 0) return
        val hint = entry.bestMove ?: return

        game.hintUsed = true
        val affected = game.rotateRing(hint.ringIndex, hint.direction)
        if (affected.isNotEmpty()) {
            gameView.playAnimation(affected, hint.direction, game.solved)
        }
        updateStats()
    }

    private fun scheduleSolutionMap() {
        solutionMap = null
        lifecycleScope.launch(Dispatchers.Default) {
            val map = game.buildSolutionMap()
            withContext(Dispatchers.Main) { solutionMap = map }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────────

    private fun updateStats() {
        tvMoves.text  = game.moves.toString()
        tvReward.text = rewardText()
        btnRestart.isEnabled = game.initialRotations.isNotEmpty()
    }

    private fun rewardText(): String {
        if (game.hintUsed) return "—"
        val n = CirclesGame.getNeutrinos(game.difficulty, game.ringCount)
        return getString(R.string.circles_reward_format, n)
    }

    private val showWinRunnable = Runnable { showWinOverlay() }

    private fun showWinOverlay() {
        tvWinMsg.text = getString(R.string.circles_win_message, game.moves)
        winOverlay.alpha = 0f
        winOverlay.visibility = View.VISIBLE
        winOverlay.animate().alpha(1f).setDuration(350L).start()
    }

    private fun hideWinOverlay() {
        winOverlay.removeCallbacks(showWinRunnable)
        winOverlay.animate().cancel()
        winOverlay.visibility = View.GONE
    }

    // ── Reward ────────────────────────────────────────────────────────────────────

    private fun awardReward() {
        if (game.hintUsed) {
            Toast.makeText(this, R.string.circles_toast_reward_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        val n = CirclesGame.getNeutrinos(game.difficulty, game.ringCount)
        if (n <= 0) return
        NeutrinoRepository(this).addBalance(n)
        Toast.makeText(this, getString(R.string.circles_toast_reward, n), Toast.LENGTH_SHORT).show()
    }

    // ── Persistence ───────────────────────────────────────────────────────────────

    private fun saveGame() { prefs.edit { putString(KEY_SAVE, game.serialize()) } }

    private fun loadGame(): Boolean {
        val json = prefs.getString(KEY_SAVE, null) ?: return false
        return game.deserialize(json)
    }
}
